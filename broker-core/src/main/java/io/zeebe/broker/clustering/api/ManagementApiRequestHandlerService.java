package io.zeebe.broker.clustering.api;

import io.zeebe.broker.clustering.base.raft.config.RaftPersistentConfigurationManager;
import io.zeebe.broker.system.deployment.handler.WorkflowRequestMessageHandler;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;

public class ManagementApiRequestHandlerService extends Actor implements Service<Void>
{
    private final Injector<BufferingServerTransport> serverTransportInjector = new Injector<>();
    private final Injector<WorkflowRequestMessageHandler> workflowRequestMessageHandlerInjector = new Injector<>();
    private final Injector<RaftPersistentConfigurationManager> raftPersistentConfigurationManagerInjector = new Injector<>();

    private BufferingServerTransport serverTransport;
    private ManagementApiRequestHandler managementApiRequestHandler;
    private WorkflowRequestMessageHandler workflowRequestMessageHandler;
    private RaftPersistentConfigurationManager raftPersistentConfigurationManager;

    @Override
    public void start(ServiceStartContext startContext)
    {
        serverTransport = serverTransportInjector.getValue();
        workflowRequestMessageHandler = workflowRequestMessageHandlerInjector.getValue();
        raftPersistentConfigurationManager = raftPersistentConfigurationManagerInjector.getValue();
        managementApiRequestHandler = new ManagementApiRequestHandler(workflowRequestMessageHandler,
            raftPersistentConfigurationManager,
            actor,
            startContext);

        startContext.async(startContext.getScheduler().submitActor(this, true));
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(actor.close());
    }

    @Override
    protected void onActorStarting()
    {
        final ActorFuture<ServerInputSubscription> subscriptionFuture = serverTransport.openSubscription("clusterManagement", managementApiRequestHandler, managementApiRequestHandler);

        actor.runOnCompletion(subscriptionFuture, (subscription, throwable) ->
        {
            if (throwable != null)
            {
                throw new RuntimeException(throwable);
            }
            else
            {
                actor.consume(subscription, () ->
                {
                    if (subscription.poll() == 0)
                    {
                        actor.yield();
                    }
                });
            }
        });
    }

    @Override
    public Void get()
    {
        return null;
    }

    public Injector<BufferingServerTransport> getServerTransportInjector()
    {
        return serverTransportInjector;
    }

    public Injector<WorkflowRequestMessageHandler> getWorkflowRequestMessageHandlerInjector()
    {
        return workflowRequestMessageHandlerInjector;
    }

    public Injector<RaftPersistentConfigurationManager> getRaftPersistentConfigurationManagerInjector()
    {
        return raftPersistentConfigurationManagerInjector;
    }
}
