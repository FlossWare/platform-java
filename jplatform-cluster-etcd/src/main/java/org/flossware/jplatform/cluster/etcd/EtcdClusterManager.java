package org.flossware.jplatform.cluster.etcd;

import io.etcd.jetcd.*;
import io.etcd.jetcd.lock.LockResponse;
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

    private final EtcdConfig config;
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

            leaseId = leaseClient.grant(this.config.getLeaseTtl()).get().getID();
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
        try {
            if (scheduler != null) {
                scheduler.shutdown();
            }
            if (leaseClient != null && leaseId > 0) {
                leaseClient.revoke(leaseId).get();
            }
            if (etcdClient != null) {
                etcdClient.close();
            }
            joined = false;
            isLeader = false;
        } catch (Exception e) {
            throw new ClusterLeaveException("Failed to leave", e);
        }
    }

    @Override
    public Set<ClusterNode> getNodes() {
        return Collections.emptySet();
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
                leaseClient.keepAliveOnce(leaseId).get();
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
