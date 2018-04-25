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

import java.util.List;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.event.DeploymentResource;
import io.zeebe.client.event.impl.RecordImpl;
import io.zeebe.client.workflow.impl.WorkflowDefinitionImpl;

public class DeploymentEventImpl extends RecordImpl implements DeploymentEvent
{
    @JsonProperty("topicName")
    private String deploymentTopic;

    private List<DeploymentResource> resources;

    private List<Workflow> deployedWorkflows;

    private DeploymentState state;

    @JsonCreator
    public DeploymentEventImpl(@JacksonInject ZeebeObjectMapper objectMapper, @JsonProperty("state") String intent)
    {
        super(objectMapper, RecordMetadata.RecordType.EVENT, RecordMetadata.ValueType.DEPLOYMENT, intent);

        state = DeploymentState.valueOf(intent);
    }

    @Override
    @JsonDeserialize(contentAs = WorkflowDefinitionImpl.class)
    public List<Workflow> getDeployedWorkflows()
    {
        return deployedWorkflows;
    }

    public void setDeployedWorkflows(List<Workflow> deployedWorkflows)
    {
        this.deployedWorkflows = deployedWorkflows;
    }

    public String getDeploymentTopic()
    {
        return deploymentTopic;
    }

    public void setDeploymentTopic(String deploymentTopic)
    {
        this.deploymentTopic = deploymentTopic;
    }

    @Override
    public DeploymentState getState()
    {
        return state;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("DeploymentEvent [topic=");
        builder.append(deploymentTopic);
        builder.append(", resource=");
        builder.append(resources);
        builder.append(", deployedWorkflows=");
        builder.append(deployedWorkflows);
        builder.append("]");
        return builder.toString();
    }

}
