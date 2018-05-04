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
package io.zeebe.broker.system.deployment.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;
import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.system.deployment.data.PendingDeployments;
import io.zeebe.broker.system.deployment.data.PendingDeployments.PendingDeployment;
import io.zeebe.broker.system.deployment.data.PendingWorkflows;
import io.zeebe.broker.system.deployment.data.PendingWorkflows.PendingWorkflow;
import io.zeebe.broker.system.deployment.data.PendingWorkflows.PendingWorkflowIterator;
import io.zeebe.broker.system.deployment.message.CreateWorkflowRequest;
import io.zeebe.broker.system.deployment.message.CreateWorkflowResponse;
import io.zeebe.broker.system.deployment.message.DeleteWorkflowMessage;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.broker.workflow.data.WorkflowEvent;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.TransportMessage;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;

public class RemoteWorkflowsManager implements StreamProcessorLifecycleAware
{
    private static final Logger LOG = Loggers.SYSTEM_LOGGER;

    private final DeleteWorkflowMessage deleteMessage = new DeleteWorkflowMessage();

    private final TransportMessage transportMessage = new TransportMessage();

    private final TopologyManager topologyManager;
    private final ClientTransport managementClient;
    private final ClientOutput output;

    private final TypedStreamEnvironment environment;
    private final PendingDeployments pendingDeployments;
    private final PendingWorkflows pendingWorkflows;

    private TypedStreamReader reader;
    private TypedStreamWriter writer;
    private ActorControl actor;

    public RemoteWorkflowsManager(
            PendingDeployments pendingDeployments,
            PendingWorkflows pendingWorkflows,
            TopologyManager topologyManager,
            TypedStreamEnvironment environment,
            ClientTransport managementClient)
    {
        this.pendingDeployments = pendingDeployments;
        this.pendingWorkflows = pendingWorkflows;
        this.topologyManager = topologyManager;
        this.managementClient = managementClient;
        this.environment = environment;
        this.output = managementClient.getOutput();
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor)
    {
        this.actor = streamProcessor.getActor();
        this.reader = environment.buildStreamReader();
        this.writer = environment.buildStreamWriter();
    }

    public boolean distributeWorkflow(
            IntArrayList partitionIds,
            long workflowKey,
            WorkflowEvent event)
    {
        final CreateWorkflowRequest createRequest = new CreateWorkflowRequest()
            .workflowKey(workflowKey)
            .deploymentKey(event.getDeploymentKey())
            .version(event.getVersion())
            .bpmnProcessId(event.getBpmnProcessId())
            .bpmnXml(event.getBpmnXml());

        return forEachPartition(partitionIds, createRequest::partitionId, addr ->
        {
            LOG.debug("Send create workflow request to '{}'. Deployment-Key: {}, Workflow-Key: {}",
                addr, event.getDeploymentKey(), workflowKey);

            final ActorFuture<ClientResponse> requestFuture = sendRequest(createRequest, addr);
            actor.runOnCompletion(requestFuture, this::onRequestResolved);

            return true;
        });
    }

    public boolean deleteWorkflow(
            IntArrayList partitionIds,
            long workflowKey,
            WorkflowEvent event)
    {
        deleteMessage
            .workflowKey(workflowKey)
            .deploymentKey(event.getDeploymentKey())
            .version(event.getVersion())
            .bpmnProcessId(event.getBpmnProcessId())
            .bpmnXml(event.getBpmnXml());

        return forEachPartition(partitionIds, deleteMessage::partitionId, addr ->
        {
            LOG.debug("Send delete workflow message to '{}'. Deployment-Key: {}, Workflow-Key: {}",
                      addr, event.getDeploymentKey(), workflowKey);
            return sendMessage(deleteMessage, addr);
        });
    }

    private boolean forEachPartition(IntArrayList partitionIds, IntConsumer partitionIdConsumer, Predicate<SocketAddress> action)
    {
        final ActorFuture<Map<Integer, NodeInfo>> partitionLeaders = topologyManager.query((toplogy) ->
        {
            final Map<Integer, NodeInfo> leaders = new HashMap<>();
            partitionIds.forEach((partitionId) -> {
                final NodeInfo leader = toplogy.getLeader(partitionId);
                if (leader != null && leader.getManagementApiAddress() != null)
                {
                    leaders.put(partitionId, leader);
                }
            });
            return leaders;
        });

        actor.runOnCompletion(partitionLeaders, (leaders, throwable) ->
        {
            partitionIds.forEach((partitionId) ->
            {
                final NodeInfo leader = leaders.get(partitionId);
                partitionIdConsumer.accept(partitionId);
                action.test(leader.getManagementApiAddress());
            });
        });

        return true;
    }

    private ActorFuture<ClientResponse> sendRequest(final BufferWriter request, final SocketAddress addr)
    {
        final RemoteAddress remoteAddress = managementClient.registerRemoteAddress(addr);
        return output.sendRequest(remoteAddress, request);
    }

    private void onRequestResolved(ClientResponse successfulRequest, Throwable throwable)
    {
        if (throwable != null)
        {
            onRequestFailed();
        }
        else
        {
            onRequestSuccessful(successfulRequest);
        }
    }

    private void onRequestFailed()
    {
        LOG.info("Create workflow request failed.");
    }

    private void onRequestSuccessful(ClientResponse request)
    {
        try
        {
            final CreateWorkflowResponse createResponse = new CreateWorkflowResponse();

            final DirectBuffer responseBuffer = request.getResponseBuffer();
            createResponse.wrap(responseBuffer, 0, responseBuffer.capacity());

            final long workflowKey = createResponse.getWorkflowKey();
            final int partitionId = createResponse.getPartitionId();
            final long deploymentKey = createResponse.getDeploymentKey();

            final PendingWorkflow pendingWorkflow = pendingWorkflows.get(workflowKey, partitionId);
            if (pendingWorkflow != null && pendingWorkflow.getState() == PendingWorkflows.STATE_CREATE)
            {
                // ignore response if pending workflow or deployment is already processed
                pendingWorkflows.put(workflowKey, partitionId, PendingWorkflows.STATE_CREATED, deploymentKey);
            }

            if (isDeploymentDistributed(deploymentKey))
            {
                final PendingDeployment pendingDeployment = pendingDeployments.get(deploymentKey);
                final TypedRecord<DeploymentEvent> event = reader.readValue(pendingDeployment.getDeploymentEventPosition(), DeploymentEvent.class);

                final RecordMetadata metadata = event.getMetadata();

                actor.runUntilDone(() ->
                {
                    final long position = writer.writeFollowUpEvent(
                        event.getKey(),
                        Intent.DISTRIBUTED,
                        event.getValue(),
                        metadata::copyRequestMetadata);
                    if (position >= 0)
                    {
                        actor.done();
                    }
                    else
                    {
                        actor.yield();
                    }
                });
            }
        }
        finally
        {
            request.close();
        }
    }

    private boolean isDeploymentDistributed(long deploymentKey)
    {
        final PendingWorkflowIterator iterator = pendingWorkflows.iterator();
        while (iterator.hasNext())
        {
            final PendingWorkflow pendingWorkflow = iterator.next();

            if (pendingWorkflow.getDeploymentKey() == deploymentKey &&
                    pendingWorkflow.getState() != PendingWorkflows.STATE_CREATED)
            {
                return false;
            }
        }

        return true;
    }

    private boolean sendMessage(final BufferWriter message, final SocketAddress addr)
    {
        final RemoteAddress remoteAddress = managementClient.registerRemoteAddress(addr);

        transportMessage.remoteAddress(remoteAddress).writer(message);

        return output.sendMessage(transportMessage);
    }
}
