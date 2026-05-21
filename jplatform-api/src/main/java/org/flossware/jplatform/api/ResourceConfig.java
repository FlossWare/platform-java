package org.flossware.jplatform.api;

import java.util.Optional;

/**
 * Configuration for application resource limits.
 * Uses convenient units (MB, seconds) compared to ResourceQuota (bytes, nanos).
 * All limits are optional - unconfigured resources have no limits.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ResourceConfig config = ResourceConfig.builder()
 *     .maxHeapMB(512)
 *     .maxThreads(50)
 *     .maxCpuTimeSeconds(60)
 *     .build();
 *
 * // Or create unlimited config
 * ResourceConfig unlimited = ResourceConfig.unlimited();
 * }</pre>
 *
 * @see ResourceQuota
 * @see ResourceMonitor
 */
public class ResourceConfig {
    private final Optional<Long> maxHeapMB;
    private final Optional<Integer> maxThreads;
    private final Optional<Long> maxCpuTimeSeconds;

    private ResourceConfig(Builder builder) {
        this.maxHeapMB = builder.maxHeapMB;
        this.maxThreads = builder.maxThreads;
        this.maxCpuTimeSeconds = builder.maxCpuTimeSeconds;
    }

    /**
     * Returns the maximum heap memory allowed for the application.
     *
     * @return the maximum heap size in megabytes, or empty if unlimited
     */
    public Optional<Long> getMaxHeapMB() {
        return maxHeapMB;
    }

    /**
     * Returns the maximum number of threads allowed for the application.
     *
     * @return the maximum thread count, or empty if unlimited
     */
    public Optional<Integer> getMaxThreads() {
        return maxThreads;
    }

    /**
     * Returns the maximum CPU time allowed for the application.
     *
     * @return the maximum CPU time in seconds, or empty if unlimited
     */
    public Optional<Long> getMaxCpuTimeSeconds() {
        return maxCpuTimeSeconds;
    }

    /**
     * Creates a resource configuration with no limits.
     *
     * @return a configuration allowing unlimited resource usage
     */
    public static ResourceConfig unlimited() {
        return builder().build();
    }

    /**
     * Creates a new builder for constructing resource configurations.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing ResourceConfig instances.
     * All resource limits are optional.
     */
    public static class Builder {
        private Optional<Long> maxHeapMB = Optional.empty();
        private Optional<Integer> maxThreads = Optional.empty();
        private Optional<Long> maxCpuTimeSeconds = Optional.empty();

        /**
         * Sets the maximum heap memory allowed.
         *
         * @param mb the maximum heap size in megabytes
         * @return this builder
         */
        public Builder maxHeapMB(long mb) {
            this.maxHeapMB = Optional.of(mb);
            return this;
        }

        /**
         * Sets the maximum number of threads allowed.
         *
         * @param threads the maximum thread count
         * @return this builder
         */
        public Builder maxThreads(int threads) {
            this.maxThreads = Optional.of(threads);
            return this;
        }

        /**
         * Sets the maximum CPU time allowed.
         *
         * @param seconds the maximum CPU time in seconds
         * @return this builder
         */
        public Builder maxCpuTimeSeconds(long seconds) {
            this.maxCpuTimeSeconds = Optional.of(seconds);
            return this;
        }

        /**
         * Builds the ResourceConfig instance.
         *
         * @return a new ResourceConfig with the configured limits
         */
        public ResourceConfig build() {
            return new ResourceConfig(this);
        }
    }
}
