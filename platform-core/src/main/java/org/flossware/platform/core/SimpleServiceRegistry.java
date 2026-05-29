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

package org.flossware.platform.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.flossware.platform.api.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple in-memory implementation of service registry for inter-application service discovery.
 *
 * <p>Thread-safe implementation using concurrent data structures. Services are stored in-memory and
 * are lost when the platform restarts.
 *
 * @since 2.3
 */
public class SimpleServiceRegistry implements ServiceRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleServiceRegistry.class);

  // Map of service interface -> list of implementations
  private final Map<Class<?>, CopyOnWriteArrayList<Object>> services;

  /** Creates a new empty service registry. */
  public SimpleServiceRegistry() {
    this.services = new ConcurrentHashMap<>();
  }

  @Override
  public <T> void registerService(Class<T> serviceInterface, T implementation) {
    Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");
    Objects.requireNonNull(implementation, "implementation cannot be null");

    if (!serviceInterface.isInterface()) {
      throw new IllegalArgumentException(serviceInterface.getName() + " is not an interface");
    }

    if (!serviceInterface.isInstance(implementation)) {
      throw new IllegalArgumentException(
          implementation.getClass().getName()
              + " does not implement "
              + serviceInterface.getName());
    }

    services
        .computeIfAbsent(serviceInterface, k -> new CopyOnWriteArrayList<>())
        .add(implementation);

    LOGGER.info(
        "Registered service: {} -> {}",
        serviceInterface.getName(),
        implementation.getClass().getName());
  }

  @Override
  public <T> Optional<T> getService(Class<T> serviceInterface) {
    Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");

    CopyOnWriteArrayList<Object> implementations = services.get(serviceInterface);
    if (implementations == null || implementations.isEmpty()) {
      LOGGER.debug("No service found for interface: {}", serviceInterface.getName());
      return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    T service = (T) implementations.get(0);
    return Optional.of(service);
  }

  @Override
  public <T> List<T> getAllServices(Class<T> serviceInterface) {
    Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");

    CopyOnWriteArrayList<Object> implementations = services.get(serviceInterface);
    if (implementations == null || implementations.isEmpty()) {
      return new ArrayList<>();
    }

    List<T> result = new ArrayList<>(implementations.size());
    for (Object impl : implementations) {
      @SuppressWarnings("unchecked")
      T service = (T) impl;
      result.add(service);
    }
    return result;
  }

  @Override
  public void unregisterService(Class<?> serviceInterface, Object implementation) {
    Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");
    Objects.requireNonNull(implementation, "implementation cannot be null");

    CopyOnWriteArrayList<Object> implementations = services.get(serviceInterface);
    if (implementations != null) {
      boolean removed = implementations.remove(implementation);
      if (removed) {
        LOGGER.info(
            "Unregistered service: {} -> {}",
            serviceInterface.getName(),
            implementation.getClass().getName());

        // Remove empty list to save memory
        if (implementations.isEmpty()) {
          services.remove(serviceInterface);
        }
      }
    }
  }

  /**
   * Clears all registered services.
   *
   * <p>Useful for testing or shutdown.
   */
  public void clear() {
    int count = services.values().stream().mapToInt(List::size).sum();
    services.clear();
    LOGGER.info("Cleared service registry ({} services removed)", count);
  }

  /**
   * Returns the total number of registered service implementations.
   *
   * @return total service count
   */
  public int getServiceCount() {
    return services.values().stream().mapToInt(List::size).sum();
  }

  /**
   * Returns the number of registered service interfaces.
   *
   * @return interface count
   */
  public int getInterfaceCount() {
    return services.size();
  }

  /**
   * Returns a list of all registered service interface types.
   *
   * @return list of service interfaces
   */
  public List<Class<?>> getRegisteredInterfaces() {
    return new ArrayList<>(services.keySet());
  }
}
