/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.processor;

import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ServerOutput;

public class TypedResponseWriterImpl implements TypedResponseWriter
{
    protected CommandResponseWriter writer;
    protected int partitionId;

    public TypedResponseWriterImpl(ServerOutput output, int partitionId)
    {
        this.writer = new CommandResponseWriter(output);
        this.partitionId = partitionId;
    }

    @Override
    public boolean writeRejection(TypedRecord<?> record)
    {
        return write(RecordType.COMMAND_REJECTION, record.getMetadata().getIntent(), record);
    }

    @Override
    public boolean writeEvent(Intent intent, TypedRecord<?> record)
    {
        return write(RecordType.EVENT, intent, record);
    }

    private boolean write(RecordType type, Intent intent, TypedRecord<?> record)
    {
        final RecordMetadata metadata = record.getMetadata();

        return writer
            .partitionId(partitionId)
            .position(0) // TODO: this depends on the value of written event => https://github.com/zeebe-io/zeebe/issues/374
            .key(record.getKey())
            .intent(intent)
            .recordType(type)
            .valueWriter(record.getValue())
            .tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId());
    }

}
