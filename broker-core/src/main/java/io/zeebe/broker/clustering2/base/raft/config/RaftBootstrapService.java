package io.zeebe.broker.clustering2.base.raft.config;

import java.util.List;

import io.zeebe.servicecontainer.*;

/**
 * Starts all locally available Rafts on broker start
 *
 */
public class RaftBootstrapService implements Service<Object>
{
    private final Injector<RaftConfigurationManager> configurationManagerInjector = new Injector<>();

    @Override
    public void start(ServiceStartContext startContext)
    {
        final RaftConfigurationManager configurationManager = configurationManagerInjector.getValue();

        startContext.run(() ->
        {
            final List<BrokerRaftPersistentStorage> configurations = configurationManager.getConfigurations().join();

            for (BrokerRaftPersistentStorage configuration : configurations)
            {

            }
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {

    }

    @Override
    public Object get()
    {
        return null;
    }

    public Injector<RaftConfigurationManager> getConfigurationManagerInjector()
    {
        return configurationManagerInjector;
    }
}
