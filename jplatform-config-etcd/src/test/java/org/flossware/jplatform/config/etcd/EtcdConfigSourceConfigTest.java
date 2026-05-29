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

package org.flossware.jplatform.config.etcd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtcdConfigSourceConfigTest {

    @Test
    void testBuilderDefaults() {
        EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder().build();

        assertEquals("http://localhost:2379", config.getEndpoints());
        assertEquals("/config", config.getKeyPrefix());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertTrue(config.isWatchEnabled());
        assertEquals(5, config.getWatchRetryDelaySeconds());
    }

    @Test
    void testBuilderCustomValues() {
        EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder()
            .endpoints("http://etcd1:2379,http://etcd2:2379")
            .keyPrefix("/app/config")
            .username("admin")
            .password("secret")
            .watchEnabled(false)
            .watchRetryDelaySeconds(10)
            .build();

        assertEquals("http://etcd1:2379,http://etcd2:2379", config.getEndpoints());
        assertEquals("/app/config", config.getKeyPrefix());
        assertEquals("admin", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertFalse(config.isWatchEnabled());
        assertEquals(10, config.getWatchRetryDelaySeconds());
    }

    @Test
    void testBuilderMissingEndpoints() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            EtcdConfigSourceConfig.builder()
                .endpoints("")
                .build();
        });
        assertTrue(exception.getMessage().contains("Endpoints"));
    }

    @Test
    void testBuilderMissingKeyPrefix() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            EtcdConfigSourceConfig.builder()
                .keyPrefix("")
                .build();
        });
        assertTrue(exception.getMessage().contains("Key prefix"));
    }

    @Test
    void testBuilderInvalidWatchRetryDelay() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            EtcdConfigSourceConfig.builder()
                .watchRetryDelaySeconds(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("at least 1"));
    }

    @Test
    void testBuilderWithAuthentication() {
        EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder()
            .username("testuser")
            .password("testpass")
            .build();

        assertEquals("testuser", config.getUsername());
        assertEquals("testpass", config.getPassword());
    }
}
