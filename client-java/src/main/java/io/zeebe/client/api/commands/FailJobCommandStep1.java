package io.zeebe.client.api.commands;

import io.zeebe.client.api.events.JobEvent;

public interface FailJobCommandStep1
{
    /**
     * Set the event of the job to mark as failed.
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
    FailJobCommandStep2 event(JobEvent event);

    interface FailJobCommandStep2
    {
        /**
         * Set the remaining retries of this job.
         * <p>
         * If the retries are greater than zero then this job will be picked up
         * again by a job subscription. Otherwise, an incident is created for
         * this job.
         *
         * @param remaingRetries
         *            the remaining retries of this job (e.g.
         *            "jobEvent.getRetries() - 1")
         *
         * @return the builder for this command. Call {@link #send()} to
         *         complete the command and send it to the broker.
         */
        FailJobCommandStep3 retries(int remaingRetries);
    }

    interface FailJobCommandStep3 extends FinalCommandStep<JobEvent>
    {
        // the place for new optional parameters
    }

}
