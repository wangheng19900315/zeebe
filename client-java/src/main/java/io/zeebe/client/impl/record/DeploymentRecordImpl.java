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
package io.zeebe.client.impl.record;

import java.util.List;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.client.api.commands.DeploymentResource;
import io.zeebe.client.api.record.*;
import io.zeebe.client.event.impl.RecordImpl;

public abstract class DeploymentRecordImpl extends RecordImpl implements DeploymentRecord
{
    @JsonProperty("topicName")
    private String deploymentTopic;

    private List<DeploymentResource> resources;

    @JsonCreator
    public DeploymentRecordImpl(@JacksonInject ZeebeObjectMapper objectMapper, RecordMetadata.RecordType recordType)
    {
        super(objectMapper, recordType, RecordMetadata.ValueType.DEPLOYMENT);
    }

    @Override
    @JsonDeserialize(contentAs = DeploymentRecordImpl.class)
    public List<DeploymentResource> getResources()
    {
        return resources;
    }

    public void setResources(List<DeploymentResource> resources)
    {
        this.resources = resources;
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

}
