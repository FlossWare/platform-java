package org.flossware.jplatform.rest.netty;

import org.flossware.jplatform.api.ServerShutdownException;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NettyApiServer startup, validation, and lifecycle.
 * Tests server start/stop behavior and route modification safety.
 */
class NettyApiServerStartupTest {

    // Use high ports for testing to avoid permission issues
    private static final int TEST_PORT = 18080;
    private static final int TEST_PORT_2 = 18081;
    private static final int TEST_PORT_3 = 18082;

    @Test
    void testStartAndStop() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();

        try {
            assertTrue(server.isRunning());
        } finally {
            server.stop();
            assertFalse(server.isRunning());
        }
    }

    @Test
    void testStartTwiceIsIdempotent() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();
        server.start(); // Second start should be no-op

        try {
            assertTrue(server.isRunning());
        } finally {
            server.stop();
        }
    }

    @Test
    void testAddRouteWhileRunning() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();

        try {
            Function<String, String> handler = input -> "response";
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                server.addRoute("/test", handler)
            );
            assertTrue(exception.getMessage().contains("Cannot add routes while server is running"));
        } finally {
            server.stop();
        }
    }

    @Test
    void testRemoveRouteWhileRunning() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.addRoute("/test", input -> "response");
        server.start();

        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                server.removeRoute("/test")
            );
            assertTrue(exception.getMessage().contains("Cannot remove routes while server is running"));
        } finally {
            server.stop();
        }
    }

    @Test
    void testStartWithRoutesAlreadyAdded() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.addRoute("/api/users", input -> "{\"users\":[]}");
        server.addRoute("/api/products", input -> "{\"products\":[]}");

        server.start();

        try {
            assertTrue(server.isRunning());
            assertEquals(2, server.getRoutes().size());
        } finally {
            server.stop();
        }
    }

    @Test
    void testCloseAfterStart() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();
        assertTrue(server.isRunning());

        server.close();
        assertFalse(server.isRunning());
    }

    @Test
    void testStartWithCustomThreadCounts() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .bossThreads(2)
            .workerThreads(4)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();

        try {
            assertTrue(server.isRunning());
        } finally {
            server.stop();
        }
    }

    @Test
    void testStartWithZeroWorkerThreads() throws Exception {
        // 0 worker threads = auto-detect (should work)
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .workerThreads(0)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();

        try {
            assertTrue(server.isRunning());
        } finally {
            server.stop();
        }
    }

    @Test
    void testStartWithCustomMaxContentLength() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .maxContentLength(2048576) // 2MB
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();

        try {
            assertTrue(server.isRunning());
        } finally {
            server.stop();
        }
    }

    @Test
    void testStartWithIpv4Address() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .host("127.0.0.1")
            .port(TEST_PORT_2)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();

        try {
            assertTrue(server.isRunning());
        } finally {
            server.stop();
        }
    }

    @Test
    void testStartWithBindAllAddress() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .host("0.0.0.0")
            .port(TEST_PORT_3)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();

        try {
            assertTrue(server.isRunning());
        } finally {
            server.stop();
        }
    }

    @Test
    void testStopWithoutStart() throws ServerShutdownException {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        // Should not throw
        assertDoesNotThrow(() -> server.stop());
        assertFalse(server.isRunning());
    }

    @Test
    void testMultipleStopCalls() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();
        server.stop();

        // Multiple stops should be safe
        assertDoesNotThrow(() -> server.stop());
        assertFalse(server.isRunning());
    }

    @Test
    void testStartStopStartCycle() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);

        // First cycle
        server.start();
        assertTrue(server.isRunning());
        server.stop();
        assertFalse(server.isRunning());

        // Second cycle - should work again
        server.start();
        assertTrue(server.isRunning());
        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void testAddRouteAfterStop() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();
        server.stop();

        // Should be able to add routes after stopping
        assertDoesNotThrow(() ->
            server.addRoute("/test", input -> "response")
        );
        assertTrue(server.getRoutes().containsKey("/test"));
    }

    @Test
    void testIsRunningBeforeStart() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        assertFalse(server.isRunning());
    }

    @Test
    void testIsRunningDuringOperation() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);

        assertFalse(server.isRunning());

        server.start();
        assertTrue(server.isRunning());

        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void testCloseWithoutStart() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();
        NettyApiServer server = new NettyApiServer(config);

        assertDoesNotThrow(() -> server.close());
        assertFalse(server.isRunning());
    }

    @Test
    void testCloseIdempotent() throws Exception {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(TEST_PORT)
            .build();

        NettyApiServer server = new NettyApiServer(config);
        server.start();
        server.close();

        // Second close should be safe
        assertDoesNotThrow(() -> server.close());
        assertFalse(server.isRunning());
    }
}
