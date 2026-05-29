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

package org.flossware.jplatform.cluster.consul;

import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.SessionClient;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.model.session.ImmutableSession;
import com.orbitz.consul.model.session.Session;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.HealthClient;
import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * Consul-based implementation of ClusterManager.
 * Provides multi-node clustering using Consul's service catalog,
 * sessions, and key/value store for leader election and membership.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Service registration with Consul agent</li>
 *   <li>Leader election via Consul sessions and KV locks</li>
 *   <li>Automatic health checking</li>
 *   <li>Event notifications for membership changes</li>
 *   <li>Session renewal to maintain cluster membership</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ConsulConfig consulConfig = ConsulConfig.builder()
 *     .consulHost("localhost")
 *     .consulPort(8500)
 *     .sessionTtl(10)
 *     .serviceName("jplatform-cluster")
 *     .build();
 *
 * ClusterConfig clusterConfig = ClusterConfig.builder()
 *     .clusterName("production-cluster")
 *     .bindAddress("192.168.1.10")
 *     .bindPort(5701)
 *     .build();
 *
 * ConsulClusterManager cluster = new ConsulClusterManager(consulConfig);
 * cluster.join(clusterConfig);
 * }</pre>
 *
 * @see ClusterManager
 * @see ConsulConfig
 * @since 1.1
 */
public class ConsulClusterManager implements ClusterManager {

    private static final Logger logger = LoggerFactory.getLogger(ConsulClusterManager.class);
    private static final String LEADER_KEY_PREFIX = "jplatform/leader/";
    private static final long SESSION_RENEW_INTERVAL_MS = 5000; // 5 seconds

    private final ConsulConfig consulConfig;
    private Consul consulClient;
    private volatile boolean joined = false;
    private volatile boolean isLeader = false;
    private ClusterConfig clusterConfig;
    private String sessionId;
    private String nodeId;
    private String serviceId;
    private final List<ClusterEventListener> listeners;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> sessionRenewalTask;
    private ScheduledFuture<?> membershipWatchTask;

    /**
     * Constructs a new Consul cluster manager with the specified configuration.
     *
     * @param consulConfig the Consul connection configuration
     * @throws IllegalArgumentException if consulConfig is null
     */
    public ConsulClusterManager(ConsulConfig consulConfig) {
        if (consulConfig == null) {
            throw new IllegalArgumentException("ConsulConfig must not be null");
        }
        this.consulConfig = consulConfig;
        this.listeners = new CopyOnWriteArrayList<>();
        this.nodeId = UUID.randomUUID().toString();
    }

    /**
     * Package-private constructor for testing.
     * Allows injection of a mock Consul client.
     *
     * @param consulConfig the Consul configuration
     * @param consulClient the Consul client to use
     */
    ConsulClusterManager(ConsulConfig consulConfig, Consul consulClient) {
        this.consulConfig = consulConfig;
        this.consulClient = consulClient;
        this.listeners = new CopyOnWriteArrayList<>();
        this.nodeId = UUID.randomUUID().toString();
    }

    /**
     * Joins the cluster with the specified configuration.
     * Registers this node as a service in Consul, creates a session,
     * and attempts leader election.
     *
     * @param config the cluster configuration
     * @throws ClusterJoinException if joining the cluster fails
     */
    @Override
    public void join(ClusterConfig config) throws ClusterJoinException {
        if (joined) {
            throw new IllegalStateException("Already joined to cluster");
        }

        this.clusterConfig = config;
        logger.info("Joining Consul cluster: {}", config.getClusterName());

        try {
            // Create Consul client if not injected (for testing)
            if (consulClient == null) {
                consulClient = Consul.builder()
                    .withHostAndPort(
                        com.google.common.net.HostAndPort.fromParts(
                            consulConfig.getConsulHost(),
                            consulConfig.getConsulPort()
                        )
                    )
                    .build();
            }

            // Register service with Consul
            registerService();

            // Create session for leader election
            createSession();

            // Start scheduler for session renewal and membership watching
            scheduler = Executors.newScheduledThreadPool(2);

            // Schedule session renewal
            sessionRenewalTask = scheduler.scheduleAtFixedRate(
                this::renewSession,
                SESSION_RENEW_INTERVAL_MS,
                SESSION_RENEW_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );

            // Schedule membership watching
            membershipWatchTask = scheduler.scheduleAtFixedRate(
                this::watchMembership,
                1000,
                5000,
                TimeUnit.MILLISECONDS
            );

            joined = true;
            logger.info("Successfully joined Consul cluster: {}", config.getClusterName());

            // Attempt leader election
            tryBecomeLeader();

        } catch (Exception e) {
            logger.error("Failed to join Consul cluster: {}", config.getClusterName(), e);
            cleanup();
            throw new ClusterJoinException(config.getClusterName(), "Failed to join cluster", e);
        }
    }

