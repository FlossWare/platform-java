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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for VolumeMount.
 * Tests constructor validation, getters, and utility methods.
 */
class VolumeMountTest {

    @Test
    void testConstructorValid() {
        VolumeMount volume = new VolumeMount("database", "/var/db", true, 1024);

        assertEquals("database", volume.getName());
        assertEquals("/var/db", volume.getMountPath());
        assertTrue(volume.isPersistent());
        assertEquals(1024, volume.getMaxSizeMB());
        assertTrue(volume.hasSizeLimit());
    }

    @Test
    void testConstructorEphemeral() {
        VolumeMount volume = new VolumeMount("cache", "/var/cache", false, 512);

        assertEquals("cache", volume.getName());
        assertEquals("/var/cache", volume.getMountPath());
        assertFalse(volume.isPersistent());
        assertEquals(512, volume.getMaxSizeMB());
        assertTrue(volume.hasSizeLimit());
    }

    @Test
    void testConstructorUnlimitedSize() {
        VolumeMount volume = new VolumeMount("logs", "/var/logs", true, 0);

        assertEquals("logs", volume.getName());
        assertEquals("/var/logs", volume.getMountPath());
        assertTrue(volume.isPersistent());
        assertEquals(0, volume.getMaxSizeMB());
        assertFalse(volume.hasSizeLimit());
    }

    @Test
    void testConstructorNullName() {
        assertThrows(IllegalArgumentException.class, () ->
            new VolumeMount(null, "/var/db", true, 1024)
        );
    }

    @Test
    void testConstructorEmptyName() {
        assertThrows(IllegalArgumentException.class, () ->
            new VolumeMount("", "/var/db", true, 1024)
        );
    }

    @Test
    void testConstructorWhitespaceName() {
        assertThrows(IllegalArgumentException.class, () ->
            new VolumeMount("   ", "/var/db", true, 1024)
        );
    }

    @Test
    void testConstructorNullMountPath() {
        assertThrows(IllegalArgumentException.class, () ->
            new VolumeMount("database", null, true, 1024)
        );
    }

    @Test
    void testConstructorEmptyMountPath() {
        assertThrows(IllegalArgumentException.class, () ->
            new VolumeMount("database", "", true, 1024)
        );
    }

    @Test
    void testConstructorWhitespaceMountPath() {
        assertThrows(IllegalArgumentException.class, () ->
            new VolumeMount("database", "   ", true, 1024)
        );
    }

    @Test
    void testConstructorNegativeMaxSize() {
        assertThrows(IllegalArgumentException.class, () ->
            new VolumeMount("database", "/var/db", true, -1)
        );
    }

    @Test
    void testHasSizeLimitWithLimit() {
        VolumeMount volume = new VolumeMount("database", "/var/db", true, 100);

        assertTrue(volume.hasSizeLimit());
    }

    @Test
    void testHasSizeLimitWithoutLimit() {
        VolumeMount volume = new VolumeMount("database", "/var/db", true, 0);

        assertFalse(volume.hasSizeLimit());
    }

    @Test
    void testToString() {
        VolumeMount volume = new VolumeMount("database", "/var/db", true, 1024);

        String str = volume.toString();

        assertTrue(str.contains("database"));
        assertTrue(str.contains("/var/db"));
        assertTrue(str.contains("true"));
        assertTrue(str.contains("1024"));
        assertTrue(str.contains("VolumeMount"));
    }

    @Test
    void testPersistentVolumeWithSizeLimit() {
        VolumeMount volume = new VolumeMount("data", "/app/data", true, 2048);

        assertEquals("data", volume.getName());
        assertEquals("/app/data", volume.getMountPath());
        assertTrue(volume.isPersistent());
        assertEquals(2048, volume.getMaxSizeMB());
        assertTrue(volume.hasSizeLimit());
    }

    @Test
    void testEphemeralVolumeUnlimited() {
        VolumeMount volume = new VolumeMount("temp", "/tmp/app", false, 0);

        assertEquals("temp", volume.getName());
        assertEquals("/tmp/app", volume.getMountPath());
        assertFalse(volume.isPersistent());
        assertEquals(0, volume.getMaxSizeMB());
        assertFalse(volume.hasSizeLimit());
    }

    @Test
    void testLargeMaxSize() {
        VolumeMount volume = new VolumeMount("bigdata", "/data", true, Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, volume.getMaxSizeMB());
        assertTrue(volume.hasSizeLimit());
    }

    @Test
    void testRelativeMountPath() {
        VolumeMount volume = new VolumeMount("rel", "data/files", true, 100);

        assertEquals("data/files", volume.getMountPath());
    }

    @Test
    void testAbsoluteMountPath() {
        VolumeMount volume = new VolumeMount("abs", "/absolute/path", true, 100);

        assertEquals("/absolute/path", volume.getMountPath());
    }

    @Test
    void testNameWithSpecialCharacters() {
        VolumeMount volume = new VolumeMount("db-volume_v2", "/var/db", true, 1024);

        assertEquals("db-volume_v2", volume.getName());
    }

    @Test
    void testMountPathWithSpecialCharacters() {
        VolumeMount volume = new VolumeMount("data", "/var/app-data/v2_backup", true, 1024);

        assertEquals("/var/app-data/v2_backup", volume.getMountPath());
    }
}
