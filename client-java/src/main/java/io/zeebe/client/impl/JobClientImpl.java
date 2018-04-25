package io.zeebe.client.impl;

import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.job.CreateJobCommandImpl;

public class JobClientImpl implements JobClient
{
    private final TopicClientImpl client;

    public JobClientImpl(TopicClientImpl client)
    {
        this.client = client;
    }

    @Override
    public CreateJobCommandStep1 newCreateCommand()
    {
        return new CreateJobCommandImpl(client.getCommandManager(), client.getMsgPackConverter(), client.getTopic());
    }

    @Override
    public CompleteJobCommandStep1 newCompleteCommand(JobEvent event)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public FailJobCommandStep1 newFailCommand(JobEvent event)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public UpdateRetriesJobCommandBuilderStep1 newUpdateRetriesCommand(JobEvent event)
    {
        // TODO Auto-generated method stub
        return null;
    }

}
