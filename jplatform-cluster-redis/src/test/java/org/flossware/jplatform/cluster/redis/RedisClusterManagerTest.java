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

package org.flossware.jplatform.cluster.redis;

import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisClusterManagerTest {
    private RedisConfig config;
    private JedisPool mockPool;
    private Jedis mockJedis;

    @BeforeEach
    void setUp() {
        config = RedisConfig.builder().build();
        mockPool = mock(JedisPool.class);
        mockJedis = mock(Jedis.class);
        when(mockPool.getResource()).thenReturn(mockJedis);
    }

    @Test
    void testConstruction() {
        RedisClusterManager manager = new RedisClusterManager(config);
        assertNotNull(manager);
        assertFalse(manager.isJoined());
    }

    @Test
    void testConstructionWithPool() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        assertNotNull(manager);
        assertSame(mockPool, manager.getJedisPool());
    }

    @Test
    void testIsJoined() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        assertFalse(manager.isJoined());
    }

    @Test
    void testIsLeader() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        assertFalse(manager.isLeader());
    }

    @Test
    void testGetLocalNode_NotJoined() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        assertNull(manager.getLocalNode());
    }

    @Test
    void testGetNodes_NotJoined() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        assertTrue(manager.getNodes().isEmpty());
    }

    @Test
    void testAddListener() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterEventListener listener = mock(ClusterEventListener.class);
        assertDoesNotThrow(() -> manager.addListener(listener));
    }

    @Test
    void testRemoveListener() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterEventListener listener = mock(ClusterEventListener.class);
        assertDoesNotThrow(() -> manager.removeListener(listener));
    }

    @Test
    void testLeave_NotJoined() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        assertDoesNotThrow(() -> manager.leave());
    }

    @Test
    void testClose() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        assertDoesNotThrow(() -> manager.close());
    }

    @Test
    void testGetNodeId() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        assertNotNull(manager.getNodeId());
        assertFalse(manager.getNodeId().isEmpty());
    }

    @Test
    void testJoin() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        manager.join(clusterConfig);

        assertTrue(manager.isJoined());
        verify(mockJedis, atLeastOnce()).ping();
    }

    @Test
    void testJoin_AlreadyJoined() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        manager.join(clusterConfig);

        assertThrows(IllegalStateException.class, () -> manager.join(clusterConfig));
    }

    @Test
    void testLeave() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        manager.join(clusterConfig);
        manager.leave();

        assertFalse(manager.isJoined());
        assertFalse(manager.isLeader());
    }

    @Test
    void testConstructorNullConfig() {
        assertThrows(IllegalArgumentException.class, () ->
            new RedisClusterManager(null)
        );
    }

    @Test
    void testConstructorWithPoolNullConfig() {
        assertThrows(IllegalArgumentException.class, () ->
            new RedisClusterManager(null, mockPool)
        );
    }

    @Test
    void testGetLocalNode_AfterJoin() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("192.168.1.100")
            .bindPort(9000)
            .build();

        manager.join(clusterConfig);

        ClusterNode localNode = manager.getLocalNode();
        assertNotNull(localNode);
        assertEquals("192.168.1.100", localNode.getAddress());
        assertEquals(9000, localNode.getPort());
        assertEquals(ClusterNode.NodeState.ACTIVE, localNode.getState());
    }

    @Test
    void testGetNodes_AfterJoin() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");

        // Mock member data
        Map<String, String> members = new HashMap<>();
        members.put("node-1", "{\"timestamp\":123456,\"address\":\"192.168.1.1\",\"port\":8080}");
        members.put("node-2", "{\"timestamp\":123457,\"address\":\"192.168.1.2\",\"port\":8081}");
        when(mockJedis.hgetAll(anyString())).thenReturn(members);

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        manager.join(clusterConfig);

        Set<ClusterNode> nodes = manager.getNodes();
        assertEquals(2, nodes.size());

        // Verify node details
        boolean found1 = false, found2 = false;
        for (ClusterNode node : nodes) {
            if ("node-1".equals(node.getNodeId())) {
                assertEquals("192.168.1.1", node.getAddress());
                assertEquals(8080, node.getPort());
                found1 = true;
            } else if ("node-2".equals(node.getNodeId())) {
                assertEquals("192.168.1.2", node.getAddress());
                assertEquals(8081, node.getPort());
                found2 = true;
            }
        }
        assertTrue(found1 && found2, "Both nodes should be found");
    }

    @Test
    void testAddListenerNotifiesOnLeaderChange() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");
        when(mockJedis.set(anyString(), anyString(), any())).thenReturn("OK");

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        ClusterEventListener listener = mock(ClusterEventListener.class);
        manager.addListener(listener);

        manager.join(clusterConfig);

        // Give scheduler time to run
        Thread.sleep(200);

        // Should eventually become leader and notify
        verify(listener, timeout(2000).atLeastOnce()).onLeaderChanged(any(ClusterNode.class));
    }

    @Test
    void testAddNullListener() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        assertDoesNotThrow(() -> manager.addListener(null));
    }

    @Test
    void testRemoveNullListener() {
        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        assertDoesNotThrow(() -> manager.removeListener(null));
    }

    @Test
    void testRemoveListenerPreventsNotifications() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");
        when(mockJedis.set(anyString(), anyString(), any())).thenReturn("OK");

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        ClusterEventListener listener = mock(ClusterEventListener.class);
        manager.addListener(listener);
        manager.removeListener(listener);

        manager.join(clusterConfig);

        // Give scheduler time to run
        Thread.sleep(500);

        // Should NOT notify since listener was removed
        verify(listener, never()).onLeaderChanged(any(ClusterNode.class));
    }

    @Test
    void testJoinFailureThrowsException() {
        when(mockPool.getResource()).thenThrow(new RuntimeException("Connection failed"));

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        assertThrows(ClusterJoinException.class, () -> manager.join(clusterConfig));
        assertFalse(manager.isJoined());
    }

    @Test
    void testGetNodesHandlesMalformedJson() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");

        // Mock member data with malformed JSON
        Map<String, String> members = new HashMap<>();
        members.put("node-1", "{\"timestamp\":123456,\"address\":\"192.168.1.1\",\"port\":8080}");
        members.put("node-bad", "not-valid-json");
        when(mockJedis.hgetAll(anyString())).thenReturn(members);

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        manager.join(clusterConfig);

        Set<ClusterNode> nodes = manager.getNodes();
        // Should only include valid node
        assertEquals(1, nodes.size());
    }

    @Test
    void testGetNodesReturnsEmptyOnException() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");
        when(mockJedis.hgetAll(anyString())).thenThrow(new RuntimeException("Redis error"));

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        manager.join(clusterConfig);

        Set<ClusterNode> nodes = manager.getNodes();
        assertTrue(nodes.isEmpty());
    }

    @Test
    void testLeadershipAcquiredWhenSetnxSucceeds() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");
        when(mockJedis.set(anyString(), anyString(), any())).thenReturn("OK");

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        manager.join(clusterConfig);

        // Give scheduler time to run leadership election
        Thread.sleep(200);

        assertTrue(manager.isLeader(), "Should become leader when SETNX succeeds");
    }

    @Test
    void testLeadershipNotAcquiredWhenSetnxFails() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");
        when(mockJedis.set(anyString(), anyString(), any())).thenReturn(null);
        when(mockJedis.get(anyString())).thenReturn("other-node-id");

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        manager.join(clusterConfig);

        // Give scheduler time to run leadership election
        Thread.sleep(200);

        assertFalse(manager.isLeader(), "Should not become leader when another node holds key");
    }

    @Test
    void testMultipleLeaveCallsAreSafe() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        manager.join(clusterConfig);
        manager.leave();

        // Second leave should not throw
        assertDoesNotThrow(() -> manager.leave());
        assertFalse(manager.isJoined());
    }

    @Test
    void testCloseCallsLeave() throws Exception {
        when(mockJedis.ping()).thenReturn("PONG");

        RedisClusterManager manager = new RedisClusterManager(config, mockPool);
        ClusterConfig clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("localhost")
            .bindPort(8080)
            .build();

        manager.join(clusterConfig);
        manager.close();

        assertFalse(manager.isJoined());
    }
}
