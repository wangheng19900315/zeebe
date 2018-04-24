package io.zeebe.client.api.commands;

import java.io.InputStream;

import io.zeebe.client.api.events.JobEvent;

public interface CompleteJobCommandStep1
{
    /**
     * Set the event of the job to complete.
     * <p>
     * The job is specified by the given event. The event must be the latest
     * event of the job to ensure that the command is based on the latest state
     * of the job. If it's not the latest one then the command is rejected.
     *
     * @param event
     *            the latest job event
     *
     * @return the command of this command
     */
    CompleteJobCommandStep2 event(JobEvent event);

    public interface CompleteJobCommandStep2 extends FinalCommandStep<JobEvent>
    {
        /**
         * Set the payload to complete the job with.
         *
         * @param payload
         *            the payload (JSON) as stream
         *
         * @return the builder for this command. Call {@link #send()} to
         *         complete the command and send it to the broker.
         */
        CompleteJobCommandStep2 payload(InputStream payload);

        /**
         * Set the payload to complete the job with.
         *
         * @param payload
         *            the payload (JSON) as String
         *
         * @return the builder for this command. Call {@link #send()} to
         *         complete the command and send it to the broker.
         */
        CompleteJobCommandStep2 payload(String payload);

        /**
         * Complete the job without payload.
         *
         * @return the builder for this command. Call {@link #send()} to
         *         complete the command and send it to the broker.
         */
        CompleteJobCommandStep2 withoutPayload();
    }

}
