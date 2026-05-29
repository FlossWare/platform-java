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

package org.flossware.jplatform.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.flossware.jplatform.api.ApplicationContext;
import org.flossware.jplatform.api.ApplicationState;
import org.flossware.jplatform.api.ResourceSnapshot;
import org.flossware.jplatform.api.ThreadPoolStats;

/**
 * Data transfer object for application information in REST API responses.
 * Contains application identity, state, deployment timestamp, and optional metrics.
 *
 * <p>Example JSON response:</p>
 * <pre>{@code
 * {
 *   "applicationId": "my-app",
 *   "name": "My Application",
 *   "state": "RUNNING",
 *   "deployedAt": 1716345600000,
 *   "threadPoolStats": {
 *     "activeThreads": 5,
 *     "completedTasks": 1234,
 *     "queuedTasks": 10,
 *     "poolSize": 8,
 *     "corePoolSize": 4,
 *     "maximumPoolSize": 16
 *   },
 *   "resourceMetrics": {
 *     "cpuTimeNanos": 500000000,
 *     "heapUsedBytes": 104857600,
 *     "threadCount": 8
 *   }
 * }
 * }</pre>
 *
 * @see ApplicationApiHandler
 */
public class ApplicationResponseDTO {

    @JsonProperty("applicationId")
    private final String applicationId;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("state")
    private final String state;

    @JsonProperty("deployedAt")
    private final long deployedAt;

    @JsonProperty("threadPoolStats")
    private final ThreadPoolStatsDTO threadPoolStats;

    @JsonProperty("resourceMetrics")
    private final ResourceMetricsDTO resourceMetrics;

    /**
     * Constructs a new application response DTO.
     *
     * @param applicationId the application identifier
     * @param name the application name
     * @param state the current application state
     * @param deployedAt the deployment timestamp in milliseconds since epoch
     * @param threadPoolStats the thread pool statistics, or null
     * @param resourceMetrics the resource metrics, or null
     */
    public ApplicationResponseDTO(String applicationId, String name, String state,
                                  long deployedAt, ThreadPoolStatsDTO threadPoolStats,
                                  ResourceMetricsDTO resourceMetrics) {
        this.applicationId = applicationId;
        this.name = name;
        this.state = state;
        this.deployedAt = deployedAt;
        this.threadPoolStats = threadPoolStats;
        this.resourceMetrics = resourceMetrics;
    }

    /**
     * Creates an application response DTO from an application context.
     * Includes full metrics if the application is running.
     *
     * @param context the application context
     * @return a new application response DTO
     */
    public static ApplicationResponseDTO fromApplicationContext(ApplicationContext context) {
        ThreadPoolStatsDTO threadPoolStats = null;
        ResourceMetricsDTO resourceMetrics = null;

        if (context.getState() == ApplicationState.RUNNING) {
            ThreadPoolStats stats = context.getThreadPool().getStats();
            threadPoolStats = new ThreadPoolStatsDTO(
                    stats.getActiveThreads(),
                    stats.getCompletedTasks(),
                    stats.getQueuedTasks(),
                    stats.getPoolSize(),
                    stats.getCorePoolSize(),
                    stats.getMaximumPoolSize()
            );

            ResourceSnapshot snapshot = context.getResourceMonitor().getCurrentSnapshot();
            resourceMetrics = new ResourceMetricsDTO(
                    snapshot.getCpuTimeNanos(),
                    snapshot.getHeapUsedBytes(),
                    snapshot.getThreadCount()
            );
        }

        return new ApplicationResponseDTO(
                context.getApplicationId(),
                context.getProperties().getOrDefault("name", context.getApplicationId()),
                context.getState().name(),
                context.getDeployedAt().toEpochMilli(),
                threadPoolStats,
                resourceMetrics
        );
    }

    /**
     * Returns the application identifier.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationId;
    }

    /**
     * Returns the application name.
     *
     * @return the application name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the current application state.
     *
     * @return the state as a string
     */
    public String getState() {
        return state;
    }

    /**
     * Returns the deployment timestamp.
     *
     * @return the timestamp in milliseconds since epoch
     */
    public long getDeployedAt() {
        return deployedAt;
    }

    /**
     * Returns the thread pool statistics.
     *
     * @return the thread pool stats, or null if not available
     */
    public ThreadPoolStatsDTO getThreadPoolStats() {
        return threadPoolStats;
    }

    /**
     * Returns the resource metrics.
     *
     * @return the resource metrics, or null if not available
     */
    public ResourceMetricsDTO getResourceMetrics() {
        return resourceMetrics;
    }

    /**
     * Nested DTO for thread pool statistics.
     */
    public static class ThreadPoolStatsDTO {
        @JsonProperty("activeThreads")
        private final int activeThreads;

        @JsonProperty("completedTasks")
        private final long completedTasks;

        @JsonProperty("queuedTasks")
        private final int queuedTasks;

        @JsonProperty("poolSize")
        private final int poolSize;

        @JsonProperty("corePoolSize")
        private final int corePoolSize;

        @JsonProperty("maximumPoolSize")
        private final int maximumPoolSize;

        public ThreadPoolStatsDTO(int activeThreads, long completedTasks, int queuedTasks,
                                 int poolSize, int corePoolSize, int maximumPoolSize) {
            this.activeThreads = activeThreads;
            this.completedTasks = completedTasks;
            this.queuedTasks = queuedTasks;
            this.poolSize = poolSize;
            this.corePoolSize = corePoolSize;
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getActiveThreads() {
            return activeThreads;
        }

        public long getCompletedTasks() {
            return completedTasks;
        }

        public int getQueuedTasks() {
            return queuedTasks;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }
    }

    /**
     * Nested DTO for resource metrics.
     */
    public static class ResourceMetricsDTO {
        @JsonProperty("cpuTimeNanos")
        private final long cpuTimeNanos;

        @JsonProperty("heapUsedBytes")
        private final long heapUsedBytes;

        @JsonProperty("threadCount")
        private final int threadCount;

        public ResourceMetricsDTO(long cpuTimeNanos, long heapUsedBytes, int threadCount) {
            this.cpuTimeNanos = cpuTimeNanos;
            this.heapUsedBytes = heapUsedBytes;
            this.threadCount = threadCount;
        }

        public long getCpuTimeNanos() {
            return cpuTimeNanos;
        }

        public long getHeapUsedBytes() {
            return heapUsedBytes;
        }

        public int getThreadCount() {
            return threadCount;
        }
    }
}
