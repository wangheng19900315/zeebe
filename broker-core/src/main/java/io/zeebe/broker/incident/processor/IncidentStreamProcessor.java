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
package io.zeebe.broker.incident.processor;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentEvent;
import io.zeebe.broker.incident.index.IncidentMap;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskHeaders;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.map.Long2LongZbMap;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;

/**
 * Is responsible for the incident lifecycle.
 */
public class IncidentStreamProcessor
{
    private static final short STATE_CREATED = 1;
    private static final short STATE_RESOLVING = 2;
    private static final short STATE_DELETING = 3;

    private static final long NON_PERSISTENT_INCIDENT = -2L;

    private final Long2LongZbMap activityInstanceMap = new Long2LongZbMap();
    private final Long2LongZbMap failedTaskMap = new Long2LongZbMap();
    private final IncidentMap incidentMap = new IncidentMap();
    private final Long2LongZbMap resolvingEvents = new Long2LongZbMap();

    public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment env)
    {
        TypedEventStreamProcessorBuilder builder = env.newStreamProcessor()
            .withStateResource(activityInstanceMap)
            .withStateResource(failedTaskMap)
            .withStateResource(incidentMap.getMap())
            .withStateResource(resolvingEvents);


        // incident events
        builder = builder
            .onCommand(ValueType.INCIDENT, Intent.CREATE, new CreateIncidentProcessor())
            .onCommand(ValueType.INCIDENT, Intent.RESOLVE, new ResolveIncidentProcessor(env))
            .onEvent(ValueType.INCIDENT, Intent.RESOLVE_FAILED, new ResolveFailedProcessor())
            .onCommand(ValueType.INCIDENT, Intent.DELETE, new DeleteIncidentProcessor(env));

        // workflow instance events
        final ActivityRewrittenProcessor activityRewrittenProcessor = new ActivityRewrittenProcessor();
        final ActivityIncidentResolvedProcessor activityIncidentResolvedProcessor = new ActivityIncidentResolvedProcessor(env);

        builder = builder
            .onEvent(ValueType.WORKFLOW_INSTANCE, Intent.PAYLOAD_UPDATED, new PayloadUpdatedProcessor())
            .onEvent(ValueType.WORKFLOW_INSTANCE, Intent.ACTIVITY_TERMINATED, new ActivityTerminatedProcessor())
            .onEvent(ValueType.WORKFLOW_INSTANCE, Intent.ACTIVITY_READY, activityRewrittenProcessor)
            .onEvent(ValueType.WORKFLOW_INSTANCE, Intent.GATEWAY_ACTIVATED, activityRewrittenProcessor)
            .onEvent(ValueType.WORKFLOW_INSTANCE, Intent.ACTIVITY_COMPLETING, activityRewrittenProcessor)
            .onEvent(ValueType.WORKFLOW_INSTANCE, Intent.ACTIVITY_ACTIVATED, activityIncidentResolvedProcessor)
            .onEvent(ValueType.WORKFLOW_INSTANCE, Intent.SEQUENCE_FLOW_TAKEN, activityIncidentResolvedProcessor)
            .onEvent(ValueType.WORKFLOW_INSTANCE, Intent.ACTIVITY_COMPLETED, activityIncidentResolvedProcessor);

        // task events
        final TaskIncidentResolvedProcessor taskIncidentResolvedProcessor = new TaskIncidentResolvedProcessor(env);

        builder = builder
            .onEvent(ValueType.TASK, Intent.FAILED, new TaskFailedProcessor())
            .onEvent(ValueType.TASK, Intent.RETRIES_UPDATED, taskIncidentResolvedProcessor)
            .onEvent(ValueType.TASK, Intent.CANCELED, taskIncidentResolvedProcessor);

        return builder.build();
    }

    private final class CreateIncidentProcessor implements TypedRecordProcessor<IncidentEvent>
    {
        private boolean isCreated;
        private boolean isTaskIncident;

        @Override
        public void processRecord(TypedRecord<IncidentEvent> event)
        {
            final IncidentEvent incidentEvent = event.getValue();

            isTaskIncident = incidentEvent.getTaskKey() > 0;
            // ensure that the task is not resolved yet
            isCreated = isTaskIncident ? failedTaskMap.get(incidentEvent.getTaskKey(), -1L) == NON_PERSISTENT_INCIDENT : true;
        }

        @Override
        public long writeRecord(TypedRecord<IncidentEvent> event, TypedStreamWriter writer)
        {
            if (isCreated)
            {
                return writer.writeFollowUpEvent(event.getKey(), Intent.CREATED, event.getValue());
            }
            else
            {
                return writer.writeRejection(event);
            }
        }

        @Override
        public void updateState(TypedRecord<IncidentEvent> event)
        {
            if (isCreated)
            {
                final IncidentEvent incidentEvent = event.getValue();
                incidentMap
                    .newIncident(event.getKey())
                    .setState(STATE_CREATED)
                    .setIncidentEventPosition(event.getPosition())
                    .setFailureEventPosition(incidentEvent.getFailureEventPosition())
                    .write();

                if (isTaskIncident)
                {
                    failedTaskMap.put(incidentEvent.getTaskKey(), event.getKey());
                }
                else
                {
                    activityInstanceMap.put(incidentEvent.getActivityInstanceKey(), event.getKey());
                }
            }
        }
    }

    private final class PayloadUpdatedProcessor implements TypedRecordProcessor<WorkflowInstanceEvent>
    {
        private boolean isResolving;
        private long incidentKey;
        private final IncidentEvent incidentEvent = new IncidentEvent();

        @Override
        public void processRecord(TypedRecord<WorkflowInstanceEvent> event)
        {
            isResolving = false;

            incidentKey = activityInstanceMap.get(event.getKey(), -1L);

            if (incidentKey > 0 && incidentMap.wrapIncidentKey(incidentKey).getState() == STATE_CREATED)
            {
                final WorkflowInstanceEvent workflowInstanceEvent = event.getValue();

                incidentEvent.reset();
                incidentEvent
                    .setWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
                    .setActivityInstanceKey(event.getKey())
                    .setPayload(workflowInstanceEvent.getPayload());

                isResolving = true;
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceEvent> event, TypedStreamWriter writer)
        {
            return isResolving ? writer.writeFollowUpCommand(incidentKey, Intent.RESOLVE, incidentEvent) : 0L;
        }
    }

    private final class ResolveIncidentProcessor implements TypedRecordProcessor<IncidentEvent>
    {
        private final TypedStreamEnvironment environment;
        private TypedStreamReader reader;

        private boolean resolving;
        private TypedRecord<WorkflowInstanceEvent> failureEvent;
        private long incidentKey;

        ResolveIncidentProcessor(TypedStreamEnvironment environment)
        {
            this.environment = environment;
        }

        @Override
        public void onOpen(TypedStreamProcessor streamProcessor)
        {
            reader = environment.getStreamReader();
        }

        @Override
        public void processRecord(TypedRecord<IncidentEvent> event)
        {
            resolving = false;

            incidentKey = event.getKey();
            incidentMap.wrapIncidentKey(incidentKey);

            resolving = incidentMap.getState() == STATE_CREATED;

            if (resolving)
            {
                // re-write the failure event with new payload
                failureEvent = reader.readValue(incidentMap.getFailureEventPosition(), WorkflowInstanceEvent.class);
                failureEvent.getValue().setPayload(event.getValue().getPayload());
            }
        }

        @Override
        public long writeRecord(TypedRecord<IncidentEvent> event, TypedStreamWriter writer)
        {
            final long position;

            if (resolving)
            {
                position = writer.writeFollowUpEvent(
                    failureEvent.getKey(),
                    failureEvent.getMetadata().getIntent(),
                    failureEvent.getValue(),
                    this::setIncidentKey);
            }
            else
            {
                position = writer.writeRejection(event);
            }
            return position;
        }

        private void setIncidentKey(RecordMetadata metadata)
        {
            metadata.incidentKey(incidentKey);
        }

        @Override
        public void updateState(TypedRecord<IncidentEvent> event)
        {
            if (resolving)
            {
                incidentMap
                    .setState(STATE_RESOLVING)
                    .write();
            }
        }
    }

    private final class ResolveFailedProcessor implements TypedRecordProcessor<IncidentEvent>
    {
        private boolean isFailed;

        @Override
        public void processRecord(TypedRecord<IncidentEvent> event)
        {
            incidentMap.wrapIncidentKey(event.getKey());

            isFailed = incidentMap.getState() == STATE_RESOLVING;
        }

        @Override
        public void updateState(TypedRecord<IncidentEvent> event)
        {
            if (isFailed)
            {
                incidentMap
                    .setState(STATE_CREATED)
                    .write();
            }
        }
    }

    private final class DeleteIncidentProcessor implements TypedRecordProcessor<IncidentEvent>
    {
        private TypedStreamReader reader;
        private final TypedStreamEnvironment environment;

        private boolean isDeleted;
        private TypedRecord<IncidentEvent> incidentToWrite;

        DeleteIncidentProcessor(TypedStreamEnvironment environment)
        {
            this.environment = environment;
        }

        @Override
        public void onOpen(TypedStreamProcessor streamProcessor)
        {
            reader = environment.getStreamReader();
        }

        @Override
        public void processRecord(TypedRecord<IncidentEvent> event)
        {
            isDeleted = false;

            incidentMap.wrapIncidentKey(event.getKey());

            final long incidentEventPosition = incidentMap.getIncidentEventPosition();
            isDeleted = incidentEventPosition > 0;

            if (isDeleted)
            {
                final TypedRecord<IncidentEvent> priorIncidentEvent =
                        reader.readValue(incidentEventPosition, IncidentEvent.class);

                incidentToWrite = priorIncidentEvent;
            }
        }

        @Override
        public long writeRecord(TypedRecord<IncidentEvent> event, TypedStreamWriter writer)
        {
            if (isDeleted)
            {
                return writer.writeFollowUpEvent(incidentToWrite.getKey(), Intent.DELETED, incidentToWrite.getValue());
            }
            else
            {
                return writer.writeRejection(event);
            }
        }

        @Override
        public void updateState(TypedRecord<IncidentEvent> event)
        {
            if (isDeleted)
            {
                incidentMap.remove(event.getKey());
            }
        }
    }

    private final class ActivityRewrittenProcessor implements TypedRecordProcessor<WorkflowInstanceEvent>
    {
        @Override
        public void updateState(TypedRecord<WorkflowInstanceEvent> event)
        {
            final long incidentKey = event.getMetadata().getIncidentKey();
            if (incidentKey > 0)
            {
                resolvingEvents.put(event.getPosition(), incidentKey);
            }
        }
    }

    private final class ActivityIncidentResolvedProcessor implements TypedRecordProcessor<WorkflowInstanceEvent>
    {
        private final TypedStreamEnvironment environment;
        private TypedStreamReader reader;

        private boolean isResolved;
        private TypedRecord<IncidentEvent> incidentEvent;

        ActivityIncidentResolvedProcessor(TypedStreamEnvironment environment)
        {
            this.environment = environment;
        }

        @Override
        public void onOpen(TypedStreamProcessor streamProcessor)
        {
            reader = environment.getStreamReader();
        }

        @Override
        public void processRecord(TypedRecord<WorkflowInstanceEvent> event)
        {
            isResolved = false;
            incidentEvent = null;

            final long incidentKey = resolvingEvents.get(event.getSourcePosition(), -1);
            if (incidentKey > 0)
            {
                incidentMap.wrapIncidentKey(incidentKey);

                if (incidentMap.getState() == STATE_RESOLVING)
                {
                    // incident is resolved when read next activity lifecycle event
                    final long incidentPosition = incidentMap.getIncidentEventPosition();
                    incidentEvent = reader.readValue(incidentPosition, IncidentEvent.class);

                    isResolved = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident map");
                }
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceEvent> event, TypedStreamWriter writer)
        {
            return isResolved ?
                    writer.writeFollowUpEvent(incidentEvent.getKey(), Intent.RESOLVED, incidentEvent.getValue())
                    : 0L;
        }

        @Override
        public void updateState(TypedRecord<WorkflowInstanceEvent> event)
        {
            if (isResolved)
            {
                incidentMap.remove(incidentEvent.getKey());
                activityInstanceMap.remove(incidentEvent.getValue().getActivityInstanceKey(), -1L);
                resolvingEvents.remove(event.getSourcePosition(), -1);
            }
        }
    }

    private final class ActivityTerminatedProcessor implements TypedRecordProcessor<WorkflowInstanceEvent>
    {
        private final IncidentEvent incidentEvent = new IncidentEvent();

        private boolean isTerminated;
        private long incidentKey;


        @Override
        public void processRecord(TypedRecord<WorkflowInstanceEvent> event)
        {
            isTerminated = false;

            incidentKey = activityInstanceMap.get(event.getKey(), -1L);

            if (incidentKey > 0)
            {
                incidentMap.wrapIncidentKey(incidentKey);

                if (incidentMap.getState() == STATE_CREATED || incidentMap.getState() == STATE_RESOLVING)
                {
                    isTerminated = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident map");
                }
            }
        }

        @Override
        public long writeRecord(TypedRecord<WorkflowInstanceEvent> event, TypedStreamWriter writer)
        {

            return isTerminated ?
                    writer.writeFollowUpCommand(incidentKey, Intent.DELETE, incidentEvent)
                    : 0L;
        }

        @Override
        public void updateState(TypedRecord<WorkflowInstanceEvent> event)
        {
            if (isTerminated)
            {
                incidentMap.setState(STATE_DELETING).write();
                activityInstanceMap.remove(event.getKey(), -1L);
            }
        }
    }

    private final class TaskFailedProcessor implements TypedRecordProcessor<TaskEvent>
    {
        private final IncidentEvent incidentEvent = new IncidentEvent();

        private boolean hasRetries;
        private boolean isResolvingIncident;

        @Override
        public void processRecord(TypedRecord<TaskEvent> event)
        {
            final TaskEvent value = event.getValue();
            hasRetries = value.getRetries() > 0;
            isResolvingIncident = event.getMetadata().hasIncidentKey();

            if (!hasRetries)
            {
                final TaskHeaders taskHeaders = value.headers();

                incidentEvent.reset();
                incidentEvent
                    .setErrorType(ErrorType.TASK_NO_RETRIES)
                    .setErrorMessage("No more retries left.")
                    .setFailureEventPosition(event.getPosition())
                    .setBpmnProcessId(taskHeaders.getBpmnProcessId())
                    .setWorkflowInstanceKey(taskHeaders.getWorkflowInstanceKey())
                    .setActivityId(taskHeaders.getActivityId())
                    .setActivityInstanceKey(taskHeaders.getActivityInstanceKey())
                    .setTaskKey(event.getKey());
            }
        }

        @Override
        public long writeRecord(TypedRecord<TaskEvent> event, TypedStreamWriter writer)
        {
            if (hasRetries)
            {
                return 0L;
            }
            else
            {
                if (!isResolvingIncident)
                {
                    return writer.writeNewCommand(Intent.CREATE, incidentEvent);
                }
                else
                {
                    return writer.writeFollowUpEvent(event.getMetadata().getIncidentKey(), Intent.RESOLVE_FAILED, incidentEvent);
                }
            }
        }

        @Override
        public void updateState(TypedRecord<TaskEvent> event)
        {
            if (!hasRetries)
            {
                failedTaskMap.put(event.getKey(), NON_PERSISTENT_INCIDENT);
            }
        }
    }

    private final class TaskIncidentResolvedProcessor implements TypedRecordProcessor<TaskEvent>
    {
        private final TypedStreamEnvironment environment;

        private TypedStreamReader reader;
        private boolean isResolved;
        private TypedRecord<IncidentEvent> persistedIncident;
        private boolean isTransientIncident;

        TaskIncidentResolvedProcessor(TypedStreamEnvironment environment)
        {
            this.environment = environment;
        }

        @Override
        public void onOpen(TypedStreamProcessor streamProcessor)
        {
            reader = environment.getStreamReader();
        }

        @Override
        public void onClose()
        {
            reader.close();
        }

        @Override
        public void processRecord(TypedRecord<TaskEvent> event)
        {
            isResolved = false;
            isTransientIncident = false;

            final long incidentKey = failedTaskMap.get(event.getKey(), -1L);
            persistedIncident = null;

            if (incidentKey > 0)
            {
                incidentMap.wrapIncidentKey(incidentKey);

                if (incidentMap.getState() == STATE_CREATED)
                {
                    persistedIncident = reader.readValue(incidentMap.getIncidentEventPosition(), IncidentEvent.class);

                    isResolved = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident map");
                }
            }
            else if (incidentKey == NON_PERSISTENT_INCIDENT)
            {
                isTransientIncident = true;
            }
        }

        @Override
        public long writeRecord(TypedRecord<TaskEvent> event, TypedStreamWriter writer)
        {
            return isResolved ?
                    writer.writeFollowUpCommand(persistedIncident.getKey(), Intent.DELETE, persistedIncident.getValue()) :
                    0L;
        }

        @Override
        public void updateState(TypedRecord<TaskEvent> event)
        {
            if (isResolved || isTransientIncident)
            {
                failedTaskMap.remove(event.getKey(), -1L);
            }
        }
    }

}
