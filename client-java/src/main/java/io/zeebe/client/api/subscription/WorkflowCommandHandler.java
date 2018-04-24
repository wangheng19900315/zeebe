package io.zeebe.client.api.subscription;

import io.zeebe.client.api.commands.WorkflowCommand;

@FunctionalInterface
public interface WorkflowCommandHandler
{

    void onWorkflowCommand(WorkflowCommand workflowCommand);
    default void onWorkflowCommandRejection(WorkflowCommand workflowCommand)
    {
    };
}
