package io.zeebe.broker.clustering.base.bootstrap;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.PartitionInstallService;
import io.zeebe.broker.clustering.base.raft.config.RaftPersistentConfiguration;
import io.zeebe.broker.clustering.base.raft.config.RaftPersistentConfigurationManager;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import org.slf4j.Logger;

import static io.zeebe.broker.transport.TransportServiceNames.*;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.*;

import java.util.Collections;
import java.util.List;

/**
 * Used to create the system topic (more precisely the single partition of the system topic).
 * Checks if the partition does already exist locally.
 *<p>
 * When operating as a standalone broker, this service is always installed on startup.
 * When operating as a multi-node cluster, this service is installed on the bootstrap node after the
 * expected count of nodes has joined the cluster.
 */
public class BootstrapSystemTopic extends Actor implements Service<Void>
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final Injector<RaftPersistentConfigurationManager> raftPersistentConfigurationManagerInjector = new Injector<>();

    private final int replicationFactor;
    private RaftPersistentConfigurationManager configurationManager;
    private ServiceStartContext serviceStartContext;

    public BootstrapSystemTopic(int replicationFactor)
    {
        this.replicationFactor = replicationFactor;
    }

    public Injector<RaftPersistentConfigurationManager> getRaftPersistentConfigurationManagerInjector()
    {
        return raftPersistentConfigurationManagerInjector;
    }


    @Override
    public void start(ServiceStartContext startContext)
    {
        serviceStartContext = startContext;
        configurationManager = raftPersistentConfigurationManagerInjector.getValue();
        startContext.async(startContext.getScheduler().submitActor(this));
    }

    @Override
    protected void onActorStarted()
    {
        final ActorFuture<List<RaftPersistentConfiguration>> configurationsFuture = configurationManager.getConfigurations();
        actor.runOnCompletion(configurationsFuture, (configurations, throwable) ->
        {
            if (throwable == null)
            {
                final long count = configurations.stream()
                    .filter(configuration -> configuration.getPartitionId() == Protocol.SYSTEM_PARTITION)
                    .count();

                if (count == 0)
                {
                    installSystemPartition();
                }
                else
                {
                    LOG.debug("Internal system partition already present. Not bootstrapping it.");
                }
            }
            else
            {
                throw new RuntimeException(throwable);
            }
        });
    }

    private void installSystemPartition()
    {
        final ActorFuture<RaftPersistentConfiguration> configurationFuture =
            configurationManager.createConfiguration(Protocol.SYSTEM_TOPIC_BUF, Protocol.SYSTEM_PARTITION, replicationFactor, Collections.emptyList());

        LOG.info("Boostrapping internal system topic '{}' with replication factor {}.", Protocol.SYSTEM_TOPIC, replicationFactor);

        actor.runOnCompletion(configurationFuture, (configuration, throwable) ->
        {
            if (throwable != null)
            {
                throw new RuntimeException(throwable);
            }
            else
            {
                final String partitionName = String.format("%s-%d", Protocol.SYSTEM_TOPIC, Protocol.SYSTEM_PARTITION);
                final ServiceName<Void> partitionInstallServiceName = partitionInstallServiceName(partitionName);
                final PartitionInstallService partitionInstallService = new PartitionInstallService(configuration, true);

                final ActorFuture<Void> partitionInstallFuture = serviceStartContext.createService(partitionInstallServiceName, partitionInstallService)
                    .dependency(LOCAL_NODE, partitionInstallService.getLocalNodeInjector())
                    .dependency(clientTransport(REPLICATION_API_CLIENT_NAME), partitionInstallService.getClientTransportInjector())
                    .install();

                actor.runOnCompletion(partitionInstallFuture, (aVoid, installThrowable) ->
                {
                    if (installThrowable != null)
                    {
                        configurationManager.deleteConfiguration(configuration);
                        throw new RuntimeException(installThrowable);
                    }
                });
            }
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(actor.close());
    }

    @Override
    public Void get()
    {
        return null;
    }
}
