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
package io.zeebe.client.impl.subscription.topic;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.zeebe.client.api.record.RecordMetadata.RecordType;
import io.zeebe.client.api.record.RecordMetadata.ValueType;
import io.zeebe.client.api.subscription.*;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1.*;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.command.*;
import io.zeebe.client.impl.event.*;
import io.zeebe.client.impl.record.GeneralRecordImpl;
import io.zeebe.client.impl.record.RecordMetadataImpl;
import io.zeebe.client.impl.subscription.SubscriptionManager;

public class ManagedTopicSubscriptionBuilderImpl implements ManagedTopicSubscriptionBuilderStep2, ManagedTopicSubscriptionBuilderStep3, ManagedTopicSubscriptionBuilderStep4
{
    private RecordHandler defaultRecordHandler;
    private JobEventHandler jobEventHandler;
    private JobCommandHandler jobCommandHandler;
    private WorkflowInstanceEventHandler workflowInstanceEventHandler;
    private WorkflowInstanceCommandHandler workflowInstanceCommandHandler;
    private WorkflowEventHandler workflowEventHandler;
    private WorkflowCommandHandler workflowCommandHandler;
    private IncidentEventHandler incidentEventHandler;
    private IncidentCommandHandler incidentCommandHandler;
    private RaftEventHandler raftEventHandler;

    private final TopicSubscriberGroupBuilder builder;
    private final ZeebeObjectMapperImpl objectMapper;

