package io.zeebe.broker.clustering.base.raft.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.logstreams.cfg.LogStreamsCfg;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

/**
 * Manages {@link RaftPersistentConfiguration} instances.
 * When the broker is started, it loads the stored files.
 * Knows where to put new configuration files when a new raft is started.
 */
public class RaftPersistentConfigurationManager extends Actor
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final List<RaftPersistentConfiguration> configurations = new ArrayList<>();
    private final String configurationStoreDirectory;
    private final LogStreamsCfg logStreamsCfg;

    public RaftPersistentConfigurationManager(String configurationStoreDirectory, LogStreamsCfg logStreamsCfg)
    {
        this.configurationStoreDirectory = configurationStoreDirectory;
        this.logStreamsCfg = logStreamsCfg;
    }

    @Override
    protected void onActorStarting()
    {
        final File[] configFiles = new File(configurationStoreDirectory).listFiles();

        if (configFiles != null && configFiles.length > 0)
        {
            for (int i = 0; i < configFiles.length; i++)
            {
                final String path = configFiles[i].getAbsolutePath();

                try
                {
                    configurations.add(new RaftPersistentConfiguration(path));
                }
                catch (Exception e)
                {
                    LOG.error("Could not load persistent raft configuration '" +
                            path + "', this broker will not join raft group.", e);
                }
            }
        }
    }

    public ActorFuture<List<RaftPersistentConfiguration>> getConfigurations()
    {
        return actor.call(() -> new ArrayList<>(configurations));
    }

    public ActorFuture<RaftPersistentConfiguration> createConfiguration(DirectBuffer topicName,
        int partitionId,
        int replicationFactor,
        List<SocketAddress> members)
    {
        return actor.call(() ->
        {
            final String logName = String.format("%s-%d", BufferUtil.bufferAsString(topicName), partitionId);
            final String filename = String.format("%s%s.meta", configurationStoreDirectory, logName);
            final RaftPersistentConfiguration storage = new RaftPersistentConfiguration(filename);

            final String[] logDirectories = logStreamsCfg.directories;
            final int assignedLogDirectory = ThreadLocalRandom.current().nextInt(logDirectories.length);

            storage.setLogDirectory(logDirectories[assignedLogDirectory].concat(logName))
                .setTopicName(topicName)
                .setPartitionId(partitionId)
                .setReplicationFactor(replicationFactor)
                .setMembers(members)
                .save();

            configurations.add(storage);

            return storage;
        });
    }

    public ActorFuture<Void> deleteConfiguration(RaftPersistentConfiguration configuration)
    {
        return actor.call(() ->
        {
            configurations.remove(configuration);
            configuration.delete();
        });
    }

    public ActorFuture<Void> close()
    {
        return actor.close();
    }
}
