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

import org.flossware.jplatform.api.NativeLibrary;
import org.flossware.jplatform.api.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NativeLibraryLoader.
 * Tests platform detection, library filtering, and extraction logic.
 *
 * Note: Full integration testing of library extraction requires creating actual
 * native library files. These tests focus on the public API contract and basic
 * filesystem operations.
 */
class NativeLibraryLoaderTest {

    @TempDir
    Path tempDir;

    private NativeLibraryLoader loader;

    @BeforeEach
    void setUp() {
        loader = new NativeLibraryLoader("test-app", tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (loader != null) {
            try {
                loader.cleanup();
            } catch (IOException e) {
                // Ignore cleanup errors in tests
            }
        }
    }

    @Test
    void testConstructorDetectsPlatform() {
        Platform platform = loader.getCurrentPlatform();
        assertNotNull(platform, "Platform should be detected");
    }

    @Test
    void testConstructorWithDefaultBasePath() {
        NativeLibraryLoader defaultLoader = new NativeLibraryLoader("test-app");
        assertNotNull(defaultLoader.getCurrentPlatform());
    }

    @Test
    void testLoadLibrariesCreatesDirectory() throws IOException {
        List<NativeLibrary> libraries = new ArrayList<>();

        Path libDir = loader.loadLibraries(libraries);

        assertNotNull(libDir);
        assertTrue(Files.exists(libDir), "Library directory should be created");
        assertTrue(Files.isDirectory(libDir), "Should be a directory");
    }

    @Test
    void testLoadLibrariesWithEmptyList() throws IOException {
        List<NativeLibrary> libraries = new ArrayList<>();

        Path libDir = loader.loadLibraries(libraries);

        assertNotNull(libDir);
        assertTrue(Files.exists(libDir));
    }

    @Test
    void testLoadLibrariesWithNullListThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            loader.loadLibraries(null);
        });
    }

    @Test
    void testGetCurrentPlatformReturnsValidPlatform() {
        Platform platform = loader.getCurrentPlatform();
        assertNotNull(platform);
        assertNotNull(platform.getOs());
        assertNotNull(platform.getArch());
    }

    @Test
    void testCleanupRemovesLibraryDirectory() throws IOException {
        // First create the directory
        List<NativeLibrary> libraries = new ArrayList<>();
        Path libDir = loader.loadLibraries(libraries);
        assertTrue(Files.exists(libDir));

        // Then cleanup
        loader.cleanup();

        // Directory should be removed
        assertFalse(Files.exists(libDir), "Cleanup should remove library directory");
    }

    @Test
    void testCleanupOnNonExistentDirectoryDoesNotThrow() {
        assertDoesNotThrow(() -> loader.cleanup());
    }

    @Test
    void testMultipleLoadLibrariesCallsReuseSameDirectory() throws IOException {
        List<NativeLibrary> libraries1 = new ArrayList<>();
        List<NativeLibrary> libraries2 = new ArrayList<>();

        Path libDir1 = loader.loadLibraries(libraries1);
        Path libDir2 = loader.loadLibraries(libraries2);

        assertEquals(libDir1, libDir2, "Multiple calls should return same directory");
    }
}
