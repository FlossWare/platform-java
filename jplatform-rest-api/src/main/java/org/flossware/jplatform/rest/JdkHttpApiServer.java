package org.flossware.jplatform.rest;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.flossware.jplatform.api.ApiServerConfig;
import org.flossware.jplatform.api.PlatformApiServer;
import org.flossware.jplatform.api.PlatformManager;
import org.flossware.jplatform.api.ServerShutdownException;
import org.flossware.jplatform.api.ServerStartupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * REST API server implementation using JDK's built-in {@code com.sun.net.httpserver.HttpServer}.
 * Provides HTTP endpoints for remote platform management including application deployment,
 * lifecycle control, and monitoring.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Application management endpoints (deploy, start, stop, undeploy)</li>
 *   <li>Platform information and health check endpoints</li>
 *   <li>CORS support with configurable allowed origins</li>
 *   <li>Optional API key authentication</li>
 *   <li>JSON request/response using Jackson</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApiServerConfig config = ApiServerConfig.builder()
 *     .port(8080)
 *     .bindAddress("0.0.0.0")
 *     .enableAuth(true)
 *     .apiKey("secret-key-123")
 *     .addAllowedOrigin("http://localhost:3000")
 *     .build();
 *
 * ApplicationManager manager = new ApplicationManager();
 * PlatformApiServer server = new JdkHttpApiServer(config, manager);
 * server.start();
 *
 * // Server is now accepting requests on port 8080
 *
 * server.stop();
 * }</pre>
 *
 * <p>Available endpoints:</p>
 * <ul>
 *   <li>POST /api/applications - Deploy application</li>
 *   <li>GET /api/applications - List applications</li>
 *   <li>GET /api/applications/{id} - Get application details</li>
 *   <li>GET /api/applications/{id}/status - Get status and metrics</li>
 *   <li>POST /api/applications/{id}/start - Start application</li>
 *   <li>POST /api/applications/{id}/stop - Stop application</li>
 *   <li>DELETE /api/applications/{id} - Undeploy application</li>
 *   <li>GET /api/applications/{id}/metrics - Get metrics history</li>
 *   <li>GET /api/platform/info - Platform information</li>
 *   <li>GET /api/health - Health check</li>
 * </ul>
 *
 * @see PlatformApiServer
 * @see ApiServerConfig
 * @see PlatformManager
 */
public class JdkHttpApiServer implements PlatformApiServer {

    private static final Logger logger = LoggerFactory.getLogger(JdkHttpApiServer.class);

    private final ApiServerConfig config;
    private final PlatformManager manager;
    private HttpServer server;
    private ExecutorService executor;
    private volatile boolean running;

    /**
     * Constructs a new JDK HTTP API server.
     *
     * @param config the server configuration
     * @param manager the platform manager for handling application operations
     * @throws NullPointerException if config or manager is null
     */
    public JdkHttpApiServer(ApiServerConfig config, PlatformManager manager) {
        this.config = Objects.requireNonNull(config, "ApiServerConfig is required");
        this.manager = Objects.requireNonNull(manager, "PlatformManager is required");
        this.running = false;
    }

    /**
     * Starts the HTTP server and begins accepting requests.
     * Creates the HTTP server, registers endpoint handlers, configures CORS,
     * sets up authentication filter, and starts the server.
     *
     * @throws ServerStartupException if the server cannot be started
     * @throws IllegalStateException if the server is already running
     */
    @Override
    public void start() throws ServerStartupException {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }

        logger.info("Starting HTTP API server on {}:{}", config.getBindAddress(), config.getPort());

