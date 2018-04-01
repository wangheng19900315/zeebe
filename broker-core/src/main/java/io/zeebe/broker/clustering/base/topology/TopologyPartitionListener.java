package io.zeebe.broker.clustering.base.topology;

import io.zeebe.broker.clustering.base.topology.Topology.PartitionInfo;

public interface TopologyPartitionListener
{
    void onPartitionUpdated(PartitionInfo partitionInfo, Topology topology);
}