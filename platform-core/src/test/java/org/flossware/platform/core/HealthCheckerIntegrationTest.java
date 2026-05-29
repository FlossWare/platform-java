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

import java.io.File;
import java.net.URI;

import org.flossware.platform.api.ApplicationDescriptor;
import org.flossware.platform.api.ResourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Integration tests for HealthChecker with ApplicationManager. */
@Tag("integration")
class HealthCheckerIntegrationTest {

  private ApplicationManager manager;
  private URI testClasspathUri;

  @BeforeEach
  void setUp() throws Exception {
    manager = new ApplicationManager();

    // Get current classpath as a minimal classpath entry
    String classPath = System.getProperty("java.class.path");
    String[] entries = classPath.split(File.pathSeparator);
    testClasspathUri = new File(entries[0]).toURI();
  }

  @AfterEach
  void tearDown() {
    if (manager != null) {
      manager.shutdown();
    }
  }

  @Test
  void testDeployWithoutHealthCheckDoesNotCreateHealthChecker() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("no-healthcheck-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("no-healthcheck-app");
    assertNotNull(context);
    assertFalse(context.getHealthChecker().isPresent(),
        "Should not create health checker when not enabled");
  }

  @Test
  void testDeployWithHealthCheckCreatesHealthChecker() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("healthcheck-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("healthcheck.enabled", "true")
            .property("healthcheck.type", "application")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("healthcheck-app");
    assertNotNull(context);
    assertTrue(context.getHealthChecker().isPresent(),
        "Should create health checker when enabled");

    HealthChecker healthChecker = context.getHealthChecker().get();
    assertNotNull(healthChecker.getLastStatus());
  }

  @Test
  void testDeployWithHttpHealthCheck() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("http-healthcheck-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("healthcheck.enabled", "true")
            .property("healthcheck.type", "http")
            .property("healthcheck.http.url", "http://localhost:9999/health")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("http-healthcheck-app");
    assertTrue(context.getHealthChecker().isPresent());
  }

  @Test
  void testDeployWithTcpHealthCheck() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("tcp-healthcheck-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("healthcheck.enabled", "true")
            .property("healthcheck.type", "tcp")
            .property("healthcheck.tcp.host", "localhost")
            .property("healthcheck.tcp.port", "9999")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("tcp-healthcheck-app");
    assertTrue(context.getHealthChecker().isPresent());
  }

  @Test
  void testUndeployStopsHealthChecker() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("undeploy-healthcheck-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("healthcheck.enabled", "true")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("undeploy-healthcheck-app");
    assertTrue(context.getHealthChecker().isPresent());

    // Undeploy should stop the health checker
    manager.undeploy("undeploy-healthcheck-app");

    // Context should be removed after undeploy
    assertNull(manager.getApplicationContext("undeploy-healthcheck-app"));
  }

  @Test
  void testHealthCheckWithCustomIntervals() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("custom-interval-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("healthcheck.enabled", "true")
            .property("healthcheck.interval", "60")
            .property("healthcheck.initialDelay", "5")
            .property("healthcheck.timeout", "10")
            .property("healthcheck.failureThreshold", "5")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("custom-interval-app");
    assertTrue(context.getHealthChecker().isPresent());
  }

  @Test
  void testBothHealthCheckAndRestartManager() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("both-features-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("healthcheck.enabled", "true")
            .property("restart.policy", "on-failure")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("both-features-app");
    assertNotNull(context);
    assertTrue(context.getHealthChecker().isPresent(),
        "Should create health checker");
    assertTrue(context.getRestartManager().isPresent(),
        "Should create restart manager");
  }
}
