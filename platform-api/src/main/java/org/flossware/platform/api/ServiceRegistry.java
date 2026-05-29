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

package org.flossware.platform.api;

import java.util.List;
import java.util.Optional;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Service registry for inter-application service discovery. Allows applications to register
 * services and look up services provided by other applications.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Register a service with version
 * MyService impl = new MyServiceImpl();
 * registry.registerService(MyService.class, impl, "1.2.3");
 *
 * // Look up a service with minimum version requirement
 * Optional<MyService> service = registry.getService(MyService.class, "1.0.0");
 * service.ifPresent(s -> s.doSomething());
 * }</pre>
 *
 * @see ApplicationContext#getServiceRegistry()
 */
public interface ServiceRegistry {
  /**
   * Registers a service implementation under the specified interface. Multiple implementations can
   * be registered for the same interface.
   *
   * @param <T> the service interface type
   * @param serviceInterface the interface class
   * @param implementation the service implementation
   * @throws NullPointerException if either parameter is null
   * @throws IllegalArgumentException if serviceInterface is not an interface or implementation does
   *     not implement the interface
   */
  <T> void registerService(@NonNull Class<T> serviceInterface, @NonNull T implementation);

  /**
   * Registers a service implementation with a semantic version under the specified interface.
   * Multiple implementations can be registered for the same interface.
   *
   * @param <T> the service interface type
   * @param serviceInterface the interface class
   * @param implementation the service implementation
   * @param version the semantic version string (e.g., "1.2.3")
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if serviceInterface is not an interface, implementation does
   *     not implement the interface, or version format is invalid
   */
  <T> void registerService(
      @NonNull Class<T> serviceInterface, @NonNull T implementation, @NonNull String version);

  /**
   * Returns the first registered service for the specified interface. If multiple services are
   * registered, returns the first one.
   *
   * @param <T> the service interface type
   * @param serviceInterface the interface class
   * @return optional containing the service, or empty if none found
   */
  @NonNull
  <T> Optional<T> getService(@NonNull Class<T> serviceInterface);

  /**
   * Returns a service that satisfies the minimum version requirement. If multiple compatible
   * services are registered, returns the first one that meets the version requirement.
   *
   * <p>Version compatibility follows semantic versioning rules: a service is compatible if it has
   * the same major version and is greater than or equal to the minimum required version.
   *
   * @param <T> the service interface type
   * @param serviceInterface the interface class
   * @param minVersion the minimum required semantic version (e.g., "1.2.0")
   * @return optional containing a compatible service, or empty if none found
   * @throws NullPointerException if either parameter is null
   * @throws IllegalArgumentException if minVersion format is invalid
   */
  @NonNull
  <T> Optional<T> getService(
      @NonNull Class<T> serviceInterface, @NonNull String minVersion);

  /**
   * Returns all registered services for the specified interface.
   *
   * @param <T> the service interface type
   * @param serviceInterface the interface class
   * @return list of all registered services, empty if none found
   */
  @NonNull
  <T> List<T> getAllServices(@NonNull Class<T> serviceInterface);

  /**
   * Unregisters a specific service implementation.
   *
   * @param serviceInterface the interface class
   * @param implementation the service implementation to remove
   */
  void unregisterService(@NonNull Class<?> serviceInterface, @NonNull Object implementation);
}
