package io.zeebe.client.api.subscription;

import io.zeebe.client.api.record.RecordMetadata;

public interface TopicSubscriptionBuilderStep1
{

    /**
     * This is a <i>managed</i> subscription. The given handlers are
     * automatically invoked whenever new event/command is available. Choose a
     * managed subscription when the client should invoke the handler in its
     * thread pool.
     *
     * <pre>
     * TopicSubscription subscription = subscriptionClient
     *  .newTopicSubscription()
     *  .managed()
     *  .name("my-app")
     *  .workflowInstanceEventHandler(wfEventHandler)
     *  .open();
     * </pre>
     *
     * @return the builder for a managed subscription
     */
    ManagedTopicSubscriptionBuilderStep2 managed();

    /**
     * This is a <i>pollable</i> subscription. Call
     * {@link PollableTopicSubscription#poll(RecordHandler)} repeatedly to
     * manually trigger the invocation for available events/command. Choose a
     * pollable subscription when you need control in which thread the handler
     * is invoked.
     *
     * <pre>
     * PollableTopicSubscription subscription = subscriptionClient
     *  .newTopicSubscription()
     *  .pollable()
     *  .name("my-app")
     *  .open();
     *
     * // every x seconds call
     * subscription.poll(handler);
     * </pre>
     *
     * @return the builder for a pollable subscription
     */
    PollableTopicSubscriptionBuilderStep2 pollable();

    interface ManagedTopicSubscriptionBuilderStep2
    {
        /**
         * Set the (unique) name of the subscription.
         * <p>
         * The name is used by the broker to persist the acknowledged
         * subscription's position. When a subscription with the same name is
         * (re-)opened then the broker resumes the subscription at the last
         * acknowledged position and starts publishing with the next
         * event/command.
         * <p>
         * The initial position of the subscription can be defined by calling
         * <code>startAtHeadOfTopic()</code>, <code>startAtTailOfTopic()</code>
         * or <code>startAtPosition()</code>. If the subscription has already an
         * acknowledged position then these calls are ignored and the
         * subscription resumes at the acknowledged position. Use
         * <code>forcedStart()</code> to enforce starting at the supplied start
         * position.
         * <p>
         * Example:
         * <pre>
         * TopicSubscription subscription = subscriptionClient
         *  .newTopicSubscription()
         *  .managed()
         *  .name("my-app")
         *  .workflowInstanceEventHandler(wfEventHandler)
         *  .startAtPosition(0)
         *  .open();
         * </pre>
         *
         * When executed the first time, this snippet creates a new subscription
         * beginning at position 0. When executed a second time, this snippet
         * creates a new subscription beginning at the position at which the
         * first subscription left off.
         *
         * @param name
         *            the (unique) name of the subscription
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep3 name(String name);
    }

    interface ManagedTopicSubscriptionBuilderStep3
    {
        /**
         * Register a handler that processes all types of topic records.
         *
         * @param handler
         *            the handler to process all types of topic records
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 recordHandler(RecordHandler handler);

        /**
         * Register a handler that processes all job events.
         *
         * @param handler
         *            the handler to process all job events
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 jobEventHandler(JobEventHandler handler);

        /**
         * Register a handler that processes all job commands.
         *
         * @param handler
         *            the handler to process all job commands
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 jobCommandHandler(JobCommandHandler handler);

        /**
         * Register a handler that processes all workflow instance events.
         *
         * @param handler
         *            the handler to process all workflow instance events
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 workflowInstanceEventHandler(WorkflowInstanceEventHandler handler);

        /**
         * Register a handler that processes all workflow instance commands.
         *
         * @param handler
         *            the handler to process all workflow instance commands
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 workflowInstanceCommandHandler(WorkflowInstanceCommandHandler handler);

        /**
         * Register a handler that processes all workflow events.
         *
         * @param handler
         *            the handler to process all workflow events
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 workflowEventHandler(WorkflowEventHandler handler);

        /**
         * Register a handler that processes all workflow commands.
         *
         * @param handler
         *            the handler to process all workflow commands
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 workflowCommandHandler(WorkflowCommandHandler handler);

        /**
         * Register a handler that processes all incident events.
         *
         * @param handler
         *            the handler to process all incident events
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 incidentEventHandler(IncidentEventHandler handler);

        /**
         * Register a handler that processes all incident commands.
         *
         * @param handler
         *            the handler to process all incident commands
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 incidentCommandHandler(IncidentCommandHandler handler);

        /**
         * Register a handler that processes all raft events.
         *
         * @param handler
         *            the handler to process all raft events
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 raftEventHandler(RaftEventHandler handler);
    }

    interface ManagedTopicSubscriptionBuilderStep4 extends ManagedTopicSubscriptionBuilderStep3
    {
        /**
         * Set the initial position of a partition to start publishing from. Can
         * be called multiple times for different partitions.
         * <p>
         * If the subscription has already an acknowledged position then this
         * call is ignored. Call <code>forcedStart()</code> to enforce starting
         * at the supplied position.
         * <p>
         * A <code>position</code> greater than the current tail position of the
         * partition is equivalent to starting at the tail position. In this
         * case, events with a lower position than the supplied position may be
         * received.
         *
         * @param partitionId
         *            the partition the start position applies to. Corresponds
         *            to the partition ID accessible via
         *            {@link RecordMetadata#getPartitionId()}.
         * @param position
         *            the position in the partition at which to start publishing
         *            events from
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 startAtPosition(int partitionId, long position);

        /**
         * Start publishing at the current tails of all of the partitions. Can
         * be overridden per partition by calling
         * {@link #startAtPosition(int, long)}.
         * <p>
         * If the subscription has already an acknowledged position then this
         * call is ignored. Call <code>forcedStart()</code> to enforce starting
         * at the tail.
         * <p>
         * It is guaranteed that this subscription does not receive any
         * event/command that was receivable before this subscription is opened.
         *
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 startAtTailOfTopic();

        /**
         * Start publishing at the head (i.e. the begin) of all of the
         * partitions. Can be overridden per partition by calling
         * {@link #startAtPosition(int, long)}.
         * <p>
         * If the subscription has already an acknowledged position then this
         * call is ignored. Call <code>forcedStart()</code> to enforce starting
         * at the begin.
         *
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 startAtHeadOfTopic();

        /**
         * Force the subscription to start at the given position.
         * <p>
         * This discards the persisted state of the acknowledged position in the
         * broker.
         *
         * @return the builder for this subscription
         */
        ManagedTopicSubscriptionBuilderStep4 forcedStart();

