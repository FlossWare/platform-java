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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hazelcast-based implementation of ClusterStateStore.
 * Provides distributed storage for application state and descriptors
 * using Hazelcast's distributed maps (IMap).
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Distributed storage with automatic replication</li>
 *   <li>Event notifications on state changes</li>
 *   <li>JSON serialization for ApplicationDescriptor</li>
 *   <li>Thread-safe concurrent access</li>
 * </ul>
 *
 * <p>Uses two Hazelcast IMaps:</p>
 * <ul>
 *   <li>"jplatform-application-states" - stores ApplicationState by ID</li>
 *   <li>"jplatform-application-descriptors" - stores serialized ApplicationDescriptor by ID</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * HazelcastStateStore store = new HazelcastStateStore(hazelcastInstance);
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
 * @see HazelcastClusterManager
 */
public class HazelcastStateStore implements ClusterStateStore {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastStateStore.class);
    private static final String STATE_MAP_NAME = "jplatform-application-states";
    private static final String DESCRIPTOR_MAP_NAME = "jplatform-application-descriptors";

    private final HazelcastInstance hazelcast;
    private final IMap<String, ApplicationState> stateMap;
    private final IMap<String, String> descriptorMap;
    private final ObjectMapper objectMapper;
    private final Map<String, Map<StateChangeListener, UUID>> listenerRegistry;

    /**
     * Constructs a new Hazelcast state store.
     *
     * @param hazelcast the Hazelcast instance to use for distributed storage
     */
    public HazelcastStateStore(HazelcastInstance hazelcast) {
        this.hazelcast = hazelcast;
        this.stateMap = hazelcast.getMap(STATE_MAP_NAME);
        this.descriptorMap = hazelcast.getMap(DESCRIPTOR_MAP_NAME);
        this.objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore failures accessing private fields
        // This is needed for Java 11+ module system restrictions
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Register custom module for ApplicationDescriptor serialization/deserialization
        // This handles the Builder pattern and excludes non-serializable fields
        this.objectMapper.registerModule(new ApplicationDescriptorJsonModule());
        this.listenerRegistry = new ConcurrentHashMap<>();

        logger.info("HazelcastStateStore initialized with maps: {}, {}",
                STATE_MAP_NAME, DESCRIPTOR_MAP_NAME);
    }

    /**
     * Stores application state in the distributed map.
     * The state is replicated across all cluster nodes.
     *
     * @param applicationId the application identifier
     * @param state the application state to store
     */
    @Override
    public void putApplicationState(String applicationId, ApplicationState state) {
        logger.debug("Storing application state: {} -> {}", applicationId, state);
        stateMap.put(applicationId, state);
    }

    /**
     * Retrieves application state from the distributed map.
     *
     * @param applicationId the application identifier
     * @return the application state, or null if not found
     */
    @Override
    public ApplicationState getApplicationState(String applicationId) {
        return stateMap.get(applicationId);
    }

    /**
     * Returns all application states in the cluster.
     * Creates a local snapshot of the distributed map.
     *
     * @return a map of application ID to state
     */
    @Override
    public Map<String, ApplicationState> getAllApplicationStates() {
        return new HashMap<>(stateMap);
    }

    /**
     * Stores an application descriptor in the distributed map.
     * The descriptor is serialized to JSON before storage.
     *
     * @param applicationId the application identifier
     * @param descriptor the application descriptor to store
     */
    @Override
    public void putApplicationDescriptor(String applicationId, ApplicationDescriptor descriptor) {
        try {
            String json = objectMapper.writeValueAsString(descriptor);
            logger.debug("Storing application descriptor: {} (size: {} bytes)",
                    applicationId, json.length());
            descriptorMap.put(applicationId, json);
        } catch (Exception e) {
            logger.error("Failed to serialize application descriptor: {}", applicationId, e);
            throw new RuntimeException("Failed to serialize application descriptor", e);
        }
    }

    /**
     * Retrieves an application descriptor from the distributed map.
     * The descriptor is deserialized from JSON.
     *
     * @param applicationId the application identifier
     * @return the application descriptor, or null if not found
     */
    @Override
    public ApplicationDescriptor getApplicationDescriptor(String applicationId) {
        String json = descriptorMap.get(applicationId);
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, ApplicationDescriptor.class);
        } catch (Exception e) {
            logger.error("Failed to deserialize application descriptor: {}", applicationId, e);
            throw new RuntimeException("Failed to deserialize application descriptor", e);
        }
    }

    /**
     * Returns all application descriptors in the cluster.
     * Creates a local snapshot and deserializes all descriptors.
     *
     * @return a map of application ID to descriptor
     * @throws RuntimeException if any descriptor fails to deserialize
     */
    @Override
    public Map<String, ApplicationDescriptor> getAllApplicationDescriptors() {
        Map<String, ApplicationDescriptor> result = new HashMap<>();

        for (Map.Entry<String, String> entry : descriptorMap.entrySet()) {
            try {
                ApplicationDescriptor descriptor = objectMapper.readValue(
                        entry.getValue(), ApplicationDescriptor.class);
                result.put(entry.getKey(), descriptor);
            } catch (Exception e) {
                logger.error("Failed to deserialize descriptor for: {}", entry.getKey(), e);
                throw new RuntimeException(
                    "Failed to deserialize application descriptor for " + entry.getKey() +
                    " - cluster state may be corrupted", e);
            }
        }

        return result;
    }

    /**
     * Subscribes to state changes for a specific application.
     * The listener will be notified whenever the state changes in the distributed map.
     *
     * @param key the application ID to watch
     * @param listener the listener to notify on changes
     */
    @Override
    public void subscribe(String key, StateChangeListener listener) {
        if (listener == null) {
            return;
        }

        UUID listenerId = stateMap.addEntryListener(
                new StateMapListener(listener),
                key,
                true
        );

        listenerRegistry.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .put(listener, listenerId);

        logger.debug("Subscribed to state changes for: {}", key);
    }

    /**
     * Unsubscribes from state changes for a specific application.
     *
     * @param key the application ID to stop watching
     * @param listener the listener to remove
     */
    @Override
    public void unsubscribe(String key, StateChangeListener listener) {
        if (listener == null) {
            return;
        }

        Map<StateChangeListener, UUID> listeners = listenerRegistry.get(key);
        if (listeners != null) {
            UUID listenerId = listeners.remove(listener);
            if (listenerId != null) {
                stateMap.removeEntryListener(listenerId);
                logger.debug("Unsubscribed from state changes for: {}", key);
            }
        }
    }

    /**
     * Internal listener that adapts Hazelcast entry events to StateChangeListener.
     */
    private class StateMapListener implements
            EntryAddedListener<String, ApplicationState>,
            EntryUpdatedListener<String, ApplicationState>,
            EntryRemovedListener<String, ApplicationState> {

        private final StateChangeListener listener;

        public StateMapListener(StateChangeListener listener) {
            this.listener = listener;
        }

        @Override
        public void entryAdded(EntryEvent<String, ApplicationState> event) {
            try {
                listener.onStateChanged(event.getKey(), event.getValue());
            } catch (Exception e) {
                logger.error("Error in state change listener for key: {}", event.getKey(), e);
            }
        }

        @Override
        public void entryUpdated(EntryEvent<String, ApplicationState> event) {
            try {
                listener.onStateChanged(event.getKey(), event.getValue());
            } catch (Exception e) {
                logger.error("Error in state change listener for key: {}", event.getKey(), e);
            }
        }

        @Override
        public void entryRemoved(EntryEvent<String, ApplicationState> event) {
            try {
                listener.onStateChanged(event.getKey(), null);
            } catch (Exception e) {
                logger.error("Error in state change listener for key: {}", event.getKey(), e);
            }
        }
    }
}
