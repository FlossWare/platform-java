package org.flossware.jplatform.registry.eureka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
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
}
