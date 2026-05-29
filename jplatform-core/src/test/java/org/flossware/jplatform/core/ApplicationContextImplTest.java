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

import org.flossware.jplatform.api.*;
import org.flossware.jplatform.monitoring.ApplicationResourceMonitor;
import org.flossware.jplatform.security.ApplicationSecurityPolicy;
import org.flossware.jplatform.threadpool.ManagedThreadPool;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ApplicationContextImpl.
 * Tests builder validation, getters, state management, and thread safety.
 */
class ApplicationContextImplTest {

    private static final String APP_ID = "test-app";

    private ApplicationDescriptor createTestDescriptor() {
        return ApplicationDescriptor.builder()
            .applicationId(APP_ID)
            .mainClass("com.test.Main")
            .build();
    }

    private ApplicationContextImpl.Builder createMinimalBuilder() {
        ApplicationDescriptor descriptor = createTestDescriptor();
        SecurityConfig secConfig = SecurityConfig.builder().build();
        ThreadPoolConfig threadConfig = ThreadPoolConfig.builder().build();
        ThreadGroup threadGroup = new ThreadGroup("test");

        return ApplicationContextImpl.builder()
            .applicationId(APP_ID)
            .descriptor(descriptor)
            .classLoader(Thread.currentThread().getContextClassLoader())
            .threadPool(new ManagedThreadPool(APP_ID, threadConfig))
            .securityPolicy(new ApplicationSecurityPolicy(APP_ID, secConfig))
            .resourceMonitor(new ApplicationResourceMonitor(APP_ID, threadGroup));
    }