    public ManagedTopicSubscriptionBuilderImpl(
            String topic,
            SubscriptionManager subscriptionManager,
            ZeebeObjectMapperImpl objectMapper,
            int prefetchCapacity)
    {
        builder = new TopicSubscriberGroupBuilder(topic, subscriptionManager, prefetchCapacity);
        this.objectMapper = objectMapper;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 recordHandler(RecordHandler handler)
    {
        this.defaultRecordHandler = handler;
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 jobEventHandler(JobEventHandler handler)
    {
        this.jobEventHandler = handler;
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 jobCommandHandler(JobCommandHandler handler)
    {
        this.jobCommandHandler = handler;
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 workflowInstanceEventHandler(WorkflowInstanceEventHandler handler)
    {
        this.workflowInstanceEventHandler = handler;
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 workflowInstanceCommandHandler(WorkflowInstanceCommandHandler handler)
    {
        this.workflowInstanceCommandHandler = handler;
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 workflowEventHandler(WorkflowEventHandler handler)
    {
        this.workflowEventHandler = handler;
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 workflowCommandHandler(WorkflowCommandHandler handler)
    {
        this.workflowCommandHandler = handler;
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 incidentEventHandler(IncidentEventHandler handler)
    {
        this.incidentEventHandler = handler;
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 incidentCommandHandler(IncidentCommandHandler handler)
    {
        this.incidentCommandHandler = handler;
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderImpl raftEventHandler(final RaftEventHandler raftEventHandler)
    {
        this.raftEventHandler = raftEventHandler;
        return this;
    }


    @Override
    public ManagedTopicSubscriptionBuilderStep4 startAtPosition(int partitionId, long position)
    {
        builder.startPosition(partitionId, position);
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 startAtTailOfTopic()
    {
        builder.startAtTailOfTopic();
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 startAtHeadOfTopic()
    {
        builder.startAtHeadOfTopic();
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep3 name(String name)
    {
        builder.name(name);
        return this;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep4 forcedStart()
    {
        builder.forceStart();
        return this;
    }

    @Override
    public TopicSubscription open()
    {
        final Future<TopicSubscriberGroup> subscription = buildSubscriberGroup();

        try
        {
            return subscription.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new ClientException("Could not open subscriber group", e);
        }
    }

    public Future<TopicSubscriberGroup> buildSubscriberGroup()
    {
        builder.handler(this::dispatchEvent);

        return builder.build();
    }

    protected void dispatchEvent(GeneralRecordImpl event) throws Exception
    {
        final RecordMetadataImpl metadata = event.getMetadata();
        final ValueType valueType = metadata.getValueType();
        final RecordType recordType = metadata.getRecordType();

        if (ValueType.JOB == valueType)
        {
            if (RecordType.EVENT == recordType && jobEventHandler != null)
            {
                final JobEventImpl jobEvent = objectMapper.fromJson(event.getAsMsgPack(), JobEventImpl.class);
                jobEvent.updateMetadata(event.getMetadata());

                jobEventHandler.onJobEvent(jobEvent);
            }
            else if (RecordType.COMMAND == recordType && jobCommandHandler != null)
            {
                final JobCommandImpl jobCommand = objectMapper.fromJson(event.getAsMsgPack(), JobCommandImpl.class);
                jobCommand.updateMetadata(event.getMetadata());

                jobCommandHandler.onJobCommand(jobCommand);
            }
            else if (RecordType.COMMAND_REJECTION == recordType && jobCommandHandler != null)
            {
                final JobCommandImpl jobCommand = objectMapper.fromJson(event.getAsMsgPack(), JobCommandImpl.class);
                jobCommand.updateMetadata(event.getMetadata());

                jobCommandHandler.onJobCommandRejection(jobCommand);
            }
        }
        else if (ValueType.WORKFLOW_INSTANCE == valueType)
        {
            if (RecordType.EVENT == recordType && workflowInstanceEventHandler != null)
            {
                final WorkflowInstanceEventImpl workflowInstanceEvent = objectMapper.fromJson(event.getAsMsgPack(), WorkflowInstanceEventImpl.class);
                workflowInstanceEvent.updateMetadata(event.getMetadata());

                workflowInstanceEventHandler.onWorkflowInstanceEvent(workflowInstanceEvent);
            }
            else if (RecordType.COMMAND == recordType && workflowInstanceCommandHandler != null)
            {
                final WorkflowInstanceCommandImpl workflowInstanceCommand = objectMapper.fromJson(event.getAsMsgPack(), WorkflowInstanceCommandImpl.class);
                workflowInstanceCommand.updateMetadata(event.getMetadata());

                workflowInstanceCommandHandler.onWorkflowInstanceCommand(workflowInstanceCommand);
            }
            else if (RecordType.COMMAND_REJECTION == recordType && workflowInstanceCommandHandler != null)
            {
                final WorkflowInstanceCommandImpl workflowInstanceCommand = objectMapper.fromJson(event.getAsMsgPack(), WorkflowInstanceCommandImpl.class);
                workflowInstanceCommand.updateMetadata(event.getMetadata());

                workflowInstanceCommandHandler.onWorkflowInstanceCommandRejection(workflowInstanceCommand);
            }
        }
        else if (ValueType.WORKFLOW == valueType)
        {
            if (RecordType.EVENT == recordType && workflowEventHandler != null)
            {
                final WorkflowEventImpl workflowEvent = objectMapper.fromJson(event.getAsMsgPack(), WorkflowEventImpl.class);
                workflowEvent.updateMetadata(event.getMetadata());

                workflowEventHandler.onWorkflowEvent(workflowEvent);
            }
            else if (RecordType.COMMAND == recordType && workflowCommandHandler != null)
            {
                final WorkflowCommandImpl workflowCommand = objectMapper.fromJson(event.getAsMsgPack(), WorkflowCommandImpl.class);
                workflowCommand.updateMetadata(event.getMetadata());

                workflowCommandHandler.onWorkflowCommand(workflowCommand);
            }
            else if (RecordType.COMMAND_REJECTION == recordType && jobCommandHandler != null)
            {
                final WorkflowCommandImpl workflowCommand = objectMapper.fromJson(event.getAsMsgPack(), WorkflowCommandImpl.class);
                workflowCommand.updateMetadata(event.getMetadata());

                workflowCommandHandler.onWorkflowCommandRejection(workflowCommand);
            }
        }
        else if (ValueType.INCIDENT == valueType)
        {
            if (RecordType.EVENT == recordType && incidentEventHandler != null)
            {
                final IncidentEventImpl incidentEvent = objectMapper.fromJson(event.getAsMsgPack(), IncidentEventImpl.class);
                incidentEvent.updateMetadata(event.getMetadata());

                incidentEventHandler.onIncidentEvent(incidentEvent);
            }
            else if (RecordType.COMMAND == recordType && incidentCommandHandler != null)
            {
                final IncidentCommandImpl incidentCommand = objectMapper.fromJson(event.getAsMsgPack(), IncidentCommandImpl.class);
                incidentCommand.updateMetadata(event.getMetadata());

                incidentCommandHandler.onIncidentCommand(incidentCommand);
            }
            else if (RecordType.COMMAND_REJECTION == recordType && jobCommandHandler != null)
            {
                final IncidentCommandImpl incidentCommand = objectMapper.fromJson(event.getAsMsgPack(), IncidentCommandImpl.class);
                incidentCommand.updateMetadata(event.getMetadata());

                incidentCommandHandler.onIncidentCommandRejection(incidentCommand);
            }
        }
        else if (ValueType.RAFT == valueType)
        {
            if (RecordType.EVENT == recordType && raftEventHandler != null)
            {
                final RaftEventImpl raftEvent = objectMapper.fromJson(event.getAsMsgPack(), RaftEventImpl.class);
                raftEvent.updateMetadata(event.getMetadata());

                raftEventHandler.onRaftEvent(raftEvent);
            }
        }
        else if (defaultRecordHandler != null)
        {
            defaultRecordHandler.onRecord(event);
        }
    }

}
