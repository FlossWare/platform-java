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

package org.flossware.jplatform.metrics.jmx;

import org.flossware.jplatform.api.ApplicationContext;
import org.flossware.jplatform.api.ApplicationState;
import org.flossware.jplatform.api.ResourceMonitor;
import org.flossware.jplatform.api.ResourceSnapshot;
import org.flossware.jplatform.api.ResourceUsageHistory;
import org.flossware.jplatform.api.ThreadPoolExecutor;
import org.flossware.jplatform.api.ThreadPoolStats;
import org.flossware.jplatform.core.ApplicationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApplicationMBeanImpl.
 */
class ApplicationMBeanImplTest {

    private ApplicationContext context;
    private ApplicationManager manager;
    private ResourceMonitor resourceMonitor;
    private ResourceSnapshot snapshot;
    private ThreadPoolExecutor threadPool;
    private ThreadPoolStats threadPoolStats;
    private ApplicationMBeanImpl mbean;

    @BeforeEach
    void setUp() {
        context = mock(ApplicationContext.class);
        manager = mock(ApplicationManager.class);
        resourceMonitor = mock(ResourceMonitor.class);
        snapshot = mock(ResourceSnapshot.class);
        threadPool = mock(ThreadPoolExecutor.class);
        threadPoolStats = mock(ThreadPoolStats.class);

        when(context.getResourceMonitor()).thenReturn(resourceMonitor);
        when(resourceMonitor.getCurrentSnapshot()).thenReturn(snapshot);
        when(context.getThreadPool()).thenReturn(threadPool);
        when(threadPool.getStats()).thenReturn(threadPoolStats);
        when(context.getState()).thenReturn(ApplicationState.RUNNING);

        mbean = new ApplicationMBeanImpl("test-app", context, manager);
    }

