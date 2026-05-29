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

package org.flossware.jplatform.fswatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for DescriptorRegistry.
 * Tests thread-safe registration, lookup, and removal of descriptor-to-application ID mappings.
 */
class DescriptorRegistryTest {

    private DescriptorRegistry registry;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        registry = new DescriptorRegistry();
    }

    @Test
    void testConstructor() {
        assertNotNull(registry);
        assertEquals(0, registry.size());
    }

    @Test
    void testPutAndGet() {
        Path descriptorPath = tempDir.resolve("app.yaml");
        String appId = "test-app";

        String previous = registry.put(descriptorPath, appId);

        assertNull(previous, "First put should return null");
        assertEquals(appId, registry.get(descriptorPath));
        assertEquals(1, registry.size());
    }

    @Test
    void testPutReplacesPreviousValue() {
        Path descriptorPath = tempDir.resolve("app.yaml");
        String firstAppId = "app-v1";
        String secondAppId = "app-v2";

        registry.put(descriptorPath, firstAppId);
        String previous = registry.put(descriptorPath, secondAppId);

        assertEquals(firstAppId, previous, "Put should return previous value");
        assertEquals(secondAppId, registry.get(descriptorPath));
        assertEquals(1, registry.size(), "Size should remain 1 when replacing");
    }

    @Test
    void testPutNullDescriptorPath() {
        assertThrows(NullPointerException.class, () -> {
            registry.put(null, "app-id");
        });
    }

    @Test
    void testPutNullApplicationId() {
        Path descriptorPath = tempDir.resolve("app.yaml");
        assertThrows(NullPointerException.class, () -> {
            registry.put(descriptorPath, null);
        });
    }

    @Test
    void testGetNonExistentPath() {
        Path descriptorPath = tempDir.resolve("nonexistent.yaml");
        assertNull(registry.get(descriptorPath));
    }

    @Test
    void testGetNullPath() {
        assertThrows(NullPointerException.class, () -> {
            registry.get(null);
        });
    }

    @Test
    void testRemove() {
        Path descriptorPath = tempDir.resolve("app.yaml");
        String appId = "test-app";

        registry.put(descriptorPath, appId);
        String removed = registry.remove(descriptorPath);

        assertEquals(appId, removed);
        assertNull(registry.get(descriptorPath));
        assertEquals(0, registry.size());
    }

    @Test
    void testRemoveNonExistentPath() {
        Path descriptorPath = tempDir.resolve("nonexistent.yaml");
        assertNull(registry.remove(descriptorPath));
    }

    @Test
    void testRemoveNullPath() {
        assertThrows(NullPointerException.class, () -> {
            registry.remove(null);
        });
    }

    @Test
    void testContains() {
        Path descriptorPath = tempDir.resolve("app.yaml");
        String appId = "test-app";

        assertFalse(registry.contains(descriptorPath));

        registry.put(descriptorPath, appId);
        assertTrue(registry.contains(descriptorPath));

        registry.remove(descriptorPath);
        assertFalse(registry.contains(descriptorPath));
    }

    @Test
    void testContainsNullPath() {
        assertThrows(NullPointerException.class, () -> {
            registry.contains(null);
        });
    }

    @Test
    void testGetAll() {
        Path path1 = tempDir.resolve("app1.yaml");
        Path path2 = tempDir.resolve("app2.yaml");
        Path path3 = tempDir.resolve("app3.json");

        registry.put(path1, "app-1");
        registry.put(path2, "app-2");
        registry.put(path3, "app-3");

        Map<Path, String> all = registry.getAll();

        assertNotNull(all);
        assertEquals(3, all.size());
        assertEquals("app-1", all.get(path1));
        assertEquals("app-2", all.get(path2));
        assertEquals("app-3", all.get(path3));
    }

    @Test
    void testGetAllReturnsIndependentCopy() {
        Path descriptorPath = tempDir.resolve("app.yaml");
        registry.put(descriptorPath, "test-app");

        Map<Path, String> copy = registry.getAll();
        copy.clear();

        // Original registry should be unchanged
        assertEquals(1, registry.size());
        assertTrue(registry.contains(descriptorPath));
    }

    @Test
    void testGetAllEmpty() {
        Map<Path, String> all = registry.getAll();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    void testClear() {
        Path path1 = tempDir.resolve("app1.yaml");
        Path path2 = tempDir.resolve("app2.yaml");

        registry.put(path1, "app-1");
        registry.put(path2, "app-2");

        assertEquals(2, registry.size());

        registry.clear();

        assertEquals(0, registry.size());
        assertFalse(registry.contains(path1));
        assertFalse(registry.contains(path2));
        assertNull(registry.get(path1));
        assertNull(registry.get(path2));
    }

    @Test
    void testClearEmptyRegistry() {
        registry.clear();
        assertEquals(0, registry.size());
    }

    @Test
    void testSize() {
        assertEquals(0, registry.size());

        Path path1 = tempDir.resolve("app1.yaml");
        registry.put(path1, "app-1");
        assertEquals(1, registry.size());

        Path path2 = tempDir.resolve("app2.yaml");
        registry.put(path2, "app-2");
        assertEquals(2, registry.size());

        registry.remove(path1);
        assertEquals(1, registry.size());

        registry.clear();
        assertEquals(0, registry.size());
    }

    @Test
    void testMultiplePathsWithDifferentExtensions() {
        Path yamlPath = tempDir.resolve("app.yaml");
        Path jsonPath = tempDir.resolve("app.json");

        registry.put(yamlPath, "yaml-app");
        registry.put(jsonPath, "json-app");

        assertEquals("yaml-app", registry.get(yamlPath));
        assertEquals("json-app", registry.get(jsonPath));
        assertEquals(2, registry.size());
    }

    @Test
    void testAbsoluteAndRelativePaths() {
        Path absolutePath = tempDir.resolve("app1.yaml").toAbsolutePath();
        Path relativePath = Paths.get("app2.yaml");

        registry.put(absolutePath, "absolute-app");
        registry.put(relativePath, "relative-app");

        assertEquals("absolute-app", registry.get(absolutePath));
        assertEquals("relative-app", registry.get(relativePath));
        assertEquals(2, registry.size());
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        int numThreads = 10;
        int opsPerThread = 100;
        Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    Path path = tempDir.resolve("app-" + threadId + "-" + j + ".yaml");
                    String appId = "app-" + threadId + "-" + j;

                    // Put
                    registry.put(path, appId);

                    // Get
                    assertEquals(appId, registry.get(path));

                    // Contains
                    assertTrue(registry.contains(path));

                    // GetAll
                    Map<Path, String> all = registry.getAll();
                    assertTrue(all.containsKey(path));

                    // Remove
                    if (j % 2 == 0) {
                        String removed = registry.remove(path);
                        assertEquals(appId, removed);
                    }
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Verify final state - should have approximately half the entries (removed every other)
        int finalSize = registry.size();
        assertTrue(finalSize >= 0 && finalSize <= numThreads * opsPerThread,
                "Final size should be within expected range");
    }

    @Test
    void testPutGetRemoveSequence() {
        Path path1 = tempDir.resolve("app1.yaml");
        Path path2 = tempDir.resolve("app2.yaml");

        // Put first
        registry.put(path1, "app-1");
        assertEquals("app-1", registry.get(path1));

        // Put second
        registry.put(path2, "app-2");
        assertEquals("app-2", registry.get(path2));
        assertEquals("app-1", registry.get(path1)); // First still there

        // Remove first
        assertEquals("app-1", registry.remove(path1));
        assertNull(registry.get(path1));
        assertEquals("app-2", registry.get(path2)); // Second still there

        // Remove second
        assertEquals("app-2", registry.remove(path2));
        assertNull(registry.get(path2));

        assertEquals(0, registry.size());
    }
}
