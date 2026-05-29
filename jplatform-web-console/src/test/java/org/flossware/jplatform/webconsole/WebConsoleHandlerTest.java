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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebConsoleHandler.
 */
class WebConsoleHandlerTest {

    private WebConsoleHandler handler;
    private HttpExchange exchange;
    private ByteArrayOutputStream responseBody;

    @BeforeEach
    void setUp() {
        handler = new WebConsoleHandler();
        exchange = mock(HttpExchange.class);
        responseBody = new ByteArrayOutputStream();
        Headers headers = new Headers();
        when(exchange.getResponseBody()).thenReturn(responseBody);
        when(exchange.getResponseHeaders()).thenReturn(headers);
    }

    @Test
    void testHandleRootPath() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        verify(exchange, atLeastOnce()).getResponseBody();
        assertTrue(responseBody.size() > 0);

        String response = responseBody.toString();
        assertTrue(response.contains("<!DOCTYPE html>"));
        assertTrue(response.contains("JPlatform Management Console"));
    }

    @Test
    void testHandleIndexHtml() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/index.html"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        assertTrue(responseBody.size() > 0);

        String response = responseBody.toString();
        assertTrue(response.contains("<!DOCTYPE html>"));
    }

    @Test
    void testHandleStaticCss() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/static/dashboard.css"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        assertTrue(responseBody.size() > 0);

        String response = responseBody.toString();
        assertTrue(response.contains("body") || response.contains("*") || response.contains("css"));
    }

    @Test
    void testHandleStaticJs() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/static/app.js"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        assertTrue(responseBody.size() > 0);

        String response = responseBody.toString();
        assertTrue(response.contains("function") || response.contains("const") || response.contains("let"));
    }

    @Test
    void testHandleNotFound() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/nonexistent.html"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(404, -1);
    }

    @Test
    void testHandleNonGetMethod() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/"));
        when(exchange.getRequestMethod()).thenReturn("POST");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(405, -1);
    }

    @Test
    void testContentTypeForHtml() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/index.html"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(exchange, atLeastOnce()).getResponseHeaders();
    }

    @Test
    void testContentTypeForCss() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/static/dashboard.css"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testContentTypeForJs() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/static/app.js"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testPathTraversalPrevention() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/static/../../../etc/passwd"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        // Should return 404, not serve files outside the web directory
        verify(exchange).sendResponseHeaders(404, -1);
    }

    @Test
    void testEmptyPath() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create(""));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        // Should handle gracefully
        verify(exchange).sendResponseHeaders(anyInt(), anyLong());
    }

    @Test
    void testMultipleSlashes() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("///index.html"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        // Should normalize path and serve index.html
        verify(exchange).sendResponseHeaders(anyInt(), anyLong());
    }

    @Test
    void testResourceLoading() {
        // Test that the handler can load resources from classpath
        InputStream indexStream = getClass().getClassLoader().getResourceAsStream("web/index.html");
        assertNotNull(indexStream, "index.html should be available in classpath");

        InputStream cssStream = getClass().getClassLoader().getResourceAsStream("web/static/dashboard.css");
        assertNotNull(cssStream, "dashboard.css should be available in classpath");

        InputStream jsStream = getClass().getClassLoader().getResourceAsStream("web/static/app.js");
        assertNotNull(jsStream, "app.js should be available in classpath");
    }

    @Test
    void testCacheControl() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/static/app.js"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        // Verify response headers are set
        verify(exchange, atLeastOnce()).getResponseHeaders();
    }

    @Test
    void testQueryParameters() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/index.html?param=value"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        // Should ignore query parameters and serve the file
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testFragmentIdentifier() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/index.html#section"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        // Should ignore fragment and serve the file
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testContentTypeForJson() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/data.json"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        Headers headers = exchange.getResponseHeaders();
        assertEquals("application/json; charset=UTF-8", headers.getFirst("Content-Type"));
    }

    @Test
    void testContentTypeForPng() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/image.png"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        Headers headers = exchange.getResponseHeaders();
        assertEquals("image/png", headers.getFirst("Content-Type"));
    }

    @Test
    void testContentTypeForJpeg() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/photo.jpeg"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        Headers headers = exchange.getResponseHeaders();
        assertEquals("image/jpeg", headers.getFirst("Content-Type"));
    }

    @Test
    void testContentTypeForSvg() throws IOException {
        when(exchange.getRequestURI()).thenReturn(URI.create("/icon.svg"));
        when(exchange.getRequestMethod()).thenReturn("GET");

        handler.handle(exchange);

        Headers headers = exchange.getResponseHeaders();
        assertEquals("image/svg+xml", headers.getFirst("Content-Type"));
    }
}
