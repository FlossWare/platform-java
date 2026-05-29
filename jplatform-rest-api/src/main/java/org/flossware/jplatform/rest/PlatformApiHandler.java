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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP handler for platform-level API endpoints.
 * Provides platform information, health checks, and system status.
 *
 * <p>Supported endpoints:</p>
 * <ul>
 *   <li>GET /api/platform/info - Platform version, uptime, JVM information</li>
 *   <li>GET /api/health - Health check (returns 200 OK)</li>
 * </ul>
 *
 * <p>Example responses:</p>
 * <pre>{@code
 * GET /api/platform/info:
 * {
 *   "platform": "JPlatform",
 *   "version": "1.0",
 *   "uptimeMillis": 123456789,
 *   "jvmVersion": "17.0.2",
 *   "jvmVendor": "Oracle Corporation",
 *   "osName": "Linux",
 *   "osVersion": "5.15.0",
 *   "availableProcessors": 8
 * }
 *
 * GET /api/health:
 * {
 *   "status": "ok",
 *   "timestamp": 1716345600000
 * }
 * }</pre>
 *
 * @see JdkHttpApiServer
 */
public class PlatformApiHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(PlatformApiHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Handles HTTP requests for platform endpoints.
     * Routes requests based on path and HTTP method.
     *
     * @param exchange the HTTP exchange
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        logger.debug("Platform API request: {} {}", method, path);

        try {
            if (path.equals("/api/health") && method.equals("GET")) {
                handleHealthCheck(exchange);
            } else if (path.equals("/api/platform/info") && method.equals("GET")) {
                handlePlatformInfo(exchange);
            } else {
                sendErrorResponse(exchange, 404, "NotFound", "Endpoint not found: " + path);
            }
        } catch (Exception e) {
            logger.error("Error handling platform API request", e);
            sendErrorResponse(exchange, 500, "InternalError", e.getMessage());
        }
    }

    /**
     * Handles GET /api/health - health check endpoint.
     * Returns a simple status indicating the server is running.
     *
     * @param exchange the HTTP exchange
     * @throws IOException if an I/O error occurs
     */
    private void handleHealthCheck(HttpExchange exchange) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", System.currentTimeMillis());

        sendJsonResponse(exchange, 200, response);
    }

    /**
     * Handles GET /api/platform/info - platform information endpoint.
     * Returns platform version, uptime, and JVM details.
     *
     * @param exchange the HTTP exchange
     * @throws IOException if an I/O error occurs
     */
    private void handlePlatformInfo(HttpExchange exchange) throws IOException {
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();

        Map<String, Object> info = new HashMap<>();
        info.put("platform", "JPlatform");
        info.put("version", "1.0");
        info.put("uptimeMillis", uptimeMillis);
        info.put("jvmVersion", System.getProperty("java.version"));
        info.put("jvmVendor", System.getProperty("java.vendor"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osVersion", System.getProperty("os.version"));
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());

        sendJsonResponse(exchange, 200, info);
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
}
