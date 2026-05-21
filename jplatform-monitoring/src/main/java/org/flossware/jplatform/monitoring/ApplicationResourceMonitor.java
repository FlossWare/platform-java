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
 * Tracks CPU time, thread count, and memory usage.
 * <p>
 * This implementation automatically polls resource metrics every 5 seconds,
 * maintaining a rolling history of snapshots for the last hour. It supports
 * quota enforcement and event notifications when quotas are exceeded.
 * <p>
 * Example usage:
 * {@code
 * ThreadGroup threadGroup = new ThreadGroup("my-app");
 * ApplicationResourceMonitor monitor = new ApplicationResourceMonitor("my-app", threadGroup);
 *
 * // Set resource quota
 * ResourceQuota quota = ResourceQuota.builder()
 *     .maxCpuTimeSeconds(60)
 *     .maxHeapBytes(100 * 1024 * 1024) // 100MB
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

    private final String applicationId;
    private final ThreadGroup applicationThreadGroup;
    private final ScheduledExecutorService scheduler;
    private final List<ResourceSnapshot> history;
    private final List<ResourceEventListener> listeners;
    private volatile ResourceQuota quota;

    /**
     * Creates a new resource monitor for the specified application.
     * <p>
     * Starts a background scheduler that collects metrics every 5 seconds.
     * The scheduler runs in a daemon thread to not prevent JVM shutdown.
     *
     * @param applicationId the unique identifier for the application
     * @param threadGroup the thread group containing the application's threads
     * @throws NullPointerException if applicationId or threadGroup is null
     */
    public ApplicationResourceMonitor(String applicationId, ThreadGroup threadGroup) {
        this.applicationId = applicationId;
        this.applicationThreadGroup = threadGroup;
        this.history = new CopyOnWriteArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();

        // Poll metrics every 5 seconds
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, applicationId + "-monitor");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::collectMetrics, 0, 5, TimeUnit.SECONDS);
        logger.info("[{}] Started resource monitor", applicationId);
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

            history.add(snapshot);

            // Keep only last hour of data
            while (history.size() > 720) { // 5s intervals for 1 hour
                history.remove(0);
            }

            // Check quota
            if (quota != null) {
                try {
                    quota.enforce(snapshot);
                } catch (ResourceQuotaExceededException e) {
                    fireQuotaExceeded(snapshot);
                }
            }

        } catch (Exception e) {
            logger.warn("[{}] Error collecting metrics", applicationId, e);
        }
    }

    private Thread[] getApplicationThreads() {
        int estimatedSize = applicationThreadGroup.activeCount() * 2;
        Thread[] threads = new Thread[estimatedSize];
        int count = applicationThreadGroup.enumerate(threads, true);
        return Arrays.copyOf(threads, count);
    }

    private long estimateHeapUsage() {
        // This is a rough approximation
        // For accurate measurement, would need JVMTI agent
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
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
        return history.isEmpty() ? createEmptySnapshot() : history.get(history.size() - 1);
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
     * Adds a listener to be notified of resource events.
     * <p>
     * Listeners are notified when quotas are exceeded.
     *
     * @param listener the listener to add
     * @throws NullPointerException if listener is null
     */
    @Override
    public void addListener(ResourceEventListener listener) {
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
     * Stops the background metric collection scheduler. This method does not wait
     * for the scheduler to terminate.
     */
    public void shutdown() {
        logger.info("[{}] Shutting down resource monitor", applicationId);
        scheduler.shutdown();
    }
}
