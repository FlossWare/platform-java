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

package org.flossware.jplatform.jvmti;

import org.flossware.jplatform.monitoring.HeapProfiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JVMTI-based heap profiler that provides precise heap usage tracking by ClassLoader.
 * <p>
 * This implementation uses a native JVMTI agent to iterate through the heap and accurately
 * measure memory usage for objects loaded by specific ClassLoaders. This provides exact
 * measurements rather than estimates.
 * </p>
 *
 * <h3>Requirements</h3>
 * <ul>
 *   <li>The native JVMTI agent library must be loaded at JVM startup</li>
 *   <li>Use {@code -agentpath:/path/to/libjplatform-agent.so} JVM argument</li>
 *   <li>Or set {@code JAVA_TOOL_OPTIONS=-agentpath:/path/to/libjplatform-agent.so}</li>
 *   <li>The native library must be compiled for your platform (Linux x64, etc.)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Check if JVMTI agent is available
 * if (JvmtiHeapProfiler.isAvailable()) {
 *     HeapProfiler profiler = new JvmtiHeapProfiler();
 *     profiler.enableProfiling("my-app");
 *
 *     ClassLoader appLoader = getApplicationClassLoader();
 *     long heapBytes = profiler.getHeapUsageBytes(appLoader);
 *     Map<String, Long> byClass = profiler.getHeapByClass(appLoader);
 *
 *     profiler.disableProfiling("my-app");
 * } else {
 *     // Fall back to estimation-based approach
 *     logger.warn("JVMTI agent not available, using estimation");
 * }
 * }</pre>
 *
 * <h3>Performance Considerations</h3>
 * <ul>
 *   <li>Heap iteration can be expensive on large heaps (100+ GB)</li>
 *   <li>Consider caching results and updating periodically rather than on every call</li>
 *   <li>The native agent adds minimal overhead when not actively profiling</li>
 *   <li>Heap iteration uses JVMTI callbacks and does not require heap dumps</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * This class is thread-safe. Multiple threads can safely call profiling methods concurrently.
 *
 * @author Scot P. Floess
 * @version 1.0
 * @since 1.0
 *
 * @see HeapProfiler
 * @see org.flossware.jplatform.monitoring.ApplicationResourceMonitor
 */
public class JvmtiHeapProfiler implements HeapProfiler {

    private static final Logger logger = LoggerFactory.getLogger(JvmtiHeapProfiler.class);

    /**
     * Name of the native library to load.
     */
    private static final String NATIVE_LIBRARY_NAME = "jplatform-agent";

    /**
     * Indicates whether the native library was successfully loaded.
     */
    private static volatile boolean nativeLibraryLoaded = false;

    /**
     * Set of application IDs for which profiling is currently enabled.
     */
    private final Set<String> enabledApplications = ConcurrentHashMap.newKeySet();

    /**
     * Static initializer to load the native JVMTI agent library.
     */
    static {
        try {
            System.loadLibrary(NATIVE_LIBRARY_NAME);
            nativeLibraryLoaded = true;
            logger.info("Successfully loaded JVMTI native library: {}", NATIVE_LIBRARY_NAME);
        } catch (UnsatisfiedLinkError e) {
            logger.warn("Failed to load JVMTI native library '{}'. Profiling will not be available. " +
                       "Ensure the library is in java.library.path or the JVMTI agent is loaded at startup.",
                       NATIVE_LIBRARY_NAME, e);
        }
    }

    /**
     * Checks if the JVMTI native agent is available and loaded.
     * <p>
     * Applications should check this before creating a {@link JvmtiHeapProfiler} instance
     * to determine if native profiling is supported.
     * </p>
     *
     * @return {@code true} if the native library is loaded and profiling is available,
     *         {@code false} otherwise
     */
    public static boolean isAvailable() {
        return nativeLibraryLoaded;
    }

    /**
     * Constructs a new JVMTI heap profiler.
     *
     * @throws UnsupportedOperationException if the native JVMTI library is not available
     */
    public JvmtiHeapProfiler() {
        if (!nativeLibraryLoaded) {
            throw new UnsupportedOperationException(
                "JVMTI native library not loaded. Check logs for details and ensure " +
                "the agent is loaded at JVM startup with -agentpath option."
            );
        }
    }

    /**
     * Native method to get total heap usage in bytes for objects loaded by the specified ClassLoader.
     * <p>
     * This method is implemented in native C code using JVMTI heap iteration callbacks.
     * </p>
     *
     * @param classLoader the ClassLoader to measure heap usage for
     * @return total heap usage in bytes
     */
    private native long getHeapUsageBytesNative(ClassLoader classLoader);

    /**
     * Native method to get heap usage breakdown by class name.
     * <p>
     * This method is implemented in native C code using JVMTI heap iteration callbacks.
     * </p>
     *
     * @param classLoader the ClassLoader to measure heap usage for
     * @return map of class names to heap usage in bytes
     */
    private native Map<String, Long> getHeapByClassNative(ClassLoader classLoader);

    @Override
    public void enableProfiling(String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) {
            throw new IllegalArgumentException("Application ID cannot be null or empty");
        }

        boolean added = enabledApplications.add(applicationId);
        if (added) {
            logger.info("Enabled JVMTI heap profiling for application: {}", applicationId);
        } else {
            logger.debug("JVMTI heap profiling already enabled for application: {}", applicationId);
        }
    }

    @Override
    public void disableProfiling(String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) {
            throw new IllegalArgumentException("Application ID cannot be null or empty");
        }

        boolean removed = enabledApplications.remove(applicationId);
        if (removed) {
            logger.info("Disabled JVMTI heap profiling for application: {}", applicationId);
        } else {
            logger.debug("JVMTI heap profiling was not enabled for application: {}", applicationId);
        }
    }

    @Override
    public boolean isProfilingEnabled(String applicationId) {
        return applicationId != null && enabledApplications.contains(applicationId);
    }

    @Override
    public long getHeapUsageBytes(ClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalArgumentException("ClassLoader cannot be null");
        }

        try {
            long heapBytes = getHeapUsageBytesNative(classLoader);
            logger.debug("Heap usage for ClassLoader {}: {} bytes",
                        classLoader.getName(), heapBytes);
            return heapBytes;
        } catch (Exception e) {
            logger.error("Error retrieving heap usage from JVMTI agent", e);
            throw new RuntimeException("Failed to retrieve heap usage from JVMTI agent", e);
        }
    }

    @Override
    public Map<String, Long> getHeapByClass(ClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalArgumentException("ClassLoader cannot be null");
        }

        try {
            Map<String, Long> heapByClass = getHeapByClassNative(classLoader);
            if (heapByClass == null) {
                logger.warn("JVMTI agent returned null for heap-by-class data");
                return Collections.emptyMap();
            }

            logger.debug("Retrieved heap breakdown for ClassLoader {}: {} classes",
                        classLoader.getName(), heapByClass.size());
            return Collections.unmodifiableMap(heapByClass);
        } catch (Exception e) {
            logger.error("Error retrieving heap-by-class data from JVMTI agent", e);
            throw new RuntimeException("Failed to retrieve heap-by-class data from JVMTI agent", e);
        }
    }
}
