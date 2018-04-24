package io.zeebe.client.api.subscription;

import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.JobEvent;

/**
 * Implementations MUST be thread-safe.
 */
@FunctionalInterface
public interface JobHandler
{

    /**
     * <p>Handles a work item. Implements the work to be done
     * whenever a work item of a certain type is created.
     */
    void handle(JobClient client, JobEvent workItemEvent);
}
