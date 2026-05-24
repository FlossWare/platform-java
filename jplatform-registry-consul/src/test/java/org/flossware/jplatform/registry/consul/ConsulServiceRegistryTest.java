package org.flossware.jplatform.registry.consul;

import com.orbitz.consul.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsulServiceRegistryTest {

    private Consul mockConsul;
    private AgentClient mockAgentClient;
    private KeyValueClient mockKvClient;
    private ConsulRegistryConfig config;
    private ConsulServiceRegistry registry;

    interface TestService {
        String execute();
    }

    static class TestServiceImpl implements TestService {
        @Override
        public String execute() {
            return "test";
        }
    }

    @BeforeEach
    void setUp() {
        mockConsul = mock(Consul.class);
        mockAgentClient = mock(AgentClient.class);
        mockKvClient = mock(KeyValueClient.class);

        when(mockConsul.agentClient()).thenReturn(mockAgentClient);
        when(mockConsul.keyValueClient()).thenReturn(mockKvClient);

        config = ConsulRegistryConfig.builder()
            .consulHost("localhost")
            .consulPort(8500)
            .nodeId("test-node")
            .build();

        registry = new ConsulServiceRegistry(config, mockConsul);
    }

    @Test
    void testRegisterService() {
        TestService impl = new TestServiceImpl();

        registry.registerService(TestService.class, impl);

        verify(mockAgentClient).register(any());
        verify(mockKvClient).putValue(anyString(), anyString());
    }

    @Test
    void testRegisterService_NullInterface() {
        assertThrows(NullPointerException.class, () ->
            registry.registerService(null, new TestServiceImpl())
        );
    }

    @Test
    void testRegisterService_NullImplementation() {
        assertThrows(NullPointerException.class, () ->
            registry.registerService(TestService.class, null)
        );
    }

    @Test
    void testRegisterService_NotAnInterface() {
        assertThrows(IllegalArgumentException.class, () ->
            registry.registerService(String.class, "test")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRegisterService_DoesNotImplementInterface() {
        assertThrows(IllegalArgumentException.class, () -> {
            @SuppressWarnings("rawtypes")
            Class serviceClass = Runnable.class;
            registry.registerService(serviceClass, new TestServiceImpl());
        });
    }

    @Test
    void testGetService() {
        TestService impl = new TestServiceImpl();
        registry.registerService(TestService.class, impl);

        Optional<TestService> result = registry.getService(TestService.class);

        assertTrue(result.isPresent());
        assertSame(impl, result.get());
    }

    @Test
    void testGetService_NotFound() {
        Optional<TestService> result = registry.getService(TestService.class);

        assertFalse(result.isPresent());
    }

    @Test
    void testGetAllServices() {
        TestService impl1 = new TestServiceImpl();
        TestService impl2 = new TestServiceImpl();

        registry.registerService(TestService.class, impl1);
        registry.registerService(TestService.class, impl2);

        List<TestService> services = registry.getAllServices(TestService.class);

        assertEquals(2, services.size());
        assertTrue(services.contains(impl1));
        assertTrue(services.contains(impl2));
    }

    @Test
    void testGetAllServices_Empty() {
        List<TestService> services = registry.getAllServices(TestService.class);

        assertTrue(services.isEmpty());
    }

    @Test
    void testUnregisterService() {
        TestService impl = new TestServiceImpl();
        registry.registerService(TestService.class, impl);

        registry.unregisterService(TestService.class, impl);

        Optional<TestService> result = registry.getService(TestService.class);
        assertFalse(result.isPresent());
    }

    @Test
    void testClose() throws Exception {
        TestService impl = new TestServiceImpl();
        registry.registerService(TestService.class, impl);

        registry.close();

        verify(mockAgentClient, atLeastOnce()).deregister(anyString());
        assertEquals(0, registry.getServiceCount());
    }

    @Test
    void testGetServiceCount() {
        assertEquals(0, registry.getServiceCount());

        registry.registerService(TestService.class, new TestServiceImpl());
        assertEquals(1, registry.getServiceCount());
    }

    @Test
    void testGetConsulClient() {
        assertSame(mockConsul, registry.getConsulClient());
    }

    @Test
    void testGetConfig() {
        assertSame(config, registry.getConfig());
    }
}
