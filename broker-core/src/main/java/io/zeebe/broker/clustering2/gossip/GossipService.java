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
package io.zeebe.broker.clustering2.gossip;

import static io.zeebe.util.LogUtil.*;

import java.util.concurrent.TimeUnit;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.transport.cfg.SocketBindingCfg;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.gossip.Gossip;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.*;
import org.slf4j.Logger;

public class GossipService implements Service<Gossip>
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final Injector<ClientTransport> clientTransportInjector = new Injector<>();
    private final Injector<BufferingServerTransport> bufferingServerTransportInjector = new Injector<>();
    private final TransportComponentCfg transportComponentCfg;

    private Gossip gossip;

    public GossipService(TransportComponentCfg transportComponentCfg)
    {
        this.transportComponentCfg = transportComponentCfg;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final BrokerGossipConfiguration configuration = transportComponentCfg.gossip;
        final BufferingServerTransport serverTransport = bufferingServerTransportInjector.getValue();
        final ClientTransport clientTransport = clientTransportInjector.getValue();

        final SocketBindingCfg managementApiConfig = transportComponentCfg.managementApi;
        final String bindHostname = managementApiConfig.getHost(transportComponentCfg.host);
        final int bindPort = managementApiConfig.port;
        final SocketAddress bindHost = new SocketAddress(bindHostname, bindPort);

        gossip = new Gossip(bindHost, serverTransport, clientTransport, configuration);

        startContext.async(startContext.getScheduler().submitActor(gossip));
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.run(() ->
        {
            catchAndLog(LOG, () -> gossip.leave().get(5, TimeUnit.SECONDS));
            catchAndLog(LOG, () -> gossip.close().get(5, TimeUnit.SECONDS));
        });
    }

    @Override
    public Gossip get()
    {
        return gossip;
    }

    public Injector<ClientTransport> getClientTransportInjector()
    {
        return clientTransportInjector;
    }

    public Injector<BufferingServerTransport> getBufferingServerTransportInjector()
    {
        return bufferingServerTransportInjector;
    }

}
