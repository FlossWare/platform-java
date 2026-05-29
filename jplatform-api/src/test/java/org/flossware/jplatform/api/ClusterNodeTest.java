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
 * Unit tests for ClusterNode.
 */
class ClusterNodeTest {

    @Test
    void testConstructor_validParameters() {
        ClusterNode node = new ClusterNode(
                "node-1",
                "192.168.1.100",
                8080,
                ClusterNode.NodeState.ACTIVE,
                System.currentTimeMillis()
        );

        assertEquals("node-1", node.getNodeId());
        assertEquals("192.168.1.100", node.getAddress());
        assertEquals(8080, node.getPort());
        assertEquals(ClusterNode.NodeState.ACTIVE, node.getState());
        assertTrue(node.getLastHeartbeat() > 0);
    }

    @Test
    void testConstructor_nullNodeId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClusterNode(
                        null,
                        "192.168.1.100",
                        8080,
                        ClusterNode.NodeState.ACTIVE,
                        System.currentTimeMillis()
                ));

        assertTrue(exception.getMessage().contains("Node ID cannot be null or empty"));
    }

    @Test
    void testConstructor_emptyNodeId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClusterNode(
                        "",
                        "192.168.1.100",
                        8080,
                        ClusterNode.NodeState.ACTIVE,
                        System.currentTimeMillis()
                ));

        assertTrue(exception.getMessage().contains("Node ID cannot be null or empty"));
    }

    @Test
    void testConstructor_whitespaceNodeId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClusterNode(
                        "   ",
                        "192.168.1.100",
                        8080,
                        ClusterNode.NodeState.ACTIVE,
                        System.currentTimeMillis()
                ));

        assertTrue(exception.getMessage().contains("Node ID cannot be null or empty"));
    }

    @Test
    void testConstructor_nullAddress() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClusterNode(
                        "node-1",
                        null,
                        8080,
                        ClusterNode.NodeState.ACTIVE,
                        System.currentTimeMillis()
                ));

        assertTrue(exception.getMessage().contains("Address cannot be null or empty"));
    }

    @Test
    void testConstructor_emptyAddress() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClusterNode(
                        "node-1",
                        "",
                        8080,
                        ClusterNode.NodeState.ACTIVE,
                        System.currentTimeMillis()
                ));

        assertTrue(exception.getMessage().contains("Address cannot be null or empty"));
    }

    @Test
    void testConstructor_invalidPortTooLow() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClusterNode(
                        "node-1",
                        "192.168.1.100",
                        0,
                        ClusterNode.NodeState.ACTIVE,
                        System.currentTimeMillis()
                ));

        assertTrue(exception.getMessage().contains("Port must be between 1 and 65535"));
    }

    @Test
    void testConstructor_invalidPortTooHigh() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClusterNode(
                        "node-1",
                        "192.168.1.100",
                        65536,
                        ClusterNode.NodeState.ACTIVE,
                        System.currentTimeMillis()
                ));

        assertTrue(exception.getMessage().contains("Port must be between 1 and 65535"));
    }

    @Test
    void testConstructor_nullState() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new ClusterNode(
                        "node-1",
                        "192.168.1.100",
                        8080,
                        null,
                        System.currentTimeMillis()
                ));

        assertTrue(exception.getMessage().contains("Node state cannot be null"));
    }

    @Test
    void testConstructor_negativeLastHeartbeat() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ClusterNode(
                        "node-1",
                        "192.168.1.100",
                        8080,
                        ClusterNode.NodeState.ACTIVE,
                        -1
                ));

        assertTrue(exception.getMessage().contains("lastHeartbeat cannot be negative"));
    }

    @Test
    void testConstructor_zeroLastHeartbeat() {
        // Zero should be valid (represents epoch time)
        ClusterNode node = new ClusterNode(
                "node-1",
                "192.168.1.100",
                8080,
                ClusterNode.NodeState.ACTIVE,
                0
        );

        assertEquals(0, node.getLastHeartbeat());
    }

    @Test
    void testToString() {
        ClusterNode node = new ClusterNode(
                "node-1",
                "192.168.1.100",
                8080,
                ClusterNode.NodeState.ACTIVE,
                System.currentTimeMillis()
        );

        String str = node.toString();
        assertTrue(str.contains("node-1"));
        assertTrue(str.contains("192.168.1.100"));
        assertTrue(str.contains("8080"));
        assertTrue(str.contains("ACTIVE"));
    }

    @Test
    void testAllNodeStates() {
        long timestamp = System.currentTimeMillis();

        for (ClusterNode.NodeState state : ClusterNode.NodeState.values()) {
            ClusterNode node = new ClusterNode(
                    "node-1",
                    "192.168.1.100",
                    8080,
                    state,
                    timestamp
            );

            assertEquals(state, node.getState());
        }
    }

    @Test
    void testValidPortBoundaries() {
        // Test minimum valid port
        ClusterNode node1 = new ClusterNode(
                "node-1",
                "192.168.1.100",
                1,
                ClusterNode.NodeState.ACTIVE,
                System.currentTimeMillis()
        );
        assertEquals(1, node1.getPort());

        // Test maximum valid port
        ClusterNode node2 = new ClusterNode(
                "node-2",
                "192.168.1.101",
                65535,
                ClusterNode.NodeState.ACTIVE,
                System.currentTimeMillis()
        );
        assertEquals(65535, node2.getPort());
    }
}
