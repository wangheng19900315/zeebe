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
package io.zeebe.test.broker.protocol.clientapi;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.SubscribedRecordDecoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ControlledActorClock;

public class ClientApiRule extends ExternalResource
{
    public static final String DEFAULT_TOPIC_NAME = "default-topic";
    public static final long DEFAULT_LOCK_DURATION = 10000L;
    public static final int DEFAULT_REPLICATION_FACTOR = 1;

    protected ClientTransport transport;
    protected Dispatcher sendBuffer;

    protected final SocketAddress brokerAddress;
    protected RemoteAddress streamAddress;

    protected MsgPackHelper msgPackHelper;
    protected RawMessageCollector incomingMessageCollector;

    private ControlledActorClock controlledActorClock = new ControlledActorClock();
    private ActorScheduler scheduler;

    protected int defaultPartitionId = -1;
    protected boolean createDefaultTopic = true;

    public ClientApiRule()
    {
        this("localhost", 51015);
    }

    public ClientApiRule(boolean createDefaultTopic)
    {
        this();
        this.createDefaultTopic = createDefaultTopic;
    }

    public ClientApiRule(String host, int port)
    {
        this.brokerAddress = new SocketAddress(host, port);
    }

    @Override
    protected void before() throws Throwable
    {
        scheduler = ActorScheduler.newActorScheduler()
                                  .setCpuBoundActorThreadCount(1)
                                  .setActorClock(controlledActorClock)
                                  .build();
        scheduler.start();

        sendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(ByteValue.ofMegabytes(32))
            .actorScheduler(scheduler)
            .build();

        incomingMessageCollector = new RawMessageCollector();

        transport = Transports.newClientTransport()
                .inputListener(incomingMessageCollector)
                .scheduler(scheduler)
                .requestPoolSize(128)
                .sendBuffer(sendBuffer)
                .build();

        msgPackHelper = new MsgPackHelper();
        streamAddress = transport.registerRemoteAddress(brokerAddress);
        doRepeatedly(() -> getPartitionsFromTopology(Protocol.SYSTEM_TOPIC)).until(l -> l != null, e -> e == null);

        if (createDefaultTopic)
        {
            createTopic(DEFAULT_TOPIC_NAME, 1);
            defaultPartitionId = getSinglePartitionId(DEFAULT_TOPIC_NAME);
        }
    }

    @Override
    protected void after()
    {
        if (transport != null)
        {
            transport.close();
        }

        if (sendBuffer != null)
        {
            sendBuffer.close();
        }

        if (scheduler != null)
        {
            scheduler.stop();
        }
    }

    /**
     * targets the default partition by default
     */
    public ExecuteCommandRequestBuilder createCmdRequest()
    {
        return new ExecuteCommandRequestBuilder(transport.getOutput(), streamAddress, msgPackHelper)
                .partitionId(defaultPartitionId);
    }

    public ControlMessageRequestBuilder createControlMessageRequest()
    {
        return new ControlMessageRequestBuilder(transport.getOutput(), streamAddress, msgPackHelper);
    }

    public ClientApiRule moveMessageStreamToTail()
    {
        incomingMessageCollector.moveToTail();
        return this;
    }

    public ClientApiRule moveMessageStreamToHead()
    {
        incomingMessageCollector.moveToHead();
        return this;
    }

    public int numSubscribedEventsAvailable()
    {
        return (int) incomingMessageCollector.getNumMessagesFulfilling(this::isSubscribedEvent);
    }

    public TestTopicClient topic()
    {
        return topic(defaultPartitionId);
    }

    public TestTopicClient topic(final int partitionId)
    {
        return new TestTopicClient(this, partitionId);
    }

    public ExecuteCommandRequest openTopicSubscription(final String name, final long startPosition)
    {
        return openTopicSubscription(defaultPartitionId, name, startPosition);
    }

    public ExecuteCommandRequest openTopicSubscription(final int partitionId, final String name, final long startPosition)
    {
        return createCmdRequest()
            .partitionId(partitionId)
            .type(ValueType.SUBSCRIBER, Intent.SUBSCRIBE)
            .command()
                .put("startPosition", startPosition)
                .put("name", name)
                .done()
            .send();
    }

    public ControlMessageRequest closeTopicSubscription(long subscriberKey)
    {
        return createControlMessageRequest()
                .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
                .data()
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", defaultPartitionId)
                .put("subscriberKey", subscriberKey)
                .done()
                .send();
    }

    public ControlMessageRequest openTaskSubscription(final String type)
    {
        return openTaskSubscription(defaultPartitionId, type, DEFAULT_LOCK_DURATION);
    }

    public ControlMessageRequest closeTaskSubscription(long subscriberKey)
    {
        return createControlMessageRequest()
                    .messageType(ControlMessageType.REMOVE_TASK_SUBSCRIPTION)
                    .data()
                        .put("subscriberKey", subscriberKey)
                    .done()
                .send();
    }

    public ControlMessageRequest openTaskSubscription(
            final int partitionId,
            final String type,
            long lockDuration,
            int credits)
    {
        return createControlMessageRequest()
                .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
                .partitionId(partitionId)
                .data()
                    .put("taskType", type)
                    .put("lockDuration", lockDuration)
                    .put("lockOwner", "test")
                    .put("credits", credits)
                    .done()
                .send();
    }

