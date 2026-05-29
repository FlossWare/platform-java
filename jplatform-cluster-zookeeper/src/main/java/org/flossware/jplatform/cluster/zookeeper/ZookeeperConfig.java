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

package org.flossware.jplatform.cluster.zookeeper;

/**
 * Configuration for ZooKeeper-based clustering.
 * Provides connection settings for ZooKeeper ensemble and session parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ZookeeperConfig config = ZookeeperConfig.builder()
 *     .connectionString("localhost:2181")
 *     .sessionTimeoutMs(30000)
 *     .build();
 * }</pre>
 *
 * @see ZookeeperClusterManager
 * @since 1.1
 */
public class ZookeeperConfig {

    private final String connectionString;
    private final int sessionTimeoutMs;
    private final int connectionTimeoutMs;
    private final int baseSleepTimeMs;
    private final int maxRetries;
    private final String namespace;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    ZookeeperConfig(Builder builder) {
        this.connectionString = builder.connectionString;
        this.sessionTimeoutMs = builder.sessionTimeoutMs;
        this.connectionTimeoutMs = builder.connectionTimeoutMs;
        this.baseSleepTimeMs = builder.baseSleepTimeMs;
        this.maxRetries = builder.maxRetries;
        this.namespace = builder.namespace;
    }

    /**
     * Returns the ZooKeeper connection string.
     *
     * @return the connection string (e.g., "host1:2181,host2:2181")
     */
    public String getConnectionString() {
        return connectionString;
    }

    /**
     * Returns the session timeout in milliseconds.
     * ZooKeeper will expire the session if no heartbeat within this time.
     *
     * @return the session timeout in milliseconds (default: 30000)
     */
    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    /**
     * Returns the connection timeout in milliseconds.
     *
     * @return the connection timeout in milliseconds (default: 15000)
     */
    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Returns the base sleep time for retry policy.
     *
     * @return the base sleep time in milliseconds (default: 1000)
     */
    public int getBaseSleepTimeMs() {
        return baseSleepTimeMs;
    }

    /**
     * Returns the maximum number of retries.
     *
     * @return the max retries (default: 3)
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Returns the namespace prefix for all znodes.
     *
     * @return the namespace, or null if not using namespaces
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Creates a new builder for ZookeeperConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ZookeeperConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String connectionString = "localhost:2181";
        private int sessionTimeoutMs = 30000;
        private int connectionTimeoutMs = 15000;
        private int baseSleepTimeMs = 1000;
        private int maxRetries = 3;
        private String namespace;

        /**
         * Sets the ZooKeeper connection string.
         *
         * @param connectionString the connection string (e.g., "host1:2181,host2:2181")
         * @return this builder for chaining
         */
        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        /**
         * Sets the session timeout in milliseconds.
         *
         * @param sessionTimeoutMs the timeout in milliseconds (must be positive)
         * @return this builder for chaining
         */
        public Builder sessionTimeoutMs(int sessionTimeoutMs) {
            if (sessionTimeoutMs <= 0) {
                throw new IllegalArgumentException("Session timeout must be positive");
            }
            this.sessionTimeoutMs = sessionTimeoutMs;
            return this;
        }

        /**
         * Sets the connection timeout in milliseconds.
         *
         * @param connectionTimeoutMs the timeout in milliseconds (must be positive)
         * @return this builder for chaining
         */
        public Builder connectionTimeoutMs(int connectionTimeoutMs) {
            if (connectionTimeoutMs <= 0) {
                throw new IllegalArgumentException("Connection timeout must be positive");
            }
            this.connectionTimeoutMs = connectionTimeoutMs;
            return this;
        }

        /**
         * Sets the base sleep time for retry policy.
         *
         * @param baseSleepTimeMs the base sleep time in milliseconds (must be positive)
         * @return this builder for chaining
         */
        public Builder baseSleepTimeMs(int baseSleepTimeMs) {
            if (baseSleepTimeMs <= 0) {
                throw new IllegalArgumentException("Base sleep time must be positive");
            }
            this.baseSleepTimeMs = baseSleepTimeMs;
            return this;
        }

        /**
         * Sets the maximum number of retries.
         *
         * @param maxRetries the max retries (must be non-negative)
         * @return this builder for chaining
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("Max retries must be non-negative");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the namespace prefix for all znodes.
         *
         * @param namespace the namespace prefix
         * @return this builder for chaining
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Builds the ZookeeperConfig instance.
         *
         * @return a new ZookeeperConfig with the configured values
         */
        public ZookeeperConfig build() {
            if (connectionString == null || connectionString.trim().isEmpty()) {
                throw new IllegalStateException("Connection string must be specified");
            }
            return new ZookeeperConfig(this);
        }
    }
}
