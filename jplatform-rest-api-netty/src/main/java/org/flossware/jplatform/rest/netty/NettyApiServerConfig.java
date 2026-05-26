package org.flossware.jplatform.rest.netty;

/**
 * Configuration for Netty-based REST API server.
 * Provides server settings and configuration parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * NettyApiServerConfig config = NettyApiServerConfig.builder()
 *     .host("0.0.0.0")
 *     .port(8080)
 *     .maxContentLength(65536)
 *     .build();
 * }</pre>
 *
 * @see NettyApiServer
 * @since 1.1
 */
public class NettyApiServerConfig {

    private final String host;
    private final int port;
    private final int bossThreads;
    private final int workerThreads;
    private final int maxContentLength;
    private final boolean keepAlive;
    private final int backlog;
    private final int globalRateLimitRps;
    private final int perIpRateLimitRps;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    NettyApiServerConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.bossThreads = builder.bossThreads;
        this.workerThreads = builder.workerThreads;
        this.maxContentLength = builder.maxContentLength;
        this.keepAlive = builder.keepAlive;
        this.backlog = builder.backlog;
        this.globalRateLimitRps = builder.globalRateLimitRps;
        this.perIpRateLimitRps = builder.perIpRateLimitRps;
    }

    /**
     * Returns the server host.
     *
     * @return the host (default: "0.0.0.0")
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the server port.
     *
     * @return the port (default: 8080)
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the number of boss threads.
     *
     * @return the boss threads (default: 1)
     */
    public int getBossThreads() {
        return bossThreads;
    }

    /**
     * Returns the number of worker threads.
     *
     * @return the worker threads (default: 0, auto-detect)
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * Returns the maximum content length in bytes.
     *
     * @return the max content length (default: 65536)
     */
    public int getMaxContentLength() {
        return maxContentLength;
    }

    /**
     * Returns whether keep-alive is enabled.
     *
     * @return true if keep-alive is enabled (default: true)
     */
    public boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * Returns the backlog size.
     *
     * @return the backlog (default: 128)
     */
    public int getBacklog() {
        return backlog;
    }

    /**
     * Returns the global rate limit in requests per second.
     *
     * @return the global rate limit (0 = unlimited, default: 1000)
     */
    public int getGlobalRateLimitRps() {
        return globalRateLimitRps;
    }

    /**
     * Returns the per-IP rate limit in requests per second.
     *
     * @return the per-IP rate limit (0 = unlimited, default: 100)
     */
    public int getPerIpRateLimitRps() {
        return perIpRateLimitRps;
    }

    /**
     * Creates a new builder for NettyApiServerConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for NettyApiServerConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String host = "0.0.0.0";
        private int port = 8080;
        private int bossThreads = 1;
        private int workerThreads = 0;
        private int maxContentLength = 65536;
        private boolean keepAlive = true;
        private int backlog = 128;
        private int globalRateLimitRps = 1000;
        private int perIpRateLimitRps = 100;

        /**
         * Sets the server host.
         *
         * @param host the host address to bind to
         * @return this builder for chaining
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the server port.
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
         * Sets the number of boss threads.
         *
         * @param bossThreads the number of boss threads (must be at least 1)
         * @return this builder for chaining
         */
        public Builder bossThreads(int bossThreads) {
            if (bossThreads < 1) {
                throw new IllegalArgumentException("Boss threads must be at least 1");
            }
            this.bossThreads = bossThreads;
            return this;
        }

        /**
         * Sets the number of worker threads.
         *
         * @param workerThreads the number of worker threads (0 = auto-detect, must be at least 0)
         * @return this builder for chaining
         */
        public Builder workerThreads(int workerThreads) {
            if (workerThreads < 0) {
                throw new IllegalArgumentException("Worker threads must be at least 0");
            }
            this.workerThreads = workerThreads;
            return this;
        }

        /**
         * Sets the maximum content length.
         *
         * @param maxContentLength the max content length in bytes (must be between 1024 and 100MB)
         * @return this builder for chaining
         * @throws IllegalArgumentException if maxContentLength is out of range
         */
        public Builder maxContentLength(int maxContentLength) {
            if (maxContentLength < 1024) {
                throw new IllegalArgumentException("Max content length must be at least 1024 bytes");
            }
            int MAX_SAFE_CONTENT_LENGTH = 100 * 1024 * 1024;  // 100MB
            if (maxContentLength > MAX_SAFE_CONTENT_LENGTH) {
                throw new IllegalArgumentException(
                    "Max content length (" + maxContentLength +
                    " bytes) exceeds maximum allowed (100MB). " +
                    "Use streaming for larger payloads."
                );
            }
            this.maxContentLength = maxContentLength;
            return this;
        }

        /**
         * Sets whether to enable keep-alive.
         *
         * @param keepAlive true to enable keep-alive
         * @return this builder for chaining
         */
        public Builder keepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        /**
         * Sets the connection backlog size.
         *
         * @param backlog the backlog size (must be at least 1)
         * @return this builder for chaining
         */
        public Builder backlog(int backlog) {
            if (backlog < 1) {
                throw new IllegalArgumentException("Backlog must be at least 1");
            }
            this.backlog = backlog;
            return this;
        }

        /**
         * Sets the global rate limit in requests per second.
         *
         * @param rps requests per second (0 = unlimited, must be >= 0)
         * @return this builder for chaining
         */
        public Builder globalRateLimit(int rps) {
            if (rps < 0) {
                throw new IllegalArgumentException("Global rate limit must be >= 0 (0 = unlimited)");
            }
            this.globalRateLimitRps = rps;
            return this;
        }

        /**
         * Sets the per-IP rate limit in requests per second.
         *
         * @param rps requests per second (0 = unlimited, must be >= 0)
         * @return this builder for chaining
         */
        public Builder perIpRateLimit(int rps) {
            if (rps < 0) {
                throw new IllegalArgumentException("Per-IP rate limit must be >= 0 (0 = unlimited)");
            }
            this.perIpRateLimitRps = rps;
            return this;
        }

        /**
         * Builds the NettyApiServerConfig instance.
         *
         * @return a new NettyApiServerConfig with the configured values
         * @throws IllegalStateException if required fields are missing
         */
        public NettyApiServerConfig build() {
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalStateException("Host must be specified");
            }
            return new NettyApiServerConfig(this);
        }
    }
}
