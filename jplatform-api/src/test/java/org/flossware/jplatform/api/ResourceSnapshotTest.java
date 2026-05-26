package org.flossware.jplatform.api;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResourceSnapshot.
 */
class ResourceSnapshotTest {

    @Test
    void testConstructor_validParameters() {
        long timestamp = System.currentTimeMillis();
        ResourceSnapshot snapshot = new ResourceSnapshot(
                timestamp, 1000, 100, 5, 10, 20, null);

        assertEquals(timestamp, snapshot.getTimestamp());
        assertEquals(1000, snapshot.getCpuTimeNanos());
        assertEquals(100, snapshot.getHeapUsedBytes());
        assertEquals(5, snapshot.getThreadCount());
        assertEquals(10, snapshot.getBytesRead());
        assertEquals(20, snapshot.getBytesWritten());
        assertNotNull(snapshot.getCustomMetrics());
        assertTrue(snapshot.getCustomMetrics().isEmpty());
    }

    @Test
    void testConstructor_withCustomMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("metric1", 100);
        metrics.put("metric2", "value");

        ResourceSnapshot snapshot = new ResourceSnapshot(
                System.currentTimeMillis(), 1000, 100, 5, 10, 20, metrics);

        assertEquals(100, snapshot.getCustomMetrics().get("metric1"));
        assertEquals("value", snapshot.getCustomMetrics().get("metric2"));
    }

    @Test
    void testConstructor_negativeTimestamp() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceSnapshot(-1, 1000, 100, 5, 10, 20, null));

        assertTrue(exception.getMessage().contains("timestamp cannot be negative"));
    }

    @Test
    void testConstructor_negativeCpuTime() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceSnapshot(System.currentTimeMillis(), -1, 100, 5, 10, 20, null));

        assertTrue(exception.getMessage().contains("cpuTimeNanos cannot be negative"));
    }

    @Test
    void testConstructor_negativeHeapUsed() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceSnapshot(System.currentTimeMillis(), 1000, -2, 5, 10, 20, null));

        assertTrue(exception.getMessage().contains("heapUsedBytes cannot be negative"));
    }

    @Test
    void testConstructor_heapUsedMinusOneAllowed() {
        // -1 is allowed to indicate "not available"
        ResourceSnapshot snapshot = new ResourceSnapshot(
                System.currentTimeMillis(), 1000, -1, 5, 10, 20, null);

        assertEquals(-1, snapshot.getHeapUsedBytes());
    }

    @Test
    void testConstructor_negativeThreadCount() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceSnapshot(System.currentTimeMillis(), 1000, 100, -1, 10, 20, null));

        assertTrue(exception.getMessage().contains("threadCount cannot be negative"));
    }

    @Test
    void testConstructor_negativeBytesRead() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceSnapshot(System.currentTimeMillis(), 1000, 100, 5, -1, 20, null));

        assertTrue(exception.getMessage().contains("bytesRead cannot be negative"));
    }

    @Test
    void testConstructor_negativeBytesWritten() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceSnapshot(System.currentTimeMillis(), 1000, 100, 5, 10, -1, null));

        assertTrue(exception.getMessage().contains("bytesWritten cannot be negative"));
    }

    @Test
    void testConstructor_customMetricsNullKey() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("validKey", "value");
        metrics.put(null, "bad-key");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceSnapshot(System.currentTimeMillis(), 1000, 100, 5, 10, 20, metrics));

        assertTrue(exception.getMessage().contains("customMetrics cannot contain null keys"));
    }

    @Test
    void testConstructor_customMetricsNullValue() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("metric1", "value1");
        metrics.put("metric2", null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceSnapshot(System.currentTimeMillis(), 1000, 100, 5, 10, 20, metrics));

        assertTrue(exception.getMessage().contains("customMetrics cannot contain null values"));
        assertTrue(exception.getMessage().contains("metric2"));
    }

    @Test
    void testGetCustomMetrics_defensiveCopy() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("metric1", 100);

        ResourceSnapshot snapshot = new ResourceSnapshot(
                System.currentTimeMillis(), 1000, 100, 5, 10, 20, metrics);

        // Modify original map after construction
        metrics.put("metric2", 200);

        // Snapshot should not be affected
        assertEquals(1, snapshot.getCustomMetrics().size());
        assertNull(snapshot.getCustomMetrics().get("metric2"));
    }

    @Test
    void testConstructor_zeroValues() {
        ResourceSnapshot snapshot = new ResourceSnapshot(
                0, 0, 0, 0, 0, 0, null);

        assertEquals(0, snapshot.getTimestamp());
        assertEquals(0, snapshot.getCpuTimeNanos());
        assertEquals(0, snapshot.getHeapUsedBytes());
        assertEquals(0, snapshot.getThreadCount());
        assertEquals(0, snapshot.getBytesRead());
        assertEquals(0, snapshot.getBytesWritten());
    }

    @Test
    void testConstructor_largeValues() {
        long largeValue = Long.MAX_VALUE;
        ResourceSnapshot snapshot = new ResourceSnapshot(
                largeValue, largeValue, largeValue, Integer.MAX_VALUE, largeValue, largeValue, null);

        assertEquals(largeValue, snapshot.getTimestamp());
        assertEquals(largeValue, snapshot.getCpuTimeNanos());
        assertEquals(largeValue, snapshot.getHeapUsedBytes());
        assertEquals(Integer.MAX_VALUE, snapshot.getThreadCount());
        assertEquals(largeValue, snapshot.getBytesRead());
        assertEquals(largeValue, snapshot.getBytesWritten());
    }

    @Test
    void testGetCustomMetrics_emptyWhenNull() {
        ResourceSnapshot snapshot = new ResourceSnapshot(
                System.currentTimeMillis(), 1000, 100, 5, 10, 20, null);

        Map<String, Object> metrics = snapshot.getCustomMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.isEmpty());
    }

    @Test
    void testGetCustomMetrics_immutable() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("metric1", 100);

        ResourceSnapshot snapshot = new ResourceSnapshot(
                System.currentTimeMillis(), 1000, 100, 5, 10, 20, metrics);

        Map<String, Object> retrieved = snapshot.getCustomMetrics();

        assertThrows(UnsupportedOperationException.class,
                () -> retrieved.put("metric2", 200));
    }
}
