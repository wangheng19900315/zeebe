package io.zeebe.client.api.subscription;

import io.zeebe.client.api.events.WorkflowEvent;

@FunctionalInterface
public interface WorkflowEventHandler
{

    void onWorkflowEvent(WorkflowEvent workflowEvent);
}