    /**
     * Leaves the cluster gracefully.
     * Deregisters the service, destroys the session, and releases resources.
     *
     * @throws ClusterLeaveException if leaving the cluster fails
     */
    @Override
    public void leave() throws ClusterLeaveException {
        if (!joined) {
            logger.warn("Not joined to any cluster");
            return;
        }

        logger.info("Leaving Consul cluster: {}", clusterConfig.getClusterName());

        try {
            cleanup();
            joined = false;
            isLeader = false;
            logger.info("Successfully left Consul cluster");

        } catch (Exception e) {
            logger.error("Error leaving Consul cluster", e);
            throw new ClusterLeaveException("Failed to leave cluster", e);
        }
    }

    /**
     * Returns all nodes currently in the cluster.
     * Queries Consul's health API for all healthy instances of the service.
     *
     * @return a set of cluster nodes
     */
    @Override
    public Set<ClusterNode> getNodes() {
        if (!joined || consulClient == null) {
            return Collections.emptySet();
        }

        try {
            HealthClient healthClient = consulClient.healthClient();
            List<ServiceHealth> services = healthClient.getHealthyServiceInstances(
                consulConfig.getServiceName()
            ).getResponse();

            Set<ClusterNode> nodes = new HashSet<>();
            for (ServiceHealth service : services) {
                String id = service.getService().getId();
                String address = service.getService().getAddress();
                int port = service.getService().getPort();
                ClusterNode.NodeState state = ClusterNode.NodeState.ACTIVE;
                long lastHeartbeat = System.currentTimeMillis();

                nodes.add(new ClusterNode(id, address, port, state, lastHeartbeat));
            }

            return nodes;

        } catch (Exception e) {
            logger.error("Error retrieving cluster nodes", e);
            return Collections.emptySet();
        }
    }

    /**
     * Returns the local node information.
     *
     * @return the local cluster node
     */
    @Override
    public ClusterNode getLocalNode() {
        if (!joined) {
            return null;
        }

        String address = clusterConfig.getBindAddress();
        if (address == null || address.isEmpty()) {
            try {
                address = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                address = "127.0.0.1";
            }
        }

        int port = clusterConfig.getBindPort();
        ClusterNode.NodeState state = ClusterNode.NodeState.ACTIVE;
        long lastHeartbeat = System.currentTimeMillis();

        return new ClusterNode(nodeId, address, port, state, lastHeartbeat);
    }

    /**
     * Checks if this node is the cluster leader.
     * Leadership is determined by holding the leader key in Consul KV with this node's session.
     *
     * @return true if this node is the leader
     */
    @Override
    public boolean isLeader() {
        return isLeader && joined;
    }

    /**
     * Adds a listener for cluster events.
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
     * Registers this node as a service in Consul.
     * Creates a unique service ID and registers with health check.
     */
    private void registerService() {
        AgentClient agentClient = consulClient.agentClient();

        serviceId = consulConfig.getServiceName() + "-" + nodeId;

        String address = clusterConfig.getBindAddress();
        if (address == null || address.isEmpty()) {
            try {
                address = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                address = "127.0.0.1";
            }
        }

        Registration registration = ImmutableRegistration.builder()
            .id(serviceId)
            .name(consulConfig.getServiceName())
            .address(address)
            .port(clusterConfig.getBindPort())
            .check(Registration.RegCheck.ttl(consulConfig.getSessionTtl() * 2L))
            .build();

        agentClient.register(registration);

        // Pass the health check
        try {
            agentClient.pass(serviceId);
        } catch (com.orbitz.consul.NotRegisteredException e) {
            logger.warn("Service health check not yet available: {}", serviceId);
        }

        logger.info("Registered service: {} at {}:{}", serviceId, address, clusterConfig.getBindPort());
    }

