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
import org.flossware.jplatform.api.ResourceMonitor;
import org.flossware.jplatform.api.ResourceSnapshot;
import org.flossware.jplatform.api.ThreadPoolExecutor;
import org.flossware.jplatform.api.ThreadPoolStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationMetricsCollectorTest {

    private ApplicationContext context;
    private ResourceMonitor resourceMonitor;
    private ResourceSnapshot snapshot;
    private ThreadPoolExecutor threadPool;
    private ThreadPoolStats poolStats;

    @BeforeEach
    void setUp() {
        context = mock(ApplicationContext.class);
        resourceMonitor = mock(ResourceMonitor.class);
        snapshot = mock(ResourceSnapshot.class);
        threadPool = mock(ThreadPoolExecutor.class);
        poolStats = mock(ThreadPoolStats.class);

        when(context.getApplicationId()).thenReturn("test-app");
        when(context.getResourceMonitor()).thenReturn(resourceMonitor);
        when(context.getThreadPool()).thenReturn(threadPool);
        when(context.getState()).thenReturn(ApplicationState.RUNNING);

        when(resourceMonitor.getCurrentSnapshot()).thenReturn(snapshot);
        when(threadPool.getStats()).thenReturn(poolStats);
    }

    @Test
    void testCollectMetricsBasic() {
        when(snapshot.getCpuTimeNanos()).thenReturn(1_000_000_000L); // 1 second
        when(snapshot.getHeapUsedBytes()).thenReturn(134217728L); // 128 MB
        when(snapshot.getThreadCount()).thenReturn(10);
        when(poolStats.getActiveThreads()).thenReturn(5);
        when(poolStats.getQueuedTasks()).thenReturn(3);
        when(poolStats.getCompletedTasks()).thenReturn(100L);

        String metrics = ApplicationMetricsCollector.collectMetrics(context);

        assertNotNull(metrics);
        assertFalse(metrics.isEmpty());

        // Verify CPU time metric
        assertTrue(metrics.contains("# HELP jplatform_app_cpu_time_seconds"));
        assertTrue(metrics.contains("# TYPE jplatform_app_cpu_time_seconds counter"));
        assertTrue(metrics.contains("jplatform_app_cpu_time_seconds{app_id=\"test-app\"} 1.0"));

        // Verify heap metric
        assertTrue(metrics.contains("# HELP jplatform_app_heap_used_bytes"));
        assertTrue(metrics.contains("# TYPE jplatform_app_heap_used_bytes gauge"));
        assertTrue(metrics.contains("jplatform_app_heap_used_bytes{app_id=\"test-app\"} 1.34217728E8"));

        // Verify thread count metric
        assertTrue(metrics.contains("# HELP jplatform_app_thread_count"));
        assertTrue(metrics.contains("# TYPE jplatform_app_thread_count gauge"));
        assertTrue(metrics.contains("jplatform_app_thread_count{app_id=\"test-app\"} 10.0"));

        // Verify thread pool metrics
        assertTrue(metrics.contains("# HELP jplatform_app_threadpool_active"));
        assertTrue(metrics.contains("jplatform_app_threadpool_active{app_id=\"test-app\"} 5.0"));

        assertTrue(metrics.contains("# HELP jplatform_app_threadpool_queued"));
        assertTrue(metrics.contains("jplatform_app_threadpool_queued{app_id=\"test-app\"} 3.0"));

        assertTrue(metrics.contains("# HELP jplatform_app_threadpool_completed"));
        assertTrue(metrics.contains("jplatform_app_threadpool_completed{app_id=\"test-app\"} 100.0"));
    }

    @Test
    void testCollectMetricsStateRunning() {
        when(snapshot.getCpuTimeNanos()).thenReturn(0L);
        when(snapshot.getHeapUsedBytes()).thenReturn(0L);
        when(snapshot.getThreadCount()).thenReturn(0);
        when(poolStats.getActiveThreads()).thenReturn(0);
        when(poolStats.getQueuedTasks()).thenReturn(0);
        when(poolStats.getCompletedTasks()).thenReturn(0L);
        when(context.getState()).thenReturn(ApplicationState.RUNNING);

        String metrics = ApplicationMetricsCollector.collectMetrics(context);

        // Verify state metrics
        assertTrue(metrics.contains("# HELP jplatform_app_state"));
        assertTrue(metrics.contains("# TYPE jplatform_app_state gauge"));

        // RUNNING should be 1.0
        assertTrue(metrics.contains("jplatform_app_state{app_id=\"test-app\",state=\"running\"} 1.0"));

        // Other states should be 0.0
        assertTrue(metrics.contains("jplatform_app_state{app_id=\"test-app\",state=\"deployed\"} 0.0"));
        assertTrue(metrics.contains("jplatform_app_state{app_id=\"test-app\",state=\"stopped\"} 0.0"));
        assertTrue(metrics.contains("jplatform_app_state{app_id=\"test-app\",state=\"failed\"} 0.0"));
    }

    @Test
    void testCollectMetricsStateStopped() {
        when(snapshot.getCpuTimeNanos()).thenReturn(0L);
        when(snapshot.getHeapUsedBytes()).thenReturn(0L);
        when(snapshot.getThreadCount()).thenReturn(0);
        when(poolStats.getActiveThreads()).thenReturn(0);
        when(poolStats.getQueuedTasks()).thenReturn(0);
        when(poolStats.getCompletedTasks()).thenReturn(0L);
        when(context.getState()).thenReturn(ApplicationState.STOPPED);

        String metrics = ApplicationMetricsCollector.collectMetrics(context);

        // STOPPED should be 1.0
        assertTrue(metrics.contains("jplatform_app_state{app_id=\"test-app\",state=\"stopped\"} 1.0"));

        // Other states should be 0.0
        assertTrue(metrics.contains("jplatform_app_state{app_id=\"test-app\",state=\"running\"} 0.0"));
        assertTrue(metrics.contains("jplatform_app_state{app_id=\"test-app\",state=\"deployed\"} 0.0"));
    }

    @Test
    void testCollectMetricsCpuConversion() {
        // Test nanoseconds to seconds conversion
        when(snapshot.getCpuTimeNanos()).thenReturn(5_000_000_000L); // 5 seconds
        when(snapshot.getHeapUsedBytes()).thenReturn(0L);
        when(snapshot.getThreadCount()).thenReturn(0);
        when(poolStats.getActiveThreads()).thenReturn(0);
        when(poolStats.getQueuedTasks()).thenReturn(0);
        when(poolStats.getCompletedTasks()).thenReturn(0L);

        String metrics = ApplicationMetricsCollector.collectMetrics(context);

        assertTrue(metrics.contains("jplatform_app_cpu_time_seconds{app_id=\"test-app\"} 5.0"));
    }

    @Test
    void testCollectMetricsZeroValues() {
        when(snapshot.getCpuTimeNanos()).thenReturn(0L);
        when(snapshot.getHeapUsedBytes()).thenReturn(0L);
        when(snapshot.getThreadCount()).thenReturn(0);
        when(poolStats.getActiveThreads()).thenReturn(0);
        when(poolStats.getQueuedTasks()).thenReturn(0);
        when(poolStats.getCompletedTasks()).thenReturn(0L);

        String metrics = ApplicationMetricsCollector.collectMetrics(context);

        assertNotNull(metrics);
        assertFalse(metrics.isEmpty());

        // Verify zero values are properly formatted
        assertTrue(metrics.contains("jplatform_app_cpu_time_seconds{app_id=\"test-app\"} 0.0"));
        assertTrue(metrics.contains("jplatform_app_heap_used_bytes{app_id=\"test-app\"} 0.0"));
        assertTrue(metrics.contains("jplatform_app_thread_count{app_id=\"test-app\"} 0.0"));
    }

    @Test
    void testCollectMetricsLargeValues() {
        when(snapshot.getCpuTimeNanos()).thenReturn(Long.MAX_VALUE);
        when(snapshot.getHeapUsedBytes()).thenReturn(Long.MAX_VALUE);
        when(snapshot.getThreadCount()).thenReturn(1000);
        when(poolStats.getActiveThreads()).thenReturn(500);
        when(poolStats.getQueuedTasks()).thenReturn(10000);
        when(poolStats.getCompletedTasks()).thenReturn(Long.MAX_VALUE);

        String metrics = ApplicationMetricsCollector.collectMetrics(context);

        assertNotNull(metrics);
        assertFalse(metrics.isEmpty());

        // Verify large values don't cause errors
        assertTrue(metrics.contains("jplatform_app_cpu_time_seconds{app_id=\"test-app\"}"));
        assertTrue(metrics.contains("jplatform_app_heap_used_bytes{app_id=\"test-app\"}"));
        assertTrue(metrics.contains("jplatform_app_thread_count{app_id=\"test-app\"} 1000.0"));
    }

    @Test
    void testCollectMetricsWithException() {
        when(context.getResourceMonitor()).thenThrow(new RuntimeException("Resource monitor unavailable"));

        String metrics = ApplicationMetricsCollector.collectMetrics(context);

        // Should return error metric for Prometheus alerting
        assertTrue(metrics.contains("# ERROR: Failed to collect metrics"));
        assertTrue(metrics.contains("jplatform_metrics_collection_errors_total"));
        assertTrue(metrics.contains("app_id=\"test-app\""));
    }

    @Test
    void testCollectMetricsAppIdWithSpecialCharacters() {
        when(context.getApplicationId()).thenReturn("test-app\"with\\quotes");
        when(snapshot.getCpuTimeNanos()).thenReturn(0L);
        when(snapshot.getHeapUsedBytes()).thenReturn(0L);
        when(snapshot.getThreadCount()).thenReturn(0);
        when(poolStats.getActiveThreads()).thenReturn(0);
        when(poolStats.getQueuedTasks()).thenReturn(0);
        when(poolStats.getCompletedTasks()).thenReturn(0L);

        String metrics = ApplicationMetricsCollector.collectMetrics(context);

        // Verify special characters are escaped
        assertTrue(metrics.contains("app_id=\"test-app\\\"with\\\\quotes\""));
    }

    @Test
    void testCollectMetricsAllStates() {
        when(snapshot.getCpuTimeNanos()).thenReturn(0L);
        when(snapshot.getHeapUsedBytes()).thenReturn(0L);
        when(snapshot.getThreadCount()).thenReturn(0);
        when(poolStats.getActiveThreads()).thenReturn(0);
        when(poolStats.getQueuedTasks()).thenReturn(0);
        when(poolStats.getCompletedTasks()).thenReturn(0L);

        // Test each state
        for (ApplicationState state : ApplicationState.values()) {
            when(context.getState()).thenReturn(state);
            String metrics = ApplicationMetricsCollector.collectMetrics(context);

            String stateName = state.name().toLowerCase();
            assertTrue(metrics.contains("jplatform_app_state{app_id=\"test-app\",state=\"" + stateName + "\"} 1.0"),
                    "State " + stateName + " should have value 1.0");
        }
    }

    @Test
    void testCollectMetricsVerifyAllMetricTypes() {
        when(snapshot.getCpuTimeNanos()).thenReturn(1000000L);
        when(snapshot.getHeapUsedBytes()).thenReturn(1000000L);
        when(snapshot.getThreadCount()).thenReturn(1);
        when(poolStats.getActiveThreads()).thenReturn(1);
        when(poolStats.getQueuedTasks()).thenReturn(1);
        when(poolStats.getCompletedTasks()).thenReturn(1L);

        String metrics = ApplicationMetricsCollector.collectMetrics(context);

        // Verify all metric types are present
        assertTrue(metrics.contains("# TYPE jplatform_app_cpu_time_seconds counter"));
        assertTrue(metrics.contains("# TYPE jplatform_app_heap_used_bytes gauge"));
        assertTrue(metrics.contains("# TYPE jplatform_app_thread_count gauge"));
        assertTrue(metrics.contains("# TYPE jplatform_app_threadpool_active gauge"));
        assertTrue(metrics.contains("# TYPE jplatform_app_threadpool_queued gauge"));
        assertTrue(metrics.contains("# TYPE jplatform_app_threadpool_completed counter"));
        assertTrue(metrics.contains("# TYPE jplatform_app_state gauge"));
    }
}
