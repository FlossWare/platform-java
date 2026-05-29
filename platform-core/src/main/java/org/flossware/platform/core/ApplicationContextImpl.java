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

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.flossware.platform.api.ApplicationContext;
import org.flossware.platform.api.ApplicationDescriptor;
import org.flossware.platform.api.ApplicationState;
import org.flossware.platform.api.MessageBus;
import org.flossware.platform.api.ResourceMonitor;
import org.flossware.platform.api.SecurityPolicy;
import org.flossware.platform.api.ServiceRegistry;
import org.flossware.platform.api.ThreadPoolExecutor;
import org.flossware.platform.api.VolumeManager;

/**
 * Implementation of ApplicationContext. Provides isolated runtime environment for an application.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Uses volatile fields for swappable state
 * (descriptor, state, classLoader, applicationInstance). The setClassLoaderAndDescriptor() method
 * is synchronized for atomic updates during hot reload.
 */
public final class ApplicationContextImpl implements ApplicationContext {

  private final String applicationId;
  private volatile ApplicationDescriptor descriptor; // Swappable during hot reload
  private volatile ApplicationState state;
  private volatile ClassLoader classLoader; // Swappable during hot reload
  private final ThreadPoolExecutor threadPool;
  private final SecurityPolicy securityPolicy;
  private final ResourceMonitor resourceMonitor;
  private final MessageBus messageBus;
  private final ServiceRegistry serviceRegistry;
  private final VolumeManager volumeManager; // Added in 2.0
  private final Map<String, String> properties;
  private volatile Object applicationInstance;
  private volatile Process nativeProcess; // Non-null for native image applications
  private volatile ContainerLauncher.ContainerInfo
      containerInfo; // Non-null for containerized applications
  private volatile org.flossware.platform.vm.VmLauncher.VmInfo
      vmInfo; // Non-null for VM applications
  private volatile RestartManager restartManager; // Non-null if auto-restart is enabled
  private volatile HealthChecker healthChecker; // Non-null if health checks are enabled
  private final Instant deployedAt; // Timestamp when application was deployed

  private ApplicationContextImpl(Builder builder) {
    this.applicationId = builder.applicationId;
    this.descriptor = builder.descriptor;
    this.state = ApplicationState.DEPLOYED;
    this.classLoader = builder.classLoader;
    this.threadPool = builder.threadPool;
    this.securityPolicy = builder.securityPolicy;
    this.resourceMonitor = builder.resourceMonitor;
    this.messageBus = builder.messageBus;
    this.serviceRegistry = builder.serviceRegistry;
    this.volumeManager = builder.volumeManager;
    this.properties =
        builder.properties != null
            ? Collections.unmodifiableMap(builder.properties)
            : Collections.emptyMap();
    this.deployedAt = Instant.now(); // Capture deployment timestamp
  }

  @Override
  public String getApplicationId() {
    return applicationId;
  }

  @Override
  public ApplicationState getState() {
    return state;
  }

  @Override
  public Instant getDeployedAt() {
    return deployedAt;
  }

  @Override
  public ClassLoader getClassLoader() {
    return classLoader;
  }

  @Override
  public ThreadPoolExecutor getThreadPool() {
    return threadPool;
  }

  @Override
  public SecurityPolicy getSecurityPolicy() {
    return securityPolicy;
  }

  @Override
  public ResourceMonitor getResourceMonitor() {
    return resourceMonitor;
  }

  @Override
  public Optional<MessageBus> getMessageBus() {
    return Optional.ofNullable(messageBus);
  }

  @Override
  public Optional<ServiceRegistry> getServiceRegistry() {
    return Optional.ofNullable(serviceRegistry);
  }

  @Override
  public Optional<VolumeManager> getVolumeManager() {
    return Optional.ofNullable(volumeManager);
  }

  @Override
  public Map<String, String> getProperties() {
    return properties;
  }

  @Override
  public Object getApplicationInstance() {
    return applicationInstance;
  }

  /**
   * Updates the application state. Package-private for use by ApplicationManager.
   *
   * @param state the new application state
   * @throws NullPointerException if state is null
   */
  void setState(ApplicationState state) {
    this.state = Objects.requireNonNull(state, "state cannot be null");
  }

  /**
   * Sets the application instance after instantiation. Package-private for use by
   * ApplicationManager.
   *
   * @param instance the application instance
   */
  void setApplicationInstance(Object instance) {
    this.applicationInstance = instance;
  }

