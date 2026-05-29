/*
 * Copyright (C) 2024-2026 FlossWare
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.flossware.jplatform.api;

import java.util.Set;

/**
 * Manages platform clustering for multi-node deployments.
 * Provides cluster membership, leader election, and node coordination.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ClusterConfig config = ClusterConfig.builder()
 *     .clusterName("jplatform-production")
 *     .bindAddress("192.168.1.10")
 *     .bindPort(5701)
 *     .addSeedNode("192.168.1.10:5701")
 *     .addSeedNode("192.168.1.11:5701")
 *     .build();
 *
 * ClusterManager cluster = new HazelcastClusterManager(config);
 * cluster.join(config);
 *
 * if (cluster.isLeader()) {
 *     // Perform leader-only operations
 * }
 * }</pre>
 *
 * @see ClusterNode
 * @see ClusterConfig
 * @since 1.0
 */
public interface ClusterManager extends AutoCloseable {

    /**
     * Joins the cluster with the specified configuration.
     *
     * @param config the cluster configuration
     * @throws ClusterJoinException if joining fails
     * @since 1.0
     */
    void join(ClusterConfig config) throws ClusterJoinException;

    /**
     * Leaves the cluster gracefully.
     *
     * @throws ClusterLeaveException if leaving fails
     * @since 1.0
     */
    void leave() throws ClusterLeaveException;

    /**
     * Returns all nodes in the cluster.
     *
     * @return a set of cluster nodes
     */
    Set<ClusterNode> getNodes();

    /**
     * Returns the local node.
     *
     * @return the local cluster node
     */
    ClusterNode getLocalNode();

    /**
     * Checks if this node is the cluster leader.
     *
     * @return true if this node is the leader
     */
    boolean isLeader();

    /**
     * Adds a listener for cluster events.
     *
     * @param listener the listener to add
     */
    void addListener(ClusterEventListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(ClusterEventListener listener);

    /**
     * Checks if this node is currently part of a cluster.
     *
     * @return true if joined to a cluster
     */
    boolean isJoined();
}
