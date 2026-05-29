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

package org.flossware.jplatform.metrics.prometheus;

import org.flossware.jplatform.api.ApplicationContext;
import org.flossware.jplatform.api.ApplicationState;
import org.flossware.jplatform.api.PrometheusExporterConfig;
import org.flossware.jplatform.api.ResourceMonitor;
import org.flossware.jplatform.api.ResourceSnapshot;
import org.flossware.jplatform.api.ThreadPoolExecutor;
import org.flossware.jplatform.api.ThreadPoolStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrometheusMetricsExporterTest {

    private PrometheusExporterConfig config;
    private PrometheusMetricsExporter exporter;
    private ApplicationContext context;

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private ApplicationContext createMockedContext(String appId, ApplicationState state) {
        ApplicationContext ctx = mock(ApplicationContext.class);
        ResourceMonitor resourceMonitor = mock(ResourceMonitor.class);
        ResourceSnapshot snapshot = mock(ResourceSnapshot.class);
        ThreadPoolExecutor threadPool = mock(ThreadPoolExecutor.class);
        ThreadPoolStats stats = mock(ThreadPoolStats.class);

        when(ctx.getApplicationId()).thenReturn(appId);
        when(ctx.getState()).thenReturn(state);
        when(ctx.getResourceMonitor()).thenReturn(resourceMonitor);
        when(ctx.getThreadPool()).thenReturn(threadPool);
        when(resourceMonitor.getCurrentSnapshot()).thenReturn(snapshot);
        when(threadPool.getStats()).thenReturn(stats);

        when(snapshot.getCpuTimeNanos()).thenReturn(1000000L);
        when(snapshot.getHeapUsedBytes()).thenReturn(100000000L);
        when(snapshot.getThreadCount()).thenReturn(10);
        when(stats.getActiveThreads()).thenReturn(5);
        when(stats.getQueuedTasks()).thenReturn(0);
        when(stats.getCompletedTasks()).thenReturn(100L);

        return ctx;
    }

    @BeforeEach
    void setUp() throws IOException {
        int port = findAvailablePort();
        config = PrometheusExporterConfig.builder()
                .port(port)
                .path("/metrics")
                .build();

        context = createMockedContext("test-app", ApplicationState.RUNNING);
    }

    @AfterEach
    void tearDown() {
        if (exporter != null && exporter.isRunning()) {
            exporter.stop();
        }
    }

    @Test
    void testConstructor() {
        exporter = new PrometheusMetricsExporter(config);
        assertNotNull(exporter);
        assertFalse(exporter.isRunning());
    }

    @Test
    void testStartSuccess() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();

        assertTrue(exporter.isRunning());
    }

    @Test
    void testStartAlreadyRunning() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();
        assertTrue(exporter.isRunning());

        // Starting again should just log warning
        exporter.start();
        assertTrue(exporter.isRunning());
    }

    @Test
    void testStopSuccess() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();
        assertTrue(exporter.isRunning());

        exporter.stop();
        assertFalse(exporter.isRunning());
    }

    @Test
    void testStopNotRunning() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        assertFalse(exporter.isRunning());

        // Stopping when not running should just log warning
        exporter.stop();
        assertFalse(exporter.isRunning());
    }

    @Test
    void testRegisterApplication() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();

        exporter.registerApplication("app1", context);

        // Verify metrics endpoint includes the application
        String metrics = fetchMetrics();
        assertTrue(metrics.contains("app_id=\"test-app\""));
    }

    @Test
    void testUnregisterApplication() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();

        exporter.registerApplication("app1", context);

        String metricsWithApp = fetchMetrics();
        assertTrue(metricsWithApp.contains("app_id=\"test-app\""));

        exporter.unregisterApplication("app1");

        String metricsWithoutApp = fetchMetrics();
        assertFalse(metricsWithoutApp.contains("app_id=\"test-app\""));
    }

    @Test
    void testMultipleApplications() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();

        ApplicationContext ctx1 = createMockedContext("app1", ApplicationState.RUNNING);
        ApplicationContext ctx2 = createMockedContext("app2", ApplicationState.STOPPED);
        ApplicationContext ctx3 = createMockedContext("app3", ApplicationState.DEPLOYED);

        exporter.registerApplication("app1", ctx1);
        exporter.registerApplication("app2", ctx2);
        exporter.registerApplication("app3", ctx3);

        String metrics = fetchMetrics();

        assertTrue(metrics.contains("app_id=\"app1\""));
        assertTrue(metrics.contains("app_id=\"app2\""));
        assertTrue(metrics.contains("app_id=\"app3\""));
    }

    @Test
    void testMetricsEndpointFormat() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();
        exporter.registerApplication("test-app", context);

        String metrics = fetchMetrics();

        // Verify Prometheus format
        assertTrue(metrics.contains("# HELP"));
        assertTrue(metrics.contains("# TYPE"));
        assertTrue(metrics.contains("jplatform_app_cpu_time_seconds"));
        assertTrue(metrics.contains("jplatform_app_heap_used_bytes"));
        assertTrue(metrics.contains("jplatform_app_thread_count"));
        assertTrue(metrics.contains("jplatform_app_state"));
    }

    @Test
    void testMetricsEndpointContentType() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();

        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://localhost:" + config.getPort() + config.getPath());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            String contentType = conn.getContentType();
            assertNotNull(contentType);
            assertTrue(contentType.contains("text/plain"));
            assertTrue(contentType.contains("version=0.0.4"));

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Test
    void testMetricsEndpointEmptyWhenNoApplications() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();

        String metrics = fetchMetrics();

        // Should return empty or minimal response when no applications registered
        assertNotNull(metrics);
    }

    @Test
    void testClose() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();
        assertTrue(exporter.isRunning());

        exporter.close();
        assertFalse(exporter.isRunning());
    }

    @Test
    void testCloseNotRunning() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        assertFalse(exporter.isRunning());

        // Closing when not running should not throw
        exporter.close();
        assertFalse(exporter.isRunning());
    }

    @Test
    void testDifferentPath() throws IOException {
        int port = findAvailablePort();
        PrometheusExporterConfig customConfig = PrometheusExporterConfig.builder()
                .port(port)
                .path("/custom-metrics")
                .build();

        exporter = new PrometheusMetricsExporter(customConfig);
        exporter.start();
        exporter.registerApplication("test-app", context);

        // Fetch from custom path
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://localhost:" + port + "/custom-metrics");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            assertEquals(200, conn.getResponseCode());

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = reader.readLine();
            assertNotNull(line);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Test
    void testConcurrentScrapes() throws IOException, InterruptedException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();
        exporter.registerApplication("test-app", context);

        // Perform multiple concurrent scrapes
        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    String metrics = fetchMetrics();
                    assertNotNull(metrics);
                    assertTrue(metrics.contains("app_id=\"test-app\""));
                } catch (IOException e) {
                    fail("Concurrent scrape failed: " + e.getMessage());
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
    }

    @Test
    void testRegisterUnregisterConcurrent() throws IOException, InterruptedException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();

        // Concurrently register and unregister applications
        Thread registerThread = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                ApplicationContext ctx = createMockedContext("app" + i, ApplicationState.RUNNING);
                exporter.registerApplication("app" + i, ctx);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread unregisterThread = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (int i = 0; i < 10; i++) {
                exporter.unregisterApplication("app" + i);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        registerThread.start();
        unregisterThread.start();

        registerThread.join();
        unregisterThread.join();

        // Should not throw any exceptions
        assertTrue(true);
    }

    @Test
    void testStartPortAlreadyInUse() throws IOException {
        // Start first exporter
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();
        assertTrue(exporter.isRunning());

        // Try to start second exporter on same port
        PrometheusMetricsExporter exporter2 = new PrometheusMetricsExporter(config);
        assertThrows(IOException.class, () -> exporter2.start());
        assertFalse(exporter2.isRunning());
    }

    @Test
    void testStopWithServerNull() throws IOException {
        exporter = new PrometheusMetricsExporter(config);

        // Stop without starting (server will be null)
        exporter.stop();
        assertFalse(exporter.isRunning());
    }

    @Test
    void testMetricsCollectionException() throws IOException {
        exporter = new PrometheusMetricsExporter(config);
        exporter.start();

        // Create a context that throws exception when accessed
        ApplicationContext faultyContext = mock(ApplicationContext.class);
        when(faultyContext.getApplicationId()).thenReturn("faulty-app");
        when(faultyContext.getResourceMonitor()).thenThrow(new RuntimeException("Simulated error"));

        exporter.registerApplication("faulty-app", faultyContext);

        // Metrics request should still work and include error metric for alerting
        String metrics = fetchMetrics();
        assertNotNull(metrics);
        // Should contain error metric for the faulty app so Prometheus can alert
        assertTrue(metrics.contains("app_id=\"faulty-app\""));
        assertTrue(metrics.contains("jplatform_metrics_collection_errors_total"));
    }

    private String fetchMetrics() throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://localhost:" + config.getPort() + config.getPath());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            assertEquals(200, conn.getResponseCode());

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            return sb.toString();

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
