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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.model.kv.Value;
import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Consul-based implementation of ClusterStateStore.
 * Provides distributed storage for application state and descriptors
 * using Consul's key/value store.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Distributed storage with Consul KV</li>
 *   <li>JSON serialization for ApplicationDescriptor</li>
 *   <li>Thread-safe concurrent access</li>
 *   <li>Watch-based state change notifications</li>
 * </ul>
 *
 * <p>Uses Consul KV paths:</p>
 * <ul>
 *   <li>"jplatform/state/{appId}" - stores ApplicationState enum name</li>
 *   <li>"jplatform/descriptor/{appId}" - stores JSON-serialized ApplicationDescriptor</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * Consul consulClient = Consul.builder().build();
 * ConsulStateStore store = new ConsulStateStore(consulClient);
 *
 * // Store application descriptor
 * store.putApplicationDescriptor("my-app", descriptor);
 *
 * // Update state
 * store.putApplicationState("my-app", ApplicationState.RUNNING);
 *
 * // Subscribe to changes
 * store.subscribe("my-app", (key, value) -> {
 *     System.out.println("State changed: " + value);
 * });
 * }</pre>
 *
 * @see ClusterStateStore
 * @see ConsulClusterManager
 * @since 1.1
 */
public class ConsulStateStore implements ClusterStateStore {

    private static final Logger logger = LoggerFactory.getLogger(ConsulStateStore.class);
    private static final String STATE_KEY_PREFIX = "jplatform/state/";
    private static final String DESCRIPTOR_KEY_PREFIX = "jplatform/descriptor/";

    private final Consul consulClient;
    private final KeyValueClient kvClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Set<StateChangeListener>> listeners;
    private final ScheduledExecutorService watchExecutor;
    private final Map<String, Future<?>> watchTasks;
    private final Map<String, Long> watchIndexes;

    /**
     * Constructs a new Consul state store.
     *
     * @param consulClient the Consul client to use for KV operations
     * @throws IllegalArgumentException if consulClient is null
     */
    public ConsulStateStore(Consul consulClient) {
        if (consulClient == null) {
            throw new IllegalArgumentException("ConsulClient must not be null");
        }
        this.consulClient = consulClient;
        this.kvClient = consulClient.keyValueClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.registerModule(new Jdk8Module());  // Support Optional types
        this.objectMapper.registerModule(new ApplicationDescriptorJsonModule());
        this.listeners = new ConcurrentHashMap<>();
        this.watchExecutor = Executors.newScheduledThreadPool(2);
        this.watchTasks = new ConcurrentHashMap<>();
        this.watchIndexes = new ConcurrentHashMap<>();

        logger.info("ConsulStateStore initialized with key prefixes: {}, {}",
                STATE_KEY_PREFIX, DESCRIPTOR_KEY_PREFIX);
    }