  /**
   * Returns the native process for native image applications. Package-private for use by
   * ApplicationManager.
   *
   * @return Optional containing the native process, or empty if this is a JVM application
   */
  Optional<Process> getNativeProcess() {
    return Optional.ofNullable(nativeProcess);
  }

  /**
   * Sets the native process for native image applications. Package-private for use by
   * ApplicationManager.
   *
   * @param process the native process
   */
  void setNativeProcess(Process process) {
    this.nativeProcess = process;
  }

  /**
   * Returns the container info for containerized applications. Package-private for use by
   * ApplicationManager.
   *
   * @return Optional containing the container info, or empty if this is not a containerized
   *     application
   */
  Optional<ContainerLauncher.ContainerInfo> getContainerInfo() {
    return Optional.ofNullable(containerInfo);
  }

  /**
   * Sets the container info for containerized applications. Package-private for use by
   * ApplicationManager.
   *
   * @param containerInfo the container information
   */
  void setContainerInfo(ContainerLauncher.ContainerInfo containerInfo) {
    this.containerInfo = containerInfo;
  }

  /**
   * Returns the VM info for virtual machine applications. Package-private for use by
   * ApplicationManager.
   *
   * @return Optional containing the VM info, or empty if this is not a VM application
   */
  Optional<org.flossware.platform.vm.VmLauncher.VmInfo> getVmInfo() {
    return Optional.ofNullable(vmInfo);
  }

  /**
   * Sets the VM info for virtual machine applications. Package-private for use by
   * ApplicationManager.
   *
   * @param vmInfo the VM information
   */
  void setVmInfo(org.flossware.platform.vm.VmLauncher.VmInfo vmInfo) {
    this.vmInfo = vmInfo;
  }

  /**
   * Returns the restart manager for this application. Package-private for use by
   * ApplicationManager.
   *
   * @return Optional containing the restart manager, or empty if auto-restart is disabled
   */
  Optional<RestartManager> getRestartManager() {
    return Optional.ofNullable(restartManager);
  }

  /**
   * Sets the restart manager for this application. Package-private for use by ApplicationManager.
   *
   * @param restartManager the restart manager
   */
  void setRestartManager(RestartManager restartManager) {
    this.restartManager = restartManager;
  }

  /**
   * Returns the health checker for this application. Package-private for use by ApplicationManager.
   *
   * @return Optional containing the health checker, or empty if health checks are disabled
   */
  Optional<HealthChecker> getHealthChecker() {
    return Optional.ofNullable(healthChecker);
  }

  /**
   * Sets the health checker for this application. Package-private for use by ApplicationManager.
   *
   * @param healthChecker the health checker
   */
  void setHealthChecker(HealthChecker healthChecker) {
    this.healthChecker = healthChecker;
  }

  /**
   * Returns the application descriptor. Package-private for use by ApplicationManager.
   *
   * @return the application descriptor
   */
  ApplicationDescriptor getDescriptor() {
    return descriptor;
  }

  /**
   * Sets a new classloader. Package-private for use by ApplicationReloader during hot reload.
   *
   * @param classLoader the new classloader
   * @throws NullPointerException if classLoader is null
   */
  void setClassLoader(ClassLoader classLoader) {
    this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
  }

  /**
   * Sets a new descriptor. Package-private for use by ApplicationReloader during hot reload.
   *
   * @param descriptor the new descriptor
   * @throws NullPointerException if descriptor is null
   */
  void setDescriptor(ApplicationDescriptor descriptor) {
    this.descriptor = Objects.requireNonNull(descriptor, "descriptor cannot be null");
  }

  /**
   * Atomically updates both classloader and descriptor. Package-private for use by
   * ApplicationReloader during hot reload to avoid race conditions.
   *
   * @param classLoader the new classloader
   * @param descriptor the new descriptor
   * @throws NullPointerException if classLoader or descriptor is null
   */
  synchronized void setClassLoaderAndDescriptor(
      ClassLoader classLoader, ApplicationDescriptor descriptor) {
    this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
    this.descriptor = Objects.requireNonNull(descriptor, "descriptor cannot be null");
  }

