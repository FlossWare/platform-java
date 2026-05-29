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

package org.flossware.jplatform.registry.etcd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.options.PutOption;
import org.flossware.jplatform.api.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * etcd-based implementation of ServiceRegistry.
 * Stores service registrations in etcd with automatic lease expiration.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Stores service metadata in etcd KV store</li>
 *   <li>Uses leases for automatic cleanup of dead services</li>
 *   <li>Maintains local cache for fast service lookups</li>
 *   <li>Publishes service availability while keeping local instances</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections.
 *
 * @since 1.1
 */
public class EtcdServiceRegistry implements ServiceRegistry, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(EtcdServiceRegistry.class);
    private static final String SERVICE_KEY_PREFIX = "/jplatform/services/";

    private final EtcdRegistryConfig config;
    private Client client;
    private final Map<Class<?>, List<Object>> localServices;
    private final Map<String, Long> registeredServices;
    private final Map<Object, String> serviceToKey;  // implementation -> etcd key mapping
    private final ObjectMapper mapper;
    private ScheduledExecutorService leaseRenewalExecutor;
    private volatile boolean started = false;
    private final Object startLock = new Object();

    /**
     * Constructs a new etcd service registry.
     *
     * @param config the etcd registry configuration
     */
    public EtcdServiceRegistry(EtcdRegistryConfig config) {
        this.config = config;
        this.localServices = new ConcurrentHashMap<>();
        this.registeredServices = new ConcurrentHashMap<>();
        this.serviceToKey = new ConcurrentHashMap<>();
        this.mapper = new ObjectMapper();
    }

    /**
     * Package-private constructor for testing.
     *
     * @param config the configuration
     * @param client the etcd client
     */
    EtcdServiceRegistry(EtcdRegistryConfig config, Client client) {
        this.config = config;
        this.client = client;
        this.localServices = new ConcurrentHashMap<>();
        this.registeredServices = new ConcurrentHashMap<>();
        this.serviceToKey = new ConcurrentHashMap<>();
        this.mapper = new ObjectMapper();
    }

    /**
     * Initializes the etcd client connection.
     * Must be called before using the registry.
     */
    public void start() {
        synchronized (startLock) {
            if (started) {
                logger.warn("EtcdServiceRegistry already started, ignoring start() call");
                return;
            }

            long leaseTtl = config.getLeaseTtl();

            // Warn if TTL is very large (services take long to disappear after crash)
            if (leaseTtl > 3600) {
                logger.warn("Lease TTL is very large ({} seconds). " +
                           "Services may take up to {} seconds to disappear after crash.",
                           leaseTtl, leaseTtl);
            }

            if (client == null) {
                client = Client.builder()
                    .endpoints(config.getEndpoints().toArray(new String[0]))
                    .build();
                logger.info("Created etcd client with endpoints: {}", config.getEndpoints());
            }

            leaseRenewalExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "etcd-lease-renewal");
                t.setDaemon(true);
                return t;
            });

            // Schedule renewal at 1/3 of TTL to provide safety margin
            long renewalPeriod = leaseTtl / 3;
            logger.info("Starting lease renewal with TTL={} seconds, renewal period={} seconds",
                       leaseTtl, renewalPeriod);

            leaseRenewalExecutor.scheduleAtFixedRate(() -> {
                try {
                    renewLeases();
                } catch (Exception e) {
                    logger.error("Exception during lease renewal, will retry on next cycle", e);
                }
            }, renewalPeriod, renewalPeriod, TimeUnit.SECONDS);

            started = true;
            logger.info("EtcdServiceRegistry started");
        }
    }

    @Override
    public <T> void registerService(Class<T> serviceInterface, T implementation) {
        if (serviceInterface == null || implementation == null) {
            throw new IllegalArgumentException("Service interface and implementation must not be null");
        }

        // Check if already registered (prevents duplicates)
        List<Object> existingServices = localServices.get(serviceInterface);
        if (existingServices != null && existingServices.contains(implementation)) {
            logger.warn("Service {} with implementation {} is already registered, ignoring duplicate",
                       serviceInterface.getName(), implementation.getClass().getName());
            return;
        }

        // Add to local registry
        localServices.computeIfAbsent(serviceInterface, k -> new CopyOnWriteArrayList<>())
            .add(implementation);

        // Publish to etcd
        try {
            String key = SERVICE_KEY_PREFIX + serviceInterface.getName() + "/" + UUID.randomUUID();
            Map<String, String> metadata = new HashMap<>();
            metadata.put("interface", serviceInterface.getName());
            metadata.put("timestamp", String.valueOf(System.currentTimeMillis()));

            String json = mapper.writeValueAsString(metadata);

            // Create lease
            Lease leaseClient = client.getLeaseClient();
            LeaseGrantResponse leaseResp = leaseClient.grant(config.getLeaseTtl())
                .get(5, TimeUnit.SECONDS);
            long leaseId = leaseResp.getID();

            // Put with lease
            KV kvClient = client.getKVClient();
            kvClient.put(
                ByteSequence.from(key, StandardCharsets.UTF_8),
                ByteSequence.from(json, StandardCharsets.UTF_8),
                PutOption.newBuilder().withLeaseId(leaseId).build()
            ).get(5, TimeUnit.SECONDS);

            registeredServices.put(key, leaseId);
            serviceToKey.put(implementation, key);  // Track service-to-key mapping

            logger.info("Registered service {} in etcd at key {}", serviceInterface.getName(), key);
        } catch (Exception e) {
            // Remove from local registry if etcd registration failed
            List<Object> services = localServices.get(serviceInterface);
            if (services != null) {
                services.remove(implementation);
            }
            logger.error("Failed to register service in etcd: " + serviceInterface.getName(), e);
            throw new RuntimeException("Failed to register service in etcd", e);
        }
    }

    @Override
    public <T> Optional<T> getService(Class<T> serviceInterface) {
        List<T> services = getAllServices(serviceInterface);
        return services.isEmpty() ? Optional.empty() : Optional.of(services.get(0));
    }

    @Override
    public <T> List<T> getAllServices(Class<T> serviceInterface) {
        if (serviceInterface == null) {
            throw new IllegalArgumentException("Service interface must not be null");
        }

        List<Object> services = localServices.get(serviceInterface);
        if (services == null) {
            return Collections.emptyList();
        }

        // Validate that all elements are of the correct type
        List<T> result = new ArrayList<>(services.size());
        for (Object service : services) {
            if (!serviceInterface.isInstance(service)) {
                logger.error("Service registry corruption: found {} in registry for {}",
                            service.getClass().getName(), serviceInterface.getName());
                throw new IllegalStateException(
                    "Service registry corrupted: service " + service.getClass().getName() +
                    " does not implement " + serviceInterface.getName()
                );
            }
            result.add(serviceInterface.cast(service));  // Safe cast
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public void unregisterService(Class<?> serviceInterface, Object implementation) {
        if (serviceInterface == null || implementation == null) {
            return;
        }

        // Remove from local registry
        List<Object> services = localServices.get(serviceInterface);
        if (services != null) {
            services.remove(implementation);
            if (services.isEmpty()) {
                localServices.remove(serviceInterface);
            }
        }

        // Remove from etcd
        String key = serviceToKey.remove(implementation);
        if (key != null && client != null) {
            Long leaseId = registeredServices.remove(key);
            try {
                // Revoke lease to immediately remove from etcd
                if (leaseId != null) {
                    client.getLeaseClient().revoke(leaseId).get(5, TimeUnit.SECONDS);
                }
                // Also delete the key directly
                client.getKVClient().delete(ByteSequence.from(key, StandardCharsets.UTF_8))
                    .get(5, TimeUnit.SECONDS);

                logger.info("Unregistered service {} from etcd and local registry",
                           serviceInterface.getName());
            } catch (Exception e) {
                logger.error("Failed to unregister service from etcd: " + serviceInterface.getName(), e);
            }
        } else {
            logger.info("Unregistered service {} from local registry only (not in etcd)",
                       serviceInterface.getName());
        }
    }

    @Override
    public void close() {
        synchronized (startLock) {
            if (!started) {
                logger.debug("EtcdServiceRegistry not started, nothing to close");
                return;
            }

            // Shutdown lease renewal executor and wait for termination
            if (leaseRenewalExecutor != null) {
                leaseRenewalExecutor.shutdown();
                try {
                    if (!leaseRenewalExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.warn("Lease renewal executor did not terminate in time");
                        leaseRenewalExecutor.shutdownNow();
                        if (!leaseRenewalExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                            logger.error("Lease renewal executor did not terminate");
                        }
                    }
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for executor termination");
                    leaseRenewalExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // Now safe to revoke all leases
            if (client != null) {
                Lease leaseClient = client.getLeaseClient();
                for (Long leaseId : registeredServices.values()) {
                    try {
                        leaseClient.revoke(leaseId).get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        logger.error("Failed to revoke lease: " + leaseId, e);
                    }
                }
                client.close();
            }

            localServices.clear();
            registeredServices.clear();
            serviceToKey.clear();

            started = false;
            logger.info("EtcdServiceRegistry closed");
        }
    }

    private void renewLeases() {
        if (client == null) return;

        Lease leaseClient = client.getLeaseClient();
        for (Long leaseId : registeredServices.values()) {
            try {
                leaseClient.keepAliveOnce(leaseId).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("Failed to renew lease: " + leaseId, e);
            }
        }
    }

    /**
     * Returns the etcd client.
     *
     * @return the client
     */
    public Client getClient() {
        return client;
    }
}
