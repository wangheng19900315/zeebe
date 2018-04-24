package io.zeebe.client.api.subscription;

import java.time.Duration;

public interface PollableJobSubscriptionBuilderStep1
{

    /**
     * Sets the work item type to subscribe to. Must not be null.
     */
    PollableJobSubscriptionBuilderStep2 workItemType(String workItemType);

    interface PollableJobSubscriptionBuilderStep2
    {
        /**
         * Sets the lock duration for which subscribed tasks will be
         * exclusively locked for this task client.
         *
         * @param lockDuration in milliseconds
         */
        PollableJobSubscriptionBuilderStep3 lockTime(long lockDuration);

        /**
         * Sets the lock duration for which subscribed tasks will be
         * exclusively locked for this task client.
         *
         * @param lockDuration duration for which tasks are being locked
         */
        PollableJobSubscriptionBuilderStep3 lockTime(Duration lockDuration);
    }

    interface PollableJobSubscriptionBuilderStep3
    {
        /**
         * Sets the owner for which subscripted tasks will be exclusively locked.
         *
         * @param lockOwner owner of which tasks are being locked
         */
        PollableJobSubscriptionBuilderStep4 lockOwner(String lockOwner);
    }

    interface PollableJobSubscriptionBuilderStep4
    {
        /**
         * Sets the number of work items which will be locked at the same time.
         *
         * @param numJobs number of locked tasks
         */
        PollableJobSubscriptionBuilderStep4 fetchSize(int numJobs);

        /**
         * Opens a new {@link PollableTaskSubscription}. Begins receiving
         * tasks from that point on.
         */
        PollableJobSubscription open();
    }
}
