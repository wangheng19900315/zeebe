package io.zeebe.client.api.commands;

import java.io.InputStream;

import io.zeebe.client.api.events.WorkflowInstanceEvent;

public interface UpdatePayloadWorkflowInstanceCommandStep1
{
    /**
     * Set the event of the workflow instance to update the payload of.
     * <p>
     * The workflow instance is specified by the given event. The event must be
     * the latest event of the workflow instance to ensure that the command is
     * based on the latest state of the workflow instance. If it's not the
     * latest one then the command is rejected.
     *
     * @param event
     *            the latest workflow instance event
     *
     * @return the builder of this command
     */
    UpdatePayloadWorkflowInstanceCommandStep2 event(WorkflowInstanceEvent event);

    interface UpdatePayloadWorkflowInstanceCommandStep2
    {
        /**
         * Set the new payload of the workflow instance.
         *
         * @param payload
         *            the payload (JSON) as stream
         *
         * @return the builder for this command. Call {@link #send()} to
         *         complete the command and send it to the broker.
         */
        UpdatePayloadWorkflowInstanceCommandStep3 payload(InputStream payload);

        /**
         * Set the new payload of the workflow instance.
         *
         * @param payload
         *            the payload (JSON) as String
         *
         * @return the builder for this command. Call {@link #send()} to
         *         complete the command and send it to the broker.
         */
        UpdatePayloadWorkflowInstanceCommandStep3 payload(String payload);
    }

    interface UpdatePayloadWorkflowInstanceCommandStep3 extends FinalCommandStep<WorkflowInstanceEvent>
    {
        // the place for new optional parameters
    }
}
