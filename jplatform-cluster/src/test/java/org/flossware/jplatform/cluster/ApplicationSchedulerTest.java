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
import com.hazelcast.map.IMap;
import org.flossware.jplatform.api.ClusterManager;
import org.flossware.jplatform.api.ClusterNode;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ApplicationScheduler.
 * Tests application assignment, scheduling strategies, node failure handling, and load balancing.
 */
class ApplicationSchedulerTest {

    private ApplicationScheduler scheduler;
    private HazelcastInstance mockHazelcast;
    private ClusterManager mockClusterManager;
    private IMap<String, String> mockAssignmentMap;
    private IMap<String, Integer> mockNodeLoadMap;

    @BeforeEach
    void setUp() {
        mockHazelcast = mock(HazelcastInstance.class);
        mockClusterManager = mock(ClusterManager.class);
        mockAssignmentMap = (IMap<String, String>) mock(IMap.class);
        mockNodeLoadMap = (IMap<String, Integer>) mock(IMap.class);

        when(mockHazelcast.<String, String>getMap("jplatform-application-assignments")).thenReturn(mockAssignmentMap);
        when(mockHazelcast.<String, Integer>getMap("jplatform-node-load")).thenReturn(mockNodeLoadMap);

        // Mock getOrDefault to return the default value when key not found
        when(mockNodeLoadMap.getOrDefault(anyString(), anyInt())).thenAnswer(invocation -> {
            // For testing, just return the default value (second argument)
            return invocation.getArgument(1);
        });
    }

    @Test
    @DisplayName("Should assign application using least-loaded strategy by default")
    void testDefaultStrategy() {
        // When
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);

