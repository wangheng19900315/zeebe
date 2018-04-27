/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl;

import java.util.concurrent.*;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.subscription.SubscriptionManager;
import io.zeebe.client.topic.CreateTopicCommandImpl;
import io.zeebe.client.topic.TopicsRequestImpl;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.transport.*;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import org.slf4j.Logger;

public class ZeebeClientImpl implements ZeebeClient
{

    public static final Logger LOG = Loggers.CLIENT_LOGGER;

    public static final String VERSION;

    static
    {
        final String version = ZeebeClient.class.getPackage().getImplementationVersion();
        VERSION = version != null ? version : "development";
    }

    protected final ZeebeClientConfiguration configuration;

    protected Dispatcher dataFrameReceiveBuffer;
    protected Dispatcher sendBuffer;
    protected ActorScheduler scheduler;

    protected ClientTransport transport;

    protected final ZeebeObjectMapperImpl objectMapper;
    protected final MsgPackConverter msgPackConverter;

    protected final ClientTopologyManager topologyManager;
    protected final RequestManager apiCommandManager;
    protected SubscriptionManager subscriptionManager;

    protected final int subscriptionPrefetchCapacity;

    protected boolean isClosed;

    public ZeebeClientImpl(final ZeebeClientConfiguration configuration)
    {
        this(configuration, null);
    }

    public ZeebeClientImpl(final ZeebeClientConfiguration configuration, ActorClock actorClock)
    {
        LOG.info("Version: {}", VERSION);

        this.configuration = configuration;

        final SocketAddress contactPoint = SocketAddress.from(configuration.getBrokerContactPoint());

        this.scheduler = ActorScheduler.newActorScheduler()
                                       .setCpuBoundActorThreadCount(configuration.getNumManagementThreads())
                                       .setIoBoundActorThreadCount(0)
                                       .setActorClock(actorClock)
                                       .setSchedulerName("client")
                                       .build();
        this.scheduler.start();

        dataFrameReceiveBuffer = Dispatchers.create("receive-buffer")
            .bufferSize(1024 * 1024 * configuration.getSendBufferSize())
            .modePubSub()
            .frameMaxLength(1024 * 1024)
            .actorScheduler(scheduler)
            .build();

        sendBuffer = Dispatchers.create("send-buffer")
            .actorScheduler(scheduler)
            .bufferSize(1024 * 1024 * configuration.getSendBufferSize())
            .build();

        final ClientTransportBuilder transportBuilder = Transports.newClientTransport()
            .messageMaxLength(1024 * 1024)
            .messageReceiveBuffer(dataFrameReceiveBuffer)
            .requestPoolSize(configuration.getMaxRequests() + 16)
            .scheduler(scheduler)
            .sendBuffer(sendBuffer);

        if (configuration.getTcpChannelKeepAlivePeriod() != null)
        {
            transportBuilder.keepAlivePeriod(configuration.getTcpChannelKeepAlivePeriod());
        }

        transport = transportBuilder.build();

        this.msgPackConverter = new MsgPackConverter();
        this.objectMapper = new ZeebeObjectMapperImpl(msgPackConverter);

        subscriptionPrefetchCapacity = configuration.getTopicSubscriptionPrefetchCapacity();

        final RemoteAddress initialContactPoint = transport.registerRemoteAddress(contactPoint);

        topologyManager = new ClientTopologyManager(transport, objectMapper, initialContactPoint);
        scheduler.submitActor(topologyManager);

        apiCommandManager = new RequestManager(
                transport.getOutput(),
                topologyManager,
                objectMapper,
                configuration.getRequestTimeout(),
                configuration.getMaxRequests(),
                configuration.getRequestBlocktime().toMillis());
        this.scheduler.submitActor(apiCommandManager);

        this.subscriptionManager = new SubscriptionManager(this);
        this.transport.registerChannelListener(subscriptionManager);
        this.scheduler.submitActor(subscriptionManager);
    }

    @Override
    public void close()
    {
        if (isClosed)
        {
            return;
        }

        isClosed = true;

        LOG.debug("Closing client ...");

        doAndLogException(() -> subscriptionManager.close().join());
        LOG.debug("subscriber group manager closed");
        doAndLogException(() -> apiCommandManager.close().join());
        LOG.debug("api command manager closed");
        doAndLogException(() -> topologyManager.close().join());
        LOG.debug("topology manager closed");
        doAndLogException(() -> transport.close());
        LOG.debug("data frame receive buffer closed");
        doAndLogException(() -> dataFrameReceiveBuffer.close());
        LOG.debug("sendbuffer closed");
        doAndLogException(() -> sendBuffer.close());

        try
        {
            scheduler.stop().get(15, TimeUnit.SECONDS);

            LOG.debug("Client closed.");
        }
        catch (InterruptedException | ExecutionException | TimeoutException e)
        {
            throw new RuntimeException("Could not shutdown client successfully", e);
        }
    }

    protected void doAndLogException(Runnable r)
    {
        try
        {
            r.run();
        }
        catch (Exception e)
        {
            Loggers.CLIENT_LOGGER.error("Exception when closing client. Ignoring", e);
        }
    }


    public RequestManager getCommandManager()
    {
        return apiCommandManager;
    }

    public ClientTopologyManager getTopologyManager()
    {
        return topologyManager;
    }

    public ZeebeObjectMapperImpl getObjectMapper()
    {
        return objectMapper;
    }

    @Override
    public ZeebeClientConfiguration getConfiguration()
    {
        return configuration;
    }

    public ClientTransport getTransport()
    {
        return transport;
    }

    public MsgPackConverter getMsgPackConverter()
    {
        return msgPackConverter;
    }

    public ActorScheduler getScheduler()
    {
        return scheduler;
    }

    public SubscriptionManager getSubscriptionManager()
    {
        return subscriptionManager;
    }

    public int getSubscriptionPrefetchCapacity()
    {
        return subscriptionPrefetchCapacity;
    }

    @Override
    public TopicClient topicClient(String topicName)
    {
        return new TopicClientImpl(this, topicName);
    }

    @Override
    public TopicClient topicClient()
    {
        // TODO resolve the default topic
        return new TopicClientImpl(this, "default-topic");
    }

    @Override
    public ZeebeObjectMapper objectMapper()
    {
        return objectMapper;
    }

    @Override
    public CreateTopicCommandStep1 newCreateTopicCommand()
    {
        return new CreateTopicCommandImpl(getCommandManager());
    }

    @Override
    public TopicsRequestStep1 newTopicsRequest()
    {
        return new TopicsRequestImpl(getCommandManager());
    }

    @Override
    public TopologyRequestStep1 newTopologyRequest()
    {
        // TODO Auto-generated method stub
        return null;
    }
}
