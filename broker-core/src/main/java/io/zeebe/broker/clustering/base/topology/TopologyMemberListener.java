package io.zeebe.broker.clustering.base.topology;

import io.zeebe.broker.clustering.base.topology.Topology.NodeInfo;

public interface TopologyMemberListener
{
    void onMemberAdded(NodeInfo memberInfo, Topology topology);

    void onMemberRemoved(NodeInfo memberInfo, Topology topology);
}