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

package org.flossware.jplatform.config.vault;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultConfigSourceConfigTest {

    @Test
    void testBuilderDefaults() {
        VaultConfigSourceConfig config = VaultConfigSourceConfig.builder()
            .token("test-token")
            .build();

        assertEquals("http://localhost:8200", config.getAddress());
        assertEquals("test-token", config.getToken());
        assertEquals("secret/config", config.getSecretPath());
        assertNull(config.getNamespace());
        assertEquals(3, config.getMaxRetries());
        assertEquals(1000, config.getRetryIntervalMs());
        assertEquals(5, config.getOpenTimeout());
        assertEquals(30, config.getReadTimeout());
    }

    @Test
    void testBuilderCustomValues() {
        VaultConfigSourceConfig config = VaultConfigSourceConfig.builder()
            .address("https://vault.example.com:8200")
            .token("s.1234567890")
            .secretPath("secret/myapp")
            .namespace("production")
            .maxRetries(5)
            .retryIntervalMs(2000)
            .openTimeout(10)
            .readTimeout(60)
            .build();

        assertEquals("https://vault.example.com:8200", config.getAddress());
        assertEquals("s.1234567890", config.getToken());
        assertEquals("secret/myapp", config.getSecretPath());
        assertEquals("production", config.getNamespace());
        assertEquals(5, config.getMaxRetries());
        assertEquals(2000, config.getRetryIntervalMs());
        assertEquals(10, config.getOpenTimeout());
        assertEquals(60, config.getReadTimeout());
    }

    @Test
    void testBuilderMissingAddress() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            VaultConfigSourceConfig.builder()
                .address("")
                .token("test-token")
                .build();
        });
        assertTrue(exception.getMessage().contains("Address"));
    }

    @Test
    void testBuilderMissingToken() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            VaultConfigSourceConfig.builder()
                .token("")
                .build();
        });
        assertTrue(exception.getMessage().contains("Token"));
    }

    @Test
    void testBuilderMissingSecretPath() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            VaultConfigSourceConfig.builder()
                .token("test-token")
                .secretPath("")
                .build();
        });
        assertTrue(exception.getMessage().contains("Secret path"));
    }

    @Test
    void testBuilderInvalidMaxRetries() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            VaultConfigSourceConfig.builder()
                .token("test-token")
                .maxRetries(-1)
                .build();
        });
        assertTrue(exception.getMessage().contains("at least 0"));
    }

    @Test
    void testBuilderInvalidRetryInterval() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            VaultConfigSourceConfig.builder()
                .token("test-token")
                .retryIntervalMs(50)
                .build();
        });
        assertTrue(exception.getMessage().contains("at least 100ms"));
    }

    @Test
    void testBuilderInvalidOpenTimeout() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            VaultConfigSourceConfig.builder()
                .token("test-token")
                .openTimeout(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("at least 1 second"));
    }

    @Test
    void testBuilderInvalidReadTimeout() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            VaultConfigSourceConfig.builder()
                .token("test-token")
                .readTimeout(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("at least 1 second"));
    }

    @Test
    void testBuilderWithNamespace() {
        VaultConfigSourceConfig config = VaultConfigSourceConfig.builder()
            .token("test-token")
            .namespace("development")
            .build();

        assertEquals("development", config.getNamespace());
    }
}
