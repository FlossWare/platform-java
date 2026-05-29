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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ApplicationDescriptorDTO and nested DTOs.
 * Tests deserialization, conversion, and validation.
 */
class ApplicationDescriptorDTOTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    void testMinimalDescriptor() throws Exception {
        String json = "{\"applicationId\":\"test-app\",\"mainClass\":\"com.example.Main\"}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);
        ApplicationDescriptor descriptor = dto.toApplicationDescriptor();

        assertNotNull(descriptor);
        assertEquals("test-app", descriptor.getApplicationId());
        assertEquals("com.example.Main", descriptor.getMainClass());
    }

    @Test
    void testFullDescriptor() throws Exception {
        String json = "{"
            + "\"applicationId\":\"test-app\","
            + "\"name\":\"Test Application\","
            + "\"version\":\"1.0.0\","
            + "\"mainClass\":\"com.example.Main\","
            + "\"classpathEntries\":[\"file:///app/lib/app.jar\",\"file:///app/lib/deps.jar\"],"
            + "\"properties\":{\"env\":\"production\"},"
            + "\"enableMessaging\":true"
            + "}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);
        ApplicationDescriptor descriptor = dto.toApplicationDescriptor();

        assertEquals("test-app", descriptor.getApplicationId());
        assertEquals("Test Application", descriptor.getName());
        assertEquals("1.0.0", descriptor.getVersion());
        assertEquals("com.example.Main", descriptor.getMainClass());
        assertEquals(2, descriptor.getClasspathEntries().size());
        assertTrue(descriptor.isEnableMessaging());
        assertEquals("production", descriptor.getProperties().get("env"));
    }

    @Test
    void testMissingApplicationId() throws Exception {
        String json = "{\"mainClass\":\"com.example.Main\"}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toApplicationDescriptor()
        );
        assertTrue(ex.getMessage().contains("applicationId"));
    }

    @Test
    void testEmptyApplicationId() throws Exception {
        String json = "{\"applicationId\":\"   \",\"mainClass\":\"com.example.Main\"}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toApplicationDescriptor()
        );
        assertTrue(ex.getMessage().contains("applicationId"));
    }

    @Test
    void testMissingMainClass() throws Exception {
        String json = "{\"applicationId\":\"test-app\"}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toApplicationDescriptor()
        );
        assertTrue(ex.getMessage().contains("mainClass"));
    }

    @Test
    void testEmptyMainClass() throws Exception {
        String json = "{\"applicationId\":\"test-app\",\"mainClass\":\"   \"}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toApplicationDescriptor()
        );
        assertTrue(ex.getMessage().contains("mainClass"));
    }

    @Test
    void testInvalidClasspathURI() throws Exception {
        String json = "{\"applicationId\":\"test-app\",\"mainClass\":\"com.example.Main\","
            + "\"classpathEntries\":[\"invalid uri with spaces\"]}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toApplicationDescriptor()
        );
        assertTrue(ex.getMessage().contains("Invalid URI"));
    }

    @Test
    void testGetApplicationId() throws Exception {
        String json = "{\"applicationId\":\"test-app\",\"mainClass\":\"com.example.Main\"}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);

        assertEquals("test-app", dto.getApplicationId());
    }

    @Test
    void testGetMainClass() throws Exception {
        String json = "{\"applicationId\":\"test-app\",\"mainClass\":\"com.example.Main\"}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);

        assertEquals("com.example.Main", dto.getMainClass());
    }

    // ThreadPoolConfigDTO Tests

    @Test
    void testThreadPoolConfigMinimal() throws Exception {
        String json = "{\"applicationId\":\"test-app\",\"mainClass\":\"com.example.Main\","
            + "\"threadPoolConfig\":{}}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);
        ApplicationDescriptor descriptor = dto.toApplicationDescriptor();

        assertNotNull(descriptor.getThreadPoolConfig());
    }

    @Test
    void testThreadPoolConfigFull() throws Exception {
        String json = "{\"corePoolSize\":5,\"maxPoolSize\":20,\"keepAliveTimeSeconds\":60,\"queueCapacity\":100}";

        ApplicationDescriptorDTO.ThreadPoolConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.ThreadPoolConfigDTO.class);
        ThreadPoolConfig config = dto.toThreadPoolConfig();

        assertEquals(5, config.getCorePoolSize());
        assertEquals(20, config.getMaxPoolSize());
        assertEquals(60L, config.getKeepAliveTimeSeconds());
        assertEquals(100, config.getQueueCapacity());
    }

    @Test
    void testThreadPoolConfigPartial() throws Exception {
        String json = "{\"corePoolSize\":10,\"maxPoolSize\":50}";

        ApplicationDescriptorDTO.ThreadPoolConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.ThreadPoolConfigDTO.class);
        ThreadPoolConfig config = dto.toThreadPoolConfig();

        assertEquals(10, config.getCorePoolSize());
        assertEquals(50, config.getMaxPoolSize());
    }

    // SecurityConfigDTO Tests

    @Test
    void testSecurityConfigMinimal() throws Exception {
        String json = "{}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);
        SecurityConfig config = dto.toSecurityConfig();

        assertNotNull(config);
    }

    @Test
    void testSecurityConfigWithFilePermissions() throws Exception {
        String json = "{\"filePermissions\":[{\"path\":\"/app/data\",\"actions\":\"read,write\"}]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);
        SecurityConfig config = dto.toSecurityConfig();

        assertEquals(1, config.getFilePermissions().size());
    }

    @Test
    void testSecurityConfigWithSocketPermissions() throws Exception {
        String json = "{\"socketPermissions\":[{\"host\":\"localhost:8080\",\"actions\":\"connect,resolve\"}]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);
        SecurityConfig config = dto.toSecurityConfig();

        assertEquals(1, config.getSocketPermissions().size());
    }

    @Test
    void testSecurityConfigWithRuntimePermissions() throws Exception {
        String json = "{\"runtimePermissions\":[\"createClassLoader\",\"setContextClassLoader\"]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);
        SecurityConfig config = dto.toSecurityConfig();

        assertEquals(2, config.getRuntimePermissions().size());
    }

    @Test
    void testSecurityConfigWithReflectionAndNativeCode() throws Exception {
        String json = "{\"allowReflection\":true,\"allowNativeCode\":false}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);
        SecurityConfig config = dto.toSecurityConfig();

        assertTrue(config.isAllowReflection());
        assertFalse(config.isAllowNativeCode());
    }

    @Test
    void testSecurityConfigNullFilePermissionEntry() throws Exception {
        String json = "{\"filePermissions\":[null]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toSecurityConfig()
        );
        assertTrue(ex.getMessage().contains("File permission entry cannot be null"));
    }

    @Test
    void testSecurityConfigEmptyFilePermissionPath() throws Exception {
        String json = "{\"filePermissions\":[{\"path\":\"\",\"actions\":\"read\"}]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toSecurityConfig()
        );
        assertTrue(ex.getMessage().contains("File permission path"));
    }

    @Test
    void testSecurityConfigNullFilePermissionPath() throws Exception {
        String json = "{\"filePermissions\":[{\"path\":null,\"actions\":\"read\"}]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toSecurityConfig()
        );
        assertTrue(ex.getMessage().contains("File permission path"));
    }

    @Test
    void testSecurityConfigEmptyFilePermissionActions() throws Exception {
        String json = "{\"filePermissions\":[{\"path\":\"/app/data\",\"actions\":\"\"}]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toSecurityConfig()
        );
        assertTrue(ex.getMessage().contains("File permission actions"));
    }

    @Test
    void testSecurityConfigNullSocketPermissionEntry() throws Exception {
        String json = "{\"socketPermissions\":[null]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toSecurityConfig()
        );
        assertTrue(ex.getMessage().contains("Socket permission entry cannot be null"));
    }

    @Test
    void testSecurityConfigEmptySocketPermissionHost() throws Exception {
        String json = "{\"socketPermissions\":[{\"host\":\"\",\"actions\":\"connect\"}]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toSecurityConfig()
        );
        assertTrue(ex.getMessage().contains("Socket permission host"));
    }

    @Test
    void testSecurityConfigEmptySocketPermissionActions() throws Exception {
        String json = "{\"socketPermissions\":[{\"host\":\"localhost:8080\",\"actions\":\"\"}]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toSecurityConfig()
        );
        assertTrue(ex.getMessage().contains("Socket permission actions"));
    }

    @Test
    void testSecurityConfigEmptyRuntimePermission() throws Exception {
        String json = "{\"runtimePermissions\":[\"\"]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toSecurityConfig()
        );
        assertTrue(ex.getMessage().contains("Runtime permission name"));
    }

    @Test
    void testSecurityConfigNullRuntimePermission() throws Exception {
        String json = "{\"runtimePermissions\":[null]}";

        ApplicationDescriptorDTO.SecurityConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.SecurityConfigDTO.class);

        ParseException ex = assertThrows(ParseException.class, () ->
            dto.toSecurityConfig()
        );
        assertTrue(ex.getMessage().contains("Runtime permission name"));
    }

    // ResourceConfigDTO Tests

    @Test
    void testResourceConfigMinimal() throws Exception {
        String json = "{}";

        ApplicationDescriptorDTO.ResourceConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.ResourceConfigDTO.class);
        ResourceConfig config = dto.toResourceConfig();

        assertNotNull(config);
    }

    @Test
    void testResourceConfigFull() throws Exception {
        String json = "{\"maxHeapMB\":512,\"maxThreads\":50,\"maxCpuTimeSeconds\":60}";

        ApplicationDescriptorDTO.ResourceConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.ResourceConfigDTO.class);
        ResourceConfig config = dto.toResourceConfig();

        assertTrue(config.getMaxHeapMB().isPresent());
        assertEquals(512L, config.getMaxHeapMB().get());
        assertTrue(config.getMaxThreads().isPresent());
        assertEquals(50, config.getMaxThreads().get());
        assertTrue(config.getMaxCpuTimeSeconds().isPresent());
        assertEquals(60L, config.getMaxCpuTimeSeconds().get());
    }

    @Test
    void testResourceConfigPartial() throws Exception {
        String json = "{\"maxHeapMB\":256}";

        ApplicationDescriptorDTO.ResourceConfigDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.ResourceConfigDTO.class);
        ResourceConfig config = dto.toResourceConfig();

        assertTrue(config.getMaxHeapMB().isPresent());
        assertEquals(256L, config.getMaxHeapMB().get());
        assertFalse(config.getMaxThreads().isPresent());
        assertFalse(config.getMaxCpuTimeSeconds().isPresent());
    }

    // Integration Tests

    @Test
    void testCompleteDescriptorWithAllConfigs() throws Exception {
        String json = "{"
            + "\"applicationId\":\"test-app\","
            + "\"name\":\"Test Application\","
            + "\"version\":\"1.0.0\","
            + "\"mainClass\":\"com.example.Main\","
            + "\"classpathEntries\":[\"file:///app/lib/app.jar\"],"
            + "\"enableMessaging\":true,"
            + "\"threadPoolConfig\":{\"corePoolSize\":5,\"maxPoolSize\":20},"
            + "\"securityConfig\":{\"allowReflection\":true},"
            + "\"resourceConfig\":{\"maxHeapMB\":512}"
            + "}";

        ApplicationDescriptorDTO dto = mapper.readValue(json, ApplicationDescriptorDTO.class);
        ApplicationDescriptor descriptor = dto.toApplicationDescriptor();

        assertNotNull(descriptor);
        assertEquals("test-app", descriptor.getApplicationId());
        assertEquals(5, descriptor.getThreadPoolConfig().getCorePoolSize());
        assertTrue(descriptor.getSecurityConfig().isAllowReflection());
        assertTrue(descriptor.getResourceConfig().getMaxHeapMB().isPresent());
        assertEquals(512L, descriptor.getResourceConfig().getMaxHeapMB().get());
    }
}
