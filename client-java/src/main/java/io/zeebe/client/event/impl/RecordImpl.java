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
package io.zeebe.client.event.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.client.api.record.*;

public abstract class RecordImpl implements Record
{
    private final RecordMetadataImpl metadata = new RecordMetadataImpl();
    private final ZeebeObjectMapper objectMapper;

    public RecordImpl(ZeebeObjectMapper objectMapper, RecordMetadata.RecordType recordType, RecordMetadata.ValueType valueType, String intent)
    {
        this.metadata.setIntent(intent);
        this.metadata.setRecordType(recordType);
        this.metadata.setValueType(valueType);
        this.objectMapper = objectMapper;
    }

    public RecordImpl(RecordImpl baseEvent, String intent)
    {
        updateMetadata(baseEvent.metadata);
        this.metadata.setIntent(intent);
        this.objectMapper = baseEvent.objectMapper;
    }

    @Override
    @JsonIgnore
    public RecordMetadataImpl getMetadata()
    {
        return metadata;
    }

    public void setTopicName(String name)
    {
        this.metadata.setTopicName(name);
    }

    public void setPartitionId(int id)
    {
        this.metadata.setPartitionId(id);
    }

    public void setKey(long key)
    {
        this.metadata.setKey(key);
    }

    public void setEventPosition(long position)
    {
        this.metadata.setPosition(position);
    }

    public boolean hasValidPartitionId()
    {
        return this.metadata.hasPartitionId();
    }

    public void updateMetadata(RecordMetadata other)
    {
        this.metadata.setKey(other.getKey());
        this.metadata.setPosition(other.getPosition());
        this.metadata.setTopicName(other.getTopicName());
        this.metadata.setPartitionId(other.getPartitionId());
        this.metadata.setRecordType(other.getRecordType());
        this.metadata.setValueType(other.getValueType());
        this.metadata.setIntent(other.getIntent());
    }

    @Override
    public String toJson()
    {
        return objectMapper.toJson(this);
    }

}
