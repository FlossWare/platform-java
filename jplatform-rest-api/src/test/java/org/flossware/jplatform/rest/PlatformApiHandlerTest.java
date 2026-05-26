package org.flossware.jplatform.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for PlatformApiHandler.
 * Tests platform information and health check endpoints.
 */
class PlatformApiHandlerTest {

    private PlatformApiHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        handler = new PlatformApiHandler();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testHandleHealthCheck() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/health");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        assertEquals("ok", responseMap.get("status"));
        assertNotNull(responseMap.get("timestamp"));
        assertTrue(responseMap.get("timestamp") instanceof Number);
    }

    @Test
    void testHandlePlatformInfo() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/platform/info");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        assertEquals("JPlatform", responseMap.get("platform"));
        assertEquals("1.0", responseMap.get("version"));
        assertNotNull(responseMap.get("uptimeMillis"));
        assertNotNull(responseMap.get("jvmVersion"));
        assertNotNull(responseMap.get("jvmVendor"));
        assertNotNull(responseMap.get("osName"));
        assertNotNull(responseMap.get("osVersion"));
        assertNotNull(responseMap.get("availableProcessors"));

        assertTrue(responseMap.get("uptimeMillis") instanceof Number);
        assertTrue(responseMap.get("availableProcessors") instanceof Number);
    }

    @Test
    void testHandleEndpointNotFound() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/platform/unknown");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        assertEquals("NotFound", responseMap.get("error"));
        assertTrue(responseMap.get("message").toString().contains("Endpoint not found"));
    }

    @Test
    void testHandleWrongMethod() throws IOException {
        HttpExchange exchange = createMockExchange("POST", "/api/health");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());

        String response = getResponseBody(exchange);
        assertTrue(response.contains("NotFound"));
    }

    @Test
    void testJsonContentTypeHeader() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/health");

        handler.handle(exchange);

        Headers headers = exchange.getResponseHeaders();
        verify(headers).set("Content-Type", "application/json; charset=UTF-8");
    }

    @Test
    void testHealthCheckTimestampIsRecent() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/health");

        long beforeRequest = System.currentTimeMillis();
        handler.handle(exchange);
        long afterRequest = System.currentTimeMillis();

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        long timestamp = ((Number) responseMap.get("timestamp")).longValue();
        assertTrue(timestamp >= beforeRequest && timestamp <= afterRequest,
                "Timestamp should be between request start and end times");
    }

    @Test
    void testPlatformInfoUptimeIncreases() throws IOException {
        HttpExchange exchange1 = createMockExchange("GET", "/api/platform/info");
        handler.handle(exchange1);

        String response1 = getResponseBody(exchange1);
        Map<String, Object> responseMap1 = objectMapper.readValue(response1, Map.class);
        long uptime1 = ((Number) responseMap1.get("uptimeMillis")).longValue();

        // Wait a bit
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        HttpExchange exchange2 = createMockExchange("GET", "/api/platform/info");
        handler.handle(exchange2);

        String response2 = getResponseBody(exchange2);
        Map<String, Object> responseMap2 = objectMapper.readValue(response2, Map.class);
        long uptime2 = ((Number) responseMap2.get("uptimeMillis")).longValue();

        assertTrue(uptime2 >= uptime1, "Uptime should increase or stay the same");
    }

    @Test
    void testPlatformInfoJvmVersion() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/platform/info");

        handler.handle(exchange);

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        String jvmVersion = (String) responseMap.get("jvmVersion");
        assertNotNull(jvmVersion);
        assertFalse(jvmVersion.isEmpty());
        assertEquals(System.getProperty("java.version"), jvmVersion);
    }

    @Test
    void testPlatformInfoJvmVendor() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/platform/info");

        handler.handle(exchange);

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        String jvmVendor = (String) responseMap.get("jvmVendor");
        assertNotNull(jvmVendor);
        assertFalse(jvmVendor.isEmpty());
        assertEquals(System.getProperty("java.vendor"), jvmVendor);
    }

    @Test
    void testPlatformInfoOsName() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/platform/info");

        handler.handle(exchange);

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        String osName = (String) responseMap.get("osName");
        assertNotNull(osName);
        assertFalse(osName.isEmpty());
        assertEquals(System.getProperty("os.name"), osName);
    }

    @Test
    void testPlatformInfoOsVersion() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/platform/info");

        handler.handle(exchange);

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        String osVersion = (String) responseMap.get("osVersion");
        assertNotNull(osVersion);
        assertFalse(osVersion.isEmpty());
        assertEquals(System.getProperty("os.version"), osVersion);
    }

    @Test
    void testPlatformInfoAvailableProcessors() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/platform/info");

        handler.handle(exchange);

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        int availableProcessors = ((Number) responseMap.get("availableProcessors")).intValue();
        assertTrue(availableProcessors > 0);
        assertEquals(Runtime.getRuntime().availableProcessors(), availableProcessors);
    }

    @Test
    void testErrorResponseFormat() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/platform/invalid");

        handler.handle(exchange);

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        assertNotNull(responseMap.get("error"));
        assertNotNull(responseMap.get("message"));
        assertNotNull(responseMap.get("status"));
        assertNotNull(responseMap.get("timestamp"));

        assertEquals(404, ((Number) responseMap.get("status")).intValue());
    }

    @Test
    void testHealthCheckMultipleCalls() throws IOException {
        // Verify health check can be called multiple times
        for (int i = 0; i < 5; i++) {
            HttpExchange exchange = createMockExchange("GET", "/api/health");
            handler.handle(exchange);

            verify(exchange).sendResponseHeaders(eq(200), anyLong());

            String response = getResponseBody(exchange);
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            assertEquals("ok", responseMap.get("status"));
        }
    }

    @Test
    void testPlatformInfoMultipleCalls() throws IOException {
        // Verify platform info can be called multiple times
        for (int i = 0; i < 5; i++) {
            HttpExchange exchange = createMockExchange("GET", "/api/platform/info");
            handler.handle(exchange);

            verify(exchange).sendResponseHeaders(eq(200), anyLong());

            String response = getResponseBody(exchange);
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            assertEquals("JPlatform", responseMap.get("platform"));
        }
    }

    @Test
    void testInvalidPath() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/invalid/endpoint");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());
    }

    @Test
    void testRootPathNotFound() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/platform");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());
    }

    @Test
    void testDeleteMethodNotFound() throws IOException {
        HttpExchange exchange = createMockExchange("DELETE", "/api/health");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());
    }

    @Test
    void testPutMethodNotFound() throws IOException {
        HttpExchange exchange = createMockExchange("PUT", "/api/health");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());
    }

    @Test
    void testResponseContainsValidJson() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/health");

        handler.handle(exchange);

        String response = getResponseBody(exchange);

        // Verify it's valid JSON by parsing it
        assertDoesNotThrow(() -> objectMapper.readValue(response, Map.class));
    }

    @Test
    void testPlatformInfoResponseContainsValidJson() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/platform/info");

        handler.handle(exchange);

        String response = getResponseBody(exchange);

        // Verify it's valid JSON by parsing it
        assertDoesNotThrow(() -> objectMapper.readValue(response, Map.class));
    }

    /**
     * Helper method to create a mock HttpExchange.
     */
    private HttpExchange createMockExchange(String method, String path) throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);

        when(exchange.getRequestMethod()).thenReturn(method);
        when(exchange.getRequestURI()).thenReturn(URI.create(path));

        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);
        when(exchange.getRequestBody()).thenReturn(inputStream);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(outputStream);

        Headers responseHeaders = mock(Headers.class);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        return exchange;
    }

    /**
     * Helper method to get response body from mock exchange.
     */
    private String getResponseBody(HttpExchange exchange) {
        ByteArrayOutputStream outputStream = (ByteArrayOutputStream) exchange.getResponseBody();
        return outputStream.toString();
    }
}
