package io.zeebe.broker.clustering.base.partitions;

import io.zeebe.broker.clustering.base.topology.Topology.PartitionInfo;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.*;

/**
 * Service representing a partition.
 */
public class Partition implements Service<Partition>
{
    private final Injector<LogStream> logStreamInjector = new Injector<>();

    private final PartitionInfo info;

    private final RaftState state;

    private LogStream logStream;

    public Partition(PartitionInfo partitionInfo, RaftState state)
    {
        this.info = partitionInfo;
        this.state = state;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        logStream = logStreamInjector.getValue();
    }

    @Override
    public Partition get()
    {
        return this;
    }

    public PartitionInfo getInfo()
    {
        return info;
    }

    public RaftState getState()
    {
        return state;
    }

    public LogStream getLogStream()
    {
        return logStream;
    }

    public Injector<LogStream> getLogStreamInjector()
    {
        return logStreamInjector;
    }
}
