package org.flossware.jplatform.cluster.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flossware.jplatform.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisStateStoreTest {
    private JedisPool mockPool;
    private Jedis mockJedis;
    private RedisStateStore store;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mockPool = mock(JedisPool.class);
        mockJedis = mock(Jedis.class);
        when(mockPool.getResource()).thenReturn(mockJedis);
        store = new RedisStateStore(mockPool);
        mapper = new ObjectMapper();
    }

    @Test
    void testConstruction() {
        assertNotNull(store);
    }

    @Test
    void testPutApplicationState() throws Exception {
        ApplicationState state = ApplicationState.RUNNING;
        String expectedJson = mapper.writeValueAsString(state);

        store.putApplicationState("app1", state);

        verify(mockJedis).hset("jplatform:states", "app1", expectedJson);
    }

    @Test
    void testGetApplicationState() throws Exception {
        ApplicationState state = ApplicationState.RUNNING;
        String json = mapper.writeValueAsString(state);
        when(mockJedis.hget("jplatform:states", "app1")).thenReturn(json);

        ApplicationState result = store.getApplicationState("app1");

        assertEquals(state, result);
    }

    @Test
    void testGetApplicationState_NotFound() {
        when(mockJedis.hget("jplatform:states", "app1")).thenReturn(null);

        ApplicationState result = store.getApplicationState("app1");

        assertNull(result);
    }

    @Test
    void testGetAllApplicationStates() throws Exception {
        Map<String, String> redisData = new HashMap<>();
        redisData.put("app1", mapper.writeValueAsString(ApplicationState.RUNNING));
        redisData.put("app2", mapper.writeValueAsString(ApplicationState.STOPPED));
        when(mockJedis.hgetAll("jplatform:states")).thenReturn(redisData);

        Map<String, ApplicationState> result = store.getAllApplicationStates();

        assertEquals(2, result.size());
        assertEquals(ApplicationState.RUNNING, result.get("app1"));
        assertEquals(ApplicationState.STOPPED, result.get("app2"));
    }

    @Test
    void testGetAllApplicationStates_Empty() {
        when(mockJedis.hgetAll("jplatform:states")).thenReturn(new HashMap<>());

        Map<String, ApplicationState> result = store.getAllApplicationStates();

        assertTrue(result.isEmpty());
    }

    @Test
    void testPutApplicationDescriptor() {
        ApplicationDescriptor desc = ApplicationDescriptor.builder()
            .applicationId("app1")
            .name("Test App")
            .version("1.0")
            .mainClass("com.example.Main")
            .build();

        // Just test that it doesn't throw exception
        assertDoesNotThrow(() -> store.putApplicationDescriptor("app1", desc));
        verify(mockJedis).hset(eq("jplatform:descriptors"), eq("app1"), anyString());
    }

    @Test
    void testGetApplicationDescriptor_NotFound() {
        when(mockJedis.hget("jplatform:descriptors", "app1")).thenReturn(null);

        ApplicationDescriptor result = store.getApplicationDescriptor("app1");

        assertNull(result);
    }

    @Test
    void testGetAllApplicationDescriptors_Empty() {
        when(mockJedis.hgetAll("jplatform:descriptors")).thenReturn(new HashMap<>());

        Map<String, ApplicationDescriptor> result = store.getAllApplicationDescriptors();

        assertTrue(result.isEmpty());
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
        store.clear();

        verify(mockJedis).del("jplatform:states", "jplatform:descriptors");
    }

    @Test
    void testListenerNotification() throws Exception {
        ClusterStateStore.StateChangeListener listener = mock(ClusterStateStore.StateChangeListener.class);
        store.subscribe("app1", listener);

        ApplicationState state = ApplicationState.RUNNING;
        store.putApplicationState("app1", state);

        verify(listener).onStateChanged("app1", state);
    }
}