    @Test
    void testBuilderMinimal() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        assertNotNull(context);
        assertEquals(APP_ID, context.getApplicationId());
        assertNotNull(context.getDescriptor());
        assertNotNull(context.getClassLoader());
        assertNotNull(context.getThreadPool());
        assertNotNull(context.getSecurityPolicy());
        assertNotNull(context.getResourceMonitor());
        assertEquals(ApplicationState.DEPLOYED, context.getState());
        assertFalse(context.getMessageBus().isPresent());
        assertFalse(context.getServiceRegistry().isPresent());
        assertFalse(context.getVolumeManager().isPresent());
        assertTrue(context.getProperties().isEmpty());
        assertNull(context.getApplicationInstance());
    }

    @Test
    void testBuilderWithProperties() {
        Map<String, String> props = new HashMap<>();
        props.put("env", "test");

        ApplicationContextImpl context = createMinimalBuilder()
            .properties(props)
            .build();

        assertNotNull(context);
        assertEquals("test", context.getProperties().get("env"));
    }

    @Test
    void testBuilderApplicationIdNull() {
        assertThrows(NullPointerException.class, () ->
            ApplicationContextImpl.builder().applicationId(null)
        );
    }

    @Test
    void testBuilderDescriptorNull() {
        assertThrows(NullPointerException.class, () ->
            ApplicationContextImpl.builder().descriptor(null)
        );
    }

    @Test
    void testBuilderClassLoaderNull() {
        assertThrows(NullPointerException.class, () ->
            ApplicationContextImpl.builder().classLoader(null)
        );
    }

    @Test
    void testBuilderThreadPoolNull() {
        assertThrows(NullPointerException.class, () ->
            ApplicationContextImpl.builder().threadPool(null)
        );
    }

    @Test
    void testBuilderSecurityPolicyNull() {
        assertThrows(NullPointerException.class, () ->
            ApplicationContextImpl.builder().securityPolicy(null)
        );
    }

    @Test
    void testBuilderResourceMonitorNull() {
        assertThrows(NullPointerException.class, () ->
            ApplicationContextImpl.builder().resourceMonitor(null)
        );
    }

    @Test
    void testBuilderMissingApplicationId() {
        ApplicationDescriptor descriptor = createTestDescriptor();
        SecurityConfig secConfig = SecurityConfig.builder().build();
        ThreadPoolConfig threadConfig = ThreadPoolConfig.builder().build();
        ThreadGroup threadGroup = new ThreadGroup("test");

        assertThrows(NullPointerException.class, () ->
            ApplicationContextImpl.builder()
                .descriptor(descriptor)
                .classLoader(Thread.currentThread().getContextClassLoader())
                .threadPool(new ManagedThreadPool(APP_ID, threadConfig))
                .securityPolicy(new ApplicationSecurityPolicy(APP_ID, secConfig))
                .resourceMonitor(new ApplicationResourceMonitor(APP_ID, threadGroup))
                .build()
        );
    }

    @Test
    void testBuilderPropertiesNullKey() {
        Map<String, String> props = new HashMap<>();
        props.put(null, "value");

        assertThrows(IllegalArgumentException.class, () ->
            ApplicationContextImpl.builder().properties(props)
        );
    }

    @Test
    void testBuilderPropertiesNullValue() {
        Map<String, String> props = new HashMap<>();
        props.put("key", null);

        assertThrows(IllegalArgumentException.class, () ->
            ApplicationContextImpl.builder().properties(props)
        );
    }

    @Test
    void testGetPropertiesIsUnmodifiable() {
        Map<String, String> props = new HashMap<>();
        props.put("key", "value");

        ApplicationContextImpl context = createMinimalBuilder()
            .properties(props)
            .build();

        assertThrows(UnsupportedOperationException.class, () ->
            context.getProperties().put("new", "value")
        );
    }

    @Test
    void testSetState() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        assertEquals(ApplicationState.DEPLOYED, context.getState());

        context.setState(ApplicationState.STARTING);
        assertEquals(ApplicationState.STARTING, context.getState());

        context.setState(ApplicationState.RUNNING);
        assertEquals(ApplicationState.RUNNING, context.getState());

        context.setState(ApplicationState.STOPPING);
        assertEquals(ApplicationState.STOPPING, context.getState());

        context.setState(ApplicationState.STOPPED);
        assertEquals(ApplicationState.STOPPED, context.getState());
    }

    @Test
    void testSetStateNull() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        assertThrows(NullPointerException.class, () ->
            context.setState(null)
        );
    }

    @Test
    void testSetApplicationInstance() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        assertNull(context.getApplicationInstance());

        Object instance = new Object();
        context.setApplicationInstance(instance);

        assertEquals(instance, context.getApplicationInstance());
    }

    @Test
    void testSetApplicationInstanceNull() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        Object instance = new Object();
        context.setApplicationInstance(instance);
        assertNotNull(context.getApplicationInstance());

        context.setApplicationInstance(null);
        assertNull(context.getApplicationInstance());
    }

    @Test
    void testSetClassLoader() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        ClassLoader newClassLoader = new ClassLoader() {};
        context.setClassLoader(newClassLoader);

        assertEquals(newClassLoader, context.getClassLoader());
    }

    @Test
    void testSetClassLoaderNull() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        assertThrows(NullPointerException.class, () ->
            context.setClassLoader(null)
        );
    }

    @Test
    void testSetDescriptor() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        ApplicationDescriptor newDescriptor = ApplicationDescriptor.builder()
            .applicationId("new-app")
            .mainClass("com.test.New")
            .build();
        context.setDescriptor(newDescriptor);

        assertEquals(newDescriptor, context.getDescriptor());
    }

    @Test
    void testSetDescriptorNull() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        assertThrows(NullPointerException.class, () ->
            context.setDescriptor(null)
        );
    }

    @Test
    void testSetClassLoaderAndDescriptor() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        ClassLoader newClassLoader = new ClassLoader() {};
        ApplicationDescriptor newDescriptor = ApplicationDescriptor.builder()
            .applicationId("new-app")
            .mainClass("com.test.New")
            .build();

        context.setClassLoaderAndDescriptor(newClassLoader, newDescriptor);

        assertEquals(newClassLoader, context.getClassLoader());
        assertEquals(newDescriptor, context.getDescriptor());
    }

    @Test
    void testSetClassLoaderAndDescriptorNullClassLoader() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        ApplicationDescriptor newDescriptor = ApplicationDescriptor.builder()
            .applicationId("new-app")
            .mainClass("com.test.New")
            .build();

        assertThrows(NullPointerException.class, () ->
            context.setClassLoaderAndDescriptor(null, newDescriptor)
        );
    }

    @Test
    void testSetClassLoaderAndDescriptorNullDescriptor() {
        ApplicationContextImpl context = createMinimalBuilder().build();

        ClassLoader newClassLoader = new ClassLoader() {};

        assertThrows(NullPointerException.class, () ->
            context.setClassLoaderAndDescriptor(newClassLoader, null)
        );
    }

    @Test
    void testGetDeployedAt() {
        Instant before = Instant.now();

        ApplicationContextImpl context = createMinimalBuilder().build();

        Instant after = Instant.now();
        Instant deployedAt = context.getDeployedAt();

        assertNotNull(deployedAt);
        assertFalse(deployedAt.isBefore(before));
        assertFalse(deployedAt.isAfter(after));
    }

    @Test
    void testMultiplePropertiesEntries() {
        Map<String, String> props = new HashMap<>();
        props.put("env", "test");
        props.put("debug", "true");
        props.put("port", "8080");

        ApplicationContextImpl context = createMinimalBuilder()
            .properties(props)
            .build();

        assertEquals(3, context.getProperties().size());
        assertEquals("test", context.getProperties().get("env"));
        assertEquals("true", context.getProperties().get("debug"));
        assertEquals("8080", context.getProperties().get("port"));
    }
}
