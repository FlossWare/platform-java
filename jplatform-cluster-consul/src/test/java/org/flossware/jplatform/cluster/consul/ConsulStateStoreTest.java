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

import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.model.kv.ImmutableValue;
import com.orbitz.consul.model.kv.Value;
import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConsulStateStore.
 */
class ConsulStateStoreTest {

    private Consul mockConsul;
    private KeyValueClient mockKvClient;
    private ConsulStateStore stateStore;

    @BeforeEach
    void setUp() {
        mockConsul = mock(Consul.class);
        mockKvClient = mock(KeyValueClient.class);
        when(mockConsul.keyValueClient()).thenReturn(mockKvClient);

        stateStore = new ConsulStateStore(mockConsul);
    }

    @Test
    void testConstructor() {
        assertNotNull(stateStore);
        assertSame(mockConsul, stateStore.getConsulClient());
    }

    @Test
    void testPutApplicationState() {
        stateStore.putApplicationState("app1", ApplicationState.RUNNING);

        verify(mockKvClient).putValue("jplatform/state/app1", "RUNNING");
    }

    @Test
    void testGetApplicationState_Found() {
        String base64Value = Base64.getEncoder().encodeToString("RUNNING".getBytes());
        Value mockValue = ImmutableValue.builder()
            .createIndex(1L)
            .modifyIndex(1L)
            .lockIndex(0L)
            .key("jplatform/state/app1")
            .flags(0L)
            .value(base64Value)
            .build();

        when(mockKvClient.getValue("jplatform/state/app1")).thenReturn(Optional.of(mockValue));

        ApplicationState state = stateStore.getApplicationState("app1");

        assertEquals(ApplicationState.RUNNING, state);
    }

    @Test
    void testGetApplicationState_NotFound() {
        when(mockKvClient.getValue("jplatform/state/app1")).thenReturn(Optional.empty());

        ApplicationState state = stateStore.getApplicationState("app1");

        assertNull(state);
    }

    @Test
    void testGetApplicationState_InvalidValue() {
        String base64Value = Base64.getEncoder().encodeToString("INVALID_STATE".getBytes());
        Value mockValue = ImmutableValue.builder()
            .createIndex(1L)
            .modifyIndex(1L)
            .lockIndex(0L)
            .key("jplatform/state/app1")
            .flags(0L)
            .value(base64Value)
            .build();

        when(mockKvClient.getValue("jplatform/state/app1")).thenReturn(Optional.of(mockValue));

        ApplicationState state = stateStore.getApplicationState("app1");

        assertNull(state);
    }

    @Test
    void testGetAllApplicationStates() {
        String base64Value1 = Base64.getEncoder().encodeToString("RUNNING".getBytes());
        String base64Value2 = Base64.getEncoder().encodeToString("STOPPED".getBytes());

        Value value1 = ImmutableValue.builder()
            .createIndex(1L)
            .modifyIndex(1L)
            .lockIndex(0L)
            .key("jplatform/state/app1")
            .flags(0L)
            .value(base64Value1)
            .build();

        Value value2 = ImmutableValue.builder()
            .createIndex(1L)
            .modifyIndex(1L)
            .lockIndex(0L)
            .key("jplatform/state/app2")
            .flags(0L)
            .value(base64Value2)
            .build();

        when(mockKvClient.getKeys("jplatform/state/"))
            .thenReturn(Arrays.asList("jplatform/state/app1", "jplatform/state/app2"));
        when(mockKvClient.getValue("jplatform/state/app1")).thenReturn(Optional.of(value1));
        when(mockKvClient.getValue("jplatform/state/app2")).thenReturn(Optional.of(value2));

        Map<String, ApplicationState> states = stateStore.getAllApplicationStates();

        assertEquals(2, states.size());
        assertEquals(ApplicationState.RUNNING, states.get("app1"));
        assertEquals(ApplicationState.STOPPED, states.get("app2"));
    }

    @Test
    void testPutApplicationDescriptor() throws Exception {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("app1")
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .build();

        stateStore.putApplicationDescriptor("app1", descriptor);

        verify(mockKvClient).putValue(eq("jplatform/descriptor/app1"), anyString());
    }

    @Test
    void testGetApplicationDescriptor_Found() {
        String json = "{\"applicationId\":\"app1\",\"name\":\"Test App\",\"version\":\"1.0\",\"mainClass\":\"com.example.Main\"}";
        String base64Value = Base64.getEncoder().encodeToString(json.getBytes());
        Value mockValue = ImmutableValue.builder()
            .createIndex(1L)
            .modifyIndex(1L)
            .lockIndex(0L)
            .key("jplatform/descriptor/app1")
            .flags(0L)
            .value(base64Value)
            .build();

        when(mockKvClient.getValue("jplatform/descriptor/app1")).thenReturn(Optional.of(mockValue));

        ApplicationDescriptor descriptor = stateStore.getApplicationDescriptor("app1");

        assertNotNull(descriptor);
        assertEquals("app1", descriptor.getApplicationId());
        assertEquals("Test App", descriptor.getName());
    }

