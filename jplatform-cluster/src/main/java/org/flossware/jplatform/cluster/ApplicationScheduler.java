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

package org.flossware.jplatform.cluster;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.flossware.jplatform.api.ClusterManager;
import org.flossware.jplatform.api.ClusterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedules and assigns applications to cluster nodes.
 * This component runs only on the cluster leader and is responsible for
 * distributing applications across available nodes.
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Assign new applications to nodes using load balancing</li>
 *   <li>Track application assignments in distributed storage</li>
 *   <li>Handle node failures by rescheduling affected applications</li>
 *   <li>Provide node selection algorithms (round-robin, least-loaded)</li>
 * </ul>
 *
 * <p>Uses Hazelcast IMap for storing assignments:</p>
 * <ul>
 *   <li>"jplatform-application-assignments" - maps application ID to node ID</li>
 *   <li>"jplatform-node-load" - tracks application count per node</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApplicationScheduler scheduler = new ApplicationScheduler(
 *     hazelcastInstance, clusterManager);
 *
 * // Leader assigns application
 * if (clusterManager.isLeader()) {
 *     scheduler.assignApplication("my-app");
 * }
 *
 * // Check which node should run the app
 * String nodeId = scheduler.getAssignedNode("my-app");
 * }</pre>
 *
 * @see ClusterManager
 * @see HazelcastClusterManager
 */
