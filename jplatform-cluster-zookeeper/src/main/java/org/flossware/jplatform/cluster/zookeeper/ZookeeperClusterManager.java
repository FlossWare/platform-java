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

package org.flossware.jplatform.cluster.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * ZooKeeper-based ClusterManager implementation using Apache Curator.
 * Provides clustering via ZooKeeper's distributed coordination primitives.
 *
 * <p>This implementation uses:
 * <ul>
 *   <li>Leader election via Curator LeaderSelector with ephemeral znodes</li>
 *   <li>Membership tracking via ephemeral znodes</li>
 *   <li>Distributed state storage via ZooKeeper znodes</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by volatile fields and concurrent collections.
 *
 * @since 1.1
 */
public class ZookeeperClusterManager implements ClusterManager {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperClusterManager.class);
    private static final String LEADER_PATH_PREFIX = "/jplatform/leader/";
    private static final String MEMBER_PATH_PREFIX = "/jplatform/members/";

    private final ZookeeperConfig config;
    private CuratorFramework client;
    private LeaderSelector leaderSelector;
    private volatile boolean joined = false;
    private volatile boolean isLeader = false;
    private volatile CountDownLatch leadershipLatch;
    private ClusterConfig clusterConfig;
    private String nodeId;
    private final List<ClusterEventListener> listeners;

    /**
     * Constructs a new ZooKeeper cluster manager.
     *
     * @param config the ZooKeeper configuration
     * @throws IllegalArgumentException if config is null
     */
    public ZookeeperClusterManager(ZookeeperConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        this.config = config;
        this.listeners = new CopyOnWriteArrayList<>();
        this.nodeId = UUID.randomUUID().toString();
    }

    /**
     * Package-private constructor for testing.
     *
     * @param config the ZooKeeper configuration
     * @param client the Curator client
     * @throws IllegalArgumentException if config is null
     */
    ZookeeperClusterManager(ZookeeperConfig config, CuratorFramework client) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        this.config = config;
        this.client = client;
        this.listeners = new CopyOnWriteArrayList<>();
        this.nodeId = UUID.randomUUID().toString();
    }

    @Override
    public void join(ClusterConfig config) throws ClusterJoinException {
        if (joined) {
            throw new IllegalStateException("Already joined");
        }
        this.clusterConfig = config;
        try {
            if (client == null) {
                ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(
                    this.config.getBaseSleepTimeMs(),
                    this.config.getMaxRetries()
                );

                CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                    .connectString(this.config.getConnectionString())
                    .sessionTimeoutMs(this.config.getSessionTimeoutMs())
                    .connectionTimeoutMs(this.config.getConnectionTimeoutMs())
                    .retryPolicy(retryPolicy);

                if (this.config.getNamespace() != null) {
                    builder.namespace(this.config.getNamespace());
                }

                client = builder.build();
                client.start();
            }

            // Register as member
            registerMember();

            // Start leader election
            String leaderPath = LEADER_PATH_PREFIX + config.getClusterName();
            leaderSelector = new LeaderSelector(client, leaderPath, new LeaderSelectorListenerAdapter() {
                @Override
                public void takeLeadership(CuratorFramework client) throws Exception {
                    isLeader = true;
                    notifyLeaderChanged(getLocalNode());
                    logger.info("Became leader for cluster: {}", config.getClusterName());

                    // Hold leadership until interrupted
                    leadershipLatch = new CountDownLatch(1);
                    try {
                        leadershipLatch.await();
                    } catch (InterruptedException e) {
                        logger.info("Leadership interrupted");
                        Thread.currentThread().interrupt();
                    } finally {
                        isLeader = false;
                        leadershipLatch = null;
                    }
                }
            });
            leaderSelector.autoRequeue();
            leaderSelector.start();

            joined = true;
        } catch (Exception e) {
            throw new ClusterJoinException(config.getClusterName(), "Failed to join", e);
        }
    }

    @Override
    public void leave() throws ClusterLeaveException {
        if (!joined) return;
        try {
            // Release leadership latch to interrupt takeLeadership
            if (leadershipLatch != null) {
                leadershipLatch.countDown();
            }

            if (leaderSelector != null) {
                leaderSelector.close();
            }

            // Unregister member
            unregisterMember();

            if (client != null) {
                client.close();
            }
            joined = false;
            isLeader = false;
        } catch (Exception e) {
            throw new ClusterLeaveException("Failed to leave", e);
        }
    }

    @Override
    public Set<ClusterNode> getNodes() {
        if (!joined) return Collections.emptySet();

        Set<ClusterNode> nodes = new HashSet<>();
        try {
            String memberPath = MEMBER_PATH_PREFIX + clusterConfig.getClusterName();
            List<String> children = client.getChildren().forPath(memberPath);

            for (String child : children) {
                nodes.add(new ClusterNode(child,
                    clusterConfig.getBindAddress(),
                    clusterConfig.getBindPort(),
                    ClusterNode.NodeState.ACTIVE,
                    System.currentTimeMillis()));
            }
        } catch (Exception e) {
            logger.error("Failed to get nodes", e);
        }
        return nodes;
    }

    @Override
    public ClusterNode getLocalNode() {
        if (!joined) return null;
        return new ClusterNode(nodeId, clusterConfig.getBindAddress(),
            clusterConfig.getBindPort(), ClusterNode.NodeState.ACTIVE, System.currentTimeMillis());
    }

    @Override
    public boolean isLeader() {
        return isLeader && joined;
    }

    @Override
    public void addListener(ClusterEventListener listener) {
        if (listener != null) listeners.add(listener);
    }

    @Override
    public void removeListener(ClusterEventListener listener) {
        if (listener != null) listeners.remove(listener);
    }

    @Override
    public boolean isJoined() {
        return joined;
    }

    @Override
    public void close() throws Exception {
        leave();
    }

    private void registerMember() {
        try {
            String memberPath = MEMBER_PATH_PREFIX + clusterConfig.getClusterName();
            // Create parent path if needed
            if (client.checkExists().forPath(memberPath) == null) {
                client.create().creatingParentsIfNeeded().forPath(memberPath);
            }

            // Create ephemeral node for this member
            String nodePath = memberPath + "/" + nodeId;
            client.create().withMode(org.apache.zookeeper.CreateMode.EPHEMERAL)
                .forPath(nodePath, String.valueOf(System.currentTimeMillis()).getBytes());
        } catch (Exception e) {
            logger.error("Failed to register member", e);
        }
    }

    private void unregisterMember() {
        try {
            String memberPath = MEMBER_PATH_PREFIX + clusterConfig.getClusterName();
            String nodePath = memberPath + "/" + nodeId;
            if (client.checkExists().forPath(nodePath) != null) {
                client.delete().forPath(nodePath);
            }
        } catch (Exception e) {
            logger.error("Failed to unregister member", e);
        }
    }

    private void notifyLeaderChanged(ClusterNode node) {
        for (ClusterEventListener listener : listeners) {
            try {
                listener.onLeaderChanged(node);
            } catch (Exception e) {
                logger.error("Listener error", e);
            }
        }
    }

    /**
     * Returns the Curator framework client.
     *
     * @return the client
     */
    public CuratorFramework getClient() {
        return client;
    }

    /**
     * Returns the node ID.
     *
     * @return the unique node identifier
     */
    public String getNodeId() {
        return nodeId;
    }
}
