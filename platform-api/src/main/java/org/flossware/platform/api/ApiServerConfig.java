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

package org.flossware.platform.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for the platform REST API server.
 * Specifies port, bind address, authentication settings, and CORS configuration.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApiServerConfig config = ApiServerConfig.builder()
 *     .port(8080)
 *     .bindAddress("0.0.0.0")
 *     .enableAuth(true)
 *     .apiKey("secret-key")
 *     .addAllowedOrigin("https://console.example.com")
 *     .addAllowedOrigin("http://localhost:3000")  // For development only
 *     .build();
 * }</pre>
 *
 * <p><strong>CORS Security:</strong> The default CORS policy denies all origins.
 * You must explicitly whitelist origins using {@link Builder#addAllowedOrigin(String)}.
 * Never use wildcard {@code "*"} in production as it bypasses same-origin policy
 * and creates XSS/CSRF vulnerabilities.</p>
 *
 * @see PlatformApiServer
 */
public class ApiServerConfig {
    private final int port;
    private final String bindAddress;
    private final boolean enableAuth;
    private final String apiKey;
    private final String apiKeyHeader;
    private final Set<String> allowedOrigins;
    private final int threadPoolSize;
    private final int maxThreadPoolSize;

    private ApiServerConfig(Builder builder) {
        this.port = builder.port;
        this.bindAddress = builder.bindAddress;
        this.enableAuth = builder.enableAuth;
        this.apiKey = builder.apiKey;
        this.apiKeyHeader = builder.apiKeyHeader;
        this.allowedOrigins = builder.allowedOrigins != null ?
                Set.copyOf(builder.allowedOrigins) : Collections.emptySet();
        this.threadPoolSize = builder.threadPoolSize;
        this.maxThreadPoolSize = builder.maxThreadPoolSize;
    }

    /**
     * Returns the port to listen on.
     *
     * @return the server port
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the bind address.
     *
     * @return the bind address
     */
    public String getBindAddress() {
        return bindAddress;
    }

    /**
     * Checks if authentication is enabled.
     *
     * @return true if authentication is required
     */
    public boolean isEnableAuth() {
        return enableAuth;
    }

    /**
     * Returns the API key for authentication.
     *
     * @return the API key, or null if authentication is disabled
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Returns the HTTP header name for the API key.
     *
     * @return the API key header name
     */
    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    /**
     * Returns the allowed CORS origins.
     *
     * @return an unmodifiable set of allowed origins
     */
    public Set<String> getAllowedOrigins() {
        return Collections.unmodifiableSet(allowedOrigins);
    }

    /**
     * Returns the core thread pool size for handling requests.
     *
     * @return the core thread pool size
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * Returns the maximum thread pool size for handling request bursts.
     *
     * @return the maximum thread pool size
     */
    public int getMaxThreadPoolSize() {
        return maxThreadPoolSize;
    }

    /**
     * Creates a new builder for constructing API server configurations.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ApiServerConfig.
     */
    public static class Builder {
        private static final Logger logger = LoggerFactory.getLogger(Builder.class);

        private int port = 8080;
        private String bindAddress = "0.0.0.0";
        private boolean enableAuth = false;
        private String apiKey;
        private String apiKeyHeader = "X-API-Key";
        private Set<String> allowedOrigins = new HashSet<>();
        private int threadPoolSize = 20;
        private int maxThreadPoolSize = 200;

        /**
         * Sets the port to listen on.
         *
         * @param port the server port
         * @return this builder
         */
        public Builder port(int port) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException(
                    "Port must be between 0 and 65535, got: " + port);
            }
            this.port = port;
            return this;
        }

        /**
         * Sets the bind address.
         *
         * @param bindAddress the bind address
         * @return this builder
         */
        public Builder bindAddress(String bindAddress) {
            if (bindAddress == null || bindAddress.trim().isEmpty()) {
                throw new IllegalArgumentException("bindAddress cannot be null or empty");
            }
            this.bindAddress = bindAddress;
            return this;
        }

        /**
         * Sets whether authentication is required.
         *
         * @param enableAuth true to require API key authentication
         * @return this builder
         */
        public Builder enableAuth(boolean enableAuth) {
            this.enableAuth = enableAuth;
            return this;
        }

        /**
         * Sets the API key for authentication.
         *
         * @param apiKey the API key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the HTTP header name for the API key.
         *
         * @param apiKeyHeader the header name
         * @return this builder
         */
        public Builder apiKeyHeader(String apiKeyHeader) {
            if (apiKeyHeader == null || apiKeyHeader.trim().isEmpty()) {
                throw new IllegalArgumentException("apiKeyHeader cannot be null or empty");
            }
            this.apiKeyHeader = apiKeyHeader;
            return this;
        }

        /**
         * Adds an allowed CORS origin.
         *
         * <p><strong>Security Warning:</strong> Avoid using wildcard {@code "*"} as it
         * allows any website to make authenticated requests to your API, creating
         * XSS and CSRF attack vectors. Always specify exact origins including protocol
         * (e.g., {@code "https://console.example.com"}).</p>
         *
         * @param origin the origin to allow (e.g., "https://console.example.com")
         * @return this builder
         */
        public Builder addAllowedOrigin(String origin) {
            if (origin == null) {
                throw new IllegalArgumentException("origin cannot be null");
            }
            if ("*".equals(origin)) {
                logger.warn("SECURITY WARNING: Wildcard CORS origin (*) allows any website to access your API. " +
                           "This creates XSS and CSRF vulnerabilities. Use specific origins instead " +
                           "(e.g., \"https://console.example.com\").");
            }
            if (this.allowedOrigins == null) {
                this.allowedOrigins = new HashSet<>();
            }
            this.allowedOrigins.add(origin);
            return this;
        }

        /**
         * Sets all allowed CORS origins, replacing any previously added.
         *
         * @param origins the origins to allow
         * @return this builder
         */
        public Builder allowedOrigins(Set<String> origins) {
            if (origins == null) {
                throw new IllegalArgumentException("origins cannot be null");
            }
            for (String origin : origins) {
                if (origin == null) {
                    throw new IllegalArgumentException("origins cannot contain null elements");
                }
            }
            this.allowedOrigins = new HashSet<>(origins);
            return this;
        }

        /**
         * Sets the core thread pool size for handling requests.
         *
         * @param size the core thread pool size
         * @return this builder
         * @throws IllegalArgumentException if size is less than 1
         */
        public Builder threadPoolSize(int size) {
            if (size < 1) {
                throw new IllegalArgumentException(
                    "Thread pool size must be at least 1, got: " + size);
            }
            this.threadPoolSize = size;
            return this;
        }

        /**
         * Sets the maximum thread pool size for handling request bursts.
         *
         * @param size the maximum thread pool size
         * @return this builder
         * @throws IllegalArgumentException if size is less than threadPoolSize
         */
        public Builder maxThreadPoolSize(int size) {
            if (size < 1) {
                throw new IllegalArgumentException(
                    "Max thread pool size must be at least 1, got: " + size);
            }
            this.maxThreadPoolSize = size;
            return this;
        }

        /**
         * Builds the ApiServerConfig instance.
         *
         * @return a new ApiServerConfig
         * @throws IllegalStateException if enableAuth is true but apiKey is not set
         * @throws IllegalStateException if maxThreadPoolSize is less than threadPoolSize
         */
        public ApiServerConfig build() {
            if (enableAuth && (apiKey == null || apiKey.trim().isEmpty())) {
                throw new IllegalStateException(
                    "apiKey is required when enableAuth is true");
            }
            if (maxThreadPoolSize < threadPoolSize) {
                throw new IllegalStateException(
                    "Max thread pool size (" + maxThreadPoolSize +
                    ") must be >= thread pool size (" + threadPoolSize + ")");
            }
            return new ApiServerConfig(this);
        }
    }
}