public class ApplicationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationScheduler.class);
    private static final String ASSIGNMENT_MAP_NAME = "jplatform-application-assignments";
    private static final String NODE_LOAD_MAP_NAME = "jplatform-node-load";

    private final HazelcastInstance hazelcast;
    private final ClusterManager clusterManager;
    private final IMap<String, String> assignmentMap;
    private final IMap<String, Integer> nodeLoadMap;
    private final SchedulingStrategy strategy;
    private final AtomicInteger roundRobinIndex;

    /**
     * Scheduling strategies for node selection.
     */
    public enum SchedulingStrategy {
        /** Distribute applications evenly across nodes in round-robin fashion */
        ROUND_ROBIN,
        /** Assign to the node with the fewest applications */
        LEAST_LOADED
    }

    /**
     * Constructs a new application scheduler with round-robin strategy.
     *
     * @param hazelcast the Hazelcast instance for distributed storage
     * @param clusterManager the cluster manager for node information
     */
    public ApplicationScheduler(HazelcastInstance hazelcast, ClusterManager clusterManager) {
        this(hazelcast, clusterManager, SchedulingStrategy.LEAST_LOADED);
    }

    /**
     * Constructs a new application scheduler with the specified strategy.
     *
     * @param hazelcast the Hazelcast instance for distributed storage
     * @param clusterManager the cluster manager for node information
     * @param strategy the scheduling strategy to use
     * @throws NullPointerException if any parameter is null
     */
    public ApplicationScheduler(HazelcastInstance hazelcast,
                                ClusterManager clusterManager,
                                SchedulingStrategy strategy) {
        this.hazelcast = Objects.requireNonNull(hazelcast, "hazelcast cannot be null");
        this.clusterManager = Objects.requireNonNull(clusterManager, "clusterManager cannot be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy cannot be null");

        this.assignmentMap = hazelcast.getMap(ASSIGNMENT_MAP_NAME);
        this.nodeLoadMap = hazelcast.getMap(NODE_LOAD_MAP_NAME);
        this.roundRobinIndex = new AtomicInteger(0);

        logger.info("ApplicationScheduler initialized with strategy: {}", strategy);
    }

    /**
     * Assigns an application to a cluster node.
     * Only the cluster leader should call this method.
     * Uses the configured scheduling strategy to select the target node.
     *
     * @param applicationId the application identifier
     * @return the node ID where the application was assigned
     * @throws IllegalStateException if not called on the leader node
     * @throws NullPointerException if applicationId is null
     */
    public String assignApplication(String applicationId) {
        Objects.requireNonNull(applicationId, "applicationId cannot be null");

        if (!clusterManager.isLeader()) {
            throw new IllegalStateException("Only the leader can assign applications");
        }

        // Check if already assigned
        String existingNode = assignmentMap.get(applicationId);
        if (existingNode != null) {
            logger.debug("Application {} already assigned to node {}", applicationId, existingNode);
            return existingNode;
        }

        // Select target node
        ClusterNode targetNode = selectNode();
        if (targetNode == null) {
            throw new IllegalStateException("No available nodes in cluster");
        }

        String nodeId = targetNode.getNodeId();
        logger.info("Assigning application {} to node {}", applicationId, nodeId);

        // Store assignment
        assignmentMap.put(applicationId, nodeId);

        // Update node load
        nodeLoadMap.compute(nodeId, (k, count) -> count == null ? 1 : count + 1);

        logger.info("Application {} assigned to node {} successfully", applicationId, nodeId);
        return nodeId;
    }

    /**
     * Unassigns an application from its current node.
     * Only the cluster leader should call this method.
     *
     * @param applicationId the application identifier
     * @throws IllegalStateException if not called on the leader node
     */
    public void unassignApplication(String applicationId) {
        if (!clusterManager.isLeader()) {
            throw new IllegalStateException("Only the leader can unassign applications");
        }

        String nodeId = assignmentMap.remove(applicationId);
        if (nodeId != null) {
            // Update node load
            nodeLoadMap.compute(nodeId, (k, count) -> count == null || count <= 1 ? null : count - 1);
            logger.info("Application {} unassigned from node {}", applicationId, nodeId);
        }
    }

    /**
     * Returns the node ID where an application is assigned.
     *
     * @param applicationId the application identifier
     * @return the node ID, or null if not assigned
     */
    public String getAssignedNode(String applicationId) {
        return assignmentMap.get(applicationId);
    }

    /**
     * Checks if an application is assigned to the local node.
     *
     * @param applicationId the application identifier
     * @return true if assigned to the local node
     */
    public boolean isAssignedToLocalNode(String applicationId) {
        String assignedNode = assignmentMap.get(applicationId);
        if (assignedNode == null) {
            return false;
        }

        ClusterNode localNode = clusterManager.getLocalNode();
        return localNode != null && assignedNode.equals(localNode.getNodeId());
    }

    /**
     * Returns all application assignments in the cluster.
     *
     * @return a map of application ID to node ID
     */
    public Map<String, String> getAllAssignments() {
        return new HashMap<>(assignmentMap);
    }

    /**
     * Returns the current load (application count) for each node.
     *
     * @return a map of node ID to application count
     */
    public Map<String, Integer> getNodeLoads() {
        return new HashMap<>(nodeLoadMap);
    }

    /**
     * Reassigns applications from a failed node to other available nodes.
     * Only the cluster leader should call this method.
     *
     * @param failedNodeId the ID of the failed node
     * @return the number of applications reassigned
     * @throws IllegalStateException if not called on the leader node
     */
    public int reassignFromFailedNode(String failedNodeId) {
        if (!clusterManager.isLeader()) {
            throw new IllegalStateException("Only the leader can reassign applications");
        }

        logger.warn("Reassigning applications from failed node: {}", failedNodeId);

        List<String> affectedApps = new ArrayList<>();
        for (Map.Entry<String, String> entry : assignmentMap.entrySet()) {
            if (failedNodeId.equals(entry.getValue())) {
                affectedApps.add(entry.getKey());
            }
        }

        int reassignedCount = 0;
        for (String appId : affectedApps) {
            try {
                // Remove old assignment
                assignmentMap.remove(appId);

                // Decrement failed node load immediately for accurate load balancing
                nodeLoadMap.compute(failedNodeId, (k, count) ->
                    count == null || count <= 1 ? null : count - 1);

                // Select new node
                ClusterNode newNode = selectNode();
                if (newNode != null) {
                    String newNodeId = newNode.getNodeId();
                    assignmentMap.put(appId, newNodeId);
                    nodeLoadMap.compute(newNodeId, (k, count) -> count == null ? 1 : count + 1);
                    reassignedCount++;
                    logger.info("Reassigned application {} from {} to {}",
                            appId, failedNodeId, newNodeId);
                }
            } catch (Exception e) {
                logger.error("Failed to reassign application: {}", appId, e);
            }
        }

        // Ensure failed node is completely removed from load map
        nodeLoadMap.remove(failedNodeId);

        logger.info("Reassigned {} applications from failed node {}", reassignedCount, failedNodeId);
        return reassignedCount;
    }

    /**
     * Selects a node using the configured scheduling strategy.
     *
     * @return the selected cluster node, or null if no nodes available
     */
    private ClusterNode selectNode() {
        Set<ClusterNode> nodes = clusterManager.getNodes();
        if (nodes.isEmpty()) {
            return null;
        }

        switch (strategy) {
            case ROUND_ROBIN:
                return selectNodeRoundRobin(nodes);
            case LEAST_LOADED:
                return selectNodeLeastLoaded(nodes);
            default:
                return selectNodeRoundRobin(nodes);
        }
    }

    /**
     * Selects a node using round-robin strategy.
     *
     * @param nodes the available cluster nodes
     * @return the selected node
     */
    private ClusterNode selectNodeRoundRobin(Set<ClusterNode> nodes) {
        List<ClusterNode> nodeList = new ArrayList<>(nodes);
        // Use Math.abs to handle integer overflow when roundRobinIndex wraps to negative
        int index = Math.abs(roundRobinIndex.getAndIncrement() % nodeList.size());
        return nodeList.get(index);
    }

    /**
     * Selects the node with the least load (fewest applications).
     *
     * @param nodes the available cluster nodes
     * @return the least loaded node
     */
    private ClusterNode selectNodeLeastLoaded(Set<ClusterNode> nodes) {
        ClusterNode leastLoaded = null;
        int minLoad = Integer.MAX_VALUE;

        for (ClusterNode node : nodes) {
            Integer load = nodeLoadMap.getOrDefault(node.getNodeId(), 0);
            if (load < minLoad) {
                minLoad = load;
                leastLoaded = node;
            }
        }

        return leastLoaded;
    }
}
