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

package org.flossware.jplatform.cluster.etcd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for etcd-based clustering.
 * Provides connection settings for etcd cluster and lease parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * EtcdConfig config = EtcdConfig.builder()
 *     .addEndpoint("http://localhost:2379")
 *     .leaseTtl(10)
 *     .build();
 * }</pre>
 *
 * @see EtcdClusterManager
 * @since 1.1
 */
public class EtcdConfig {

    private final List<String> endpoints;
    private final long leaseTtl;
    private final String namespace;
    private final String username;
    private final String password;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    EtcdConfig(Builder builder) {
        this.endpoints = Collections.unmodifiableList(new ArrayList<>(builder.endpoints));
        this.leaseTtl = builder.leaseTtl;
        this.namespace = builder.namespace;
        this.username = builder.username;
        this.password = builder.password;
    }

    /**
     * Returns the etcd endpoints.
     *
     * @return immutable list of endpoints (default: ["http://localhost:2379"])
     */
    public List<String> getEndpoints() {
        return endpoints;
    }

    /**
     * Returns the lease TTL in seconds.
     * Leases are used for leader election and will expire if not renewed.
     *
     * @return the lease TTL in seconds (default: 10)
     */
    public long getLeaseTtl() {
        return leaseTtl;
    }

    /**
     * Returns the key namespace prefix.
     *
     * @return the namespace, or null if not using namespaces
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the authentication username.
     *
     * @return the username, or null if not using authentication
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the authentication password.
     *
     * @return the password, or null if not using authentication
     */
    public String getPassword() {
        return password;
    }

    /**
     * Creates a new builder for EtcdConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for EtcdConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private final List<String> endpoints = new ArrayList<>();
        private long leaseTtl = 10;
        private String namespace;
        private String username;
        private String password;

        /**
         * Constructs a builder with default endpoint.
         */
        public Builder() {
            endpoints.add("http://localhost:2379");
        }

        /**
         * Adds an etcd endpoint.
         *
         * <p>The default endpoint "http://localhost:2379" is automatically removed
         * when adding the first non-localhost endpoint. If explicitly adding localhost,
         * it is preserved.</p>
         *
         * @param endpoint the endpoint URL (e.g., "http://localhost:2379")
         * @return this builder for chaining
         * @throws IllegalArgumentException if endpoint is null or empty
         */
        public Builder addEndpoint(String endpoint) {
            if (endpoint == null || endpoint.trim().isEmpty()) {
                throw new IllegalArgumentException("Endpoint must not be null or empty");
            }
            // Clear default localhost only when adding a different endpoint
            if (this.endpoints.size() == 1 &&
                "http://localhost:2379".equals(this.endpoints.get(0)) &&
                !"http://localhost:2379".equals(endpoint)) {
                this.endpoints.clear();
            }
            this.endpoints.add(endpoint);
            return this;
        }

        /**
         * Sets the etcd endpoints, replacing any existing ones.
         *
         * @param endpoints the list of endpoint URLs
         * @return this builder for chaining
         * @throws IllegalArgumentException if endpoints list is null or contains null/empty entries
         */
        public Builder endpoints(List<String> endpoints) {
            if (endpoints == null) {
                throw new IllegalArgumentException("Endpoints list must not be null");
            }
            for (String endpoint : endpoints) {
                if (endpoint == null || endpoint.trim().isEmpty()) {
                    throw new IllegalArgumentException("Endpoints list must not contain null or empty entries");
                }
            }
            this.endpoints.clear();
            this.endpoints.addAll(endpoints);
            return this;
        }

        /**
         * Sets the lease TTL in seconds.
         *
         * @param leaseTtl the TTL in seconds (must be at least 5)
         * @return this builder for chaining
         */
        public Builder leaseTtl(long leaseTtl) {
            if (leaseTtl < 5) {
                throw new IllegalArgumentException("Lease TTL must be at least 5 seconds");
            }
            // Prevent overflow when converted to milliseconds and ensure reasonable values
            if (leaseTtl > 86400) {  // 24 hours
                throw new IllegalArgumentException("Lease TTL too large (max 86400 seconds / 24 hours): " + leaseTtl);
            }
            this.leaseTtl = leaseTtl;
            return this;
        }

        /**
         * Sets the key namespace prefix.
         *
         * @param namespace the namespace prefix
         * @return this builder for chaining
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Sets the authentication username.
         *
         * @param username the username
         * @return this builder for chaining
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the authentication password.
         *
         * @param password the password
         * @return this builder for chaining
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Builds the EtcdConfig instance.
         *
         * @return a new EtcdConfig with the configured values
         */
        public EtcdConfig build() {
            if (endpoints.isEmpty()) {
                throw new IllegalStateException("At least one endpoint must be specified");
            }
            return new EtcdConfig(this);
        }
    }
}
