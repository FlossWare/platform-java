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

package org.flossware.jplatform.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClassLoaderVersion.
 * Tests version tracking, reference counting, and garbage collection eligibility.
 */
class ClassLoaderVersionTest {

    private ClassLoader testClassLoader;

    @BeforeEach
    void setUp() {
        testClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @Test
    void testConstructorInitializesFieldsCorrectly() {
        int version = 5;
        Instant before = Instant.now();

        ClassLoaderVersion clv = new ClassLoaderVersion(version, testClassLoader);

        Instant after = Instant.now();

        assertEquals(version, clv.getVersion());
        assertEquals(testClassLoader, clv.getClassLoader());
        assertNotNull(clv.getCreatedAt());
        assertTrue(clv.getCreatedAt().isAfter(before.minusSeconds(1)));
        assertTrue(clv.getCreatedAt().isBefore(after.plusSeconds(1)));
        assertEquals(1, clv.getReferenceCount(), "Initial reference count should be 1");
    }

    @Test
    void testIncrementReferenceIncreasesCount() {
        ClassLoaderVersion clv = new ClassLoaderVersion(1, testClassLoader);

        assertEquals(1, clv.getReferenceCount());

        int newCount = clv.incrementReference();
        assertEquals(2, newCount);
        assertEquals(2, clv.getReferenceCount());

        newCount = clv.incrementReference();
        assertEquals(3, newCount);
        assertEquals(3, clv.getReferenceCount());
    }

    @Test
    void testDecrementReferenceDecreasesCount() {
        ClassLoaderVersion clv = new ClassLoaderVersion(1, testClassLoader);

        clv.incrementReference(); // Now at 2
        clv.incrementReference(); // Now at 3

        assertEquals(3, clv.getReferenceCount());

        int newCount = clv.decrementReference();
        assertEquals(2, newCount);
        assertEquals(2, clv.getReferenceCount());
    }

    @Test
    void testDecrementReferenceDoesNotGoBelowZero() {
        ClassLoaderVersion clv = new ClassLoaderVersion(1, testClassLoader);

        assertEquals(1, clv.getReferenceCount());

        clv.decrementReference(); // Should go to 0
        assertEquals(0, clv.getReferenceCount());

        clv.decrementReference(); // Should stay at 0
        assertEquals(0, clv.getReferenceCount());

        clv.decrementReference(); // Should still stay at 0
        assertEquals(0, clv.getReferenceCount());
    }

    @Test
    void testCanGarbageCollectReturnsTrueWhenCountIsZero() {
        ClassLoaderVersion clv = new ClassLoaderVersion(1, testClassLoader);

        assertFalse(clv.canGarbageCollect(), "Should not be eligible with count = 1");

        clv.decrementReference(); // Now at 0
        assertTrue(clv.canGarbageCollect(), "Should be eligible with count = 0");
    }

    @Test
    void testCanGarbageCollectReturnsFalseWhenCountIsPositive() {
        ClassLoaderVersion clv = new ClassLoaderVersion(1, testClassLoader);

        clv.incrementReference(); // Now at 2

        assertFalse(clv.canGarbageCollect());
    }

    @Test
    void testToStringContainsVersionAndTimestamp() {
        ClassLoaderVersion clv = new ClassLoaderVersion(42, testClassLoader);

        String str = clv.toString();

        assertTrue(str.contains("version=42"), "toString should contain version");
        assertTrue(str.contains("createdAt="), "toString should contain createdAt");
        assertTrue(str.contains("referenceCount="), "toString should contain referenceCount");
    }

    @Test
    void testConstructorWithNullClassLoader() {
        // Verify that constructor rejects null ClassLoader
        assertThrows(NullPointerException.class, () -> {
            new ClassLoaderVersion(1, null);
        });
    }

    @Test
    void testMultipleInstancesAreIndependent() {
        ClassLoaderVersion clv1 = new ClassLoaderVersion(1, testClassLoader);
        ClassLoaderVersion clv2 = new ClassLoaderVersion(2, testClassLoader);

        clv1.incrementReference(); // clv1 at 2
        clv2.decrementReference(); // clv2 at 0

        assertEquals(2, clv1.getReferenceCount());
        assertEquals(0, clv2.getReferenceCount());
        assertFalse(clv1.canGarbageCollect());
        assertTrue(clv2.canGarbageCollect());
    }
}
