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

package org.flossware.jplatform.api;

import java.util.List;
import java.util.Optional;

/**
 * Service registry for inter-application service discovery.
 * Allows applications to register services and look up services provided by other applications.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Register a service
 * MyService impl = new MyServiceImpl();
 * registry.registerService(MyService.class, impl);
 *
 * // Look up a service
 * Optional<MyService> service = registry.getService(MyService.class);
 * service.ifPresent(s -> s.doSomething());
 * }</pre>
 *
 * @see ApplicationContext#getServiceRegistry()
 */
public interface ServiceRegistry {
    /**
     * Registers a service implementation under the specified interface.
     * Multiple implementations can be registered for the same interface.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class
     * @param implementation the service implementation
     * @throws NullPointerException if either parameter is null
     * @throws IllegalArgumentException if serviceInterface is not an interface or
     *         implementation does not implement the interface
     */
    <T> void registerService(Class<T> serviceInterface, T implementation);

    /**
     * Returns the first registered service for the specified interface.
     * If multiple services are registered, returns the first one.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class
     * @return optional containing the service, or empty if none found
     */
    <T> Optional<T> getService(Class<T> serviceInterface);

    /**
     * Returns all registered services for the specified interface.
     *
     * @param <T> the service interface type
     * @param serviceInterface the interface class
     * @return list of all registered services, empty if none found
     */
    <T> List<T> getAllServices(Class<T> serviceInterface);

    /**
     * Unregisters a specific service implementation.
     *
     * @param serviceInterface the interface class
     * @param implementation the service implementation to remove
     */
    void unregisterService(Class<?> serviceInterface, Object implementation);
}