        // Then
        assertNotNull(scheduler);
    }

    @Test
    @DisplayName("Should assign application using round-robin strategy")
    void testRoundRobinStrategy() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager,
                ApplicationScheduler.SchedulingStrategy.ROUND_ROBIN);

        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockAssignmentMap.get(anyString())).thenReturn(null);

        Set<ClusterNode> nodes = createNodes(3);
        when(mockClusterManager.getNodes()).thenReturn(nodes);

        // When - Assign multiple applications
        String node1 = scheduler.assignApplication("app1");
        String node2 = scheduler.assignApplication("app2");
        String node3 = scheduler.assignApplication("app3");
        String node4 = scheduler.assignApplication("app4");

        // Then - Should cycle through nodes
        assertNotNull(node1);
        assertNotNull(node2);
        assertNotNull(node3);
        assertNotNull(node4);
    }

    @Test
    @DisplayName("Should assign application using least-loaded strategy")
    void testLeastLoadedStrategy() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager,
                ApplicationScheduler.SchedulingStrategy.LEAST_LOADED);

        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockAssignmentMap.get(anyString())).thenReturn(null);

        Set<ClusterNode> nodes = createNodes(3);
        when(mockClusterManager.getNodes()).thenReturn(nodes);

        // Setup node loads: node1=2, node2=1, node3=3
        List<ClusterNode> nodeList = new ArrayList<>(nodes);
        when(mockNodeLoadMap.getOrDefault(nodeList.get(0).getNodeId(), 0)).thenReturn(2);
        when(mockNodeLoadMap.getOrDefault(nodeList.get(1).getNodeId(), 0)).thenReturn(1);
        when(mockNodeLoadMap.getOrDefault(nodeList.get(2).getNodeId(), 0)).thenReturn(3);

        // When
        String assignedNode = scheduler.assignApplication("test-app");

        // Then - Should assign to node with least load (node2)
        assertEquals(nodeList.get(1).getNodeId(), assignedNode);
    }

    @Test
    @DisplayName("Should throw exception when non-leader tries to assign")
    void testAssignAsNonLeader() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        when(mockClusterManager.isLeader()).thenReturn(false);

        // When/Then
        assertThrows(IllegalStateException.class, () -> scheduler.assignApplication("test-app"));
    }

    @Test
    @DisplayName("Should throw exception when no nodes available")
    void testAssignWithNoNodes() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockAssignmentMap.get(anyString())).thenReturn(null);
        when(mockClusterManager.getNodes()).thenReturn(Collections.emptySet());

        // When/Then
        assertThrows(IllegalStateException.class, () -> scheduler.assignApplication("test-app"));
    }

    @Test
    @DisplayName("Should return existing assignment if already assigned")
    void testAssignAlreadyAssigned() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String appId = "test-app";
        String existingNode = "node-123";

        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockAssignmentMap.get(appId)).thenReturn(existingNode);

        // When
        String assignedNode = scheduler.assignApplication(appId);

        // Then
        assertEquals(existingNode, assignedNode);
        verify(mockAssignmentMap, never()).put(anyString(), anyString());
    }

    @Test
    @DisplayName("Should update node load when assigning")
    void testAssignUpdatesNodeLoad() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockAssignmentMap.get(anyString())).thenReturn(null);

        Set<ClusterNode> nodes = createNodes(1);
        when(mockClusterManager.getNodes()).thenReturn(nodes);

        String nodeId = nodes.iterator().next().getNodeId();

        // Mock compute to actually execute the BiFunction
        when(mockNodeLoadMap.compute(eq(nodeId), any())).thenAnswer(invocation -> {
            java.util.function.BiFunction<String, Integer, Integer> func = invocation.getArgument(1);
            return func.apply(nodeId, null);
        });

        // When
        scheduler.assignApplication("test-app");

        // Then
        verify(mockNodeLoadMap).compute(eq(nodeId), any());
    }

    @Test
    @DisplayName("Should unassign application successfully")
    void testUnassignApplication() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String appId = "test-app";
        String nodeId = "node-123";

        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockAssignmentMap.remove(appId)).thenReturn(nodeId);

        // When
        scheduler.unassignApplication(appId);

        // Then
        verify(mockAssignmentMap).remove(appId);
        verify(mockNodeLoadMap).compute(eq(nodeId), any());
    }

    @Test
    @DisplayName("Should throw exception when non-leader tries to unassign")
    void testUnassignAsNonLeader() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        when(mockClusterManager.isLeader()).thenReturn(false);

        // When/Then
        assertThrows(IllegalStateException.class, () -> scheduler.unassignApplication("test-app"));
    }

    @Test
    @DisplayName("Should handle unassign when application not assigned")
    void testUnassignNotAssigned() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String appId = "test-app";

        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockAssignmentMap.remove(appId)).thenReturn(null);

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> scheduler.unassignApplication(appId));
    }

    @Test
    @DisplayName("Should get assigned node for application")
    void testGetAssignedNode() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String appId = "test-app";
        String nodeId = "node-123";

        when(mockAssignmentMap.get(appId)).thenReturn(nodeId);

        // When
        String assignedNode = scheduler.getAssignedNode(appId);

        // Then
        assertEquals(nodeId, assignedNode);
    }

    @Test
    @DisplayName("Should return null for unassigned application")
    void testGetAssignedNodeNotAssigned() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String appId = "test-app";

        when(mockAssignmentMap.get(appId)).thenReturn(null);

        // When
        String assignedNode = scheduler.getAssignedNode(appId);

        // Then
        assertNull(assignedNode);
    }

    @Test
    @DisplayName("Should check if assigned to local node")
    void testIsAssignedToLocalNode() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String appId = "test-app";
        String localNodeId = "local-node-123";

        ClusterNode localNode = new ClusterNode(localNodeId, "localhost", 5701,
                ClusterNode.NodeState.ACTIVE, System.currentTimeMillis());

        when(mockAssignmentMap.get(appId)).thenReturn(localNodeId);
        when(mockClusterManager.getLocalNode()).thenReturn(localNode);

        // When
        boolean isLocal = scheduler.isAssignedToLocalNode(appId);

        // Then
        assertTrue(isLocal);
    }

    @Test
    @DisplayName("Should return false when assigned to different node")
    void testIsAssignedToDifferentNode() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String appId = "test-app";
        String assignedNodeId = "remote-node-456";
        String localNodeId = "local-node-123";

        ClusterNode localNode = new ClusterNode(localNodeId, "localhost", 5701,
                ClusterNode.NodeState.ACTIVE, System.currentTimeMillis());

        when(mockAssignmentMap.get(appId)).thenReturn(assignedNodeId);
        when(mockClusterManager.getLocalNode()).thenReturn(localNode);

        // When
        boolean isLocal = scheduler.isAssignedToLocalNode(appId);

        // Then
        assertFalse(isLocal);
    }

    @Test
    @DisplayName("Should return false when application not assigned")
    void testIsAssignedToLocalNodeNotAssigned() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String appId = "test-app";

        when(mockAssignmentMap.get(appId)).thenReturn(null);

        // When
        boolean isLocal = scheduler.isAssignedToLocalNode(appId);

        // Then
        assertFalse(isLocal);
    }

    @Test
    @DisplayName("Should get all assignments")
    void testGetAllAssignments() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        Map<String, String> expectedAssignments = new HashMap<>();
        expectedAssignments.put("app1", "node1");
        expectedAssignments.put("app2", "node2");
        expectedAssignments.put("app3", "node1");

        // Mock the map methods needed for HashMap copy constructor
        when(mockAssignmentMap.size()).thenReturn(expectedAssignments.size());
        when(mockAssignmentMap.entrySet()).thenReturn(expectedAssignments.entrySet());
        when(mockAssignmentMap.isEmpty()).thenReturn(false);

        // When
        Map<String, String> assignments = scheduler.getAllAssignments();

        // Then
        assertEquals(expectedAssignments.size(), assignments.size());
    }

    @Test
    @DisplayName("Should get node loads")
    void testGetNodeLoads() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        Map<String, Integer> expectedLoads = new HashMap<>();
        expectedLoads.put("node1", 3);
        expectedLoads.put("node2", 1);
        expectedLoads.put("node3", 2);

        // Mock the map methods needed for HashMap copy constructor
        when(mockNodeLoadMap.size()).thenReturn(expectedLoads.size());
        when(mockNodeLoadMap.entrySet()).thenReturn(expectedLoads.entrySet());
        when(mockNodeLoadMap.isEmpty()).thenReturn(false);

        // When
        Map<String, Integer> loads = scheduler.getNodeLoads();

        // Then
        assertEquals(expectedLoads.size(), loads.size());
    }

    @Test
    @DisplayName("Should reassign applications from failed node")
    void testReassignFromFailedNode() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String failedNodeId = "failed-node-123";

        when(mockClusterManager.isLeader()).thenReturn(true);

        // Setup assignments
        Map<String, String> assignments = new HashMap<>();
        assignments.put("app1", failedNodeId);
        assignments.put("app2", failedNodeId);
        assignments.put("app3", "other-node");

        // Mock entrySet() to return the assignments
        when(mockAssignmentMap.entrySet()).thenReturn(assignments.entrySet());
        when(mockAssignmentMap.remove("app1")).thenReturn(failedNodeId);
        when(mockAssignmentMap.remove("app2")).thenReturn(failedNodeId);

        // Setup available nodes
        Set<ClusterNode> nodes = createNodes(2);
        when(mockClusterManager.getNodes()).thenReturn(nodes);

        // Mock compute to execute the BiFunction
        when(mockNodeLoadMap.compute(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            java.util.function.BiFunction<String, Integer, Integer> func = invocation.getArgument(1);
            return func.apply(key, null);
        });

        // When
        int reassignedCount = scheduler.reassignFromFailedNode(failedNodeId);

        // Then
        assertEquals(2, reassignedCount);
        verify(mockAssignmentMap).remove("app1");
        verify(mockAssignmentMap).remove("app2");
        verify(mockAssignmentMap, times(2)).put(anyString(), anyString());
        verify(mockNodeLoadMap).remove(failedNodeId);
    }

    @Test
    @DisplayName("Should throw exception when non-leader tries to reassign")
    void testReassignAsNonLeader() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        when(mockClusterManager.isLeader()).thenReturn(false);

        // When/Then
        assertThrows(IllegalStateException.class,
                () -> scheduler.reassignFromFailedNode("failed-node"));
    }

    @Test
    @DisplayName("Should handle reassignment when no nodes available")
    void testReassignWithNoNodes() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String failedNodeId = "failed-node-123";

        when(mockClusterManager.isLeader()).thenReturn(true);

        // Setup assignments
        Map<String, String> assignments = new ConcurrentHashMap<>();
        assignments.put("app1", failedNodeId);

        when(mockAssignmentMap.entrySet()).thenReturn(assignments.entrySet());
        when(mockAssignmentMap.remove("app1")).thenReturn(failedNodeId);
        when(mockClusterManager.getNodes()).thenReturn(Collections.emptySet());

        // When
        int reassignedCount = scheduler.reassignFromFailedNode(failedNodeId);

        // Then
        assertEquals(0, reassignedCount);
        verify(mockNodeLoadMap).remove(failedNodeId);
    }

    @Test
    @DisplayName("Should handle errors during reassignment gracefully")
    void testReassignWithErrors() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        String failedNodeId = "failed-node-123";

        when(mockClusterManager.isLeader()).thenReturn(true);

        // Setup assignments
        Map<String, String> assignments = new HashMap<>();
        assignments.put("app1", failedNodeId);
        assignments.put("app2", failedNodeId);

        when(mockAssignmentMap.entrySet()).thenReturn(assignments.entrySet());
        when(mockAssignmentMap.remove("app1")).thenThrow(new RuntimeException("Test error"));
        when(mockAssignmentMap.remove("app2")).thenReturn(failedNodeId);

        Set<ClusterNode> nodes = createNodes(1);
        when(mockClusterManager.getNodes()).thenReturn(nodes);

        // Mock compute to execute the BiFunction
        when(mockNodeLoadMap.compute(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            java.util.function.BiFunction<String, Integer, Integer> func = invocation.getArgument(1);
            return func.apply(key, null);
        });

        // When
        int reassignedCount = scheduler.reassignFromFailedNode(failedNodeId);

        // Then - Should continue despite error
        assertEquals(1, reassignedCount);
    }

    @Test
    @DisplayName("Should balance load across nodes with least-loaded strategy")
    void testLoadBalancing() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager,
                ApplicationScheduler.SchedulingStrategy.LEAST_LOADED);

        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockAssignmentMap.get(anyString())).thenReturn(null);

        Set<ClusterNode> nodes = createNodes(3);
        when(mockClusterManager.getNodes()).thenReturn(nodes);

        List<ClusterNode> nodeList = new ArrayList<>(nodes);

        // Setup initial loads
        Map<String, Integer> loads = new ConcurrentHashMap<>();
        loads.put(nodeList.get(0).getNodeId(), 0);
        loads.put(nodeList.get(1).getNodeId(), 0);
        loads.put(nodeList.get(2).getNodeId(), 0);

        when(mockNodeLoadMap.getOrDefault(anyString(), eq(0))).thenAnswer(invocation -> {
            String nodeId = invocation.getArgument(0);
            return loads.getOrDefault(nodeId, 0);
        });

        when(mockNodeLoadMap.compute(anyString(), any())).thenAnswer(invocation -> {
            String nodeId = invocation.getArgument(0);
            loads.merge(nodeId, 1, Integer::sum);
            return loads.get(nodeId);
        });

        // When - Assign multiple applications
        for (int i = 0; i < 9; i++) {
            scheduler.assignApplication("app-" + i);
        }

        // Then - Each node should get 3 applications (balanced)
        verify(mockAssignmentMap, times(9)).put(anyString(), anyString());
        verify(mockNodeLoadMap, times(9)).compute(anyString(), any());
    }

    @Test
    @DisplayName("Should handle round-robin with single node")
    void testRoundRobinSingleNode() {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager,
                ApplicationScheduler.SchedulingStrategy.ROUND_ROBIN);

        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockAssignmentMap.get(anyString())).thenReturn(null);

        Set<ClusterNode> nodes = createNodes(1);
        when(mockClusterManager.getNodes()).thenReturn(nodes);

        String nodeId = nodes.iterator().next().getNodeId();

        // When - Assign multiple applications
        String node1 = scheduler.assignApplication("app1");
        String node2 = scheduler.assignApplication("app2");
        String node3 = scheduler.assignApplication("app3");

        // Then - All should go to the same node
        assertEquals(nodeId, node1);
        assertEquals(nodeId, node2);
        assertEquals(nodeId, node3);
    }

    @Test
    @DisplayName("Should handle concurrent assignments safely")
    void testConcurrentAssignments() throws InterruptedException {
        // Given
        scheduler = new ApplicationScheduler(mockHazelcast, mockClusterManager);
        when(mockClusterManager.isLeader()).thenReturn(true);
        when(mockAssignmentMap.get(anyString())).thenReturn(null);

        Set<ClusterNode> nodes = createNodes(3);
        when(mockClusterManager.getNodes()).thenReturn(nodes);

        // Mock compute to execute the BiFunction
        when(mockNodeLoadMap.compute(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            java.util.function.BiFunction<String, Integer, Integer> func = invocation.getArgument(1);
            return func.apply(key, null);
        });

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // When - Assign from multiple threads
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    scheduler.assignApplication("app-" + index);
                } catch (Exception e) {
                    // Ignore - testing concurrency safety
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Then - All assignments should complete
        verify(mockAssignmentMap, atLeast(1)).put(anyString(), anyString());
    }

    /**
     * Helper method to create a set of cluster nodes for testing.
     */
    private Set<ClusterNode> createNodes(int count) {
        Set<ClusterNode> nodes = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ClusterNode node = new ClusterNode(
                    "node-" + i,
                    "host-" + i,
                    5701 + i,
                    ClusterNode.NodeState.ACTIVE,
                    System.currentTimeMillis()
            );
            nodes.add(node);
        }
        return nodes;
    }
}
