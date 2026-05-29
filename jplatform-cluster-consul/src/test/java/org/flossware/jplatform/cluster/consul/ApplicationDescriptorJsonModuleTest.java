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

package org.flossware.jplatform.cluster.consul;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flossware.jplatform.api.ApplicationDescriptor;
import org.flossware.jplatform.api.ThreadPoolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ApplicationDescriptorJsonModule.
 * Tests custom Jackson serialization/deserialization of ApplicationDescriptor.
 */
class ApplicationDescriptorJsonModuleTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new ApplicationDescriptorJsonModule());
    }

    @Test
    void testSerializeMinimalDescriptor() throws Exception {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .name("Test App")
                .version("1.0")
                .mainClass("com.example.Main")
                .build();

        String json = mapper.writeValueAsString(descriptor);

        assertNotNull(json);
        assertTrue(json.contains("\"applicationId\":\"app1\""));
        assertTrue(json.contains("\"name\":\"Test App\""));
        assertTrue(json.contains("\"version\":\"1.0\""));
        assertTrue(json.contains("\"mainClass\":\"com.example.Main\""));
    }

    @Test
    void testDeserializeMinimalDescriptor() throws Exception {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\",\"mainClass\":\"com.example.Main\"}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        assertNotNull(descriptor);
        assertEquals("app1", descriptor.getApplicationId());
        assertEquals("Test App", descriptor.getName());
        assertEquals("1.0", descriptor.getVersion());
        assertEquals("com.example.Main", descriptor.getMainClass());
    }

    @Test
    void testSerializeWithClasspath() throws Exception {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .name("Test App")
                .version("1.0")
                .mainClass("com.example.Main")
                .addClasspathEntry(URI.create("file:///app/lib/app.jar"))
                .addClasspathEntry(URI.create("file:///app/lib/dep.jar"))
                .build();

        String json = mapper.writeValueAsString(descriptor);

        assertTrue(json.contains("\"classpath\""));
        assertTrue(json.contains("file:///app/lib/app.jar"));
        assertTrue(json.contains("file:///app/lib/dep.jar"));
    }

    @Test
    void testDeserializeWithClasspath() throws Exception {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\"," +
                "\"mainClass\":\"com.example.Main\"," +
                "\"classpath\":[\"file:///app/lib/app.jar\",\"file:///app/lib/dep.jar\"]}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        assertEquals(2, descriptor.getClasspathEntries().size());
        assertTrue(descriptor.getClasspathEntries().contains(URI.create("file:///app/lib/app.jar")));
        assertTrue(descriptor.getClasspathEntries().contains(URI.create("file:///app/lib/dep.jar")));
    }

    @Test
    void testSerializeWithProperties() throws Exception {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .name("Test App")
                .version("1.0")
                .mainClass("com.example.Main")
                .property("env", "production")
                .property("region", "us-east-1")
                .build();

        String json = mapper.writeValueAsString(descriptor);

        assertTrue(json.contains("\"properties\""));
        assertTrue(json.contains("\"env\":\"production\""));
        assertTrue(json.contains("\"region\":\"us-east-1\""));
    }

    @Test
    void testDeserializeWithProperties() throws Exception {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\"," +
                "\"mainClass\":\"com.example.Main\"," +
                "\"properties\":{\"env\":\"production\",\"region\":\"us-east-1\"}}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        Map<String, String> props = descriptor.getProperties();
        assertEquals(2, props.size());
        assertEquals("production", props.get("env"));
        assertEquals("us-east-1", props.get("region"));
    }

    @Test
    void testSerializeWithThreadPoolConfig() throws Exception {
        ThreadPoolConfig threadPoolConfig = ThreadPoolConfig.builder()
                .corePoolSize(4)
                .maxPoolSize(8)
                .queueCapacity(100)
                .keepAliveTimeSeconds(60)
                .build();

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .name("Test App")
                .version("1.0")
                .mainClass("com.example.Main")
                .threadPoolConfig(threadPoolConfig)
                .build();

        String json = mapper.writeValueAsString(descriptor);

        assertTrue(json.contains("\"threadPool\""));
        assertTrue(json.contains("\"corePoolSize\":4"));
        assertTrue(json.contains("\"maxPoolSize\":8"));
        assertTrue(json.contains("\"queueCapacity\":100"));
        assertTrue(json.contains("\"keepAliveTimeSeconds\":60"));
    }

    @Test
    void testDeserializeWithThreadPoolConfig() throws Exception {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\"," +
                "\"mainClass\":\"com.example.Main\"," +
                "\"threadPool\":{\"corePoolSize\":4,\"maxPoolSize\":8,\"queueCapacity\":100,\"keepAliveTimeSeconds\":60}}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        ThreadPoolConfig config = descriptor.getThreadPoolConfig();
        assertNotNull(config);
        assertEquals(4, config.getCorePoolSize());
        assertEquals(8, config.getMaxPoolSize());
        assertEquals(100, config.getQueueCapacity());
        assertEquals(60, config.getKeepAliveTimeSeconds());
    }

    @Test
    void testSerializeWithMessagingEnabled() throws Exception {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .name("Test App")
                .version("1.0")
                .mainClass("com.example.Main")
                .enableMessaging(true)
                .build();

        String json = mapper.writeValueAsString(descriptor);

        assertTrue(json.contains("\"enableMessaging\":true"));
    }

    @Test
    void testDeserializeWithMessagingEnabled() throws Exception {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\"," +
                "\"mainClass\":\"com.example.Main\",\"enableMessaging\":true}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        assertTrue(descriptor.isEnableMessaging());
    }

    @Test
    void testSerializeDeserializeRoundTrip() throws Exception {
        ApplicationDescriptor original = ApplicationDescriptor.builder()
                .applicationId("app1")
                .name("Test App")
                .version("1.0")
                .mainClass("com.example.Main")
                .addClasspathEntry(URI.create("file:///app/app.jar"))
                .property("key1", "value1")
                .property("key2", "value2")
                .threadPoolConfig(ThreadPoolConfig.builder()
                        .corePoolSize(2)
                        .maxPoolSize(4)
                        .queueCapacity(50)
                        .keepAliveTimeSeconds(30)
                        .build())
                .enableMessaging(true)
                .build();

        String json = mapper.writeValueAsString(original);
        ApplicationDescriptor deserialized = mapper.readValue(json, ApplicationDescriptor.class);

        assertEquals(original.getApplicationId(), deserialized.getApplicationId());
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getVersion(), deserialized.getVersion());
        assertEquals(original.getMainClass(), deserialized.getMainClass());
        assertEquals(original.getClasspathEntries().size(), deserialized.getClasspathEntries().size());
        assertEquals(original.getProperties().size(), deserialized.getProperties().size());
        assertEquals(original.isEnableMessaging(), deserialized.isEnableMessaging());
        assertEquals(original.getThreadPoolConfig().getCorePoolSize(),
                deserialized.getThreadPoolConfig().getCorePoolSize());
    }

    @Test
    void testDeserializeEmptyClasspath() throws Exception {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\"," +
                "\"mainClass\":\"com.example.Main\",\"classpath\":[]}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        assertTrue(descriptor.getClasspathEntries().isEmpty());
    }

    @Test
    void testDeserializeEmptyProperties() throws Exception {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\"," +
                "\"mainClass\":\"com.example.Main\",\"properties\":{}}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        assertTrue(descriptor.getProperties().isEmpty());
    }

    @Test
    void testDeserializeNullClasspath() throws Exception {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\"," +
                "\"mainClass\":\"com.example.Main\"}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        // Should have empty classpath, not null
        assertNotNull(descriptor.getClasspathEntries());
        assertTrue(descriptor.getClasspathEntries().isEmpty());
    }

    @Test
    void testDeserializeNullThreadPool() throws Exception {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\"," +
                "\"mainClass\":\"com.example.Main\"}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        // Should have default thread pool config
        assertNotNull(descriptor.getThreadPoolConfig());
    }

    @Test
    void testDeserializeNullMessaging() throws Exception {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\"," +
                "\"mainClass\":\"com.example.Main\"}";

        ApplicationDescriptor descriptor = mapper.readValue(json, ApplicationDescriptor.class);

        // Default is false
        assertFalse(descriptor.isEnableMessaging());
    }

    @Test
    void testSerializeWithEmptyClasspath() throws Exception {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .name("Test App")
                .version("1.0")
                .mainClass("com.example.Main")
                .build();

        String json = mapper.writeValueAsString(descriptor);

        assertTrue(json.contains("\"classpath\":[]"));
    }

    @Test
    void testSerializeWithEmptyProperties() throws Exception {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .name("Test App")
                .version("1.0")
                .mainClass("com.example.Main")
                .build();

        String json = mapper.writeValueAsString(descriptor);

        assertTrue(json.contains("\"properties\":{}"));
    }
}
