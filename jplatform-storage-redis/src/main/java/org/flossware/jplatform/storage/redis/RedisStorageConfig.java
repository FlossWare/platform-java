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

package org.flossware.jplatform.storage.redis;

/**
 * Configuration for Redis-based storage.
 * Stores volume metadata in Redis key-value store.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * RedisStorageConfig config = RedisStorageConfig.builder()
 *     .host("localhost")
 *     .port(6379)
 *     .build();
 * }</pre>
 *
 * @see RedisVolumeManager
 * @since 1.1
 */
public class RedisStorageConfig {

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final int connectionTimeout;
    private final int socketTimeout;
    private final String keyPrefix;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    RedisStorageConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.password = builder.password;
        this.database = builder.database;
        this.connectionTimeout = builder.connectionTimeout;
        this.socketTimeout = builder.socketTimeout;
        this.keyPrefix = builder.keyPrefix;
    }

    /**
     * Returns the Redis host.
     *
     * @return the host (default: "localhost")
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the Redis port.
     *
     * @return the port (default: 6379)
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the Redis password.
     *
     * @return the password, or null if not using authentication
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the Redis database number.
     *
     * @return the database (default: 0)
     */
    public int getDatabase() {
        return database;
    }

    /**
     * Returns the connection timeout in milliseconds.
     *
     * @return the connection timeout (default: 2000)
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Returns the socket timeout in milliseconds.
     *
     * @return the socket timeout (default: 2000)
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * Returns the key prefix for all Redis keys.
     *
     * @return the key prefix (default: "jplatform:volumes:")
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Creates a new builder for RedisStorageConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RedisStorageConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        private int connectionTimeout = 2000;
        private int socketTimeout = 2000;
        private String keyPrefix = "jplatform:volumes:";

        /**
         * Sets the Redis host.
         *
         * @param host the host
         * @return this builder for chaining
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the Redis port.
         *
         * @param port the port (must be between 1 and 65535)
         * @return this builder for chaining
         */
        public Builder port(int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            this.port = port;
            return this;
        }

        /**
         * Sets the Redis password.
         *
         * @param password the password
         * @return this builder for chaining
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the Redis database number.
         *
         * @param database the database (must be at least 0)
         * @return this builder for chaining
         */
        public Builder database(int database) {
            if (database < 0) {
                throw new IllegalArgumentException("Database must be at least 0");
            }
            this.database = database;
            return this;
        }

        /**
         * Sets the connection timeout.
         *
         * @param connectionTimeout the timeout in milliseconds (must be at least 100)
         * @return this builder for chaining
         */
        public Builder connectionTimeout(int connectionTimeout) {
            if (connectionTimeout < 100) {
                throw new IllegalArgumentException("Connection timeout must be at least 100ms");
            }
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        /**
         * Sets the socket timeout.
         *
         * @param socketTimeout the timeout in milliseconds (must be at least 100)
         * @return this builder for chaining
         */
        public Builder socketTimeout(int socketTimeout) {
            if (socketTimeout < 100) {
                throw new IllegalArgumentException("Socket timeout must be at least 100ms");
            }
            this.socketTimeout = socketTimeout;
            return this;
        }

        /**
         * Sets the key prefix.
         *
         * @param keyPrefix the prefix for Redis keys
         * @return this builder for chaining
         */
        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        /**
         * Builds the RedisStorageConfig instance.
         *
         * @return a new RedisStorageConfig with the configured values
         * @throws IllegalStateException if required fields are missing
         */
        public RedisStorageConfig build() {
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalStateException("Host must be specified");
            }
            return new RedisStorageConfig(this);
        }
    }
}