    /**
     * Creates a Consul session for this node.
     * The session is used for leader election and will auto-expire if not renewed.
     */
    private void createSession() {
        SessionClient sessionClient = consulClient.sessionClient();

        Session session = ImmutableSession.builder()
            .name("jplatform-" + nodeId)
            .ttl(consulConfig.getSessionTtl() + "s")
            .build();

        sessionId = sessionClient.createSession(session).getId();

        logger.info("Created Consul session: {}", sessionId);
    }

    /**
     * Renews the Consul session to prevent expiration.
     * This method is called periodically by the scheduler.
     */
    private void renewSession() {
        if (sessionId == null || consulClient == null) {
            return;
        }

        try {
            SessionClient sessionClient = consulClient.sessionClient();
            sessionClient.renewSession(sessionId);

            // Also pass health check
            try {
                AgentClient agentClient = consulClient.agentClient();
                agentClient.pass(serviceId);
            } catch (com.orbitz.consul.NotRegisteredException e) {
                logger.warn("Service not registered for health check: {}", serviceId);
                // Re-register the service
                registerService();
            }

            logger.debug("Renewed session: {}", sessionId);
        } catch (Exception e) {
            logger.error("Failed to renew session", e);
            // Session may have expired, try to recreate
            try {
                createSession();
                tryBecomeLeader();
            } catch (Exception ex) {
                logger.error("Failed to recreate session", ex);
            }
        }
    }

    /**
     * Watches for membership changes in the cluster.
     * This method is called periodically by the scheduler.
     */
    private void watchMembership() {
        // This is a simplified implementation
        // A production version would use Consul's blocking queries
        // to get real-time notifications
    }

    /**
     * Attempts to acquire leadership by locking the leader key with this node's session.
     */
    private void tryBecomeLeader() {
        if (!joined || sessionId == null) {
            return;
        }

        try {
            KeyValueClient kvClient = consulClient.keyValueClient();
            String leaderKey = LEADER_KEY_PREFIX + clusterConfig.getClusterName();

            // Try to acquire the lock
            boolean acquired = kvClient.acquireLock(leaderKey, nodeId, sessionId);

            boolean wasLeader = isLeader;
            isLeader = acquired;

            if (isLeader && !wasLeader) {
                logger.info("This node is now the cluster LEADER");
                notifyLeaderChanged(getLocalNode());
            } else if (!isLeader && wasLeader) {
                logger.info("This node is no longer the leader");
            } else if (!isLeader) {
                logger.debug("This node is a FOLLOWER");
            }

        } catch (Exception e) {
            logger.error("Error during leader election", e);
            isLeader = false;
        }
    }

    /**
     * Cleans up all resources (session, service registration, scheduler).
     */
    private void cleanup() {
        // Cancel scheduled tasks
        if (sessionRenewalTask != null) {
            sessionRenewalTask.cancel(false);
        }
        if (membershipWatchTask != null) {
            membershipWatchTask.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Release leader lock
        if (isLeader && sessionId != null && consulClient != null) {
            try {
                KeyValueClient kvClient = consulClient.keyValueClient();
                String leaderKey = LEADER_KEY_PREFIX + clusterConfig.getClusterName();
                kvClient.releaseLock(leaderKey, sessionId);
                logger.info("Released leader lock");
            } catch (Exception e) {
                logger.error("Error releasing leader lock", e);
            }
        }

        // Destroy session
        if (sessionId != null && consulClient != null) {
            try {
                SessionClient sessionClient = consulClient.sessionClient();
                sessionClient.destroySession(sessionId);
                sessionId = null;
                logger.info("Destroyed session");
            } catch (Exception e) {
                logger.error("Error destroying session", e);
            }
        }

        // Deregister service
        if (serviceId != null && consulClient != null) {
            try {
                AgentClient agentClient = consulClient.agentClient();
                agentClient.deregister(serviceId);
                logger.info("Deregistered service: {}", serviceId);
            } catch (Exception e) {
                logger.error("Error deregistering service", e);
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

    /**
     * Returns the Consul client instance.
     * Useful for accessing Consul-specific features.
     *
     * @return the Consul client, or null if not joined
     */
    public Consul getConsulClient() {
        return consulClient;
    }

    /**
     * Returns the session ID for this node.
     *
     * @return the session ID, or null if not joined
     */
    public String getSessionId() {
        return sessionId;
    }
}
