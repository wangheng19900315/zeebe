package io.zeebe.broker.clustering.base.bootstrap;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LOCAL_NODE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.partitionInstallServiceName;
import static io.zeebe.broker.transport.TransportServiceNames.REPLICATION_API_CLIENT_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.clientTransport;

import java.util.List;

import io.zeebe.broker.clustering.base.partitions.PartitionInstallService;
import io.zeebe.broker.clustering.base.raft.config.RaftPersistentConfiguration;
import io.zeebe.broker.clustering.base.raft.config.RaftPersistentConfigurationManager;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.buffer.BufferUtil;

/**
 * Always installed on broker startup: reads configuration of all locally available
 * partitions and starts the corresponding services (raft, logstream, partition ...)
 */
public class BootstrapLocalPartitions implements Service<Object>
{
    private final Injector<RaftPersistentConfigurationManager> configurationManagerInjector = new Injector<>();

    @Override
    public void start(ServiceStartContext startContext)
    {
        final RaftPersistentConfigurationManager configurationManager = configurationManagerInjector.getValue();

        startContext.run(() ->
        {
            final List<RaftPersistentConfiguration> configurations = configurationManager.getConfigurations().join();

            for (RaftPersistentConfiguration configuration : configurations)
            {
                installPartition(startContext, configuration);
            }
        });
    }

    private void installPartition(ServiceStartContext startContext, RaftPersistentConfiguration configuration)
    {
        final String partitionName = String.format("%s-%d", BufferUtil.bufferAsString(configuration.getTopicName()), configuration.getPartitionId());
        final ServiceName<Void> partitionInstallServiceName = partitionInstallServiceName(partitionName);
        final boolean isInternalSystemPartition = configuration.getPartitionId() == Protocol.SYSTEM_PARTITION;

        final PartitionInstallService partitionInstallService = new PartitionInstallService(configuration, isInternalSystemPartition);

        startContext.createService(partitionInstallServiceName, partitionInstallService)
            .dependency(LOCAL_NODE, partitionInstallService.getLocalNodeInjector())
            .dependency(clientTransport(REPLICATION_API_CLIENT_NAME), partitionInstallService.getClientTransportInjector())
            .install();
    }

    @Override
    public Object get()
    {
        return null;
    }

    public Injector<RaftPersistentConfigurationManager> getConfigurationManagerInjector()
    {
        return configurationManagerInjector;
    }
}
