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

package org.flossware.jplatform.registry.consul;

import com.orbitz.consul.Consul;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import org.flossware.jplatform.api.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Consul-based implementation of ServiceRegistry.
 * Provides distributed service discovery by publishing service metadata
 * to Consul while maintaining local service instances.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Local service storage with in-memory registry</li>
 *   <li>Service metadata published to Consul for discovery</li>
 *   <li>Health checking via Consul TTL checks</li>
 *   <li>Support for multiple implementations per interface</li>
 *   <li>Thread-safe concurrent access</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ConsulRegistryConfig config = ConsulRegistryConfig.builder()
 *     .consulHost("localhost")
 *     .consulPort(8500)
 *     .nodeId("node-1")
 *     .build();
 *
 * ConsulServiceRegistry registry = new ConsulServiceRegistry(config);
 *
 * // Register a service
 * MyService impl = new MyServiceImpl();
 * registry.registerService(MyService.class, impl);
 *
 * // Lookup service
 * Optional<MyService> service = registry.getService(MyService.class);
 * }</pre>
 *
 * @see ServiceRegistry
 * @see ConsulRegistryConfig
 * @since 1.1
 */
public class ConsulServiceRegistry implements ServiceRegistry, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ConsulServiceRegistry.class);

    private final ConsulRegistryConfig config;
    private final Consul consulClient;
    private final AgentClient agentClient;
    private final KeyValueClient kvClient;
    private final Map<Class<?>, List<Object>> localServices;
    private final Map<ServiceKey, String> registeredServiceIds;

    /**
     * Composite key for tracking service registrations.
     * Uses object identity to distinguish between different instances
     * of the same implementation class registered for the same interface.
     */
    private static class ServiceKey {
        private final Class<?> serviceInterface;
        private final Object implementation;

        ServiceKey(Class<?> serviceInterface, Object implementation) {
            this.serviceInterface = serviceInterface;
            this.implementation = implementation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ServiceKey)) return false;
            ServiceKey that = (ServiceKey) o;
            return serviceInterface.equals(that.serviceInterface) &&
                   implementation == that.implementation;  // Identity comparison
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceInterface, System.identityHashCode(implementation));
        }
    }

    /**
     * Constructs a new Consul service registry with the specified configuration.
     *
     * @param config the Consul registry configuration
     */
    public ConsulServiceRegistry(ConsulRegistryConfig config) {
        this.config = config;
        this.localServices = new ConcurrentHashMap<>();
        this.registeredServiceIds = new ConcurrentHashMap<>();

        // Create Consul client
        this.consulClient = Consul.builder()
            .withHostAndPort(
                com.google.common.net.HostAndPort.fromParts(
                    config.getConsulHost(),
                    config.getConsulPort()
                )
            )
            .build();

        this.agentClient = consulClient.agentClient();
        this.kvClient = consulClient.keyValueClient();

        logger.info("ConsulServiceRegistry initialized with node ID: {}", config.getNodeId());
    }

    /**
     * Package-private constructor for testing.
     * Allows injection of a mock Consul client.
     *
     * @param config the Consul registry configuration
     * @param consulClient the Consul client to use
     */
    ConsulServiceRegistry(ConsulRegistryConfig config, Consul consulClient) {
        this.config = config;
        this.consulClient = consulClient;
        this.agentClient = consulClient.agentClient();
        this.kvClient = consulClient.keyValueClient();
        this.localServices = new ConcurrentHashMap<>();
        this.registeredServiceIds = new ConcurrentHashMap<>();

        logger.info("ConsulServiceRegistry initialized (test mode) with node ID: {}", config.getNodeId());
    }

    /**
     * Registers a service implementation under the specified interface.
     * The service is stored locally and its metadata is published to Consul.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class
     * @param implementation the service implementation
     * @throws NullPointerException if either parameter is null
     * @throws IllegalArgumentException if serviceInterface is not an interface or
     *         implementation does not implement the interface
     */
    @Override
    public <T> void registerService(Class<T> serviceInterface, T implementation) {
        Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");
        Objects.requireNonNull(implementation, "implementation cannot be null");

        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException(serviceInterface + " is not an interface");
        }

        if (!serviceInterface.isInstance(implementation)) {
            throw new IllegalArgumentException(
                implementation.getClass() + " does not implement " + serviceInterface);
        }

        logger.debug("Registering service: {} with implementation: {}",
            serviceInterface.getName(), implementation.getClass().getName());

        // Register in Consul first to ensure consistency
        registerInConsul(serviceInterface, implementation);

        // Only add to local registry if Consul registration succeeds
        localServices.computeIfAbsent(serviceInterface, k -> new CopyOnWriteArrayList<>())
            .add(implementation);

        logger.info("Registered service: {}", serviceInterface.getSimpleName());
    }

    /**
     * Returns the first registered service for the specified interface.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class
     * @return optional containing the service, or empty if none found
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getService(Class<T> serviceInterface) {
        List<Object> services = localServices.get(serviceInterface);
        if (services != null && !services.isEmpty()) {
            return Optional.of((T) services.get(0));
        }
        return Optional.empty();
    }

    /**
     * Returns all registered services for the specified interface.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class
     * @return list of all registered services, empty if none found
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getAllServices(Class<T> serviceInterface) {
        List<Object> services = localServices.get(serviceInterface);
        if (services != null) {
            return (List<T>) new ArrayList<>(services);
        }
        return new ArrayList<>();
    }

    /**
     * Unregisters a specific service implementation.
     * Removes the service from local registry and Consul.
     *
     * @param serviceInterface the interface class
     * @param implementation the service implementation to remove
     */
    @Override
    public void unregisterService(Class<?> serviceInterface, Object implementation) {
        logger.debug("Unregistering service: {} with implementation: {}",
            serviceInterface.getName(), implementation.getClass().getName());

        // Remove from local registry
        List<Object> services = localServices.get(serviceInterface);
        if (services != null) {
            services.remove(implementation);
            if (services.isEmpty()) {
                localServices.remove(serviceInterface);
            }
        }

        // Unregister from Consul
        unregisterFromConsul(serviceInterface, implementation);

        logger.info("Unregistered service: {}", serviceInterface.getSimpleName());
    }

    /**
     * Closes the service registry and releases resources.
     * Unregisters all services from Consul.
     *
     * @throws Exception if closing fails
     */
    @Override
    public void close() throws Exception {
        logger.info("Closing ConsulServiceRegistry");

        // Unregister all services from Consul
        for (String serviceId : registeredServiceIds.values()) {
            try {
                agentClient.deregister(serviceId);
                logger.debug("Deregistered service: {}", serviceId);
            } catch (Exception e) {
                logger.error("Error deregistering service: {}", serviceId, e);
            }
        }

        registeredServiceIds.clear();
        localServices.clear();

        logger.info("ConsulServiceRegistry closed");
    }

    /**
     * Registers a service in Consul's service catalog.
     *
     * @param serviceInterface the service interface
     * @param implementation the service implementation
     */
    private void registerInConsul(Class<?> serviceInterface, Object implementation) {
        try {
            String serviceName = config.getServicePrefix() + "-" +
                serviceInterface.getSimpleName().toLowerCase();
            String serviceId = serviceName + "-" + config.getNodeId() + "-" +
                UUID.randomUUID().toString();

            Registration registration = ImmutableRegistration.builder()
                .id(serviceId)
                .name(serviceName)
                .check(Registration.RegCheck.ttl(config.getServiceTtl()))
                .addTags(
                    "interface:" + serviceInterface.getName(),
                    "implementation:" + implementation.getClass().getName(),
                    "node:" + config.getNodeId()
                )
                .build();

            agentClient.register(registration);

            // Pass health check
            try {
                agentClient.pass(serviceId);
            } catch (com.orbitz.consul.NotRegisteredException e) {
                logger.warn("Service health check not yet available: {}", serviceId);
            }

            // Store service ID for later cleanup using composite key
            ServiceKey key = new ServiceKey(serviceInterface, implementation);
            registeredServiceIds.put(key, serviceId);

            // Store metadata in Consul KV
            String kvKey = "jplatform/services/" + config.getNodeId() + "/" + serviceInterface.getName();
            kvClient.putValue(kvKey, implementation.getClass().getName());

            logger.debug("Registered service in Consul: {} with ID: {}", serviceName, serviceId);

        } catch (Exception e) {
            logger.error("Failed to register service in Consul: {}", serviceInterface.getName(), e);
            throw new RuntimeException("Failed to register service in Consul", e);
        }
    }

    /**
     * Unregisters a service from Consul's service catalog.
     *
     * @param serviceInterface the service interface
     * @param implementation the service implementation
     */
    private void unregisterFromConsul(Class<?> serviceInterface, Object implementation) {
        try {
            ServiceKey key = new ServiceKey(serviceInterface, implementation);
            String serviceId = registeredServiceIds.remove(key);

            if (serviceId != null) {
                agentClient.deregister(serviceId);
                logger.debug("Unregistered service from Consul: {}", serviceId);

                // Remove metadata from Consul KV
                String kvKey = "jplatform/services/" + config.getNodeId() + "/" + serviceInterface.getName();
                kvClient.deleteKey(kvKey);
            }

        } catch (Exception e) {
            logger.error("Failed to unregister service from Consul: {}", serviceInterface.getName(), e);
        }
    }

    /**
     * Returns the Consul client instance.
     * Useful for accessing Consul-specific features.
     *
     * @return the Consul client
     */
    public Consul getConsulClient() {
        return consulClient;
    }

    /**
     * Returns the registry configuration.
     *
     * @return the configuration
     */
    public ConsulRegistryConfig getConfig() {
        return config;
    }

    /**
     * Returns the number of locally registered services.
     *
     * @return the count of service interfaces
     */
    public int getServiceCount() {
        return localServices.size();
    }
}
