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

package org.flossware.jplatform.api;

import java.util.Map;

/**
 * Provides precise heap memory profiling for applications.
 * Uses JVMTI to iterate over heap objects and attribute memory usage
 * to specific ClassLoaders.
 *
 * <p>This is an optional feature that requires a native JVMTI agent.
 * If the agent is not available, the platform falls back to estimation
 * based on Runtime.totalMemory() - Runtime.freeMemory().</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * if (HeapProfiler.isAvailable()) {
 *     HeapProfiler profiler = new JvmtiHeapProfiler();
 *     long heapBytes = profiler.getHeapUsageBytes(classLoader);
 *     System.out.println("Precise heap usage: " + heapBytes + " bytes");
 * }
 * }</pre>
 *
 * @see ResourceMonitor
 */
public interface HeapProfiler {

    /**
     * Returns the heap memory usage for objects loaded by the specified ClassLoader.
     *
     * @param classLoader the ClassLoader to measure
     * @return the heap usage in bytes
     */
    long getHeapUsageBytes(ClassLoader classLoader);

    /**
     * Returns heap usage broken down by class name.
     *
     * @param classLoader the ClassLoader to measure
     * @return a map of class name to heap bytes
     */
    Map<String, Long> getHeapByClass(ClassLoader classLoader);

    /**
     * Enables profiling for the specified application.
     * Some implementations may require explicit enablement for performance reasons.
     *
     * @param applicationId the application identifier
     */
    void enableProfiling(String applicationId);

    /**
     * Disables profiling for the specified application.
     *
     * @param applicationId the application identifier
     */
    void disableProfiling(String applicationId);

    /**
     * Checks if the JVMTI heap profiler is available.
     * This method checks if the native agent library was loaded successfully.
     *
     * @return true if the profiler is available
     */
    static boolean isAvailable() {
        try {
            System.loadLibrary("jplatform-agent");
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }
}
