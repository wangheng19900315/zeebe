package io.zeebe.client.api.commands;

import java.util.List;

import io.zeebe.client.api.record.DeploymentRecord;

public interface DeploymentCommand extends DeploymentRecord
{
    DeploymentCommandName getName();

    String getDeploymentTopic();

    List<DeploymentResource> getResources();

    enum DeploymentCommandName
    {
        CREATE
    }

}
