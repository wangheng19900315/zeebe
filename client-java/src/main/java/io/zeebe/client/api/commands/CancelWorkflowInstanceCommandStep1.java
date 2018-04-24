package io.zeebe.client.api.commands;

import io.zeebe.client.api.events.WorkflowInstanceEvent;

public interface CancelWorkflowInstanceCommandStep1
{
    /**
     * Set the event of the workflow instance to canceled.
     * <p>
     * The workflow instance is specified by the given event. The event must be
     * the latest event of the workflow instance to ensure that the command is
     * based on the latest state of the workflow instance. If it's not the
     * latest one then the command is rejected.
     *
     * @param event
     *            the latest workflow instance event
     *
     * @return the builder for this command. Call {@link #send()} to
     *         complete the command and send it to the broker.
     */
    CancelWorkflowInstanceCommandStep2 event(WorkflowInstanceEvent event);

    interface CancelWorkflowInstanceCommandStep2 extends FinalCommandStep<WorkflowInstanceEvent>
    {
        // the place for new optional parameters
    }
}
