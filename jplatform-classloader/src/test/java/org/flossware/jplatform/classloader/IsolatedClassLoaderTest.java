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

package org.flossware.jplatform.classloader;

import org.flossware.jplatform.api.ApplicationDescriptor;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for IsolatedClassLoader.
 * Tests factory method validation, classpath processing, and lifecycle.
 */
class IsolatedClassLoaderTest {

    private static final String TEST_APP_ID = "test-app";

    @Test
    void testCreateWithNullApplicationId() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .build();

        assertThrows(NullPointerException.class, () ->
            IsolatedClassLoader.create(null, descriptor, ClassLoader.getSystemClassLoader())
        );
    }

    @Test
    void testCreateWithNullDescriptor() {
        assertThrows(NullPointerException.class, () ->
            IsolatedClassLoader.create(TEST_APP_ID, null, ClassLoader.getSystemClassLoader())
        );
    }

    @Test
    void testCreateWithNullParentLoader() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .build();

        assertThrows(NullPointerException.class, () ->
            IsolatedClassLoader.create(TEST_APP_ID, descriptor, null)
        );
    }

    @Test
    void testCreateMinimal() {
        // ApplicationClassLoader requires at least one class source
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("file:///tmp/test.jar"))
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertNotNull(loader);
        assertEquals(TEST_APP_ID, loader.getApplicationId());
        assertEquals(descriptor, loader.getDescriptor());
        assertNotNull(loader.getResourceTracker());
    }

    @Test
    void testCreateWithFileClasspath() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("file:///opt/app/lib/app.jar"))
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertNotNull(loader);
    }

    @Test
    void testCreateWithHttpClasspath() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("http://repo.example.com/lib.jar"))
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertNotNull(loader);
    }

    @Test
    void testCreateWithHttpsClasspath() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("https://repo.example.com/lib.jar"))
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertNotNull(loader);
    }

    @Test
    void testCreateWithHttpBasicAuth() {
        Map<String, String> props = new HashMap<>();
        props.put("classpath.repo.example.com.auth.type", "basic");
        props.put("classpath.repo.example.com.auth.username", "user");
        props.put("classpath.repo.example.com.auth.password", "pass");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("http://repo.example.com/lib.jar"))
            .properties(props)
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertNotNull(loader);
    }

    @Test
    void testCreateWithHttpBearerAuth() {
        Map<String, String> props = new HashMap<>();
        props.put("classpath.repo.example.com.auth.type", "bearer");
        props.put("classpath.repo.example.com.auth.token", "secret-token");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("http://repo.example.com/lib.jar"))
            .properties(props)
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertNotNull(loader);
    }

    @Test
    void testCreateWithMavenClasspath() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("maven:org.example:library:1.0.0"))
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertNotNull(loader);
    }

    @Test
    void testCreateWithMavenClasspathWithClassifier() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("maven:org.example:library:1.0.0:tests"))
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertNotNull(loader);
    }

    @Test
    void testCreateWithMavenClasspathInvalidFormat() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("maven:invalid"))
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            IsolatedClassLoader.create(TEST_APP_ID, descriptor, ClassLoader.getSystemClassLoader())
        );
    }

    @Test
    void testCreateWithMavenClasspathEmptyGroupId() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("maven::artifact:1.0.0"))
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            IsolatedClassLoader.create(TEST_APP_ID, descriptor, ClassLoader.getSystemClassLoader())
        );
    }

    @Test
    void testCreateWithMavenClasspathEmptyArtifactId() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("maven:org.example::1.0.0"))
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            IsolatedClassLoader.create(TEST_APP_ID, descriptor, ClassLoader.getSystemClassLoader())
        );
    }

    @Test
    void testCreateWithMavenClasspathEmptyVersion() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("maven:org.example:artifact:"))
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            IsolatedClassLoader.create(TEST_APP_ID, descriptor, ClassLoader.getSystemClassLoader())
        );
    }

    @Test
    void testCreateWithMavenClasspathEmptyCoordinates() {
        // URI.create("maven:") throws URISyntaxException, so we can't even create the descriptor
        // This test validates that invalid URIs are caught at descriptor creation time
        assertThrows(IllegalArgumentException.class, () ->
            URI.create("maven:")
        );
    }

    @Test
    void testCreateWithUnsupportedScheme() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("ftp://repo.example.com/lib.jar"))
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            IsolatedClassLoader.create(TEST_APP_ID, descriptor, ClassLoader.getSystemClassLoader())
        );
    }

    @Test
    void testCreateWithMultipleClasspathEntries() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("file:///opt/app/lib/app.jar"))
            .addClasspathEntry(URI.create("http://repo.example.com/lib.jar"))
            .addClasspathEntry(URI.create("maven:org.example:library:1.0.0"))
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertNotNull(loader);
    }

    @Test
    void testGetStatistics() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("file:///tmp/test.jar"))
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        ClassLoaderStatistics stats = loader.getStatistics();

        assertNotNull(stats);
        assertEquals(TEST_APP_ID, stats.getApplicationId());
    }

    @Test
    void testClose() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("file:///tmp/test.jar"))
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertDoesNotThrow(() -> loader.close());
    }

    @Test
    void testCloseIdempotent() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId(TEST_APP_ID)
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .addClasspathEntry(URI.create("file:///tmp/test.jar"))
            .build();

        IsolatedClassLoader loader = IsolatedClassLoader.create(
            TEST_APP_ID,
            descriptor,
            ClassLoader.getSystemClassLoader()
        );

        assertDoesNotThrow(() -> {
            loader.close();
            loader.close();  // Should not throw
        });
    }
}
