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
import io.zeebe.client.api.commands.WorkflowCommand;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.record.WorkflowRecordImpl;

public class WorkflowCommandImpl extends WorkflowRecordImpl implements WorkflowCommand
{
    private WorkflowCommandName name;

    @JsonCreator
    public WorkflowCommandImpl(@JacksonInject ZeebeObjectMapper objectMapper)
    {
        super(objectMapper, RecordMetadata.RecordType.COMMAND);
    }

    @Override
    public WorkflowCommandName getName()
    {
        return name;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("WorkflowCommand [Command=");
        builder.append(name);
        builder.append(", bpmnProcessId=");
        builder.append(getBpmnProcessId());
        builder.append(", version=");
        builder.append(getVersion());
        builder.append(", deploymentKey=");
        builder.append(getDeploymentKey());
        builder.append(", bpmnXml=");
        builder.append(getBpmnXml());
        builder.append("]");
        return builder.toString();
    }

    @Override
    protected void mapIntent(String intent)
    {
        name = WorkflowCommandName.valueOf(intent);
    }

}
