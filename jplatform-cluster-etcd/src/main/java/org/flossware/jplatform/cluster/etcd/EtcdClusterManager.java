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

package org.flossware.jplatform.cluster.etcd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lock.LockResponse;
import io.etcd.jetcd.options.GetOption;
import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * etcd-based ClusterManager implementation.
 * Provides clustering via etcd's distributed coordination primitives.
 *
 * @since 1.1
 */
public class EtcdClusterManager implements ClusterManager {
    private static final Logger logger = LoggerFactory.getLogger(EtcdClusterManager.class);
    private static final String LEADER_KEY_PREFIX = "/jplatform/leader/";
    private static final String NODES_KEY_PREFIX = "/jplatform/nodes/";

    private final EtcdConfig config;
    private final ObjectMapper objectMapper;
    private Client etcdClient;
    private Lock lockClient;
    private Lease leaseClient;
    private volatile boolean joined = false;
    private volatile boolean isLeader = false;
    private ClusterConfig clusterConfig;
    private long leaseId;
    private String nodeId;
    private final List<ClusterEventListener> listeners;
    private ScheduledExecutorService scheduler;

    /**
     * Constructs a new etcd cluster manager.
     *
     * @param config the etcd configuration
     * @throws IllegalArgumentException if config is null
     */
    public EtcdClusterManager(EtcdConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.listeners = new CopyOnWriteArrayList<>();
        this.nodeId = UUID.randomUUID().toString();
    }

    /**
     * Package-private constructor for testing.
     *
     * @param config the etcd configuration
     * @param client the etcd client
     * @throws IllegalArgumentException if config is null
     */
    EtcdClusterManager(EtcdConfig config, Client client) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.etcdClient = client;
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
            if (etcdClient == null) {
                etcdClient = Client.builder()
                    .endpoints(this.config.getEndpoints().toArray(new String[0]))
                    .build();
            }
            lockClient = etcdClient.getLockClient();
            leaseClient = etcdClient.getLeaseClient();

            leaseId = leaseClient.grant(this.config.getLeaseTtl()).get(10, TimeUnit.SECONDS).getID();
            scheduler = Executors.newScheduledThreadPool(1);
            long renewalPeriod = Math.max(1, this.config.getLeaseTtl() / 2);
            scheduler.scheduleAtFixedRate(this::keepAlive, 0, renewalPeriod, TimeUnit.SECONDS);

            joined = true;
            tryBecomeLeader();
        } catch (Exception e) {
            throw new ClusterJoinException(config.getClusterName(), "Failed to join", e);
        }
    }

    @Override
    public void leave() throws ClusterLeaveException {
        if (!joined) return;

        Exception firstException = null;

        // Shutdown scheduler
        if (scheduler != null) {
            try {
                scheduler.shutdown();
            } catch (Exception e) {
                logger.error("Failed to shutdown scheduler", e);
                firstException = e;
            }
        }

        // Revoke lease
        if (leaseClient != null && leaseId > 0) {
            try {
                leaseClient.revoke(leaseId).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("Failed to revoke lease", e);
                if (firstException == null) firstException = e;
            }
        }

        // Close client
        if (etcdClient != null) {
            try {
                etcdClient.close();
            } catch (Exception e) {
                logger.error("Failed to close client", e);
                if (firstException == null) firstException = e;
            }
        }

        joined = false;
        isLeader = false;

        if (firstException != null) {
            throw new ClusterLeaveException("Failed to leave", firstException);
        }
    }

    @Override
    public Set<ClusterNode> getNodes() {
        if (!joined) {
            return Collections.emptySet();
        }

        Set<ClusterNode> nodes = new HashSet<>();
        try {
            KV kvClient = etcdClient.getKVClient();
            GetResponse response = kvClient.get(
                ByteSequence.from(NODES_KEY_PREFIX, StandardCharsets.UTF_8),
                GetOption.newBuilder().isPrefix(true).build()
            ).get(5, TimeUnit.SECONDS);

            for (KeyValue kv : response.getKvs()) {
                String json = kv.getValue().toString(StandardCharsets.UTF_8);
                ClusterNode node = objectMapper.readValue(json, ClusterNode.class);
                nodes.add(node);
            }
        } catch (Exception e) {
            logger.error("Failed to get cluster nodes from etcd", e);
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
        try {
            String key = LEADER_KEY_PREFIX + clusterConfig.getClusterName();
            LockResponse response = lockClient.lock(
                ByteSequence.from(key, StandardCharsets.UTF_8), leaseId).get(5, TimeUnit.SECONDS);
            isLeader = response != null;
            if (isLeader) {
                notifyLeaderChanged(getLocalNode());
            }
        } catch (Exception e) {
            logger.debug("Not leader", e);
            isLeader = false;
        }
    }

    private void keepAlive() {
        if (leaseId > 0) {
            try {
                leaseClient.keepAliveOnce(leaseId).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("Failed to keep alive", e);
            }
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
     * Returns the etcd client.
     *
     * @return the client
     */
    public Client getEtcdClient() {
        return etcdClient;
    }
}
