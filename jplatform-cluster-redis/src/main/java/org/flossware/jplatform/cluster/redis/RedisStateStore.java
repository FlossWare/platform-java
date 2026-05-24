package org.flossware.jplatform.cluster.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Redis-based implementation of ClusterStateStore.
 * Stores distributed application state and descriptors using Redis hashes.
 *
 * <p>This implementation uses:
 * <ul>
 *   <li>Redis hashes for storing application states</li>
 *   <li>Redis hashes for storing application descriptors</li>
 *   <li>JSON serialization for complex objects</li>
 *   <li>Local caching for read performance</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections.
 *
 * @since 1.1
 */
public class RedisStateStore implements ClusterStateStore {
    private static final Logger logger = LoggerFactory.getLogger(RedisStateStore.class);
    private static final String STATE_KEY = "jplatform:states";
    private static final String DESCRIPTOR_KEY = "jplatform:descriptors";

    private final JedisPool pool;
    private final ObjectMapper mapper;
    private final Map<String, List<StateChangeListener>> listeners;

    /**
     * Constructs a new Redis state store.
     *
     * @param pool the Jedis connection pool
     */
    public RedisStateStore(JedisPool pool) {
        this.pool = pool;
        this.mapper = new ObjectMapper();
        this.listeners = new ConcurrentHashMap<>();
    }

    @Override
    public void putApplicationState(String id, ApplicationState state) {
        try (Jedis jedis = pool.getResource()) {
            String json = mapper.writeValueAsString(state);
            jedis.hset(STATE_KEY, id, json);
            notifyListeners(id, state);
        } catch (Exception e) {
            logger.error("Failed to put application state for " + id, e);
        }
    }

    @Override
    public ApplicationState getApplicationState(String id) {
        try (Jedis jedis = pool.getResource()) {
            String json = jedis.hget(STATE_KEY, id);
            if (json == null) return null;
            return mapper.readValue(json, ApplicationState.class);
        } catch (Exception e) {
            logger.error("Failed to get application state for " + id, e);
            return null;
        }
    }

    @Override
    public Map<String, ApplicationState> getAllApplicationStates() {
        Map<String, ApplicationState> states = new HashMap<>();
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> allStates = jedis.hgetAll(STATE_KEY);
            for (Map.Entry<String, String> entry : allStates.entrySet()) {
                try {
                    ApplicationState state = mapper.readValue(entry.getValue(), ApplicationState.class);
                    states.put(entry.getKey(), state);
                } catch (Exception e) {
                    logger.error("Failed to deserialize state for " + entry.getKey(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get all application states", e);
        }
        return states;
    }

    @Override
    public void putApplicationDescriptor(String id, ApplicationDescriptor desc) {
        try (Jedis jedis = pool.getResource()) {
            String json = mapper.writeValueAsString(desc);
            jedis.hset(DESCRIPTOR_KEY, id, json);
        } catch (Exception e) {
            logger.error("Failed to put application descriptor for " + id, e);
        }
    }

    @Override
    public ApplicationDescriptor getApplicationDescriptor(String id) {
        try (Jedis jedis = pool.getResource()) {
            String json = jedis.hget(DESCRIPTOR_KEY, id);
            if (json == null) return null;
            return mapper.readValue(json, ApplicationDescriptor.class);
        } catch (Exception e) {
            logger.error("Failed to get application descriptor for " + id, e);
            return null;
        }
    }

    @Override
    public Map<String, ApplicationDescriptor> getAllApplicationDescriptors() {
        Map<String, ApplicationDescriptor> descriptors = new HashMap<>();
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> allDescriptors = jedis.hgetAll(DESCRIPTOR_KEY);
            for (Map.Entry<String, String> entry : allDescriptors.entrySet()) {
                try {
                    ApplicationDescriptor desc = mapper.readValue(entry.getValue(), ApplicationDescriptor.class);
                    descriptors.put(entry.getKey(), desc);
                } catch (Exception e) {
                    logger.error("Failed to deserialize descriptor for " + entry.getKey(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get all application descriptors", e);
        }
        return descriptors;
    }

    @Override
    public void subscribe(String key, StateChangeListener listener) {
        listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void unsubscribe(String key, StateChangeListener listener) {
        List<StateChangeListener> keyListeners = listeners.get(key);
        if (keyListeners != null) {
            keyListeners.remove(listener);
        }
    }

    private void notifyListeners(String id, ApplicationState state) {
        List<StateChangeListener> keyListeners = listeners.get(id);
        if (keyListeners != null) {
            for (StateChangeListener listener : keyListeners) {
                try {
                    listener.onStateChanged(id, state);
                } catch (Exception e) {
                    logger.error("Listener error for " + id, e);
                }
            }
        }
    }

    /**
     * Clears all stored state and descriptors.
     * Useful for testing.
     */
    public void clear() {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(STATE_KEY, DESCRIPTOR_KEY);
        } catch (Exception e) {
            logger.error("Failed to clear state store", e);
        }
    }
}
