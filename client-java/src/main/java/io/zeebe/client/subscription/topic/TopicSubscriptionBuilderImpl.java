package io.zeebe.client.subscription.topic;

import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1;
import io.zeebe.client.impl.TopicClientImpl;

public class TopicSubscriptionBuilderImpl implements TopicSubscriptionBuilderStep1
{
    private final TopicClientImpl client;

    public TopicSubscriptionBuilderImpl(TopicClientImpl client)
    {
        this.client = client;
    }

    @Override
    public ManagedTopicSubscriptionBuilderStep2 managed()
    {
        return new ManagedTopicSubscriptionBuilderImpl(client.getTopic(), client.getSubscriptionManager(), client.getObjectMapper(),
                client.getConfiguration().getTopicSubscriptionPrefetchCapacity());
    }

    @Override
    public PollableEventSubscriptionBuilderStep2 pollable()
    {
        // TODO Auto-generated method stub
        return null;
    }

}
