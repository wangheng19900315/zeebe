package io.zeebe.broker.clustering.base.snapshotrepl;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.logstreams.spi.SnapshotWriter;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;

public class FollowerSnapshotReplicator implements Service<FollowerSnapshotReplicator>
{
    private final byte[] writeBuffer = new byte[4 * 1024];

    private final RequestSnapshotMessage requestSnapshotMessage = new RequestSnapshotMessage();
    private final BeginSnapshotMessage beginSnapshotMessage = new BeginSnapshotMessage();
    private final SnapshotFragmentMessage snapshotFragmentMessage = new SnapshotFragmentMessage();

    private ActorControl actor;

    private Injector<ClientTransport> clientTransportInjector = new Injector<>();
    private Injector<Partition> partitionInjector = new Injector<>();
    private Injector<TopologyManager> topologyManagerInjector = new Injector<>();

    private Partition partition;
    private ClientTransport clientTransport;
    private TopologyManager topologyManager;

    private SnapshotReplicatorState state;

    public FollowerSnapshotReplicator(ActorControl actor)
    {
        this.actor = actor;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        partition = partitionInjector.getValue();
        clientTransport = clientTransportInjector.getValue();
        topologyManager = topologyManagerInjector.getValue();

        state = new RequestSnapshotState(false);
    }

    public void onBeginSnapshotMessage(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
    {
        state.onBeginSnapshotMessage(remoteAddress, buffer, offset, length);
    }

    public void onSnapshotFragment(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
    {
        state.onBeginSnapshotMessage(remoteAddress, buffer, offset, length);
    }

    @Override
    public FollowerSnapshotReplicator get()
    {
        return this;
    }

    interface SnapshotReplicatorState
    {
        void onBeginSnapshotMessage(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length);

        void onSnapshotFragment(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length);
    }

    class RequestSnapshotState implements SnapshotReplicatorState
    {
        RequestSnapshotState(boolean isRetry)
        {
            final Duration timeout;

            if (isRetry)
            {
                timeout = Duration.ofSeconds(10);
            }
            else
            {
                timeout = Duration.ofMinutes(5);
            }

            actor.runDelayed(timeout, requestSnapshot(this));
        }

        private Runnable requestSnapshot(RequestSnapshotState expectedState)
        {
            return () ->
            {
                if (state == expectedState)
                {
                    final int partitionId = partition.getInfo().getPartitionId();
                    final long position = partition.getLogStream().getCommitPosition();

                    requestSnapshotMessage.setPartitionId(partitionId)
                        .setPosition(position);

                    final ActorFuture<SocketAddress> remoteAddress = topologyManager.query((t) ->
                    {
                        final NodeInfo leader = t.getLeader(partitionId);
                        if (leader != null)
                        {
                            return leader.getManagementApiAddress();
                        }
                        else
                        {
                            return null;
                        }
                    });

                    actor.runOnCompletion(remoteAddress, (a, t) ->
                    {
                        if (state != expectedState)
                        {
                            return;
                        }

                        if (a != null)
                        {
                            final TransportMessage transportMessage = new TransportMessage();
                            transportMessage.writer(requestSnapshotMessage);

                            actor.runUntilDone(() ->
                            {
                                final boolean wasSent = clientTransport.getOutput()
                                    .sendMessage(transportMessage);

                                if (wasSent)
                                {
                                    actor.done();
                                }
                                else
                                {
                                    actor.yield();
                                }
                            });
                        }
                        else
                        {
                            state = new RequestSnapshotState(true);
                        }
                    });
                }
            };
        }

        @Override
        public void onBeginSnapshotMessage(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
        {
            // ignore
        }

        @Override
        public void onSnapshotFragment(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
        {
            // ignore
        }

    }

    class AwaitBeginSnapshot implements SnapshotReplicatorState
    {
        @Override
        public void onBeginSnapshotMessage(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
        {
            beginSnapshotMessage.wrap(buffer, offset, length);

            final long position = beginSnapshotMessage.getPosition();
            final long snapshotLength = beginSnapshotMessage.getSnapshotLength();
            final DirectBuffer snapshotName = BufferUtil.cloneBuffer(beginSnapshotMessage.getSnapshotName());
            final DirectBuffer checksum = BufferUtil.cloneBuffer(beginSnapshotMessage.getChecksum());

            final SnapshotStorage snapshotStorage = partition.getSnapshotStorage();

            try
            {
                final SnapshotWriter snapshot = snapshotStorage.createSnapshot(BufferUtil.bufferAsString(snapshotName), position);
                state = new WriteSnapshotState(snapshotName, checksum, position, snapshotLength, snapshot);
            }
            catch (Exception e)
            {
                state = new RequestSnapshotState(true);
            }

        }

        @Override
        public void onSnapshotFragment(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
        {
            // ignore / invalid
        }
    }

    class WriteSnapshotState implements SnapshotReplicatorState
    {
        private final DirectBuffer name;
        private final DirectBuffer checksum;
        private final long position;
        private final long snapshotLength;

        private SnapshotWriter snapshotWriter;

        private long expectedOffset  = 0;

        WriteSnapshotState(DirectBuffer name, DirectBuffer checksum, long position, long snapshotLength, SnapshotWriter snapshot)
        {
            this.name = name;
            this.checksum = checksum;
            this.position = position;
            this.snapshotLength = snapshotLength;
            this.snapshotWriter = snapshot;
        }

        @Override
        public void onBeginSnapshotMessage(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
        {
            // ignore / invalid
        }

        @Override
        public void onSnapshotFragment(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
        {
            snapshotFragmentMessage.wrap(buffer, offset, length);

            if (position == snapshotFragmentMessage.getPosition() && expectedOffset == snapshotFragmentMessage.getOffset())
            {
                writeFragment();

                if (expectedOffset >= snapshotLength)
                {
                    onSnapshotFullyWritten();
                }
            }
        }

        private void writeFragment()
        {
            final DirectBuffer data = snapshotFragmentMessage.getData();
            final OutputStream outputStream = snapshotWriter.getOutputStream();

            final int dataLength = data.capacity();
            int writeOffset = 0;

            while (writeOffset < dataLength)
            {
                final int readLength = Math.min(writeBuffer.length, dataLength - writeOffset);
                data.getBytes(writeOffset, writeBuffer, 0, readLength);

                try
                {
                    outputStream.write(writeBuffer, 0, readLength);
                    writeOffset += readLength;
                }
                catch (IOException e)
                {
                    state = new RequestSnapshotState(true);
                    snapshotWriter.abort();
                }
            }

            expectedOffset = expectedOffset + dataLength;
        }

        private void onSnapshotFullyWritten()
        {
            if (snapshotWriter.validateChecksum(checksum))
            {
                try
                {
                    snapshotWriter.commit();
                    state = new RequestSnapshotState(false);
                }
                catch (Exception e)
                {
                    state = new RequestSnapshotState(true);
                }
            }
            else
            {
                state = new RequestSnapshotState(true);
                snapshotWriter.abort();
            }
        }
    }
}
