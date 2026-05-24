package org.flossware.jplatform.cluster.redis;

/**
 * Configuration for Redis-based clustering.
 * Provides connection settings for Redis server and lease parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * RedisConfig config = RedisConfig.builder()
 *     .host("localhost")
 *     .port(6379)
 *     .leaseTtl(10)
 *     .build();
 * }</pre>
 *
 * @see RedisClusterManager
 * @since 1.1
 */
public class RedisConfig {

    private final String host;
    private final int port;
    private final long leaseTtl;
    private final String password;
    private final int database;
    private final int timeout;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    RedisConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.leaseTtl = builder.leaseTtl;
        this.password = builder.password;
        this.database = builder.database;
        this.timeout = builder.timeout;
    }

    /**
     * Returns the Redis host.
     *
     * @return the host address (default: "localhost")
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the Redis port.
     *
     * @return the port number (default: 6379)
     */
    public int getPort() {
        return port;
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
     * Returns the authentication password.
     *
     * @return the password, or null if not using authentication
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the Redis database number.
     *
     * @return the database number (default: 0)
     */
    public int getDatabase() {
        return database;
    }

    /**
     * Returns the connection timeout in milliseconds.
     *
     * @return the timeout in milliseconds (default: 2000)
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Creates a new builder for RedisConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RedisConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String host = "localhost";
        private int port = 6379;
        private long leaseTtl = 10;
        private String password;
        private int database = 0;
        private int timeout = 2000;

        /**
         * Sets the Redis host.
         *
         * @param host the host address
         * @return this builder for chaining
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the Redis port.
         *
         * @param port the port number (must be between 1 and 65535)
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
         * Sets the lease TTL in seconds.
         *
         * @param leaseTtl the TTL in seconds (must be at least 5)
         * @return this builder for chaining
         */
        public Builder leaseTtl(long leaseTtl) {
            if (leaseTtl < 5) {
                throw new IllegalArgumentException("Lease TTL must be at least 5 seconds");
            }
            this.leaseTtl = leaseTtl;
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
         * Sets the Redis database number.
         *
         * @param database the database number (must be non-negative)
         * @return this builder for chaining
         */
        public Builder database(int database) {
            if (database < 0) {
                throw new IllegalArgumentException("Database must be non-negative");
            }
            this.database = database;
            return this;
        }

        /**
         * Sets the connection timeout in milliseconds.
         *
         * @param timeout the timeout in milliseconds (must be positive)
         * @return this builder for chaining
         */
        public Builder timeout(int timeout) {
            if (timeout <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * Builds the RedisConfig instance.
         *
         * @return a new RedisConfig with the configured values
         */
        public RedisConfig build() {
            return new RedisConfig(this);
        }
    }
}
