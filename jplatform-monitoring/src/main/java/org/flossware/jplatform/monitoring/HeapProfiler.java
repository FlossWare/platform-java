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

package org.flossware.jplatform.monitoring;

import java.util.Map;

/**
 * Interface for heap profiling implementations.
 * <p>
 * Provides methods to measure heap usage for specific ClassLoaders,
 * enabling precise memory tracking for isolated applications.
 * </p>
 *
 * <h3>Implementations</h3>
 * <ul>
 *   <li><strong>JvmtiHeapProfiler</strong> - Uses native JVMTI agent for exact measurements</li>
 *   <li><strong>EstimatingHeapProfiler</strong> - Uses heuristics and JVM-wide metrics</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * HeapProfiler profiler = createProfiler();
 * profiler.enableProfiling("my-app");
 *
 * ClassLoader appLoader = getApplicationClassLoader();
 * long heapBytes = profiler.getHeapUsageBytes(appLoader);
 * Map<String, Long> byClass = profiler.getHeapByClass(appLoader);
 *
 * profiler.disableProfiling("my-app");
 * }</pre>
 *
 * @author Scot P. Floess
 * @version 1.0
 * @since 1.0
 *
 * @see org.flossware.jplatform.jvmti.JvmtiHeapProfiler
 */
public interface HeapProfiler {

    /**
     * Enables heap profiling for the specified application.
     * <p>
     * This method should be called before measuring heap usage to activate
     * profiling for the application. Some implementations may use this to
     * set up tracking structures or enable JVM features.
     * </p>
     *
     * @param applicationId the unique identifier for the application
     * @throws IllegalArgumentException if applicationId is null or empty
     */
    void enableProfiling(String applicationId);

    /**
     * Disables heap profiling for the specified application.
     * <p>
     * After calling this method, heap measurements may no longer be accurate
     * for the application. Implementations should clean up any tracking
     * structures or resources associated with the application.
     * </p>
     *
     * @param applicationId the unique identifier for the application
     * @throws IllegalArgumentException if applicationId is null or empty
     */
    void disableProfiling(String applicationId);

    /**
     * Checks if profiling is currently enabled for the specified application.
     *
     * @param applicationId the unique identifier for the application
     * @return {@code true} if profiling is enabled, {@code false} otherwise
     */
    boolean isProfilingEnabled(String applicationId);

    /**
     * Returns the total heap usage in bytes for objects loaded by the specified ClassLoader.
     * <p>
     * This includes all objects whose classes were loaded by the ClassLoader,
     * including objects in all generations (young, old, metaspace).
     * </p>
     *
     * <h3>Implementation Notes</h3>
     * <ul>
     *   <li>JVMTI-based: Iterates heap and sums sizes of matching objects</li>
     *   <li>Estimation-based: Uses heuristics based on JVM-wide metrics</li>
     * </ul>
     *
     * @param classLoader the ClassLoader to measure heap usage for
     * @return total heap usage in bytes
     * @throws IllegalArgumentException if classLoader is null
     * @throws RuntimeException if heap measurement fails
     */
    long getHeapUsageBytes(ClassLoader classLoader);

    /**
     * Returns a breakdown of heap usage by class name.
     * <p>
     * The returned map contains class names as keys (e.g., "java.lang.String",
     * "com.example.MyClass") and their corresponding heap usage in bytes as values.
     * </p>
     *
     * <h3>Implementation Notes</h3>
     * <ul>
     *   <li>JVMTI-based: Iterates heap and groups by class name</li>
     *   <li>Estimation-based: May return empty map or approximate values</li>
     * </ul>
     *
     * <h3>Performance</h3>
     * This method may be expensive on large heaps. Consider caching results.
     *
     * @param classLoader the ClassLoader to measure heap usage for
     * @return map of class names to heap usage in bytes (never null)
     * @throws IllegalArgumentException if classLoader is null
     * @throws RuntimeException if heap measurement fails
     */
    Map<String, Long> getHeapByClass(ClassLoader classLoader);
}
