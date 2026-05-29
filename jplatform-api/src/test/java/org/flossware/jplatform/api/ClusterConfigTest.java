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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ClusterConfig and its Builder.
 * Tests builder validation, defaults, and configuration options.
 */
class ClusterConfigTest {

    @Test
    void testBuilderDefaults() {
        ClusterConfig config = ClusterConfig.builder().build();

        assertEquals("jplatform-cluster", config.getClusterName());
        assertEquals("localhost", config.getBindAddress());
        assertEquals(5701, config.getBindPort());
        assertTrue(config.getSeedNodes().isEmpty());
        assertEquals(ClusterConfig.StateBackend.HAZELCAST, config.getStateBackend());
    }

    @Test
    void testBuilderAllFields() {
        ClusterConfig config = ClusterConfig.builder()
            .clusterName("production-cluster")
            .bindAddress("192.168.1.10")
            .bindPort(6701)
            .addSeedNode("192.168.1.10:5701")
            .addSeedNode("192.168.1.11:5701")
            .stateBackend(ClusterConfig.StateBackend.REDIS)
            .build();

        assertEquals("production-cluster", config.getClusterName());
        assertEquals("192.168.1.10", config.getBindAddress());
        assertEquals(6701, config.getBindPort());
        assertEquals(2, config.getSeedNodes().size());
        assertEquals("192.168.1.10:5701", config.getSeedNodes().get(0));
        assertEquals("192.168.1.11:5701", config.getSeedNodes().get(1));
        assertEquals(ClusterConfig.StateBackend.REDIS, config.getStateBackend());
    }

    @Test
    void testBuilderClusterNameNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().clusterName(null)
        );
    }

    @Test
    void testBuilderClusterNameEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().clusterName("")
        );
    }

    @Test
    void testBuilderClusterNameWhitespace() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().clusterName("   ")
        );
    }

    @Test
    void testBuilderBindAddressNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().bindAddress(null)
        );
    }

    @Test
    void testBuilderBindAddressEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().bindAddress("")
        );
    }

    @Test
    void testBuilderBindAddressWhitespace() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().bindAddress("   ")
        );
    }

    @Test
    void testBuilderBindPortZero() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().bindPort(0)
        );
    }

    @Test
    void testBuilderBindPortNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().bindPort(-1)
        );
    }

    @Test
    void testBuilderBindPortTooHigh() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().bindPort(65536)
        );
    }

    @Test
    void testBuilderBindPortMinValid() {
        ClusterConfig config = ClusterConfig.builder()
            .bindPort(1)
            .build();

        assertEquals(1, config.getBindPort());
    }

    @Test
    void testBuilderBindPortMaxValid() {
        ClusterConfig config = ClusterConfig.builder()
            .bindPort(65535)
            .build();

        assertEquals(65535, config.getBindPort());
    }

    @Test
    void testBuilderAddSeedNodeNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().addSeedNode(null)
        );
    }

    @Test
    void testBuilderAddSeedNodeEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().addSeedNode("")
        );
    }

    @Test
    void testBuilderAddSeedNodeWhitespace() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().addSeedNode("   ")
        );
    }

    @Test
    void testBuilderSeedNodesNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().seedNodes(null)
        );
    }

    @Test
    void testBuilderSeedNodesReplacesPrevious() {
        ClusterConfig config = ClusterConfig.builder()
            .addSeedNode("old-node:5701")
            .seedNodes(Arrays.asList("new-node1:5701", "new-node2:5701"))
            .build();

        assertEquals(2, config.getSeedNodes().size());
        assertEquals("new-node1:5701", config.getSeedNodes().get(0));
        assertEquals("new-node2:5701", config.getSeedNodes().get(1));
        assertFalse(config.getSeedNodes().contains("old-node:5701"));
    }

    @Test
    void testBuilderSeedNodesEmpty() {
        ClusterConfig config = ClusterConfig.builder()
            .addSeedNode("node1:5701")
            .seedNodes(List.of())
            .build();

        assertTrue(config.getSeedNodes().isEmpty());
    }

    @Test
    void testBuilderStateBackendNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ClusterConfig.builder().stateBackend(null)
        );
    }

    @Test
    void testBuilderStateBackendHazelcast() {
        ClusterConfig config = ClusterConfig.builder()
            .stateBackend(ClusterConfig.StateBackend.HAZELCAST)
            .build();

        assertEquals(ClusterConfig.StateBackend.HAZELCAST, config.getStateBackend());
    }

    @Test
    void testBuilderStateBackendRedis() {
        ClusterConfig config = ClusterConfig.builder()
            .stateBackend(ClusterConfig.StateBackend.REDIS)
            .build();

        assertEquals(ClusterConfig.StateBackend.REDIS, config.getStateBackend());
    }

    @Test
    void testBuilderStateBackendMemory() {
        ClusterConfig config = ClusterConfig.builder()
            .stateBackend(ClusterConfig.StateBackend.MEMORY)
            .build();

        assertEquals(ClusterConfig.StateBackend.MEMORY, config.getStateBackend());
    }

    @Test
    void testGetSeedNodesIsUnmodifiable() {
        ClusterConfig config = ClusterConfig.builder()
            .addSeedNode("node1:5701")
            .build();

        List<String> seedNodes = config.getSeedNodes();

        assertThrows(UnsupportedOperationException.class, () ->
            seedNodes.add("node2:5701")
        );
    }

    @Test
    void testStateBackendEnumValues() {
        ClusterConfig.StateBackend[] backends = ClusterConfig.StateBackend.values();

        assertEquals(3, backends.length);
        assertEquals(ClusterConfig.StateBackend.HAZELCAST, backends[0]);
        assertEquals(ClusterConfig.StateBackend.REDIS, backends[1]);
        assertEquals(ClusterConfig.StateBackend.MEMORY, backends[2]);
    }

    @Test
    void testStateBackendValueOf() {
        assertEquals(ClusterConfig.StateBackend.HAZELCAST,
            ClusterConfig.StateBackend.valueOf("HAZELCAST"));
        assertEquals(ClusterConfig.StateBackend.REDIS,
            ClusterConfig.StateBackend.valueOf("REDIS"));
        assertEquals(ClusterConfig.StateBackend.MEMORY,
            ClusterConfig.StateBackend.valueOf("MEMORY"));
    }

    @Test
    void testMultipleSeedNodes() {
        ClusterConfig config = ClusterConfig.builder()
            .addSeedNode("node1:5701")
            .addSeedNode("node2:5701")
            .addSeedNode("node3:5701")
            .build();

        assertEquals(3, config.getSeedNodes().size());
        assertEquals("node1:5701", config.getSeedNodes().get(0));
        assertEquals("node2:5701", config.getSeedNodes().get(1));
        assertEquals("node3:5701", config.getSeedNodes().get(2));
    }

    @Test
    void testBuilderReuse() {
        ClusterConfig.Builder builder = ClusterConfig.builder()
            .clusterName("test-cluster");

        ClusterConfig config1 = builder.build();
        ClusterConfig config2 = builder
            .bindPort(8080)
            .build();

        assertEquals("test-cluster", config1.getClusterName());
        assertEquals("test-cluster", config2.getClusterName());
        assertEquals(5701, config1.getBindPort());
        assertEquals(8080, config2.getBindPort());
    }
}
