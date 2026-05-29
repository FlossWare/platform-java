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

package org.flossware.jplatform.cluster.consul;

/**
 * Configuration for Consul-based clustering.
 * Provides connection settings for Consul agent and session parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ConsulConfig config = ConsulConfig.builder()
 *     .consulHost("localhost")
 *     .consulPort(8500)
 *     .sessionTtl(10)
 *     .serviceName("jplatform-cluster")
 *     .build();
 * }</pre>
 *
 * @see ConsulClusterManager
 * @since 1.1
 */
public class ConsulConfig {

    private final String consulHost;
    private final int consulPort;
    private final int sessionTtl;
    private final String serviceName;
    private final String datacenter;
    private final String token;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    ConsulConfig(Builder builder) {
        this.consulHost = builder.consulHost;
        this.consulPort = builder.consulPort;
        this.sessionTtl = builder.sessionTtl;
        this.serviceName = builder.serviceName;
        this.datacenter = builder.datacenter;
        this.token = builder.token;
    }

    /**
     * Returns the Consul host address.
     *
     * @return the host address (default: "localhost")
     */
    public String getConsulHost() {
        return consulHost;
    }

    /**
     * Returns the Consul HTTP API port.
     *
     * @return the port number (default: 8500)
     */
    public int getConsulPort() {
        return consulPort;
    }

    /**
     * Returns the session TTL in seconds.
     * Sessions are used for leader election and will expire if not renewed.
     *
     * @return the session TTL in seconds (default: 10)
     */
    public int getSessionTtl() {
        return sessionTtl;
    }

    /**
     * Returns the service name for cluster membership.
     *
     * @return the service name (default: "jplatform-cluster")
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Returns the Consul datacenter name.
     *
     * @return the datacenter name, or null if using default
     */
    public String getDatacenter() {
        return datacenter;
    }

    /**
     * Returns the Consul ACL token.
     *
     * @return the ACL token, or null if not using ACLs
     */
    public String getToken() {
        return token;
    }

    /**
     * Creates a new builder for ConsulConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ConsulConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String consulHost = "localhost";
        private int consulPort = 8500;
        private int sessionTtl = 10;
        private String serviceName = "jplatform-cluster";
        private String datacenter;
        private String token;

        /**
         * Sets the Consul host address.
         *
         * @param consulHost the host address
         * @return this builder for chaining
         * @throws IllegalArgumentException if consulHost is null or empty
         */
        public Builder consulHost(String consulHost) {
            if (consulHost == null || consulHost.trim().isEmpty()) {
                throw new IllegalArgumentException("ConsulHost must not be null or empty");
            }
            this.consulHost = consulHost;
            return this;
        }

        /**
         * Sets the Consul HTTP API port.
         *
         * @param consulPort the port number
         * @return this builder for chaining
         * @throws IllegalArgumentException if port is not in valid range (1-65535)
         */
        public Builder consulPort(int consulPort) {
            if (consulPort < 1 || consulPort > 65535) {
                throw new IllegalArgumentException("ConsulPort must be between 1 and 65535");
            }
            this.consulPort = consulPort;
            return this;
        }

        /**
         * Sets the session TTL in seconds.
         *
         * @param sessionTtl the TTL in seconds (must be between 10 and 86400)
         * @return this builder for chaining
         */
        public Builder sessionTtl(int sessionTtl) {
            if (sessionTtl < 10 || sessionTtl > 86400) {
                throw new IllegalArgumentException(
                    "Session TTL must be between 10 and 86400 seconds");
            }
            this.sessionTtl = sessionTtl;
            return this;
        }

        /**
         * Sets the service name for cluster membership.
         *
         * @param serviceName the service name
         * @return this builder for chaining
         * @throws IllegalArgumentException if serviceName is null or empty
         */
        public Builder serviceName(String serviceName) {
            if (serviceName == null || serviceName.trim().isEmpty()) {
                throw new IllegalArgumentException("ServiceName must not be null or empty");
            }
            this.serviceName = serviceName;
            return this;
        }

        /**
         * Sets the Consul datacenter.
         *
         * @param datacenter the datacenter name
         * @return this builder for chaining
         */
        public Builder datacenter(String datacenter) {
            this.datacenter = datacenter;
            return this;
        }

        /**
         * Sets the Consul ACL token for authenticated access.
         *
         * @param token the ACL token
         * @return this builder for chaining
         */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * Builds the ConsulConfig instance.
         *
         * @return a new ConsulConfig with the configured values
         */
        public ConsulConfig build() {
            return new ConsulConfig(this);
        }
    }
}
