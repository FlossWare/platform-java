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

package org.flossware.jplatform.registry.etcd;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class EtcdServiceRegistryTest {
    private EtcdRegistryConfig config;
    private Client mockClient;
    private Lease mockLease;
    private KV mockKV;
    private EtcdServiceRegistry registry;

    interface TestService {
        void doSomething();
    }

    static class TestServiceImpl implements TestService {
        @Override
        public void doSomething() {}
    }

    @BeforeEach
    void setUp() {
        config = EtcdRegistryConfig.builder().build();
        mockClient = mock(Client.class);
        mockLease = mock(Lease.class);
        mockKV = mock(KV.class);

        // Set up mock responses for etcd operations
        when(mockClient.getLeaseClient()).thenReturn(mockLease);
        when(mockClient.getKVClient()).thenReturn(mockKV);

        // Mock lease grant response
        LeaseGrantResponse leaseGrantResponse = mock(LeaseGrantResponse.class);
        when(leaseGrantResponse.getID()).thenReturn(123456L);
        CompletableFuture<LeaseGrantResponse> leaseFuture = CompletableFuture.completedFuture(leaseGrantResponse);
        when(mockLease.grant(anyLong())).thenReturn(leaseFuture);

        // Mock KV put response
        PutResponse putResponse = mock(PutResponse.class);
        CompletableFuture<PutResponse> putFuture = CompletableFuture.completedFuture(putResponse);
        when(mockKV.put(any(), any(), any())).thenReturn(putFuture);

        registry = new EtcdServiceRegistry(config, mockClient);
    }

    @Test
    void testConstruction() {
        assertNotNull(registry);
    }

    @Test
    void testConstructionWithConfig() {
        EtcdServiceRegistry reg = new EtcdServiceRegistry(config);
        assertNotNull(reg);
    }

    @Test
    void testRegisterService() {
        TestService impl = new TestServiceImpl();

        assertDoesNotThrow(() -> registry.registerService(TestService.class, impl));
    }

    @Test
    void testRegisterServiceNullInterface() {
        TestService impl = new TestServiceImpl();

        assertThrows(IllegalArgumentException.class, () ->
            registry.registerService(null, impl));
    }

    @Test
    void testRegisterServiceNullImplementation() {
        assertThrows(IllegalArgumentException.class, () ->
            registry.registerService(TestService.class, null));
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
    void testGetServiceNotRegistered() {
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
    void testGetAllServicesEmpty() {
        List<TestService> services = registry.getAllServices(TestService.class);

        assertTrue(services.isEmpty());
    }

    @Test
    void testUnregisterService() {
        TestService impl = new TestServiceImpl();
        registry.registerService(TestService.class, impl);

        registry.unregisterService(TestService.class, impl);

        assertFalse(registry.getService(TestService.class).isPresent());
    }

    @Test
    void testUnregisterServiceNotRegistered() {
        TestService impl = new TestServiceImpl();

        assertDoesNotThrow(() -> registry.unregisterService(TestService.class, impl));
    }

    @Test
    void testUnregisterServiceNull() {
        assertDoesNotThrow(() -> registry.unregisterService(null, null));
    }

    @Test
    void testClose() {
        assertDoesNotThrow(() -> registry.close());
    }

    @Test
    void testGetClient() {
        assertSame(mockClient, registry.getClient());
    }

    @Test
    void testStartIdempotent() {
        // Create registry without client (will need to start)
        EtcdServiceRegistry reg = new EtcdServiceRegistry(config);

        // First start should succeed
        assertDoesNotThrow(() -> reg.start());

        // Second start should be idempotent (no error)
        assertDoesNotThrow(() -> reg.start());

        reg.close();
    }

    @Test
    void testStartWithLargeLeaseTtl() {
        // Create config with large lease TTL (> 3600 seconds)
        EtcdRegistryConfig largeTtlConfig = EtcdRegistryConfig.builder()
            .leaseTtl(7200)  // 2 hours
            .build();

        EtcdServiceRegistry reg = new EtcdServiceRegistry(largeTtlConfig);

        // Start should log a warning but not fail
        assertDoesNotThrow(() -> reg.start());

        reg.close();
    }

    @Test
    void testUnregisterWithEtcdCleanup() throws Exception {
        // Mock lease revoke and delete operations
        io.etcd.jetcd.kv.DeleteResponse deleteResponse = mock(io.etcd.jetcd.kv.DeleteResponse.class);
        CompletableFuture<io.etcd.jetcd.kv.DeleteResponse> deleteFuture =
            CompletableFuture.completedFuture(deleteResponse);
        when(mockKV.delete(any())).thenReturn(deleteFuture);

        io.etcd.jetcd.lease.LeaseRevokeResponse revokeResponse = mock(io.etcd.jetcd.lease.LeaseRevokeResponse.class);
        CompletableFuture<io.etcd.jetcd.lease.LeaseRevokeResponse> revokeFuture =
            CompletableFuture.completedFuture(revokeResponse);
        when(mockLease.revoke(anyLong())).thenReturn(revokeFuture);

        // Register and then unregister
        TestService impl = new TestServiceImpl();
        registry.registerService(TestService.class, impl);
        registry.unregisterService(TestService.class, impl);

        // Verify etcd cleanup was called
        verify(mockLease).revoke(anyLong());
        verify(mockKV).delete(any());
    }

    @Test
    void testCloseNotStarted() {
        // Create registry that was never started
        EtcdServiceRegistry reg = new EtcdServiceRegistry(config);

        // Close should not fail even if not started
        assertDoesNotThrow(() -> reg.close());
    }

    @Test
    void testRegisterDuplicateService() {
        TestService impl = new TestServiceImpl();

        // Register once
        registry.registerService(TestService.class, impl);

        // Register same instance again - should be idempotent (warning logged)
        assertDoesNotThrow(() -> registry.registerService(TestService.class, impl));

        // Should still have only one instance
        Optional<TestService> service = registry.getService(TestService.class);
        assertTrue(service.isPresent());
    }

    @Test
    void testGetServiceNullInterface() {
        // Attempting to get service with null interface should throw
        assertThrows(IllegalArgumentException.class, () ->
            registry.getService(null));
    }

    @Test
    void testGetAllServicesNullInterface() {
        // Attempting to get all services with null interface should throw
        assertThrows(IllegalArgumentException.class, () ->
            registry.getAllServices(null));
    }
}
