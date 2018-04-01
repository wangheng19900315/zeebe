package io.zeebe.broker.clustering.base.topology;

import io.zeebe.broker.clustering.base.topology.Topology.NodeInfo;
import io.zeebe.servicecontainer.*;

/**
 * This is us :)
 */
public class LocalNodeService implements Service<NodeInfo>
{
    private final NodeInfo localNode;

    public LocalNodeService(NodeInfo memberInfo)
    {
        this.localNode = memberInfo;
    }

    @Override
    public NodeInfo get()
    {
        return localNode;
    }

}
