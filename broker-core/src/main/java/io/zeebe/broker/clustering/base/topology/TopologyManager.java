package io.zeebe.broker.clustering.base.topology;

import java.util.function.Function;

import io.zeebe.util.sched.future.ActorFuture;

/**
 * Maintains the cluster topology.
 *
 * Three main interactions are possible:
 * <ul>
 * <li>async querying the topology (see {@link #query(Function)})</li>
 * <li>async requesting a snapshot (See {@link #getTopologyDto()}</li>
 * <li>observer: registering a listener and getting updated about node and partition events</li>
 * </ul>
 */
public interface TopologyManager
{
    /**
     * Can be used to query the topology.
     */
    <R> ActorFuture<R> query(Function<Topology, R> query);

    ActorFuture<TopologyDto> getTopologyDto();

    void removeTopologyMemberListener(TopologyMemberListener listener);

    void addTopologyMemberListener(TopologyMemberListener listener);

    void removeTopologyPartitionListener(TopologyPartitionListener listener);

    void addTopologyPartitionListener(TopologyPartitionListener listener);
}
