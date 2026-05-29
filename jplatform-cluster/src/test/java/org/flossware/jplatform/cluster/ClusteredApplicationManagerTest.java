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

import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ClusteredApplicationManager.
 * Tests cluster-aware deploy/start/stop/undeploy operations and failure handling.
 */
class ClusteredApplicationManagerTest {

    private ClusteredApplicationManager manager;
    private MessageBus mockMessageBus;
    private ServiceRegistry mockServiceRegistry;
    private ClusterManager mockClusterManager;
    private ClusterStateStore mockStateStore;
    private ApplicationScheduler mockScheduler;

    @BeforeEach
    void setUp() {
        mockMessageBus = mock(MessageBus.class);
        mockServiceRegistry = mock(ServiceRegistry.class);
        mockClusterManager = mock(ClusterManager.class);
        mockStateStore = mock(ClusterStateStore.class);
        mockScheduler = mock(ApplicationScheduler.class);
    }

    @Test
    @DisplayName("Should create manager without clustering")
    void testCreateStandaloneManager() {
        // When
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry, null, null);

        // Then
        assertNotNull(manager);
        assertNull(manager.getClusterManager());
        assertNull(manager.getStateStore());
        assertNull(manager.getScheduler());
    }

    @Test
    @DisplayName("Should create manager with clustering")
    void testCreateClusteredManager() {
        // Given
        HazelcastClusterManager hzClusterManager = mock(HazelcastClusterManager.class);
        when(hzClusterManager.getHazelcastInstance()).thenReturn(mock(com.hazelcast.core.HazelcastInstance.class));

        // When
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry,
                hzClusterManager, mockStateStore);

        // Then
        assertNotNull(manager);
        assertNotNull(manager.getClusterManager());
        assertNotNull(manager.getStateStore());
        assertNotNull(manager.getScheduler());
    }

    @Test
    @DisplayName("Should deploy application in standalone mode")
    void testDeployStandalone() throws Exception {
        // Given
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry, null, null);

        ApplicationDescriptor descriptor = createTestDescriptor("test-app");

        // When
        manager.deploy(descriptor);

        // Then
        Map<String, ApplicationState> apps = manager.listApplications();
        assertTrue(apps.containsKey("test-app"));
    }

    @Test
    @DisplayName("Should deploy application in cluster mode as leader")
    void testDeployClusteredAsLeader() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockScheduler.assignApplication(anyString())).thenReturn("local-node");
        when(mockScheduler.isAssignedToLocalNode(anyString())).thenReturn(true);

        ApplicationDescriptor descriptor = createTestDescriptor("test-app");

        // When
        manager.deploy(descriptor);

        // Then
        verify(mockStateStore).putApplicationDescriptor("test-app", descriptor);
        verify(mockStateStore).putApplicationState("test-app", ApplicationState.DEPLOYED);
        verify(mockScheduler).assignApplication("test-app");
    }

    @Test
    @DisplayName("Should deploy application in cluster mode as follower assigned to local node")
    void testDeployClusteredAsFollowerLocal() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockClusterManager.isLeader()).thenReturn(false);
        when(mockScheduler.isAssignedToLocalNode(anyString())).thenReturn(true);

        ApplicationDescriptor descriptor = createTestDescriptor("test-app");

        // When
        manager.deploy(descriptor);

        // Then
        verify(mockStateStore).putApplicationDescriptor("test-app", descriptor);
        verify(mockStateStore).putApplicationState("test-app", ApplicationState.DEPLOYED);
        verify(mockScheduler, never()).assignApplication(anyString());
    }

    @Test
    @DisplayName("Should skip local deployment when assigned to different node")
    void testDeployClusteredAssignedToOtherNode() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockScheduler.assignApplication(anyString())).thenReturn("remote-node");
        when(mockScheduler.isAssignedToLocalNode(anyString())).thenReturn(false);

        ApplicationDescriptor descriptor = createTestDescriptor("test-app");

        // When
        manager.deploy(descriptor);

        // Then
        verify(mockStateStore).putApplicationDescriptor("test-app", descriptor);
        verify(mockScheduler).assignApplication("test-app");
    }

    @Test
    @DisplayName("Should start application in standalone mode")
    void testStartStandalone() throws Exception {
        // Given
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry, null, null);

        ApplicationDescriptor descriptor = createTestDescriptor("test-app");
        manager.deploy(descriptor);

        // When
        manager.start("test-app");

        // Then
        Map<String, ApplicationState> apps = manager.listApplications();
        assertEquals(ApplicationState.RUNNING, apps.get("test-app"));
    }

    @Test
    @DisplayName("Should start application in cluster mode when assigned to local node")
    void testStartClusteredLocalNode() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockScheduler.isAssignedToLocalNode("test-app")).thenReturn(true);

        ApplicationDescriptor descriptor = createTestDescriptor("test-app");
        manager.deploy(descriptor);

        // When
        manager.start("test-app");

        // Then
        verify(mockStateStore).putApplicationState("test-app", ApplicationState.RUNNING);
    }

    @Test
    @DisplayName("Should throw exception when starting application not assigned to local node")
    void testStartClusteredRemoteNode() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockScheduler.isAssignedToLocalNode("test-app")).thenReturn(false);

        // When/Then
        assertThrows(IllegalStateException.class, () -> manager.start("test-app"));
    }

    @Test
    @DisplayName("Should stop application in standalone mode")
    void testStopStandalone() throws Exception {
        // Given
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry, null, null);

        ApplicationDescriptor descriptor = createTestDescriptor("test-app");
        manager.deploy(descriptor);
        manager.start("test-app");

        // When
        manager.stop("test-app");

        // Then
        Map<String, ApplicationState> apps = manager.listApplications();
        assertEquals(ApplicationState.STOPPED, apps.get("test-app"));
    }

    @Test
    @DisplayName("Should stop application in cluster mode when assigned to local node")
    void testStopClusteredLocalNode() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockScheduler.isAssignedToLocalNode("test-app")).thenReturn(true);

        ApplicationDescriptor descriptor = createTestDescriptor("test-app");
        manager.deploy(descriptor);
        manager.start("test-app");

        // When
        manager.stop("test-app");

        // Then
        verify(mockStateStore).putApplicationState("test-app", ApplicationState.STOPPED);
    }

    @Test
    @DisplayName("Should throw exception when stopping application not assigned to local node")
    void testStopClusteredRemoteNode() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockScheduler.isAssignedToLocalNode("test-app")).thenReturn(false);

        // When/Then
        assertThrows(IllegalStateException.class, () -> manager.stop("test-app"));
    }

    @Test
    @DisplayName("Should undeploy application in standalone mode")
    void testUndeployStandalone() throws Exception {
        // Given
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry, null, null);

        ApplicationDescriptor descriptor = createTestDescriptor("test-app");
        manager.deploy(descriptor);

        // When
        manager.undeploy("test-app");

        // Then
        Map<String, ApplicationState> apps = manager.listApplications();
        assertFalse(apps.containsKey("test-app"));
    }

    @Test
    @DisplayName("Should undeploy application in cluster mode as leader")
    void testUndeployClusteredAsLeader() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockScheduler.isAssignedToLocalNode("test-app")).thenReturn(true);

        ApplicationDescriptor descriptor = createTestDescriptor("test-app");
        manager.deploy(descriptor);

        // When
        manager.undeploy("test-app");

        // Then
        verify(mockScheduler).unassignApplication("test-app");
        verify(mockStateStore).putApplicationState("test-app", ApplicationState.UNDEPLOYED);
    }

    @Test
    @DisplayName("Should undeploy only from cluster state when assigned to remote node")
    void testUndeployClusteredRemoteNode() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockScheduler.isAssignedToLocalNode("test-app")).thenReturn(false);

        // When
        manager.undeploy("test-app");

        // Then
        verify(mockScheduler).unassignApplication("test-app");
        verify(mockStateStore).putApplicationState("test-app", ApplicationState.UNDEPLOYED);
    }

    @Test
    @DisplayName("Should list local applications in standalone mode")
    void testListApplicationsStandalone() throws Exception {
        // Given
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry, null, null);

        manager.deploy(createTestDescriptor("app1"));
        manager.deploy(createTestDescriptor("app2"));

        // When
        Map<String, ApplicationState> apps = manager.listApplications();

        // Then
        assertEquals(2, apps.size());
        assertTrue(apps.containsKey("app1"));
        assertTrue(apps.containsKey("app2"));
    }

    @Test
    @DisplayName("Should list cluster-wide applications in cluster mode")
    void testListApplicationsClustered() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);

        Map<String, ApplicationState> clusterApps = new HashMap<>();
        clusterApps.put("app1", ApplicationState.RUNNING);
        clusterApps.put("app2", ApplicationState.STOPPED);
        clusterApps.put("app3", ApplicationState.DEPLOYED);

        when(mockStateStore.getAllApplicationStates()).thenReturn(clusterApps);

        // When
        Map<String, ApplicationState> apps = manager.listApplications();

        // Then
        assertEquals(3, apps.size());
        verify(mockStateStore).getAllApplicationStates();
    }

    @Test
    @DisplayName("Should handle node joined event")
    void testNodeJoinedEvent() throws Exception {
        // Given
        setupClusteredManager();

        ClusterEventListener listener = captureClusterListener();

        ClusterNode newNode = new ClusterNode("node-2", "host-2", 5702,
                ClusterNode.NodeState.ACTIVE, System.currentTimeMillis());

        // When
        listener.onNodeJoined(newNode);

        // Then - should log but not throw exception
        assertDoesNotThrow(() -> listener.onNodeJoined(newNode));
    }

    @Test
    @DisplayName("Should handle node left event as leader")
    void testNodeLeftEventAsLeader() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockScheduler.reassignFromFailedNode(anyString())).thenReturn(2);

        ClusterEventListener listener = captureClusterListener();

        ClusterNode leftNode = new ClusterNode("failed-node", "host-2", 5702,
                ClusterNode.NodeState.DEAD, System.currentTimeMillis());

        // When
        listener.onNodeLeft(leftNode);

        // Then
        verify(mockScheduler).reassignFromFailedNode("failed-node");
    }

    @Test
    @DisplayName("Should handle node left event as follower")
    void testNodeLeftEventAsFollower() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isLeader()).thenReturn(false);

        ClusterEventListener listener = captureClusterListener();

        ClusterNode leftNode = new ClusterNode("failed-node", "host-2", 5702,
                ClusterNode.NodeState.DEAD, System.currentTimeMillis());

        // When
        listener.onNodeLeft(leftNode);

        // Then
        verify(mockScheduler, never()).reassignFromFailedNode(anyString());
    }

    @Test
    @DisplayName("Should handle leader changed event")
    void testLeaderChangedEvent() throws Exception {
        // Given
        setupClusteredManager();

        ClusterEventListener listener = captureClusterListener();

        ClusterNode newLeader = new ClusterNode("node-2", "host-2", 5702,
                ClusterNode.NodeState.ACTIVE, System.currentTimeMillis());

        // When
        listener.onLeaderChanged(newLeader);

        // Then - should log but not throw exception
        assertDoesNotThrow(() -> listener.onLeaderChanged(newLeader));
    }

    @Test
    @DisplayName("Should handle reassignment failure gracefully")
    void testReassignmentFailure() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockScheduler.reassignFromFailedNode(anyString()))
                .thenThrow(new RuntimeException("Reassignment failed"));

        ClusterEventListener listener = captureClusterListener();

        ClusterNode leftNode = new ClusterNode("failed-node", "host-2", 5702,
                ClusterNode.NodeState.DEAD, System.currentTimeMillis());

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> listener.onNodeLeft(leftNode));
    }

    @Test
    @DisplayName("Should support full application lifecycle in standalone mode")
    void testFullLifecycleStandalone() throws Exception {
        // Given
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry, null, null);

        ApplicationDescriptor descriptor = createTestDescriptor("lifecycle-app");

        // When/Then
        manager.deploy(descriptor);
        assertEquals(ApplicationState.DEPLOYED, manager.listApplications().get("lifecycle-app"));

        manager.start("lifecycle-app");
        assertEquals(ApplicationState.RUNNING, manager.listApplications().get("lifecycle-app"));

        manager.stop("lifecycle-app");
        assertEquals(ApplicationState.STOPPED, manager.listApplications().get("lifecycle-app"));

        manager.undeploy("lifecycle-app");
        assertFalse(manager.listApplications().containsKey("lifecycle-app"));
    }

    @Test
    @DisplayName("Should support full application lifecycle in cluster mode")
    void testFullLifecycleClustered() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockScheduler.assignApplication(anyString())).thenReturn("local-node");
        when(mockScheduler.isAssignedToLocalNode(anyString())).thenReturn(true);

        ApplicationDescriptor descriptor = createTestDescriptor("lifecycle-app");

        // When/Then
        manager.deploy(descriptor);
        verify(mockStateStore).putApplicationState("lifecycle-app", ApplicationState.DEPLOYED);

        manager.start("lifecycle-app");
        verify(mockStateStore).putApplicationState("lifecycle-app", ApplicationState.RUNNING);

        manager.stop("lifecycle-app");
        verify(mockStateStore).putApplicationState("lifecycle-app", ApplicationState.STOPPED);

        manager.undeploy("lifecycle-app");
        verify(mockStateStore).putApplicationState("lifecycle-app", ApplicationState.UNDEPLOYED);
        verify(mockScheduler).unassignApplication("lifecycle-app");
    }

    @Test
    @DisplayName("Should handle multiple applications in cluster")
    void testMultipleApplicationsClustered() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockScheduler.assignApplication(anyString())).thenReturn("local-node");
        when(mockScheduler.isAssignedToLocalNode(anyString())).thenReturn(true);

        // When
        manager.deploy(createTestDescriptor("app1"));
        manager.deploy(createTestDescriptor("app2"));
        manager.deploy(createTestDescriptor("app3"));

        // Then
        verify(mockStateStore, times(3)).putApplicationDescriptor(anyString(), any());
        verify(mockScheduler, times(3)).assignApplication(anyString());
    }

    @Test
    @DisplayName("Should return correct cluster manager")
    void testGetClusterManager() {
        // Given
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry,
                mockClusterManager, mockStateStore);

        // When
        ClusterManager cm = manager.getClusterManager();

        // Then
        assertEquals(mockClusterManager, cm);
    }

    @Test
    @DisplayName("Should return correct state store")
    void testGetStateStore() {
        // Given
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry,
                mockClusterManager, mockStateStore);

        // When
        ClusterStateStore store = manager.getStateStore();

        // Then
        assertEquals(mockStateStore, store);
    }

    @Test
    @DisplayName("Should work with null message bus and service registry")
    void testNullDependencies() {
        // When
        manager = new ClusteredApplicationManager(null, null, null, null);

        // Then
        assertNotNull(manager);
    }

    @Test
    @DisplayName("Should handle deployment errors in cluster mode")
    void testDeploymentErrorClustered() throws Exception {
        // Given
        setupClusteredManager();

        when(mockClusterManager.isJoined()).thenReturn(true);
        when(mockClusterManager.isLeader()).thenReturn(true);
        doThrow(new RuntimeException("State store error"))
                .when(mockStateStore).putApplicationDescriptor(anyString(), any());

        ApplicationDescriptor descriptor = createTestDescriptor("error-app");

        // When/Then - deployment wraps errors in Exception after rollback
        Exception ex = assertThrows(Exception.class, () -> manager.deploy(descriptor));
        assertTrue(ex.getMessage().contains("Deployment failed and rollback completed"));
        assertTrue(ex.getCause() instanceof RuntimeException);
    }

    /**
     * Helper method to set up a fully mocked clustered manager.
     */
    private void setupClusteredManager() {
        HazelcastClusterManager hzClusterManager = mock(HazelcastClusterManager.class);
        com.hazelcast.core.HazelcastInstance mockHazelcast = mock(com.hazelcast.core.HazelcastInstance.class);

        when(hzClusterManager.getHazelcastInstance()).thenReturn(mockHazelcast);
        when(mockHazelcast.getMap(anyString())).thenReturn(mock(com.hazelcast.map.IMap.class));

        // Inject the mock scheduler using reflection
        manager = new ClusteredApplicationManager(mockMessageBus, mockServiceRegistry,
                mockClusterManager, mockStateStore);

        try {
            java.lang.reflect.Field schedulerField =
                    ClusteredApplicationManager.class.getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            schedulerField.set(manager, mockScheduler);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock scheduler", e);
        }
    }

    /**
     * Helper method to capture the cluster event listener.
     */
    private ClusterEventListener captureClusterListener() {
        try {
            org.mockito.ArgumentCaptor<ClusterEventListener> listenerCaptor =
                    org.mockito.ArgumentCaptor.forClass(ClusterEventListener.class);
            verify(mockClusterManager).addListener(listenerCaptor.capture());
            return listenerCaptor.getValue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to capture listener", e);
        }
    }

    /**
     * Helper method to create a test application descriptor.
     */
    private ApplicationDescriptor createTestDescriptor(String appId) {
        return ApplicationDescriptor.builder()
                .applicationId(appId)
                .name(appId + "-name")
                .version("1.0.0")
                .mainClass("org.flossware.jplatform.cluster.TestApp")
                .addClasspathEntry(java.net.URI.create("file:///test/app.jar"))
                .build();
    }
}
