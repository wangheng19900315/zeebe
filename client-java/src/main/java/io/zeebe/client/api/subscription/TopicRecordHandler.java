package io.zeebe.client.api.subscription;

import io.zeebe.client.api.record.Record;

@FunctionalInterface
public interface TopicRecordHandler
{

    void onEntry(Record entry);
}
