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

package org.flossware.jplatform.config;

import org.flossware.jplatform.api.ApplicationDescriptor;
import org.flossware.jplatform.api.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YamlDescriptorParser.
 */
class YamlDescriptorParserTest {

    private YamlDescriptorParser parser;

    @BeforeEach
    void setUp() {
        parser = new YamlDescriptorParser();
    }

    @Test
    void testGetSupportedFormat() {
        assertEquals(YamlDescriptorParser.Format.YAML, parser.getSupportedFormat());
    }

    @Test
    void testParseValidYaml() throws ParseException {
        String yaml = "applicationId: test-app\n" +
                "name: Test Application\n" +
                "version: 1.0.0\n" +
                "mainClass: com.example.TestApp\n" +
                "classpathEntries:\n" +
                "  - file:///path/to/app.jar\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertEquals("test-app", descriptor.getApplicationId());
        assertEquals("Test Application", descriptor.getName());
        assertEquals("1.0.0", descriptor.getVersion());
        assertEquals("com.example.TestApp", descriptor.getMainClass());
        assertEquals(1, descriptor.getClasspathEntries().size());
        assertTrue(descriptor.getClasspathEntries().get(0).toString().contains("app.jar"));
    }

    @Test
    void testParseYamlWithThreadPool() throws ParseException {
        String yaml = "applicationId: test-app\n" +
                "mainClass: com.example.TestApp\n" +
                "classpathEntries:\n" +
                "  - file:///app.jar\n" +
                "threadPoolConfig:\n" +
                "  corePoolSize: 4\n" +
                "  maxPoolSize: 20\n" +
                "  queueCapacity: 100\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertNotNull(descriptor.getThreadPoolConfig());
        // Verify custom values from YAML are parsed
        assertEquals(4, descriptor.getThreadPoolConfig().getCorePoolSize());
        assertEquals(20, descriptor.getThreadPoolConfig().getMaxPoolSize());
        assertEquals(100, descriptor.getThreadPoolConfig().getQueueCapacity());
    }

    @Test
    void testParseYamlWithSecurity() throws ParseException {
        String yaml = "applicationId: test-app\n" +
                "mainClass: com.example.TestApp\n" +
                "classpathEntries:\n" +
                "  - file:///app.jar\n" +
                "securityConfig:\n" +
                "  allowReflection: true\n" +
                "  allowNativeCode: false\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertNotNull(descriptor.getSecurityConfig());
        assertTrue(descriptor.getSecurityConfig().isAllowReflection());
        assertFalse(descriptor.getSecurityConfig().isAllowNativeCode());
    }

    @Test
    void testParseYamlWithResources() throws ParseException {
        String yaml = "applicationId: test-app\n" +
                "mainClass: com.example.TestApp\n" +
                "classpathEntries:\n" +
                "  - file:///app.jar\n" +
                "resourceConfig:\n" +
                "  maxHeapMB: 512\n" +
                "  maxThreads: 50\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertNotNull(descriptor.getResourceConfig());
        assertTrue(descriptor.getResourceConfig().getMaxHeapMB().isPresent());
        assertEquals(512L, descriptor.getResourceConfig().getMaxHeapMB().get());
        assertTrue(descriptor.getResourceConfig().getMaxThreads().isPresent());
        assertEquals(50, descriptor.getResourceConfig().getMaxThreads().get());
    }

    @Test
    void testParseYamlWithProperties() throws ParseException {
        String yaml = "applicationId: test-app\n" +
                "mainClass: com.example.TestApp\n" +
                "classpathEntries:\n" +
                "  - file:///app.jar\n" +
                "properties:\n" +
                "  app.name: MyApp\n" +
                "  app.version: 1.0\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertNotNull(descriptor.getProperties());
        assertEquals("MyApp", descriptor.getProperties().get("app.name"));
        assertEquals("1.0", descriptor.getProperties().get("app.version"));
    }

    @Test
    void testParseYamlWithMessaging() throws ParseException {
        String yaml = "applicationId: test-app\n" +
                "mainClass: com.example.TestApp\n" +
                "classpathEntries:\n" +
                "  - file:///app.jar\n" +
                "enableMessaging: true\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertTrue(descriptor.isEnableMessaging());
    }

    @Test
    void testParseInvalidYaml() {
        String invalidYaml = "applicationId: test-app\n" +
                "invalid yaml syntax: [\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(invalidYaml.getBytes());
        assertThrows(ParseException.class, () -> parser.parse(inputStream));
    }

