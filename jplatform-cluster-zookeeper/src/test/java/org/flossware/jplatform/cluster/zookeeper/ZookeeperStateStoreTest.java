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

package org.flossware.jplatform.cluster.zookeeper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.*;
import org.apache.zookeeper.data.Stat;
import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZookeeperStateStoreTest {
    private CuratorFramework mockClient;
    private ZookeeperStateStore store;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mockClient = mock(CuratorFramework.class);
        store = new ZookeeperStateStore(mockClient);
        mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());
    }

    @Test
    void testConstruction() {
        assertNotNull(store);
    }

    @Test
    void testGetApplicationState_NotFound() throws Exception {
        ExistsBuilder existsBuilder = mock(ExistsBuilder.class);
        when(mockClient.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath(anyString())).thenReturn(null);

        ApplicationState result = store.getApplicationState("app1");

        assertNull(result);
    }

    @Test
    void testGetApplicationState() throws Exception {
        ApplicationState state = ApplicationState.RUNNING;
        byte[] data = mapper.writeValueAsBytes(state);

        ExistsBuilder existsBuilder = mock(ExistsBuilder.class);
        GetDataBuilder getDataBuilder = mock(GetDataBuilder.class);

        when(mockClient.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath(anyString())).thenReturn(new Stat());
        when(mockClient.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath(anyString())).thenReturn(data);

        ApplicationState result = store.getApplicationState("app1");

        assertEquals(state, result);
    }

    @Test
    void testGetAllApplicationStates_Empty() throws Exception {
        ExistsBuilder existsBuilder = mock(ExistsBuilder.class);
        when(mockClient.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath(anyString())).thenReturn(null);

        assertTrue(store.getAllApplicationStates().isEmpty());
    }

    @Test
    void testGetAllApplicationStates() throws Exception {
        ExistsBuilder existsBuilder = mock(ExistsBuilder.class);
        GetChildrenBuilder childrenBuilder = mock(GetChildrenBuilder.class);
        GetDataBuilder getDataBuilder = mock(GetDataBuilder.class);

        when(mockClient.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath(anyString())).thenReturn(new Stat());
        when(mockClient.getChildren()).thenReturn(childrenBuilder);
        when(childrenBuilder.forPath(anyString())).thenReturn(Collections.singletonList("app1"));
        when(mockClient.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath(anyString()))
            .thenReturn(mapper.writeValueAsBytes(ApplicationState.RUNNING));

        assertEquals(1, store.getAllApplicationStates().size());
    }

    @Test
    void testPutApplicationDescriptor() throws Exception {
        // Mock the checkExists chain (path doesn't exist yet)
        ExistsBuilder existsBuilder = mock(ExistsBuilder.class);
        when(mockClient.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath(anyString())).thenReturn(null);

        // Mock the create chain - need to mock the full fluent chain
        CreateBuilder createBuilder = mock(CreateBuilder.class);
        ProtectACLCreateModeStatPathAndBytesable protectBuilder = mock(ProtectACLCreateModeStatPathAndBytesable.class);
        ACLBackgroundPathAndBytesable<String> aclBuilder = mock(ACLBackgroundPathAndBytesable.class);

        when(mockClient.create()).thenReturn(createBuilder);
        when(createBuilder.creatingParentsIfNeeded()).thenReturn(protectBuilder);
        when(protectBuilder.withMode(any())).thenReturn(aclBuilder);
        when(aclBuilder.forPath(anyString(), any(byte[].class))).thenReturn(null);

        ApplicationDescriptor desc = ApplicationDescriptor.builder()
            .applicationId("app1")
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .build();

        assertDoesNotThrow(() -> store.putApplicationDescriptor("app1", desc));
    }

    @Test
    void testGetApplicationDescriptor_NotFound() throws Exception {
        ExistsBuilder existsBuilder = mock(ExistsBuilder.class);
        when(mockClient.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath(anyString())).thenReturn(null);

        ApplicationDescriptor result = store.getApplicationDescriptor("app1");

        assertNull(result);
    }

    @Test
    void testGetAllApplicationDescriptors_Empty() throws Exception {
        ExistsBuilder existsBuilder = mock(ExistsBuilder.class);
        when(mockClient.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath(anyString())).thenReturn(null);

        assertTrue(store.getAllApplicationDescriptors().isEmpty());
    }

    @Test
    void testSubscribe() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        assertDoesNotThrow(() -> store.subscribe("app1", listener));
    }

    @Test
    void testUnsubscribe() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        store.subscribe("app1", listener);
        assertDoesNotThrow(() -> store.unsubscribe("app1", listener));
    }

    @Test
    void testClear() {
        assertDoesNotThrow(() -> store.clear());
    }

    @Test
    void testConstructorNullClient() {
        assertThrows(IllegalArgumentException.class, () ->
            new ZookeeperStateStore(null)
        );
    }

    @Test
    void testGetApplicationStateMultipleStates() throws Exception {
        ApplicationState state1 = ApplicationState.RUNNING;
        ApplicationState state2 = ApplicationState.STOPPED;
        byte[] data1 = mapper.writeValueAsBytes(state1);
        byte[] data2 = mapper.writeValueAsBytes(state2);

        ExistsBuilder existsBuilder = mock(ExistsBuilder.class);
        GetDataBuilder getDataBuilder = mock(GetDataBuilder.class);

        when(mockClient.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath("/jplatform/states/app1")).thenReturn(new Stat());
        when(existsBuilder.forPath("/jplatform/states/app2")).thenReturn(new Stat());
        when(mockClient.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/jplatform/states/app1")).thenReturn(data1);
        when(getDataBuilder.forPath("/jplatform/states/app2")).thenReturn(data2);

        ApplicationState result1 = store.getApplicationState("app1");
        ApplicationState result2 = store.getApplicationState("app2");

        assertEquals(state1, result1);
        assertEquals(state2, result2);
    }

    @Test
    void testSubscribeSameListenerMultipleTimes() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        assertDoesNotThrow(() -> store.subscribe("app1", listener));
        assertDoesNotThrow(() -> store.subscribe("app1", listener));
    }

    @Test
    void testUnsubscribeNotSubscribed() {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);

        assertDoesNotThrow(() -> store.unsubscribe("app1", listener));
    }

    @Test
    void testAllApplicationStatesReturnsMap() throws Exception {
        ExistsBuilder existsBuilder = mock(ExistsBuilder.class);
        when(mockClient.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath(anyString())).thenReturn(null);

        Map<String, ApplicationState> states = store.getAllApplicationStates();

        assertNotNull(states);
        assertTrue(states.isEmpty());
    }

    @Test
    void testAllApplicationDescriptorsReturnsMap() throws Exception {
        ExistsBuilder existsBuilder = mock(ExistsBuilder.class);
        when(mockClient.checkExists()).thenReturn(existsBuilder);
        when(existsBuilder.forPath(anyString())).thenReturn(null);

        Map<String, ApplicationDescriptor> descriptors = store.getAllApplicationDescriptors();

        assertNotNull(descriptors);
        assertTrue(descriptors.isEmpty());
    }
}
