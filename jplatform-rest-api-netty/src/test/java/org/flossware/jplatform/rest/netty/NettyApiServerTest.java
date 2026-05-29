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

import org.flossware.jplatform.api.ServerShutdownException;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyApiServerTest {

    @Test
    void testConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new NettyApiServer(null);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testGetPort() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(9090)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        assertEquals(9090, server.getPort());
    }

    @Test
    void testIsRunningInitially() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();

        NettyApiServer server = new NettyApiServer(config);
        assertFalse(server.isRunning());
    }

    @Test
    void testAddRoute() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        Function<String, String> handler = input -> "{\"result\":\"ok\"}";
        server.addRoute("/api/test", handler);

        assertTrue(server.getRoutes().containsKey("/api/test"));
    }

    @Test
    void testRemoveRoute() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        Function<String, String> handler = input -> "{\"result\":\"ok\"}";
        server.addRoute("/api/test", handler);
        server.removeRoute("/api/test");

        assertFalse(server.getRoutes().containsKey("/api/test"));
    }

    @Test
    void testGetConfig() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(9090)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        assertSame(config, server.getConfig());
    }

    @Test
    void testMultipleRoutes() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.addRoute("/api/users", input -> "{\"users\":[]}");
        server.addRoute("/api/products", input -> "{\"products\":[]}");
        server.addRoute("/api/orders", input -> "{\"orders\":[]}");

        assertEquals(3, server.getRoutes().size());
        assertTrue(server.getRoutes().containsKey("/api/users"));
        assertTrue(server.getRoutes().containsKey("/api/products"));
        assertTrue(server.getRoutes().containsKey("/api/orders"));
    }

    @Test
    void testRouteHandler() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        Function<String, String> handler = input -> "{\"echo\":\"" + input + "\"}";
        server.addRoute("/api/echo", handler);

        Function<String, String> retrievedHandler = server.getRoutes().get("/api/echo");
        assertNotNull(retrievedHandler);
        assertEquals("{\"echo\":\"test\"}", retrievedHandler.apply("test"));
    }

    @Test
    void testReplaceRoute() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.addRoute("/api/test", input -> "v1");
        server.addRoute("/api/test", input -> "v2");

        Function<String, String> handler = server.getRoutes().get("/api/test");
        assertEquals("v2", handler.apply(""));
    }

    @Test
    void testStopIdempotent() throws ServerShutdownException {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.stop();
        server.stop();

        assertFalse(server.isRunning());
    }

    @Test
    void testCloseStopsServer() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.close();
        assertFalse(server.isRunning());
    }

    @Test
    void testCustomHost() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .host("127.0.0.1")
            .build();

        NettyApiServer server = new NettyApiServer(config);
        assertEquals("127.0.0.1", server.getConfig().getHost());
    }

    @Test
    void testCustomMaxContentLength() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .maxContentLength(131072)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        assertEquals(131072, server.getConfig().getMaxContentLength());
    }

    @Test
    void testCustomBossThreads() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .bossThreads(2)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        assertEquals(2, server.getConfig().getBossThreads());
    }

    @Test
    void testCustomWorkerThreads() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .workerThreads(8)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        assertEquals(8, server.getConfig().getWorkerThreads());
    }

    @Test
    void testKeepAliveDisabled() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .keepAlive(false)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        assertFalse(server.getConfig().isKeepAlive());
    }

    @Test
    void testCustomBacklog() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .backlog(256)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        assertEquals(256, server.getConfig().getBacklog());
    }

    @Test
    void testRemoveNonExistentRoute() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        assertDoesNotThrow(() -> server.removeRoute("/api/nonexistent"));
    }

    @Test
    void testGetRoutesReturnsInternalMap() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.addRoute("/test", input -> "response");
        assertTrue(server.getRoutes().containsKey("/test"));
    }

    @Test
    void testGetRoutesInitiallyEmpty() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        assertTrue(server.getRoutes().isEmpty());
    }

    @Test
    void testAddNullRoute() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        assertThrows(IllegalArgumentException.class, () ->
            server.addRoute(null, input -> "response")
        );
    }

    @Test
    void testAddRouteWithNullHandler() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        assertThrows(IllegalArgumentException.class, () ->
            server.addRoute("/test", null)
        );
    }

    @Test
    void testRemoveNullRoute() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        assertThrows(NullPointerException.class, () ->
            server.removeRoute(null)
        );
    }

    @Test
    void testAddEmptyRoute() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        assertThrows(IllegalArgumentException.class, () ->
            server.addRoute("", input -> "response")
        );
    }

    @Test
    void testAddRouteWithSlashPrefix() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.addRoute("/api/v1/users", input -> "users");
        assertTrue(server.getRoutes().containsKey("/api/v1/users"));
    }

    @Test
    void testAddRouteWithoutSlashPrefix() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.addRoute("api/users", input -> "users");
        assertTrue(server.getRoutes().containsKey("api/users"));
    }

    @Test
    void testAddMultipleRoutesRemoveOne() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.addRoute("/route1", input -> "1");
        server.addRoute("/route2", input -> "2");
        server.addRoute("/route3", input -> "3");

        server.removeRoute("/route2");

        assertEquals(2, server.getRoutes().size());
        assertTrue(server.getRoutes().containsKey("/route1"));
        assertFalse(server.getRoutes().containsKey("/route2"));
        assertTrue(server.getRoutes().containsKey("/route3"));
    }

    @Test
    void testRouteHandlerWithComplexLogic() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        Function<String, String> complexHandler = input -> {
            if (input == null || input.isEmpty()) {
                return "{\"error\":\"empty\"}";
            }
            return "{\"processed\":\"" + input.toUpperCase() + "\"}";
        };

        server.addRoute("/api/process", complexHandler);

        Function<String, String> handler = server.getRoutes().get("/api/process");
        assertEquals("{\"processed\":\"HELLO\"}", handler.apply("hello"));
        assertEquals("{\"error\":\"empty\"}", handler.apply(""));
    }

    @Test
    void testGetPortDefaultValue() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        assertEquals(8080, server.getPort());
    }

    @Test
    void testIsRunningAfterStopCall() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void testCloseIdempotent() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.close();
        server.close();

        assertFalse(server.isRunning());
    }

    @Test
    void testAddSameRouteTwice() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.addRoute("/test", input -> "first");
        server.addRoute("/test", input -> "second");

        assertEquals(1, server.getRoutes().size());
    }

    @Test
    void testRemoveThenAddSameRoute() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        server.addRoute("/test", input -> "first");
        server.removeRoute("/test");
        server.addRoute("/test", input -> "second");

        Function<String, String> handler = server.getRoutes().get("/test");
        assertEquals("second", handler.apply(""));
    }

    @Test
    void testConfigPortRange() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(1024)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        assertEquals(1024, server.getPort());
    }

    @Test
    void testConfigBuilderWithEmptyHost() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            NettyApiServerConfig.builder()
                .host("")
                .port(8080)
                .build();
        });
        assertTrue(exception.getMessage().contains("Host must be specified"));
    }

    @Test
    void testConfigBuilderWithInvalidPortNegative() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .port(-1);
        });
        assertTrue(exception.getMessage().contains("Port must be between 1 and 65535"));
    }

    @Test
    void testConfigBuilderWithInvalidPortTooHigh() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .port(70000);
        });
        assertTrue(exception.getMessage().contains("Port must be between 1 and 65535"));
    }

    @Test
    void testConfigBuilderWithInvalidBossThreads() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .bossThreads(0);
        });
        assertTrue(exception.getMessage().contains("Boss threads must be at least 1"));
    }

    @Test
    void testConfigBuilderWithInvalidWorkerThreads() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .workerThreads(-1);
        });
        assertTrue(exception.getMessage().contains("Worker threads must be at least 0"));
    }

    @Test
    void testConfigBuilderWithInvalidMaxContentLength() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .maxContentLength(512);
        });
        assertTrue(exception.getMessage().contains("Max content length must be at least 1024"));
    }

    @Test
    void testConfigBuilderWithMaxContentLengthTooLarge() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .maxContentLength(200 * 1024 * 1024);
        });
        assertTrue(exception.getMessage().contains("exceeds maximum allowed"));
    }

    @Test
    void testConfigBuilderWithInvalidBacklog() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .backlog(0);
        });
        assertTrue(exception.getMessage().contains("Backlog must be at least 1"));
    }

    @Test
    void testConfigBuilderWithInvalidGlobalRateLimit() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .globalRateLimit(-1);
        });
        assertTrue(exception.getMessage().contains("Global rate limit must be >= 0"));
    }

    @Test
    void testConfigBuilderWithInvalidPerIpRateLimit() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .perIpRateLimit(-1);
        });
        assertTrue(exception.getMessage().contains("Per-IP rate limit must be >= 0"));
    }

    @Test
    void testConfigBuilderWithInvalidReadTimeout() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .readTimeout(0);
        });
        assertTrue(exception.getMessage().contains("Read timeout must be positive"));
    }

    @Test
    void testConfigBuilderWithInvalidWriteTimeout() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .writeTimeout(-1);
        });
        assertTrue(exception.getMessage().contains("Write timeout must be positive"));
    }

    @Test
    void testAddRouteWhileRunning() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(18091)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();

        try {
            Exception exception = assertThrows(IllegalStateException.class, () -> {
                server.addRoute("/test", input -> "response");
            });
            assertTrue(exception.getMessage().contains("Cannot add routes while server is running"));
        } finally {
            server.stop();
        }
    }

    @Test
    void testRemoveRouteWhileRunning() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(18092)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.addRoute("/test", input -> "response");
        server.start();

        try {
            Exception exception = assertThrows(IllegalStateException.class, () -> {
                server.removeRoute("/test");
            });
            assertTrue(exception.getMessage().contains("Cannot remove routes while server is running"));
        } finally {
            server.stop();
        }
    }
}
