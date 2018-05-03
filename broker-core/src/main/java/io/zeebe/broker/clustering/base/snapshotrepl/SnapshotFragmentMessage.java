package io.zeebe.broker.clustering.base.snapshotrepl;

import io.zeebe.clustering.management.*;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SnapshotFragmentMessage implements BufferReader, BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final SnapshotFragmentEncoder bodyEncoder = new SnapshotFragmentEncoder();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final SnapshotFragmentDecoder bodyDecoder = new SnapshotFragmentDecoder();

    protected int partitionId = SnapshotFragmentDecoder.partitionIdNullValue();
    protected long position = SnapshotFragmentDecoder.positionNullValue();
    protected long offset = SnapshotFragmentDecoder.offsetNullValue();
    protected DirectBuffer data = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
            bodyEncoder.sbeBlockLength() +
            SnapshotFragmentEncoder.dataHeaderLength() +
            data.capacity();
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
            .position(position)
            .offset(offset)
            .putData(data, 0, data.capacity());
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
        offset = bodyDecoder.offset();

        offset += headerDecoder.blockLength();

        final int dataLength = bodyDecoder.dataLength();
        offset += SnapshotFragmentDecoder.dataHeaderLength();

        data.wrap(buffer, offset, dataLength);
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public SnapshotFragmentMessage setPartitionId(int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public long getPosition()
    {
        return position;
    }

    public SnapshotFragmentMessage setPosition(long position)
    {
        this.position = position;
        return this;
    }

    public long getOffset()
    {
        return offset;
    }

    public SnapshotFragmentMessage setOffset(long offset)
    {
        this.offset = offset;
        return this;
    }

    public DirectBuffer getData()
    {
        return data;
    }

    public SnapshotFragmentMessage setData(DirectBuffer data)
    {
        this.data = data;
        return this;
    }
}