    public ControlMessageRequest openTaskSubscription(
            final int partitionId,
            final String type,
            long lockDuration)
    {
        return openTaskSubscription(partitionId, type, lockDuration, 10);
    }

    public Stream<RawMessage> incomingMessages()
    {
        return Stream.generate(incomingMessageCollector);
    }

    /**
     * @return an infinite stream of received subscribed events; make sure to use short-circuiting operations
     *   to reduce it to a finite stream
     */
    public Stream<SubscribedRecord> subscribedEvents()
    {
        return incomingMessages().filter(this::isSubscribedEvent)
                .map(this::asSubscribedEvent);
    }

    public Stream<RawMessage> commandResponses()
    {
        return incomingMessages().filter(this::isCommandResponse);
    }

    public void interruptAllChannels()
    {
        transport.interruptAllChannels();
    }

    public SocketAddress getBrokerAddress()
    {
        return brokerAddress;
    }

    protected SubscribedRecord asSubscribedEvent(RawMessage message)
    {
        final SubscribedRecord event = new SubscribedRecord(message);
        event.wrap(message.getMessage(), 0, message.getMessage().capacity());
        return event;
    }

    protected boolean isCommandResponse(RawMessage message)
    {
        return message.isResponse() &&
                isMessageOfType(message.getMessage(), ExecuteCommandResponseDecoder.TEMPLATE_ID);
    }

    protected boolean isSubscribedEvent(RawMessage message)
    {
        return message.isMessage() &&
                isMessageOfType(message.getMessage(), SubscribedRecordDecoder.TEMPLATE_ID);
    }

    protected boolean isMessageOfType(DirectBuffer message, int type)
    {
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(message, 0);

        return headerDecoder.templateId() == type;
    }

    public ExecuteCommandResponse createTopic(String name, int partitions)
    {
        return createTopic(name, partitions, DEFAULT_REPLICATION_FACTOR);
    }

    public ExecuteCommandResponse createTopic(String name, int partitions, int replicationFactor)
    {
        final ExecuteCommandResponse response = createCmdRequest()
            .partitionId(Protocol.SYSTEM_PARTITION)
            .type(ValueType.TOPIC, Intent.CREATE)
            .command()
                .put("name", name)
                .put("partitions", partitions)
                .put("replicationFactor", replicationFactor)
                .done()
            .sendAndAwait();

        if (response.intent() == Intent.CREATING)
        {
            waitForTopic(name, partitions);
        }

        return response;
    }

    public void waitForTopic(String name, int partitions)
    {

        waitUntil(() -> getPartitionIds(name).size() >= partitions);
    }

    @SuppressWarnings("unchecked")
    public List<Integer> getPartitionIds(String topicName)
    {
        final ControlMessageResponse response = requestPartitions();

        final Map<String, Object> data = response.getData();
        final List<Map<String, Object>> partitions = (List<Map<String, Object>>) data.get("partitions");

        return partitions.stream()
                         .filter(p -> topicName.equals(p.get("topic")))
                         .map(p -> (Integer) p.get("id"))
                         .collect(Collectors.toList());
    }

    public ControlMessageResponse requestPartitions()
    {
        return createControlMessageRequest()
            .partitionId(Protocol.SYSTEM_PARTITION)
            .messageType(ControlMessageType.REQUEST_PARTITIONS)
            .data().done()
            .sendAndAwait();
    }

    @SuppressWarnings("unchecked")
    public List<Integer> getPartitionsFromTopology(String topicName)
    {
        final ControlMessageResponse response = createControlMessageRequest()
            .messageType(ControlMessageType.REQUEST_TOPOLOGY)
            .data().done()
            .sendAndAwait();

        final Map<String, Object> topology = response.getData();
        final List<Map<String, Object>> brokers = (List<Map<String, Object>>) topology.get("brokers");

        final Set<Integer> partitionIds = new HashSet<>();
        for (Map<String, Object> broker : brokers)
        {
            final List<Map<String, Object>> brokerPartitionStates = (List<Map<String, Object>>) broker.get("partitions");
            for (Map<String, Object> brokerPartitionState : brokerPartitionStates)
            {
                if (topicName.equals(brokerPartitionState.get("topicName")))
                {
                    partitionIds.add((int) brokerPartitionState.get("partitionId"));
                }
            }
        }
        return new ArrayList<>(partitionIds);
    }

    public int getSinglePartitionId(String topicName)
    {
        final List<Integer> partitionIds = getPartitionsFromTopology(topicName);
        if (partitionIds.size() != 1)
        {
            throw new RuntimeException("There are " + partitionIds.size() + " partitions of topic " + topicName);
        }
        else
        {
            return partitionIds.get(0);
        }
    }

    public int getDefaultPartitionId()
    {
        return defaultPartitionId;
    }

    public ClientTransport getTransport()
    {
        return transport;
    }

    public ControlledActorClock getClock()
    {
        return controlledActorClock;
    }
}
