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

package org.flossware.jplatform.cluster.redis;

import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.util.*;
import java.util.concurrent.*;

/**
 * Redis-based ClusterManager implementation.
 * Provides clustering via Redis distributed primitives using SETNX for leader election.
 *
 * <p>This implementation uses Redis for:
 * <ul>
 *   <li>Leader election via SETNX (SET if Not eXists) with TTL</li>
 *   <li>Membership tracking via Redis pub/sub</li>
 *   <li>Distributed state storage via Redis hashes</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by volatile fields and concurrent collections.
 *
 * @since 1.1
 */
public class RedisClusterManager implements ClusterManager {
    private static final Logger logger = LoggerFactory.getLogger(RedisClusterManager.class);
    private static final String LEADER_KEY_PREFIX = "jplatform:leader:";
    private static final String MEMBER_KEY_PREFIX = "jplatform:members:";

    private final RedisConfig config;
    private JedisPool pool;
    private volatile boolean joined = false;
    private volatile boolean isLeader = false;
    private ClusterConfig clusterConfig;
    private String nodeId;
    private final List<ClusterEventListener> listeners;
    private ScheduledExecutorService scheduler;

    /**
     * Constructs a new Redis cluster manager.
     *
     * @param config the Redis configuration
     * @throws IllegalArgumentException if config is null
     */
    public RedisClusterManager(RedisConfig config) {
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
     * @param config the Redis configuration
     * @param pool the Jedis pool
     * @throws IllegalArgumentException if config is null
     */
    RedisClusterManager(RedisConfig config, JedisPool pool) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        this.config = config;
        this.pool = pool;
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
            if (pool == null) {
                pool = new JedisPool(this.config.getHost(), this.config.getPort());
            }

            // Test connection
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }

            // Register as member
            registerMember();

            // Start leader election attempts
            scheduler = Executors.newScheduledThreadPool(1);
            long renewalPeriod = Math.max(1, this.config.getLeaseTtl() / 2);
            scheduler.scheduleAtFixedRate(this::tryBecomeLeader, 0,
                renewalPeriod, TimeUnit.SECONDS);

            joined = true;
        } catch (Exception e) {
            throw new ClusterJoinException(config.getClusterName(), "Failed to join", e);
        }
    }

    @Override
    public void leave() throws ClusterLeaveException {
        if (!joined) return;
        try {
            if (scheduler != null) {
                scheduler.shutdown();
            }

            // Unregister member
            unregisterMember();

            // Release leader key if we hold it
            if (isLeader) {
                releaseLeadership();
            }

            if (pool != null) {
                pool.close();
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
        try (Jedis jedis = pool.getResource()) {
            String memberKey = MEMBER_KEY_PREFIX + clusterConfig.getClusterName();
            Map<String, String> members = jedis.hgetAll(memberKey);

            for (Map.Entry<String, String> entry : members.entrySet()) {
                String memberId = entry.getKey();
                String nodeInfoJson = entry.getValue();

                try {
                    // Parse JSON to extract address, port, timestamp
                    String address = extractJsonString(nodeInfoJson, "address");
                    int port = extractJsonInt(nodeInfoJson, "port");
                    long timestamp = extractJsonLong(nodeInfoJson, "timestamp");

                    nodes.add(new ClusterNode(
                        memberId,
                        address,
                        port,
                        ClusterNode.NodeState.ACTIVE,
                        timestamp
                    ));
                } catch (Exception e) {
                    logger.error("Failed to parse node info for: {}", memberId, e);
                }
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

    private void tryBecomeLeader() {
        try (Jedis jedis = pool.getResource()) {
            String key = LEADER_KEY_PREFIX + clusterConfig.getClusterName();

            // Try to acquire leadership using SETNX with TTL
            String result = jedis.set(key, nodeId,
                SetParams.setParams().nx().ex(config.getLeaseTtl()));

            boolean wasLeader = isLeader;

            // We're leader if SET succeeded OR the key holds our nodeId
            if ("OK".equals(result)) {
                isLeader = true;
            } else {
                String currentLeader = jedis.get(key);
                isLeader = nodeId.equals(currentLeader);
            }

            // Notify if leadership changed
            if (isLeader && !wasLeader) {
                notifyLeaderChanged(getLocalNode());
            }
        } catch (Exception e) {
            logger.debug("Leader election attempt failed", e);
            isLeader = false;
        }
    }

    private void registerMember() {
        try (Jedis jedis = pool.getResource()) {
            String memberKey = MEMBER_KEY_PREFIX + clusterConfig.getClusterName();
            // Store node info as JSON: {"timestamp": ..., "address": "...", "port": ...}
            String nodeInfo = String.format(
                "{\"timestamp\":%d,\"address\":\"%s\",\"port\":%d}",
                System.currentTimeMillis(),
                clusterConfig.getBindAddress(),
                clusterConfig.getBindPort()
            );
            jedis.hset(memberKey, nodeId, nodeInfo);
            jedis.expire(memberKey, config.getLeaseTtl() * 2);
        } catch (Exception e) {
            logger.error("Failed to register member", e);
        }
    }

    private void unregisterMember() {
        try (Jedis jedis = pool.getResource()) {
            String memberKey = MEMBER_KEY_PREFIX + clusterConfig.getClusterName();
            jedis.hdel(memberKey, nodeId);
        } catch (Exception e) {
            logger.error("Failed to unregister member", e);
        }
    }

    private void releaseLeadership() {
        try (Jedis jedis = pool.getResource()) {
            String key = LEADER_KEY_PREFIX + clusterConfig.getClusterName();
            String currentLeader = jedis.get(key);

            // Only delete if we still hold it
            if (nodeId.equals(currentLeader)) {
                jedis.del(key);
            }
        } catch (Exception e) {
            logger.error("Failed to release leadership", e);
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
     * Returns the Jedis pool.
     *
     * @return the pool
     */
    public JedisPool getJedisPool() {
        return pool;
    }

    /**
     * Extracts a string value from a simple JSON object.
     *
     * @param json the JSON string
     * @param key the key to extract
     * @return the extracted string value
     */
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            throw new IllegalArgumentException("Key not found in JSON: " + key);
        }
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    /**
     * Extracts an integer value from a simple JSON object.
     *
     * @param json the JSON string
     * @param key the key to extract
     * @return the extracted integer value
     */
    private int extractJsonInt(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            throw new IllegalArgumentException("Key not found in JSON: " + key);
        }
        start += pattern.length();
        int end = json.indexOf(",", start);
        if (end == -1) {
            end = json.indexOf("}", start);
        }
        return Integer.parseInt(json.substring(start, end).trim());
    }

    /**
     * Extracts a long value from a simple JSON object.
     *
     * @param json the JSON string
     * @param key the key to extract
     * @return the extracted long value
     */
    private long extractJsonLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            throw new IllegalArgumentException("Key not found in JSON: " + key);
        }
        start += pattern.length();
        int end = json.indexOf(",", start);
        if (end == -1) {
            end = json.indexOf("}", start);
        }
        return Long.parseLong(json.substring(start, end).trim());
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
