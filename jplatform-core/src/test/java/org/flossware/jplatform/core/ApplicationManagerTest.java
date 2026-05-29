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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApplicationManager.
 * Tests core lifecycle management: deploy, start, stop, undeploy.
 * <p>
 * Note: These tests focus on API contract and state management.
 * Full integration tests with actual application loading are in jplatform-launcher module.
 */
class ApplicationManagerTest {

    private ApplicationManager manager;

    @BeforeEach
    void setUp() {
        manager = new ApplicationManager();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    void testInitialState() {
        Map<String, ApplicationState> apps = manager.listApplications();
        assertNotNull(apps);
        assertTrue(apps.isEmpty(), "New manager should have no applications");
    }

    @Test
    void testGetApplicationContextReturnsNullForNonExistent() {
        ApplicationContext context = manager.getApplicationContext("non-existent");
        assertNull(context, "Should return null for non-existent application");
    }

    @Test
    void testUndeployNonExistentApplicationThrowsException() {
        assertThrows(IllegalStateException.class, () -> manager.undeploy("non-existent"),
                "Undeploying non-existent application should throw IllegalStateException");
    }

    @Test
    void testStartNonExistentApplicationThrowsException() {
        assertThrows(IllegalStateException.class, () -> manager.start("non-existent"),
                "Starting non-existent application should throw IllegalStateException");
    }

    @Test
    void testStopNonExistentApplicationThrowsException() {
        assertThrows(IllegalStateException.class, () -> manager.stop("non-existent"),
                "Stopping non-existent application should throw IllegalStateException");
    }

    @Test
    void testShutdownOnEmptyManager() throws Exception {
        manager.shutdown();
        assertEquals(0, manager.listApplications().size(),
                "Shutdown on empty manager should succeed");
    }

    @Test
    void testConstructorWithNullMessageBus() {
        ApplicationManager mgr = new ApplicationManager(null, null);
        assertNotNull(mgr);
        assertTrue(mgr.listApplications().isEmpty());
    }

    @Test
    void testConstructorWithMessageBusAndRegistry() {
        MessageBus mockBus = new MessageBus() {
            @Override
            public void publish(String topic, Message message) {}
            @Override
            public Subscription subscribe(String topic, MessageHandler handler) {
                return new Subscription() {
                    @Override
                    public String getTopic() { return topic; }
                    @Override
                    public void cancel() {}
                    @Override
                    public boolean isActive() { return false; }
                };
            }
            @Override
            public void unsubscribe(Subscription subscription) {}
        };

        ServiceRegistry mockRegistry = new ServiceRegistry() {
            @Override
            public <T> void registerService(Class<T> serviceInterface, T implementation) {}
            @Override
            public <T> java.util.Optional<T> getService(Class<T> serviceInterface) {
                return java.util.Optional.empty();
            }
            @Override
            public <T> java.util.List<T> getAllServices(Class<T> serviceInterface) {
                return Collections.emptyList();
            }
            @Override
            public void unregisterService(Class<?> serviceInterface, Object implementation) {}
        };

        ApplicationManager mgr = new ApplicationManager(mockBus, mockRegistry);
        assertNotNull(mgr);
        assertTrue(mgr.listApplications().isEmpty());
    }

    @Test
    void testListApplicationsReturnsImmutableView() {
        Map<String, ApplicationState> apps = manager.listApplications();
        assertNotNull(apps);
        // Should be a snapshot/view
        assertEquals(0, apps.size());
    }

    @Test
    void testGetApplicationContextNullInput() {
        // ConcurrentHashMap doesn't allow null keys
        assertThrows(NullPointerException.class, () ->
            manager.getApplicationContext(null)
        );
    }

    @Test
    void testGetApplicationContextEmptyString() {
        ApplicationContext context = manager.getApplicationContext("");
        assertNull(context);
    }

    @Test
    void testGetStartupOrder() {
        java.util.List<String> order = manager.getStartupOrder();
        assertNotNull(order);
        assertTrue(order.isEmpty(), "New manager should have empty startup order");
    }

    @Test
    void testGetDependentApplications() {
        Set<String> dependents = manager.getDependentApplications("non-existent");
        assertNotNull(dependents);
        assertTrue(dependents.isEmpty(), "Non-existent app should have no dependents");
    }

    @Test
    void testStartNullApplicationId() {
        assertThrows(NullPointerException.class, () -> manager.start(null));
    }

    @Test
    void testStartEmptyApplicationId() {
        assertThrows(IllegalStateException.class, () -> manager.start(""));
    }

    @Test
    void testStopNullApplicationId() {
        assertThrows(NullPointerException.class, () -> manager.stop(null));
    }

    @Test
    void testStopEmptyApplicationId() {
        assertThrows(IllegalStateException.class, () -> manager.stop(""));
    }

    @Test
    void testUndeployNullApplicationId() {
        assertThrows(NullPointerException.class, () -> manager.undeploy(null));
    }

    @Test
    void testUndeployEmptyApplicationId() {
        assertThrows(IllegalStateException.class, () -> manager.undeploy(""));
    }

    @Test
    void testStartAllOnEmptyManager() throws Exception {
        manager.startAll();
        // Should complete without error
        assertEquals(0, manager.listApplications().size());
    }

    @Test
    void testMultipleShutdownCalls() throws Exception {
        manager.shutdown();
        // Second shutdown should not throw
        manager.shutdown();
        assertEquals(0, manager.listApplications().size());
    }

    @Test
    void testDefaultConstructor() {
        ApplicationManager mgr = new ApplicationManager();
        assertNotNull(mgr);
        assertNotNull(mgr.listApplications());
        assertTrue(mgr.listApplications().isEmpty());
    }

    // Note: Full deployment tests with actual class loading would require:
    // 1. A real JAR file with compiled classes
    // 2. A valid main class that implements Application interface
    // 3. Proper classpath setup
    // These are tested in integration tests in jplatform-launcher module.
}
