/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.raft.RaftConfiguration;

public class ClusterCfg extends GlobalCfgSupport
{
    private String[] initialContactPoints = new String[0];

    private PartitionMetadataStorageCfg partitionMetadata = new PartitionMetadataStorageCfg();

    private GossipConfiguration gossip = new GossipConfiguration();

    private RaftConfiguration raft = new RaftConfiguration();

    @Override
    public void applyGlobalConfiguration(GlobalCfg globalConfig)
    {
        partitionMetadata.applyGlobalConfiguration(globalConfig);
    }

    public String[] getInitialContactPoints()
    {
        return initialContactPoints;
    }

    public void setInitialContactPoints(String[] initialContactPoints)
    {
        this.initialContactPoints = initialContactPoints;
    }

    public GossipConfiguration getGossip()
    {
        return gossip;
    }

    public void setGossip(GossipConfiguration gossip)
    {
        this.gossip = gossip;
    }

    public RaftConfiguration getRaft()
    {
        return raft;
    }

    public void setRaft(RaftConfiguration raft)
    {
        this.raft = raft;
    }

    public PartitionMetadataStorageCfg getPartitionMetadata()
    {
        return partitionMetadata;
    }

    public void setPartitionMetadata(PartitionMetadataStorageCfg partitionMetadata)
    {
        this.partitionMetadata = partitionMetadata;
    }
}
