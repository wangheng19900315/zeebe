package io.zeebe.broker.clustering.base.snapshotrepl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.sched.Actor;
import org.agrona.DirectBuffer;

public class SnapshotReplicationManager extends Actor implements Service<SnapshotReplicationManager>
{
    private final Injector<ClientTransport> managementApiClientTransportInjector = new Injector<>();

    private final ServiceGroupReference<Partition> followerPartitionsGroupReference = ServiceGroupReference.<Partition>create()
        .onAdd((name, partition) -> onFollowerPartitionAdded(partition))
        .onRemove((name, partition) -> onFollowerPartitionRemoved(partition))
        .build();

    private final ServiceGroupReference<Partition> leaderPartitionsGroupReference = ServiceGroupReference.<Partition>create()
        .onAdd((name, partition) -> onLeaderPartitionAdded(partition))
        .onRemove((name, partition) -> onLeaderPartitionRemoved(partition))
        .build();

    private final List<Partition> leaderPartitions = new ArrayList<>();

    private final List<Partition> followerPartitions = new ArrayList<>();

    public void onSnapshotRequested(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
    {

    }

    public void onBeginSnapshotMessage(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
    {

    }

    public void onSnapshotFragment(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
    {

    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        startContext.async(startContext.getScheduler().submitActor(this));
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(actor.close());
    }

    @Override
    public SnapshotReplicationManager get()
    {
        return this;
    }

    private void onLeaderPartitionRemoved(Partition partition)
    {
        leaderPartitions.remove(partition);
    }

    private void onLeaderPartitionAdded(Partition partition)
    {
        leaderPartitions.add(partition);
    }

    private void onFollowerPartitionRemoved(Partition partition)
    {
        followerPartitions.remove(partition);
    }

    private void onFollowerPartitionAdded(Partition partition)
    {
        followerPartitions.add(partition);
    }
}
