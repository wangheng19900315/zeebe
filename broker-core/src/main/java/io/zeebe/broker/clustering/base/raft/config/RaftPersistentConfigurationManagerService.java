package io.zeebe.broker.clustering.base.raft.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import io.zeebe.broker.logstreams.cfg.LogStreamsCfg;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.servicecontainer.*;

public class RaftPersistentConfigurationManagerService implements Service<RaftPersistentConfigurationManager>
{
    private final TransportComponentCfg config;
    private final LogStreamsCfg logStreamsCfg;
    private RaftPersistentConfigurationManager service;

    public RaftPersistentConfigurationManagerService(TransportComponentCfg config, LogStreamsCfg logStreamsCfg)
    {
        this.config = config;
        this.logStreamsCfg = logStreamsCfg;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final File configDirectory = new File(config.management.getDirectory());

        if (!configDirectory.exists())
        {
            try
            {
                configDirectory.getParentFile().mkdirs();
                Files.createDirectory(configDirectory.toPath());
            }
            catch (final IOException e)
            {
                throw new RuntimeException("Unable to create directory " + configDirectory, e);
            }
        }

        service = new RaftPersistentConfigurationManager(config.management.getDirectory(), logStreamsCfg);

        startContext.async(startContext.getScheduler().submitActor(service));
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(service.close());
    }

    @Override
    public RaftPersistentConfigurationManager get()
    {
        return service;
    }

}
