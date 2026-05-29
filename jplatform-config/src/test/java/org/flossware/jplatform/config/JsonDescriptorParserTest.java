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
 * Unit tests for JsonDescriptorParser.
 */
class JsonDescriptorParserTest {

    private JsonDescriptorParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonDescriptorParser();
    }

    @Test
    void testGetSupportedFormat() {
        assertEquals(JsonDescriptorParser.Format.JSON, parser.getSupportedFormat());
    }

    @Test
    void testParseValidJson() throws ParseException {
        String json = "{\n" +
                "  \"applicationId\": \"test-app\",\n" +
                "  \"name\": \"Test Application\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"mainClass\": \"com.example.TestApp\",\n" +
                "  \"classpathEntries\": [\"file:///path/to/app.jar\"]\n" +
                "}";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertEquals("test-app", descriptor.getApplicationId());
        assertEquals("Test Application", descriptor.getName());
        assertEquals("1.0.0", descriptor.getVersion());
        assertEquals("com.example.TestApp", descriptor.getMainClass());
        assertEquals(1, descriptor.getClasspathEntries().size());
    }

    @Test
    void testParseJsonWithThreadPool() throws ParseException {
        String json = "{\n" +
                "  \"applicationId\": \"test-app\",\n" +
                "  \"mainClass\": \"com.example.TestApp\",\n" +
                "  \"classpathEntries\": [\"file:///app.jar\"],\n" +
                "  \"threadPoolConfig\": {\n" +
                "    \"corePoolSize\": 4,\n" +
                "    \"maxPoolSize\": 20,\n" +
                "    \"queueCapacity\": 100\n" +
                "  }\n" +
                "}";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertNotNull(descriptor.getThreadPoolConfig());
        // Verify custom values from JSON are parsed
        assertEquals(4, descriptor.getThreadPoolConfig().getCorePoolSize());
        assertEquals(20, descriptor.getThreadPoolConfig().getMaxPoolSize());
        assertEquals(100, descriptor.getThreadPoolConfig().getQueueCapacity());
    }

    @Test
    void testParseJsonWithSecurity() throws ParseException {
        String json = "{\n" +
                "  \"applicationId\": \"test-app\",\n" +
                "  \"mainClass\": \"com.example.TestApp\",\n" +
                "  \"classpathEntries\": [\"file:///app.jar\"],\n" +
                "  \"securityConfig\": {\n" +
                "    \"allowReflection\": true,\n" +
                "    \"allowNativeCode\": false\n" +
                "  }\n" +
                "}";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertNotNull(descriptor.getSecurityConfig());
        assertTrue(descriptor.getSecurityConfig().isAllowReflection());
        assertFalse(descriptor.getSecurityConfig().isAllowNativeCode());
    }

    @Test
    void testParseJsonWithResources() throws ParseException {
        String json = "{\n" +
                "  \"applicationId\": \"test-app\",\n" +
                "  \"mainClass\": \"com.example.TestApp\",\n" +
                "  \"classpathEntries\": [\"file:///app.jar\"],\n" +
                "  \"resourceConfig\": {\n" +
                "    \"maxHeapMB\": 256,\n" +
                "    \"maxThreads\": 25\n" +
                "  }\n" +
                "}";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertNotNull(descriptor.getResourceConfig());
        assertTrue(descriptor.getResourceConfig().getMaxHeapMB().isPresent());
        assertEquals(256L, descriptor.getResourceConfig().getMaxHeapMB().get());
        assertTrue(descriptor.getResourceConfig().getMaxThreads().isPresent());
        assertEquals(25, descriptor.getResourceConfig().getMaxThreads().get());
    }

    @Test
    void testParseJsonWithProperties() throws ParseException {
        String json = "{\n" +
                "  \"applicationId\": \"test-app\",\n" +
                "  \"mainClass\": \"com.example.TestApp\",\n" +
                "  \"classpathEntries\": [\"file:///app.jar\"],\n" +
                "  \"properties\": {\n" +
                "    \"key1\": \"value1\",\n" +
                "    \"key2\": \"value2\"\n" +
                "  }\n" +
                "}";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertNotNull(descriptor.getProperties());
        assertEquals("value1", descriptor.getProperties().get("key1"));
        assertEquals("value2", descriptor.getProperties().get("key2"));
    }

    @Test
    void testParseInvalidJson() {
        String invalidJson = "{ \"applicationId\": \"test\", invalid json }";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(invalidJson.getBytes());
        assertThrows(ParseException.class, () -> parser.parse(inputStream));
    }

    @Test
    void testParseMissingRequiredFields() {
        String json = "{\n" +
                "  \"applicationId\": \"test-app\",\n" +
                "  \"name\": \"Test\"\n" +
                "}";  // Missing mainClass and classpathEntries

        ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes());
        // Currently throws NullPointerException from builder when required fields missing
        assertThrows(Exception.class, () -> parser.parse(inputStream));
    }

    @Test
    void testParseFileValid(@TempDir Path tempDir) throws IOException, ParseException {
        Path jsonFile = tempDir.resolve("app.json");
        String json = "{\n" +
                "  \"applicationId\": \"file-test-app\",\n" +
                "  \"mainClass\": \"com.example.FileApp\",\n" +
                "  \"classpathEntries\": [\"file:///app.jar\"]\n" +
                "}";
        Files.writeString(jsonFile, json);

        ApplicationDescriptor descriptor = parser.parseFile(jsonFile);

        assertNotNull(descriptor);
        assertEquals("file-test-app", descriptor.getApplicationId());
    }

    @Test
    void testParseFileNotFound() {
        Path nonExistent = Path.of("/nonexistent/app.json");
        assertThrows(ParseException.class, () -> parser.parseFile(nonExistent));
    }

    @Test
    void testParseCompleteDescriptor() throws ParseException {
        String json = "{\n" +
                "  \"applicationId\": \"complete-app\",\n" +
                "  \"name\": \"Complete Application\",\n" +
                "  \"version\": \"3.0.0\",\n" +
                "  \"mainClass\": \"com.example.CompleteApp\",\n" +
                "  \"classpathEntries\": [\n" +
                "    \"file:///lib/app.jar\",\n" +
                "    \"file:///lib/deps.jar\"\n" +
                "  ],\n" +
                "  \"threadPoolConfig\": {\n" +
                "    \"corePoolSize\": 10,\n" +
                "    \"maxPoolSize\": 40,\n" +
                "    \"queueCapacity\": 500\n" +
                "  },\n" +
                "  \"securityConfig\": {\n" +
                "    \"allowReflection\": false,\n" +
                "    \"allowNativeCode\": true\n" +
                "  },\n" +
                "  \"resourceConfig\": {\n" +
                "    \"maxHeapMB\": 2048,\n" +
                "    \"maxThreads\": 200\n" +
                "  },\n" +
                "  \"enableMessaging\": true,\n" +
                "  \"properties\": {\n" +
                "    \"app.env\": \"production\",\n" +
                "    \"app.debug\": \"false\"\n" +
                "  }\n" +
                "}";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes());
        ApplicationDescriptor descriptor = parser.parse(inputStream);

        assertNotNull(descriptor);
        assertEquals("complete-app", descriptor.getApplicationId());
        assertEquals("Complete Application", descriptor.getName());
        assertEquals("3.0.0", descriptor.getVersion());
        assertEquals("com.example.CompleteApp", descriptor.getMainClass());
        assertEquals(2, descriptor.getClasspathEntries().size());

        assertNotNull(descriptor.getThreadPoolConfig());
        // Verify the values from the JSON (10 and 40)
        assertEquals(10, descriptor.getThreadPoolConfig().getCorePoolSize());
        assertEquals(40, descriptor.getThreadPoolConfig().getMaxPoolSize());

        assertNotNull(descriptor.getSecurityConfig());
        assertFalse(descriptor.getSecurityConfig().isAllowReflection());

        assertNotNull(descriptor.getResourceConfig());
        assertTrue(descriptor.getResourceConfig().getMaxHeapMB().isPresent());
        assertEquals(2048L, descriptor.getResourceConfig().getMaxHeapMB().get());

        assertTrue(descriptor.isEnableMessaging());

        assertNotNull(descriptor.getProperties());
        assertEquals(2, descriptor.getProperties().size());
    }
}
