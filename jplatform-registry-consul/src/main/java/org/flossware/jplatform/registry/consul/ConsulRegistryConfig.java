package org.flossware.jplatform.registry.consul;

/**
 * Configuration for Consul-based service registry.
 * Provides connection settings for Consul agent and service registration parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ConsulRegistryConfig config = ConsulRegistryConfig.builder()
 *     .consulHost("localhost")
 *     .consulPort(8500)
 *     .serviceTtl(30)
 *     .nodeId("node-1")
 *     .build();
 * }</pre>
 *
 * @see ConsulServiceRegistry
 * @since 1.1
 */
public class ConsulRegistryConfig {

    private final String consulHost;
    private final int consulPort;
    private final int serviceTtl;
    private final String nodeId;
    private final String datacenter;
    private final String token;
    private final String servicePrefix;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    ConsulRegistryConfig(Builder builder) {
        this.consulHost = builder.consulHost;
        this.consulPort = builder.consulPort;
        this.serviceTtl = builder.serviceTtl;
        this.nodeId = builder.nodeId;
        this.datacenter = builder.datacenter;
        this.token = builder.token;
        this.servicePrefix = builder.servicePrefix;
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
     * Returns the service TTL in seconds.
     * Services must send health check passes within this interval.
     *
     * @return the service TTL in seconds (default: 30)
     */
    public int getServiceTtl() {
        return serviceTtl;
    }

    /**
     * Returns the unique node identifier.
     * Used to distinguish services from different nodes.
     *
     * @return the node ID
     */
    public String getNodeId() {
        return nodeId;
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
     * Returns the service name prefix.
     * Service names will be prefixed with this value.
     *
     * @return the service prefix (default: "jplatform")
     */
    public String getServicePrefix() {
        return servicePrefix;
    }

    /**
     * Creates a new builder for ConsulRegistryConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ConsulRegistryConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String consulHost = "localhost";
        private int consulPort = 8500;
        private int serviceTtl = 30;
        private String nodeId = java.util.UUID.randomUUID().toString();
        private String datacenter;
        private String token;
        private String servicePrefix = "jplatform";

        /**
         * Sets the Consul host address.
         *
         * @param consulHost the host address
         * @return this builder for chaining
         */
        public Builder consulHost(String consulHost) {
            this.consulHost = consulHost;
            return this;
        }

        /**
         * Sets the Consul HTTP API port.
         *
         * @param consulPort the port number
         * @return this builder for chaining
         */
        public Builder consulPort(int consulPort) {
            this.consulPort = consulPort;
            return this;
        }

        /**
         * Sets the service TTL in seconds.
         *
         * @param serviceTtl the TTL in seconds (must be at least 10)
         * @return this builder for chaining
         */
        public Builder serviceTtl(int serviceTtl) {
            if (serviceTtl < 10) {
                throw new IllegalArgumentException("Service TTL must be at least 10 seconds");
            }
            this.serviceTtl = serviceTtl;
            return this;
        }

        /**
         * Sets the unique node identifier.
         *
         * @param nodeId the node ID
         * @return this builder for chaining
         */
        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
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
         * Sets the service name prefix.
         *
         * @param servicePrefix the service prefix
         * @return this builder for chaining
         */
        public Builder servicePrefix(String servicePrefix) {
            this.servicePrefix = servicePrefix;
            return this;
        }

        /**
         * Builds the ConsulRegistryConfig instance.
         *
         * @return a new ConsulRegistryConfig with the configured values
         */
        public ConsulRegistryConfig build() {
            return new ConsulRegistryConfig(this);
        }
    }
}
