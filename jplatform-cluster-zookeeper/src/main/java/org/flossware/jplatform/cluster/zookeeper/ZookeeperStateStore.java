package org.flossware.jplatform.cluster.zookeeper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ZooKeeper-based implementation of ClusterStateStore using Apache Curator.
 * Stores distributed application state and descriptors using ZooKeeper znodes.
 *
 * <p>This implementation uses:
 * <ul>
 *   <li>ZooKeeper znodes for storing application states</li>
 *   <li>ZooKeeper znodes for storing application descriptors</li>
 *   <li>JSON serialization for complex objects</li>
 *   <li>Watchers for change notifications</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections.
 *
 * @since 1.1
 */
public class ZookeeperStateStore implements ClusterStateStore {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperStateStore.class);
    private static final String STATE_PATH = "/jplatform/states";
    private static final String DESCRIPTOR_PATH = "/jplatform/descriptors";

    private final CuratorFramework client;
    private final ObjectMapper mapper;
    private final Map<String, List<StateChangeListener>> listeners;

    /**
     * Constructs a new ZooKeeper state store.
     *
     * @param client the Curator framework client
     */
    public ZookeeperStateStore(CuratorFramework client) {
        this.client = client;
        this.mapper = new ObjectMapper();
        this.listeners = new ConcurrentHashMap<>();
    }

    @Override
    public void putApplicationState(String id, ApplicationState state) {
        try {
            String json = mapper.writeValueAsString(state);
            String path = STATE_PATH + "/" + id;

            if (client.checkExists().forPath(path) != null) {
                client.setData().forPath(path, json.getBytes());
            } else {
                client.create().creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(path, json.getBytes());
            }
            notifyListeners(id, state);
        } catch (Exception e) {
            logger.error("Failed to put application state for " + id, e);
        }
    }

    @Override
    public ApplicationState getApplicationState(String id) {
        try {
            String path = STATE_PATH + "/" + id;
            if (client.checkExists().forPath(path) == null) {
                return null;
            }
            byte[] data = client.getData().forPath(path);
            return mapper.readValue(data, ApplicationState.class);
        } catch (Exception e) {
            logger.error("Failed to get application state for " + id, e);
            return null;
        }
    }

    @Override
    public Map<String, ApplicationState> getAllApplicationStates() {
        Map<String, ApplicationState> states = new HashMap<>();
        try {
            if (client.checkExists().forPath(STATE_PATH) == null) {
                return states;
            }
            List<String> children = client.getChildren().forPath(STATE_PATH);
            for (String child : children) {
                try {
                    String path = STATE_PATH + "/" + child;
                    byte[] data = client.getData().forPath(path);
                    ApplicationState state = mapper.readValue(data, ApplicationState.class);
                    states.put(child, state);
                } catch (Exception e) {
                    logger.error("Failed to deserialize state for " + child, e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get all application states", e);
        }
        return states;
    }

    @Override
    public void putApplicationDescriptor(String id, ApplicationDescriptor desc) {
        try {
            String json = mapper.writeValueAsString(desc);
            String path = DESCRIPTOR_PATH + "/" + id;

            if (client.checkExists().forPath(path) != null) {
                client.setData().forPath(path, json.getBytes());
            } else {
                client.create().creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(path, json.getBytes());
            }
        } catch (Exception e) {
            logger.error("Failed to put application descriptor for " + id, e);
        }
    }

    @Override
    public ApplicationDescriptor getApplicationDescriptor(String id) {
        try {
            String path = DESCRIPTOR_PATH + "/" + id;
            if (client.checkExists().forPath(path) == null) {
                return null;
            }
            byte[] data = client.getData().forPath(path);
            return mapper.readValue(data, ApplicationDescriptor.class);
        } catch (Exception e) {
            logger.error("Failed to get application descriptor for " + id, e);
            return null;
        }
    }

    @Override
    public Map<String, ApplicationDescriptor> getAllApplicationDescriptors() {
        Map<String, ApplicationDescriptor> descriptors = new HashMap<>();
        try {
            if (client.checkExists().forPath(DESCRIPTOR_PATH) == null) {
                return descriptors;
            }
            List<String> children = client.getChildren().forPath(DESCRIPTOR_PATH);
            for (String child : children) {
                try {
                    String path = DESCRIPTOR_PATH + "/" + child;
                    byte[] data = client.getData().forPath(path);
                    ApplicationDescriptor desc = mapper.readValue(data, ApplicationDescriptor.class);
                    descriptors.put(child, desc);
                } catch (Exception e) {
                    logger.error("Failed to deserialize descriptor for " + child, e);
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
        try {
            if (client.checkExists().forPath(STATE_PATH) != null) {
                client.delete().deletingChildrenIfNeeded().forPath(STATE_PATH);
            }
            if (client.checkExists().forPath(DESCRIPTOR_PATH) != null) {
                client.delete().deletingChildrenIfNeeded().forPath(DESCRIPTOR_PATH);
            }
        } catch (Exception e) {
            logger.error("Failed to clear state store", e);
        }
    }
}
