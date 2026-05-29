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

package org.flossware.jplatform.cluster.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZookeeperClusterManagerTest {
    private ZookeeperConfig config;
    private CuratorFramework mockClient;

    @BeforeEach
    void setUp() {
        config = ZookeeperConfig.builder().build();
        mockClient = mock(CuratorFramework.class);
    }

    @Test
    void testConstruction() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config);
        assertNotNull(manager);
        assertFalse(manager.isJoined());
    }

    @Test
    void testConstructionWithClient() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertNotNull(manager);
        assertSame(mockClient, manager.getClient());
    }

    @Test
    void testConstructorNullConfig() {
        assertThrows(IllegalArgumentException.class, () ->
            new ZookeeperClusterManager(null)
        );
    }

    @Test
    void testConstructorWithClientNullConfig() {
        assertThrows(IllegalArgumentException.class, () ->
            new ZookeeperClusterManager(null, mockClient)
        );
    }

    @Test
    void testIsJoined() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertFalse(manager.isJoined());
    }

    @Test
    void testIsLeader() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertFalse(manager.isLeader());
    }

    @Test
    void testGetLocalNode_NotJoined() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertNull(manager.getLocalNode());
    }

    @Test
    void testGetNodes_NotJoined() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertTrue(manager.getNodes().isEmpty());
    }

    @Test
    void testAddListener() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        ClusterEventListener listener = mock(ClusterEventListener.class);
        assertDoesNotThrow(() -> manager.addListener(listener));
    }

    @Test
    void testAddNullListener() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertDoesNotThrow(() -> manager.addListener(null));
    }

    @Test
    void testRemoveListener() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        ClusterEventListener listener = mock(ClusterEventListener.class);
        assertDoesNotThrow(() -> manager.removeListener(listener));
    }

    @Test
    void testRemoveNullListener() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertDoesNotThrow(() -> manager.removeListener(null));
    }

    @Test
    void testLeave_NotJoined() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertDoesNotThrow(() -> manager.leave());
        assertFalse(manager.isJoined());
    }

    @Test
    void testClose() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertDoesNotThrow(() -> manager.close());
    }

    @Test
    void testCloseWhenNotJoined() throws Exception {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertDoesNotThrow(() -> manager.close());
        assertFalse(manager.isJoined());
    }

    @Test
    void testMultipleCloseCallsAreSafe() throws Exception {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        manager.close();
        // Second close should be safe
        assertDoesNotThrow(() -> manager.close());
        assertFalse(manager.isJoined());
    }

    @Test
    void testGetNodeId() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertNotNull(manager.getNodeId());
        assertFalse(manager.getNodeId().isEmpty());
    }

    @Test
    void testNodeIdIsUnique() {
        ZookeeperClusterManager manager1 = new ZookeeperClusterManager(config, mockClient);
        ZookeeperClusterManager manager2 = new ZookeeperClusterManager(config, mockClient);

        assertNotEquals(manager1.getNodeId(), manager2.getNodeId());
    }

    @Test
    void testNodeIdIsUUID() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        String nodeId = manager.getNodeId();

        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertTrue(nodeId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void testGetClient() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertSame(mockClient, manager.getClient());
    }

    @Test
    void testIsLeaderWhenNotJoinedReturnsFalse() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertFalse(manager.isLeader());
    }

    @Test
    void testLeaveWhenNotJoinedIsNoOp() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        assertDoesNotThrow(() -> manager.leave());
        assertFalse(manager.isJoined());
    }

    @Test
    void testMultipleLeaveCallsAreSafe() {
        ZookeeperClusterManager manager = new ZookeeperClusterManager(config, mockClient);
        manager.leave();
        // Second leave should be safe
        assertDoesNotThrow(() -> manager.leave());
        assertFalse(manager.isJoined());
    }
}
