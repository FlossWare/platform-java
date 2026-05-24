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
         */
        public Builder serviceName(String serviceName) {
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
