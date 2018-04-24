package io.zeebe.client.api.events;

import io.zeebe.client.api.record.JobRecord;

public interface JobEvent extends JobRecord
{
    /**
     * @return the current state
     */
    JobState getState();

    enum JobState
    {
        CREATED,
        LOCKED,
        COMPLETED,
        LOCK_EXPIRED,
        FAILED,
        RETRIES_UPDATED,
        CANCELED,
    }
}
