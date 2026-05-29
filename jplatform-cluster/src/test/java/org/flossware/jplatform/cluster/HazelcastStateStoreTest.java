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

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import org.flossware.jplatform.api.*;
import org.flossware.jplatform.api.ClusterStateStore.StateChangeListener;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for HazelcastStateStore.
 * Tests distributed state storage, descriptor serialization, and state change listeners.
 */
class HazelcastStateStoreTest {

    private HazelcastStateStore stateStore;
    private HazelcastInstance mockHazelcast;
    private IMap<String, ApplicationState> mockStateMap;
    private IMap<String, String> mockDescriptorMap;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockHazelcast = mock(HazelcastInstance.class);
        mockStateMap = (IMap<String, ApplicationState>) mock(IMap.class);
        mockDescriptorMap = (IMap<String, String>) mock(IMap.class);

        when(mockHazelcast.<String, ApplicationState>getMap("jplatform-application-states")).thenReturn(mockStateMap);
        when(mockHazelcast.<String, String>getMap("jplatform-application-descriptors")).thenReturn(mockDescriptorMap);

        stateStore = new HazelcastStateStore(mockHazelcast);
    }

    @Test
    @DisplayName("Should store application state successfully")
    void testPutApplicationState() {
        // Given
        String appId = "test-app";
        ApplicationState state = ApplicationState.RUNNING;

        // When
        stateStore.putApplicationState(appId, state);

        // Then
        verify(mockStateMap).put(appId, state);
    }

    @Test
    @DisplayName("Should retrieve application state successfully")
    void testGetApplicationState() {
        // Given
        String appId = "test-app";
        ApplicationState expectedState = ApplicationState.RUNNING;
        when(mockStateMap.get(appId)).thenReturn(expectedState);

        // When
        ApplicationState actualState = stateStore.getApplicationState(appId);

        // Then
        assertEquals(expectedState, actualState);
        verify(mockStateMap).get(appId);
    }

    @Test
    @DisplayName("Should return null for non-existent application state")
    void testGetApplicationStateNotFound() {
        // Given
        String appId = "non-existent-app";
        when(mockStateMap.get(appId)).thenReturn(null);

        // When
        ApplicationState state = stateStore.getApplicationState(appId);

        // Then
        assertNull(state);
    }

    @Test
    @DisplayName("Should return all application states")
    void testGetAllApplicationStates() {
        // Given
        Map<String, ApplicationState> expectedStates = new HashMap<>();
        expectedStates.put("app1", ApplicationState.RUNNING);
        expectedStates.put("app2", ApplicationState.STOPPED);
        expectedStates.put("app3", ApplicationState.DEPLOYED);

        when(mockStateMap.entrySet()).thenReturn(expectedStates.entrySet());
        when(mockStateMap.size()).thenReturn(expectedStates.size());
        when(mockStateMap.isEmpty()).thenReturn(false);

        // Create a mock that behaves like a real HashMap for iteration
        Map<String, ApplicationState> mockMapBehavior = new HashMap<>(expectedStates);
        when(mockStateMap.entrySet()).thenReturn(mockMapBehavior.entrySet());
        when(mockStateMap.keySet()).thenReturn(mockMapBehavior.keySet());
        when(mockStateMap.values()).thenReturn(mockMapBehavior.values());

        // When
        Map<String, ApplicationState> actualStates = stateStore.getAllApplicationStates();

        // Then
        assertEquals(expectedStates.size(), actualStates.size());
    }

    @Test
    @DisplayName("Should store application descriptor successfully")
    void testPutApplicationDescriptor() {
        // Given
        String appId = "test-app";
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId(appId)
                .name("Test App")
                .version("1.0.0")
                .mainClass("com.test.Main")
                .build();

        // When
        stateStore.putApplicationDescriptor(appId, descriptor);

        // Then
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockDescriptorMap).put(eq(appId), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertNotNull(json);
        assertTrue(json.contains(appId));
        assertTrue(json.contains("Test App"));
    }

    @Test
    @DisplayName("Should retrieve application descriptor successfully")
    void testGetApplicationDescriptor() {
        // Given
        String appId = "test-app";
        String json = "{\"applicationId\":\"test-app\",\"name\":\"Test App\",\"version\":\"1.0.0\"," +
                "\"mainClass\":\"com.test.Main\",\"classpath\":[],\"environmentVariables\":{},\"systemProperties\":{}}";
        when(mockDescriptorMap.get(appId)).thenReturn(json);

        // When
        ApplicationDescriptor descriptor = stateStore.getApplicationDescriptor(appId);

        // Then
        assertNotNull(descriptor);
        assertEquals(appId, descriptor.getApplicationId());
        assertEquals("Test App", descriptor.getName());
        assertEquals("1.0.0", descriptor.getVersion());
    }

    @Test
    @DisplayName("Should return null for non-existent application descriptor")
    void testGetApplicationDescriptorNotFound() {
        // Given
        String appId = "non-existent-app";
        when(mockDescriptorMap.get(appId)).thenReturn(null);

        // When
        ApplicationDescriptor descriptor = stateStore.getApplicationDescriptor(appId);

        // Then
        assertNull(descriptor);
    }

    @Test
    @DisplayName("Should throw exception for invalid JSON in descriptor")
    void testGetApplicationDescriptorInvalidJson() {
        // Given
        String appId = "test-app";
        String invalidJson = "{invalid json}";
        when(mockDescriptorMap.get(appId)).thenReturn(invalidJson);

        // When/Then
        assertThrows(RuntimeException.class, () -> stateStore.getApplicationDescriptor(appId));
    }

    @Test
    @DisplayName("Should return all application descriptors")
    void testGetAllApplicationDescriptors() {
        // Given
        Map<String, String> jsonMap = new HashMap<>();
        jsonMap.put("app1", "{\"applicationId\":\"app1\",\"name\":\"App 1\",\"version\":\"1.0.0\"," +
                "\"mainClass\":\"com.test.Main1\",\"classpath\":[],\"environmentVariables\":{},\"systemProperties\":{}}");
        jsonMap.put("app2", "{\"applicationId\":\"app2\",\"name\":\"App 2\",\"version\":\"2.0.0\"," +
                "\"mainClass\":\"com.test.Main2\",\"classpath\":[],\"environmentVariables\":{},\"systemProperties\":{}}");

        when(mockDescriptorMap.entrySet()).thenReturn(jsonMap.entrySet());

        // When
        Map<String, ApplicationDescriptor> descriptors = stateStore.getAllApplicationDescriptors();

        // Then
        assertEquals(2, descriptors.size());
        assertTrue(descriptors.containsKey("app1"));
        assertTrue(descriptors.containsKey("app2"));
        assertEquals("App 1", descriptors.get("app1").getName());
        assertEquals("App 2", descriptors.get("app2").getName());
    }

    @Test
    @DisplayName("Should throw RuntimeException when encountering invalid JSON")
    void testGetAllApplicationDescriptorsWithInvalid() {
        // Given
        Map<String, String> jsonMap = new HashMap<>();
        jsonMap.put("app1", "{\"applicationId\":\"app1\",\"name\":\"App 1\",\"version\":\"1.0.0\"," +
                "\"mainClass\":\"com.test.Main1\",\"classpath\":[],\"environmentVariables\":{},\"systemProperties\":{}}");
        jsonMap.put("app2", "{invalid json}");

        when(mockDescriptorMap.entrySet()).thenReturn(jsonMap.entrySet());

        // When / Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> stateStore.getAllApplicationDescriptors());

        assertTrue(exception.getMessage().contains("Failed to deserialize application descriptor"));
        assertTrue(exception.getMessage().contains("cluster state may be corrupted"));
    }

    @Test
    @DisplayName("Should subscribe to state changes")
    void testSubscribe() {
        // Given
        String appId = "test-app";
        StateChangeListener listener = mock(StateChangeListener.class);
        UUID listenerId = UUID.randomUUID();

        when(mockStateMap.addEntryListener(any(), eq(appId), eq(true)))
                .thenReturn(listenerId);

        // When
        stateStore.subscribe(appId, listener);

        // Then
        verify(mockStateMap).addEntryListener(any(), eq(appId), eq(true));
    }

    @Test
    @DisplayName("Should handle null listener in subscribe")
    void testSubscribeNullListener() {
        // Given
        String appId = "test-app";

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> stateStore.subscribe(appId, null));
        verify(mockStateMap, never()).addEntryListener(any(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("Should unsubscribe from state changes")
    void testUnsubscribe() {
        // Given
        String appId = "test-app";
        StateChangeListener listener = mock(StateChangeListener.class);
        UUID listenerId = UUID.randomUUID();

        when(mockStateMap.addEntryListener(any(), eq(appId), eq(true)))
                .thenReturn(listenerId);

        stateStore.subscribe(appId, listener);

        // When
        stateStore.unsubscribe(appId, listener);

        // Then
        verify(mockStateMap).removeEntryListener(listenerId);
    }

    @Test
    @DisplayName("Should handle null listener in unsubscribe")
    void testUnsubscribeNullListener() {
        // Given
        String appId = "test-app";

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> stateStore.unsubscribe(appId, null));
        verify(mockStateMap, never()).removeEntryListener(any(UUID.class));
    }

    @Test
    @DisplayName("Should handle unsubscribe for non-existent listener")
    void testUnsubscribeNonExistent() {
        // Given
        String appId = "test-app";
        StateChangeListener listener = mock(StateChangeListener.class);

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> stateStore.unsubscribe(appId, listener));
    }

    @Test
    @DisplayName("Should notify listener on entry added")
    @SuppressWarnings("unchecked")
    void testListenerEntryAdded() {
        // Given
        String appId = "test-app";
        StateChangeListener listener = mock(StateChangeListener.class);
        UUID listenerId = UUID.randomUUID();

        ArgumentCaptor<com.hazelcast.map.listener.MapListener> listenerCaptor =
                ArgumentCaptor.forClass(com.hazelcast.map.listener.MapListener.class);
        when(mockStateMap.addEntryListener(listenerCaptor.capture(), eq(appId), eq(true)))
                .thenReturn(listenerId);

        stateStore.subscribe(appId, listener);

        // Get the internal listener
        Object internalListener = listenerCaptor.getValue();
        assertTrue(internalListener instanceof EntryAddedListener);

        // When - Simulate entry added event
        EntryEvent<String, ApplicationState> event = mock(EntryEvent.class);
        when(event.getKey()).thenReturn(appId);
        when(event.getValue()).thenReturn(ApplicationState.RUNNING);

        ((EntryAddedListener<String, ApplicationState>) internalListener).entryAdded(event);

        // Then
        verify(listener).onStateChanged(appId, ApplicationState.RUNNING);
    }

    @Test
    @DisplayName("Should notify listener on entry updated")
    @SuppressWarnings("unchecked")
    void testListenerEntryUpdated() {
        // Given
        String appId = "test-app";
        StateChangeListener listener = mock(StateChangeListener.class);
        UUID listenerId = UUID.randomUUID();

        ArgumentCaptor<com.hazelcast.map.listener.MapListener> listenerCaptor =
                ArgumentCaptor.forClass(com.hazelcast.map.listener.MapListener.class);
        when(mockStateMap.addEntryListener(listenerCaptor.capture(), eq(appId), eq(true)))
                .thenReturn(listenerId);

        stateStore.subscribe(appId, listener);

        // Get the internal listener
        Object internalListener = listenerCaptor.getValue();
        assertTrue(internalListener instanceof EntryUpdatedListener);

        // When - Simulate entry updated event
        EntryEvent<String, ApplicationState> event = mock(EntryEvent.class);
        when(event.getKey()).thenReturn(appId);
        when(event.getValue()).thenReturn(ApplicationState.STOPPED);

        ((EntryUpdatedListener<String, ApplicationState>) internalListener).entryUpdated(event);

        // Then
        verify(listener).onStateChanged(appId, ApplicationState.STOPPED);
    }

    @Test
    @DisplayName("Should notify listener on entry removed")
    @SuppressWarnings("unchecked")
    void testListenerEntryRemoved() {
        // Given
        String appId = "test-app";
        StateChangeListener listener = mock(StateChangeListener.class);
        UUID listenerId = UUID.randomUUID();

        ArgumentCaptor<com.hazelcast.map.listener.MapListener> listenerCaptor =
                ArgumentCaptor.forClass(com.hazelcast.map.listener.MapListener.class);
        when(mockStateMap.addEntryListener(listenerCaptor.capture(), eq(appId), eq(true)))
                .thenReturn(listenerId);

        stateStore.subscribe(appId, listener);

        // Get the internal listener
        Object internalListener = listenerCaptor.getValue();
        assertTrue(internalListener instanceof EntryRemovedListener);

        // When - Simulate entry removed event
        EntryEvent<String, ApplicationState> event = mock(EntryEvent.class);
        when(event.getKey()).thenReturn(appId);

        ((EntryRemovedListener<String, ApplicationState>) internalListener).entryRemoved(event);

        // Then
        verify(listener).onStateChanged(appId, null);
    }

    @Test
    @DisplayName("Should handle listener exceptions gracefully")
    @SuppressWarnings("unchecked")
    void testListenerExceptionHandling() {
        // Given
        String appId = "test-app";
        StateChangeListener listener = mock(StateChangeListener.class);
        UUID listenerId = UUID.randomUUID();

        ArgumentCaptor<com.hazelcast.map.listener.MapListener> listenerCaptor =
                ArgumentCaptor.forClass(com.hazelcast.map.listener.MapListener.class);
        when(mockStateMap.addEntryListener(listenerCaptor.capture(), eq(appId), eq(true)))
                .thenReturn(listenerId);

        doThrow(new RuntimeException("Test exception")).when(listener).onStateChanged(anyString(), any());

        stateStore.subscribe(appId, listener);

        // Get the internal listener
        Object internalListener = listenerCaptor.getValue();

        // When - Simulate entry added event
        EntryEvent<String, ApplicationState> event = mock(EntryEvent.class);
        when(event.getKey()).thenReturn(appId);
        when(event.getValue()).thenReturn(ApplicationState.RUNNING);

        // Then - should not throw exception
        assertDoesNotThrow(() ->
            ((EntryAddedListener<String, ApplicationState>) internalListener).entryAdded(event));
    }

    @Test
    @DisplayName("Should handle multiple subscriptions for same app")
    void testMultipleSubscriptions() {
        // Given
        String appId = "test-app";
        StateChangeListener listener1 = mock(StateChangeListener.class);
        StateChangeListener listener2 = mock(StateChangeListener.class);
        UUID listenerId1 = UUID.randomUUID();
        UUID listenerId2 = UUID.randomUUID();

        when(mockStateMap.addEntryListener(any(), eq(appId), eq(true)))
                .thenReturn(listenerId1, listenerId2);

        // When
        stateStore.subscribe(appId, listener1);
        stateStore.subscribe(appId, listener2);

        // Then
        verify(mockStateMap, times(2)).addEntryListener(any(), eq(appId), eq(true));
    }

    @Test
    @DisplayName("Should store and retrieve complex descriptor")
    void testComplexDescriptor() {
        // Given
        String appId = "complex-app";
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId(appId)
                .name("Complex App")
                .version("2.3.1")
                .mainClass("com.example.ComplexMain")
                .addClasspathEntry(URI.create("file:///lib/app.jar"))
                .addClasspathEntry(URI.create("file:///lib/dependency.jar"))
                .property("ENV_VAR", "value")
                .property("prop.key", "prop.value")
                .build();

        // When
        stateStore.putApplicationDescriptor(appId, descriptor);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockDescriptorMap).put(eq(appId), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        when(mockDescriptorMap.get(appId)).thenReturn(json);

        ApplicationDescriptor retrieved = stateStore.getApplicationDescriptor(appId);

        // Then
        assertNotNull(retrieved);
        assertEquals(appId, retrieved.getApplicationId());
        assertEquals("Complex App", retrieved.getName());
        assertEquals("2.3.1", retrieved.getVersion());
        assertEquals("com.example.ComplexMain", retrieved.getMainClass());
        assertEquals(2, retrieved.getClasspathEntries().size());
        assertTrue(retrieved.getProperties().containsKey("ENV_VAR"));
        assertTrue(retrieved.getProperties().containsKey("prop.key"));
    }

    @Test
    @DisplayName("Should handle state transitions")
    void testStateTransitions() {
        // Given
        String appId = "transitioning-app";

        // When - Simulate state transitions
        stateStore.putApplicationState(appId, ApplicationState.DEPLOYED);
        stateStore.putApplicationState(appId, ApplicationState.STARTING);
        stateStore.putApplicationState(appId, ApplicationState.RUNNING);
        stateStore.putApplicationState(appId, ApplicationState.STOPPING);
        stateStore.putApplicationState(appId, ApplicationState.STOPPED);

        // Then
        verify(mockStateMap).put(appId, ApplicationState.DEPLOYED);
        verify(mockStateMap).put(appId, ApplicationState.STARTING);
        verify(mockStateMap).put(appId, ApplicationState.RUNNING);
        verify(mockStateMap).put(appId, ApplicationState.STOPPING);
        verify(mockStateMap).put(appId, ApplicationState.STOPPED);
    }

    @Test
    @DisplayName("Should handle concurrent subscriptions safely")
    void testConcurrentSubscriptions() throws InterruptedException {
        // Given
        String appId = "test-app";
        int threadCount = 10;
        List<StateChangeListener> listeners = new ArrayList<>();
        List<UUID> listenerIds = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            listeners.add(mock(StateChangeListener.class));
            listenerIds.add(UUID.randomUUID());
        }

        when(mockStateMap.addEntryListener(any(), eq(appId), eq(true)))
                .thenReturn(listenerIds.get(0), listenerIds.subList(1, listenerIds.size()).toArray(new UUID[0]));

        // When - Subscribe from multiple threads
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> stateStore.subscribe(appId, listeners.get(index)));
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        verify(mockStateMap, times(threadCount)).addEntryListener(any(), eq(appId), eq(true));
    }
}
