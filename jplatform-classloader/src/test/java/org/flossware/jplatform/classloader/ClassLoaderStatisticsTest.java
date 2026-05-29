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

package org.flossware.jplatform.classloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ClassLoaderStatistics.
 * Tests constructor validation, getters, and calculations.
 */
class ClassLoaderStatisticsTest {

    @Test
    void testConstructorValid() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics("app1", 100, 50000L, 25L);

        assertEquals("app1", stats.getApplicationId());
        assertEquals(100, stats.getClassesLoaded());
        assertEquals(50000L, stats.getTotalBytesLoaded());
        assertEquals(25L, stats.getCacheHits());
    }

    @Test
    void testConstructorZeroValues() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics("app1", 0, 0L, 0L);

        assertEquals(0, stats.getClassesLoaded());
        assertEquals(0L, stats.getTotalBytesLoaded());
        assertEquals(0L, stats.getCacheHits());
    }

    @Test
    void testConstructorNullApplicationId() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClassLoaderStatistics(null, 10, 1000L, 5L)
        );
    }

    @Test
    void testConstructorNegativeClassesLoaded() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClassLoaderStatistics("app1", -1, 1000L, 0L)
        );
    }

    @Test
    void testConstructorNegativeTotalBytesLoaded() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClassLoaderStatistics("app1", 10, -1L, 0L)
        );
    }

    @Test
    void testConstructorNegativeCacheHits() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClassLoaderStatistics("app1", 10, 1000L, -1L)
        );
    }

    @Test
    void testConstructorCacheHitsExceedsClassesLoaded() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClassLoaderStatistics("app1", 10, 1000L, 15L)
        );
    }

    @Test
    void testConstructorCacheHitsEqualsClassesLoaded() {
        // This should be valid - all classes from cache
        ClassLoaderStatistics stats = new ClassLoaderStatistics("app1", 10, 1000L, 10L);

        assertEquals(10, stats.getClassesLoaded());
        assertEquals(10L, stats.getCacheHits());
    }

    @Test
    void testGetCacheHitRateWithClasses() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics("app1", 100, 50000L, 25L);

        assertEquals(0.25, stats.getCacheHitRate(), 0.0001);
    }

    @Test
    void testGetCacheHitRateWithNoClasses() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics("app1", 0, 0L, 0L);

        assertEquals(0.0, stats.getCacheHitRate(), 0.0001);
    }

    @Test
    void testGetCacheHitRateAllCached() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics("app1", 50, 10000L, 50L);

        assertEquals(1.0, stats.getCacheHitRate(), 0.0001);
    }

    @Test
    void testGetCacheHitRateNoCached() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics("app1", 50, 10000L, 0L);

        assertEquals(0.0, stats.getCacheHitRate(), 0.0001);
    }

    @Test
    void testToString() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics("app1", 100, 50000L, 25L);

        String str = stats.toString();

        assertTrue(str.contains("app1"));
        assertTrue(str.contains("classes=100"));
        assertTrue(str.contains("bytes=50000"));
        assertTrue(str.contains("cacheHits=25"));
        assertTrue(str.contains("hitRate=25.00%"));
    }

    @Test
    void testToStringZeroClasses() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics("app1", 0, 0L, 0L);

        String str = stats.toString();

        assertTrue(str.contains("classes=0"));
        assertTrue(str.contains("hitRate=0.00%"));
    }

    @Test
    void testToStringHundredPercentCached() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics("app1", 50, 10000L, 50L);

        String str = stats.toString();

        assertTrue(str.contains("hitRate=100.00%"));
    }

    @Test
    void testLargeValues() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "app1",
            Integer.MAX_VALUE,
            Long.MAX_VALUE,
            Integer.MAX_VALUE
        );

        assertEquals(Integer.MAX_VALUE, stats.getClassesLoaded());
        assertEquals(Long.MAX_VALUE, stats.getTotalBytesLoaded());
        assertEquals(Integer.MAX_VALUE, stats.getCacheHits());
        assertEquals(1.0, stats.getCacheHitRate(), 0.0001);
    }
}