    @Test
    void testGetApplicationDescriptor_NotFound() {
        when(mockKvClient.getValue("jplatform/descriptor/app1")).thenReturn(Optional.empty());

        ApplicationDescriptor descriptor = stateStore.getApplicationDescriptor("app1");

        assertNull(descriptor);
    }

    @Test
    void testGetApplicationDescriptor_InvalidJson() {
        String base64Value = Base64.getEncoder().encodeToString("{invalid json".getBytes());
        Value mockValue = ImmutableValue.builder()
            .createIndex(1L)
            .modifyIndex(1L)
            .lockIndex(0L)
            .key("jplatform/descriptor/app1")
            .flags(0L)
            .value(base64Value)
            .build();

        when(mockKvClient.getValue("jplatform/descriptor/app1")).thenReturn(Optional.of(mockValue));

        ApplicationDescriptor descriptor = stateStore.getApplicationDescriptor("app1");

        assertNull(descriptor);
    }

    @Test
    void testGetAllApplicationDescriptors() {
        String json1 = "{\"applicationId\":\"app1\",\"name\":\"App 1\",\"version\":\"1.0\",\"mainClass\":\"com.example.Main1\"}";
        String json2 = "{\"applicationId\":\"app2\",\"name\":\"App 2\",\"version\":\"2.0\",\"mainClass\":\"com.example.Main2\"}";
        String base64Value1 = Base64.getEncoder().encodeToString(json1.getBytes());
        String base64Value2 = Base64.getEncoder().encodeToString(json2.getBytes());

        Value value1 = ImmutableValue.builder()
            .createIndex(1L)
            .modifyIndex(1L)
            .lockIndex(0L)
            .key("jplatform/descriptor/app1")
            .flags(0L)
            .value(base64Value1)
            .build();

        Value value2 = ImmutableValue.builder()
            .createIndex(1L)
            .modifyIndex(1L)
            .lockIndex(0L)
            .key("jplatform/descriptor/app2")
            .flags(0L)
            .value(base64Value2)
            .build();

        when(mockKvClient.getKeys("jplatform/descriptor/"))
            .thenReturn(Arrays.asList("jplatform/descriptor/app1", "jplatform/descriptor/app2"));
        when(mockKvClient.getValue("jplatform/descriptor/app1")).thenReturn(Optional.of(value1));
        when(mockKvClient.getValue("jplatform/descriptor/app2")).thenReturn(Optional.of(value2));

        Map<String, ApplicationDescriptor> descriptors = stateStore.getAllApplicationDescriptors();

        assertEquals(2, descriptors.size());
        assertEquals("App 1", descriptors.get("app1").getName());
        assertEquals("App 2", descriptors.get("app2").getName());
    }

    @Test
    void testSubscribe() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        stateStore.subscribe("app1", listener);
        stateStore.subscribe("app1", null); // Should not throw
    }

    @Test
    void testUnsubscribe() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        stateStore.subscribe("app1", listener);
        stateStore.unsubscribe("app1", listener);
        stateStore.unsubscribe("app1", null); // Should not throw
    }

    @Test
    void testSubscribeNotification() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        stateStore.subscribe("app1", listener);
        stateStore.putApplicationState("app1", ApplicationState.RUNNING);

        verify(listener).onStateChanged("app1", ApplicationState.RUNNING);
    }

    @Test
    void testMultipleSubscribers() {
        ClusterStateStore.StateChangeListener listener1 = mock(ClusterStateStore.StateChangeListener.class);
        ClusterStateStore.StateChangeListener listener2 = mock(ClusterStateStore.StateChangeListener.class);

        stateStore.subscribe("app1", listener1);
        stateStore.subscribe("app1", listener2);
        stateStore.putApplicationState("app1", ApplicationState.RUNNING);

        verify(listener1).onStateChanged("app1", ApplicationState.RUNNING);
        verify(listener2).onStateChanged("app1", ApplicationState.RUNNING);
    }

    @Test
    void testUnsubscribeRemovesListener() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        stateStore.subscribe("app1", listener);
        stateStore.unsubscribe("app1", listener);
        stateStore.putApplicationState("app1", ApplicationState.RUNNING);

        verify(listener, never()).onStateChanged(anyString(), any());
    }

    @Test
    void testListenerExceptionHandling() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);
        doThrow(new RuntimeException("Test exception")).when(listener).onStateChanged(anyString(), any());

        stateStore.subscribe("app1", listener);

        // Should not throw, exception is caught and logged
        assertDoesNotThrow(() -> stateStore.putApplicationState("app1", ApplicationState.RUNNING));
    }
}