    @Test
    void testConstructorNullApplicationId() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApplicationMBeanImpl(null, context, manager));
    }

    @Test
    void testConstructorEmptyApplicationId() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApplicationMBeanImpl("", context, manager));
    }

    @Test
    void testConstructorNullContext() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApplicationMBeanImpl("test-app", null, manager));
    }

    @Test
    void testConstructorNullManager() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApplicationMBeanImpl("test-app", context, null));
    }

    @Test
    void testGetApplicationId() {
        assertEquals("test-app", mbean.getApplicationId());
    }

    @Test
    void testGetState() {
        when(context.getState()).thenReturn(ApplicationState.RUNNING);
        assertEquals("RUNNING", mbean.getState());

        when(context.getState()).thenReturn(ApplicationState.STOPPED);
        assertEquals("STOPPED", mbean.getState());

        when(context.getState()).thenReturn(ApplicationState.DEPLOYED);
        assertEquals("DEPLOYED", mbean.getState());
    }

    @Test
    void testGetCpuTimeNanos() {
        when(snapshot.getCpuTimeNanos()).thenReturn(123456789L);
        assertEquals(123456789L, mbean.getCpuTimeNanos());
        verify(resourceMonitor).getCurrentSnapshot();
    }

    @Test
    void testGetHeapUsedBytes() {
        when(snapshot.getHeapUsedBytes()).thenReturn(134217728L);
        assertEquals(134217728L, mbean.getHeapUsedBytes());
        verify(resourceMonitor).getCurrentSnapshot();
    }

    @Test
    void testGetThreadCount() {
        when(snapshot.getThreadCount()).thenReturn(12);
        assertEquals(12, mbean.getThreadCount());
        verify(resourceMonitor).getCurrentSnapshot();
    }

    @Test
    void testGetActiveThreads() {
        when(threadPoolStats.getActiveThreads()).thenReturn(5);
        assertEquals(5, mbean.getActiveThreads());
        verify(threadPool).getStats();
    }

    @Test
    void testGetQueuedTasks() {
        when(threadPoolStats.getQueuedTasks()).thenReturn(10);
        assertEquals(10, mbean.getQueuedTasks());
        verify(threadPool).getStats();
    }

    @Test
    void testGetCompletedTasks() {
        when(threadPoolStats.getCompletedTasks()).thenReturn(1000L);
        assertEquals(1000L, mbean.getCompletedTasks());
        verify(threadPool).getStats();
    }

    @Test
    void testStart() throws Exception {
        mbean.start();
        verify(manager).start("test-app");
    }

    @Test
    void testStartThrowsException() throws Exception {
        doThrow(new RuntimeException("Start failed")).when(manager).start("test-app");

        Exception exception = assertThrows(Exception.class, () -> mbean.start());
        assertTrue(exception.getMessage().contains("Start failed"));
    }

    @Test
    void testStop() throws Exception {
        mbean.stop();
        verify(manager).stop("test-app");
    }

    @Test
    void testStopThrowsException() throws Exception {
        doThrow(new RuntimeException("Stop failed")).when(manager).stop("test-app");

        Exception exception = assertThrows(Exception.class, () -> mbean.stop());
        assertTrue(exception.getMessage().contains("Stop failed"));
    }

    @Test
    void testGetResourceHistory() {
        // Create mock snapshots
        long now = System.currentTimeMillis();

        ResourceSnapshot snapshot1 = mock(ResourceSnapshot.class);
        when(snapshot1.getTimestamp()).thenReturn(now - 120000); // 2 minutes ago
        when(snapshot1.getCpuTimeNanos()).thenReturn(1000000L);
        when(snapshot1.getHeapUsedBytes()).thenReturn(100000000L);
        when(snapshot1.getThreadCount()).thenReturn(10);
        when(snapshot1.getBytesRead()).thenReturn(1000L);
        when(snapshot1.getBytesWritten()).thenReturn(2000L);

        ResourceSnapshot snapshot2 = mock(ResourceSnapshot.class);
        when(snapshot2.getTimestamp()).thenReturn(now - 60000); // 1 minute ago
        when(snapshot2.getCpuTimeNanos()).thenReturn(2000000L);
        when(snapshot2.getHeapUsedBytes()).thenReturn(110000000L);
        when(snapshot2.getThreadCount()).thenReturn(12);
        when(snapshot2.getBytesRead()).thenReturn(1500L);
        when(snapshot2.getBytesWritten()).thenReturn(2500L);

        List<ResourceSnapshot> snapshotsList = Arrays.asList(snapshot1, snapshot2);
        ResourceUsageHistory history = mock(ResourceUsageHistory.class);
        when(history.size()).thenReturn(2);
        when(history.getSnapshots()).thenReturn(snapshotsList);

        when(resourceMonitor.getHistory(any(Duration.class))).thenReturn(history);

        String json = mbean.getResourceHistory(5);

        assertNotNull(json);
        assertTrue(json.contains("test-app"));
        assertTrue(json.contains("\"durationMinutes\":5"));
        assertTrue(json.contains("\"snapshotCount\":2"));
        assertTrue(json.contains("snapshots"));
        assertTrue(json.contains("cpuTimeNanos"));
        assertTrue(json.contains("heapUsedBytes"));

        verify(resourceMonitor).getHistory(Duration.ofMinutes(5));
    }

    @Test
    void testGetResourceHistoryEmpty() {
        ResourceUsageHistory history = mock(ResourceUsageHistory.class);
        when(history.size()).thenReturn(0);
        when(history.getSnapshots()).thenReturn(Arrays.asList());

        when(resourceMonitor.getHistory(any(Duration.class))).thenReturn(history);

        String json = mbean.getResourceHistory(10);

        assertNotNull(json);
        assertTrue(json.contains("\"snapshotCount\":0"));
        assertTrue(json.contains("\"durationMinutes\":10"));
    }

    @Test
    void testGetResourceHistoryException() {
        when(resourceMonitor.getHistory(any(Duration.class)))
                .thenThrow(new RuntimeException("History not available"));

        String json = mbean.getResourceHistory(5);

        assertNotNull(json);
        assertTrue(json.contains("error"));
        assertTrue(json.contains("History not available"));
    }

    @Test
    void testMultipleGettersCalled() {
        when(snapshot.getCpuTimeNanos()).thenReturn(111L);
        when(snapshot.getHeapUsedBytes()).thenReturn(222L);
        when(snapshot.getThreadCount()).thenReturn(3);
        when(threadPoolStats.getActiveThreads()).thenReturn(1);
        when(threadPoolStats.getQueuedTasks()).thenReturn(2);
        when(threadPoolStats.getCompletedTasks()).thenReturn(100L);

        // Call all getters
        assertEquals(111L, mbean.getCpuTimeNanos());
        assertEquals(222L, mbean.getHeapUsedBytes());
        assertEquals(3, mbean.getThreadCount());
        assertEquals(1, mbean.getActiveThreads());
        assertEquals(2, mbean.getQueuedTasks());
        assertEquals(100L, mbean.getCompletedTasks());

        // Verify interactions
        verify(resourceMonitor, times(3)).getCurrentSnapshot();
        verify(threadPool, times(3)).getStats();
    }
}
