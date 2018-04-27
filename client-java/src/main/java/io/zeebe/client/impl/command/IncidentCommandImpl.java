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
package io.zeebe.client.impl.command;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.zeebe.client.api.commands.IncidentCommand;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.record.IncidentRecordImpl;

public class IncidentCommandImpl extends IncidentRecordImpl implements IncidentCommand
{
    private IncidentCommandName name;

    @JsonCreator
    public IncidentCommandImpl(@JacksonInject ZeebeObjectMapper objectMapper)
    {
        super(objectMapper, RecordMetadata.RecordType.COMMAND);
    }

    @Override
    public IncidentCommandName getName()
    {
        return name;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("IncidentCommand [command=");
        builder.append(name);
        builder.append(", errorType=");
        builder.append(getErrorType());
        builder.append(", errorMessage=");
        builder.append(getErrorMessage());
        builder.append(", bpmnProcessId=");
        builder.append(getBpmnProcessId());
        builder.append(", workflowInstanceKey=");
        builder.append(getWorkflowInstanceKey());
        builder.append(", activityId=");
        builder.append(getActivityId());
        builder.append(", activityInstanceKey=");
        builder.append(getActivityInstanceKey());
        builder.append(", jobKey=");
        builder.append(getJobKey());
        builder.append("]");
        return builder.toString();
    }

    @Override
    protected void mapIntent(String intent)
    {
        name = IncidentCommandName.valueOf(intent);
    }

}
