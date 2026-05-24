package org.flossware.jplatform.cluster.consul;

import com.orbitz.consul.*;
import com.orbitz.consul.model.health.Service;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.model.session.SessionCreatedResponse;
import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConsulClusterManager.
 */
class ConsulClusterManagerTest {

    private Consul mockConsul;
    private AgentClient mockAgentClient;
    private SessionClient mockSessionClient;
    private KeyValueClient mockKvClient;
    private HealthClient mockHealthClient;
    private ConsulConfig consulConfig;
    private ClusterConfig clusterConfig;

    @BeforeEach
    void setUp() {
        mockConsul = mock(Consul.class);
        mockAgentClient = mock(AgentClient.class);
        mockSessionClient = mock(SessionClient.class);
        mockKvClient = mock(KeyValueClient.class);
        mockHealthClient = mock(HealthClient.class);

        when(mockConsul.agentClient()).thenReturn(mockAgentClient);
        when(mockConsul.sessionClient()).thenReturn(mockSessionClient);
        when(mockConsul.keyValueClient()).thenReturn(mockKvClient);
        when(mockConsul.healthClient()).thenReturn(mockHealthClient);

        consulConfig = ConsulConfig.builder()
            .consulHost("localhost")
            .consulPort(8500)
            .sessionTtl(10)
            .serviceName("test-cluster")
            .build();

        clusterConfig = ClusterConfig.builder()
            .clusterName("test-cluster")
            .bindAddress("192.168.1.10")
            .bindPort(5701)
            .build();
    }

    @Test
    void testConstructor() {
        ConsulClusterManager manager = new ConsulClusterManager(consulConfig);
        assertNotNull(manager);
        assertFalse(manager.isJoined());
        assertFalse(manager.isLeader());
    }

    @Test
    void testJoin_Success() throws Exception {
        // Mock session creation
        SessionCreatedResponse sessionResponse = mock(SessionCreatedResponse.class);
        when(sessionResponse.getId()).thenReturn("session-123");
        when(mockSessionClient.createSession(any())).thenReturn(sessionResponse);

        // Mock leader election
        when(mockKvClient.acquireLock(anyString(), anyString(), anyString())).thenReturn(true);

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        manager.join(clusterConfig);

        assertTrue(manager.isJoined());
        verify(mockAgentClient).register(any());
        verify(mockSessionClient).createSession(any());

        manager.close();
    }

    @Test
    void testJoin_AlreadyJoined() throws Exception {
        SessionCreatedResponse sessionResponse = mock(SessionCreatedResponse.class);
        when(sessionResponse.getId()).thenReturn("session-123");
        when(mockSessionClient.createSession(any())).thenReturn(sessionResponse);
        when(mockKvClient.acquireLock(anyString(), anyString(), anyString())).thenReturn(false);

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        manager.join(clusterConfig);

        assertThrows(IllegalStateException.class, () -> manager.join(clusterConfig));

        manager.close();
    }

    @Test
    void testJoin_Failure() {
        when(mockSessionClient.createSession(any())).thenThrow(new RuntimeException("Connection failed"));

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);

