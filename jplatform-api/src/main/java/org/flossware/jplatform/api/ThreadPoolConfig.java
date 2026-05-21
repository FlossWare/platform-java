package org.flossware.jplatform.api;

/**
 * Configuration for application thread pool.
 * Defines core pool size, maximum pool size, keep-alive time, and queue capacity.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ThreadPoolConfig config = ThreadPoolConfig.builder()
 *     .corePoolSize(5)
 *     .maxPoolSize(20)
 *     .keepAliveTimeSeconds(120)
 *     .queueCapacity(200)
 *     .build();
 *
 * // Or use default configuration
 * ThreadPoolConfig defaults = ThreadPoolConfig.defaultConfig();
 * // core=2, max=10, keepAlive=60s, queue=100
 * }</pre>
 *
 * @see ThreadPoolExecutor
 * @see ThreadPoolStats
 */
public class ThreadPoolConfig {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTimeSeconds;
    private final int queueCapacity;

    private ThreadPoolConfig(Builder builder) {
        this.corePoolSize = builder.corePoolSize;
        this.maxPoolSize = builder.maxPoolSize;
        this.keepAliveTimeSeconds = builder.keepAliveTimeSeconds;
        this.queueCapacity = builder.queueCapacity;
    }

    /**
     * Returns the minimum number of threads to keep in the pool.
     *
     * @return the core pool size
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Returns the maximum number of threads allowed in the pool.
     *
     * @return the maximum pool size
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * Returns the time that excess idle threads wait before terminating.
     *
     * @return the keep-alive time in seconds
     */
    public long getKeepAliveTimeSeconds() {
        return keepAliveTimeSeconds;
    }

    /**
     * Returns the maximum number of tasks that can be queued for execution.
     *
     * @return the queue capacity
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * Creates a thread pool configuration with default settings.
     * Defaults: core=2, max=10, keepAlive=60s, queue=100
     *
     * @return a configuration with default values
     */
    public static ThreadPoolConfig defaultConfig() {
        return builder().build();
    }

    /**
     * Creates a new builder for constructing thread pool configurations.
     *
     * @return a new builder instance with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing ThreadPoolConfig instances.
     * All properties have default values.
     */
    public static class Builder {
        private int corePoolSize = 2;
        private int maxPoolSize = 10;
        private long keepAliveTimeSeconds = 60;
        private int queueCapacity = 100;

        /**
         * Sets the core pool size.
         *
         * @param corePoolSize the minimum number of threads to keep in the pool
         * @return this builder
         */
        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        /**
         * Sets the maximum pool size.
         *
         * @param maxPoolSize the maximum number of threads allowed in the pool
         * @return this builder
         */
        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        /**
         * Sets the keep-alive time for excess idle threads.
         *
         * @param keepAliveTimeSeconds the time in seconds that excess idle threads wait before terminating
         * @return this builder
         */
        public Builder keepAliveTimeSeconds(long keepAliveTimeSeconds) {
            this.keepAliveTimeSeconds = keepAliveTimeSeconds;
            return this;
        }

        /**
         * Sets the task queue capacity.
         *
         * @param queueCapacity the maximum number of tasks that can be queued
         * @return this builder
         */
        public Builder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        /**
         * Builds the ThreadPoolConfig instance.
         *
         * @return a new ThreadPoolConfig with the configured values
         */
        public ThreadPoolConfig build() {
            return new ThreadPoolConfig(this);
        }
    }
}
