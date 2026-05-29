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

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Runtime context for an application running on the platform. Provides access to isolated resources
 * and optional platform features.
 *
 * <p>Each application receives its own context with isolated:
 *
 * <ul>
 *   <li>ClassLoader for class isolation
 *   <li>Thread pool for concurrent execution
 *   <li>Security policy for permission control
 *   <li>Resource monitoring for CPU/memory tracking
 * </ul>
 *
 * <p>Optional features (if enabled):
 *
 * <ul>
 *   <li>Message bus for inter-application communication
 *   <li>Service registry for service discovery
 * </ul>
 *
 * @see Application
 */
public interface ApplicationContext {
  /**
   * Returns the unique identifier for this application.
   *
   * @return the application ID
   */
  @NonNull
  String getApplicationId();

  /**
   * Returns the current lifecycle state of this application.
   *
   * @return the application state
   * @see ApplicationState
   */
  @NonNull
  ApplicationState getState();

  /**
   * Returns the timestamp when this application was deployed.
   *
   * @return the deployment timestamp
   */
  @NonNull
  Instant getDeployedAt();

  /**
   * Returns the isolated classloader for this application. All application classes are loaded
   * through this classloader for isolation.
   *
   * @return the application's classloader
   */
  @NonNull
  ClassLoader getClassLoader();

  /**
   * Returns the dedicated thread pool for this application. Use this for concurrent execution to
   * maintain resource isolation.
   *
   * @return the application's thread pool
   */
  @NonNull
  ThreadPoolExecutor getThreadPool();

  /**
   * Returns the security policy for this application. Use this to check permissions before
   * performing privileged operations.
   *
   * @return the application's security policy
   */
  @NonNull
  SecurityPolicy getSecurityPolicy();

  /**
   * Returns the resource monitor tracking this application's resource usage. Provides CPU time,
   * memory usage, and thread count metrics.
   *
   * @return the application's resource monitor
   */
  @NonNull
  ResourceMonitor getResourceMonitor();

  /**
   * Returns the message bus if messaging is enabled for this application.
   *
   * @return optional message bus, empty if messaging is not enabled
   */
  @NonNull
  Optional<MessageBus> getMessageBus();

  /**
   * Returns the service registry if messaging is enabled for this application.
   *
   * @return optional service registry, empty if messaging is not enabled
   */
  @NonNull
  Optional<ServiceRegistry> getServiceRegistry();

  /**
   * Returns the volume manager if volumes are defined for this application. Provides access to
   * persistent and ephemeral storage directories.
   *
   * @return optional volume manager, empty if no volumes are defined
   * @since 2.0
   */
  @NonNull
  Optional<VolumeManager> getVolumeManager();

  /**
   * Returns custom properties configured for this application.
   *
   * @return immutable map of application properties
   */
  @NonNull
  Map<String, String> getProperties();

  /**
   * Returns the application instance if it has been started.
   *
   * @return the application instance, or null if not yet started
   */
  @Nullable
  Object getApplicationInstance();
}
