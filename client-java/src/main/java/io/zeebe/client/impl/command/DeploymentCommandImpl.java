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

import java.util.List;

import com.fasterxml.jackson.annotation.*;
import io.zeebe.client.api.commands.DeploymentCommand;
import io.zeebe.client.api.commands.DeploymentResource;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.event.impl.RecordImpl;

public class DeploymentCommandImpl extends RecordImpl implements DeploymentCommand
{
    @JsonProperty("topicName")
    private String deploymentTopic;

    private List<DeploymentResource> resources;

    private DeploymentCommandName commandName;

    @JsonCreator
    public DeploymentCommandImpl(@JacksonInject ZeebeObjectMapper objectMapper, @JsonProperty("commandName") String commandName)
    {
        super(objectMapper, RecordMetadata.RecordType.COMMAND, RecordMetadata.ValueType.DEPLOYMENT, commandName);

        this.commandName = DeploymentCommandName.valueOf(commandName);
    }

    public DeploymentCommandImpl(DeploymentCommandName commandName)
    {
        super(null, RecordMetadata.RecordType.COMMAND, RecordMetadata.ValueType.DEPLOYMENT, commandName.name());

        this.commandName = commandName;
    }

    @Override
    public String getDeploymentTopic()
    {
        return deploymentTopic;
    }

    public void setDeploymentTopic(String deploymentTopic)
    {
        this.deploymentTopic = deploymentTopic;
    }

    @Override
    public List<DeploymentResource> getResources()
    {
        return resources;
    }

    public void setResources(List<DeploymentResource> resources)
    {
        this.resources = resources;
    }

    @Override
    public DeploymentCommandName getName()
    {
        return commandName;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("DeploymentCommand [topic=");
        builder.append(deploymentTopic);
        builder.append(", resource=");
        builder.append(resources);
        builder.append("]");
        return builder.toString();
    }

}
