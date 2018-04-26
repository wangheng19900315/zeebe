package io.zeebe.client.impl;

import io.zeebe.client.api.clients.SubscriptionClient;
import io.zeebe.client.api.subscription.JobSubscriptionBuilderStep1;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1;
import io.zeebe.client.subscription.JobSubcriptionBuilder;

public class SubscriptionClientImpl implements SubscriptionClient
{
    private final TopicClientImpl client;

    public SubscriptionClientImpl(TopicClientImpl client)
    {
        this.client = client;
    }

    @Override
    public TopicSubscriptionBuilderStep1 newTopicSubscription()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JobSubscriptionBuilderStep1 newJobSubscription()
    {
        return new JobSubcriptionBuilder(client);
    }

}
