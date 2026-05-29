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

package org.flossware.jplatform.cluster.etcd;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for EtcdConfig.
 * Tests builder validation, default values, and all configuration options.
 */
class EtcdConfigTest {

    @Test
    void testDefaultConfiguration() {
        EtcdConfig config = EtcdConfig.builder().build();

        assertNotNull(config);
        assertEquals(1, config.getEndpoints().size());
        assertEquals("http://localhost:2379", config.getEndpoints().get(0));
        assertEquals(10L, config.getLeaseTtl());
        assertNull(config.getNamespace());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
    }

    @Test
    void testAddSingleEndpoint() {
        EtcdConfig config = EtcdConfig.builder()
            .addEndpoint("http://etcd1:2379")
            .build();

        assertEquals(1, config.getEndpoints().size());
        assertEquals("http://etcd1:2379", config.getEndpoints().get(0));
    }

    @Test
    void testAddMultipleEndpoints() {
        EtcdConfig config = EtcdConfig.builder()
            .addEndpoint("http://etcd1:2379")
            .addEndpoint("http://etcd2:2379")
            .addEndpoint("http://etcd3:2379")
            .build();

        assertEquals(3, config.getEndpoints().size());
        assertTrue(config.getEndpoints().contains("http://etcd1:2379"));
        assertTrue(config.getEndpoints().contains("http://etcd2:2379"));
        assertTrue(config.getEndpoints().contains("http://etcd3:2379"));
    }

    @Test
    void testAddEndpointRemovesDefaultLocalhost() {
        EtcdConfig config = EtcdConfig.builder()
            .addEndpoint("http://etcd1:2379")
            .build();

        assertEquals(1, config.getEndpoints().size());
        assertEquals("http://etcd1:2379", config.getEndpoints().get(0));
        assertFalse(config.getEndpoints().contains("http://localhost:2379"));
    }

    @Test
    void testAddLocalhostExplicitlyAddsAnother() {
        EtcdConfig config = EtcdConfig.builder()
            .addEndpoint("http://localhost:2379")
            .build();

        // Default localhost + explicit localhost = 2
        assertEquals(2, config.getEndpoints().size());
        assertEquals("http://localhost:2379", config.getEndpoints().get(0));
        assertEquals("http://localhost:2379", config.getEndpoints().get(1));
    }

    @Test
    void testAddLocalhostThenRemoteEndpoint() {
        EtcdConfig config = EtcdConfig.builder()
            .addEndpoint("http://localhost:2379")
            .addEndpoint("http://etcd1:2379")
            .build();

        // Default localhost + explicit localhost + remote = 3
        assertEquals(3, config.getEndpoints().size());
        assertTrue(config.getEndpoints().contains("http://localhost:2379"));
        assertTrue(config.getEndpoints().contains("http://etcd1:2379"));
    }

