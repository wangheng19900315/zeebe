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
package io.zeebe.broker.workflow.map;

import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.map.Long2LongZbMap;
import io.zeebe.util.cache.ExpandableBufferCache;
import org.agrona.DirectBuffer;

/**
 * Cache of workflow instance payload. It contains an LRU cache of the payload
 * and an map which holds the position of the payload events.
 *
 * <p>
 * When a payload is requested then the it is returned from the cache. If it is
 * not present in the cache then the payload event is seek in the log stream.
 */
public class PayloadCache implements AutoCloseable
{
    private final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();

    private final Long2LongZbMap map;
    private final ZbMapSnapshotSupport<Long2LongZbMap> snapshotSupport;

    private final ExpandableBufferCache cache;
    private final LogStreamReader logStreamReader;

    public PayloadCache(int cacheSize, LogStreamReader logStreamReader)
    {
        this.map = new Long2LongZbMap();
        this.snapshotSupport = new ZbMapSnapshotSupport<>(map);

        this.logStreamReader = logStreamReader;
        this.cache = new ExpandableBufferCache(cacheSize, 1024, this::lookupPayload);
    }

    private DirectBuffer lookupPayload(long position)
    {
        DirectBuffer payload = null;

        final boolean found = logStreamReader.seek(position);
        if (found && logStreamReader.hasNext())
        {
            final LoggedEvent event = logStreamReader.next();

            workflowInstanceEvent.reset();
            event.readValue(workflowInstanceEvent);

            payload = workflowInstanceEvent.getPayload();
        }

        return payload;
    }

    public DirectBuffer getPayload(long workflowInstanceKey)
    {
        DirectBuffer payload = null;

        final long position = map.get(workflowInstanceKey, -1L);

        if (position > 0)
        {
            payload = cache.get(position);
        }
        return payload == null ? WorkflowInstanceEvent.NO_PAYLOAD : payload;
    }

    public void addPayload(long workflowInstanceKey, long payloadEventPosition, DirectBuffer payload)
    {
        map.put(workflowInstanceKey, payloadEventPosition);
        cache.put(payloadEventPosition, payload);
    }

    public void remove(long workflowInstanceKey)
    {
        map.remove(workflowInstanceKey, -1L);
    }

    public ZbMapSnapshotSupport<Long2LongZbMap> getSnapshotSupport()
    {
        return snapshotSupport;
    }

    @Override
    public void close()
    {
        map.close();
    }

}
