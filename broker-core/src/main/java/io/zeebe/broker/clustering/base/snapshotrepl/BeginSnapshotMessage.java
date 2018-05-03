package io.zeebe.broker.clustering.base.snapshotrepl;

import io.zeebe.clustering.management.*;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class BeginSnapshotMessage implements BufferReader, BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final BeginSnapshotMessageEncoder bodyEncoder = new BeginSnapshotMessageEncoder();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final BeginSnapshotMessageDecoder bodyDecoder = new BeginSnapshotMessageDecoder();

    protected int partitionId = BeginSnapshotMessageEncoder.partitionIdNullValue();
    protected long position = BeginSnapshotMessageEncoder.positionNullValue();
    protected long snapshotLength = BeginSnapshotMessageEncoder.snapshotLengthNullValue();
    protected DirectBuffer snapshotName = new UnsafeBuffer(0, 0);
    protected DirectBuffer checksum = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
            bodyEncoder.sbeBlockLength() +
            BeginSnapshotMessageEncoder.snapshotNameHeaderLength() +
            snapshotName.capacity() +
            BeginSnapshotMessageEncoder.checksumHeaderLength() +
            checksum.capacity();
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
            .snapshotLength(snapshotLength)
            .putSnapshotName(snapshotName, 0, snapshotName.capacity())
            .putChecksum(checksum, 0, checksum.capacity());
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
        snapshotLength = bodyDecoder.snapshotLength();

        offset += headerDecoder.blockLength();

        final int snapshotNameLength = bodyDecoder.snapshotNameLength();
        offset += BeginSnapshotMessageEncoder.snapshotNameHeaderLength();

        snapshotName.wrap(buffer, offset, snapshotNameLength);

        offset += snapshotNameLength;

        final int checksumLength = bodyDecoder.checksumLength();
        offset += BeginSnapshotMessageEncoder.checksumHeaderLength();

        checksum.wrap(buffer, offset, checksumLength);
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public BeginSnapshotMessage setPartitionId(int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public long getPosition()
    {
        return position;
    }

    public BeginSnapshotMessage setPosition(long position)
    {
        this.position = position;
        return this;
    }

    public long getSnapshotLength()
    {
        return snapshotLength;
    }

    public BeginSnapshotMessage setSnapshotLength(long snapshotLength)
    {
        this.snapshotLength = snapshotLength;
        return this;
    }

    public DirectBuffer getSnapshotName()
    {
        return snapshotName;
    }

    public BeginSnapshotMessage setSnapshotName(DirectBuffer snapshotName)
    {
        this.snapshotName = snapshotName;
        return this;
    }

    public DirectBuffer getChecksum()
    {
        return checksum;
    }

    public BeginSnapshotMessage setChecksum(DirectBuffer checksum)
    {
        this.checksum = checksum;
        return this;
    }
}
