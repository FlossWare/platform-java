package org.flossware.jplatform.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot of an application's resource usage at a point in time.
 * Captures CPU time, memory usage, thread count, I/O metrics, and custom metrics.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ResourceSnapshot snapshot = monitor.getCurrentSnapshot();
 *
 * // Get CPU time in seconds
 * double cpuSeconds = snapshot.getCpuTimeNanos() / 1_000_000_000.0;
 *
 * // Get memory in MB
 * long memoryMB = snapshot.getHeapUsedBytes() / 1024 / 1024;
 *
 * // Get thread count
 * int threads = snapshot.getThreadCount();
 * }</pre>
 *
 * @see ResourceMonitor
 */
public class ResourceSnapshot {
    private final long timestamp;
    private final long cpuTimeNanos;
    private final long heapUsedBytes;
    private final int threadCount;
    private final long bytesRead;
    private final long bytesWritten;
    private final Map<String, Object> customMetrics;

    /**
     * Constructs a new resource snapshot.
     *
     * @param timestamp the timestamp in milliseconds since epoch
     * @param cpuTimeNanos the CPU time in nanoseconds
     * @param heapUsedBytes the heap memory usage in bytes
     * @param threadCount the number of active threads
     * @param bytesRead the cumulative bytes read
     * @param bytesWritten the cumulative bytes written
     * @param customMetrics application-specific metrics, or null for none
     * @throws IllegalArgumentException if any metric value is negative or customMetrics contains null keys/values
     */
    public ResourceSnapshot(long timestamp, long cpuTimeNanos, long heapUsedBytes,
                          int threadCount, long bytesRead, long bytesWritten,
                          Map<String, Object> customMetrics) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp cannot be negative: " + timestamp);
        }
        if (cpuTimeNanos < 0) {
            throw new IllegalArgumentException("cpuTimeNanos cannot be negative: " + cpuTimeNanos);
        }
        // Allow -1 to indicate "not available", but reject other negative values
        if (heapUsedBytes < -1) {
            throw new IllegalArgumentException("heapUsedBytes cannot be negative (except -1 for N/A): " + heapUsedBytes);
        }
        if (threadCount < 0) {
            throw new IllegalArgumentException("threadCount cannot be negative: " + threadCount);
        }
        if (bytesRead < 0) {
            throw new IllegalArgumentException("bytesRead cannot be negative: " + bytesRead);
        }
        if (bytesWritten < 0) {
            throw new IllegalArgumentException("bytesWritten cannot be negative: " + bytesWritten);
        }

        this.timestamp = timestamp;
        this.cpuTimeNanos = cpuTimeNanos;
        this.heapUsedBytes = heapUsedBytes;
        this.threadCount = threadCount;
        this.bytesRead = bytesRead;
        this.bytesWritten = bytesWritten;

        // Validate and copy customMetrics
        if (customMetrics != null) {
            for (Map.Entry<String, Object> entry : customMetrics.entrySet()) {
                if (entry.getKey() == null) {
                    throw new IllegalArgumentException("customMetrics cannot contain null keys");
                }
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException(
                        "customMetrics cannot contain null values (key: " + entry.getKey() + ")");
                }
            }
            this.customMetrics = new HashMap<>(customMetrics);
        } else {
            this.customMetrics = Collections.emptyMap();
        }
    }

    /**
     * Returns the timestamp when this snapshot was captured.
     *
     * @return the timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the cumulative CPU time consumed by the application.
     *
     * @return the CPU time in nanoseconds
     */
    public long getCpuTimeNanos() {
        return cpuTimeNanos;
    }

    /**
     * Returns the amount of heap memory currently in use.
     *
     * @return the heap usage in bytes
     */
    public long getHeapUsedBytes() {
        return heapUsedBytes;
    }

    /**
     * Returns the number of active threads in the application.
     *
     * @return the thread count
     */
    public int getThreadCount() {
        return threadCount;
    }

    /**
     * Returns the cumulative number of bytes read by the application.
     *
     * @return the bytes read
     */
    public long getBytesRead() {
        return bytesRead;
    }

    /**
     * Returns the cumulative number of bytes written by the application.
     *
     * @return the bytes written
     */
    public long getBytesWritten() {
        return bytesWritten;
    }

    /**
     * Returns custom application-specific metrics captured in this snapshot.
     *
     * @return an unmodifiable map of custom metric names to their values
     */
    public Map<String, Object> getCustomMetrics() {
        return Collections.unmodifiableMap(customMetrics);
    }

    @Override
    public String toString() {
        return String.format("ResourceSnapshot{timestamp=%d, cpu=%dns, heap=%dB, threads=%d, read=%dB, written=%dB}",
                timestamp, cpuTimeNanos, heapUsedBytes, threadCount, bytesRead, bytesWritten);
    }
}
