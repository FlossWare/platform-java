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
         * @throws IllegalArgumentException if corePoolSize is negative
         */
        public Builder corePoolSize(int corePoolSize) {
            if (corePoolSize < 0) {
                throw new IllegalArgumentException("corePoolSize must be >= 0, got: " + corePoolSize);
            }
            this.corePoolSize = corePoolSize;
            return this;
        }

        /**
         * Sets the maximum pool size.
         *
         * @param maxPoolSize the maximum number of threads allowed in the pool
         * @return this builder
         * @throws IllegalArgumentException if maxPoolSize is <= 0
         */
        public Builder maxPoolSize(int maxPoolSize) {
            if (maxPoolSize <= 0) {
                throw new IllegalArgumentException("maxPoolSize must be > 0, got: " + maxPoolSize);
            }
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        /**
         * Sets the keep-alive time for excess idle threads.
         *
         * @param keepAliveTimeSeconds the time in seconds that excess idle threads wait before terminating
         * @return this builder
         * @throws IllegalArgumentException if keepAliveTimeSeconds is negative
         */
        public Builder keepAliveTimeSeconds(long keepAliveTimeSeconds) {
            if (keepAliveTimeSeconds < 0) {
                throw new IllegalArgumentException("keepAliveTimeSeconds must be >= 0, got: " + keepAliveTimeSeconds);
            }
            this.keepAliveTimeSeconds = keepAliveTimeSeconds;
            return this;
        }

        /**
         * Sets the task queue capacity.
         *
         * @param queueCapacity the maximum number of tasks that can be queued
         * @return this builder
         * @throws IllegalArgumentException if queueCapacity is negative
         */
        public Builder queueCapacity(int queueCapacity) {
            if (queueCapacity < 0) {
                throw new IllegalArgumentException("queueCapacity must be >= 0, got: " + queueCapacity);
            }
            this.queueCapacity = queueCapacity;
            return this;
        }

        /**
         * Builds the ThreadPoolConfig instance.
         *
         * @return a new ThreadPoolConfig with the configured values
         * @throws IllegalArgumentException if any parameter is invalid
         */
        public ThreadPoolConfig build() {
            if (corePoolSize < 0) {
                throw new IllegalArgumentException("corePoolSize must be >= 0, got: " + corePoolSize);
            }
            if (maxPoolSize <= 0) {
                throw new IllegalArgumentException("maxPoolSize must be > 0, got: " + maxPoolSize);
            }
            if (maxPoolSize < corePoolSize) {
                throw new IllegalArgumentException(
                    "maxPoolSize (" + maxPoolSize + ") must be >= corePoolSize (" + corePoolSize + ")");
            }
            if (keepAliveTimeSeconds < 0) {
                throw new IllegalArgumentException("keepAliveTimeSeconds must be >= 0, got: " + keepAliveTimeSeconds);
            }
            if (queueCapacity < 0) {
                throw new IllegalArgumentException("queueCapacity must be >= 0, got: " + queueCapacity);
            }
            return new ThreadPoolConfig(this);
        }
    }
}
