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
 * Unit tests for ThreadPoolStats.
 */
class ThreadPoolStatsTest {

    @Test
    void testConstructor_validParameters() {
        ThreadPoolStats stats = new ThreadPoolStats(5, 100, 10, 10, 2, 20);

        assertEquals(5, stats.getActiveThreads());
        assertEquals(100, stats.getCompletedTasks());
        assertEquals(10, stats.getQueuedTasks());
        assertEquals(10, stats.getPoolSize());
        assertEquals(2, stats.getCorePoolSize());
        assertEquals(20, stats.getMaximumPoolSize());
    }

    @Test
    void testConstructor_negativeActiveThreads() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(-1, 100, 10, 10, 2, 20));

        assertTrue(exception.getMessage().contains("activeThreads cannot be negative"));
    }

    @Test
    void testConstructor_negativeCompletedTasks() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(5, -1, 10, 10, 2, 20));

        assertTrue(exception.getMessage().contains("completedTasks cannot be negative"));
    }

    @Test
    void testConstructor_negativeQueuedTasks() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(5, 100, -1, 10, 2, 20));

        assertTrue(exception.getMessage().contains("queuedTasks cannot be negative"));
    }

    @Test
    void testConstructor_negativePoolSize() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(5, 100, 10, -1, 2, 20));

        assertTrue(exception.getMessage().contains("poolSize cannot be negative"));
    }

    @Test
    void testConstructor_negativeCorePoolSize() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(5, 100, 10, 10, -1, 20));

        assertTrue(exception.getMessage().contains("corePoolSize cannot be negative"));
    }

    @Test
    void testConstructor_negativeMaximumPoolSize() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(5, 100, 10, 10, 2, -1));

        assertTrue(exception.getMessage().contains("maximumPoolSize cannot be negative"));
    }

    @Test
    void testConstructor_maximumLessThanCore() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(5, 100, 10, 10, 20, 10));

        assertTrue(exception.getMessage().contains("maximumPoolSize"));
        assertTrue(exception.getMessage().contains("cannot be less than corePoolSize"));
    }

    @Test
    void testConstructor_poolSizeExceedsMaximum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(5, 100, 10, 50, 2, 20));

        assertTrue(exception.getMessage().contains("poolSize"));
        assertTrue(exception.getMessage().contains("cannot exceed maximumPoolSize"));
    }

    @Test
    void testConstructor_activeThreadsExceedsPoolSize() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(15, 100, 10, 10, 2, 20));

        assertTrue(exception.getMessage().contains("activeThreads"));
        assertTrue(exception.getMessage().contains("cannot exceed poolSize"));
    }

    @Test
    void testConstructor_zeroValues() {
        ThreadPoolStats stats = new ThreadPoolStats(0, 0, 0, 0, 0, 0);

        assertEquals(0, stats.getActiveThreads());
        assertEquals(0, stats.getCompletedTasks());
        assertEquals(0, stats.getQueuedTasks());
        assertEquals(0, stats.getPoolSize());
        assertEquals(0, stats.getCorePoolSize());
        assertEquals(0, stats.getMaximumPoolSize());
    }

    @Test
    void testConstructor_poolSizeEqualsMaximum() {
        // Pool size can equal maximum (fully scaled)
        ThreadPoolStats stats = new ThreadPoolStats(5, 100, 10, 20, 2, 20);

        assertEquals(20, stats.getPoolSize());
        assertEquals(20, stats.getMaximumPoolSize());
    }

    @Test
    void testConstructor_activeThreadsEqualsPoolSize() {
        // Active threads can equal pool size (all busy)
        ThreadPoolStats stats = new ThreadPoolStats(10, 100, 10, 10, 2, 20);

        assertEquals(10, stats.getActiveThreads());
        assertEquals(10, stats.getPoolSize());
    }

    @Test
    void testConstructor_coreEqualsMaximum() {
        // Core can equal maximum (fixed-size pool)
        ThreadPoolStats stats = new ThreadPoolStats(5, 100, 10, 10, 10, 10);

        assertEquals(10, stats.getCorePoolSize());
        assertEquals(10, stats.getMaximumPoolSize());
    }

    @Test
    void testConstructor_largeValues() {
        ThreadPoolStats stats = new ThreadPoolStats(
                100,
                Long.MAX_VALUE,
                1000,
                200,
                50,
                200);

        assertEquals(100, stats.getActiveThreads());
        assertEquals(Long.MAX_VALUE, stats.getCompletedTasks());
        assertEquals(1000, stats.getQueuedTasks());
        assertEquals(200, stats.getPoolSize());
        assertEquals(50, stats.getCorePoolSize());
        assertEquals(200, stats.getMaximumPoolSize());
    }

    @Test
    void testToString() {
        ThreadPoolStats stats = new ThreadPoolStats(5, 100, 10, 10, 2, 20);

        String str = stats.toString();
        assertTrue(str.contains("active=5"));
        assertTrue(str.contains("completed=100"));
        assertTrue(str.contains("queued=10"));
        assertTrue(str.contains("poolSize=10"));
        assertTrue(str.contains("core=2"));
        assertTrue(str.contains("max=20"));
    }

    @Test
    void testConstructor_validBoundary_poolSize() {
        // poolSize = maximumPoolSize - 1 (valid)
        ThreadPoolStats stats = new ThreadPoolStats(5, 100, 10, 19, 2, 20);

        assertEquals(19, stats.getPoolSize());
    }

    @Test
    void testConstructor_validBoundary_activeThreads() {
        // activeThreads = poolSize - 1 (valid)
        ThreadPoolStats stats = new ThreadPoolStats(9, 100, 10, 10, 2, 20);

        assertEquals(9, stats.getActiveThreads());
    }

    @Test
    void testConstructor_invalidBoundary_poolSizeExceedsMaxByOne() {
        // poolSize = maximumPoolSize + 1 (invalid)
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(5, 100, 10, 21, 2, 20));

        assertTrue(exception.getMessage().contains("poolSize"));
        assertTrue(exception.getMessage().contains("21"));
        assertTrue(exception.getMessage().contains("20"));
    }

    @Test
    void testConstructor_invalidBoundary_activeThreadsExceedsPoolByOne() {
        // activeThreads = poolSize + 1 (invalid)
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPoolStats(11, 100, 10, 10, 2, 20));

        assertTrue(exception.getMessage().contains("activeThreads"));
        assertTrue(exception.getMessage().contains("11"));
        assertTrue(exception.getMessage().contains("10"));
    }
}
