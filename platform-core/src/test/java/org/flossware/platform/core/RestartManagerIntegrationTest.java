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
import org.flossware.platform.api.RestartPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Integration tests for RestartManager with ApplicationManager. */
@Tag("integration")
class RestartManagerIntegrationTest {

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
  void testDeployWithoutRestartPolicyDoesNotCreateRestartManager() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("no-restart-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .addClasspathEntry(testClasspathUri)
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("no-restart-app");
    assertNotNull(context);
    assertFalse(
        context.getRestartManager().isPresent(),
        "Should not create restart manager when policy not configured");
  }

  @Test
  void testDeployWithRestartPolicyCreatesRestartManager() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("restart-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("restart.policy", "on-failure")
            .property("restart.maxRetries", "3")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("restart-app");
    assertNotNull(context);
    assertTrue(
        context.getRestartManager().isPresent(),
        "Should create restart manager when policy configured");

    RestartManager restartManager = context.getRestartManager().get();
    RestartPolicy policy = restartManager.getPolicy();
    assertEquals(RestartPolicy.RestartCondition.ON_FAILURE, policy.getCondition());
    assertEquals(3, policy.getMaxRetries());
  }

  @Test
  void testDeployWithAlwaysRestartPolicy() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("always-restart-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("restart.policy", "always")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("always-restart-app");
    assertNotNull(context);
    assertTrue(context.getRestartManager().isPresent());

    RestartManager restartManager = context.getRestartManager().get();
    assertEquals(RestartPolicy.RestartCondition.ALWAYS, restartManager.getPolicy().getCondition());
  }

  @Test
  void testDeployWithNeverRestartPolicy() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("never-restart-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("restart.policy", "never")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("never-restart-app");
    assertNotNull(context);
    assertTrue(context.getRestartManager().isPresent());

    RestartManager restartManager = context.getRestartManager().get();
    assertEquals(RestartPolicy.RestartCondition.NEVER, restartManager.getPolicy().getCondition());
  }

  @Test
  void testUndeployStopsRestartManager() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("undeploy-test-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("restart.policy", "on-failure")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("undeploy-test-app");
    assertTrue(context.getRestartManager().isPresent());

    // Undeploy should stop the restart manager
    manager.undeploy("undeploy-test-app");

    // Context should be removed after undeploy
    assertNull(manager.getApplicationContext("undeploy-test-app"));
  }

  @Test
  void testRestartPolicyWithCustomBackoff() throws Exception {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("custom-backoff-app")
            .mainClass("com.example.App")
            .addClasspathEntry(testClasspathUri)
            .property("restart.policy", "on-failure")
            .property("restart.maxRetries", "5")
            .property("restart.initialBackoff", "10")
            .property("restart.maxBackoff", "120")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor);

    ApplicationContextImpl context =
        (ApplicationContextImpl) manager.getApplicationContext("custom-backoff-app");
    RestartManager restartManager = context.getRestartManager().get();

    RestartPolicy policy = restartManager.getPolicy();
    assertEquals(5, policy.getMaxRetries());
    assertEquals(10, policy.getInitialBackoffSeconds());
    assertEquals(120, policy.getMaxBackoffSeconds());
  }

  @Test
  void testMultipleApplicationsWithDifferentRestartPolicies() throws Exception {
    // Deploy app with on-failure policy
    ApplicationDescriptor descriptor1 =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App1")
            .addClasspathEntry(testClasspathUri)
            .property("restart.policy", "on-failure")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    // Deploy app with always policy
    ApplicationDescriptor descriptor2 =
        ApplicationDescriptor.builder()
            .applicationId("app2")
            .mainClass("com.example.App2")
            .addClasspathEntry(testClasspathUri)
            .property("restart.policy", "always")
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    // Deploy app without restart policy
    ApplicationDescriptor descriptor3 =
        ApplicationDescriptor.builder()
            .applicationId("app3")
            .mainClass("com.example.App3")
            .addClasspathEntry(testClasspathUri)
            .resourceConfig(ResourceConfig.builder().build())
            .build();

    manager.deploy(descriptor1);
    manager.deploy(descriptor2);
    manager.deploy(descriptor3);

    ApplicationContextImpl context1 =
        (ApplicationContextImpl) manager.getApplicationContext("app1");
    ApplicationContextImpl context2 =
        (ApplicationContextImpl) manager.getApplicationContext("app2");
    ApplicationContextImpl context3 =
        (ApplicationContextImpl) manager.getApplicationContext("app3");

    assertTrue(context1.getRestartManager().isPresent());
    assertEquals(
        RestartPolicy.RestartCondition.ON_FAILURE,
        context1.getRestartManager().get().getPolicy().getCondition());

    assertTrue(context2.getRestartManager().isPresent());
    assertEquals(
        RestartPolicy.RestartCondition.ALWAYS,
        context2.getRestartManager().get().getPolicy().getCondition());

    assertFalse(context3.getRestartManager().isPresent());
  }
}
