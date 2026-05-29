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

package org.flossware.jplatform.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DependencyGraph.
 * Tests topological sort, cycle detection, and dependency tracking.
 */
class DependencyGraphTest {

    private DependencyGraph graph;

    @BeforeEach
    void setUp() {
        graph = new DependencyGraph();
    }

    @Test
    void testAddNode() {
        graph.addNode("app-a");

        assertTrue(graph.containsNode("app-a"));
        assertEquals(1, graph.size());
    }

    @Test
    void testAddEdge() {
        graph.addNode("app-a");
        graph.addNode("app-b");
        graph.addEdge("app-a", "app-b");

        Set<String> dependencies = graph.getDependencies("app-a");
        assertTrue(dependencies.contains("app-b"));

        Set<String> dependents = graph.getDependents("app-b");
        assertTrue(dependents.contains("app-a"));
    }

    @Test
    void testTopologicalSortSimpleChain() {
        // app-a depends on app-b (app-b must start before app-a)
        // app-b depends on app-c (app-c must start before app-b)
        // Expected startup order: [app-c, app-b, app-a] (dependencies first)
        graph.addNode("app-a");
        graph.addNode("app-b");
        graph.addNode("app-c");
        graph.addEdge("app-a", "app-b");  // app-a depends on app-b
        graph.addEdge("app-b", "app-c");  // app-b depends on app-c

        List<String> sorted = graph.topologicalSort();

        assertEquals(3, sorted.size());
        // app-c has no dependencies, should come first
        // app-b depends on app-c, should come after app-c
        // app-a depends on app-b, should come last
        int indexC = sorted.indexOf("app-c");
        int indexB = sorted.indexOf("app-b");
        int indexA = sorted.indexOf("app-a");

        assertTrue(indexC < indexB, "app-c should come before app-b (app-b depends on app-c)");
        assertTrue(indexB < indexA, "app-b should come before app-a (app-a depends on app-b)");
    }

    @Test
    void testTopologicalSortDiamond() {
        // Diamond dependency (edges point from dependent to dependency):
        //     app-a (depends on app-b and app-c)
        //    /     \
        // app-b   app-c (both depend on app-d)
        //    \     /
        //     app-d (no dependencies)
        graph.addNode("app-a");
        graph.addNode("app-b");
        graph.addNode("app-c");
        graph.addNode("app-d");
        graph.addEdge("app-a", "app-b");  // app-a depends on app-b
        graph.addEdge("app-a", "app-c");  // app-a depends on app-c
        graph.addEdge("app-b", "app-d");  // app-b depends on app-d
        graph.addEdge("app-c", "app-d");  // app-c depends on app-d

        List<String> sorted = graph.topologicalSort();

        assertEquals(4, sorted.size());

        int indexA = sorted.indexOf("app-a");
        int indexB = sorted.indexOf("app-b");
        int indexC = sorted.indexOf("app-c");
        int indexD = sorted.indexOf("app-d");

        // app-d has no dependencies, must come before everything
        assertTrue(indexD < indexB, "app-d should come before app-b");
        assertTrue(indexD < indexC, "app-d should come before app-c");

        // app-b and app-c depend on app-d, must come before app-a
        assertTrue(indexB < indexA, "app-b should come before app-a");
        assertTrue(indexC < indexA, "app-c should come before app-a");
    }

    @Test
    void testDetectCycleSimple() {
        graph.addNode("app-a");
        graph.addNode("app-b");
        graph.addEdge("app-a", "app-b");
        graph.addEdge("app-b", "app-a");

        List<String> cycle = graph.detectCycle();

        assertFalse(cycle.isEmpty(), "Should detect a cycle");
        assertTrue(cycle.contains("app-a") && cycle.contains("app-b"),
                "Cycle should contain both nodes");
    }

    @Test
    void testDetectCycleThreeNodes() {
        graph.addNode("app-a");
        graph.addNode("app-b");
        graph.addNode("app-c");
        graph.addEdge("app-a", "app-b");
        graph.addEdge("app-b", "app-c");
        graph.addEdge("app-c", "app-a");

        List<String> cycle = graph.detectCycle();

        assertFalse(cycle.isEmpty(), "Should detect a cycle");
        assertEquals(3, cycle.size(), "Cycle should contain all three nodes");
    }

    @Test
    void testNoCycleDetected() {
        graph.addNode("app-a");
        graph.addNode("app-b");
        graph.addNode("app-c");
        graph.addEdge("app-a", "app-b");
        graph.addEdge("app-b", "app-c");

        List<String> cycle = graph.detectCycle();

        assertTrue(cycle.isEmpty(), "Should not detect a cycle in acyclic graph");
    }

    @Test
    void testTopologicalSortThrowsOnCycle() {
        graph.addNode("app-a");
        graph.addNode("app-b");
        graph.addEdge("app-a", "app-b");
        graph.addEdge("app-b", "app-a");

        assertThrows(IllegalStateException.class, () -> graph.topologicalSort(),
                "Topological sort should throw on cyclic graph");
    }

    @Test
    void testRemoveNode() {
        graph.addNode("app-a");
        graph.addNode("app-b");
        graph.addNode("app-c");
        graph.addEdge("app-a", "app-b");
        graph.addEdge("app-b", "app-c");

        assertEquals(3, graph.size());

        graph.removeNode("app-b");

        assertEquals(2, graph.size());
        assertTrue(graph.containsNode("app-a"));
        assertFalse(graph.containsNode("app-b"));
        assertTrue(graph.containsNode("app-c"));

        // Edges involving app-b should be removed
        assertTrue(graph.getDependencies("app-a").isEmpty());
        assertTrue(graph.getDependents("app-c").isEmpty());
    }

    @Test
    void testGetAllNodes() {
        graph.addNode("app-a");
        graph.addNode("app-b");
        graph.addNode("app-c");

        Set<String> nodes = graph.getAllNodes();

        assertEquals(3, nodes.size());
        assertTrue(nodes.contains("app-a"));
        assertTrue(nodes.contains("app-b"));
        assertTrue(nodes.contains("app-c"));
    }

    @Test
    void testEmptyGraph() {
        assertEquals(0, graph.size());
        assertTrue(graph.topologicalSort().isEmpty());
        assertTrue(graph.detectCycle().isEmpty());
    }
}
