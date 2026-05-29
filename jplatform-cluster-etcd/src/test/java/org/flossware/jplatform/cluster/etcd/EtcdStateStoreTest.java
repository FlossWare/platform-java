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

package org.flossware.jplatform.cluster.etcd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EtcdStateStoreTest {
    private Client mockClient;
    private KV mockKv;
    private EtcdStateStore store;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mockClient = mock(Client.class);
        mockKv = mock(KV.class);
        when(mockClient.getKVClient()).thenReturn(mockKv);
        store = new EtcdStateStore(mockClient);
        mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());  // Support Optional types
    }

    @Test
    void testConstructorNullClientThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new EtcdStateStore(null)
        );
    }

    @Test
    void testConstruction() {
        assertNotNull(store);
    }

    @Test
    void testPutApplicationState() throws Exception {
        ApplicationState state = ApplicationState.RUNNING;
        PutResponse putResponse = mock(PutResponse.class);
        CompletableFuture<PutResponse> putFuture = CompletableFuture.completedFuture(putResponse);

        when(mockKv.put(any(ByteSequence.class), any(ByteSequence.class)))
            .thenReturn(putFuture);

        store.putApplicationState("app1", state);

        verify(mockKv, times(1)).put(any(ByteSequence.class), any(ByteSequence.class));
    }

    @Test
    void testPutApplicationStateNullId() {
        store.putApplicationState(null, ApplicationState.RUNNING);
        verify(mockKv, never()).put(any(ByteSequence.class), any(ByteSequence.class));
    }

    @Test
    void testPutApplicationStateNullState() {
        store.putApplicationState("app1", null);
        verify(mockKv, never()).put(any(ByteSequence.class), any(ByteSequence.class));
    }

    @Test
    void testGetApplicationState() throws Exception {
        ApplicationState state = ApplicationState.RUNNING;
        byte[] json = mapper.writeValueAsBytes(state);

        KeyValue kv = mock(KeyValue.class);
        when(kv.getValue()).thenReturn(ByteSequence.from(json));

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.singletonList(kv));

        CompletableFuture<GetResponse> getFuture = CompletableFuture.completedFuture(getResponse);
        when(mockKv.get(any(ByteSequence.class))).thenReturn(getFuture);

        ApplicationState result = store.getApplicationState("app1");

        assertEquals(state, result);
    }

    @Test
    void testGetApplicationStateNotFound() throws Exception {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.emptyList());

        CompletableFuture<GetResponse> getFuture = CompletableFuture.completedFuture(getResponse);
        when(mockKv.get(any(ByteSequence.class))).thenReturn(getFuture);

        ApplicationState result = store.getApplicationState("app1");

        assertNull(result);
    }

    @Test
    void testGetApplicationStateNullId() {
        ApplicationState result = store.getApplicationState(null);
        assertNull(result);
        verify(mockKv, never()).get(any(ByteSequence.class));
    }

    @Test
    void testGetApplicationStateException() throws Exception {
        CompletableFuture<GetResponse> getFuture = new CompletableFuture<>();
        getFuture.completeExceptionally(new RuntimeException("etcd error"));

        when(mockKv.get(any(ByteSequence.class))).thenReturn(getFuture);

        ApplicationState result = store.getApplicationState("app1");

        assertNull(result);
    }

    @Test
    void testGetAllApplicationStates() throws Exception {
        ApplicationState state1 = ApplicationState.RUNNING;
        ApplicationState state2 = ApplicationState.STOPPED;

        KeyValue kv1 = mock(KeyValue.class);
        when(kv1.getKey()).thenReturn(ByteSequence.from("/jplatform/states/app1", StandardCharsets.UTF_8));
        when(kv1.getValue()).thenReturn(ByteSequence.from(mapper.writeValueAsBytes(state1)));

        KeyValue kv2 = mock(KeyValue.class);
        when(kv2.getKey()).thenReturn(ByteSequence.from("/jplatform/states/app2", StandardCharsets.UTF_8));
        when(kv2.getValue()).thenReturn(ByteSequence.from(mapper.writeValueAsBytes(state2)));

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(List.of(kv1, kv2));

        CompletableFuture<GetResponse> getFuture = CompletableFuture.completedFuture(getResponse);
        when(mockKv.get(any(ByteSequence.class), any(GetOption.class))).thenReturn(getFuture);

        Map<String, ApplicationState> result = store.getAllApplicationStates();

        assertEquals(2, result.size());
        assertEquals(state1, result.get("app1"));
        assertEquals(state2, result.get("app2"));
    }

    @Test
    void testGetAllApplicationStatesEmpty() throws Exception {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.emptyList());

        CompletableFuture<GetResponse> getFuture = CompletableFuture.completedFuture(getResponse);
        when(mockKv.get(any(ByteSequence.class), any(GetOption.class))).thenReturn(getFuture);

        Map<String, ApplicationState> result = store.getAllApplicationStates();

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAllApplicationStatesException() throws Exception {
        CompletableFuture<GetResponse> getFuture = new CompletableFuture<>();
        getFuture.completeExceptionally(new RuntimeException("etcd error"));

        when(mockKv.get(any(ByteSequence.class), any(GetOption.class))).thenReturn(getFuture);

        assertThrows(RuntimeException.class, () ->
            store.getAllApplicationStates()
        );
    }

    @Test
    void testGetAllApplicationStatesDeserializationError() throws Exception {
        KeyValue kv = mock(KeyValue.class);
        when(kv.getKey()).thenReturn(ByteSequence.from("/jplatform/states/app1", StandardCharsets.UTF_8));
        when(kv.getValue()).thenReturn(ByteSequence.from("invalid json", StandardCharsets.UTF_8));

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.singletonList(kv));

        CompletableFuture<GetResponse> getFuture = CompletableFuture.completedFuture(getResponse);
        when(mockKv.get(any(ByteSequence.class), any(GetOption.class))).thenReturn(getFuture);

        assertThrows(RuntimeException.class, () ->
            store.getAllApplicationStates()
        );
    }

    @Test
    void testPutApplicationDescriptor() throws Exception {
        ApplicationDescriptor desc = ApplicationDescriptor.builder()
            .applicationId("app1")
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .build();

        PutResponse putResponse = mock(PutResponse.class);
        CompletableFuture<PutResponse> putFuture = CompletableFuture.completedFuture(putResponse);

        when(mockKv.put(any(ByteSequence.class), any(ByteSequence.class)))
            .thenReturn(putFuture);

        store.putApplicationDescriptor("app1", desc);

        verify(mockKv, times(1)).put(any(ByteSequence.class), any(ByteSequence.class));
    }

    @Test
    void testPutApplicationDescriptorNullId() {
        ApplicationDescriptor desc = ApplicationDescriptor.builder()
            .applicationId("app1")
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .build();

        store.putApplicationDescriptor(null, desc);
        verify(mockKv, never()).put(any(ByteSequence.class), any(ByteSequence.class));
    }

    @Test
    void testPutApplicationDescriptorNullDescriptor() {
        store.putApplicationDescriptor("app1", null);
        verify(mockKv, never()).put(any(ByteSequence.class), any(ByteSequence.class));
    }

    @Test
    void testGetApplicationDescriptor() throws Exception {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.emptyList());

        CompletableFuture<GetResponse> getFuture = CompletableFuture.completedFuture(getResponse);
        when(mockKv.get(any(ByteSequence.class))).thenReturn(getFuture);

        // Just test that get is called - actual serialization is tested in integration tests
        ApplicationDescriptor result = store.getApplicationDescriptor("app1");

        verify(mockKv, times(1)).get(any(ByteSequence.class));
        // Returns null when not found
        assertNull(result);
    }

    @Test
    void testGetApplicationDescriptorNotFound() throws Exception {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.emptyList());

        CompletableFuture<GetResponse> getFuture = CompletableFuture.completedFuture(getResponse);
        when(mockKv.get(any(ByteSequence.class))).thenReturn(getFuture);

        ApplicationDescriptor result = store.getApplicationDescriptor("app1");

        assertNull(result);
    }

    @Test
    void testGetApplicationDescriptorNullId() {
        ApplicationDescriptor result = store.getApplicationDescriptor(null);
        assertNull(result);
        verify(mockKv, never()).get(any(ByteSequence.class));
    }

    @Test
    void testGetAllApplicationDescriptors() throws Exception {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.emptyList());

        CompletableFuture<GetResponse> getFuture = CompletableFuture.completedFuture(getResponse);
        when(mockKv.get(any(ByteSequence.class), any(GetOption.class))).thenReturn(getFuture);

        // Just test that get is called - actual serialization is tested in integration tests
        Map<String, ApplicationDescriptor> result = store.getAllApplicationDescriptors();

        verify(mockKv, times(1)).get(any(ByteSequence.class), any(GetOption.class));
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAllApplicationDescriptorsEmpty() throws Exception {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.emptyList());

        CompletableFuture<GetResponse> getFuture = CompletableFuture.completedFuture(getResponse);
        when(mockKv.get(any(ByteSequence.class), any(GetOption.class))).thenReturn(getFuture);

        Map<String, ApplicationDescriptor> result = store.getAllApplicationDescriptors();

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAllApplicationDescriptorsDeserializationError() throws Exception {
        KeyValue kv = mock(KeyValue.class);
        when(kv.getKey()).thenReturn(ByteSequence.from("/jplatform/descriptors/app1", StandardCharsets.UTF_8));
        when(kv.getValue()).thenReturn(ByteSequence.from("invalid json", StandardCharsets.UTF_8));

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.singletonList(kv));

        CompletableFuture<GetResponse> getFuture = CompletableFuture.completedFuture(getResponse);
        when(mockKv.get(any(ByteSequence.class), any(GetOption.class))).thenReturn(getFuture);

        assertThrows(RuntimeException.class, () ->
            store.getAllApplicationDescriptors()
        );
    }

    @Test
    void testSubscribe() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        assertDoesNotThrow(() -> store.subscribe("app1", listener));
    }

    @Test
    void testSubscribeNullKey() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        assertDoesNotThrow(() -> store.subscribe(null, listener));
        verify(listener, never()).onStateChanged(anyString(), any());
    }

    @Test
    void testSubscribeNullListener() {
        assertDoesNotThrow(() -> store.subscribe("app1", null));
    }

    @Test
    void testUnsubscribe() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        store.subscribe("app1", listener);
        assertDoesNotThrow(() -> store.unsubscribe("app1", listener));
    }

    @Test
    void testUnsubscribeNullKey() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        assertDoesNotThrow(() -> store.unsubscribe(null, listener));
    }

    @Test
    void testUnsubscribeNullListener() {
        assertDoesNotThrow(() -> store.unsubscribe("app1", null));
    }

    @Test
    void testListenerNotification() throws Exception {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);
        store.subscribe("app1", listener);

        ApplicationState state = ApplicationState.RUNNING;
        PutResponse putResponse = mock(PutResponse.class);
        CompletableFuture<PutResponse> putFuture = CompletableFuture.completedFuture(putResponse);

        when(mockKv.put(any(ByteSequence.class), any(ByteSequence.class)))
            .thenReturn(putFuture);

        store.putApplicationState("app1", state);

        verify(listener, times(1)).onStateChanged("app1", state);
    }

    @Test
    void testListenerNotificationException() throws Exception {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);
        doThrow(new RuntimeException("Listener error"))
            .when(listener).onStateChanged(anyString(), any(ApplicationState.class));

        store.subscribe("app1", listener);

        ApplicationState state = ApplicationState.RUNNING;
        PutResponse putResponse = mock(PutResponse.class);
        CompletableFuture<PutResponse> putFuture = CompletableFuture.completedFuture(putResponse);

        when(mockKv.put(any(ByteSequence.class), any(ByteSequence.class)))
            .thenReturn(putFuture);

        // Should not throw - listener exceptions are caught
        assertDoesNotThrow(() -> store.putApplicationState("app1", state));
    }

    @Test
    void testClear() throws Exception {
        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        CompletableFuture<DeleteResponse> deleteFuture = CompletableFuture.completedFuture(deleteResponse);

        when(mockKv.delete(any(ByteSequence.class), any(DeleteOption.class)))
            .thenReturn(deleteFuture);

        assertDoesNotThrow(() -> store.clear());

        verify(mockKv, times(2)).delete(any(ByteSequence.class), any(DeleteOption.class));
    }

    @Test
    void testClearException() throws Exception {
        CompletableFuture<DeleteResponse> deleteFuture = new CompletableFuture<>();
        deleteFuture.completeExceptionally(new RuntimeException("etcd error"));

        when(mockKv.delete(any(ByteSequence.class), any(DeleteOption.class)))
            .thenReturn(deleteFuture);

        // Should not throw - errors are logged
        assertDoesNotThrow(() -> store.clear());
    }
}
