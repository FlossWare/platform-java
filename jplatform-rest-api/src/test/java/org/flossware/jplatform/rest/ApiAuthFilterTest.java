package org.flossware.jplatform.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.flossware.jplatform.api.ApiServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ApiAuthFilter.
 * Tests API key authentication, header validation, and filter behavior.
 */
class ApiAuthFilterTest {

    private ApiServerConfig config;
    private ApiAuthFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testConstructorWithConfig() {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("test-key")
                .build();

        filter = new ApiAuthFilter(config);

        assertNotNull(filter);
    }

    @Test
    void testDescription() {
        config = ApiServerConfig.builder()
                .port(8080)
                .build();

        filter = new ApiAuthFilter(config);

        assertEquals("API Key Authentication Filter", filter.description());
    }

    @Test
    void testDoFilterAuthDisabled() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(false)
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        // Should pass through without checking auth
        verify(chain).doFilter(exchange);
        verify(exchange, never()).sendResponseHeaders(anyInt(), anyLong());
    }

    @Test
    void testDoFilterOptionsRequest() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("test-key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("OPTIONS");
        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        // OPTIONS requests should pass through for CORS preflight
        verify(chain).doFilter(exchange);
        verify(exchange, never()).sendResponseHeaders(anyInt(), anyLong());
    }

    @Test
    void testDoFilterValidApiKey() throws IOException {
        String apiKey = "valid-api-key-123";
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey(apiKey)
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Headers requestHeaders = new Headers();
        requestHeaders.add("X-API-Key", apiKey);
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        // Should pass through with valid API key
        verify(chain).doFilter(exchange);
        verify(exchange, never()).sendResponseHeaders(eq(401), anyLong());
    }

    @Test
    void testDoFilterInvalidApiKey() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("correct-key")
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Headers requestHeaders = new Headers();
        requestHeaders.add("X-API-Key", "wrong-key");
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        // Should return 401 Unauthorized
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
        verify(chain, never()).doFilter(exchange);

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        assertEquals("Unauthorized", responseMap.get("error"));
        assertEquals(401, ((Number) responseMap.get("status")).intValue());
    }

    @Test
    void testDoFilterMissingApiKey() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("test-key")
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Headers requestHeaders = new Headers();
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        // Should return 401 Unauthorized
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
        verify(chain, never()).doFilter(exchange);

        String response = getResponseBody(exchange);
        assertTrue(response.contains("Unauthorized"));
    }

    @Test
    void testDoFilterEmptyApiKey() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("test-key")
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Headers requestHeaders = new Headers();
        requestHeaders.add("X-API-Key", "");
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        // Should return 401 Unauthorized
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
        verify(chain, never()).doFilter(exchange);
    }

    @Test
    void testDoFilterCustomApiKeyHeader() throws IOException {
        String apiKey = "custom-key";
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey(apiKey)
                .apiKeyHeader("Authorization")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Headers requestHeaders = new Headers();
        requestHeaders.add("Authorization", apiKey);
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        // Should pass through with valid API key in custom header
        verify(chain).doFilter(exchange);
        verify(exchange, never()).sendResponseHeaders(eq(401), anyLong());
    }

    @Test
    void testDoFilterWrongHeaderName() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("test-key")
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Headers requestHeaders = new Headers();
        requestHeaders.add("Authorization", "test-key");
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        // Should return 401 because key is in wrong header
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
        verify(chain, never()).doFilter(exchange);
    }

    @Test
    void testUnauthorizedResponseFormat() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("test-key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Headers requestHeaders = new Headers();
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        assertEquals("Unauthorized", responseMap.get("error"));
        assertNotNull(responseMap.get("message"));
        assertEquals(401, ((Number) responseMap.get("status")).intValue());

        Headers responseHeaders = exchange.getResponseHeaders();
        verify(responseHeaders).set("Content-Type", "application/json; charset=UTF-8");
    }

    @Test
    void testMultipleRequestsWithValidKey() throws IOException {
        String apiKey = "valid-key";
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey(apiKey)
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        Filter.Chain chain = mock(Filter.Chain.class);

        // Test multiple requests
        for (int i = 0; i < 5; i++) {
            HttpExchange exchange = createMockExchange("GET");
            Headers requestHeaders = new Headers();
            requestHeaders.add("X-API-Key", apiKey);
            when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

            filter.doFilter(exchange, chain);

            verify(chain, times(i + 1)).doFilter(any(HttpExchange.class));
        }
    }

    @Test
    void testMultipleRequestsWithInvalidKey() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("correct-key")
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        Filter.Chain chain = mock(Filter.Chain.class);

        // Test multiple requests with invalid key
        for (int i = 0; i < 5; i++) {
            HttpExchange exchange = createMockExchange("GET");
            Headers requestHeaders = new Headers();
            requestHeaders.add("X-API-Key", "wrong-key");
            when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

            filter.doFilter(exchange, chain);

            verify(exchange).sendResponseHeaders(eq(401), anyLong());
        }

        // Chain should never be called
        verify(chain, never()).doFilter(any(HttpExchange.class));
    }

    @Test
    void testPostMethodWithValidKey() throws IOException {
        String apiKey = "test-key";
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey(apiKey)
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("POST");
        Headers requestHeaders = new Headers();
        requestHeaders.add("X-API-Key", apiKey);
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        verify(chain).doFilter(exchange);
    }

    @Test
    void testDeleteMethodWithValidKey() throws IOException {
        String apiKey = "test-key";
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey(apiKey)
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("DELETE");
        Headers requestHeaders = new Headers();
        requestHeaders.add("X-API-Key", apiKey);
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        verify(chain).doFilter(exchange);
    }

    @Test
    void testCaseSensitiveApiKey() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("TestKey123")
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Headers requestHeaders = new Headers();
        requestHeaders.add("X-API-Key", "testkey123"); // Wrong case
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        // Should return 401 because keys are case-sensitive
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
        verify(chain, never()).doFilter(exchange);
    }

    @Test
    void testNullApiKeyHeader() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("test-key")
                .apiKeyHeader("X-API-Key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Headers requestHeaders = mock(Headers.class);
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
        when(requestHeaders.getFirst("X-API-Key")).thenReturn(null);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        // Should return 401 for null API key
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
        verify(chain, never()).doFilter(exchange);
    }

    @Test
    void testUnauthorizedMessageContent() throws IOException {
        config = ApiServerConfig.builder()
                .port(8080)
                .enableAuth(true)
                .apiKey("test-key")
                .build();

        filter = new ApiAuthFilter(config);

        HttpExchange exchange = createMockExchange("GET");
        Headers requestHeaders = new Headers();
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Filter.Chain chain = mock(Filter.Chain.class);

        filter.doFilter(exchange, chain);

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        String message = (String) responseMap.get("message");
        assertTrue(message.contains("API key") || message.contains("Unauthorized"),
                "Error message should mention API key or authorization");
    }

    /**
     * Helper method to create a mock HttpExchange.
     */
    private HttpExchange createMockExchange(String method) throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);

        when(exchange.getRequestMethod()).thenReturn(method);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(outputStream);

        Headers responseHeaders = mock(Headers.class);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 12345);
        when(exchange.getRemoteAddress()).thenReturn(remoteAddress);

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
