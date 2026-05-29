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

package org.flossware.jplatform.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP handler for application management API endpoints.
 * Provides REST endpoints for deploying, starting, stopping, and querying applications.
 *
 * <p>Supported endpoints:</p>
 * <ul>
 *   <li>POST /api/applications - Deploy a new application</li>
 *   <li>GET /api/applications - List all applications</li>
 *   <li>GET /api/applications/{id} - Get application details</li>
 *   <li>GET /api/applications/{id}/status - Get application status and metrics</li>
 *   <li>POST /api/applications/{id}/start - Start an application</li>
 *   <li>POST /api/applications/{id}/stop - Stop an application</li>
 *   <li>DELETE /api/applications/{id} - Undeploy an application</li>
 *   <li>GET /api/applications/{id}/metrics - Get resource metrics history</li>
 * </ul>
 *
 * <p>Example deploy request:</p>
 * <pre>{@code
 * POST /api/applications
 * {
 *   "applicationId": "my-app",
 *   "name": "My Application",
 *   "mainClass": "com.example.MyApp",
 *   "classpathEntries": ["file:///path/to/app.jar"],
 *   "enableMessaging": false
 * }
 * }</pre>
 *
 * @see PlatformManager
 * @see JdkHttpApiServer
 */
public class ApplicationApiHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationApiHandler.class);
    private static final long MAX_REQUEST_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Pattern APP_ID_PATTERN = Pattern.compile("/api/applications/([^/]+)$");
    private static final Pattern APP_STATUS_PATTERN = Pattern.compile("/api/applications/([^/]+)/status$");
    private static final Pattern APP_START_PATTERN = Pattern.compile("/api/applications/([^/]+)/start$");
    private static final Pattern APP_STOP_PATTERN = Pattern.compile("/api/applications/([^/]+)/stop$");
    private static final Pattern APP_METRICS_PATTERN = Pattern.compile("/api/applications/([^/]+)/metrics$");

    private final PlatformManager manager;

    /**
     * Constructs a new application API handler.
     *
     * @param manager the platform manager to delegate operations to
     */
    public ApplicationApiHandler(PlatformManager manager) {
        this.manager = Objects.requireNonNull(manager, "PlatformManager is required");
    }

    /**
     * Handles HTTP requests for application endpoints.
     * Routes requests based on path and HTTP method.
     *
     * @param exchange the HTTP exchange
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        logger.debug("Application API request: {} {}", method, path);

        try {
            if (path.equals("/api/applications") && method.equals("GET")) {
                handleListApplications(exchange);
            } else if (path.equals("/api/applications") && method.equals("POST")) {
                handleDeploy(exchange);
            } else if (matches(path, APP_ID_PATTERN) && method.equals("GET")) {
                String appId = extractAppId(path, APP_ID_PATTERN);
                handleGetApplication(exchange, appId);
            } else if (matches(path, APP_STATUS_PATTERN) && method.equals("GET")) {
                String appId = extractAppId(path, APP_STATUS_PATTERN);
                handleGetStatus(exchange, appId);
            } else if (matches(path, APP_START_PATTERN) && method.equals("POST")) {
                String appId = extractAppId(path, APP_START_PATTERN);
                handleStart(exchange, appId);
            } else if (matches(path, APP_STOP_PATTERN) && method.equals("POST")) {
                String appId = extractAppId(path, APP_STOP_PATTERN);
                handleStop(exchange, appId);
            } else if (matches(path, APP_METRICS_PATTERN) && method.equals("GET")) {
                String appId = extractAppId(path, APP_METRICS_PATTERN);
                handleGetMetrics(exchange, appId);
            } else if (matches(path, APP_ID_PATTERN) && method.equals("DELETE")) {
                String appId = extractAppId(path, APP_ID_PATTERN);
                handleUndeploy(exchange, appId);
            } else {
                sendErrorResponse(exchange, 404, "NotFound", "Endpoint not found: " + path);
            }
        } catch (IllegalStateException e) {
            logger.warn("Client error: {}", e.getMessage());
            sendErrorResponse(exchange, 400, "BadRequest", e.getMessage());
        } catch (Exception e) {
            logger.error("Error handling application API request", e);
            sendErrorResponse(exchange, 500, "InternalError", e.getMessage());
        }
    }

    /**
     * Handles POST /api/applications - deploy a new application.
     * Accepts JSON ApplicationDescriptor in the request body.
     *
     * @param exchange the HTTP exchange
     * @throws IOException if an I/O error occurs
     */
    private void handleDeploy(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            // Limit request size to prevent DoS
            InputStream limitedStream = new BoundedInputStream(is, MAX_REQUEST_SIZE);

            ApplicationDescriptorDTO dto = mapper.readValue(limitedStream, ApplicationDescriptorDTO.class);
            ApplicationDescriptor descriptor = dto.toApplicationDescriptor();

            manager.deploy(descriptor);

            ApplicationContext context = manager.getApplicationContext(descriptor.getApplicationId());
            ApplicationResponseDTO response = ApplicationResponseDTO.fromApplicationContext(context);

            logger.info("Deployed application: {}", descriptor.getApplicationId());
            sendJsonResponse(exchange, 201, response);
        } catch (RequestTooLargeException e) {
            logger.warn("Deploy request too large: {}", e.getMessage());
            sendErrorResponse(exchange, 413, "PayloadTooLarge",
                "Request body exceeds maximum size of " + MAX_REQUEST_SIZE + " bytes");
        } catch (Exception e) {
            logger.error("Failed to deploy application", e);
            sendErrorResponse(exchange, 400, "DeploymentFailed", e.getMessage());
        }
    }

    /**
     * Handles GET /api/applications - list all applications.
     * Returns a list of all deployed applications with their current states.
     *
     * @param exchange the HTTP exchange
     * @throws IOException if an I/O error occurs
     */
    private void handleListApplications(HttpExchange exchange) throws IOException {
        Map<String, ApplicationState> apps = manager.listApplications();
        List<Map<String, String>> appList = new ArrayList<>();

        for (Map.Entry<String, ApplicationState> entry : apps.entrySet()) {
            Map<String, String> appInfo = new HashMap<>();
            appInfo.put("applicationId", entry.getKey());
            appInfo.put("state", entry.getValue().name());
            appList.add(appInfo);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("applications", appList);
        response.put("count", appList.size());

        sendJsonResponse(exchange, 200, response);
    }

    /**
     * Handles GET /api/applications/{id} - get application details.
     * Returns full application information including metrics if running.
     *
     * @param exchange the HTTP exchange
     * @param appId the application identifier
     * @throws IOException if an I/O error occurs
     */
    private void handleGetApplication(HttpExchange exchange, String appId) throws IOException {
        ApplicationContext context = manager.getApplicationContext(appId);
        if (context == null) {
            sendErrorResponse(exchange, 404, "NotFound", "Application not found: " + appId);
            return;
        }

        ApplicationResponseDTO response = ApplicationResponseDTO.fromApplicationContext(context);
        sendJsonResponse(exchange, 200, response);
    }

    /**
     * Handles GET /api/applications/{id}/status - get application status.
     * Returns current state and runtime metrics.
     *
     * @param exchange the HTTP exchange
     * @param appId the application identifier
     * @throws IOException if an I/O error occurs
     */
    private void handleGetStatus(HttpExchange exchange, String appId) throws IOException {
        ApplicationContext context = manager.getApplicationContext(appId);
        if (context == null) {
            sendErrorResponse(exchange, 404, "NotFound", "Application not found: " + appId);
            return;
        }

        Map<String, Object> status = new HashMap<>();
        status.put("applicationId", appId);
        status.put("state", context.getState().name());

        if (context.getState() == ApplicationState.RUNNING) {
            ThreadPoolStats poolStats = context.getThreadPool().getStats();
            Map<String, Object> threadPool = new HashMap<>();
            threadPool.put("activeThreads", poolStats.getActiveThreads());
            threadPool.put("completedTasks", poolStats.getCompletedTasks());
            threadPool.put("queuedTasks", poolStats.getQueuedTasks());
            threadPool.put("poolSize", poolStats.getPoolSize());
            status.put("threadPool", threadPool);

            ResourceSnapshot snapshot = context.getResourceMonitor().getCurrentSnapshot();
            Map<String, Object> resources = new HashMap<>();
            resources.put("cpuTimeNanos", snapshot.getCpuTimeNanos());
            resources.put("heapUsedBytes", snapshot.getHeapUsedBytes());
            resources.put("threadCount", snapshot.getThreadCount());
            status.put("resources", resources);
        }

        sendJsonResponse(exchange, 200, status);
    }

    /**
     * Handles POST /api/applications/{id}/start - start an application.
     * Transitions the application from DEPLOYED or STOPPED to RUNNING state.
     *
     * @param exchange the HTTP exchange
     * @param appId the application identifier
     * @throws IOException if an I/O error occurs
     */
    private void handleStart(HttpExchange exchange, String appId) throws IOException {
        try {
            manager.start(appId);
            ApplicationContext context = manager.getApplicationContext(appId);
            ApplicationResponseDTO response = ApplicationResponseDTO.fromApplicationContext(context);

            logger.info("Started application: {}", appId);
            sendJsonResponse(exchange, 200, response);
        } catch (IllegalStateException e) {
            sendErrorResponse(exchange, 400, "InvalidState", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to start application: {}", appId, e);
            sendErrorResponse(exchange, 500, "StartFailed", e.getMessage());
        }
    }

    /**
     * Handles POST /api/applications/{id}/stop - stop an application.
     * Transitions the application from RUNNING to STOPPED state.
     *
     * @param exchange the HTTP exchange
     * @param appId the application identifier
     * @throws IOException if an I/O error occurs
     */
    private void handleStop(HttpExchange exchange, String appId) throws IOException {
        try {
            manager.stop(appId);
            ApplicationContext context = manager.getApplicationContext(appId);
            ApplicationResponseDTO response = ApplicationResponseDTO.fromApplicationContext(context);

            logger.info("Stopped application: {}", appId);
            sendJsonResponse(exchange, 200, response);
        } catch (IllegalStateException e) {
            sendErrorResponse(exchange, 400, "InvalidState", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to stop application: {}", appId, e);
            sendErrorResponse(exchange, 500, "StopFailed", e.getMessage());
        }
    }

    /**
     * Handles DELETE /api/applications/{id} - undeploy an application.
     * Removes the application from the platform and cleans up resources.
     *
     * @param exchange the HTTP exchange
     * @param appId the application identifier
     * @throws IOException if an I/O error occurs
     */
    private void handleUndeploy(HttpExchange exchange, String appId) throws IOException {
        try {
            manager.undeploy(appId);

            Map<String, Object> response = new HashMap<>();
            response.put("applicationId", appId);
            response.put("status", "undeployed");

            logger.info("Undeployed application: {}", appId);
            sendJsonResponse(exchange, 200, response);
        } catch (IllegalStateException e) {
            sendErrorResponse(exchange, 400, "InvalidState", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to undeploy application: {}", appId, e);
            sendErrorResponse(exchange, 500, "UndeployFailed", e.getMessage());
        }
    }

    /**
     * Handles GET /api/applications/{id}/metrics - get resource metrics history.
     * Returns historical resource usage data if available.
     *
     * @param exchange the HTTP exchange
     * @param appId the application identifier
     * @throws IOException if an I/O error occurs
     */
    private void handleGetMetrics(HttpExchange exchange, String appId) throws IOException {
        ApplicationContext context = manager.getApplicationContext(appId);
        if (context == null) {
            sendErrorResponse(exchange, 404, "NotFound", "Application not found: " + appId);
            return;
        }

        ResourceMonitor monitor = context.getResourceMonitor();
        // Get last 24 hours of metrics history
        ResourceUsageHistory history;
        try {
            history = monitor.getHistory(java.time.Duration.ofHours(24));
        } catch (Exception e) {
            sendErrorResponse(exchange, 404, "NotAvailable", "Metrics history not available for this application");
            return;
        }
        List<ResourceSnapshot> snapshots = history.getSnapshots();

        List<Map<String, Object>> metricsData = new ArrayList<>();
        for (ResourceSnapshot snapshot : snapshots) {
            Map<String, Object> data = new HashMap<>();
            data.put("timestamp", snapshot.getTimestamp());
            data.put("cpuTimeNanos", snapshot.getCpuTimeNanos());
            data.put("heapUsedBytes", snapshot.getHeapUsedBytes());
            data.put("threadCount", snapshot.getThreadCount());
            metricsData.add(data);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("applicationId", appId);
        response.put("metrics", metricsData);
        response.put("count", metricsData.size());

        sendJsonResponse(exchange, 200, response);
    }

    /**
     * Checks if a path matches a pattern.
     *
     * @param path the path to check
     * @param pattern the pattern to match against
     * @return true if the path matches
     */
    private boolean matches(String path, Pattern pattern) {
        return pattern.matcher(path).matches();
    }

    /**
     * Extracts the application ID from a path using a pattern.
     *
     * @param path the path containing the application ID
     * @param pattern the pattern with a capture group for the ID
     * @return the extracted application ID
     */
    private String extractAppId(String path, Pattern pattern) {
        Matcher matcher = pattern.matcher(path);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Invalid path: " + path);
    }

    /**
     * Sends a JSON response with the specified status code.
     *
     * @param exchange the HTTP exchange
     * @param statusCode the HTTP status code
     * @param data the data to serialize as JSON
     * @throws IOException if an I/O error occurs
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = mapper.writeValueAsString(data);
        byte[] jsonBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, jsonBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonBytes);
        }
    }

    /**
     * Sends an error response with the specified status code and message.
     *
     * @param exchange the HTTP exchange
     * @param statusCode the HTTP status code
     * @param error the error type
     * @param message the error message
     * @throws IOException if an I/O error occurs
     */
    private void sendErrorResponse(HttpExchange exchange, int statusCode, String error, String message) throws IOException {
        ErrorResponseDTO errorResponse = ErrorResponseDTO.create(error, message, statusCode);
        sendJsonResponse(exchange, statusCode, errorResponse);
    }

    /**
     * Data transfer object for application descriptor in deploy requests.
     * Simplified version for JSON deserialization.
     */
    public static class ApplicationDescriptorDTO {
        public String applicationId;
        public String name;
        public String version;
        public String mainClass;
        public List<String> classpathEntries;
        public Map<String, String> properties;
        public boolean enableMessaging;

        /**
         * Converts this DTO to an ApplicationDescriptor.
         *
         * @return the application descriptor
         * @throws IllegalArgumentException if required fields are missing or invalid
         */
        public ApplicationDescriptor toApplicationDescriptor() {
            if (applicationId == null || applicationId.trim().isEmpty()) {
                throw new IllegalArgumentException("applicationId is required");
            }
            if (mainClass == null || mainClass.trim().isEmpty()) {
                throw new IllegalArgumentException("mainClass is required");
            }

            ApplicationDescriptor.Builder builder = ApplicationDescriptor.builder()
                    .applicationId(applicationId)
                    .mainClass(mainClass);

            if (name != null) {
                builder.name(name);
            }
            if (version != null) {
                builder.version(version);
            }
            if (classpathEntries != null) {
                for (String entry : classpathEntries) {
                    try {
                        builder.addClasspathEntry(java.net.URI.create(entry));
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Invalid classpath entry: " + entry, e);
                    }
                }
            }
            if (properties != null) {
                properties.forEach(builder::property);
            }

            builder.enableMessaging(enableMessaging);

            return builder.build();
        }
    }

    /**
     * Exception thrown when request size exceeds the limit.
     */
    private static class RequestTooLargeException extends IOException {
        public RequestTooLargeException(String message) {
            super(message);
        }
    }

    /**
     * InputStream wrapper that enforces a maximum read size.
     * Throws RequestTooLargeException if the limit is exceeded.
     */
    private static class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private final long maxSize;
        private long bytesRead = 0;

        public BoundedInputStream(InputStream delegate, long maxSize) {
            this.delegate = delegate;
            this.maxSize = maxSize;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                bytesRead++;
                if (bytesRead > maxSize) {
                    throw new RequestTooLargeException(
                        "Request size exceeded maximum of " + maxSize + " bytes");
                }
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = delegate.read(b, off, len);
            if (count > 0) {
                bytesRead += count;
                if (bytesRead > maxSize) {
                    throw new RequestTooLargeException(
                        "Request size exceeded maximum of " + maxSize + " bytes");
                }
            }
            return count;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
