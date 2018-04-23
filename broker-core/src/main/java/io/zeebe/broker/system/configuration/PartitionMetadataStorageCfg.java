package io.zeebe.broker.system.configuration;

public class PartitionMetadataStorageCfg extends DirectoryCfg
{
    @Override
    protected String componentDirectoryName()
    {
        return "directory";
    }
}
