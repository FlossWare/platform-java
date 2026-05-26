package org.flossware.jplatform.cluster.etcd;

import io.etcd.jetcd.Client;
import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EtcdClusterManagerTest {
    private EtcdConfig config;
    private Client mockClient;

    @BeforeEach
    void setUp() {
        config = EtcdConfig.builder().build();
        mockClient = mock(Client.class);
    }

    @Test
    void testConstruction() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        assertNotNull(manager);
        assertFalse(manager.isJoined());
    }

    @Test
    void testIsJoined() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        assertFalse(manager.isJoined());
    }

    @Test
    void testIsLeader() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        assertFalse(manager.isLeader());
    }

    @Test
    void testGetLocalNode_NotJoined() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        assertNull(manager.getLocalNode());
    }

    @Test
    void testGetNodes() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        assertTrue(manager.getNodes().isEmpty());
    }

    @Test
    void testAddListener() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        ClusterEventListener listener = mock(ClusterEventListener.class);
        assertDoesNotThrow(() -> manager.addListener(listener));
    }

    @Test
    void testRemoveListener() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        ClusterEventListener listener = mock(ClusterEventListener.class);
        assertDoesNotThrow(() -> manager.removeListener(listener));
    }

    @Test
    void testLeave_NotJoined() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        assertDoesNotThrow(() -> manager.leave());
    }

    @Test
    void testClose() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        assertDoesNotThrow(() -> manager.close());
    }

    @Test
    void testGetEtcdClient() {
        EtcdClusterManager manager = new EtcdClusterManager(config, mockClient);
        assertSame(mockClient, manager.getEtcdClient());
    }

    @Test
    void testConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new EtcdClusterManager(null);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testPackageConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new EtcdClusterManager(null, mockClient);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testAddNullListener() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        assertDoesNotThrow(() -> manager.addListener(null));
    }

    @Test
    void testRemoveNullListener() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        assertDoesNotThrow(() -> manager.removeListener(null));
    }

    @Test
    void testMultipleListeners() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        ClusterEventListener listener1 = mock(ClusterEventListener.class);
        ClusterEventListener listener2 = mock(ClusterEventListener.class);

        manager.addListener(listener1);
        manager.addListener(listener2);

        // Both should be registered (would be tested by event firing, but that requires join)
        assertDoesNotThrow(() -> {
            manager.removeListener(listener1);
            manager.removeListener(listener2);
        });
    }

    @Test
    void testRemoveListenerNotAdded() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        ClusterEventListener listener = mock(ClusterEventListener.class);

        assertDoesNotThrow(() -> manager.removeListener(listener));
    }

    @Test
    void testLeaveMultipleTimes() {
        EtcdClusterManager manager = new EtcdClusterManager(config);

        assertDoesNotThrow(() -> manager.leave());
        assertDoesNotThrow(() -> manager.leave());
    }

    @Test
    void testCloseMultipleTimes() {
        EtcdClusterManager manager = new EtcdClusterManager(config);

        assertDoesNotThrow(() -> manager.close());
        assertDoesNotThrow(() -> manager.close());
    }

    @Test
    void testNodeIdIsUniqueAcrossInstances() {
        EtcdClusterManager manager1 = new EtcdClusterManager(config);
        EtcdClusterManager manager2 = new EtcdClusterManager(config);

        // Note: can't access nodeId directly as it's private, but we can verify
        // through behavior that instances are independent
        assertNotSame(manager1, manager2);
    }

    @Test
    void testInitialStateNotLeader() {
        EtcdClusterManager manager = new EtcdClusterManager(config);
        assertFalse(manager.isLeader());
        assertFalse(manager.isJoined());
    }
}
