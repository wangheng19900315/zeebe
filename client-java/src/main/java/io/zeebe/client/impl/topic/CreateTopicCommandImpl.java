/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl.topic;

import io.zeebe.client.api.commands.CreateTopicCommandStep1;
import io.zeebe.client.api.commands.CreateTopicCommandStep1.CreateTopicCommandStep2;
import io.zeebe.client.api.commands.CreateTopicCommandStep1.CreateTopicCommandStep3;
import io.zeebe.client.api.commands.TopicCommand.TopicCommandName;
import io.zeebe.client.api.events.TopicEvent;
import io.zeebe.client.impl.CommandImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.command.TopicCommandImpl;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.protocol.Protocol;

public class CreateTopicCommandImpl extends CommandImpl<TopicEvent> implements CreateTopicCommandStep1, CreateTopicCommandStep2, CreateTopicCommandStep3
{
    private final TopicCommandImpl command = new TopicCommandImpl(TopicCommandName.CREATE);

    public CreateTopicCommandImpl(RequestManager client)
    {
        super(client);

        this.command.setTopicName(Protocol.SYSTEM_TOPIC);
        this.command.setPartitionId(Protocol.SYSTEM_PARTITION);
    }

    @Override
    public CreateTopicCommandStep3 partitions(int partitions)
    {
        this.command.setPartitions(partitions);
        return this;
    }

    @Override
    public CreateTopicCommandStep2 name(String topicName)
    {
        this.command.setName(topicName);
        return this;
    }

    @Override
    public RecordImpl getCommand()
    {
        return command;
    }

}