        try {
            // Create HTTP server
            InetSocketAddress address = new InetSocketAddress(config.getBindAddress(), config.getPort());
            server = HttpServer.create(address, 0);

            // Register handlers with CORS wrapper
            server.createContext("/api/applications", wrapWithCors(new ApplicationApiHandler(manager)));
            server.createContext("/api/platform", wrapWithCors(new PlatformApiHandler()));
            server.createContext("/api/health", wrapWithCors(new PlatformApiHandler()));

            // Set up authentication filter if enabled
            if (config.isEnableAuth()) {
                logger.info("API authentication enabled with header: {}", config.getApiKeyHeader());
                ApiAuthFilter authFilter = new ApiAuthFilter(config);
                server.createContext("/api/applications").getFilters().add(authFilter);
                server.createContext("/api/platform").getFilters().add(authFilter);
            }

            // Configure executor for handling requests
            executor = Executors.newFixedThreadPool(10);
            server.setExecutor(executor);

            // Start the server
            server.start();
            running = true;

            logger.info("HTTP API server started successfully on port {}", config.getPort());

            if (!config.getAllowedOrigins().isEmpty()) {
                logger.info("CORS enabled for origins: {}", config.getAllowedOrigins());
            }

        } catch (IOException e) {
            logger.error("Failed to start HTTP API server", e);
            throw new ServerStartupException("Failed to start HTTP API server on port " + config.getPort(),
                    config.getPort(), e);
        }
    }

    /**
     * Stops the HTTP server and releases resources.
     * Gracefully shuts down the server with a 5-second delay.
     *
     * @throws ServerShutdownException if the server cannot be stopped
     * @throws IllegalStateException if the server is not running
     */
    @Override
    public void stop() throws ServerShutdownException {
        if (!running) {
            throw new IllegalStateException("Server is not running");
        }

        logger.info("Stopping HTTP API server");

        try {
            // Stop with 5 second delay for graceful shutdown
            server.stop(5);

            // Shutdown executor
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.warn("Executor did not terminate in time, forcing shutdown");
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for executor shutdown, forcing shutdown");
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            running = false;

            logger.info("HTTP API server stopped successfully");

        } catch (Exception e) {
            logger.error("Error stopping HTTP API server", e);
            throw new ServerShutdownException("Failed to stop HTTP API server", e);
        }
    }

    /**
     * Returns the port the server is listening on.
     *
     * @return the server port from configuration
     */
    @Override
    public int getPort() {
        return config.getPort();
    }

    /**
     * Checks if the server is currently running.
     *
     * @return true if the server is running, false otherwise
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Closes the server and releases resources.
     * Calls {@link #stop()} if the server is running.
     *
     * @throws Exception if the server cannot be stopped
     */
    @Override
    public void close() throws Exception {
        if (running) {
            stop();
        }
    }

    /**
     * Wraps a handler with CORS support.
     * Adds CORS headers to all responses and handles OPTIONS preflight requests.
     *
     * @param handler the handler to wrap
     * @return a new handler with CORS support
     */
    private com.sun.net.httpserver.HttpHandler wrapWithCors(com.sun.net.httpserver.HttpHandler handler) {
        return exchange -> {
            // Add CORS headers
            addCorsHeaders(exchange);

            // Handle OPTIONS preflight
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Delegate to actual handler
            handler.handle(exchange);
        };
    }

    /**
     * Adds CORS headers to the HTTP response.
     * Validates the request Origin header against allowed origins from configuration.
     * If no origins are configured, allows all origins with "*".
     *
     * @param exchange the HTTP exchange
     */
    private void addCorsHeaders(HttpExchange exchange) {
        Headers responseHeaders = exchange.getResponseHeaders();
        Headers requestHeaders = exchange.getRequestHeaders();

        Set<String> allowedOrigins = config.getAllowedOrigins();
        if (!allowedOrigins.isEmpty()) {
            // Get the Origin header from the request
            String requestOrigin = requestHeaders.getFirst("Origin");

            if (requestOrigin != null && allowedOrigins.contains(requestOrigin)) {
                // Echo back the origin only if it's in the allowed set
                responseHeaders.add("Access-Control-Allow-Origin", requestOrigin);
                // Vary header is important for proper caching of CORS responses
                responseHeaders.add("Vary", "Origin");
                logger.debug("Accepting CORS request from allowed origin: {}", requestOrigin);
            } else {
                // Origin not allowed or not provided - don't add CORS header
                // Browser will block the response
                if (requestOrigin != null) {
                    logger.debug("Rejecting CORS request from disallowed origin: {}", requestOrigin);
                }
            }
        } else {
            // No restrictions configured - allow all origins
            responseHeaders.add("Access-Control-Allow-Origin", "*");
        }

        responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, " + config.getApiKeyHeader());
        responseHeaders.add("Access-Control-Max-Age", "3600");
    }
}
