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

package org.flossware.jplatform.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResourceQuota.
 * Tests builder validation and quota enforcement.
 */
class ResourceQuotaTest {

    @Test
    void testBuilderAcceptsValidValues() {
        ResourceQuota quota = ResourceQuota.builder()
                .maxHeapBytes(512 * 1024 * 1024)
                .maxThreadCount(50)
                .maxCpuTimeNanos(60_000_000_000L)
                .build();

        assertTrue(quota.getMaxHeapBytes().isPresent());
        assertEquals(512 * 1024 * 1024, quota.getMaxHeapBytes().get());

        assertTrue(quota.getMaxThreadCount().isPresent());
        assertEquals(50, quota.getMaxThreadCount().get());

        assertTrue(quota.getMaxCpuTimeNanos().isPresent());
        assertEquals(60_000_000_000L, quota.getMaxCpuTimeNanos().get());
    }

    @Test
    void testBuilderRejectsZeroHeapBytes() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ResourceQuota.builder().maxHeapBytes(0);
        });

        assertTrue(exception.getMessage().contains("maxHeapBytes must be > 0"));
    }

    @Test
    void testBuilderRejectsZeroThreadCount() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ResourceQuota.builder().maxThreadCount(0);
        });

        assertTrue(exception.getMessage().contains("maxThreadCount must be > 0"));
    }

    @Test
    void testBuilderRejectsZeroCpuTimeNanos() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ResourceQuota.builder().maxCpuTimeNanos(0);
        });

        assertTrue(exception.getMessage().contains("maxCpuTimeNanos must be > 0"));
    }

    @Test
    void testBuilderRejectsNegativeHeapBytes() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ResourceQuota.builder().maxHeapBytes(-512);
        });

        assertEquals("maxHeapBytes must be > 0, got: -512", exception.getMessage());
    }

    @Test
    void testBuilderRejectsNegativeThreadCount() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ResourceQuota.builder().maxThreadCount(-10);
        });

        assertEquals("maxThreadCount must be > 0, got: -10", exception.getMessage());
    }

    @Test
    void testBuilderRejectsNegativeCpuTimeNanos() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ResourceQuota.builder().maxCpuTimeNanos(-1000);
        });

        assertEquals("maxCpuTimeNanos must be > 0, got: -1000", exception.getMessage());
    }

    @Test
    void testBuilderWithNoLimits() {
        ResourceQuota quota = ResourceQuota.builder().build();

        assertFalse(quota.getMaxHeapBytes().isPresent());
        assertFalse(quota.getMaxThreadCount().isPresent());
        assertFalse(quota.getMaxCpuTimeNanos().isPresent());
    }

    @Test
    void testEnforceHeapQuotaExceeded() {
        ResourceQuota quota = ResourceQuota.builder()
                .maxHeapBytes(100 * 1024 * 1024) // 100 MB
                .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
                System.currentTimeMillis(),
                1_000_000_000L,      // CPU time
                200 * 1024 * 1024L,  // 200 MB - exceeds quota
                10,                  // thread count
                0L,                  // bytes read
                0L,                  // bytes written
                null                 // custom metrics
        );

        ResourceQuotaExceededException exception = assertThrows(ResourceQuotaExceededException.class, () -> {
            quota.enforce(snapshot);
        });

        assertTrue(exception.getMessage().contains("Heap quota exceeded"));
    }

    @Test
    void testEnforceThreadCountQuotaExceeded() {
        ResourceQuota quota = ResourceQuota.builder()
                .maxThreadCount(20)
                .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
                System.currentTimeMillis(),
                1_000_000_000L,      // CPU time
                50 * 1024 * 1024L,   // heap
                30,                  // exceeds quota
                0L,                  // bytes read
                0L,                  // bytes written
                null                 // custom metrics
        );

        ResourceQuotaExceededException exception = assertThrows(ResourceQuotaExceededException.class, () -> {
            quota.enforce(snapshot);
        });

        assertTrue(exception.getMessage().contains("Thread count quota exceeded"));
    }

    @Test
    void testEnforceCpuTimeQuotaExceeded() {
        ResourceQuota quota = ResourceQuota.builder()
                .maxCpuTimeNanos(10_000_000_000L) // 10 seconds
                .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
                System.currentTimeMillis(),
                20_000_000_000L,     // 20 seconds - exceeds quota
                50 * 1024 * 1024L,   // heap
                10,                  // thread count
                0L,                  // bytes read
                0L,                  // bytes written
                null                 // custom metrics
        );

        ResourceQuotaExceededException exception = assertThrows(ResourceQuotaExceededException.class, () -> {
            quota.enforce(snapshot);
        });

        assertTrue(exception.getMessage().contains("CPU time quota exceeded"));
    }

    @Test
    void testEnforceWithinQuota() {
        ResourceQuota quota = ResourceQuota.builder()
                .maxHeapBytes(100 * 1024 * 1024)
                .maxThreadCount(50)
                .maxCpuTimeNanos(60_000_000_000L)
                .build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
                System.currentTimeMillis(),
                30_000_000_000L,     // 30 seconds - within quota
                50 * 1024 * 1024L,   // 50 MB - within quota
                25,                  // 25 threads - within quota
                0L,                  // bytes read
                0L,                  // bytes written
                null                 // custom metrics
        );

        assertDoesNotThrow(() -> quota.enforce(snapshot));
    }

    @Test
    void testEnforceWithNoQuotasSet() {
        ResourceQuota quota = ResourceQuota.builder().build();

        ResourceSnapshot snapshot = new ResourceSnapshot(
                System.currentTimeMillis(),
                Long.MAX_VALUE,      // CPU time
                Long.MAX_VALUE,      // heap
                Integer.MAX_VALUE,   // threads
                0L,                  // bytes read
                0L,                  // bytes written
                null                 // custom metrics
        );

        // No quotas set, so no enforcement should occur
        assertDoesNotThrow(() -> quota.enforce(snapshot));
    }
}
