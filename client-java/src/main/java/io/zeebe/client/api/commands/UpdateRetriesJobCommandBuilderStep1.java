package io.zeebe.client.api.commands;

import io.zeebe.client.api.events.JobEvent;

public interface UpdateRetriesJobCommandBuilderStep1
{
    /**
     * Set the event of the job to update the retries of.
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
    UpdateRetriesJobCommandStep2 event(JobEvent event);

    interface UpdateRetriesJobCommandStep2
    {
        /**
         * Set the retries of this job.
         * <p>
         * If the given retries are greater than zero then this job will be
         * picked up again by a job subscription and a related incident will be
         * marked as resolved.
         *
         * @param retries
         *            the retries of this job
         *
         * @return the builder for this command. Call {@link #send()} to
         *         complete the command and send it to the broker.
         */
        UpdateRetriesJobCommandStep3 retries(int retries);
    }

    interface UpdateRetriesJobCommandStep3 extends FinalCommandStep<JobEvent>
    {
        // the place for new optional parameters
    }
}
