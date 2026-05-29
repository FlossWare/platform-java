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

import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Resource monitor for an application.
 * Tracks CPU time and thread count. Heap usage tracking is not supported without JVMTI instrumentation.
 * <p>
 * This implementation automatically polls resource metrics every 5 seconds,
 * maintaining a rolling history of snapshots for the last hour. It supports
 * quota enforcement and event notifications when quotas are exceeded.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe. Uses CopyOnWriteArrayList for history
 * and listeners, volatile fields for quota and enforcer references.
 * <p>
 * <strong>Limitations:</strong>
 * <ul>
 *   <li>Heap usage is reported as -1 (not available) - accurate per-application heap tracking
 *       requires JVMTI instrumentation which is not currently implemented</li>
 *   <li>Disk I/O metrics are reported as 0 (not available)</li>
 * </ul>
 * <p>
 * Example usage:
 * {@code
 * ThreadGroup threadGroup = new ThreadGroup("my-app");
 * ApplicationResourceMonitor monitor = new ApplicationResourceMonitor("my-app", threadGroup);
 *
 * // Set resource quota (note: heap quotas cannot be enforced without heap tracking)
 * ResourceQuota quota = ResourceQuota.builder()
 *     .maxCpuTimeSeconds(60)
 *     .maxThreads(20)
 *     .build();
 * monitor.setQuota(quota);
 *
 * // Add listener for quota violations
 * monitor.addListener((appId, quota, snapshot) -> {
 *     System.err.println("Quota exceeded for " + appId);
 * });
 *
 * // Get current snapshot
 * ResourceSnapshot snapshot = monitor.getCurrentSnapshot();
 * System.out.println("CPU time: " + snapshot.getCpuTimeNanos());
 * System.out.println("Thread count: " + snapshot.getThreadCount());
 * System.out.println("Heap usage: " + snapshot.getHeapUsedBytes() + " (-1 = not available)");
 *
 * // Get history
 * ResourceUsageHistory history = monitor.getHistory(Duration.ofMinutes(10));
 *
 * // When done
 * monitor.shutdown();
 * }
 *
 * @see ResourceMonitor
 * @see ResourceSnapshot
 * @see ResourceQuota
 */
