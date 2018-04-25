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
package io.zeebe.client.impl.event;

import java.io.InputStream;

import com.fasterxml.jackson.annotation.*;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.event.impl.RecordImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.task.impl.subscription.MsgPackField;

public class WorkflowInstanceEventImpl extends RecordImpl implements WorkflowInstanceEvent
{
    private String bpmnProcessId;
    private int version = -1;
    private long workflowKey = -1L;
    private long workflowInstanceKey = -1L;
    private String activityId;
    private final MsgPackField payload;

    private WorkflowInstanceState state;

    @JsonCreator
    public WorkflowInstanceEventImpl(@JacksonInject ZeebeObjectMapper objectMapper, @JacksonInject MsgPackConverter converter, @JsonProperty("state") String state)
    {
        super(objectMapper, RecordMetadata.RecordType.EVENT, RecordMetadata.ValueType.WORKFLOW_INSTANCE, state);
        this.payload = new MsgPackField(converter);

        this.state = WorkflowInstanceState.valueOf(state);
    }

    public WorkflowInstanceEventImpl(WorkflowInstanceEventImpl baseEvent, String state)
    {
        super(baseEvent, state);
        this.bpmnProcessId = baseEvent.bpmnProcessId;
        this.version = baseEvent.version;
        this.workflowKey = baseEvent.workflowKey;
        this.workflowInstanceKey = baseEvent.workflowInstanceKey;
        this.activityId = baseEvent.activityId;
        this.payload = new MsgPackField(baseEvent.payload);
    }

    @Override
    public String getBpmnProcessId()
    {
        return bpmnProcessId;
    }

    public void setBpmnProcessId(String bpmnProcessId)
    {
        this.bpmnProcessId = bpmnProcessId;
    }

    @Override
    public int getVersion()
    {
        return version;
    }

    public void setVersion(int version)
    {
        this.version = version;
    }

    @Override
    public long getWorkflowInstanceKey()
    {
        return workflowInstanceKey;
    }

    public void setWorkflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceKey = workflowInstanceKey;
    }

    @Override
    public String getActivityId()
    {
        return activityId;
    }

    public void setActivityId(String activityId)
    {
        this.activityId = activityId;
    }

    @Override
    @JsonIgnore
    public String getPayload()
    {
        return payload.getAsJson();
    }

    @JsonProperty("payload")
    public byte[] getPayloadMsgPack()
    {
        return this.payload.getMsgPack();
    }

    @JsonProperty("payload")
    public void setPayload(byte[] msgpack)
    {
        this.payload.setMsgPack(msgpack);
    }

    public void setPayloadAsJson(String json)
    {
        this.payload.setJson(json);
    }

    public void setPayloadAsJson(InputStream json)
    {
        this.payload.setJson(json);
    }

    @Override
    public long getWorkflowKey()
    {
        return workflowKey;
    }

    public void setWorkflowKey(long workflowKey)
    {
        this.workflowKey = workflowKey;
    }

    @Override
    public WorkflowInstanceState getState()
    {
        return state;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("WorkflowInstanceEvent [state=");
        builder.append(state);
        builder.append(", workflowInstanceKey=");
        builder.append(workflowInstanceKey);
        builder.append(", workflowKey=");
        builder.append(workflowKey);
        builder.append(", bpmnProcessId=");
        builder.append(bpmnProcessId);
        builder.append(", version=");
        builder.append(version);
        builder.append(", activityId=");
        builder.append(activityId);
        builder.append(", payload=");
        builder.append(payload.getAsJson());
        builder.append("]");
        return builder.toString();
    }
}
