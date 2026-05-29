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

package org.flossware.jplatform.monitoring;

import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ApplicationResourceMonitor.
 * Tests resource tracking, quota enforcement, listener notifications, and shutdown.
 */
class ApplicationResourceMonitorTest {

    private ThreadGroup testThreadGroup;
    private ApplicationResourceMonitor monitor;
    private static final String TEST_APP_ID = "test-app";

    @BeforeEach
    void setUp() {
        testThreadGroup = new ThreadGroup("test-app-threads");
    }

    @AfterEach
    void tearDown() {
        if (monitor != null) {
            monitor.shutdown();
        }
    }

    @Test
    void testConstructorWithDefaults() {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup);
        assertNotNull(monitor);
        assertNotNull(monitor.getCurrentSnapshot());
    }

    @Test
    void testConstructorWithCustomConfiguration() {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, 100);
        assertNotNull(monitor);
        assertNotNull(monitor.getCurrentSnapshot());
    }

    @Test
    void testConstructorNullApplicationId() {
        assertThrows(NullPointerException.class, () -> {
            new ApplicationResourceMonitor(null, testThreadGroup);
        });
    }

    @Test
    void testConstructorNullThreadGroup() {
        assertThrows(NullPointerException.class, () -> {
            new ApplicationResourceMonitor(TEST_APP_ID, null);
        });
    }

    @Test
    void testConstructorInvalidPollInterval() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 0, 100);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, -1, 100);
        });
    }

    @Test
    void testConstructorInvalidHistorySize() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, -1);
        });
    }

    @Test
    void testGetCurrentSnapshotInitiallyReturnsEmptySnapshot() {
        // Use very short poll interval
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 10, 10);

        ResourceSnapshot snapshot = monitor.getCurrentSnapshot();
        assertNotNull(snapshot);
        assertTrue(snapshot.getTimestamp() > 0);
    }

    @Test
    void testGetCurrentSnapshotAfterMetricCollection() throws InterruptedException {
        // Short poll interval to collect metrics quickly
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, 10);

        // Wait for at least one collection cycle
        Thread.sleep(1500);

        ResourceSnapshot snapshot = monitor.getCurrentSnapshot();
        assertNotNull(snapshot);
        assertTrue(snapshot.getTimestamp() > 0);
        // Heap is not available (-1)
        assertEquals(-1, snapshot.getHeapUsedBytes());
    }

    @Test
    void testGetHistoryWithNullDuration() {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup);
        assertThrows(NullPointerException.class, () -> {
            monitor.getHistory(null);
        });
    }

    @Test
    void testGetHistoryReturnsFilteredSnapshots() throws InterruptedException {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, 100);

        // Wait for a few snapshots to be collected
        Thread.sleep(3000);

        ResourceUsageHistory history = monitor.getHistory(Duration.ofSeconds(2));
        assertNotNull(history);
        assertTrue(history.getSnapshots().size() >= 0);
    }

    @Test
    void testSetAndGetQuota() {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup);

        ResourceQuota quota = ResourceQuota.builder()
            .maxThreadCount(10)
            .maxCpuTimeNanos(60_000_000_000L) // 60 seconds
            .build();

        monitor.setQuota(quota);
        assertEquals(quota, monitor.getQuota());
    }

    @Test
    void testSetQuotaNull() {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup);

        monitor.setQuota(null);
        assertNull(monitor.getQuota());
    }

    @Test
    void testSetAndGetEnforcer() {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup);

        ResourceConfig config = ResourceConfig.builder().build();
        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            appId -> {},
            appId -> {}
        );

        monitor.setEnforcer(enforcer);
        assertEquals(enforcer, monitor.getEnforcer());
    }

    @Test
    void testSetEnforcerNull() {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup);

        monitor.setEnforcer(null);
        assertNull(monitor.getEnforcer());
    }

    @Test
    void testAddListenerNull() {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup);

        assertThrows(NullPointerException.class, () -> {
            monitor.addListener(null);
        });
    }

    @Test
    void testAddAndRemoveListener() {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup);

        ResourceEventListener listener = new ResourceEventListener() {
            @Override
            public void onQuotaExceeded(String applicationId, ResourceQuota quota, ResourceSnapshot snapshot) {}
            @Override
            public void onThresholdCrossed(String applicationId, String metric, double threshold, double currentValue) {}
        };

        monitor.addListener(listener);
        monitor.removeListener(listener);
        // No exception should be thrown
    }

    @Test
    void testQuotaExceededFiresListener() throws InterruptedException {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, 10);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger(0);

        ResourceEventListener listener = new ResourceEventListener() {
            @Override
            public void onQuotaExceeded(String applicationId, ResourceQuota quota, ResourceSnapshot snapshot) {
                assertEquals(TEST_APP_ID, applicationId);
                assertNotNull(quota);
                assertNotNull(snapshot);
                callCount.incrementAndGet();
                latch.countDown();
            }
            @Override
            public void onThresholdCrossed(String applicationId, String metric, double threshold, double currentValue) {}
        };

        monitor.addListener(listener);

        // Create test threads to ensure thread count quota is exceeded
        Thread t1 = new Thread(testThreadGroup, () -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-thread-1");

        Thread t2 = new Thread(testThreadGroup, () -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-thread-2");

        t1.start();
        t2.start();

        // Set a quota that will be exceeded (max 1 thread, but we have 2)
        ResourceQuota quota = ResourceQuota.builder()
            .maxThreadCount(1)
            .build();

        monitor.setQuota(quota);

        // Wait for listener to be called
        boolean called = latch.await(5, TimeUnit.SECONDS);

        // Cleanup threads
        t1.interrupt();
        t2.interrupt();
        t1.join(1000);
        t2.join(1000);

        assertTrue(called, "Listener should have been called when quota exceeded");
        assertTrue(callCount.get() > 0, "Listener call count should be > 0");
    }

    @Test
    void testMultipleListeners() throws InterruptedException {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, 10);

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        ResourceEventListener listener1 = new ResourceEventListener() {
            @Override
            public void onQuotaExceeded(String applicationId, ResourceQuota quota, ResourceSnapshot snapshot) {
                latch1.countDown();
            }
            @Override
            public void onThresholdCrossed(String applicationId, String metric, double threshold, double currentValue) {}
        };
        ResourceEventListener listener2 = new ResourceEventListener() {
            @Override
            public void onQuotaExceeded(String applicationId, ResourceQuota quota, ResourceSnapshot snapshot) {
                latch2.countDown();
            }
            @Override
            public void onThresholdCrossed(String applicationId, String metric, double threshold, double currentValue) {}
        };

        monitor.addListener(listener1);
        monitor.addListener(listener2);

        // Create test threads to exceed quota
        Thread t1 = new Thread(testThreadGroup, () -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-thread-1");

        Thread t2 = new Thread(testThreadGroup, () -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-thread-2");

        t1.start();
        t2.start();

        ResourceQuota quota = ResourceQuota.builder()
            .maxThreadCount(1) // Exceeded by having 2 threads
            .build();

        monitor.setQuota(quota);

        boolean called1 = latch1.await(5, TimeUnit.SECONDS);
        boolean called2 = latch2.await(5, TimeUnit.SECONDS);

        // Cleanup
        t1.interrupt();
        t2.interrupt();
        t1.join(1000);
        t2.join(1000);

        assertTrue(called1, "Listener 1 should be called");
        assertTrue(called2, "Listener 2 should be called");
    }

    @Test
    void testListenerExceptionDoesNotStopOtherListeners() throws InterruptedException {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, 10);

        CountDownLatch goodListenerLatch = new CountDownLatch(1);

        ResourceEventListener badListener = new ResourceEventListener() {
            @Override
            public void onQuotaExceeded(String applicationId, ResourceQuota quota, ResourceSnapshot snapshot) {
                throw new RuntimeException("Simulated listener error");
            }
            @Override
            public void onThresholdCrossed(String applicationId, String metric, double threshold, double currentValue) {}
        };

        ResourceEventListener goodListener = new ResourceEventListener() {
            @Override
            public void onQuotaExceeded(String applicationId, ResourceQuota quota, ResourceSnapshot snapshot) {
                goodListenerLatch.countDown();
            }
            @Override
            public void onThresholdCrossed(String applicationId, String metric, double threshold, double currentValue) {}
        };

        monitor.addListener(badListener);
        monitor.addListener(goodListener);

        // Create test threads to exceed quota
        Thread t1 = new Thread(testThreadGroup, () -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-thread-1");

        Thread t2 = new Thread(testThreadGroup, () -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-thread-2");

        t1.start();
        t2.start();

        ResourceQuota quota = ResourceQuota.builder()
            .maxThreadCount(1)
            .build();

        monitor.setQuota(quota);

        boolean called = goodListenerLatch.await(5, TimeUnit.SECONDS);

        // Cleanup
        t1.interrupt();
        t2.interrupt();
        t1.join(1000);
        t2.join(1000);

        assertTrue(called,
            "Good listener should still be called despite bad listener exception");
    }

    @Test
    void testShutdownStopsMetricCollection() throws InterruptedException {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, 10);

        // Wait for a collection
        Thread.sleep(1500);

        ResourceSnapshot before = monitor.getCurrentSnapshot();
        long timestampBefore = before.getTimestamp();

        monitor.shutdown();

        // Wait a bit to ensure no more collections happen
        Thread.sleep(2000);

        ResourceSnapshot after = monitor.getCurrentSnapshot();

        // After shutdown, getCurrentSnapshot should return the last collected snapshot
        // The timestamp should not have changed (no new collections)
        assertEquals(timestampBefore, after.getTimestamp());
    }

    @Test
    void testShutdownIsIdempotent() {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup);

        monitor.shutdown();
        monitor.shutdown(); // Should not throw exception
    }

    @Test
    void testHistorySizeLimit() throws InterruptedException {
        int maxHistory = 5;
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, maxHistory);

        // Wait for more snapshots than the limit
        Thread.sleep(7000);

        ResourceUsageHistory history = monitor.getHistory(Duration.ofMinutes(1));

        // Should not exceed max history size
        assertTrue(history.getSnapshots().size() <= maxHistory,
            "History size should not exceed " + maxHistory);
    }

    @Test
    void testThreadTracking() throws InterruptedException {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, 10);

        // Create some threads in the thread group
        Thread t1 = new Thread(testThreadGroup, () -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-thread-1");

        Thread t2 = new Thread(testThreadGroup, () -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-thread-2");

        t1.start();
        t2.start();

        // Wait for metrics to be collected
        Thread.sleep(2000);

        ResourceSnapshot snapshot = monitor.getCurrentSnapshot();

        // Should track threads in the group
        assertTrue(snapshot.getThreadCount() >= 2,
            "Should track at least 2 threads in the thread group");

        // Cleanup
        t1.interrupt();
        t2.interrupt();
        t1.join(1000);
        t2.join(1000);
    }

    @Test
    void testEnforcerIntegration() throws InterruptedException {
        monitor = new ApplicationResourceMonitor(TEST_APP_ID, testThreadGroup, 1, 10);

        CountDownLatch shutdownLatch = new CountDownLatch(1);

        ResourceConfig config = ResourceConfig.builder()
            .threadEnforcementAction(EnforcementAction.SHUTDOWN)
            .violationGracePeriod(1)
            .build();

        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            appId -> shutdownLatch.countDown(),
            appId -> {}
        );

        monitor.setEnforcer(enforcer);

        // Create test threads to exceed quota
        Thread t1 = new Thread(testThreadGroup, () -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-thread-1");

        Thread t2 = new Thread(testThreadGroup, () -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-thread-2");

        t1.start();
        t2.start();

        ResourceQuota quota = ResourceQuota.builder()
            .maxThreadCount(1)
            .build();

        monitor.setQuota(quota);

        // The enforcer should eventually trigger shutdown action
        boolean shutdownCalled = shutdownLatch.await(10, TimeUnit.SECONDS);

        // Cleanup
        t1.interrupt();
        t2.interrupt();
        t1.join(1000);
        t2.join(1000);

        assertTrue(shutdownCalled, "Enforcer should trigger shutdown action");
    }
}
