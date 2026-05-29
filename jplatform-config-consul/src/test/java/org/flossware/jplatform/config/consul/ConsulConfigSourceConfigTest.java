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

package org.flossware.jplatform.config.consul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsulConfigSourceConfigTest {

    @Test
    void testBuilderDefaults() {
        ConsulConfigSourceConfig config = ConsulConfigSourceConfig.builder().build();
        assertEquals("localhost", config.getHost());
        assertEquals(8500, config.getPort());
        assertNull(config.getToken());
        assertEquals("config", config.getKeyPrefix());
        assertTrue(config.isWatchEnabled());
        assertEquals(10, config.getWatchIntervalSeconds());
    }

    @Test
    void testBuilderWithAllFields() {
        ConsulConfigSourceConfig config = ConsulConfigSourceConfig.builder()
            .host("consul.example.com")
            .port(8501)
            .token("secret-token")
            .keyPrefix("myapp/config")
            .watchEnabled(false)
            .watchIntervalSeconds(30)
            .build();

        assertEquals("consul.example.com", config.getHost());
        assertEquals(8501, config.getPort());
        assertEquals("secret-token", config.getToken());
        assertEquals("myapp/config", config.getKeyPrefix());
        assertFalse(config.isWatchEnabled());
        assertEquals(30, config.getWatchIntervalSeconds());
    }

    @Test
    void testPortValidation() {
        assertThrows(IllegalArgumentException.class, () -> 
            ConsulConfigSourceConfig.builder().port(0).build());
        assertThrows(IllegalArgumentException.class, () -> 
            ConsulConfigSourceConfig.builder().port(65536).build());
    }

    @Test
    void testWatchIntervalValidation() {
        assertThrows(IllegalArgumentException.class, () -> 
            ConsulConfigSourceConfig.builder().watchIntervalSeconds(0).build());
    }

    @Test
    void testHostValidation() {
        assertThrows(IllegalStateException.class, () -> 
            ConsulConfigSourceConfig.builder().host(null).build());
        assertThrows(IllegalStateException.class, () -> 
            ConsulConfigSourceConfig.builder().host("  ").build());
    }

    @Test
    void testKeyPrefixValidation() {
        assertThrows(IllegalStateException.class, () -> 
            ConsulConfigSourceConfig.builder().keyPrefix(null).build());
    }
}
