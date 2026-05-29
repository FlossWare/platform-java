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
import org.junit.jupiter.api.Test;

/** Unit tests for WorkloadProfile. */
class WorkloadProfileTest {

  @Test
  void testAnalyzeJavaApp() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-java-app")
            .mainClass("com.example.App")
            .resourceConfig(ResourceConfig.builder().maxThreads(2).maxHeapMB(1024).build())
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertEquals("test-java-app", profile.getApplicationId());
    assertTrue(profile.isJavaApp());
    assertFalse(profile.isNativeApp());
    assertFalse(profile.isContainerImage());
    assertEquals(2, profile.getCpuCores());
    assertEquals(1024, profile.getMemoryMB());
    assertFalse(profile.needsKernelAccess());
    assertFalse(profile.requiresVmIsolation());
    assertEquals(1, profile.getRequiredReplicas());
  }

  @Test
  void testAnalyzeNativeApp() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("native-app")
            .property("native.executable", "/usr/bin/postgres")
            .resourceConfig(ResourceConfig.builder().maxThreads(4).maxHeapMB(8192).build())
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertFalse(profile.isJavaApp());
    assertTrue(profile.isNativeApp());
    assertFalse(profile.isContainerImage());
    assertEquals(4, profile.getCpuCores());
    assertEquals(8192, profile.getMemoryMB());
  }

  @Test
  void testAnalyzeContainerImage() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("container-app")
            .property("container.image", "nginx:latest")
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertFalse(profile.isJavaApp());
    assertFalse(profile.isNativeApp());
    assertTrue(profile.isContainerImage());
  }

  @Test
  void testAnalyzeWithKernelAccess() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("kernel-app")
            .property("vm.required", "true")
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertTrue(profile.needsKernelAccess());
    assertTrue(profile.requiresVmIsolation());
  }

  @Test
  void testAnalyzeWithReplicas() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("scaled-app")
            .mainClass("com.example.App")
            .property("replicas", "25")
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertEquals(25, profile.getRequiredReplicas());
    assertTrue(profile.requiresMassiveScale());
  }

  @Test
  void testAnalyzeInvalidReplicasDefaultsToOne() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app")
            .mainClass("com.example.App")
            .property("replicas", "invalid")
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertEquals(1, profile.getRequiredReplicas());
  }

  @Test
  void testAnalyzeWithoutResourcesUsesDefaults() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("minimal-app")
            .mainClass("com.example.App")
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertEquals(1, profile.getCpuCores());
    assertEquals(512, profile.getMemoryMB());
  }

  @Test
  void testIsLightweight() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("light-app")
            .mainClass("com.example.App")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(2048).build())
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertTrue(profile.isLightweight());
  }

  @Test
  void testIsNotLightweightDueToMemory() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("heavy-app")
            .mainClass("com.example.App")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(8192).build())
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertFalse(profile.isLightweight());
  }

  @Test
  void testIsNotLightweightDueToNonJava() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("native-app")
            .property("native.executable", "/bin/sh")
            .resourceConfig(ResourceConfig.builder().maxHeapMB(512).build())
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertFalse(profile.isLightweight());
  }

  @Test
  void testIsHeavyMemory() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("heavy-memory-app")
            .mainClass("com.example.App")
            .resourceConfig(ResourceConfig.builder().maxThreads(4).maxHeapMB(32768).build())
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertTrue(profile.isHeavy());
  }

  @Test
  void testIsHeavyCpu() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("heavy-cpu-app")
            .mainClass("com.example.App")
            .resourceConfig(ResourceConfig.builder().maxThreads(16).maxHeapMB(4096).build())
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertTrue(profile.isHeavy());
  }

  @Test
  void testRequiresMassiveScale() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("scaled-app")
            .mainClass("com.example.App")
            .property("replicas", "50")
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertTrue(profile.requiresMassiveScale());
  }

  @Test
  void testDoesNotRequireMassiveScale() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("small-app")
            .mainClass("com.example.App")
            .property("replicas", "3")
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);

    assertFalse(profile.requiresMassiveScale());
  }

  @Test
  void testAnalyzeNull() {
    assertThrows(NullPointerException.class, () -> WorkloadProfile.analyze(null));
  }

  @Test
  void testToString() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .resourceConfig(ResourceConfig.builder().maxThreads(4).maxHeapMB(2048).build())
            .property("replicas", "5")
            .build();

    WorkloadProfile profile = WorkloadProfile.analyze(descriptor);
    String str = profile.toString();

    assertTrue(str.contains("app=test-app"));
    assertTrue(str.contains("type=Java"));
    assertTrue(str.contains("cpu=4"));
    assertTrue(str.contains("memory=2048MB"));
    assertTrue(str.contains("replicas=5"));
  }

  @Test
  void testEquality() {
    ApplicationDescriptor descriptor1 =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .resourceConfig(ResourceConfig.builder().maxThreads(2).maxHeapMB(1024).build())
            .build();

    ApplicationDescriptor descriptor2 =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .resourceConfig(ResourceConfig.builder().maxThreads(2).maxHeapMB(1024).build())
            .build();

    WorkloadProfile profile1 = WorkloadProfile.analyze(descriptor1);
    WorkloadProfile profile2 = WorkloadProfile.analyze(descriptor2);

    assertEquals(profile1, profile2);
    assertEquals(profile1.hashCode(), profile2.hashCode());
  }
}