    /**
     * Stores application state in Consul KV.
     * The state enum name is stored as a string value.
     *
     * @param applicationId the application identifier
     * @param state the application state to store
     */
    @Override
    public void putApplicationState(String applicationId, ApplicationState state) {
        if (applicationId == null) {
            throw new IllegalArgumentException("applicationId cannot be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        logger.debug("Storing application state: {} -> {}", applicationId, state);
        String key = STATE_KEY_PREFIX + applicationId;
        kvClient.putValue(key, state.name());
        notifyListeners(applicationId, state);
    }

    /**
     * Retrieves application state from Consul KV.
     *
     * @param applicationId the application identifier
     * @return the application state, or null if not found
     */
    @Override
    public ApplicationState getApplicationState(String applicationId) {
        String key = STATE_KEY_PREFIX + applicationId;
        Optional<Value> value = kvClient.getValue(key);

        if (value.isPresent() && value.get().getValueAsString().isPresent()) {
            String stateName = value.get().getValueAsString().get();
            try {
                return ApplicationState.valueOf(stateName);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid state value for {}: {}", applicationId, stateName);
                return null;  // Return null for invalid state value rather than throwing
            }
        }

        return null;
    }

    /**
     * Returns all application states in the cluster.
     * Queries all keys under the state prefix.
     *
     * @return a map of application ID to state
     */
    @Override
    public Map<String, ApplicationState> getAllApplicationStates() {
        Map<String, ApplicationState> result = new HashMap<>();
        List<String> keys = kvClient.getKeys(STATE_KEY_PREFIX);

        for (String key : keys) {
            String appId = key.substring(STATE_KEY_PREFIX.length());
            ApplicationState state = getApplicationState(appId);
            if (state != null) {
                result.put(appId, state);
            }
        }

        return result;
    }

    /**
     * Stores an application descriptor in Consul KV.
     * The descriptor is serialized to JSON before storage.
     *
     * @param applicationId the application identifier
     * @param descriptor the application descriptor to store
     */
    @Override
    public void putApplicationDescriptor(String applicationId, ApplicationDescriptor descriptor) {
        if (applicationId == null) {
            throw new IllegalArgumentException("applicationId cannot be null");
        }
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor cannot be null");
        }
        try {
            String json = objectMapper.writeValueAsString(descriptor);
            logger.debug("Storing application descriptor: {} (size: {} bytes)",
                    applicationId, json.length());
            String key = DESCRIPTOR_KEY_PREFIX + applicationId;
            kvClient.putValue(key, json);
        } catch (Exception e) {
            logger.error("Failed to serialize application descriptor: {}", applicationId, e);
            throw new RuntimeException("Failed to serialize application descriptor", e);
        }
    }

    /**
     * Retrieves an application descriptor from Consul KV.
     * The descriptor is deserialized from JSON.
     *
     * @param applicationId the application identifier
     * @return the application descriptor, or null if not found
     */
    @Override
    public ApplicationDescriptor getApplicationDescriptor(String applicationId) {
        String key = DESCRIPTOR_KEY_PREFIX + applicationId;
        Optional<Value> value = kvClient.getValue(key);

        if (value.isPresent() && value.get().getValueAsString().isPresent()) {
            String json = value.get().getValueAsString().get();
            try {
                return objectMapper.readValue(json, ApplicationDescriptor.class);
            } catch (Exception e) {
                logger.error("Failed to deserialize application descriptor: {}", applicationId, e);
                return null;  // Return null for invalid JSON rather than throwing
            }
        }

        return null;
    }

    /**
     * Returns all application descriptors in the cluster.
     * Queries all keys under the descriptor prefix and deserializes them.
     *
     * @return a map of application ID to descriptor
     */
    @Override
    public Map<String, ApplicationDescriptor> getAllApplicationDescriptors() {
        Map<String, ApplicationDescriptor> result = new HashMap<>();
        List<String> keys = kvClient.getKeys(DESCRIPTOR_KEY_PREFIX);

        for (String key : keys) {
            String appId = key.substring(DESCRIPTOR_KEY_PREFIX.length());
            ApplicationDescriptor descriptor = getApplicationDescriptor(appId);
            if (descriptor != null) {
                result.put(appId, descriptor);
            }
        }

        return result;
    }

    /**
     * Subscribes to state changes for a specific application.
     * The listener will be notified whenever the state changes detected via Consul watch.
     *
     * <p>Implementation uses Consul polling to detect changes from other cluster nodes.
     * A background watch task polls Consul every 5 seconds for state changes.</p>
     *
     * @param key the application ID to watch
     * @param listener the listener to notify on changes
     */
    @Override
    public void subscribe(String key, StateChangeListener listener) {
        if (listener == null) {
            return;
        }

        boolean firstListener = listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
            .add(listener);

        // Start watch task if this is the first listener for this key
        if (firstListener && !watchTasks.containsKey(key)) {
            startWatch(key);
        }

        logger.debug("Subscribed to state changes for: {}", key);
    }

    /**
     * Unsubscribes from state changes for a specific application.
     * Stops the watch task if this is the last listener.
     *
     * @param key the application ID to stop watching
     * @param listener the listener to remove
     */
    @Override
    public void unsubscribe(String key, StateChangeListener listener) {
        if (listener == null) {
            return;
        }

        Set<StateChangeListener> keyListeners = listeners.get(key);
        if (keyListeners != null) {
            keyListeners.remove(listener);
            if (keyListeners.isEmpty()) {
                listeners.remove(key);
                stopWatch(key);
            }
            logger.debug("Unsubscribed from state changes for: {}", key);
        }
    }

    /**
     * Notifies all registered listeners for a specific application.
     *
     * @param applicationId the application ID
     * @param newState the new state value
     */
    private void notifyListeners(String applicationId, Object newState) {
        Set<StateChangeListener> keyListeners = listeners.get(applicationId);
        if (keyListeners != null) {
            for (StateChangeListener listener : keyListeners) {
                try {
                    listener.onStateChanged(applicationId, newState);
                } catch (Exception e) {
                    logger.error("Error notifying listener for key: {}", applicationId, e);
                }
            }
        }
    }

    /**
     * Starts a watch task for a specific key.
     *
     * @param applicationId the application ID to watch
     */
    private void startWatch(String applicationId) {
        Future<?> task = watchExecutor.scheduleWithFixedDelay(
            () -> watchKey(applicationId),
            0, 5, TimeUnit.SECONDS
        );
        watchTasks.put(applicationId, task);
        watchIndexes.put(applicationId, 0L);
        logger.debug("Started watch task for: {}", applicationId);
    }

    /**
     * Stops the watch task for a specific key.
     *
     * @param applicationId the application ID to stop watching
     */
    private void stopWatch(String applicationId) {
        Future<?> task = watchTasks.remove(applicationId);
        if (task != null) {
            task.cancel(false);
        }
        watchIndexes.remove(applicationId);
        logger.debug("Stopped watch task for: {}", applicationId);
    }

    /**
     * Watches a key for changes and notifies listeners.
     * Uses simple polling - checks the value every execution.
     *
     * @param applicationId the application ID to watch
     */
    private void watchKey(String applicationId) {
        try {
            ApplicationState currentState = getApplicationState(applicationId);
            Long lastIndex = watchIndexes.get(applicationId);

            if (currentState != null) {
                // Use a simple change detection based on value
                // Since Consul client doesn't easily expose modify index, we compare values
                long currentIndex = currentState.hashCode();

                if (lastIndex != null && lastIndex != currentIndex) {
                    // State changed, notify listeners
                    notifyListeners(applicationId, currentState);
                }

                watchIndexes.put(applicationId, currentIndex);
            }
        } catch (Exception e) {
            logger.error("Error watching key: " + applicationId, e);
        }
    }

    /**
     * Closes the state store and shuts down the watch executor.
     */
    public void close() {
        logger.info("Closing ConsulStateStore");

        // Cancel all watch tasks
        for (Future<?> task : watchTasks.values()) {
            task.cancel(false);
        }
        watchTasks.clear();
        watchIndexes.clear();

        // Shutdown watch executor
        watchExecutor.shutdown();
        try {
            if (!watchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                watchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            watchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        listeners.clear();
        logger.info("ConsulStateStore closed");
    }

    /**
     * Returns the Consul client instance.
     *
     * @return the Consul client
     */
    public Consul getConsulClient() {
        return consulClient;
    }
}
