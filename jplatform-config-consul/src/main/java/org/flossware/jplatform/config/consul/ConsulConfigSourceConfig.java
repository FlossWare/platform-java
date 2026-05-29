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

package org.flossware.jplatform.config.consul;

/**
 * Configuration for Consul-based configuration source.
 * Provides connection settings for Consul agent and configuration parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ConsulConfigSourceConfig config = ConsulConfigSourceConfig.builder()
 *     .host("localhost")
 *     .port(8500)
 *     .keyPrefix("config/myapp")
 *     .build();
 * }</pre>
 *
 * @see ConsulConfigSource
 * @since 1.1
 */
public class ConsulConfigSourceConfig {

    private final String host;
    private final int port;
    private final String token;
    private final String keyPrefix;
    private final boolean watchEnabled;
    private final long watchIntervalSeconds;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    ConsulConfigSourceConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.token = builder.token;
        this.keyPrefix = builder.keyPrefix;
        this.watchEnabled = builder.watchEnabled;
        this.watchIntervalSeconds = builder.watchIntervalSeconds;
    }

    /**
     * Returns the Consul host.
     *
     * @return the host (default: "localhost")
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the Consul port.
     *
     * @return the port (default: 8500)
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the Consul ACL token.
     *
     * @return the token, or null if not using ACL
     */
    public String getToken() {
        return token;
    }

    /**
     * Returns the key prefix for configuration keys.
     *
     * @return the key prefix (default: "config")
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Returns whether configuration watching is enabled.
     *
     * @return true if watching is enabled (default: true)
     */
    public boolean isWatchEnabled() {
        return watchEnabled;
    }

    /**
     * Returns the watch polling interval in seconds.
     *
     * @return the watch interval (default: 10)
     */
    public long getWatchIntervalSeconds() {
        return watchIntervalSeconds;
    }

    /**
     * Creates a new builder for ConsulConfigSourceConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ConsulConfigSourceConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String host = "localhost";
        private int port = 8500;
        private String token;
        private String keyPrefix = "config";
        private boolean watchEnabled = true;
        private long watchIntervalSeconds = 10;

        /**
         * Sets the Consul host.
         *
         * @param host the host
         * @return this builder for chaining
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the Consul port.
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
         * Sets the Consul ACL token.
         *
         * @param token the ACL token
         * @return this builder for chaining
         */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * Sets the key prefix.
         *
         * @param keyPrefix the prefix for configuration keys
         * @return this builder for chaining
         */
        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        /**
         * Sets whether to enable configuration watching.
         *
         * @param watchEnabled true to enable watching
         * @return this builder for chaining
         */
        public Builder watchEnabled(boolean watchEnabled) {
            this.watchEnabled = watchEnabled;
            return this;
        }

        /**
         * Sets the watch polling interval.
         *
         * @param watchIntervalSeconds the interval in seconds (must be at least 1)
         * @return this builder for chaining
         */
        public Builder watchIntervalSeconds(long watchIntervalSeconds) {
            if (watchIntervalSeconds < 1) {
                throw new IllegalArgumentException("Watch interval must be at least 1 second");
            }
            this.watchIntervalSeconds = watchIntervalSeconds;
            return this;
        }

        /**
         * Builds the ConsulConfigSourceConfig instance.
         *
         * @return a new ConsulConfigSourceConfig with the configured values
         * @throws IllegalStateException if required fields are missing
         */
        public ConsulConfigSourceConfig build() {
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalStateException("Host must be specified");
            }
            if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
                throw new IllegalStateException("Key prefix must be specified");
            }
            return new ConsulConfigSourceConfig(this);
        }
    }
}
