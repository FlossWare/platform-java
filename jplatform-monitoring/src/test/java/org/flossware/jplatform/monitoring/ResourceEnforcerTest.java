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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ResourceEnforcer.
 * Tests quota enforcement actions, grace periods, and different violation types.
 */
class ResourceEnforcerTest {

    private static final String TEST_APP_ID = "test-app";
    private ThreadGroup testThreadGroup;
    private ResourceConfig defaultConfig;
    private Consumer<String> mockShutdownAction;
    private Consumer<String> mockKillAction;
    private Consumer<ThreadGroup> mockThrottleAction;
    private AtomicInteger shutdownCount;
    private AtomicInteger killCount;
    private AtomicInteger throttleCount;

    @BeforeEach
    void setUp() {
        testThreadGroup = new ThreadGroup(TEST_APP_ID);
        shutdownCount = new AtomicInteger(0);
        killCount = new AtomicInteger(0);
        throttleCount = new AtomicInteger(0);

        mockShutdownAction = appId -> shutdownCount.incrementAndGet();
        mockKillAction = appId -> killCount.incrementAndGet();
        mockThrottleAction = tg -> throttleCount.incrementAndGet();

        defaultConfig = ResourceConfig.builder()
            .cpuEnforcementAction(EnforcementAction.NOTIFY)
            .memoryEnforcementAction(EnforcementAction.NOTIFY)
            .threadEnforcementAction(EnforcementAction.NOTIFY)
            .violationGracePeriod(1)
            .build();
    }

    @Test
    void testConstructorWithAllParameters() {
        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            defaultConfig,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction,
            mockThrottleAction
        );

