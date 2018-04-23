/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.util.FileUtil;

public class LogsCfg extends DirectoryCfg
{
    private String[] directories = null;

    private String defaultLogSegmentSize = "512MB";

    private SnapshotStorageCfg snapshots = new SnapshotStorageCfg();

    @Override
    public void applyGlobalConfiguration(GlobalCfg globalConfig)
    {
        snapshots.applyGlobalConfiguration(globalConfig);

        if (directories == null || directories.length == 0)
        {
            super.applyGlobalConfiguration(globalConfig);
            directories = new String[] { directory };
            return;
        }

        for (int i = 0; i < directories.length; i++)
        {
            directories[i] = FileUtil.getCanonicalPath(directories[i]);
        }
    }

    @Override
    protected String componentDirectoryName()
    {
        return "directories";
    }

    public String[] getDirectories()
    {
        return directories;
    }

    public void setDirectories(String[] directories)
    {
        this.directories = directories;
    }

    public String getDefaultLogSegmentSize()
    {
        return defaultLogSegmentSize;
    }

    public void setDefaultLogSegmentSize(String defaultLogSegmentSize)
    {
        this.defaultLogSegmentSize = defaultLogSegmentSize;
    }

    public SnapshotStorageCfg getSnapshots()
    {
        return snapshots;
    }

    public void setSnapshots(SnapshotStorageCfg snapshots)
    {
        this.snapshots = snapshots;
    }
}
