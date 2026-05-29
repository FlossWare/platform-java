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

package org.flossware.jplatform.rest.netty;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for NettyApiServer HTTP request handling.
 * Tests actual HTTP requests and responses via HttpRequestHandler.
 */
class NettyApiServerHttpTest {

    private static final int TEST_PORT = 18090;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    private NettyApiServer server;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    @Test
    void testSuccessfulRequest() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/test", input -> "{\"status\":\"success\"}");
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/test"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("{\"status\":\"success\"}", response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    }

    @Test
    void testRequestWithBody() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/echo", input -> "{\"echo\":\"" + input + "\"}");
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/echo"))
            .POST(HttpRequest.BodyPublishers.ofString("hello world"))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("{\"echo\":\"hello world\"}", response.body());
    }

    @Test
    void testRouteNotFound() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/nonexistent"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Route not found"));
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    }

    @Test
    void testHandlerThrowsIllegalArgumentException() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/validate", input -> {
            if (input.isEmpty()) {
                throw new IllegalArgumentException("Input cannot be empty");
            }
            return "{\"status\":\"ok\"}";
        });
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/validate"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Invalid request"));
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    }

    @Test
    void testHandlerThrowsGenericException() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/error", input -> {
            throw new RuntimeException("Simulated server error");
        });
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/error"))
            .POST(HttpRequest.BodyPublishers.ofString("test"))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("Internal server error"));
        assertFalse(response.body().contains("Simulated server error"), "Error details should not leak to client");
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    }

    @Test
    void testMultipleRequests() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/counter", input -> "{\"count\":1}");
        server.start();

        for (int i = 0; i < 5; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/counter"))
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
    }

    @Test
    void testMultipleRoutes() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/users", input -> "{\"users\":[]}");
        server.addRoute("/api/products", input -> "{\"products\":[]}");
        server.addRoute("/api/orders", input -> "{\"orders\":[]}");
        server.start();

        // Test /api/users
        HttpRequest request1 = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/users"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();
        HttpResponse<String> response1 = httpClient.send(request1, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response1.statusCode());
        assertTrue(response1.body().contains("users"));

        // Test /api/products
        HttpRequest request2 = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/products"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();
        HttpResponse<String> response2 = httpClient.send(request2, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response2.statusCode());
        assertTrue(response2.body().contains("products"));

        // Test /api/orders
        HttpRequest request3 = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/orders"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();
        HttpResponse<String> response3 = httpClient.send(request3, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response3.statusCode());
        assertTrue(response3.body().contains("orders"));
    }

    @Test
    void testContentLengthHeader() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/test", input -> "{\"status\":\"ok\"}");
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/test"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertTrue(response.headers().firstValue("Content-Length").isPresent());
        int contentLength = Integer.parseInt(response.headers().firstValue("Content-Length").get());
        assertEquals(response.body().length(), contentLength);
    }

    @Test
    void testGetRequest() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/info", input -> "{\"info\":\"server running\"}");
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/info"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("server running"));
    }

    @Test
    void testEmptyResponseBody() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/empty", input -> "");
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/empty"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void testLargeRequestBody() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .maxContentLength(2048576) // 2MB
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/upload", input -> "{\"received\":" + input.length() + "}");
        server.start();

        // Create a 1MB payload
        String largePayload = "x".repeat(1024 * 1024);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/upload"))
            .POST(HttpRequest.BodyPublishers.ofString(largePayload))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"received\":1048576"));
    }

    @Test
    void testJsonResponse() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/data", input ->
            "{\"name\":\"test\",\"value\":123,\"active\":true}"
        );
        server.start();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/data"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("\"name\":\"test\""));
        assertTrue(body.contains("\"value\":123"));
        assertTrue(body.contains("\"active\":true"));
    }

    @Test
    void testSpecialCharactersInBody() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/echo", input -> "{\"echo\":\"" + input + "\"}");
        server.start();

        String specialChars = "Hello\nWorld\t!@#$%";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/echo"))
            .POST(HttpRequest.BodyPublishers.ofString(specialChars))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Hello"));
        assertTrue(response.body().contains("World"));
    }

    @Test
    void testConcurrentRequests() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .workerThreads(4)
            .build();

        server = new NettyApiServer(config);
        server.addRoute("/api/concurrent", input -> "{\"thread\":\"" + Thread.currentThread().getName() + "\"}");
        server.start();

        // Make 10 concurrent requests
        Thread[] threads = new Thread[10];
        boolean[] success = new boolean[10];

        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/api/concurrent"))
                        .POST(HttpRequest.BodyPublishers.ofString("request-" + index))
                        .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    success[index] = (response.statusCode() == 200);
                } catch (Exception e) {
                    success[index] = false;
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all requests succeeded
        for (boolean s : success) {
            assertTrue(s, "All concurrent requests should succeed");
        }
    }
}