        assertNotNull(enforcer);
        assertEquals(TEST_APP_ID, enforcer.getApplicationId());
        assertNotNull(enforcer.getPolicy());
    }

    @Test
    void testConstructorWithoutThrottle() {
        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            defaultConfig,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        assertNotNull(enforcer);
        assertEquals(TEST_APP_ID, enforcer.getApplicationId());
    }

    @Test
    void testConstructorNullApplicationId() {
        assertThrows(NullPointerException.class, () -> {
            new ResourceEnforcer(
                null,
                defaultConfig,
                testThreadGroup,
                mockShutdownAction,
                mockKillAction
            );
        });
    }

    @Test
    void testConstructorNullConfig() {
        assertThrows(NullPointerException.class, () -> {
            new ResourceEnforcer(
                TEST_APP_ID,
                null,
                testThreadGroup,
                mockShutdownAction,
                mockKillAction
            );
        });
    }

    @Test
    void testConstructorNullThreadGroup() {
        assertThrows(NullPointerException.class, () -> {
            new ResourceEnforcer(
                TEST_APP_ID,
                defaultConfig,
                null,
                mockShutdownAction,
                mockKillAction
            );
        });
    }

    @Test
    void testConstructorNullShutdownAction() {
        assertThrows(NullPointerException.class, () -> {
            new ResourceEnforcer(
                TEST_APP_ID,
                defaultConfig,
                testThreadGroup,
                null,
                mockKillAction
            );
        });
    }

    @Test
    void testConstructorNullKillAction() {
        assertThrows(NullPointerException.class, () -> {
            new ResourceEnforcer(
                TEST_APP_ID,
                defaultConfig,
                testThreadGroup,
                mockShutdownAction,
                null
            );
        });
    }

    @Test
    void testNotifyActionDoesNotTriggerCallbacks() {
        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            defaultConfig,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxCpuTimeNanos(1_000_000_000L) // 1 second
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            2_000_000_000L, // 2 seconds in nanos - exceeds quota
            0, 0, 0, 0,
            java.util.Collections.emptyMap()
        );

        enforcer.enforceQuota(quota, snapshot);

        assertEquals(0, shutdownCount.get());
        assertEquals(0, killCount.get());
    }

    @Test
    void testShutdownActionTriggered() {
        ResourceConfig config = ResourceConfig.builder()
            .cpuEnforcementAction(EnforcementAction.SHUTDOWN)
            .violationGracePeriod(1)
            .build();

        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxCpuTimeNanos(1_000_000_000L) // 1 second
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            2_000_000_000L, // 2 seconds - exceeds quota
            0, 0, 0, 0,
            java.util.Collections.emptyMap()
        );

        enforcer.enforceQuota(quota, snapshot);

        assertEquals(1, shutdownCount.get());
        assertEquals(0, killCount.get());
    }

    @Test
    void testKillActionTriggered() {
        ResourceConfig config = ResourceConfig.builder()
            .cpuEnforcementAction(EnforcementAction.KILL)
            .violationGracePeriod(1)
            .build();

        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxCpuTimeNanos(1_000_000_000L) // 1 second
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            2_000_000_000L,
            0, 0, 0, 0,
            java.util.Collections.emptyMap()
        );

        enforcer.enforceQuota(quota, snapshot);

        assertEquals(0, shutdownCount.get());
        assertEquals(1, killCount.get());
    }

    @Test
    void testThrottleActionTriggered() {
        ResourceConfig config = ResourceConfig.builder()
            .cpuEnforcementAction(EnforcementAction.THROTTLE)
            .violationGracePeriod(1)
            .build();

        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction,
            mockThrottleAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxCpuTimeNanos(1_000_000_000L) // 1 second
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            2_000_000_000L,
            0, 0, 0, 0,
            java.util.Collections.emptyMap()
        );

        enforcer.enforceQuota(quota, snapshot);

        assertEquals(1, throttleCount.get());
        assertEquals(0, shutdownCount.get());
        assertEquals(0, killCount.get());
    }

    @Test
    void testThrottleActionWithoutThrottleCallback() {
        ResourceConfig config = ResourceConfig.builder()
            .cpuEnforcementAction(EnforcementAction.THROTTLE)
            .violationGracePeriod(1)
            .build();

        // Create enforcer without throttle action
        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxCpuTimeNanos(1_000_000_000L) // 1 second
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            2_000_000_000L,
            0, 0, 0, 0,
            java.util.Collections.emptyMap()
        );

        // Should not throw exception, just log warning
        assertDoesNotThrow(() -> enforcer.enforceQuota(quota, snapshot));
    }

    @Test
    void testHeapQuotaEnforcement() {
        ResourceConfig config = ResourceConfig.builder()
            .memoryEnforcementAction(EnforcementAction.SHUTDOWN)
            .violationGracePeriod(1)
            .build();

        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxHeapBytes(100 * 1024 * 1024L) // 100 MB
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            0,
            200 * 1024 * 1024L, // 200 MB - exceeds quota
            0, 0, 0,
            java.util.Collections.emptyMap()
        );

        enforcer.enforceQuota(quota, snapshot);

        assertEquals(1, shutdownCount.get());
    }

    @Test
    void testThreadQuotaEnforcement() {
        ResourceConfig config = ResourceConfig.builder()
            .threadEnforcementAction(EnforcementAction.SHUTDOWN)
            .violationGracePeriod(1)
            .build();

        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxThreadCount(10)
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            0, 0,
            20, // 20 threads - exceeds quota
            0, 0,
            java.util.Collections.emptyMap()
        );

        enforcer.enforceQuota(quota, snapshot);

        assertEquals(1, shutdownCount.get());
    }

    @Test
    void testMultipleQuotaViolations() {
        ResourceConfig config = ResourceConfig.builder()
            .cpuEnforcementAction(EnforcementAction.SHUTDOWN)
            .memoryEnforcementAction(EnforcementAction.SHUTDOWN)
            .threadEnforcementAction(EnforcementAction.SHUTDOWN)
            .violationGracePeriod(1)
            .build();

        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxCpuTimeNanos(1_000_000_000L) // 1 second
            .maxHeapBytes(100 * 1024 * 1024L) // 100 MB
            .maxThreadCount(10)
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            2_000_000_000L, // CPU exceeded
            200 * 1024 * 1024L, // Heap exceeded
            20, // Threads exceeded
            0, 0,
            java.util.Collections.emptyMap()
        );

        enforcer.enforceQuota(quota, snapshot);

        // Should trigger shutdown action for each violation type
        assertEquals(3, shutdownCount.get());
    }

    @Test
    void testNoViolationDoesNotTriggerAction() {
        ResourceConfig config = ResourceConfig.builder()
            .cpuEnforcementAction(EnforcementAction.SHUTDOWN)
            .violationGracePeriod(1)
            .build();

        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxCpuTimeNanos(10_000_000_000L) // 10 seconds
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            1_000_000_000L, // 1 second - under quota
            0, 0, 0, 0,
            java.util.Collections.emptyMap()
        );

        enforcer.enforceQuota(quota, snapshot);

        assertEquals(0, shutdownCount.get());
        assertEquals(0, killCount.get());
    }

    @Test
    void testActionExceptionDoesNotPropagate() {
        Consumer<String> failingAction = appId -> {
            throw new RuntimeException("Simulated action failure");
        };

        ResourceConfig config = ResourceConfig.builder()
            .cpuEnforcementAction(EnforcementAction.SHUTDOWN)
            .violationGracePeriod(1)
            .build();

        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            failingAction,
            mockKillAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxCpuTimeNanos(1_000_000_000L) // 1 second
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            2_000_000_000L,
            0, 0, 0, 0,
            java.util.Collections.emptyMap()
        );

        // Should not throw exception
        assertDoesNotThrow(() -> enforcer.enforceQuota(quota, snapshot));
    }

    @Test
    void testGracePeriodPreventsImmediateAction() {
        ResourceConfig config = ResourceConfig.builder()
            .cpuEnforcementAction(EnforcementAction.SHUTDOWN)
            .violationGracePeriod(10) // 10 violations grace period
            .build();

        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            config,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        ResourceQuota quota = ResourceQuota.builder()
            .maxCpuTimeNanos(1_000_000_000L) // 1 second
            .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            2_000_000_000L,
            0, 0, 0, 0,
            java.util.Collections.emptyMap()
        );

        // First violation - should not trigger action due to grace period
        enforcer.enforceQuota(quota, snapshot);

        assertEquals(0, shutdownCount.get());
    }

    @Test
    void testGetPolicy() {
        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            defaultConfig,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        EnforcementPolicy policy = enforcer.getPolicy();
        assertNotNull(policy);
    }

    @Test
    void testGetApplicationId() {
        ResourceEnforcer enforcer = new ResourceEnforcer(
            TEST_APP_ID,
            defaultConfig,
            testThreadGroup,
            mockShutdownAction,
            mockKillAction
        );

        assertEquals(TEST_APP_ID, enforcer.getApplicationId());
    }
}