  /**
   * Creates a new builder for constructing application contexts.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for constructing ApplicationContextImpl instances. Used internally by
   * ApplicationManager.
   */
  public static class Builder {
    private String applicationId;
    private ApplicationDescriptor descriptor;
    private ClassLoader classLoader;
    private ThreadPoolExecutor threadPool;
    private SecurityPolicy securityPolicy;
    private ResourceMonitor resourceMonitor;
    private MessageBus messageBus;
    private ServiceRegistry serviceRegistry;
    private VolumeManager volumeManager;
    private Map<String, String> properties;

    /**
     * Sets the application identifier.
     *
     * @param applicationId the application ID
     * @return this builder
     * @throws NullPointerException if applicationId is null
     */
    public Builder applicationId(String applicationId) {
      this.applicationId = Objects.requireNonNull(applicationId, "applicationId cannot be null");
      return this;
    }

    /**
     * Sets the application descriptor.
     *
     * @param descriptor the application descriptor
     * @return this builder
     * @throws NullPointerException if descriptor is null
     */
    public Builder descriptor(ApplicationDescriptor descriptor) {
      this.descriptor = Objects.requireNonNull(descriptor, "descriptor cannot be null");
      return this;
    }

    /**
     * Sets the isolated classloader.
     *
     * @param classLoader the application's classloader
     * @return this builder
     * @throws NullPointerException if classLoader is null
     */
    public Builder classLoader(ClassLoader classLoader) {
      this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
      return this;
    }

    /**
     * Sets the isolated thread pool.
     *
     * @param threadPool the application's thread pool
     * @return this builder
     * @throws NullPointerException if threadPool is null
     */
    public Builder threadPool(ThreadPoolExecutor threadPool) {
      this.threadPool = Objects.requireNonNull(threadPool, "threadPool cannot be null");
      return this;
    }

    /**
     * Sets the security policy.
     *
     * @param securityPolicy the application's security policy
     * @return this builder
     * @throws NullPointerException if securityPolicy is null
     */
    public Builder securityPolicy(SecurityPolicy securityPolicy) {
      this.securityPolicy = Objects.requireNonNull(securityPolicy, "securityPolicy cannot be null");
      return this;
    }

    /**
     * Sets the resource monitor.
     *
     * @param resourceMonitor the application's resource monitor
     * @return this builder
     * @throws NullPointerException if resourceMonitor is null
     */
    public Builder resourceMonitor(ResourceMonitor resourceMonitor) {
      this.resourceMonitor =
          Objects.requireNonNull(resourceMonitor, "resourceMonitor cannot be null");
      return this;
    }

    /**
     * Sets the message bus (optional).
     *
     * @param messageBus the shared message bus, or null if messaging is disabled
     * @return this builder
     */
    public Builder messageBus(MessageBus messageBus) {
      this.messageBus = messageBus;
      return this;
    }

    /**
     * Sets the service registry (optional).
     *
     * @param serviceRegistry the shared service registry, or null if disabled
     * @return this builder
     */
    public Builder serviceRegistry(ServiceRegistry serviceRegistry) {
      this.serviceRegistry = serviceRegistry;
      return this;
    }

    /**
     * Sets the volume manager (optional).
     *
     * @param volumeManager the volume manager for persistent storage, or null if no volumes
     * @return this builder
     * @since 2.0
     */
    public Builder volumeManager(VolumeManager volumeManager) {
      this.volumeManager = volumeManager;
      return this;
    }

    /**
     * Sets the application properties.
     *
     * @param properties the application configuration properties
     * @return this builder
     */
    public Builder properties(Map<String, String> properties) {
      if (properties != null) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
          if (entry.getKey() == null) {
            throw new IllegalArgumentException("Properties map cannot contain null keys");
          }
          if (entry.getValue() == null) {
            throw new IllegalArgumentException("Properties map cannot contain null values");
          }
        }
      }
      this.properties = properties;
      return this;
    }

    /**
     * Builds the ApplicationContextImpl instance.
     *
     * @return a new ApplicationContextImpl with the configured values
     * @throws NullPointerException if any required field is not set
     */
    public ApplicationContextImpl build() {
      Objects.requireNonNull(applicationId, "applicationId is required");
      Objects.requireNonNull(descriptor, "descriptor is required");
      Objects.requireNonNull(classLoader, "classLoader is required");
      Objects.requireNonNull(threadPool, "threadPool is required");
      Objects.requireNonNull(securityPolicy, "securityPolicy is required");
      Objects.requireNonNull(resourceMonitor, "resourceMonitor is required");

      return new ApplicationContextImpl(this);
    }
  }
}
