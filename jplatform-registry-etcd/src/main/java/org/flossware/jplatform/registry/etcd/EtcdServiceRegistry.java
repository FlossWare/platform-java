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
    private final ObjectMapper mapper;
    private ScheduledExecutorService leaseRenewalExecutor;

    /**
     * Constructs a new etcd service registry.
     *
     * @param config the etcd registry configuration
     */
    public EtcdServiceRegistry(EtcdRegistryConfig config) {
        this.config = config;
        this.localServices = new ConcurrentHashMap<>();
        this.registeredServices = new ConcurrentHashMap<>();
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
        this.mapper = new ObjectMapper();
    }

    /**
     * Initializes the etcd client connection.
     * Must be called before using the registry.
     */
    public void start() {
        if (client == null) {
            client = Client.builder()
                .endpoints(config.getEndpoints().toArray(new String[0]))
                .build();
        }

        leaseRenewalExecutor = Executors.newSingleThreadScheduledExecutor();
        leaseRenewalExecutor.scheduleAtFixedRate(() -> {
            try {
                renewLeases();
            } catch (Exception e) {
                logger.error("Exception during lease renewal, will retry on next cycle", e);
            }
        }, config.getLeaseTtl() / 2, config.getLeaseTtl() / 2, TimeUnit.SECONDS);
    }

    @Override
    public <T> void registerService(Class<T> serviceInterface, T implementation) {
        if (serviceInterface == null || implementation == null) {
            throw new IllegalArgumentException("Service interface and implementation must not be null");
        }

        // Add to local registry
        localServices.computeIfAbsent(serviceInterface, k -> new CopyOnWriteArrayList<>())
            .add(implementation);

        // Publish to etcd
        try {
            String key = SERVICE_KEY_PREFIX + serviceInterface.getName() + "/" + UUID.randomUUID();
            Map<String, String> metadata = new HashMap<>();
            metadata.put("interface", serviceInterface.getName());
            metadata.put("implementation", implementation.getClass().getName());
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

            logger.info("Registered service {} in etcd", serviceInterface.getName());
        } catch (Exception e) {
            logger.error("Failed to register service in etcd: " + serviceInterface.getName(), e);
        }
    }

    @Override
    public <T> Optional<T> getService(Class<T> serviceInterface) {
        List<T> services = getAllServices(serviceInterface);
        return services.isEmpty() ? Optional.empty() : Optional.of(services.get(0));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getAllServices(Class<T> serviceInterface) {
        List<Object> services = localServices.get(serviceInterface);
        if (services == null) {
            return Collections.emptyList();
        }
        return (List<T>) new ArrayList<>(services);
    }

    @Override
    public void unregisterService(Class<?> serviceInterface, Object implementation) {
        if (serviceInterface == null || implementation == null) {
            return;
        }

        List<Object> services = localServices.get(serviceInterface);
        if (services != null) {
            services.remove(implementation);
            if (services.isEmpty()) {
                localServices.remove(serviceInterface);
            }
        }

        logger.info("Unregistered service {} from local registry", serviceInterface.getName());
    }

    @Override
    public void close() {
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
