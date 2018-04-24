package io.zeebe.client.api.commands;

import io.zeebe.client.api.record.WorkflowRecord;

public interface WorkflowCommand extends WorkflowRecord
{
    WorkflowCommandName getName();

    enum WorkflowCommandName
    {
        CREATE,
        DELETE,
    }

}
