package org.flossware.jplatform.cluster.redis;

import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import static org.junit.jupiter.api.Assertions.*;
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
}
