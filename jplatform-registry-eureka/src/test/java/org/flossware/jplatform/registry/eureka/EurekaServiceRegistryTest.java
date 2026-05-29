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

package org.flossware.jplatform.registry.eureka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EurekaServiceRegistryTest {

    @Mock
    private ScheduledExecutorService heartbeatExecutor;

    private EurekaRegistryConfig config;

    interface TestService {
        String getName();
    }

    static class TestServiceImpl implements TestService {
        @Override
        public String getName() {
            return "test";
        }
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        config = EurekaRegistryConfig.builder()
            .appName("test-app")
            .registerWithEureka(false)
            .fetchRegistry(false)
            .build();
    }

    @Test
    void testConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new EurekaServiceRegistry(null);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testRegisterServiceNullInterface() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.registerService(null, new TestServiceImpl());
        });
        assertTrue(exception.getMessage().contains("must not be null"));
    }

    @Test
    void testRegisterServiceNullImplementation() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            registry.registerService(TestService.class, null);
        });
        assertTrue(exception.getMessage().contains("must not be null"));
    }

    @Test
    void testRegisterAndGetService() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service = new TestServiceImpl();
        registry.registerService(TestService.class, service);

        Optional<TestService> retrieved = registry.getService(TestService.class);
        assertTrue(retrieved.isPresent());
        assertSame(service, retrieved.get());
    }

    @Test
    void testGetServiceNotRegistered() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        Optional<TestService> retrieved = registry.getService(TestService.class);
        assertFalse(retrieved.isPresent());
    }

    @Test
    void testRegisterMultipleImplementations() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service1 = new TestServiceImpl();
        TestService service2 = new TestServiceImpl();
        TestService service3 = new TestServiceImpl();

        registry.registerService(TestService.class, service1);
        registry.registerService(TestService.class, service2);
        registry.registerService(TestService.class, service3);

        List<TestService> services = registry.getAllServices(TestService.class);
        assertEquals(3, services.size());
        assertTrue(services.contains(service1));
        assertTrue(services.contains(service2));
        assertTrue(services.contains(service3));
    }

    @Test
    void testGetAllServicesEmpty() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        List<TestService> services = registry.getAllServices(TestService.class);
        assertTrue(services.isEmpty());
    }

    @Test
    void testUnregisterService() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service = new TestServiceImpl();
        registry.registerService(TestService.class, service);

        assertTrue(registry.getService(TestService.class).isPresent());

        registry.unregisterService(TestService.class, service);

        assertFalse(registry.getService(TestService.class).isPresent());
    }

    @Test
    void testUnregisterOneOfMany() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service1 = new TestServiceImpl();
        TestService service2 = new TestServiceImpl();

        registry.registerService(TestService.class, service1);
        registry.registerService(TestService.class, service2);

        registry.unregisterService(TestService.class, service1);

        List<TestService> services = registry.getAllServices(TestService.class);
        assertEquals(1, services.size());
        assertSame(service2, services.get(0));
    }

    @Test
    void testUnregisterNullSafe() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        assertDoesNotThrow(() -> {
            registry.unregisterService(null, null);
        });

        assertDoesNotThrow(() -> {
            registry.unregisterService(TestService.class, null);
        });

        assertDoesNotThrow(() -> {
            registry.unregisterService(null, new TestServiceImpl());
        });
    }

    @Test
    void testGetServiceReturnsFirstWhenMultiple() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service1 = new TestServiceImpl();
        TestService service2 = new TestServiceImpl();

        registry.registerService(TestService.class, service1);
        registry.registerService(TestService.class, service2);

        Optional<TestService> retrieved = registry.getService(TestService.class);
        assertTrue(retrieved.isPresent());
        assertSame(service1, retrieved.get());
    }

    @Test
    void testClose() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service = new TestServiceImpl();
        registry.registerService(TestService.class, service);

        registry.close();

        assertTrue(registry.getLocalServices().isEmpty());
    }

    @Test
    void testGetConfig() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        assertSame(config, registry.getConfig());
    }

    @Test
    void testConcurrentRegistration() throws InterruptedException {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    registry.registerService(TestService.class, new TestServiceImpl());
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        List<TestService> services = registry.getAllServices(TestService.class);
        assertEquals(100, services.size());
    }

    @Test
    void testMultipleServiceTypes() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        interface AnotherService {
            void doSomething();
        }

        class AnotherServiceImpl implements AnotherService {
            @Override
            public void doSomething() {}
        }

        TestService testService = new TestServiceImpl();
        AnotherService anotherService = new AnotherServiceImpl();

        registry.registerService(TestService.class, testService);
        registry.registerService(AnotherService.class, anotherService);

        assertTrue(registry.getService(TestService.class).isPresent());
        assertTrue(registry.getService(AnotherService.class).isPresent());

        assertEquals(1, registry.getAllServices(TestService.class).size());
        assertEquals(1, registry.getAllServices(AnotherService.class).size());
    }

    @Test
    void testSendHeartbeats() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        assertDoesNotThrow(() -> {
            registry.sendHeartbeats();
        });
    }

    @Test
    void testStartIdempotent() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config);

        registry.start();
        registry.start();

        assertDoesNotThrow(() -> registry.close());
    }

    @Test
    void testUnregisterNonexistentService() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        assertDoesNotThrow(() -> {
            registry.unregisterService(TestService.class, new TestServiceImpl());
        });
    }

    @Test
    void testGetAllServicesNullInterface() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        // Should throw NullPointerException when accessing ConcurrentHashMap with null key
        assertThrows(NullPointerException.class, () -> {
            registry.getAllServices(null);
        });
    }

    @Test
    void testGetServiceNullInterface() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        // Should throw NullPointerException when accessing ConcurrentHashMap with null key
        assertThrows(NullPointerException.class, () -> {
            registry.getService(null);
        });
    }

    @Test
    void testUnregisterWithNullInterface() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);
        TestService service = new TestServiceImpl();

        registry.registerService(TestService.class, service);

        // Unregister with null should be no-op
        assertDoesNotThrow(() -> registry.unregisterService(null, service));

        // Service should still be there
        assertTrue(registry.getService(TestService.class).isPresent());
    }

    @Test
    void testUnregisterWithNullImplementation() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);
        TestService service = new TestServiceImpl();

        registry.registerService(TestService.class, service);

        // Unregister with null implementation should be no-op
        assertDoesNotThrow(() -> registry.unregisterService(TestService.class, null));

        // Service should still be there
        assertTrue(registry.getService(TestService.class).isPresent());
    }

    @Test
    void testUnregisterRemovesOnlySpecifiedInstance() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service1 = new TestServiceImpl();
        TestService service2 = new TestServiceImpl();

        registry.registerService(TestService.class, service1);
        registry.registerService(TestService.class, service2);

        assertEquals(2, registry.getAllServices(TestService.class).size());

        registry.unregisterService(TestService.class, service1);

        List<TestService> services = registry.getAllServices(TestService.class);
        assertEquals(1, services.size());
        assertSame(service2, services.get(0));
    }

    @Test
    void testUnregisterLastInstanceCleansUpEntry() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service = new TestServiceImpl();
        registry.registerService(TestService.class, service);

        assertTrue(registry.getService(TestService.class).isPresent());

        registry.unregisterService(TestService.class, service);

        assertFalse(registry.getService(TestService.class).isPresent());
    }

    @Test
    void testCloseIdempotent() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        assertDoesNotThrow(() -> {
            registry.close();
            registry.close();
            registry.close();
        });
    }

    @Test
    void testCloseWithoutStart() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config);

        assertDoesNotThrow(() -> registry.close());
    }

    @Test
    void testStartWithRegistrationDisabled() {
        EurekaRegistryConfig configNoReg = EurekaRegistryConfig.builder()
            .appName("test-app")
            .registerWithEureka(false)
            .build();

        EurekaServiceRegistry registry = new EurekaServiceRegistry(configNoReg);

        assertDoesNotThrow(() -> {
            registry.start();
            registry.close();
        });
    }

    @Test
    void testGetLocalServicesMap() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service = new TestServiceImpl();
        registry.registerService(TestService.class, service);

        Map<Class<?>, List<Object>> localServices = registry.getLocalServices();
        assertNotNull(localServices);
        assertTrue(localServices.containsKey(TestService.class));
        assertEquals(1, localServices.get(TestService.class).size());
    }

    @Test
    void testSendHeartbeatsWithNoRegisteredInstances() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        // Should not throw even with no registered instances
        assertDoesNotThrow(() -> registry.sendHeartbeats());
    }

    @Test
    void testRegisterSameInstanceTwice() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service = new TestServiceImpl();
        registry.registerService(TestService.class, service);
        registry.registerService(TestService.class, service);

        // Should have both registrations (CopyOnWriteArrayList allows duplicates)
        assertEquals(2, registry.getAllServices(TestService.class).size());
    }

    @Test
    void testGetAllServicesReturnsCopy() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service = new TestServiceImpl();
        registry.registerService(TestService.class, service);

        List<TestService> services1 = registry.getAllServices(TestService.class);
        List<TestService> services2 = registry.getAllServices(TestService.class);

        assertNotSame(services1, services2);
        assertEquals(services1.size(), services2.size());
    }

    @Test
    void testCloseAfterServicesRegistered() {
        EurekaServiceRegistry registry = new EurekaServiceRegistry(config, heartbeatExecutor);

        TestService service = new TestServiceImpl();
        registry.registerService(TestService.class, service);

        assertTrue(registry.getService(TestService.class).isPresent());

        registry.close();

        // After close, services should be cleared
        assertFalse(registry.getService(TestService.class).isPresent());
    }
}
