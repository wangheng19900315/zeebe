package io.zeebe.client.impl;

import io.zeebe.client.api.clients.*;
import io.zeebe.client.workflow.WorkflowsClientImpl;

public class TopicClientImpl implements TopicClient
{
    private final ZeebeClientImpl client;
    private final String topic;

    public TopicClientImpl(ZeebeClientImpl client, String topic)
    {
        this.client = client;
        this.topic = topic;
    }

    @Override
    public WorkflowClient workflowClient()
    {
        return new WorkflowsClientImpl(this);
    }

    @Override
    public JobClient jobClient()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SubscriptionClient subscriptionClient()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public String getTopic()
    {
        return topic;
    }

    public RequestManager getCommandManager()
    {
        return client.getCommandManager();
    }

    public ZeebeObjectMapper getObjectMapper()
    {
        return client.getObjectMapper();
    }

}