        /**
         * Open the subscription and start to process available events/commands.
         *
         * @return the subscription
         */
        TopicSubscription open();
    }

    interface PollableTopicSubscriptionBuilderStep2
    {
        /**
         * Set the name of the subscription.
         * <p>
         * The name is used by the broker to persist the acknowledged
         * subscription's position. When a subscription with the same name is
         * (re-)opened then the broker resumes the subscription at the last
         * acknowledged position and starts publishing with the next
         * event/command.
         * <p>
         * The initial position of the subscription can be defined by calling
         * <code>startAtHeadOfTopic()</code>, <code>startAtTailOfTopic()</code>
         * or <code>startAtPosition()</code>. If the subscription has already an
         * acknowledged position then these calls are ignored and the
         * subscription resumes at the acknowledged position. Use
         * <code>forcedStart()</code> to enforce starting at the supplied start
         * position.
         * <p>
         * Example:
         * <pre>
         * PollableTopicSubscription subscription = subscriptionClient
         *  .newTopicSubscription()
         *  .managed()
         *  .name("my-app")
         *  .startAtPosition(0)
         *  .open();
         * </pre>
         *
         * When executed the first time, this snippet creates a new subscription
         * beginning at position 0. When executed a second time, this snippet
         * creates a new subscription beginning at the position at which the
         * first subscription left off.
         *
         * @param name
         *            the (unique) name of the subscription
         * @return the builder for this subscription
         */
        PollableTopicSubscriptionBuilderStep3 name(String name);
    }

    interface PollableTopicSubscriptionBuilderStep3
    {
        /**
         * Set the initial position of a partition to start publishing from. Can
         * be called multiple times for different partitions.
         * <p>
         * If the subscription has already an acknowledged position then this
         * call is ignored. Call <code>forcedStart()</code> to enforce starting
         * at the supplied position.
         * <p>
         * A <code>position</code> greater than the current tail position of the
         * partition is equivalent to starting at the tail position. In this
         * case, events with a lower position than the supplied position may be
         * received.
         *
         * @param partitionId
         *            the partition the start position applies to. Corresponds
         *            to the partition ID accessible via
         *            {@link RecordMetadata#getPartitionId()}.
         * @param position
         *            the position in the partition at which to start publishing
         *            events from
         * @return the builder for this subscription
         */
        PollableTopicSubscriptionBuilderStep3 startAtPosition(int partitionId, long position);

        /**
         * Start publishing at the current tails of all of the partitions. Can
         * be overridden per partition by calling
         * {@link #startAtPosition(int, long)}.
         * <p>
         * If the subscription has already an acknowledged position then this
         * call is ignored. Call <code>forcedStart()</code> to enforce starting
         * at the tail.
         * <p>
         * It is guaranteed that this subscription does not receive any
         * event/command that was receivable before this subscription is opened.
         *
         * @return the builder for this subscription
         */
        PollableTopicSubscriptionBuilderStep3 startAtTailOfTopic();

        /**
         * Start publishing at the head (i.e. the begin) of all of the
         * partitions. Can be overridden per partition by calling
         * {@link #startAtPosition(int, long)}.
         * <p>
         * If the subscription has already an acknowledged position then this
         * call is ignored. Call <code>forcedStart()</code> to enforce starting
         * at the begin.
         *
         * @return the builder for this subscription
         */
        PollableTopicSubscriptionBuilderStep3 startAtHeadOfTopic();

        /**
         * Force the subscription to start at the given position.
         * <p>
         * This discards the persisted state of the acknowledged position in the
         * broker.
         *
         * @return the builder for this subscription
         */
        PollableTopicSubscriptionBuilderStep3 forcedStart();

        /**
         * Open the subscription. Call
         * {@link PollableTopicSubscription#poll(RecordHandler)} to process
         * available events/commands.
         *
         * @return the subscription
         */
        PollableTopicSubscription open();
    }

}
