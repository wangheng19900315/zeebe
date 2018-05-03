package io.zeebe.broker.clustering.base.snapshotrepl;

import io.zeebe.clustering.management.*;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class RequestSnapshotMessage implements BufferReader, BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final RequestSnapshotMessageEncoder bodyEncoder = new RequestSnapshotMessageEncoder();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final RequestSnapshotMessageDecoder bodyDecoder = new RequestSnapshotMessageDecoder();

    protected int partitionId = BeginSnapshotMessageEncoder.partitionIdNullValue();
    protected long position = BeginSnapshotMessageEncoder.positionNullValue();

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
            bodyEncoder.sbeBlockLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
            .partitionId(partitionId)
            .position(position);
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer,
            offset,
            headerDecoder.blockLength(),
            headerDecoder.version());

        partitionId = bodyDecoder.partitionId();
        position = bodyDecoder.position();
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public RequestSnapshotMessage setPartitionId(int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public long getPosition()
    {
        return position;
    }

    public RequestSnapshotMessage setPosition(long position)
    {
        this.position = position;
        return this;
    }
}
