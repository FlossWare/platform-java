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

package org.flossware.jplatform.config.etcd;

/**
 * Configuration for Etcd-based configuration source.
 * Provides connection settings for etcd cluster and configuration parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder()
 *     .endpoints("http://localhost:2379")
 *     .keyPrefix("/config/myapp")
 *     .build();
 * }</pre>
 *
 * @see EtcdConfigSource
 * @since 1.1
 */
public class EtcdConfigSourceConfig {

    private final String endpoints;
    private final String keyPrefix;
    private final String username;
    private final String password;
    private final boolean watchEnabled;
    private final long watchRetryDelaySeconds;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    EtcdConfigSourceConfig(Builder builder) {
        this.endpoints = builder.endpoints;
        this.keyPrefix = builder.keyPrefix;
        this.username = builder.username;
        this.password = builder.password;
        this.watchEnabled = builder.watchEnabled;
        this.watchRetryDelaySeconds = builder.watchRetryDelaySeconds;
    }

    /**
     * Returns the etcd endpoints.
     *
     * @return comma-separated list of endpoints (default: "http://localhost:2379")
     */
    public String getEndpoints() {
        return endpoints;
    }

    /**
     * Returns the key prefix for configuration keys.
     *
     * @return the key prefix (default: "/config")
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Returns the authentication username.
     *
     * @return the username, or null if not using auth
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the authentication password.
     *
     * @return the password, or null if not using auth
     */
    public String getPassword() {
        return password;
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
     * Returns the watch retry delay in seconds.
     *
     * @return the retry delay (default: 5)
     */
    public long getWatchRetryDelaySeconds() {
        return watchRetryDelaySeconds;
    }

    /**
     * Creates a new builder for EtcdConfigSourceConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for EtcdConfigSourceConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String endpoints = "http://localhost:2379";
        private String keyPrefix = "/config";
        private String username;
        private String password;
        private boolean watchEnabled = true;
        private long watchRetryDelaySeconds = 5;

        /**
         * Sets the etcd endpoints.
         *
         * @param endpoints comma-separated list of endpoints (e.g., "http://etcd1:2379,http://etcd2:2379")
         * @return this builder for chaining
         */
        public Builder endpoints(String endpoints) {
            this.endpoints = endpoints;
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
         * Sets the authentication username.
         *
         * @param username the username for etcd authentication
         * @return this builder for chaining
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the authentication password.
         *
         * @param password the password for etcd authentication
         * @return this builder for chaining
         */
        public Builder password(String password) {
            this.password = password;
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
         * Sets the watch retry delay.
         *
         * @param watchRetryDelaySeconds the delay in seconds (must be at least 1)
         * @return this builder for chaining
         */
        public Builder watchRetryDelaySeconds(long watchRetryDelaySeconds) {
            if (watchRetryDelaySeconds < 1) {
                throw new IllegalArgumentException("Watch retry delay must be at least 1 second");
            }
            this.watchRetryDelaySeconds = watchRetryDelaySeconds;
            return this;
        }

        /**
         * Builds the EtcdConfigSourceConfig instance.
         *
         * @return a new EtcdConfigSourceConfig with the configured values
         * @throws IllegalStateException if required fields are missing
         */
        public EtcdConfigSourceConfig build() {
            if (endpoints == null || endpoints.trim().isEmpty()) {
                throw new IllegalStateException("Endpoints must be specified");
            }
            if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
                throw new IllegalStateException("Key prefix must be specified");
            }
            return new EtcdConfigSourceConfig(this);
        }
    }
}
