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

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import org.flossware.jplatform.api.ApiServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * HTTP filter for API key authentication.
 * Validates the API key header if authentication is enabled in the configuration.
 * Returns 401 Unauthorized if authentication fails, otherwise passes the request through.
 *
 * <p>Example configuration:</p>
 * <pre>{@code
 * ApiServerConfig config = ApiServerConfig.builder()
 *     .enableAuth(true)
 *     .apiKey("secret-key-123")
 *     .apiKeyHeader("X-API-Key")
 *     .build();
 *
 * // Filter will check for "X-API-Key: secret-key-123" header
 * }</pre>
 *
 * @see ApiServerConfig
 * @see JdkHttpApiServer
 */
public class ApiAuthFilter extends Filter {

    private static final Logger logger = LoggerFactory.getLogger(ApiAuthFilter.class);

    private final ApiServerConfig config;

    /**
     * Constructs a new API authentication filter.
     *
     * @param config the API server configuration containing auth settings
     * @throws NullPointerException if config is null
     * @throws IllegalArgumentException if auth is enabled but API key is not configured
     */
    public ApiAuthFilter(ApiServerConfig config) {
        this.config = Objects.requireNonNull(config, "API server config cannot be null");

        // Validate auth configuration
        if (config.isEnableAuth()) {
            if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "API key must be configured when authentication is enabled");
            }
            if (config.getApiKeyHeader() == null || config.getApiKeyHeader().trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "API key header must be configured when authentication is enabled");
            }
        }
    }

    /**
     * Filters the HTTP request and checks authentication if enabled.
     * Allows OPTIONS requests to pass through for CORS preflight.
     *
     * @param exchange the HTTP exchange
     * @param chain the filter chain to continue processing
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        // Allow OPTIONS requests for CORS preflight
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            chain.doFilter(exchange);
            return;
        }

        // If auth is disabled, pass through
        if (!config.isEnableAuth()) {
            chain.doFilter(exchange);
            return;
        }

        // Check API key
        String apiKeyHeader = config.getApiKeyHeader();
        String providedKey = exchange.getRequestHeaders().getFirst(apiKeyHeader);

        if (providedKey == null || !constantTimeEquals(providedKey, config.getApiKey())) {
            logger.warn("Unauthorized request from {}: missing or invalid API key",
                    exchange.getRemoteAddress());
            sendUnauthorized(exchange);
            return;
        }

        // Auth successful, continue
        chain.doFilter(exchange);
    }

    /**
     * Performs constant-time comparison of two strings to prevent timing attacks.
     * Uses MessageDigest.isEqual which compares all bytes regardless of match.
     *
     * @param a first string
     * @param b second string
     * @return true if strings are equal, false otherwise
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    /**
     * Sends a 401 Unauthorized response.
     *
     * @param exchange the HTTP exchange
     * @throws IOException if an I/O error occurs
     */
    private void sendUnauthorized(HttpExchange exchange) throws IOException {
        String response = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\",\"status\":401}";
        byte[] responseBytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(401, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * Returns a description of this filter.
     *
     * @return the filter description
     */
    @Override
    public String description() {
        return "API Key Authentication Filter";
    }
}
