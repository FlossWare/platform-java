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

package org.flossware.jplatform.registry.etcd;

import org.junit.jupiter.api.Test;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EtcdRegistryConfigTest {

    @Test
    void testDefaultValues() {
        EtcdRegistryConfig config = EtcdRegistryConfig.builder().build();

        assertEquals(1, config.getEndpoints().size());
        assertEquals("http://localhost:2379", config.getEndpoints().get(0));
        assertEquals(30, config.getLeaseTtl());
        assertNull(config.getNamespace());
    }

    @Test
    void testCustomEndpoint() {
        EtcdRegistryConfig config = EtcdRegistryConfig.builder()
            .addEndpoint("http://etcd.example.com:2379")
            .build();

        assertEquals(1, config.getEndpoints().size());
        assertEquals("http://etcd.example.com:2379", config.getEndpoints().get(0));
    }

    @Test
    void testMultipleEndpoints() {
        EtcdRegistryConfig config = EtcdRegistryConfig.builder()
            .endpoints(Arrays.asList("http://etcd1:2379", "http://etcd2:2379"))
            .build();

        assertEquals(2, config.getEndpoints().size());
        assertTrue(config.getEndpoints().contains("http://etcd1:2379"));
        assertTrue(config.getEndpoints().contains("http://etcd2:2379"));
    }

    @Test
    void testCustomLeaseTtl() {
        EtcdRegistryConfig config = EtcdRegistryConfig.builder()
            .leaseTtl(60)
            .build();

        assertEquals(60, config.getLeaseTtl());
    }

    @Test
    void testInvalidLeaseTtl() {
        assertThrows(IllegalArgumentException.class, () ->
            EtcdRegistryConfig.builder().leaseTtl(5).build()
        );
    }

    @Test
    void testNamespace() {
        EtcdRegistryConfig config = EtcdRegistryConfig.builder()
            .namespace("prod")
            .build();

        assertEquals("prod", config.getNamespace());
    }

    @Test
    void testBuilderChaining() {
        EtcdRegistryConfig config = EtcdRegistryConfig.builder()
            .addEndpoint("http://etcd.example.com:2379")
            .leaseTtl(45)
            .namespace("test")
            .build();

        assertEquals("http://etcd.example.com:2379", config.getEndpoints().get(0));
        assertEquals(45, config.getLeaseTtl());
        assertEquals("test", config.getNamespace());
    }

    @Test
    void testEmptyEndpoints() {
        assertThrows(IllegalStateException.class, () ->
            EtcdRegistryConfig.builder().endpoints(Arrays.asList()).build()
        );
    }
}
