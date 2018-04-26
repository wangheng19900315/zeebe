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

import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.impl.record.MsgPackField;

public class GeneralRecordImpl extends RecordImpl
{
    private final MsgPackField content;

    public GeneralRecordImpl(
            final ZeebeObjectMapper objectMapper,
            final MsgPackConverter converter,
            final RecordMetadata.RecordType recordType,
            final RecordMetadata.ValueType valueType,
            final byte[] rawContent)
    {
        super(objectMapper, recordType, valueType);

        this.content = new MsgPackField(converter);
        this.content.setMsgPack(rawContent);
    }

    public byte[] getAsMsgPack()
    {
        return content.getMsgPack();
    }

    @Override
    public String toString()
    {
        return "Record [metadata=" + getMetadata() + ", content=" + toJson() + "]";
    }

    @Override
    protected void mapIntent(String intent)
    {
        // do nothing
    }

}
