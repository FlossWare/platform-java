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

package org.flossware.jplatform.config.vault;

/**
 * Configuration for Vault-based configuration source.
 * Provides connection settings for HashiCorp Vault and configuration parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * VaultConfigSourceConfig config = VaultConfigSourceConfig.builder()
 *     .address("http://localhost:8200")
 *     .token("s.1234567890")
 *     .secretPath("secret/myapp")
 *     .build();
 * }</pre>
 *
 * @see VaultConfigSource
 * @since 1.1
 */
public class VaultConfigSourceConfig {

    private final String address;
    private final String token;
    private final String secretPath;
    private final String namespace;
    private final int maxRetries;
    private final int retryIntervalMs;
    private final int openTimeout;
    private final int readTimeout;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    VaultConfigSourceConfig(Builder builder) {
        this.address = builder.address;
        this.token = builder.token;
        this.secretPath = builder.secretPath;
        this.namespace = builder.namespace;
        this.maxRetries = builder.maxRetries;
        this.retryIntervalMs = builder.retryIntervalMs;
        this.openTimeout = builder.openTimeout;
        this.readTimeout = builder.readTimeout;
    }

    /**
     * Returns the Vault address.
     *
     * @return the address (default: "http://localhost:8200")
     */
    public String getAddress() {
        return address;
    }

    /**
     * Returns the Vault authentication token.
     *
     * @return the token
     */
    public String getToken() {
        return token;
    }

    /**
     * Returns the secret path.
     *
     * @return the secret path (default: "secret/config")
     */
    public String getSecretPath() {
        return secretPath;
    }

    /**
     * Returns the Vault namespace.
     *
     * @return the namespace, or null if not using namespaces
     */
    public String getNamespace() {
        return namespace;
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
     * Returns the retry interval in milliseconds.
     *
     * @return the retry interval (default: 1000)
     */
    public int getRetryIntervalMs() {
        return retryIntervalMs;
    }

    /**
     * Returns the connection open timeout in seconds.
     *
     * @return the open timeout (default: 5)
     */
    public int getOpenTimeout() {
        return openTimeout;
    }

    /**
     * Returns the read timeout in seconds.
     *
     * @return the read timeout (default: 30)
     */
    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Creates a new builder for VaultConfigSourceConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for VaultConfigSourceConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String address = "http://localhost:8200";
        private String token;
        private String secretPath = "secret/config";
        private String namespace;
        private int maxRetries = 3;
        private int retryIntervalMs = 1000;
        private int openTimeout = 5;
        private int readTimeout = 30;

        /**
         * Sets the Vault address.
         *
         * @param address the Vault server address (e.g., "https://vault.example.com:8200")
         * @return this builder for chaining
         */
        public Builder address(String address) {
            this.address = address;
            return this;
        }

        /**
         * Sets the Vault authentication token.
         *
         * @param token the authentication token
         * @return this builder for chaining
         */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * Sets the secret path.
         *
         * @param secretPath the path to secrets (e.g., "secret/myapp")
         * @return this builder for chaining
         */
        public Builder secretPath(String secretPath) {
            this.secretPath = secretPath;
            return this;
        }

        /**
         * Sets the Vault namespace (Vault Enterprise feature).
         *
         * @param namespace the namespace
         * @return this builder for chaining
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Sets the maximum number of retries.
         *
         * @param maxRetries the max retries (must be at least 0)
         * @return this builder for chaining
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("Max retries must be at least 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the retry interval in milliseconds.
         *
         * @param retryIntervalMs the interval in milliseconds (must be at least 100)
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
         * Sets the connection open timeout.
         *
         * @param openTimeout the timeout in seconds (must be at least 1)
         * @return this builder for chaining
         */
        public Builder openTimeout(int openTimeout) {
            if (openTimeout < 1) {
                throw new IllegalArgumentException("Open timeout must be at least 1 second");
            }
            this.openTimeout = openTimeout;
            return this;
        }

        /**
         * Sets the read timeout.
         *
         * @param readTimeout the timeout in seconds (must be at least 1)
         * @return this builder for chaining
         */
        public Builder readTimeout(int readTimeout) {
            if (readTimeout < 1) {
                throw new IllegalArgumentException("Read timeout must be at least 1 second");
            }
            this.readTimeout = readTimeout;
            return this;
        }

        /**
         * Builds the VaultConfigSourceConfig instance.
         *
         * @return a new VaultConfigSourceConfig with the configured values
         * @throws IllegalStateException if required fields are missing
         */
        public VaultConfigSourceConfig build() {
            if (address == null || address.trim().isEmpty()) {
                throw new IllegalStateException("Address must be specified");
            }
            if (token == null || token.trim().isEmpty()) {
                throw new IllegalStateException("Token must be specified");
            }
            if (secretPath == null || secretPath.trim().isEmpty()) {
                throw new IllegalStateException("Secret path must be specified");
            }
            return new VaultConfigSourceConfig(this);
        }
    }
}
