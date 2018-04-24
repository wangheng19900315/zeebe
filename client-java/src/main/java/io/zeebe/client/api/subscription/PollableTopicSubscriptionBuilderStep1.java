package io.zeebe.client.api.subscription;

public interface PollableTopicSubscriptionBuilderStep1
{

    // TODO: check javadoc

    /**
     * <p>Sets the name of a subscription. The name is used by the broker to record and persist the
     * subscription's position. When a subscription is reopened, this state is used to resume
     * the subscription at the previous position. In this case, methods like {@link #startAtPosition(long)}
     * have no effect (the subscription has already started before).
     *
     * <p>Example:
     * <pre>
     * TopicSubscriptionBuilder builder = ...;
     * builder
     *   .startAtPosition(0)
     *   .name("app1")
     *   ...
     *   .open();
     * </pre>
     * When executed the first time, this snippet creates a new subscription beginning at position 0.
     * When executed a second time, this snippet creates a new subscription beginning at the position
     * at which the first subscription left off.
     *
     * <p>Use {@link #forcedStart()} to enforce starting at the supplied start position.
     *
     * <p>This parameter is required.
     *
     * @param name the name of the subscription. must be unique for the addressed topic
     * @return this builder
     */
    PollableTopicSubscriptionBuilderStep2 name(String name);

    interface PollableTopicSubscriptionBuilderStep2
    {
        /**
         * <p>Defines the position at which to start receiving events from a specific partition.
         * A <code>position</code> greater than the current tail position
         * of the partition is equivalent to starting at the tail position. In this case,
         * events with a lower position than the supplied position may be received.
         *
         * @param partitionId the partition the start position applies to. Corresponds to the partition ID
         *   accessible via {@link EventMetadata#getPartitionId()}.
         * @param position the position in the topic at which to start receiving events from
         * @return this builder
         */
        PollableTopicSubscriptionBuilderStep2 startAtPosition(int partitionId, long position);

        /**
         * Forces the subscription to start over, discarding any
         * state previously persisted in the broker. The next received events are based
         * on the configured start position.
         *
         * @return this builder
         */
        PollableTopicSubscriptionBuilderStep2 forcedStart();

        /**
         * <p>Starts subscribing at the current tails of all of the partitions belonging to the topic.
         * In particular, it is guaranteed that this subscription does not receive any event that
         * was receivable before this subscription is opened.
         *
         * <p>Start position can be overridden per partition via {@link #startAtPosition(int, long)}.
         *
         * @return this builder
         */
        PollableTopicSubscriptionBuilderStep2 startAtTailOfTopic();

        /**
         * Same as invoking {@link #startAtTailOfTopic} but subscribes at the beginning of all partitions.
         *
         * @return this builder
         */
        PollableTopicSubscriptionBuilderStep2 startAtHeadOfTopic();

        PollableTopicSubscription open();
    }
}
