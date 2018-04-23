package io.zeebe.broker.system.configuration;

public class BrokerCfg
{
    private boolean standalone = true;

    private GlobalCfg global = new GlobalCfg();

    private NetworkCfg network = new NetworkCfg();

    private ClusterCfg cluster = new ClusterCfg();

    private ThreadsCfg threads = new ThreadsCfg();

    private MetricsCfg metrics = new MetricsCfg();

    private LogsCfg logs = new LogsCfg();

    public BrokerCfg init()
    {
        global.init();

        global.applyGlobalConfiguration(global);
        network.applyGlobalConfiguration(global);
        cluster.applyGlobalConfiguration(global);
        threads.applyGlobalConfiguration(global);
        metrics.applyGlobalConfiguration(global);
        logs.applyGlobalConfiguration(global);

        return this;
    }

    public boolean isStandalone()
    {
        return standalone;
    }

    public void setStandalone(boolean standalone)
    {
        this.standalone = standalone;
    }

    public GlobalCfg getGlobal()
    {
        return global;
    }

    public void setGlobal(GlobalCfg global)
    {
        this.global = global;
    }

    public NetworkCfg getNetwork()
    {
        return network;
    }

    public void setNetwork(NetworkCfg network)
    {
        this.network = network;
    }

    public ClusterCfg getCluster()
    {
        return cluster;
    }

    public void setCluster(ClusterCfg cluster)
    {
        this.cluster = cluster;
    }

    public ThreadsCfg getThreads()
    {
        return threads;
    }

    public void setThreads(ThreadsCfg threads)
    {
        this.threads = threads;
    }

    public MetricsCfg getMetrics()
    {
        return metrics;
    }

    public void setMetrics(MetricsCfg metrics)
    {
        this.metrics = metrics;
    }

    public LogsCfg getLogs()
    {
        return logs;
    }

    public void setLogs(LogsCfg logs)
    {
        this.logs = logs;
    }

}
