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
package io.zeebe.client.subscription.job;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobSubscription;
import io.zeebe.client.api.subscription.JobSubscriptionBuilderStep1.*;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.subscription.SubscriptionManager;

public class ManagedJobSubscriptionBuilderImpl
        implements ManagedJobSubscriptionBuilderStep2, ManagedJobSubscriptionBuilderStep3, ManagedJobSubscriptionBuilderStep4
{
    private final JobSubscriberGroupBuilder subscriberBuilder;

    public ManagedJobSubscriptionBuilderImpl(String topic, SubscriptionManager subscriptionManager)
    {
        this.subscriberBuilder = new JobSubscriberGroupBuilder(topic, subscriptionManager);
    }

    @Override
    public ManagedJobSubscriptionBuilderStep3 jobType(String type)
    {
        subscriberBuilder.jobType(type);
        return this;
    }

    @Override
    public ManagedJobSubscriptionBuilderStep4 lockTime(long lockTime)
    {
        subscriberBuilder.lockTime(lockTime);
        return this;
    }

    @Override
    public ManagedJobSubscriptionBuilderStep4 lockTime(Duration lockTime)
    {
        subscriberBuilder.lockTime(lockTime.toMillis());
        return this;
    }

    @Override
    public ManagedJobSubscriptionBuilderStep4 lockOwner(String lockOwner)
    {
        subscriberBuilder.lockOwner(lockOwner);
        return this;
    }

    @Override
    public ManagedJobSubscriptionBuilderStep4 fetchSize(int fetchSize)
    {
        subscriberBuilder.jobFetchSize(fetchSize);
        return this;
    }

    @Override
    public ManagedJobSubscriptionBuilderStep4 handler(JobHandler handler)
    {
        subscriberBuilder.jobHandler(handler);
        return this;
    }

    @Override
    public JobSubscription open()
    {
        final Future<JobSubscriberGroup> subscriberGroup = subscriberBuilder.build();

        try
        {
            return subscriberGroup.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new ClientException("Could not open subscription", e);
        }
    }

}