public class ApplicationResourceMonitor implements ResourceMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationResourceMonitor.class);
    private static final long DEFAULT_POLL_INTERVAL_SECONDS = 5;
    private static final int DEFAULT_HISTORY_SIZE = 720; // 1 hour at 5s intervals

    private final String applicationId;
    private final ThreadGroup applicationThreadGroup;
    private final ScheduledExecutorService scheduler;
    private final Deque<ResourceSnapshot> history;
    private final List<ResourceEventListener> listeners;
    private final int maxHistorySize;
    private volatile ResourceQuota quota;
    private volatile ResourceEnforcer enforcer;  // Optional enforcer for quota violations

    /**
     * Creates a new resource monitor with default configuration.
     * <p>
     * Uses default polling interval of 5 seconds and history size of 720 snapshots (1 hour).
     * The scheduler runs in a daemon thread to not prevent JVM shutdown.
     *
     * @param applicationId the unique identifier for the application
     * @param threadGroup the thread group containing the application's threads
     * @throws NullPointerException if applicationId or threadGroup is null
     */
    public ApplicationResourceMonitor(String applicationId, ThreadGroup threadGroup) {
        this(applicationId, threadGroup, DEFAULT_POLL_INTERVAL_SECONDS, DEFAULT_HISTORY_SIZE);
    }

    /**
     * Creates a new resource monitor with custom configuration.
     * <p>
     * The scheduler runs in a daemon thread to not prevent JVM shutdown.
     *
     * @param applicationId the unique identifier for the application
     * @param threadGroup the thread group containing the application's threads
     * @param pollIntervalSeconds the interval in seconds between metric collections (must be > 0)
     * @param maxHistorySize the maximum number of snapshots to retain in history (must be > 0)
     * @throws NullPointerException if applicationId or threadGroup is null
     * @throws IllegalArgumentException if pollIntervalSeconds or maxHistorySize is <= 0
     */
    public ApplicationResourceMonitor(String applicationId, ThreadGroup threadGroup,
                                     long pollIntervalSeconds, int maxHistorySize) {
        this.applicationId = Objects.requireNonNull(applicationId, "applicationId cannot be null");
        this.applicationThreadGroup = Objects.requireNonNull(threadGroup, "threadGroup cannot be null");

        if (pollIntervalSeconds <= 0) {
            throw new IllegalArgumentException("pollIntervalSeconds must be > 0, got: " + pollIntervalSeconds);
        }
        if (maxHistorySize <= 0) {
            throw new IllegalArgumentException("maxHistorySize must be > 0, got: " + maxHistorySize);
        }

        this.maxHistorySize = maxHistorySize;
        this.history = new ArrayDeque<>(maxHistorySize);
        this.listeners = new CopyOnWriteArrayList<>();

        // Poll metrics at configured interval
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, applicationId + "-monitor");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::collectMetrics, 0, pollIntervalSeconds, TimeUnit.SECONDS);
        logger.info("[{}] Started resource monitor (poll interval: {}s, history size: {})",
                   applicationId, pollIntervalSeconds, maxHistorySize);
    }

    private void collectMetrics() {
        try {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

            // Get threads in this application's thread group
            Thread[] threads = getApplicationThreads();

            long totalCpuTime = 0;
            for (Thread thread : threads) {
                if (threadMXBean.isThreadCpuTimeSupported()) {
                    long cpuTime = threadMXBean.getThreadCpuTime(thread.getId());
                    if (cpuTime > 0) {
                        totalCpuTime += cpuTime;
                    }
                }
            }

            // Estimate heap usage (approximation)
            long heapUsed = estimateHeapUsage();

            ResourceSnapshot snapshot = new ResourceSnapshot(
                    System.currentTimeMillis(),
                    totalCpuTime,
                    heapUsed,
                    threads.length,
                    0, // bytesRead - not easily available without instrumentation
                    0, // bytesWritten - not easily available
                    Collections.emptyMap()
            );

            history.addLast(snapshot);

            // Keep only configured history size
            while (history.size() > maxHistorySize) {
                history.removeFirst();  // O(1) with ArrayDeque
            }

            // Check quota
            if (quota != null) {
                try {
                    quota.enforce(snapshot);
                } catch (ResourceQuotaExceededException e) {
                    fireQuotaExceeded(snapshot);

                    // Trigger enforcement action if configured
                    if (enforcer != null) {
                        enforcer.enforceQuota(quota, snapshot);
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("[{}] Error collecting metrics", applicationId, e);
        }
    }

    private Thread[] getApplicationThreads() {
        // Cap at reasonable maximum to prevent overflow and excessive memory use
        final int MAX_THREADS = 100_000;
        final int MAX_RETRIES = 5;

        int initialSize = applicationThreadGroup.activeCount();
        if (initialSize < 0) {
            initialSize = 0;
        }
        // Prevent overflow: activeCount() * 2 must fit in int and leave room for retry growth
        if (initialSize >= MAX_THREADS / 2) {
            initialSize = MAX_THREADS / 2 - 1;
            logger.warn("[{}] Thread count {} exceeds reasonable limit, capping at {}",
                       applicationId, applicationThreadGroup.activeCount(), initialSize * 2);
        }

        Thread[] threads = new Thread[initialSize * 2];
        int count;
        int retries = 0;

        // Retry with larger array if needed (count == length means array was full, some threads missed)
        while ((count = applicationThreadGroup.enumerate(threads, true)) == threads.length) {
            if (++retries > MAX_RETRIES) {
                logger.warn("[{}] Thread enumeration retry limit reached after {} attempts, may miss some threads",
                           applicationId, retries);
                break;
            }

            // Prevent overflow: threads.length * 2 must fit in int and not exceed MAX_THREADS
            int newSize = threads.length * 2;
            if (newSize < 0 || newSize > MAX_THREADS) {
                newSize = MAX_THREADS;
                logger.warn("[{}] Thread array size capped at {}", applicationId, MAX_THREADS);
            }
            threads = new Thread[newSize];
        }

        return Arrays.copyOf(threads, count);
    }

    private long estimateHeapUsage() {
        // Per-application heap tracking is not supported without JVMTI instrumentation.
        // Returning the total JVM heap (as previous implementation did) gives false data
        // that attributes all heap usage to every application, making quota enforcement
        // and monitoring meaningless in multi-application scenarios.
        //
        // Future enhancement: Provide optional JVMTI agent for accurate per-application
        // heap tracking, or use Java Flight Recorder APIs for approximation.
        //
        // For now, return -1 to indicate "not available"
        return -1;
    }

    /**
     * Returns the most recent resource snapshot.
     * <p>
     * If no snapshots have been collected yet, returns an empty snapshot
     * with all metrics set to zero.
     *
     * @return the current resource snapshot
     */
    @Override
    public ResourceSnapshot getCurrentSnapshot() {
        return history.isEmpty() ? createEmptySnapshot() : history.getLast();
    }

    /**
     * Returns the resource usage history for the specified duration.
     * <p>
     * Only includes snapshots within the specified time window from the current time.
     * History is limited to the last hour of data (720 snapshots at 5-second intervals).
     *
     * @param duration the duration of history to retrieve
     * @return the resource usage history for the specified duration
     * @throws NullPointerException if duration is null
     */
    @Override
    public ResourceUsageHistory getHistory(Duration duration) {
        Objects.requireNonNull(duration, "duration cannot be null");

        long cutoffTime = System.currentTimeMillis() - duration.toMillis();
        List<ResourceSnapshot> filtered = new ArrayList<>();

        for (ResourceSnapshot snapshot : history) {
            if (snapshot.getTimestamp() >= cutoffTime) {
                filtered.add(snapshot);
            }
        }

        return new ResourceUsageHistory(filtered);
    }

    /**
     * Sets the resource quota for this application.
     * <p>
     * The quota is checked against each new snapshot. If exceeded, registered
     * listeners are notified via {@link ResourceEventListener#onQuotaExceeded}.
     *
     * @param quota the resource quota to enforce, or null to remove quota enforcement
     */
    @Override
    public void setQuota(ResourceQuota quota) {
        this.quota = quota;
        logger.info("[{}] Resource quota set: {}", applicationId, quota);
    }

    /**
     * Returns the current resource quota, if set.
     *
     * @return the resource quota, or null if no quota is set
     */
    @Override
    public ResourceQuota getQuota() {
        return quota;
    }

    /**
     * Sets the resource enforcer for automatic quota enforcement.
     * <p>
     * When set, the enforcer will automatically take configured actions
     * (throttle, shutdown, kill) when quotas are exceeded after the
     * grace period.
     *
     * @param enforcer the resource enforcer, or null to disable enforcement
     * @since 2.0
     */
    public void setEnforcer(ResourceEnforcer enforcer) {
        this.enforcer = enforcer;
        logger.info("[{}] Resource enforcer {}", applicationId,
                enforcer != null ? "enabled" : "disabled");
    }

    /**
     * Returns the current resource enforcer, if set.
     *
     * @return the resource enforcer, or null if not set
     * @since 2.0
     */
    public ResourceEnforcer getEnforcer() {
        return enforcer;
    }

    /**
     * Adds a listener to be notified of resource events.
     * <p>
     * Listeners are notified when quotas are exceeded.
     *
     * @param listener the listener to add
     * @throws NullPointerException if listener is null
     */
    @Override
    public void addListener(ResourceEventListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.add(listener);
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    @Override
    public void removeListener(ResourceEventListener listener) {
        listeners.remove(listener);
    }

    private void fireQuotaExceeded(ResourceSnapshot snapshot) {
        for (ResourceEventListener listener : listeners) {
            try {
                listener.onQuotaExceeded(applicationId, quota, snapshot);
            } catch (Exception e) {
                logger.warn("[{}] Error in quota exceeded listener", applicationId, e);
            }
        }
    }

    private ResourceSnapshot createEmptySnapshot() {
        return new ResourceSnapshot(
                System.currentTimeMillis(),
                0, 0, 0, 0, 0,
                Collections.emptyMap()
        );
    }

    /**
     * Shuts down the resource monitor.
     * <p>
     * Stops the background metric collection scheduler and waits for it to terminate.
     * If the scheduler does not terminate within 5 seconds, forces shutdown.
     */
    public void shutdown() {
        logger.info("[{}] Shutting down resource monitor", applicationId);
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("[{}] Resource monitor did not terminate in 5 seconds, forcing shutdown",
                        applicationId);
                scheduler.shutdownNow();

                // Wait a bit longer for shutdownNow() to take effect
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.error("[{}] Resource monitor failed to terminate", applicationId);
                }
            }
        } catch (InterruptedException e) {
            logger.warn("[{}] Interrupted while waiting for resource monitor shutdown", applicationId);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("[{}] Resource monitor shut down", applicationId);
    }
}
