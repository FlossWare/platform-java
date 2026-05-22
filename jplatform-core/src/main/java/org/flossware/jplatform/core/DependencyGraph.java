package org.flossware.jplatform.core;

import java.util.*;

/**
 * Directed graph data structure for modeling application dependencies.
 *
 * <p>Provides algorithms for:</p>
 * <ul>
 *   <li>Adding dependency edges between applications</li>
 *   <li>Detecting circular dependencies</li>
 *   <li>Topological sorting for startup order</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * DependencyGraph graph = new DependencyGraph();
 * graph.addNode("app-a");
 * graph.addNode("app-b");
 * graph.addNode("app-c");
 * graph.addEdge("app-a", "app-b");  // app-a depends on app-b
 * graph.addEdge("app-b", "app-c");  // app-b depends on app-c
 *
 * List<String> startupOrder = graph.topologicalSort();  // [app-c, app-b, app-a]
 * }</pre>
 *
 * @since 2.0
 */
class DependencyGraph {

    private final Map<String, Set<String>> adjacencyList;  // node -> dependencies
    private final Map<String, Set<String>> reversedList;   // node -> dependents

    /**
     * Creates a new empty dependency graph.
     */
    public DependencyGraph() {
        this.adjacencyList = new HashMap<>();
        this.reversedList = new HashMap<>();
    }

    /**
     * Adds a node (application) to the graph.
     *
     * @param applicationId the application identifier
     */
    public void addNode(String applicationId) {
        adjacencyList.putIfAbsent(applicationId, new HashSet<>());
        reversedList.putIfAbsent(applicationId, new HashSet<>());
    }

    /**
     * Adds a directed edge indicating a dependency.
     *
     * @param from the dependent application (depends on 'to')
     * @param to the dependency application (required by 'from')
     */
    public void addEdge(String from, String to) {
        addNode(from);
        addNode(to);
        adjacencyList.get(from).add(to);
        reversedList.get(to).add(from);
    }

    /**
     * Returns the applications that depend on the given application.
     *
     * @param applicationId the application identifier
     * @return set of dependent application IDs
     */
    public Set<String> getDependents(String applicationId) {
        return Collections.unmodifiableSet(
                reversedList.getOrDefault(applicationId, Collections.emptySet())
        );
    }

    /**
     * Returns the applications that the given application depends on.
     *
     * @param applicationId the application identifier
     * @return set of dependency application IDs
     */
    public Set<String> getDependencies(String applicationId) {
        return Collections.unmodifiableSet(
                adjacencyList.getOrDefault(applicationId, Collections.emptySet())
        );
    }

    /**
     * Detects circular dependencies in the graph.
     *
     * @return list of application IDs forming a cycle, or empty list if no cycle exists
     */
    public List<String> detectCycle() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        Map<String, String> parent = new HashMap<>();

        for (String node : adjacencyList.keySet()) {
            if (!visited.contains(node)) {
                List<String> cycle = detectCycleDFS(node, visited, recursionStack, parent);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * DFS-based cycle detection helper.
     *
     * @param node current node being visited
     * @param visited set of fully visited nodes
     * @param recursionStack nodes in current DFS path
     * @param parent parent mapping for reconstructing cycle
     * @return list of nodes forming cycle, or empty if no cycle
     */
    private List<String> detectCycleDFS(String node, Set<String> visited,
                                        Set<String> recursionStack, Map<String, String> parent) {
        visited.add(node);
        recursionStack.add(node);

        for (String neighbor : adjacencyList.getOrDefault(node, Collections.emptySet())) {
            if (!visited.contains(neighbor)) {
                parent.put(neighbor, node);
                List<String> cycle = detectCycleDFS(neighbor, visited, recursionStack, parent);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            } else if (recursionStack.contains(neighbor)) {
                // Found cycle, reconstruct it
                return reconstructCycle(neighbor, node, parent);
            }
        }

        recursionStack.remove(node);
        return Collections.emptyList();
    }

    /**
     * Reconstructs the cycle path from parent mapping.
     *
     * @param start the node where cycle was detected
     * @param end the node that created the cycle edge
     * @param parent parent mapping from DFS
     * @return list of nodes in the cycle
     */
    private List<String> reconstructCycle(String start, String end, Map<String, String> parent) {
        List<String> cycle = new ArrayList<>();
        cycle.add(start);

        String current = end;
        while (!current.equals(start)) {
            cycle.add(current);
            current = parent.get(current);
        }

        Collections.reverse(cycle);
        return cycle;
    }

    /**
     * Performs topological sort to determine startup order.
     *
     * <p>Returns applications in an order such that all dependencies of an application
     * appear before the application itself.</p>
     *
     * @return list of application IDs in startup order (dependencies first)
     * @throws IllegalStateException if a circular dependency is detected
     */
    public List<String> topologicalSort() {
        List<String> cycle = detectCycle();
        if (!cycle.isEmpty()) {
            throw new IllegalStateException("Circular dependency detected: " + cycle);
        }

        // Kahn's algorithm
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : adjacencyList.keySet()) {
            inDegree.put(node, 0);
        }

        for (String node : adjacencyList.keySet()) {
            for (String neighbor : adjacencyList.get(node)) {
                inDegree.put(neighbor, inDegree.get(neighbor) + 1);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            result.add(node);

            for (String neighbor : adjacencyList.get(node)) {
                int degree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, degree);
                if (degree == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        return result;
    }

    /**
     * Returns all nodes in the graph.
     *
     * @return set of all application IDs
     */
    public Set<String> getAllNodes() {
        return Collections.unmodifiableSet(adjacencyList.keySet());
    }

    /**
     * Checks if the graph contains a node.
     *
     * @param applicationId the application identifier
     * @return true if node exists, false otherwise
     */
    public boolean containsNode(String applicationId) {
        return adjacencyList.containsKey(applicationId);
    }

    /**
     * Returns the number of nodes in the graph.
     *
     * @return node count
     */
    public int size() {
        return adjacencyList.size();
    }
}
