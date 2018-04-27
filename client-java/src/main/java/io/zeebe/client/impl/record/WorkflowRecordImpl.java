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

import static java.nio.charset.StandardCharsets.UTF_8;

import io.zeebe.client.api.record.*;

public abstract class WorkflowRecordImpl extends RecordImpl implements WorkflowRecord
{
    private String bpmnProcessId;
    private int version;
    private byte[] bpmnXml;
    private long deploymentKey;

    public WorkflowRecordImpl(ZeebeObjectMapper objectMapper, RecordMetadata.RecordType recordType)
    {
        super(objectMapper, recordType, RecordMetadata.ValueType.WORKFLOW);
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
    public long getDeploymentKey()
    {
        return deploymentKey;
    }

    public void setDeploymentKey(long deploymentKey)
    {
        this.deploymentKey = deploymentKey;
    }

    @Override
    public String getBpmnXml()
    {
        return new String(bpmnXml, UTF_8);
    }

    public void setBpmnXml(byte[] bpmnXml)
    {
        this.bpmnXml = bpmnXml;
    }

}
