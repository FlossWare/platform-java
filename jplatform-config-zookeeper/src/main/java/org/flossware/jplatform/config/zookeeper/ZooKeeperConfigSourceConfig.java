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

package org.flossware.jplatform.config.zookeeper;

/**
 * Configuration for ZooKeeper-based configuration source.
 * Provides connection settings for ZooKeeper ensemble and configuration parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ZooKeeperConfigSourceConfig config = ZooKeeperConfigSourceConfig.builder()
 *     .connectString("localhost:2181")
 *     .basePath("/config/myapp")
 *     .build();
 * }</pre>
 *
 * @see ZooKeeperConfigSource
 * @since 1.1
 */
public class ZooKeeperConfigSourceConfig {

    private final String connectString;
    private final String basePath;
    private final int sessionTimeoutMs;
    private final int connectionTimeoutMs;
    private final int retryCount;
    private final int retryIntervalMs;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    ZooKeeperConfigSourceConfig(Builder builder) {
        this.connectString = builder.connectString;
        this.basePath = builder.basePath;
        this.sessionTimeoutMs = builder.sessionTimeoutMs;
        this.connectionTimeoutMs = builder.connectionTimeoutMs;
        this.retryCount = builder.retryCount;
        this.retryIntervalMs = builder.retryIntervalMs;
    }

    /**
     * Returns the ZooKeeper connection string.
     *
     * @return the connection string (e.g., "host1:2181,host2:2181")
     */
    public String getConnectString() {
        return connectString;
    }

    /**
     * Returns the base path for configuration keys.
     * All configuration keys will be stored under this path.
     *
     * @return the base path (default: "/config")
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * Returns the session timeout in milliseconds.
     *
     * @return the session timeout (default: 60000)
     */
    public int getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    /**
     * Returns the connection timeout in milliseconds.
     *
     * @return the connection timeout (default: 15000)
     */
    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Returns the number of connection retry attempts.
     *
     * @return the retry count (default: 3)
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * Returns the interval between retry attempts in milliseconds.
     *
     * @return the retry interval (default: 1000)
     */
    public int getRetryIntervalMs() {
        return retryIntervalMs;
    }

    /**
     * Creates a new builder for ZooKeeperConfigSourceConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ZooKeeperConfigSourceConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String connectString = "localhost:2181";
        private String basePath = "/config";
        private int sessionTimeoutMs = 60000;
        private int connectionTimeoutMs = 15000;
        private int retryCount = 3;
        private int retryIntervalMs = 1000;

        /**
         * Sets the ZooKeeper connection string.
         *
         * @param connectString the connection string (e.g., "host1:2181,host2:2181")
         * @return this builder for chaining
         */
        public Builder connectString(String connectString) {
            this.connectString = connectString;
            return this;
        }

        /**
         * Sets the base path for configuration keys.
         *
         * @param basePath the base path (e.g., "/config/myapp")
         * @return this builder for chaining
         */
        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        /**
         * Sets the session timeout in milliseconds.
         *
         * @param sessionTimeoutMs the timeout (must be at least 1000)
         * @return this builder for chaining
         */
        public Builder sessionTimeoutMs(int sessionTimeoutMs) {
            if (sessionTimeoutMs < 1000) {
                throw new IllegalArgumentException("Session timeout must be at least 1000ms");
            }
            this.sessionTimeoutMs = sessionTimeoutMs;
            return this;
        }

        /**
         * Sets the connection timeout in milliseconds.
         *
         * @param connectionTimeoutMs the timeout (must be at least 1000)
         * @return this builder for chaining
         */
        public Builder connectionTimeoutMs(int connectionTimeoutMs) {
            if (connectionTimeoutMs < 1000) {
                throw new IllegalArgumentException("Connection timeout must be at least 1000ms");
            }
            this.connectionTimeoutMs = connectionTimeoutMs;
            return this;
        }

        /**
         * Sets the number of connection retry attempts.
         *
         * @param retryCount the retry count (must be at least 0)
         * @return this builder for chaining
         */
        public Builder retryCount(int retryCount) {
            if (retryCount < 0) {
                throw new IllegalArgumentException("Retry count must be at least 0");
            }
            this.retryCount = retryCount;
            return this;
        }

        /**
         * Sets the interval between retry attempts.
         *
         * @param retryIntervalMs the interval (must be at least 100)
         * @return this builder for chaining
         */
        public Builder retryIntervalMs(int retryIntervalMs) {
            if (retryIntervalMs < 100) {
                throw new IllegalArgumentException("Retry interval must be at least 100ms");
            }
            this.retryIntervalMs = retryIntervalMs;
            return this;
        }

        /**
         * Builds the ZooKeeperConfigSourceConfig instance.
         *
         * @return a new ZooKeeperConfigSourceConfig with the configured values
         * @throws IllegalStateException if required fields are missing
         */
        public ZooKeeperConfigSourceConfig build() {
            if (connectString == null || connectString.trim().isEmpty()) {
                throw new IllegalStateException("Connect string must be specified");
            }
            if (basePath == null || basePath.trim().isEmpty()) {
                throw new IllegalStateException("Base path must be specified");
            }
            return new ZooKeeperConfigSourceConfig(this);
        }
    }
}
