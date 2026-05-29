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

package org.flossware.jplatform.webconsole;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * HTTP handler for serving the web console static resources.
 * This handler serves static HTML, CSS, and JavaScript files from the classpath
 * resources under /web/. It provides routing for the web console interface:
 * <ul>
 *   <li>/ or /console → index.html</li>
 *   <li>/static/* → CSS/JS files from /web/static/</li>
 *   <li>Other paths → served from /web/ directory</li>
 * </ul>
 *
 * Example usage:
 * <pre>{@code
 * HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
 * server.createContext("/console", new WebConsoleHandler());
 * server.start();
 * }</pre>
 *
 * @author Scot P. Floess
 * @version 1.0
 * @since 1.0
 */
public class WebConsoleHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebConsoleHandler.class);

    /**
     * Constructs a new WebConsoleHandler.
     */
    public WebConsoleHandler() {
    }

    /**
     * Determines the MIME content type based on file extension.
     *
     * @param path the file path to analyze
     * @return the appropriate MIME content type
     */
    private String getContentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        } else if (path.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        } else if (path.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        } else if (path.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    /**
     * Sends a 404 Not Found response to the client.
     *
     * @param exchange the HTTP exchange
     * @throws IOException if an I/O error occurs
     */
    private void send404(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(404, -1);
        exchange.getResponseBody().close();
    }

    /**
     * Sends a 405 Method Not Allowed response to the client.
     *
     * @param exchange the HTTP exchange
     * @throws IOException if an I/O error occurs
     */
    private void send405(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(405, -1);
        exchange.getResponseBody().close();
    }

    /**
     * Handles incoming HTTP requests and serves static files from the classpath.
     * The handler maps URL paths to classpath resources:
     * <ul>
     *   <li>Root path (/) and /console → /web/index.html</li>
     *   <li>All other paths → /web/{path}</li>
     * </ul>
     *
     * @param exchange the HTTP exchange containing request and response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Only allow GET requests
        if (!"GET".equals(exchange.getRequestMethod())) {
            logger.warn("Rejecting non-GET request: {}", exchange.getRequestMethod());
            send405(exchange);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        logger.debug("Handling request for path: {}", path);

        // Normalize path to prevent traversal attacks
        path = path.replaceAll("//+", "/");
        if (path.contains("..")) {
            logger.warn("Path traversal attempt detected: {}", path);
            send404(exchange);
            return;
        }

        // Route root and /console to index.html
        if (path.equals("/") || path.equals("/console")) {
            path = "/web/index.html";
        } else {
            // Prepend /web to all other paths
            path = "/web" + path;
        }

        logger.debug("Mapped to resource path: {}", path);

        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                logger.warn("Resource not found: {}", path);
                send404(exchange);
                return;
            }

            byte[] content = is.readAllBytes();
            String contentType = getContentType(path);

            logger.debug("Serving {} bytes of {}", content.length, contentType);

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.getResponseBody().close();

            logger.debug("Successfully served: {}", path);
        } catch (IOException e) {
            logger.error("Error serving resource: {}", path, e);
            send404(exchange);
        }
    }
}
