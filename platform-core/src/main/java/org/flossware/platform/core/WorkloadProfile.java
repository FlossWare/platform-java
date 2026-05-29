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

import java.util.Objects;

import org.flossware.platform.api.ApplicationDescriptor;

/**
 * Analyzes and profiles an application workload for placement decisions.
 *
 * <p>Extracts key characteristics from an {@link ApplicationDescriptor} that influence placement
 * decisions:
 *
 * <ul>
 *   <li>Workload type (Java, native, container image)
 *   <li>Resource requirements (CPU, memory, disk)
 *   <li>Isolation requirements (kernel access, security policies)
 *   <li>Scale requirements (replica count, auto-scaling)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * ApplicationDescriptor descriptor = ...;
 * WorkloadProfile profile = WorkloadProfile.analyze(descriptor);
 *
 * if (profile.isJavaApp() && profile.getMemoryMB() < 4096) {
 *     return ExecutionBackend.IN_JVM;
 * } else if (profile.needsKernelAccess()) {
 *     return ExecutionBackend.VIRTUAL_MACHINE;
 * }
 * }</pre>
 *
 * @since 2.2
 * @see WorkloadPlacementScheduler
 * @see PlacementDecision
 */
public final class WorkloadProfile {

  private final boolean isJavaApp;
  private final boolean isNativeApp;
  private final boolean isContainerImage;
  private final int cpuCores;
  private final int memoryMB;
  private final boolean needsKernelAccess;
  private final boolean requiresVmIsolation;
  private final int requiredReplicas;
  private final String applicationId;

  private WorkloadProfile(Builder builder) {
    this.applicationId = builder.applicationId;
    this.isJavaApp = builder.isJavaApp;
    this.isNativeApp = builder.isNativeApp;
    this.isContainerImage = builder.isContainerImage;
    this.cpuCores = builder.cpuCores;
    this.memoryMB = builder.memoryMB;
    this.needsKernelAccess = builder.needsKernelAccess;
    this.requiresVmIsolation = builder.requiresVmIsolation;
    this.requiredReplicas = builder.requiredReplicas;
  }

  /**
   * Analyzes an application descriptor and creates a workload profile.
   *
   * @param descriptor the application descriptor to analyze
   * @return the workload profile
   * @throws NullPointerException if descriptor is null
   */
  public static WorkloadProfile analyze(ApplicationDescriptor descriptor) {
    Objects.requireNonNull(descriptor, "descriptor cannot be null");

    Builder builder = new Builder();
    builder.applicationId = descriptor.getApplicationId();

    // Determine workload type
    builder.isNativeApp =
        descriptor.getProperties().get("native.executable") != null
            || descriptor.getProperties().get("nativeImage") != null;
    builder.isContainerImage = descriptor.getProperties().get("container.image") != null;
    // Only consider it a Java app if it has a mainClass and is NOT a native/container app
    builder.isJavaApp =
        descriptor.getMainClass() != null
            && !descriptor.getMainClass().isEmpty()
            && !builder.isNativeApp
            && !builder.isContainerImage;

    // Extract resource requirements
    if (descriptor.getResourceConfig() != null) {
      builder.cpuCores = descriptor.getResourceConfig().getMaxThreads().orElse(1);
      builder.memoryMB = descriptor.getResourceConfig().getMaxHeapMB().orElse(512L).intValue();
    } else {
      // Defaults if not specified
      builder.cpuCores = 1;
      builder.memoryMB = 512;
    }

    // Check isolation requirements
    builder.needsKernelAccess =
        "true".equalsIgnoreCase(descriptor.getProperties().get("vm.required"))
            || "true".equalsIgnoreCase(descriptor.getProperties().get("kernel.access"));

    builder.requiresVmIsolation =
        "true".equalsIgnoreCase(descriptor.getProperties().get("vm.required"))
            || "true".equalsIgnoreCase(descriptor.getProperties().get("security.vm.isolated"));

    // Extract scale requirements
    String replicasStr = descriptor.getProperties().get("replicas");
    if (replicasStr != null) {
      try {
        builder.requiredReplicas = Integer.parseInt(replicasStr);
      } catch (NumberFormatException e) {
        builder.requiredReplicas = 1;
      }
    } else {
      builder.requiredReplicas = 1;
    }

    return builder.build();
  }

  /**
   * Returns the application identifier.
   *
   * @return the application ID
   */
  public String getApplicationId() {
    return applicationId;
  }

  /**
   * Checks if this is a Java application.
   *
   * @return true if the workload has a mainClass defined
   */
  public boolean isJavaApp() {
    return isJavaApp;
  }

