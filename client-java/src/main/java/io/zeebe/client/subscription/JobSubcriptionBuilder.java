package io.zeebe.client.subscription;

import io.zeebe.client.api.subscription.JobSubscriptionBuilderStep1;
import io.zeebe.client.impl.TopicClientImpl;

public class JobSubcriptionBuilder implements JobSubscriptionBuilderStep1
{
    private final TopicClientImpl client;

    public JobSubcriptionBuilder(TopicClientImpl client)
    {
        this.client = client;
    }

    @Override
    public ManagedJobSubscriptionBuilderStep2 managed()
    {
        return new ManagedJobSubscriptionBuilderImpl(client.getTopic(), client.getSubscriptionManager());
    }

    @Override
    public PollableJobSubscriptionBuilderStep2 pollable()
    {
        return new PollableJobSubscriptionBuilderImpl(client.getTopic(), client.getSubscriptionManager());
    }

}
