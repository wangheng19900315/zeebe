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
package io.zeebe.broker.clustering.management.gossip;

import static io.zeebe.broker.clustering.management.ClusteringHelper.writeAPIAddressesIntoBuffer;

import java.util.Iterator;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.broker.clustering.management.MemberRaftComposite;
import io.zeebe.gossip.GossipSyncRequestHandler;
import io.zeebe.gossip.dissemination.GossipSyncRequest;
import io.zeebe.util.DeferredCommandContext;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class APISyncHandler implements GossipSyncRequestHandler
{
    public static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final DeferredCommandContext clusterManagerCmdQueue;
    private final ClusterManagerContext clusterManagerContext;

    public APISyncHandler(DeferredCommandContext clusterManagerCmdQueue, ClusterManagerContext clusterManagerContext)
    {
        this.clusterManagerCmdQueue = clusterManagerCmdQueue;
        this.clusterManagerContext = clusterManagerContext;
    }

    @Override
    public void onSyncRequest(GossipSyncRequest request)
    {
        clusterManagerCmdQueue.runAsync(() -> {
            LOG.debug("Got API sync request.");
            final Iterator<MemberRaftComposite> iterator = clusterManagerContext.getMemberListService()
                                                                                .iterator();

            while (iterator.hasNext())
            {
                final MemberRaftComposite next = iterator.next();

                if (next.hasApis())
                {
                    final DirectBuffer payload = writeAPIAddressesIntoBuffer(next.getManagementApi(), next.getReplicationApi(), next.getClientApi());
                    request.addPayload(next.getMember()
                                           .getAddress(), payload);
                }
            }
            request.done();
            LOG.debug("Send API sync response.");
        });
    }
}
