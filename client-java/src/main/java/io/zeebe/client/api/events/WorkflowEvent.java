package io.zeebe.client.api.events;

import io.zeebe.client.api.record.WorkflowRecord;

public interface WorkflowEvent extends WorkflowRecord
{
    /**
     * @return the current state
     */
    WorkflowState getState();

    enum WorkflowState
    {
        CREATED,
        DELETED,
    }

}