    @Test
    void testAddEndpointNullThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            EtcdConfig.builder().addEndpoint(null)
        );
    }

    @Test
    void testAddEndpointEmptyThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            EtcdConfig.builder().addEndpoint("")
        );
    }

    @Test
    void testAddEndpointWhitespaceThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            EtcdConfig.builder().addEndpoint("   ")
        );
    }

    @Test
    void testSetEndpointsList() {
        List<String> endpoints = Arrays.asList(
            "http://etcd1:2379",
            "http://etcd2:2379",
            "http://etcd3:2379"
        );

        EtcdConfig config = EtcdConfig.builder()
            .endpoints(endpoints)
            .build();

        assertEquals(3, config.getEndpoints().size());
        assertEquals(endpoints, config.getEndpoints());
    }

    @Test
    void testSetEndpointsReplacesExisting() {
        List<String> endpoints = Arrays.asList(
            "http://etcd1:2379",
            "http://etcd2:2379"
        );

        EtcdConfig config = EtcdConfig.builder()
            .addEndpoint("http://temp:2379")
            .endpoints(endpoints)
            .build();

        assertEquals(2, config.getEndpoints().size());
        assertFalse(config.getEndpoints().contains("http://temp:2379"));
        assertEquals(endpoints, config.getEndpoints());
    }

    @Test
    void testSetEndpointsNullThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            EtcdConfig.builder().endpoints(null)
        );
    }

    @Test
    void testSetEndpointsWithNullEntryThrowsException() {
        List<String> endpoints = Arrays.asList("http://etcd1:2379", null);

        assertThrows(IllegalArgumentException.class, () ->
            EtcdConfig.builder().endpoints(endpoints)
        );
    }

    @Test
    void testSetEndpointsWithEmptyEntryThrowsException() {
        List<String> endpoints = Arrays.asList("http://etcd1:2379", "");

        assertThrows(IllegalArgumentException.class, () ->
            EtcdConfig.builder().endpoints(endpoints)
        );
    }

    @Test
    void testEndpointsImmutable() {
        EtcdConfig config = EtcdConfig.builder()
            .addEndpoint("http://etcd1:2379")
            .build();

        assertThrows(UnsupportedOperationException.class, () ->
            config.getEndpoints().add("http://etcd2:2379")
        );
    }

    @Test
    void testSetLeaseTtl() {
        EtcdConfig config = EtcdConfig.builder()
            .leaseTtl(30)
            .build();

        assertEquals(30L, config.getLeaseTtl());
    }

    @Test
    void testSetLeaseTtlMinimum() {
        EtcdConfig config = EtcdConfig.builder()
            .leaseTtl(5)
            .build();

        assertEquals(5L, config.getLeaseTtl());
    }

    @Test
    void testSetLeaseTtlMaximum() {
        EtcdConfig config = EtcdConfig.builder()
            .leaseTtl(86400)
            .build();

        assertEquals(86400L, config.getLeaseTtl());
    }

    @Test
    void testSetLeaseTtlTooSmallThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            EtcdConfig.builder().leaseTtl(4)
        );
    }

    @Test
    void testSetLeaseTtlTooLargeThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            EtcdConfig.builder().leaseTtl(86401)
        );
    }

    @Test
    void testSetNamespace() {
        EtcdConfig config = EtcdConfig.builder()
            .namespace("/mynamespace")
            .build();

        assertEquals("/mynamespace", config.getNamespace());
    }

    @Test
    void testSetNamespaceNull() {
        EtcdConfig config = EtcdConfig.builder()
            .namespace(null)
            .build();

        assertNull(config.getNamespace());
    }

    @Test
    void testSetUsername() {
        EtcdConfig config = EtcdConfig.builder()
            .username("admin")
            .build();

        assertEquals("admin", config.getUsername());
    }

    @Test
    void testSetUsernameNull() {
        EtcdConfig config = EtcdConfig.builder()
            .username(null)
            .build();

        assertNull(config.getUsername());
    }

    @Test
    void testSetPassword() {
        EtcdConfig config = EtcdConfig.builder()
            .password("secret")
            .build();

        assertEquals("secret", config.getPassword());
    }

    @Test
    void testSetPasswordNull() {
        EtcdConfig config = EtcdConfig.builder()
            .password(null)
            .build();

        assertNull(config.getPassword());
    }

    @Test
    void testCompleteConfiguration() {
        EtcdConfig config = EtcdConfig.builder()
            .addEndpoint("http://etcd1:2379")
            .addEndpoint("http://etcd2:2379")
            .leaseTtl(20)
            .namespace("/production")
            .username("admin")
            .password("secret")
            .build();

        assertEquals(2, config.getEndpoints().size());
        assertEquals(20L, config.getLeaseTtl());
        assertEquals("/production", config.getNamespace());
        assertEquals("admin", config.getUsername());
        assertEquals("secret", config.getPassword());
    }

    @Test
    void testBuilderReuse() {
        EtcdConfig.Builder builder = EtcdConfig.builder()
            .addEndpoint("http://etcd1:2379")
            .leaseTtl(15);

        EtcdConfig config1 = builder.build();
        EtcdConfig config2 = builder.build();

        assertNotSame(config1, config2);
        assertEquals(config1.getEndpoints(), config2.getEndpoints());
        assertEquals(config1.getLeaseTtl(), config2.getLeaseTtl());
    }
}
