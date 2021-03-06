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
package io.zeebe.client.event.impl;

import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.task.impl.ControlMessageRequest;
import io.zeebe.protocol.clientapi.ControlMessageType;

public class CloseTopicSubscriptionCommandImpl extends ControlMessageRequest<Void>
{

    protected CloseSubscriptionRequest request = new CloseSubscriptionRequest();

    public CloseTopicSubscriptionCommandImpl(final RequestManager commandManager, int partitionId, long subscriberKey)
    {
        super(commandManager,
                ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION,
                partitionId,
                Void.class);
        this.request.setSubscriberKey(subscriberKey);
    }

    @Override
    public Object getRequest()
    {
        return request;
    }

}
