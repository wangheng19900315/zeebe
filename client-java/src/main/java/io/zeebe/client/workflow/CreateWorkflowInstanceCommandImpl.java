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
package io.zeebe.client.workflow;

import java.io.InputStream;

import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1.CreateWorkflowInstanceCommandStep2;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1.CreateWorkflowInstanceCommandStep3;
import io.zeebe.client.api.commands.WorkflowInstanceCommand.WorkflowInstanceCommandName;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.event.impl.RecordImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.cmd.CommandImpl;
import io.zeebe.client.impl.command.WorkflowInstanceCommandImpl;
import io.zeebe.client.impl.data.MsgPackConverter;

public class CreateWorkflowInstanceCommandImpl extends CommandImpl<WorkflowInstanceEvent>
        implements CreateWorkflowInstanceCommandStep1, CreateWorkflowInstanceCommandStep2, CreateWorkflowInstanceCommandStep3
{
    private final WorkflowInstanceCommandImpl command;

    public CreateWorkflowInstanceCommandImpl(final RequestManager commandManager, MsgPackConverter converter, String topic)
    {
        super(commandManager);

        command = new WorkflowInstanceCommandImpl(converter, WorkflowInstanceCommandName.CREATE_WORKFLOW_INSTANCE);
        command.setTopicName(topic);
    }

    @Override
    public CreateWorkflowInstanceCommandStep3 payload(final InputStream payload)
    {
        this.command.setPayloadAsJson(payload);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCommandStep3 payload(final String payload)
    {
        this.command.setPayloadAsJson(payload);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCommandStep2 bpmnProcessId(final String id)
    {
        this.command.setBpmnProcessId(id);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCommandStep3 version(final int version)
    {
        this.command.setVersion(version);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCommandStep3 latestVersion()
    {
        return version(LATEST_VERSION);
    }

    @Override
    public CreateWorkflowInstanceCommandStep3 workflowKey(long workflowKey)
    {
        this.command.setWorkflowKey(workflowKey);
        return this;
    }

    @Override
    public RecordImpl getCommand()
    {
        return command;
    }

}
