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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flossware.jplatform.api.ApplicationContext;
import org.flossware.jplatform.api.PlatformManager;
import org.flossware.jplatform.api.ResourceMonitor;
import org.flossware.jplatform.api.ResourceSnapshot;
import org.flossware.jplatform.api.ResourceUsageHistory;
import org.flossware.jplatform.api.ThreadPoolExecutor;
import org.flossware.jplatform.api.ThreadPoolStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of ApplicationMBean that exposes application metrics via JMX.
 * Delegates attribute access to the ApplicationContext and operations to the ApplicationManager.
 *
 * <p>This implementation provides:</p>
 * <ul>
 *   <li>Real-time resource monitoring (CPU, memory, threads)</li>
 *   <li>Thread pool statistics (active, queued, completed tasks)</li>
 *   <li>Lifecycle operations (start, stop)</li>
 *   <li>Historical resource data in JSON format</li>
 * </ul>
 *
 * <p>Thread safety: This class is thread-safe as it delegates to thread-safe components
 * (ApplicationContext and ApplicationManager).</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApplicationMBeanImpl mbean = new ApplicationMBeanImpl(
 *     "my-app",
 *     applicationContext,
 *     applicationManager
 * );
 *
 * // MBean will be registered by JmxMetricsExporter
 * ObjectName name = new ObjectName("org.flossware.jplatform:type=Application,id=my-app");
 * mBeanServer.registerMBean(mbean, name);
 * }</pre>
 *
 * @see ApplicationMBean
 * @see org.flossware.jplatform.api.ApplicationContext
 * @see org.flossware.jplatform.api.PlatformManager
 */
public class ApplicationMBeanImpl implements ApplicationMBean {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationMBeanImpl.class);

    private final String applicationId;
    private final ApplicationContext context;
    private final PlatformManager manager;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new ApplicationMBean implementation.
     *
     * @param applicationId the unique identifier for the application
     * @param context the application context containing metrics and resources
     * @param manager the platform manager for lifecycle operations
     * @throws IllegalArgumentException if any parameter is null
     */
    public ApplicationMBeanImpl(String applicationId, ApplicationContext context, PlatformManager manager) {
        if (applicationId == null || applicationId.isEmpty()) {
            throw new IllegalArgumentException("Application ID cannot be null or empty");
        }
        if (context == null) {
            throw new IllegalArgumentException("ApplicationContext cannot be null");
        }
        if (manager == null) {
            throw new IllegalArgumentException("PlatformManager cannot be null");
        }

        this.applicationId = applicationId;
        this.context = context;
        this.manager = manager;
        this.objectMapper = new ObjectMapper();

        logger.debug("[{}] ApplicationMBean implementation created", applicationId);
    }

    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @Override
    public String getState() {
        return context.getState().name();
    }

    @Override
    public long getCpuTimeNanos() {
        ResourceMonitor monitor = context.getResourceMonitor();
        if (monitor == null) {
            return -1;  // Indicate unavailable
        }
        ResourceSnapshot snapshot = monitor.getCurrentSnapshot();
        return snapshot.getCpuTimeNanos();
    }

    @Override
    public long getHeapUsedBytes() {
        ResourceMonitor monitor = context.getResourceMonitor();
        if (monitor == null) {
            return -1;  // Indicate unavailable
        }
        ResourceSnapshot snapshot = monitor.getCurrentSnapshot();
        return snapshot.getHeapUsedBytes();
    }

    @Override
    public int getThreadCount() {
        ResourceMonitor monitor = context.getResourceMonitor();
        if (monitor == null) {
            return -1;  // Indicate unavailable
        }
        ResourceSnapshot snapshot = monitor.getCurrentSnapshot();
        return snapshot.getThreadCount();
    }

    @Override
    public int getActiveThreads() {
        ThreadPoolExecutor executor = context.getThreadPool();
        if (executor == null) {
            return -1;  // Indicate unavailable
        }
        ThreadPoolStats stats = executor.getStats();
        return stats.getActiveThreads();
    }

    @Override
    public int getQueuedTasks() {
        ThreadPoolExecutor executor = context.getThreadPool();
        if (executor == null) {
            return -1;  // Indicate unavailable
        }
        ThreadPoolStats stats = executor.getStats();
        return stats.getQueuedTasks();
    }

    @Override
    public long getCompletedTasks() {
        ThreadPoolExecutor executor = context.getThreadPool();
        if (executor == null) {
            return -1;  // Indicate unavailable
        }
        ThreadPoolStats stats = executor.getStats();
        return stats.getCompletedTasks();
    }

    @Override
    public void start() throws Exception {
        logger.info("[{}] JMX operation: start() invoked", applicationId);
        try {
            manager.start(applicationId);
            logger.info("[{}] Application started successfully via JMX", applicationId);
        } catch (Exception e) {
            logger.error("[{}] Failed to start application via JMX", applicationId, e);
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("[{}] JMX operation: stop() invoked", applicationId);
        try {
            manager.stop(applicationId);
            logger.info("[{}] Application stopped successfully via JMX", applicationId);
        } catch (Exception e) {
            logger.error("[{}] Failed to stop application via JMX", applicationId, e);
            throw e;
        }
    }

    @Override
    public String getResourceHistory(int minutes) {
        logger.debug("[{}] JMX operation: getResourceHistory({}) invoked", applicationId, minutes);

        try {
            ResourceMonitor monitor = context.getResourceMonitor();
            if (monitor == null) {
                return "{\"error\": \"Resource monitoring not available for this application\"}";
            }

            Duration duration = Duration.ofMinutes(minutes);
            ResourceUsageHistory history = monitor.getHistory(duration);

            // Convert to a simplified JSON-friendly structure
            Map<String, Object> result = new HashMap<>();
            result.put("applicationId", applicationId);
            result.put("durationMinutes", minutes);
            result.put("snapshotCount", history.size());

            List<Map<String, Object>> snapshots = history.getSnapshots().stream()
                    .map(this::snapshotToMap)
                    .collect(Collectors.toList());

            result.put("snapshots", snapshots);

            String json = objectMapper.writeValueAsString(result);
            logger.debug("[{}] Resource history retrieved: {} snapshots", applicationId, history.size());
            return json;

        } catch (Exception e) {
            logger.error("[{}] Failed to retrieve resource history", applicationId, e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Converts a ResourceSnapshot to a Map for JSON serialization.
     *
     * @param snapshot the resource snapshot to convert
     * @return a map representation of the snapshot
     */
    private Map<String, Object> snapshotToMap(ResourceSnapshot snapshot) {
        Map<String, Object> map = new HashMap<>();
        map.put("timestamp", snapshot.getTimestamp());
        map.put("cpuTimeNanos", snapshot.getCpuTimeNanos());
        map.put("heapUsedBytes", snapshot.getHeapUsedBytes());
        map.put("threadCount", snapshot.getThreadCount());
        map.put("bytesRead", snapshot.getBytesRead());
        map.put("bytesWritten", snapshot.getBytesWritten());
        return map;
    }
}
