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
package io.zeebe.broker.clustering.orchestration.state;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.orchestration.topic.TopicEvent;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.protocol.clientapi.Intent;

public class TopicCreateProcessor implements TypedRecordProcessor<TopicEvent>
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final Predicate<DirectBuffer> topicExists;
    private final Consumer<DirectBuffer> notifyListeners;
    private final BiConsumer<Long, TopicEvent> addTopic;

    private boolean isCreating;

    public TopicCreateProcessor(final Predicate<DirectBuffer> topicExists, final Consumer<DirectBuffer> notifyListeners, final BiConsumer<Long, TopicEvent> addTopic)
    {
        this.topicExists = topicExists;
        this.notifyListeners = notifyListeners;
        this.addTopic = addTopic;
    }

    @Override
    public void processRecord(final TypedRecord<TopicEvent> event)
    {
        isCreating = false;

        final TopicEvent topicEvent = event.getValue();
        final DirectBuffer topicName = topicEvent.getName();

        if (topicExists.test(topicName))
        {
            LOG.warn("Rejecting topic {} creation as a topic with the same name already exists", bufferAsString(topicName));
        }
        else if (topicEvent.getPartitions() < 1)
        {
            LOG.warn("Rejecting topic {} creation as a topic has to have at least one partition", bufferAsString(topicName));
        }
        else if (topicEvent.getReplicationFactor() < 1)
        {
            LOG.warn("Rejecting topic {} creation as a topic has to have at least one replication", bufferAsString(topicName));
        }
        else
        {
            LOG.info("Creating topic {} with partition count {} and replication factor {}", bufferAsString(topicName), topicEvent.getPartitions(), topicEvent.getReplicationFactor());
            isCreating = true;
        }
    }

    @Override
    public boolean executeSideEffects(final TypedRecord<TopicEvent> event, final TypedResponseWriter responseWriter)
    {
        if (isCreating)
        {
            final boolean written = responseWriter.writeEvent(Intent.CREATING, event);

            if (written)
            {
                notifyListeners.accept(event.getValue().getName());
            }

            return written;
        }
        else
        {
            return responseWriter.writeRejection(event);
        }
    }

    @Override
    public long writeRecord(final TypedRecord<TopicEvent> event, final TypedStreamWriter writer)
    {
        if (isCreating)
        {
            return writer.writeFollowUpEvent(event.getKey(), Intent.CREATING, event.getValue());
        }
        else
        {
            return writer.writeRejection(event);
        }
    }

    @Override
    public void updateState(final TypedRecord<TopicEvent> event)
    {
        if (isCreating)
        {
            addTopic.accept(event.getKey(), event.getValue());
        }
    }
}
