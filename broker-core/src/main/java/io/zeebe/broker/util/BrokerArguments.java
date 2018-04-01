package io.zeebe.broker.util;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class BrokerArguments
{
    private final OptionParser parser = new OptionParser();

    private final ArgumentAcceptingOptionSpec<String> configSpec;
    private final ArgumentAcceptingOptionSpec<Integer> bootstrapExpectSpec;
    private final ArgumentAcceptingOptionSpec<Integer> replicationFactorSpec;

    private final OptionSet optionSet;

    public BrokerArguments(String[] arguments)
    {
        configSpec = parser.accepts("config")
                .withOptionalArg()
                .ofType(String.class);

        bootstrapExpectSpec = parser.accepts("bootstrap-expect")
                .withOptionalArg()
                .ofType(Integer.class);

        replicationFactorSpec = parser.accepts("system-topic-replication-factor")
                .withOptionalArg()
                .ofType(Integer.class)
                .defaultsTo(3);

        optionSet = parser.parse(arguments);
    }

    public String getConfigFile()
    {
        return optionSet.valueOf(configSpec);
    }

    public Integer getBoostrapExpectCount()
    {
        return optionSet.valueOf(bootstrapExpectSpec);
    }

    public Integer getReplicationFactor()
    {
        return optionSet.valueOf(replicationFactorSpec);
    }
}
