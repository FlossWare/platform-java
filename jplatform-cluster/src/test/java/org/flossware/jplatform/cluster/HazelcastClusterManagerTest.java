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

package org.flossware.jplatform.cluster;

import com.hazelcast.core.HazelcastInstance;
import org.flossware.jplatform.api.ClusterEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HazelcastClusterManager.
 * Tests basic operations without requiring a full Hazelcast cluster.
 */
class HazelcastClusterManagerTest {

    private HazelcastInstance mockHazelcast;

    @BeforeEach
    void setUp() {
        mockHazelcast = mock(HazelcastInstance.class);
    }

    @Test
    void testConstruction() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertNotNull(manager);
        assertFalse(manager.isJoined());
    }

    @Test
    void testPackageConstructor() {
        HazelcastClusterManager manager = new HazelcastClusterManager(mockHazelcast);
        assertNotNull(manager);
        assertFalse(manager.isJoined());
    }

    @Test
    void testIsJoinedInitially() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertFalse(manager.isJoined());
    }

    @Test
    void testIsLeaderInitially() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertFalse(manager.isLeader());
    }

    @Test
    void testGetLocalNodeNotJoined() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertNull(manager.getLocalNode());
    }

    @Test
    void testGetNodesNotJoined() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertTrue(manager.getNodes().isEmpty());
    }

    @Test
    void testAddListener() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        ClusterEventListener listener = mock(ClusterEventListener.class);
        assertDoesNotThrow(() -> manager.addListener(listener));
    }

    @Test
    void testAddNullListener() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertDoesNotThrow(() -> manager.addListener(null));
    }

    @Test
    void testRemoveListener() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        ClusterEventListener listener = mock(ClusterEventListener.class);
        assertDoesNotThrow(() -> manager.removeListener(listener));
    }

    @Test
    void testRemoveNullListener() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertDoesNotThrow(() -> manager.removeListener(null));
    }

    @Test
    void testRemoveListenerNotAdded() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        ClusterEventListener listener = mock(ClusterEventListener.class);
        assertDoesNotThrow(() -> manager.removeListener(listener));
    }

    @Test
    void testAddMultipleListeners() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        ClusterEventListener listener1 = mock(ClusterEventListener.class);
        ClusterEventListener listener2 = mock(ClusterEventListener.class);
        ClusterEventListener listener3 = mock(ClusterEventListener.class);

        assertDoesNotThrow(() -> {
            manager.addListener(listener1);
            manager.addListener(listener2);
            manager.addListener(listener3);
        });
    }

    @Test
    void testRemoveOneOfMultipleListeners() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        ClusterEventListener listener1 = mock(ClusterEventListener.class);
        ClusterEventListener listener2 = mock(ClusterEventListener.class);

        manager.addListener(listener1);
        manager.addListener(listener2);

        assertDoesNotThrow(() -> manager.removeListener(listener1));
    }

    @Test
    void testLeaveNotJoined() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertDoesNotThrow(() -> manager.leave());
    }

    @Test
    void testLeaveMultipleTimes() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertDoesNotThrow(() -> {
            manager.leave();
            manager.leave();
            manager.leave();
        });
    }

    @Test
    void testClose() throws Exception {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertDoesNotThrow(() -> manager.close());
    }

    @Test
    void testCloseMultipleTimes() throws Exception {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertDoesNotThrow(() -> {
            manager.close();
            manager.close();
        });
    }

    @Test
    void testGetHazelcastInstance() {
        HazelcastClusterManager manager = new HazelcastClusterManager(mockHazelcast);
        assertSame(mockHazelcast, manager.getHazelcastInstance());
    }

    @Test
    void testGetHazelcastInstanceNotInitialized() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertNull(manager.getHazelcastInstance());
    }

    @Test
    void testInitialStateAllFalse() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertFalse(manager.isJoined());
        assertFalse(manager.isLeader());
    }

    @Test
    void testMultipleInstances() {
        HazelcastClusterManager manager1 = new HazelcastClusterManager();
        HazelcastClusterManager manager2 = new HazelcastClusterManager();

        assertNotSame(manager1, manager2);
        assertFalse(manager1.isJoined());
        assertFalse(manager2.isJoined());
    }

    @Test
    void testAddSameListenerTwice() {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        ClusterEventListener listener = mock(ClusterEventListener.class);

        manager.addListener(listener);
        manager.addListener(listener);

        // Should be able to add same listener twice (CopyOnWriteArrayList allows duplicates)
        assertDoesNotThrow(() -> manager.removeListener(listener));
    }

    @Test
    void testCloseNotJoined() throws Exception {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        assertDoesNotThrow(() -> manager.close());
        assertFalse(manager.isJoined());
    }

    @Test
    void testLeaveAfterClose() throws Exception {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        manager.close();
        assertDoesNotThrow(() -> manager.leave());
    }

    @Test
    void testCloseAfterLeave() throws Exception {
        HazelcastClusterManager manager = new HazelcastClusterManager();
        manager.leave();
        assertDoesNotThrow(() -> manager.close());
    }
}