  /**
   * Checks if this is a native executable.
   *
   * @return true if the workload specifies a native executable
   */
  public boolean isNativeApp() {
    return isNativeApp;
  }

  /**
   * Checks if this is a container image.
   *
   * @return true if the workload specifies a container image
   */
  public boolean isContainerImage() {
    return isContainerImage;
  }

  /**
   * Returns the requested CPU cores.
   *
   * @return number of CPU cores (default: 1)
   */
  public int getCpuCores() {
    return cpuCores;
  }

  /**
   * Returns the requested memory in megabytes.
   *
   * @return memory in MB (default: 512)
   */
  public int getMemoryMB() {
    return memoryMB;
  }

  /**
   * Checks if the workload needs kernel-level access.
   *
   * <p>Workloads requiring kernel access must run in a VM or on bare metal, not in containers or
   * in-JVM isolation.
   *
   * @return true if kernel access is required
   */
  public boolean needsKernelAccess() {
    return needsKernelAccess;
  }

  /**
   * Checks if the workload explicitly requires VM isolation.
   *
   * <p>This is a policy override that forces VM placement regardless of other characteristics.
   *
   * @return true if VM isolation is required
   */
  public boolean requiresVmIsolation() {
    return requiresVmIsolation;
  }

  /**
   * Returns the required number of replicas.
   *
   * @return replica count (default: 1)
   */
  public int getRequiredReplicas() {
    return requiredReplicas;
  }

  /**
   * Checks if this is a lightweight workload suitable for in-JVM execution.
   *
   * <p>A workload is considered lightweight if:
   *
   * <ul>
   *   <li>It's a Java application
   *   <li>Memory requirement is below 4GB
   *   <li>No kernel access required
   *   <li>No explicit VM isolation required
   * </ul>
   *
   * @return true if the workload is lightweight
   */
  public boolean isLightweight() {
    return isJavaApp && memoryMB < 4096 && !needsKernelAccess && !requiresVmIsolation;
  }

  /**
   * Checks if this is a heavy workload requiring dedicated resources.
   *
   * <p>A workload is considered heavy if:
   *
   * <ul>
   *   <li>Memory requirement is 16GB or more
   *   <li>CPU requirement is 8 cores or more
   * </ul>
   *
   * @return true if the workload is heavy
   */
  public boolean isHeavy() {
    return memoryMB >= 16384 || cpuCores >= 8;
  }

  /**
   * Checks if this workload requires massive scale.
   *
   * <p>Massive scale is defined as requiring 10 or more replicas.
   *
   * @return true if the workload needs massive scale
   */
  public boolean requiresMassiveScale() {
    return requiredReplicas >= 10;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WorkloadProfile that = (WorkloadProfile) o;
    return isJavaApp == that.isJavaApp
        && isNativeApp == that.isNativeApp
        && isContainerImage == that.isContainerImage
        && cpuCores == that.cpuCores
        && memoryMB == that.memoryMB
        && needsKernelAccess == that.needsKernelAccess
        && requiresVmIsolation == that.requiresVmIsolation
        && requiredReplicas == that.requiredReplicas
        && Objects.equals(applicationId, that.applicationId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        isJavaApp,
        isNativeApp,
        isContainerImage,
        cpuCores,
        memoryMB,
        needsKernelAccess,
        requiresVmIsolation,
        requiredReplicas,
        applicationId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("WorkloadProfile{");
    if (applicationId != null) {
      sb.append("app=").append(applicationId).append(", ");
    }
    sb.append("type=");
    if (isJavaApp) {
      sb.append("Java");
    } else if (isNativeApp) {
      sb.append("Native");
    } else if (isContainerImage) {
      sb.append("Container");
    } else {
      sb.append("Unknown");
    }

    sb.append(", cpu=")
        .append(cpuCores)
        .append(", memory=")
        .append(memoryMB)
        .append("MB")
        .append(", replicas=")
        .append(requiredReplicas);

    if (needsKernelAccess) {
      sb.append(", kernelAccess");
    }
    if (requiresVmIsolation) {
      sb.append(", vmRequired");
    }

    sb.append('}');
    return sb.toString();
  }

  /** Builder for constructing WorkloadProfile instances. */
  private static class Builder {
    private String applicationId;
    private boolean isJavaApp;
    private boolean isNativeApp;
    private boolean isContainerImage;
    private int cpuCores = 1;
    private int memoryMB = 512;
    private boolean needsKernelAccess;
    private boolean requiresVmIsolation;
    private int requiredReplicas = 1;

    private WorkloadProfile build() {
      return new WorkloadProfile(this);
    }
  }
}
