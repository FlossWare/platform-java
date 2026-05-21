package org.flossware.jplatform.api;

import java.util.Optional;

/**
 * Defines resource limits for an application.
 * Quotas can be set for heap memory, thread count, and CPU time.
 * All limits are optional - only configured limits are enforced.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ResourceQuota quota = ResourceQuota.builder()
 *     .maxHeapBytes(512 * 1024 * 1024)  // 512 MB
 *     .maxThreadCount(50)
 *     .maxCpuTimeNanos(60_000_000_000L) // 60 seconds
 *     .build();
 *
 * // Enforce quota against current usage
 * try {
 *     quota.enforce(snapshot);
 * } catch (ResourceQuotaExceededException e) {
 *     // Handle quota violation
 * }
 * }</pre>
 *
 * @see ResourceSnapshot
 * @see ResourceMonitor
 * @see ResourceQuotaExceededException
 */
public class ResourceQuota {
    private final Optional<Long> maxHeapBytes;
    private final Optional<Integer> maxThreadCount;
    private final Optional<Long> maxCpuTimeNanos;

    private ResourceQuota(Builder builder) {
        this.maxHeapBytes = builder.maxHeapBytes;
        this.maxThreadCount = builder.maxThreadCount;
        this.maxCpuTimeNanos = builder.maxCpuTimeNanos;
    }

    /**
     * Returns the maximum heap memory allowed for the application.
     *
     * @return the maximum heap size in bytes, or empty if no limit is set
     */
    public Optional<Long> getMaxHeapBytes() {
        return maxHeapBytes;
    }

    /**
     * Returns the maximum number of threads allowed for the application.
     *
     * @return the maximum thread count, or empty if no limit is set
     */
    public Optional<Integer> getMaxThreadCount() {
        return maxThreadCount;
    }

    /**
     * Returns the maximum CPU time allowed for the application.
     *
     * @return the maximum CPU time in nanoseconds, or empty if no limit is set
     */
    public Optional<Long> getMaxCpuTimeNanos() {
        return maxCpuTimeNanos;
    }

    /**
     * Checks if the given resource snapshot violates any configured quotas.
     * Throws an exception if any limit is exceeded.
     *
     * @param snapshot the resource usage to check
     * @throws ResourceQuotaExceededException if any configured quota is exceeded
     */
    public void enforce(ResourceSnapshot snapshot) throws ResourceQuotaExceededException {
        maxHeapBytes.ifPresent(max -> {
            if (snapshot.getHeapUsedBytes() > max) {
                throw new ResourceQuotaExceededException(
                        "Heap quota exceeded: " + snapshot.getHeapUsedBytes() + " > " + max);
            }
        });

        maxThreadCount.ifPresent(max -> {
            if (snapshot.getThreadCount() > max) {
                throw new ResourceQuotaExceededException(
                        "Thread count quota exceeded: " + snapshot.getThreadCount() + " > " + max);
            }
        });

        maxCpuTimeNanos.ifPresent(max -> {
            if (snapshot.getCpuTimeNanos() > max) {
                throw new ResourceQuotaExceededException(
                        "CPU time quota exceeded: " + snapshot.getCpuTimeNanos() + " > " + max);
            }
        });
    }

    /**
     * Creates a new builder for constructing resource quotas.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing ResourceQuota instances.
     * All quota limits are optional.
     */
    public static class Builder {
        private Optional<Long> maxHeapBytes = Optional.empty();
        private Optional<Integer> maxThreadCount = Optional.empty();
        private Optional<Long> maxCpuTimeNanos = Optional.empty();

        /**
         * Sets the maximum heap memory allowed.
         *
         * @param bytes the maximum heap size in bytes
         * @return this builder
         */
        public Builder maxHeapBytes(long bytes) {
            this.maxHeapBytes = Optional.of(bytes);
            return this;
        }

        /**
         * Sets the maximum number of threads allowed.
         *
         * @param count the maximum thread count
         * @return this builder
         */
        public Builder maxThreadCount(int count) {
            this.maxThreadCount = Optional.of(count);
            return this;
        }

        /**
         * Sets the maximum CPU time allowed.
         *
         * @param nanos the maximum CPU time in nanoseconds
         * @return this builder
         */
        public Builder maxCpuTimeNanos(long nanos) {
            this.maxCpuTimeNanos = Optional.of(nanos);
            return this;
        }

        /**
         * Builds the ResourceQuota instance.
         *
         * @return a new ResourceQuota with the configured limits
         */
        public ResourceQuota build() {
            return new ResourceQuota(this);
        }
    }
}
