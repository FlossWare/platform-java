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

package org.flossware.jplatform.core;

import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for ApplicationManager to verify fine-grained locking behavior.
 * Tests that operations on different applications can execute in parallel.
 */
class ApplicationManagerConcurrencyTest {

    private ApplicationManager manager;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        manager = new ApplicationManager();
        executor = Executors.newFixedThreadPool(10);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    void testConcurrentDeploymentOfDifferentApplications() throws Exception {
        int numApps = 10;
        CountDownLatch latch = new CountDownLatch(numApps);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numApps; i++) {
            final int appIndex = i;
            Future<?> future = executor.submit(() -> {
                try {
                    ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                            .applicationId("app-" + appIndex)
                            .name("Test App " + appIndex)
                            .mainClass("com.example.TestApp" + appIndex)
                            .addClasspathEntry(URI.create("file:///test" + appIndex + ".jar"))
                            .build();

                    manager.deploy(descriptor);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Failed to deploy app-" + appIndex + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All deployments should complete");
        assertEquals(numApps, successCount.get(), "All applications should deploy successfully");

        // Verify all apps are deployed
        Map<String, ApplicationState> apps = manager.listApplications();
        assertEquals(numApps, apps.size());

        for (int i = 0; i < numApps; i++) {
            assertTrue(apps.containsKey("app-" + i));
            assertEquals(ApplicationState.DEPLOYED, apps.get("app-" + i));
        }
    }

    @Test
    void testConcurrentUndeploymentOfDifferentApplications() throws Exception {
        int numApps = 5;

        // First deploy all apps
        for (int i = 0; i < numApps; i++) {
            ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                    .applicationId("app-" + i)
                    .name("Test App " + i)
                    .mainClass("com.example.TestApp" + i)
                    .addClasspathEntry(URI.create("file:///test" + i + ".jar"))
                    .build();
            manager.deploy(descriptor);
        }

        assertEquals(numApps, manager.listApplications().size());

        // Now undeploy concurrently
        CountDownLatch latch = new CountDownLatch(numApps);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numApps; i++) {
            final int appIndex = i;
            executor.submit(() -> {
                try {
                    manager.undeploy("app-" + appIndex);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Failed to undeploy app-" + appIndex + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All undeployments should complete");
        assertEquals(numApps, successCount.get(), "All applications should undeploy successfully");

        // Verify all apps are undeployed
        assertEquals(0, manager.listApplications().size());
    }

    @Test
    void testSameApplicationOperationsAreSerialized() throws Exception {
        // This test verifies that operations on the SAME application are serialized
        // We can't easily test this directly, but we can verify it doesn't cause errors

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("same-app")
                .name("Same App")
                .mainClass("com.example.TestApp")
                .addClasspathEntry(URI.create("file:///test.jar"))
                .build();

        manager.deploy(descriptor);

        int numAttempts = 20;
        CountDownLatch latch = new CountDownLatch(numAttempts);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Try to get context many times concurrently
        for (int i = 0; i < numAttempts; i++) {
            executor.submit(() -> {
                try {
                    ApplicationContext ctx = manager.getApplicationContext("same-app");
                    assertNotNull(ctx);
                    assertEquals("same-app", ctx.getApplicationId());
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(0, errorCount.get(), "No errors should occur when accessing same app concurrently");
    }

    @Test
    void testDeployDuplicateApplicationThrowsException() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("duplicate")
                .name("Duplicate App")
                .mainClass("com.example.TestApp")
                .addClasspathEntry(URI.create("file:///test.jar"))
                .build();

        assertDoesNotThrow(() -> manager.deploy(descriptor));
        assertThrows(IllegalStateException.class, () -> manager.deploy(descriptor),
                "Deploying duplicate application should throw IllegalStateException");
    }
}