    @Test
    void testParseMissingRequiredFields() {
        String yaml = "applicationId: test-app\n" +
                "name: Test\n";  // Missing mainClass and classpathEntries

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes());
        // Currently throws NullPointerException from builder when required fields missing
        assertThrows(Exception.class, () -> parser.parse(inputStream));
    }

    @Test
    void testParseFileValid(@TempDir Path tempDir) throws IOException, ParseException {
        Path yamlFile = tempDir.resolve("app.yaml");
        String yaml = "applicationId: file-test-app\n" +
                "mainClass: com.example.FileApp\n" +
                "classpathEntries:\n" +
                "  - file:///app.jar\n";
        Files.writeString(yamlFile, yaml);

        ApplicationDescriptor descriptor = parser.parseFile(yamlFile);

        assertNotNull(descriptor);
        assertEquals("file-test-app", descriptor.getApplicationId());
    }

    @Test
    void testParseFileNotFound() {
        Path nonExistent = Path.of("/nonexistent/app.yaml");
        assertThrows(ParseException.class, () -> parser.parseFile(nonExistent));
    }

    @Test
    void testParseCompleteDescriptor() throws ParseException {
        String yaml = "applicationId: complete-app\n" +
                "name: Complete Application\n" +
                "version: 2.0.0\n" +
                "mainClass: com.example.CompleteApp\n" +
                "classpathEntries:\n" +
                "  - file:///lib/app.jar\n" +
                "  - file:///lib/deps.jar\n" +
                "threadPoolConfig:\n" +
                "  corePoolSize: 8\n" +
                "  maxPoolSize: 32\n" +
                "  queueCapacity: 200\n" +
                "securityConfig:\n" +
                "  allowReflection: true\n" +
                "  allowNativeCode: false\n" +
                "resourceConfig:\n" +
                "  maxHeapMB: 1024\n" +
                "  maxThreads: 100\n" +
                "enableMessaging: true\n" +
                "properties:\n" +
                "  db.url: jdbc:postgresql://localhost/mydb\n" +
                "  db.user: admin\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertEquals("complete-app", descriptor.getApplicationId());
        assertEquals("Complete Application", descriptor.getName());
        assertEquals("2.0.0", descriptor.getVersion());
        assertEquals("com.example.CompleteApp", descriptor.getMainClass());
        assertEquals(2, descriptor.getClasspathEntries().size());

        assertNotNull(descriptor.getThreadPoolConfig());
        // Verify the values from the YAML (8 and 32)
        assertEquals(8, descriptor.getThreadPoolConfig().getCorePoolSize());
        assertEquals(32, descriptor.getThreadPoolConfig().getMaxPoolSize());

        assertNotNull(descriptor.getSecurityConfig());
        assertTrue(descriptor.getSecurityConfig().isAllowReflection());

        assertNotNull(descriptor.getResourceConfig());
        assertTrue(descriptor.getResourceConfig().getMaxHeapMB().isPresent());
        assertEquals(1024L, descriptor.getResourceConfig().getMaxHeapMB().get());

        assertTrue(descriptor.isEnableMessaging());

        assertNotNull(descriptor.getProperties());
        assertEquals(2, descriptor.getProperties().size());
        assertTrue(descriptor.getProperties().containsKey("db.url"));
    }

    @Test
    void testParseFileIsDirectory(@TempDir Path tempDir) {
        // Create a directory instead of a file
        Path dirPath = tempDir.resolve("test-dir");
        assertDoesNotThrow(() -> Files.createDirectory(dirPath));

        // Attempting to parse a directory should throw ParseException
        ParseException exception = assertThrows(ParseException.class, () -> {
            parser.parseFile(dirPath);
        });
        assertTrue(exception.getMessage().contains("not a regular file"));
    }

    @Test
    void testParseFileNotReadable(@TempDir Path tempDir) throws IOException {
        // Create a file and make it not readable
        Path filePath = tempDir.resolve("unreadable.yaml");
        Files.writeString(filePath, "applicationId: test\nmainClass: Test\nclasspathEntries:\n  - file:///test.jar\n");

        // Make file unreadable
        assertTrue(filePath.toFile().setReadable(false));

        try {
            // Attempting to parse unreadable file should throw ParseException
            ParseException exception = assertThrows(ParseException.class, () -> {
                parser.parseFile(filePath);
            });
            assertTrue(exception.getMessage().contains("not readable"));
        } finally {
            // Restore permissions for cleanup
            filePath.toFile().setReadable(true);
        }
    }

    @Test
    void testParseFileNullApplicationId(@TempDir Path tempDir) throws IOException {
        // Create YAML without applicationId
        String yaml = "name: Test Application\n" +
                "mainClass: com.example.TestApp\n" +
                "classpathEntries:\n" +
                "  - file:///app.jar\n";

        Path filePath = tempDir.resolve("no-appid.yaml");
        Files.writeString(filePath, yaml);

        // Should throw ParseException for missing applicationId
        ParseException exception = assertThrows(ParseException.class, () -> {
            parser.parseFile(filePath);
        });
        assertTrue(exception.getMessage().contains("applicationId is required"));
    }

    @Test
    void testParseStreamNullApplicationId() {
        // Create YAML without applicationId
        String yaml = "name: Test Application\n" +
                "mainClass: com.example.TestApp\n" +
                "classpathEntries:\n" +
                "  - file:///app.jar\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes());

        // Should throw ParseException for missing applicationId
        ParseException exception = assertThrows(ParseException.class, () -> {
            parser.parse(inputStream);
        });
        assertTrue(exception.getMessage().contains("applicationId is required"));
    }

    @Test
    void testGetObjectMapper() {
        // Test the protected getObjectMapper method (exposed for testing)
        assertNotNull(parser.getObjectMapper());
    }
}
