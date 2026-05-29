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

import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.*;
import com.hazelcast.cp.CPSubsystem;
import com.hazelcast.cp.lock.FencedLock;
import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hazelcast-based implementation of ClusterManager.
 * Provides multi-node clustering with automatic discovery, leader election,
 * and membership management using Hazelcast IMDG.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>TCP/IP discovery with seed nodes</li>
 *   <li>Leader election via Hazelcast CP subsystem</li>
 *   <li>Automatic failure detection and recovery</li>
 *   <li>Event notifications for membership changes</li>
 * </ul>
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
 * HazelcastClusterManager cluster = new HazelcastClusterManager();
 * cluster.join(config);
 * }</pre>
 *
 * @see ClusterManager
 * @see ClusterConfig
 */
public class HazelcastClusterManager implements ClusterManager {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastClusterManager.class);
    private static final String LEADER_LOCK_NAME = "jplatform-leader-lock";

    private HazelcastInstance hazelcast;
    private ClusterConfig clusterConfig;
    private volatile boolean joined;
    private final List<ClusterEventListener> listeners;
    private FencedLock leaderLock;
    private volatile boolean isLeader;
    private UUID membershipListenerId;

    /**
     * Constructs a new Hazelcast cluster manager.
     */
    public HazelcastClusterManager() {
        this.listeners = new CopyOnWriteArrayList<>();
        this.joined = false;
        this.isLeader = false;
    }

    /**
     * Package-private constructor for testing.
     * Allows injection of a mock HazelcastInstance.
     *
     * @param hazelcast the Hazelcast instance to use
     */
    HazelcastClusterManager(HazelcastInstance hazelcast) {
        this.hazelcast = hazelcast;
        this.listeners = new CopyOnWriteArrayList<>();
        this.joined = false;
        this.isLeader = false;
    }

    /**
     * Joins the cluster with the specified configuration.
     * Initializes Hazelcast, registers membership listeners, and attempts leader election.
     *
     * @param config the cluster configuration containing network settings and seed nodes
     * @throws ClusterJoinException if joining the cluster fails
     */
    @Override
    public void join(ClusterConfig config) throws ClusterJoinException {
        if (joined) {
            throw new IllegalStateException("Already joined to cluster");
        }

        this.clusterConfig = config;
        logger.info("Joining cluster: {}", config.getClusterName());

        try {
            // Create Hazelcast instance if not already injected (for testing)
            if (hazelcast == null) {
                // Configure Hazelcast
                Config hazelcastConfig = new Config();
                hazelcastConfig.setClusterName(config.getClusterName());

                // Network configuration
                NetworkConfig network = hazelcastConfig.getNetworkConfig();
                network.setPort(config.getBindPort());
                network.setPortAutoIncrement(false);

                if (config.getBindAddress() != null && !config.getBindAddress().isEmpty()) {
                    network.getInterfaces().setEnabled(true);
                    network.getInterfaces().addInterface(config.getBindAddress());
                }

                // Disable multicast, use TCP/IP discovery
                network.getJoin().getMulticastConfig().setEnabled(false);

                TcpIpConfig tcpIp = network.getJoin().getTcpIpConfig();
                tcpIp.setEnabled(true);
                for (String seed : config.getSeedNodes()) {
                    tcpIp.addMember(seed);
                    logger.debug("Added seed node: {}", seed);
                }

                // Enable CP subsystem for leader election
                // Set CP member count based on cluster size (min 1, max 3)
                // For development/testing, smaller clusters are allowed
                int clusterSize = Math.max(config.getSeedNodes().size(), 1);
                int cpMemberCount = Math.min(clusterSize, 3);
                hazelcastConfig.getCPSubsystemConfig().setCPMemberCount(cpMemberCount);
                logger.info("CP subsystem configured with {} members", cpMemberCount);

                // Create Hazelcast instance
                hazelcast = Hazelcast.newHazelcastInstance(hazelcastConfig);
            }

            // Register membership listener
            membershipListenerId = hazelcast.getCluster().addMembershipListener(new MembershipListener() {
                @Override
                public void memberAdded(MembershipEvent event) {
                    logger.info("Node joined cluster: {}", event.getMember().getUuid());
                    ClusterNode node = convertMember(event.getMember());
                    notifyNodeJoined(node);
                }

                @Override
                public void memberRemoved(MembershipEvent event) {
                    logger.info("Node left cluster: {}", event.getMember().getUuid());
                    ClusterNode node = convertMember(event.getMember());
                    notifyNodeLeft(node);
                    // Re-attempt leader election if leader left
                    if (isLeader) {
                        tryBecomeLeader();
                    }
                }
            });

            joined = true;
            logger.info("Successfully joined cluster: {}", config.getClusterName());

            // Attempt to become leader
            tryBecomeLeader();

        } catch (Exception e) {
            logger.error("Failed to join cluster: {}", config.getClusterName(), e);
            throw new ClusterJoinException(config.getClusterName(), "Failed to join cluster", e);
        }
    }

    /**
     * Leaves the cluster gracefully.
     * Releases leader lock if held and shuts down Hazelcast instance.
     *
     * @throws ClusterLeaveException if leaving the cluster fails
     */
    @Override
    public void leave() throws ClusterLeaveException {
        if (!joined) {
            logger.warn("Not joined to any cluster");
            return;
        }

        logger.info("Leaving cluster: {}", clusterConfig.getClusterName());

        try {
            // Release leader lock
            if (leaderLock != null && leaderLock.isLockedByCurrentThread()) {
                leaderLock.unlock();
                isLeader = false;
                logger.info("Released leader lock");
            }

            // Unregister membership listener
            if (membershipListenerId != null && hazelcast != null) {
                hazelcast.getCluster().removeMembershipListener(membershipListenerId);
            }

            // Shutdown Hazelcast
            if (hazelcast != null) {
                hazelcast.shutdown();
                hazelcast = null;
            }

            joined = false;
            logger.info("Successfully left cluster");

        } catch (Exception e) {
            logger.error("Error leaving cluster", e);
            throw new ClusterLeaveException("Failed to leave cluster", e);
        }
    }

    /**
     * Returns all nodes currently in the cluster.
     *
     * @return a set of cluster nodes
     */
    @Override
    public Set<ClusterNode> getNodes() {
        if (!joined || hazelcast == null) {
            return Collections.emptySet();
        }

        Set<ClusterNode> nodes = new HashSet<>();
        for (Member member : hazelcast.getCluster().getMembers()) {
            nodes.add(convertMember(member));
        }
        return nodes;
    }

    /**
     * Returns the local node information.
     *
     * @return the local cluster node
     */
    @Override
    public ClusterNode getLocalNode() {
        if (!joined || hazelcast == null) {
            return null;
        }

        Member localMember = hazelcast.getCluster().getLocalMember();
        return convertMember(localMember);
    }

    /**
     * Checks if this node is the cluster leader.
     * Leader election is based on acquiring a distributed lock via Hazelcast CP subsystem.
     *
     * @return true if this node holds the leader lock
     */
    @Override
    public boolean isLeader() {
        return isLeader && leaderLock != null && leaderLock.isLockedByCurrentThread();
    }

    /**
     * Adds a listener for cluster events (node join, leave, leader change).
     *
     * @param listener the listener to add
     */
    @Override
    public void addListener(ClusterEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
            logger.debug("Added cluster event listener: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * Removes a previously registered cluster event listener.
     *
     * @param listener the listener to remove
     */
    @Override
    public void removeListener(ClusterEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
            logger.debug("Removed cluster event listener: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * Checks if this node is currently joined to a cluster.
     *
     * @return true if joined to a cluster
     */
    @Override
    public boolean isJoined() {
        return joined;
    }

    /**
     * Closes the cluster manager and releases all resources.
     * Equivalent to calling leave().
     *
     * @throws Exception if closing fails
     */
    @Override
    public void close() throws Exception {
        leave();
    }

    /**
     * Returns the underlying Hazelcast instance.
     * Useful for accessing Hazelcast-specific features.
     *
     * @return the Hazelcast instance, or null if not joined
     */
    public HazelcastInstance getHazelcastInstance() {
        return hazelcast;
    }

    /**
     * Attempts to acquire the leader lock and become the cluster leader.
     * Uses Hazelcast CP subsystem's FencedLock for distributed leader election.
     */
    private void tryBecomeLeader() {
        if (!joined || hazelcast == null) {
            return;
        }

        try {
            CPSubsystem cpSubsystem = hazelcast.getCPSubsystem();
            leaderLock = cpSubsystem.getLock(LEADER_LOCK_NAME);

            // Try to acquire lock (non-blocking)
            if (leaderLock.tryLock()) {
                boolean wasLeader = isLeader;
                isLeader = true;
                logger.info("This node is now the cluster LEADER");

                if (!wasLeader) {
                    notifyLeaderChanged(getLocalNode());
                }
            } else {
                isLeader = false;
                logger.info("This node is a FOLLOWER");
            }

        } catch (Exception e) {
            logger.error("Error during leader election", e);
            isLeader = false;
        }
    }

    /**
     * Converts a Hazelcast Member to a ClusterNode.
     *
     * @param member the Hazelcast member
     * @return the cluster node representation
     */
    private ClusterNode convertMember(Member member) {
        String nodeId = member.getUuid().toString();
        String address = member.getAddress().getHost();
        int port = member.getAddress().getPort();
        ClusterNode.NodeState state = ClusterNode.NodeState.ACTIVE;
        long lastHeartbeat = System.currentTimeMillis();

        return new ClusterNode(nodeId, address, port, state, lastHeartbeat);
    }

    /**
     * Notifies all registered listeners that a node has joined.
     *
     * @param node the node that joined
     */
    private void notifyNodeJoined(ClusterNode node) {
        for (ClusterEventListener listener : listeners) {
            try {
                listener.onNodeJoined(node);
            } catch (Exception e) {
                logger.error("Error notifying listener of node join", e);
            }
        }
    }

    /**
     * Notifies all registered listeners that a node has left.
     *
     * @param node the node that left
     */
    private void notifyNodeLeft(ClusterNode node) {
        for (ClusterEventListener listener : listeners) {
            try {
                listener.onNodeLeft(node);
            } catch (Exception e) {
                logger.error("Error notifying listener of node leave", e);
            }
        }
    }

    /**
     * Notifies all registered listeners that the leader has changed.
     *
     * @param newLeader the new leader node
     */
    private void notifyLeaderChanged(ClusterNode newLeader) {
        for (ClusterEventListener listener : listeners) {
            try {
                listener.onLeaderChanged(newLeader);
            } catch (Exception e) {
                logger.error("Error notifying listener of leader change", e);
            }
        }
    }
}
