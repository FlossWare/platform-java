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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link ThreadPoolConfig}.
 */
class ThreadPoolConfigTest {

    @Test
    void testDefaultConfig() {
        ThreadPoolConfig config = ThreadPoolConfig.defaultConfig();

        assertNotNull(config);
        assertEquals(2, config.getCorePoolSize());
        assertEquals(10, config.getMaxPoolSize());
        assertEquals(60, config.getKeepAliveTimeSeconds());
        assertEquals(100, config.getQueueCapacity());
    }

    @Test
    void testBuilderWithValidValues() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
            .corePoolSize(5)
            .maxPoolSize(20)
            .keepAliveTimeSeconds(120)
            .queueCapacity(200)
            .build();

        assertEquals(5, config.getCorePoolSize());
        assertEquals(20, config.getMaxPoolSize());
        assertEquals(120, config.getKeepAliveTimeSeconds());
        assertEquals(200, config.getQueueCapacity());
    }

    @Test
    void testBuilderWithEqualCoreAndMaxPoolSize() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
            .corePoolSize(10)
            .maxPoolSize(10)
            .build();

        assertEquals(10, config.getCorePoolSize());
        assertEquals(10, config.getMaxPoolSize());
    }

    @Test
    void testBuilderRejectsNegativeCorePoolSize() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            ThreadPoolConfig.builder()
                .corePoolSize(-1)
                .build()
        );
        assertEquals("corePoolSize must be >= 0, got: -1", exception.getMessage());
    }

    @Test
    void testBuilderRejectsZeroMaxPoolSize() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            ThreadPoolConfig.builder()
                .maxPoolSize(0)
                .build()
        );
        assertEquals("maxPoolSize must be > 0, got: 0", exception.getMessage());
    }

    @Test
    void testBuilderRejectsNegativeMaxPoolSize() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            ThreadPoolConfig.builder()
                .maxPoolSize(-5)
                .build()
        );
        assertEquals("maxPoolSize must be > 0, got: -5", exception.getMessage());
    }

    @Test
    void testBuilderRejectsMaxPoolSizeLessThanCorePoolSize() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            ThreadPoolConfig.builder()
                .corePoolSize(10)
                .maxPoolSize(5)
                .build()
        );
        assertEquals("maxPoolSize (5) must be >= corePoolSize (10)", exception.getMessage());
    }

    @Test
    void testBuilderRejectsNegativeKeepAliveTime() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            ThreadPoolConfig.builder()
                .keepAliveTimeSeconds(-1)
                .build()
        );
        assertEquals("keepAliveTimeSeconds must be >= 0, got: -1", exception.getMessage());
    }

    @Test
    void testBuilderRejectsNegativeQueueCapacity() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            ThreadPoolConfig.builder()
                .queueCapacity(-100)
                .build()
        );
        assertEquals("queueCapacity must be >= 0, got: -100", exception.getMessage());
    }

    @Test
    void testBuilderWithZeroCorePoolSizeIsValid() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
            .corePoolSize(0)
            .maxPoolSize(5)
            .build();

        assertEquals(0, config.getCorePoolSize());
        assertEquals(5, config.getMaxPoolSize());
    }

    @Test
    void testBuilderWithZeroKeepAliveTimeIsValid() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
            .keepAliveTimeSeconds(0)
            .build();

        assertEquals(0, config.getKeepAliveTimeSeconds());
    }

    @Test
    void testBuilderWithZeroQueueCapacityIsValid() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
            .queueCapacity(0)
            .build();

        assertEquals(0, config.getQueueCapacity());
    }
}
