package org.flossware.jplatform.api;

import java.time.Duration;

/**
 * Resource monitor tracking application resource usage.
 * Monitors CPU time, memory usage, thread count, and enforces quotas.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ResourceMonitor monitor = context.getResourceMonitor();
 *
 * // Get current usage
 * ResourceSnapshot snapshot = monitor.getCurrentSnapshot();
 * System.out.println("CPU: " + snapshot.getCpuTimeNanos() / 1_000_000_000.0 + "s");
 * System.out.println("Memory: " + snapshot.getHeapUsedBytes() / 1024 / 1024 + " MB");
 *
 * // Set quota
 * ResourceQuota quota = ResourceQuota.builder()
 *     .maxHeapBytes(512 * 1024 * 1024)  // 512 MB
 *     .maxThreadCount(50)
 *     .build();
 * monitor.setQuota(quota);
 * }</pre>
 *
 * @see ResourceSnapshot
 * @see ResourceQuota
 */
public interface ResourceMonitor {
    /**
     * Returns the current resource usage snapshot.
     *
     * @return current resource usage
     */
    ResourceSnapshot getCurrentSnapshot();

    /**
     * Returns historical resource usage for the specified duration.
     *
     * @param duration how far back to retrieve history
     * @return resource usage history
     */
    ResourceUsageHistory getHistory(Duration duration);

    /**
     * Sets a resource quota for this application.
     * Exceeding the quota will trigger listener notifications.
     *
     * @param quota the resource quota to enforce
     */
    void setQuota(ResourceQuota quota);

    /**
     * Returns the current resource quota, if set.
     *
     * @return the resource quota, or null if not set
     */
    ResourceQuota getQuota();

    /**
     * Adds a listener to be notified of resource events.
     *
     * @param listener the listener to add
     */
    void addListener(ResourceEventListener listener);

    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(ResourceEventListener listener);
}