        assertThrows(ClusterJoinException.class, () -> manager.join(clusterConfig));
        assertFalse(manager.isJoined());
    }

    @Test
    void testLeave_Success() throws Exception {
        SessionCreatedResponse sessionResponse = mock(SessionCreatedResponse.class);
        when(sessionResponse.getId()).thenReturn("session-123");
        when(mockSessionClient.createSession(any())).thenReturn(sessionResponse);
        when(mockKvClient.acquireLock(anyString(), anyString(), anyString())).thenReturn(true);

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        manager.join(clusterConfig);
        assertTrue(manager.isJoined());

        manager.leave();

        assertFalse(manager.isJoined());
        assertFalse(manager.isLeader());
        verify(mockKvClient).releaseLock(anyString(), anyString());
        verify(mockSessionClient).destroySession("session-123");
        verify(mockAgentClient).deregister(anyString());
    }

    @Test
    void testLeave_NotJoined() throws Exception {
        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);

        // Should not throw
        manager.leave();

        assertFalse(manager.isJoined());
    }

    @Test
    void testGetNodes_NotJoined() {
        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);

        Set<ClusterNode> nodes = manager.getNodes();

        assertTrue(nodes.isEmpty());
    }

    @Test
    void testGetNodes_MultipleServices() throws Exception {
        SessionCreatedResponse sessionResponse = mock(SessionCreatedResponse.class);
        when(sessionResponse.getId()).thenReturn("session-123");
        when(mockSessionClient.createSession(any())).thenReturn(sessionResponse);
        when(mockKvClient.acquireLock(anyString(), anyString(), anyString())).thenReturn(false);

        // Mock health client response
        ServiceHealth service1 = mock(ServiceHealth.class);
        Service svc1 = mock(Service.class);
        when(service1.getService()).thenReturn(svc1);
        when(svc1.getId()).thenReturn("node-1");
        when(svc1.getAddress()).thenReturn("192.168.1.10");
        when(svc1.getPort()).thenReturn(5701);

        ServiceHealth service2 = mock(ServiceHealth.class);
        Service svc2 = mock(Service.class);
        when(service2.getService()).thenReturn(svc2);
        when(svc2.getId()).thenReturn("node-2");
        when(svc2.getAddress()).thenReturn("192.168.1.11");
        when(svc2.getPort()).thenReturn(5701);

        com.orbitz.consul.model.ConsulResponse<List<ServiceHealth>> response =
            mock(com.orbitz.consul.model.ConsulResponse.class);
        when(response.getResponse()).thenReturn(Arrays.asList(service1, service2));
        when(mockHealthClient.getHealthyServiceInstances("test-cluster")).thenReturn(response);

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        manager.join(clusterConfig);

        Set<ClusterNode> nodes = manager.getNodes();

        assertEquals(2, nodes.size());
        assertTrue(nodes.stream().anyMatch(n -> n.getNodeId().equals("node-1")));
        assertTrue(nodes.stream().anyMatch(n -> n.getNodeId().equals("node-2")));

        manager.close();
    }

    @Test
    void testGetLocalNode_NotJoined() {
        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);

        ClusterNode localNode = manager.getLocalNode();

        assertNull(localNode);
    }

    @Test
    void testGetLocalNode_Joined() throws Exception {
        SessionCreatedResponse sessionResponse = mock(SessionCreatedResponse.class);
        when(sessionResponse.getId()).thenReturn("session-123");
        when(mockSessionClient.createSession(any())).thenReturn(sessionResponse);
        when(mockKvClient.acquireLock(anyString(), anyString(), anyString())).thenReturn(false);

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        manager.join(clusterConfig);

        ClusterNode localNode = manager.getLocalNode();

        assertNotNull(localNode);
        assertEquals("192.168.1.10", localNode.getAddress());
        assertEquals(5701, localNode.getPort());
        assertEquals(ClusterNode.NodeState.ACTIVE, localNode.getState());

        manager.close();
    }

    @Test
    void testIsLeader_True() throws Exception {
        SessionCreatedResponse sessionResponse = mock(SessionCreatedResponse.class);
        when(sessionResponse.getId()).thenReturn("session-123");
        when(mockSessionClient.createSession(any())).thenReturn(sessionResponse);
        when(mockKvClient.acquireLock(anyString(), anyString(), anyString())).thenReturn(true);

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        manager.join(clusterConfig);

        assertTrue(manager.isLeader());

        manager.close();
    }

    @Test
    void testIsLeader_False() throws Exception {
        SessionCreatedResponse sessionResponse = mock(SessionCreatedResponse.class);
        when(sessionResponse.getId()).thenReturn("session-123");
        when(mockSessionClient.createSession(any())).thenReturn(sessionResponse);
        when(mockKvClient.acquireLock(anyString(), anyString(), anyString())).thenReturn(false);

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        manager.join(clusterConfig);

        assertFalse(manager.isLeader());

        manager.close();
    }

    @Test
    void testAddListener() {
        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        ClusterEventListener listener = mock(ClusterEventListener.class);

        manager.addListener(listener);
        manager.addListener(null); // Should not throw
    }

    @Test
    void testRemoveListener() {
        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        ClusterEventListener listener = mock(ClusterEventListener.class);

        manager.addListener(listener);
        manager.removeListener(listener);
        manager.removeListener(null); // Should not throw
    }

    @Test
    void testLeaderChangedNotification() throws Exception {
        SessionCreatedResponse sessionResponse = mock(SessionCreatedResponse.class);
        when(sessionResponse.getId()).thenReturn("session-123");
        when(mockSessionClient.createSession(any())).thenReturn(sessionResponse);
        when(mockKvClient.acquireLock(anyString(), anyString(), anyString())).thenReturn(true);

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        ClusterEventListener listener = mock(ClusterEventListener.class);
        manager.addListener(listener);

        manager.join(clusterConfig);

        // Give the scheduler time to run
        Thread.sleep(100);

        verify(listener, atLeastOnce()).onLeaderChanged(any(ClusterNode.class));

        manager.close();
    }

    @Test
    void testClose() throws Exception {
        SessionCreatedResponse sessionResponse = mock(SessionCreatedResponse.class);
        when(sessionResponse.getId()).thenReturn("session-123");
        when(mockSessionClient.createSession(any())).thenReturn(sessionResponse);
        when(mockKvClient.acquireLock(anyString(), anyString(), anyString())).thenReturn(true);

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);
        manager.join(clusterConfig);

        manager.close();

        assertFalse(manager.isJoined());
        verify(mockAgentClient).deregister(anyString());
    }

    @Test
    void testGetSessionId() throws Exception {
        SessionCreatedResponse sessionResponse = mock(SessionCreatedResponse.class);
        when(sessionResponse.getId()).thenReturn("session-123");
        when(mockSessionClient.createSession(any())).thenReturn(sessionResponse);
        when(mockKvClient.acquireLock(anyString(), anyString(), anyString())).thenReturn(false);

        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);

        assertNull(manager.getSessionId());

        manager.join(clusterConfig);

        assertEquals("session-123", manager.getSessionId());

        manager.close();
    }

    @Test
    void testGetConsulClient() {
        ConsulClusterManager manager = new ConsulClusterManager(consulConfig, mockConsul);

        assertSame(mockConsul, manager.getConsulClient());
    }
}
