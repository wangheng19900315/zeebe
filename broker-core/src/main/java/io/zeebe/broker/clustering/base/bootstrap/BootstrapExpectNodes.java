package io.zeebe.broker.clustering.base.bootstrap;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.topology.*;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ScheduledTimer;
import org.slf4j.Logger;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.*;

import java.time.Duration;

/**
 * Service implementing the "-bootstrap-expect" parameter on startup:
 * Waits for the specified number of nodes to join the cluster and then bootstraps
 * the system topic with the specified replication factor.
 */
public class BootstrapExpectNodes extends Actor implements Service<Void>, TopologyMemberListener
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
    private TopologyManager topologyManager;

    private final int replicationFactor;
    private final int countOfExpectedNodes;
    private int nodeCount;

    private ServiceStartContext serviceStartContext;
    private ScheduledTimer loggerTimer;

    public BootstrapExpectNodes(int replicationFactor, int countOfExpectedNodes)
    {
        this.replicationFactor = replicationFactor;
        this.countOfExpectedNodes = countOfExpectedNodes;
        this.nodeCount = 0;
    }

    public Injector<TopologyManager> getTopologyManagerInjector()
    {
        return topologyManagerInjector;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        this.serviceStartContext = startContext;
        this.topologyManager = topologyManagerInjector.getValue();

        startContext.async(startContext.getScheduler().submitActor(this));
    }

    @Override
    protected void onActorStarted()
    {
        // register listener
        topologyManager.addTopologyMemberListener(this);

        loggerTimer = actor.runAtFixedRate(Duration.ofSeconds(5), () ->
        {
            LOG.info("Cluster bootstrap: Waiting for nodes, expecting {} got {}.", countOfExpectedNodes, nodeCount);
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

    @Override
    public void onMemberAdded(Topology.NodeInfo memberInfo, Topology topology)
    {
        actor.run(() ->
        {
            nodeCount++;

            if (nodeCount >= countOfExpectedNodes)
            {
                loggerTimer.cancel();
                topologyManager.removeTopologyMemberListener(this);

                installSystemTopicBootstrapService();
                actor.close();
            }
        });
    }

    @Override
    public void onMemberRemoved(Topology.NodeInfo memberInfo, Topology topology)
    {
        actor.run(() -> nodeCount--);
    }

    private void installSystemTopicBootstrapService()
    {
        final BootstrapSystemTopic systemPartitionBootstrapService = new BootstrapSystemTopic(replicationFactor);

        serviceStartContext.createService(SYSTEM_PARTITION_BOOTSTRAP_SERVICE_NAME, systemPartitionBootstrapService)
            .dependency(RAFT_CONFIGURATION_MANAGER, systemPartitionBootstrapService.getRaftPersistentConfigurationManagerInjector())
            .dependency(RAFT_BOOTSTRAP_SERVICE)
            .install();
    }
}
