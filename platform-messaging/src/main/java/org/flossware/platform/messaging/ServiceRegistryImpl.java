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

package org.flossware.platform.messaging;

import org.flossware.platform.api.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service registry for inter-application service discovery.
 * Allows applications to register and lookup services.
 * <p>
 * This implementation provides a type-safe service registry where applications
 * can register service implementations and other applications can discover them
 * by interface type. Multiple implementations of the same interface are supported.
 * <p>
 * Example usage:
 * {@code
 * ServiceRegistryImpl registry = new ServiceRegistryImpl();
 *
 * // Register a service
 * MyService service = new MyServiceImpl();
 * registry.registerService(MyService.class, service);
 *
 * // Lookup a service
 * Optional<MyService> found = registry.getService(MyService.class);
 * found.ifPresent(s -> s.doSomething());
 *
 * // Get all implementations
 * List<MyService> allServices = registry.getAllServices(MyService.class);
 *
 * // Unregister
 * registry.unregisterService(MyService.class, service);
 * }
 *
 * @see ServiceRegistry
 */
public class ServiceRegistryImpl implements ServiceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRegistryImpl.class);

    private final Map<Class<?>, List<ServiceEntry>> services;

    /**
     * Creates a new service registry.
     */
    public ServiceRegistryImpl() {
        this.services = new ConcurrentHashMap<>();
        LOGGER.info("ServiceRegistry created");
    }

    /**
     * Registers a service implementation for the specified interface.
     * <p>
     * The service interface must be an interface type, and the implementation
     * must implement that interface. Multiple implementations can be registered
     * for the same interface.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class
     * @param implementation the implementation instance
     * @throws NullPointerException if serviceInterface or implementation is null
     * @throws IllegalArgumentException if serviceInterface is not an interface or
     *                                  implementation does not implement the interface
     */
    @Override
    public <T> void registerService(Class<T> serviceInterface, T implementation) {
        registerService(serviceInterface, implementation, null);
    }

    /**
     * Registers a service implementation with a semantic version.
     * Note: This implementation currently ignores the version parameter.
     * Version support may be added in a future release.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class
     * @param implementation the implementation instance
     * @param version the semantic version (currently ignored)
     * @throws NullPointerException if serviceInterface or implementation is null
     * @throws IllegalArgumentException if serviceInterface is not an interface or
     *                                  implementation does not implement the interface
     */
    @Override
    public <T> void registerService(Class<T> serviceInterface, T implementation, String version) {
        Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");
        Objects.requireNonNull(implementation, "implementation cannot be null");

        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException("serviceInterface must be an interface, got: " + serviceInterface);
        }

        if (!serviceInterface.isInstance(implementation)) {
            throw new IllegalArgumentException(
                    "implementation must implement " + serviceInterface.getName());
        }

        ServiceEntry entry = new ServiceEntry(implementation);

        services.computeIfAbsent(serviceInterface, k -> new CopyOnWriteArrayList<>())
                .add(entry);

        if (version != null) {
            LOGGER.info("Registered service: {} -> {} (version: {})", serviceInterface.getName(),
                    implementation.getClass().getName(), version);
        } else {
            LOGGER.info("Registered service: {} -> {}", serviceInterface.getName(),
                    implementation.getClass().getName());
        }
    }

    /**
     * Returns the first registered implementation for the specified service interface.
     * <p>
     * If multiple implementations are registered, returns the first one that was registered.
     * Use {@link #getAllServices(Class)} to get all implementations.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class to lookup
     * @return an Optional containing the first registered implementation, or empty if none found
     * @throws NullPointerException if serviceInterface is null
     */
    @Override
    public <T> Optional<T> getService(Class<T> serviceInterface) {
        Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");

        List<ServiceEntry> entries = services.get(serviceInterface);

        if (entries == null || entries.isEmpty()) {
            LOGGER.debug("No service found for: {}", serviceInterface.getName());
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        T service = (T) entries.get(0).getImplementation();
        return Optional.of(service);
    }

    /**
     * Returns a service matching the minimum version requirement.
     * Note: This implementation currently ignores the version parameter and returns
     * the first registered service. Version support may be added in a future release.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class to lookup
     * @param minVersion the minimum required version (currently ignored)
     * @return an Optional containing the first registered implementation, or empty if none found
     * @throws NullPointerException if serviceInterface or minVersion is null
     */
    @Override
    public <T> Optional<T> getService(Class<T> serviceInterface, String minVersion) {
        Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");
        Objects.requireNonNull(minVersion, "minVersion cannot be null");

        // For now, just return the first service (no version checking)
        return getService(serviceInterface);
    }

    /**
     * Returns all registered implementations for the specified service interface.
     * <p>
     * Returns an empty list if no implementations are registered.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class to lookup
     * @return a list of all registered implementations (never null, may be empty)
     * @throws NullPointerException if serviceInterface is null
     */
    @Override
    public <T> List<T> getAllServices(Class<T> serviceInterface) {
        Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");

        List<ServiceEntry> entries = services.get(serviceInterface);

        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> result = new ArrayList<>();
        for (ServiceEntry entry : entries) {
            @SuppressWarnings("unchecked")
            T service = (T) entry.getImplementation();
            result.add(service);
        }

        LOGGER.debug("Found {} services for: {}", result.size(), serviceInterface.getName());
        return result;
    }

    /**
     * Unregisters a specific service implementation.
     * <p>
     * Removes the specified implementation from the registry. If this was the last
     * implementation for the interface, the interface entry is removed from the registry.
     * Uses identity comparison (==) to match implementations.
     *
     * @param serviceInterface the interface class
     * @param implementation the implementation instance to remove
     */
    @Override
    public void unregisterService(Class<?> serviceInterface, Object implementation) {
        Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");
        Objects.requireNonNull(implementation, "implementation cannot be null");

        List<ServiceEntry> entries = services.get(serviceInterface);

        if (entries != null) {
            // Remove only first matching registration to maintain symmetry with register
            Iterator<ServiceEntry> iter = entries.iterator();
            boolean removed = false;
            while (iter.hasNext() && !removed) {
                if (iter.next().getImplementation() == implementation) {
                    iter.remove();
                    removed = true;
                }
            }

            if (removed) {
                LOGGER.info("Unregistered service: {} -> {}", serviceInterface.getName(),
                        implementation.getClass().getName());
            }

            if (entries.isEmpty()) {
                services.remove(serviceInterface);
            }
        }
    }

    /**
     * Removes all registered services from the registry.
     */
    public void clear() {
        int count = services.values().stream().mapToInt(List::size).sum();
        services.clear();
        LOGGER.info("Cleared all {} registered services", count);
    }

    private static class ServiceEntry {
        private final Object implementation;
        private final long registrationTime;

        ServiceEntry(Object implementation) {
            this.implementation = implementation;
            this.registrationTime = System.currentTimeMillis();
        }

        Object getImplementation() {
            return implementation;
        }

        long getRegistrationTime() {
            return registrationTime;
        }
    }
}
