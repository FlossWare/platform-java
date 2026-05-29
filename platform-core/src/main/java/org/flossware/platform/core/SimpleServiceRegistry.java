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

  /** Holder for a service implementation with its optional version. */
  private static class ServiceEntry {
    final Object implementation;
    final SemanticVersion version;

    ServiceEntry(Object implementation, SemanticVersion version) {
      this.implementation = implementation;
      this.version = version;
    }
  }

  // Map of service interface -> list of service entries
  private final Map<Class<?>, CopyOnWriteArrayList<ServiceEntry>> services;

  /** Creates a new empty service registry. */
  public SimpleServiceRegistry() {
    this.services = new ConcurrentHashMap<>();
  }

  @Override
  public <T> void registerService(Class<T> serviceInterface, T implementation) {
    registerService(serviceInterface, implementation, null);
  }

  @Override
  public <T> void registerService(Class<T> serviceInterface, T implementation, String version) {
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

    SemanticVersion semVer = version != null ? SemanticVersion.parse(version) : null;
    ServiceEntry entry = new ServiceEntry(implementation, semVer);

    services.computeIfAbsent(serviceInterface, k -> new CopyOnWriteArrayList<>()).add(entry);

    if (version != null) {
      LOGGER.info(
          "Registered service: {} -> {} (version: {})",
          serviceInterface.getName(),
          implementation.getClass().getName(),
          version);
    } else {
      LOGGER.info(
          "Registered service: {} -> {}",
          serviceInterface.getName(),
          implementation.getClass().getName());
    }
  }

  @Override
  public <T> Optional<T> getService(Class<T> serviceInterface) {
    Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");

    CopyOnWriteArrayList<ServiceEntry> entries = services.get(serviceInterface);
    if (entries == null || entries.isEmpty()) {
      LOGGER.debug("No service found for interface: {}", serviceInterface.getName());
      return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    T service = (T) entries.get(0).implementation;
    return Optional.of(service);
  }

  @Override
  public <T> Optional<T> getService(Class<T> serviceInterface, String minVersion) {
    Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");
    Objects.requireNonNull(minVersion, "minVersion cannot be null");

    SemanticVersion requiredVersion = SemanticVersion.parse(minVersion);

    CopyOnWriteArrayList<ServiceEntry> entries = services.get(serviceInterface);
    if (entries == null || entries.isEmpty()) {
      LOGGER.debug("No service found for interface: {}", serviceInterface.getName());
      return Optional.empty();
    }

    // Find first service with compatible version
    for (ServiceEntry entry : entries) {
      if (entry.version != null && entry.version.isCompatibleWith(requiredVersion)) {
        @SuppressWarnings("unchecked")
        T service = (T) entry.implementation;
        LOGGER.debug(
            "Found compatible service: {} version {} (required: {})",
            serviceInterface.getName(),
            entry.version,
            minVersion);
        return Optional.of(service);
      }
    }

    LOGGER.debug(
        "No compatible service found for interface: {} (required version: {})",
        serviceInterface.getName(),
        minVersion);
    return Optional.empty();
  }

  @Override
  public <T> List<T> getAllServices(Class<T> serviceInterface) {
    Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");

    CopyOnWriteArrayList<ServiceEntry> entries = services.get(serviceInterface);
    if (entries == null || entries.isEmpty()) {
      return new ArrayList<>();
    }

    List<T> result = new ArrayList<>(entries.size());
    for (ServiceEntry entry : entries) {
      @SuppressWarnings("unchecked")
      T service = (T) entry.implementation;
      result.add(service);
    }
    return result;
  }

  @Override
  public void unregisterService(Class<?> serviceInterface, Object implementation) {
    Objects.requireNonNull(serviceInterface, "serviceInterface cannot be null");
    Objects.requireNonNull(implementation, "implementation cannot be null");

    CopyOnWriteArrayList<ServiceEntry> entries = services.get(serviceInterface);
    if (entries != null) {
      ServiceEntry toRemove = null;
      for (ServiceEntry entry : entries) {
        if (entry.implementation.equals(implementation)) {
          toRemove = entry;
          break;
        }
      }

      if (toRemove != null) {
        entries.remove(toRemove);
        LOGGER.info(
            "Unregistered service: {} -> {}",
            serviceInterface.getName(),
            implementation.getClass().getName());

        // Remove empty list to save memory
        if (entries.isEmpty()) {
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
