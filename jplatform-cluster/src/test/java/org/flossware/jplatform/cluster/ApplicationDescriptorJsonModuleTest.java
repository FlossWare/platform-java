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

package org.flossware.jplatform.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flossware.jplatform.api.ApplicationDescriptor;
import org.flossware.jplatform.api.ThreadPoolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApplicationDescriptorJsonModule serialization/deserialization.
 */
class ApplicationDescriptorJsonModuleTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new ApplicationDescriptorJsonModule());
    }

    @Test
    void testSerializeAndDeserialize_validDescriptor() throws IOException {
        ApplicationDescriptor original = ApplicationDescriptor.builder()
                .applicationId("app1")
                .name("Test App")
                .version("1.0")
                .mainClass("com.test.Main")
                .addClasspathEntry(URI.create("file:///lib/test.jar"))
                .property("key1", "value1")
                .threadPoolConfig(ThreadPoolConfig.builder()
                        .corePoolSize(5)
                        .maxPoolSize(10)
                        .queueCapacity(100)
                        .keepAliveTimeSeconds(60)
                        .build())
                .enableMessaging(true)
                .build();

        String json = mapper.writeValueAsString(original);
        ApplicationDescriptor deserialized = mapper.readValue(json, ApplicationDescriptor.class);

        assertEquals(original.getApplicationId(), deserialized.getApplicationId());
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getVersion(), deserialized.getVersion());
        assertEquals(original.getMainClass(), deserialized.getMainClass());
        assertEquals(original.getClasspathEntries(), deserialized.getClasspathEntries());
        assertEquals(original.getProperties(), deserialized.getProperties());
        assertEquals(original.isEnableMessaging(), deserialized.isEnableMessaging());

        ThreadPoolConfig originalPool = original.getThreadPoolConfig();
        ThreadPoolConfig deserializedPool = deserialized.getThreadPoolConfig();
        assertNotNull(deserializedPool);
        assertEquals(originalPool.getCorePoolSize(), deserializedPool.getCorePoolSize());
        assertEquals(originalPool.getMaxPoolSize(), deserializedPool.getMaxPoolSize());
        assertEquals(originalPool.getQueueCapacity(), deserializedPool.getQueueCapacity());
        assertEquals(originalPool.getKeepAliveTimeSeconds(), deserializedPool.getKeepAliveTimeSeconds());
    }

    @Test
    void testDeserialize_missingApplicationId() {
        String json = "{\"name\":\"App\",\"version\":\"1.0\",\"mainClass\":\"com.App\"}";

        IOException exception = assertThrows(IOException.class,
                () -> mapper.readValue(json, ApplicationDescriptor.class));

        assertTrue(exception.getMessage().contains("Missing required field: applicationId"));
    }

    @Test
    void testDeserialize_missingName() {
        String json = "{\"applicationId\":\"app1\",\"version\":\"1.0\",\"mainClass\":\"com.App\"}";

        IOException exception = assertThrows(IOException.class,
                () -> mapper.readValue(json, ApplicationDescriptor.class));

        assertTrue(exception.getMessage().contains("Missing required field: name"));
    }

    @Test
    void testDeserialize_missingVersion() {
        String json = "{\"applicationId\":\"app1\",\"name\":\"App\",\"mainClass\":\"com.App\"}";

        IOException exception = assertThrows(IOException.class,
                () -> mapper.readValue(json, ApplicationDescriptor.class));

        assertTrue(exception.getMessage().contains("Missing required field: version"));
    }

    @Test
    void testDeserialize_missingMainClass() {
        String json = "{\"applicationId\":\"app1\",\"name\":\"App\",\"version\":\"1.0\"}";

        IOException exception = assertThrows(IOException.class,
                () -> mapper.readValue(json, ApplicationDescriptor.class));

        assertTrue(exception.getMessage().contains("Missing required field: mainClass"));
    }

    @Test
    void testDeserialize_threadPoolMissingCorePoolSize() {
        String json = "{" +
                "\"applicationId\":\"app1\"," +
                "\"name\":\"App\"," +
                "\"version\":\"1.0\"," +
                "\"mainClass\":\"com.App\"," +
                "\"threadPool\":{" +
                "\"maxPoolSize\":10," +
                "\"queueCapacity\":100," +
                "\"keepAliveTimeSeconds\":60" +
                "}" +
                "}";

        IOException exception = assertThrows(IOException.class,
                () -> mapper.readValue(json, ApplicationDescriptor.class));

        assertTrue(exception.getMessage().contains("threadPool object is incomplete"));
    }

    @Test
    void testDeserialize_threadPoolMissingMaxPoolSize() {
        String json = "{" +
                "\"applicationId\":\"app1\"," +
                "\"name\":\"App\"," +
                "\"version\":\"1.0\"," +
                "\"mainClass\":\"com.App\"," +
                "\"threadPool\":{" +
                "\"corePoolSize\":5," +
                "\"queueCapacity\":100," +
                "\"keepAliveTimeSeconds\":60" +
                "}" +
                "}";

        IOException exception = assertThrows(IOException.class,
                () -> mapper.readValue(json, ApplicationDescriptor.class));

        assertTrue(exception.getMessage().contains("threadPool object is incomplete"));
    }

    @Test
    void testDeserialize_threadPoolMissingQueueCapacity() {
        String json = "{" +
                "\"applicationId\":\"app1\"," +
                "\"name\":\"App\"," +
                "\"version\":\"1.0\"," +
                "\"mainClass\":\"com.App\"," +
                "\"threadPool\":{" +
                "\"corePoolSize\":5," +
                "\"maxPoolSize\":10," +
                "\"keepAliveTimeSeconds\":60" +
                "}" +
                "}";

        IOException exception = assertThrows(IOException.class,
                () -> mapper.readValue(json, ApplicationDescriptor.class));

        assertTrue(exception.getMessage().contains("threadPool object is incomplete"));
    }

    @Test
    void testDeserialize_threadPoolMissingKeepAliveTimeSeconds() {
        String json = "{" +
                "\"applicationId\":\"app1\"," +
                "\"name\":\"App\"," +
                "\"version\":\"1.0\"," +
                "\"mainClass\":\"com.App\"," +
                "\"threadPool\":{" +
                "\"corePoolSize\":5," +
                "\"maxPoolSize\":10," +
                "\"queueCapacity\":100" +
                "}" +
                "}";

        IOException exception = assertThrows(IOException.class,
                () -> mapper.readValue(json, ApplicationDescriptor.class));

        assertTrue(exception.getMessage().contains("threadPool object is incomplete"));
    }

    @Test
    void testDeserialize_noThreadPool() throws IOException {
        String json = "{" +
                "\"applicationId\":\"app1\"," +
                "\"name\":\"App\"," +
                "\"version\":\"1.0\"," +
                "\"mainClass\":\"com.App\"" +
                "}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        assertEquals("app1", descriptor.getApplicationId());
        assertEquals("App", descriptor.getName());
        assertEquals("1.0", descriptor.getVersion());
        assertEquals("com.App", descriptor.getMainClass());
        // When no threadPool is provided, ApplicationDescriptor uses default config
        assertNotNull(descriptor.getThreadPoolConfig());
        assertEquals(2, descriptor.getThreadPoolConfig().getCorePoolSize());
        assertEquals(10, descriptor.getThreadPoolConfig().getMaxPoolSize());
        assertEquals(100, descriptor.getThreadPoolConfig().getQueueCapacity());
        assertEquals(60, descriptor.getThreadPoolConfig().getKeepAliveTimeSeconds());
    }

    @Test
    void testDeserialize_emptyClasspath() throws IOException {
        String json = "{" +
                "\"applicationId\":\"app1\"," +
                "\"name\":\"App\"," +
                "\"version\":\"1.0\"," +
                "\"mainClass\":\"com.App\"," +
                "\"classpath\":[]" +
                "}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        assertEquals("app1", descriptor.getApplicationId());
        assertTrue(descriptor.getClasspathEntries().isEmpty());
    }

    @Test
    void testDeserialize_emptyProperties() throws IOException {
        String json = "{" +
                "\"applicationId\":\"app1\"," +
                "\"name\":\"App\"," +
                "\"version\":\"1.0\"," +
                "\"mainClass\":\"com.App\"," +
                "\"properties\":{}," +
                "\"enableMessaging\":false" +
                "}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        assertEquals("app1", descriptor.getApplicationId());
        assertTrue(descriptor.getProperties().isEmpty());
        assertFalse(descriptor.isEnableMessaging());
    }

    @Test
    void testDeserialize_withClasspathAndProperties() throws IOException {
        String json = "{" +
                "\"applicationId\":\"app1\"," +
                "\"name\":\"App\"," +
                "\"version\":\"1.0\"," +
                "\"mainClass\":\"com.App\"," +
                "\"classpath\":[\"file:///lib/a.jar\",\"file:///lib/b.jar\"]," +
                "\"properties\":{\"key1\":\"value1\",\"key2\":\"value2\"}," +
                "\"enableMessaging\":true" +
                "}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        assertEquals("app1", descriptor.getApplicationId());
        assertEquals(2, descriptor.getClasspathEntries().size());
        assertEquals(2, descriptor.getProperties().size());
        assertEquals("value1", descriptor.getProperties().get("key1"));
        assertEquals("value2", descriptor.getProperties().get("key2"));
        assertTrue(descriptor.isEnableMessaging());
    }
}
