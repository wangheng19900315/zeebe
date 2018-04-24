package io.zeebe.client.api.commands;

public interface CreateTopicCommandStep1
{
    /**
     * Set the name of the topic to create to. The name must be unique within
     * the broker/cluster.
     *
     * @param topicName
     *            the unique name of the new topic
     *
     * @return the builder for this command
     */
    CreateTopicCommandStep2 name(String topicName);

    interface CreateTopicCommandStep2
    {
        /**
         * Set the number of partitions to create for this topic.
         *
         * @param partitions
         *            the number of partitions for this topic
         *
         * @return the builder for this command. Call {@link #send()} to
         *         complete the command and send it to the broker.
         */
        CreateTopicCommandStep3 partitions(int partitions);
    }

    interface CreateTopicCommandStep3 extends FinalCommandStep<Void>
    {
        // the place for new optional parameters
    }

}
