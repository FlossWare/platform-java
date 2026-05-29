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

package org.flossware.jplatform.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResourceUsageHistory.
 */
class ResourceUsageHistoryTest {

    private ResourceSnapshot createSnapshot(long timestamp) {
        return new ResourceSnapshot(timestamp, 1000, 100, 5, 0, 0, null);
    }

    @Test
    void testConstructor_validSnapshots() {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(createSnapshot(1000));
        snapshots.add(createSnapshot(2000));
        snapshots.add(createSnapshot(3000));

        ResourceUsageHistory history = new ResourceUsageHistory(snapshots);

        assertEquals(3, history.size());
        assertFalse(history.isEmpty());
        assertEquals(3, history.getSnapshots().size());
    }

    @Test
    void testConstructor_emptyList() {
        ResourceUsageHistory history = new ResourceUsageHistory(Collections.emptyList());

        assertEquals(0, history.size());
        assertTrue(history.isEmpty());
    }

    @Test
    void testConstructor_nullList() {
        ResourceUsageHistory history = new ResourceUsageHistory(null);

        assertEquals(0, history.size());
        assertTrue(history.isEmpty());
        assertNotNull(history.getSnapshots());
    }

    @Test
    void testConstructor_nullElementAtStart() {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(null);
        snapshots.add(createSnapshot(2000));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceUsageHistory(snapshots));

        assertTrue(exception.getMessage().contains("cannot contain null elements"));
        assertTrue(exception.getMessage().contains("index 0"));
    }

    @Test
    void testConstructor_nullElementInMiddle() {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(createSnapshot(1000));
        snapshots.add(null);
        snapshots.add(createSnapshot(3000));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceUsageHistory(snapshots));

        assertTrue(exception.getMessage().contains("cannot contain null elements"));
        assertTrue(exception.getMessage().contains("index 1"));
    }

    @Test
    void testConstructor_nullElementAtEnd() {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(createSnapshot(1000));
        snapshots.add(createSnapshot(2000));
        snapshots.add(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceUsageHistory(snapshots));

        assertTrue(exception.getMessage().contains("cannot contain null elements"));
        assertTrue(exception.getMessage().contains("index 2"));
    }

    @Test
    void testConstructor_multipleNullElements() {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(null);
        snapshots.add(createSnapshot(2000));
        snapshots.add(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ResourceUsageHistory(snapshots));

        // Should report first null element
        assertTrue(exception.getMessage().contains("index 0"));
    }

    @Test
    void testGetSnapshots_immutable() {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(createSnapshot(1000));
        snapshots.add(createSnapshot(2000));

        ResourceUsageHistory history = new ResourceUsageHistory(snapshots);
        List<ResourceSnapshot> retrieved = history.getSnapshots();

        assertThrows(UnsupportedOperationException.class,
                () -> retrieved.add(createSnapshot(3000)));
    }

    @Test
    void testConstructor_defensiveCopy() {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(createSnapshot(1000));
        snapshots.add(createSnapshot(2000));

        ResourceUsageHistory history = new ResourceUsageHistory(snapshots);

        // Modify original list after construction
        snapshots.add(createSnapshot(3000));

        // History should not be affected
        assertEquals(2, history.size());
    }

    @Test
    void testSize_multipleSnapshots() {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            snapshots.add(createSnapshot(i * 1000));
        }

        ResourceUsageHistory history = new ResourceUsageHistory(snapshots);

        assertEquals(10, history.size());
    }

    @Test
    void testIsEmpty_withSnapshots() {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(createSnapshot(1000));

        ResourceUsageHistory history = new ResourceUsageHistory(snapshots);

        assertFalse(history.isEmpty());
    }

    @Test
    void testIsEmpty_noSnapshots() {
        ResourceUsageHistory history = new ResourceUsageHistory(null);

        assertTrue(history.isEmpty());
    }

    @Test
    void testGetSnapshots_orderPreserved() {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        ResourceSnapshot s1 = createSnapshot(1000);
        ResourceSnapshot s2 = createSnapshot(2000);
        ResourceSnapshot s3 = createSnapshot(3000);
        snapshots.add(s1);
        snapshots.add(s2);
        snapshots.add(s3);

        ResourceUsageHistory history = new ResourceUsageHistory(snapshots);
        List<ResourceSnapshot> retrieved = history.getSnapshots();

        assertEquals(s1, retrieved.get(0));
        assertEquals(s2, retrieved.get(1));
        assertEquals(s3, retrieved.get(2));
    }
}
