package org.flossware.jplatform.cluster.zookeeper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.*;
import org.apache.zookeeper.data.Stat;
import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

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
    void testPutApplicationDescriptor() {
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
}
