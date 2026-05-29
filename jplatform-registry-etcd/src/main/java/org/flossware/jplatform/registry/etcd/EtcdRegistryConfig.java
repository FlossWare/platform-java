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

package org.flossware.jplatform.registry.etcd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for etcd-based service registry.
 * Provides connection settings for etcd cluster and service registration parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * EtcdRegistryConfig config = EtcdRegistryConfig.builder()
 *     .addEndpoint("http://localhost:2379")
 *     .leaseTtl(30)
 *     .build();
 * }</pre>
 *
 * @see EtcdServiceRegistry
 * @since 1.1
 */
public class EtcdRegistryConfig {

    private final List<String> endpoints;
    private final long leaseTtl;
    private final String namespace;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    EtcdRegistryConfig(Builder builder) {
        this.endpoints = Collections.unmodifiableList(new ArrayList<>(builder.endpoints));
        this.leaseTtl = builder.leaseTtl;
        this.namespace = builder.namespace;
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
     * Service registrations expire if not renewed within this time.
     *
     * @return the lease TTL in seconds (default: 30)
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
     * Creates a new builder for EtcdRegistryConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for EtcdRegistryConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private final List<String> endpoints = new ArrayList<>();
        private long leaseTtl = 30;
        private String namespace;

        /**
         * Constructs a builder with default endpoint.
         */
        public Builder() {
            endpoints.add("http://localhost:2379");
        }

        /**
         * Adds an etcd endpoint.
         *
         * @param endpoint the endpoint URL (e.g., "http://localhost:2379")
         * @return this builder for chaining
         */
        public Builder addEndpoint(String endpoint) {
            if (this.endpoints.size() == 1 && "http://localhost:2379".equals(this.endpoints.get(0))) {
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
         */
        public Builder endpoints(List<String> endpoints) {
            this.endpoints.clear();
            this.endpoints.addAll(endpoints);
            return this;
        }

        /**
         * Sets the lease TTL in seconds.
         *
         * @param leaseTtl the TTL in seconds (must be at least 10)
         * @return this builder for chaining
         */
        public Builder leaseTtl(long leaseTtl) {
            if (leaseTtl < 10) {
                throw new IllegalArgumentException("Lease TTL must be at least 10 seconds");
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
         * Builds the EtcdRegistryConfig instance.
         *
         * @return a new EtcdRegistryConfig with the configured values
         */
        public EtcdRegistryConfig build() {
            if (endpoints.isEmpty()) {
                throw new IllegalStateException("At least one endpoint must be specified");
            }
            return new EtcdRegistryConfig(this);
        }
    }
}
