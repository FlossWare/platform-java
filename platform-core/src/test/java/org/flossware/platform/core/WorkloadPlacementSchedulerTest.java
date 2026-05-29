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

import static org.junit.jupiter.api.Assertions.*;

import org.flossware.platform.api.ApplicationDescriptor;
import org.flossware.platform.api.ResourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for WorkloadPlacementScheduler. */
class WorkloadPlacementSchedulerTest {

  private WorkloadPlacementScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new WorkloadPlacementScheduler();
  }

  @Test
  void testLightweightJavaAppPlacedInJvm() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("auth-service")
            .mainClass("com.example.AuthService")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(512).build())
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    assertEquals(ExecutionBackend.IN_JVM, decision.getBackend());
    assertTrue(decision.getReason().contains("Lightweight"));
  }

  @Test
  void testHeavyJavaAppPlacedInContainer() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("ml-model")
            .mainClass("com.example.MLService")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(32768).build())
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    assertEquals(ExecutionBackend.CONTAINER, decision.getBackend());
    assertTrue(decision.getReason().contains("Heavy"));
  }

  @Test
  void testModerateJavaAppPlacedInContainer() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("api-service")
            .mainClass("com.example.ApiService")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(8192).build())
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    assertEquals(ExecutionBackend.CONTAINER, decision.getBackend());
    assertTrue(decision.getReason().contains("Moderate"));
  }

  @Test
  void testNativeBinaryPlacedInVm() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("postgres")
            .property("native.executable", "/usr/bin/postgres")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(16384).build())
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    assertEquals(ExecutionBackend.VIRTUAL_MACHINE, decision.getBackend());
    assertTrue(decision.getReason().contains("Native binary"));
  }

  @Test
  void testContainerImagePlacedInContainer() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("nginx")
            .property("container.image", "nginx:latest")
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    assertEquals(ExecutionBackend.CONTAINER, decision.getBackend());
  }

  @Test
  void testKernelAccessRequiresVm() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("kernel-app")
            .mainClass("com.example.App")
            .property("kernel.access", "true")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(1024).build())
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    assertEquals(ExecutionBackend.VIRTUAL_MACHINE, decision.getBackend());
    assertTrue(decision.getReason().contains("kernel"));
  }

  @Test
  void testVmIsolationRequired() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("financial-app")
            .mainClass("com.example.FinancialService")
            .property("vm.required", "true")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(2048).build())
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    assertEquals(ExecutionBackend.VIRTUAL_MACHINE, decision.getBackend());
    assertTrue(decision.getReason().contains("VM isolation"));
  }

  @Test
  void testMassiveScalePlacedInKubernetes() {
    WorkloadPlacementScheduler k8sScheduler = new WorkloadPlacementScheduler(true);

    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("web-frontend")
            .mainClass("com.example.Frontend")
            .property("replicas", "50")
            .build();

    PlacementDecision decision = k8sScheduler.scheduleWorkload(descriptor);

    assertEquals(ExecutionBackend.KUBERNETES, decision.getBackend());
    assertTrue(decision.getReason().contains("Massive scale"));
  }

  @Test
  void testMassiveScaleWithoutKubernetesAvailable() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("web-frontend")
            .mainClass("com.example.Frontend")
            .property("replicas", "50")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(2048).build())
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    // Should not use Kubernetes if not available
    assertNotEquals(ExecutionBackend.KUBERNETES, decision.getBackend());
  }

  @Test
  void testExplicitBackendOverride() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("override-app")
            .mainClass("com.example.App")
            .property("deploymentTarget", "container")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(512).build())
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    // Even though it's lightweight, explicit override to container
    assertEquals(ExecutionBackend.CONTAINER, decision.getBackend());
    assertTrue(decision.getReason().contains("Explicit"));
  }

  @Test
  void testInvalidExplicitBackendFallsBackToAutomatic() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("invalid-override")
            .mainClass("com.example.App")
            .property("deploymentTarget", "invalid-backend")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(512).build())
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    // Should fall back to automatic (in-JVM for lightweight Java)
    assertEquals(ExecutionBackend.IN_JVM, decision.getBackend());
  }

  @Test
  void testScheduleWorkloadNull() {
    assertThrows(NullPointerException.class, () -> scheduler.scheduleWorkload(null));
  }

  @Test
  void testDecisionIncludesApplicationId() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    assertEquals("test-app", decision.getApplicationId());
  }

  @Test
  void testKubernetesAvailability() {
    WorkloadPlacementScheduler withK8s = new WorkloadPlacementScheduler(true);
    WorkloadPlacementScheduler withoutK8s = new WorkloadPlacementScheduler(false);

    assertTrue(withK8s.isKubernetesAvailable());
    assertFalse(withoutK8s.isKubernetesAvailable());
  }

  @Test
  void testThresholdConstants() {
    assertEquals(4096, WorkloadPlacementScheduler.getLightweightMemoryThresholdMB());
    assertEquals(16384, WorkloadPlacementScheduler.getHeavyMemoryThresholdMB());
    assertEquals(10, WorkloadPlacementScheduler.getMassiveScaleReplicaThreshold());
  }

  @Test
  void testDefaultJavaAppWithNoResources() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("minimal-app")
            .mainClass("com.example.App")
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    // Defaults (512MB) should be lightweight, placed in-JVM
    assertEquals(ExecutionBackend.IN_JVM, decision.getBackend());
  }

  @Test
  void testNonJavaWithoutImageOrExecutable() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("unknown-app")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(1024).build())
            .build();

    PlacementDecision decision = scheduler.scheduleWorkload(descriptor);

    // Non-Java workload without image/executable defaults to container
    assertEquals(ExecutionBackend.CONTAINER, decision.getBackend());
  }
}
