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

package org.flossware.jplatform.metrics.jmx;

/**
 * MBean interface for exposing application metrics via JMX.
 * Provides read-only access to application state, resource usage, and thread pool statistics,
 * plus operations for lifecycle management and historical data retrieval.
 *
 * <p>JMX clients can monitor:</p>
 * <ul>
 *   <li>Application identification and state</li>
 *   <li>CPU and memory consumption</li>
 *   <li>Thread pool activity and task completion</li>
 * </ul>
 *
 * <p>Example JMX query using jconsole or JVisualVM:</p>
 * <pre>
 * ObjectName: org.flossware.jplatform:type=Application,id=my-app
 * Attributes: ApplicationId, State, CpuTimeNanos, HeapUsedBytes, ThreadCount
 * Operations: start(), stop(), getResourceHistory(int)
 * </pre>
 *
 * @see ApplicationMBeanImpl
 */
public interface ApplicationMBean {

    /**
     * Returns the unique identifier for this application.
     *
     * @return the application ID
     */
    String getApplicationId();

    /**
     * Returns the current lifecycle state of the application.
     *
     * @return the application state as a string (e.g., "RUNNING", "STOPPED")
     */
    String getState();

    /**
     * Returns the cumulative CPU time consumed by the application.
     *
     * @return the CPU time in nanoseconds
     */
    long getCpuTimeNanos();

    /**
     * Returns the current heap memory usage of the application.
     *
     * @return the heap usage in bytes
     */
    long getHeapUsedBytes();

    /**
     * Returns the total number of threads in the application's thread pool.
     *
     * @return the thread count
     */
    int getThreadCount();

    /**
     * Returns the number of threads currently executing tasks.
     *
     * @return the active thread count
     */
    int getActiveThreads();

    /**
     * Returns the number of tasks waiting in the queue for execution.
     *
     * @return the queued task count
     */
    int getQueuedTasks();

    /**
     * Returns the total number of tasks that have completed execution.
     *
     * @return the completed task count
     */
    long getCompletedTasks();

    /**
     * Starts the application if it is not already running.
     *
     * @throws Exception if the application cannot be started
     */
    void start() throws Exception;

    /**
     * Stops the application if it is currently running.
     *
     * @throws Exception if the application cannot be stopped
     */
    void stop() throws Exception;

    /**
     * Returns a JSON string containing resource usage history for the specified time period.
     * The history includes snapshots of CPU time, memory usage, and thread count.
     *
     * @param minutes the number of minutes of history to retrieve
     * @return a JSON string representation of the resource history
     */
    String getResourceHistory(int minutes);
}
