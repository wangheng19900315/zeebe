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
package io.zeebe.broker.incident.data;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.spec.MsgPackHelper;

public class IncidentEvent extends UnpackedObject
{
    protected static final DirectBuffer EMPTY_PAYLOAD = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

    private final EnumProperty<ErrorType> errorTypeProp = new EnumProperty<>("errorType", ErrorType.class, ErrorType.UNKNOWN);
    private final StringProperty errorMessageProp = new StringProperty("errorMessage", "");

    private final LongProperty failureEventPosition = new LongProperty("failureEventPosition", -1L);

    private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
    private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey", -1L);
    private final StringProperty activityIdProp = new StringProperty("activityId", "");
    private final LongProperty activityInstanceKeyProp = new LongProperty("activityInstanceKey", -1L);
    private final LongProperty taskKeyProp = new LongProperty("taskKey", -1L);

    private final BinaryProperty payloadProp = new BinaryProperty("payload", EMPTY_PAYLOAD);

    public IncidentEvent()
    {
        this
            .declareProperty(errorTypeProp)
            .declareProperty(errorMessageProp)
            .declareProperty(failureEventPosition)
            .declareProperty(bpmnProcessIdProp)
            .declareProperty(workflowInstanceKeyProp)
            .declareProperty(activityIdProp)
            .declareProperty(activityInstanceKeyProp)
            .declareProperty(taskKeyProp)
            .declareProperty(payloadProp);
    }

    public ErrorType getErrorType()
    {
        return errorTypeProp.getValue();
    }

    public IncidentEvent setErrorType(ErrorType errorType)
    {
        this.errorTypeProp.setValue(errorType);
        return this;
    }

    public DirectBuffer getErrorMessage()
    {
        return errorMessageProp.getValue();
    }

    public IncidentEvent setErrorMessage(String errorMessage)
    {
        this.errorMessageProp.setValue(errorMessage);
        return this;
    }

    public long getFailureEventPosition()
    {
        return failureEventPosition.getValue();
    }

    public IncidentEvent setFailureEventPosition(long failureEventPosition)
    {
        this.failureEventPosition.setValue(failureEventPosition);
        return this;
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessIdProp.getValue();
    }

    public IncidentEvent setBpmnProcessId(DirectBuffer directBuffer)
    {
        bpmnProcessIdProp.setValue(directBuffer, 0, directBuffer.capacity());
        return this;
    }

    public DirectBuffer getActivityId()
    {
        return activityIdProp.getValue();
    }

    public IncidentEvent setActivityId(DirectBuffer activityId)
    {
        this.activityIdProp.setValue(activityId, 0, activityId.capacity());
        return this;
    }

    public long getWorkflowInstanceKey()
    {
        return workflowInstanceKeyProp.getValue();
    }

    public IncidentEvent setWorkflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
        return this;
    }

    public long getActivityInstanceKey()
    {
        return activityInstanceKeyProp.getValue();
    }

    public IncidentEvent setActivityInstanceKey(long activityInstanceKey)
    {
        this.activityInstanceKeyProp.setValue(activityInstanceKey);
        return this;
    }

    public long getTaskKey()
    {
        return taskKeyProp.getValue();
    }

    public IncidentEvent setTaskKey(long taskKey)
    {
        this.taskKeyProp.setValue(taskKey);
        return this;
    }

    public DirectBuffer getPayload()
    {
        return this.payloadProp.getValue();
    }

    public IncidentEvent setPayload(DirectBuffer payload)
    {
        this.payloadProp.setValue(payload);
        return this;
    }

    public IncidentEvent initFromWorkflowInstanceFailure(TypedRecord<WorkflowInstanceEvent> workflowInstanceEvent)
    {
        final WorkflowInstanceEvent value = workflowInstanceEvent.getValue();

        setFailureEventPosition(workflowInstanceEvent.getPosition());
        setActivityInstanceKey(workflowInstanceEvent.getKey());
        setBpmnProcessId(value.getBpmnProcessId());
        setWorkflowInstanceKey(value.getWorkflowInstanceKey());
        setActivityId(value.getActivityId());

        return this;
    }
}
