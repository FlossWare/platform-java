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
import io.etcd.jetcd.kv.GetResponse;
import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * etcd-based implementation of ClusterStateStore.
 * Stores distributed application state and descriptors using etcd key-value store.
 *
 * <p>This implementation uses:
 * <ul>
 *   <li>etcd KV for storing application states</li>
 *   <li>etcd KV for storing application descriptors</li>
 *   <li>JSON serialization for complex objects</li>
 *   <li>Key prefixes for data organization</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections.
 *
 * @since 1.1
 */
public class EtcdStateStore implements ClusterStateStore {
    private static final Logger logger = LoggerFactory.getLogger(EtcdStateStore.class);
    private static final String STATE_KEY_PREFIX = "/jplatform/states/";
    private static final String DESCRIPTOR_KEY_PREFIX = "/jplatform/descriptors/";

    private final Client client;
    private final ObjectMapper mapper;
    private final Map<String, List<StateChangeListener>> listeners;

    /**
     * Constructs a new etcd state store.
     *
     * @param client the etcd client
     * @throws IllegalArgumentException if client is null
     */
    public EtcdStateStore(Client client) {
        if (client == null) {
            throw new IllegalArgumentException("Client must not be null");
        }
        this.client = client;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new Jdk8Module());  // Support Optional types
        this.listeners = new ConcurrentHashMap<>();
    }

    @Override
    public void putApplicationState(String id, ApplicationState state) {
        if (id == null || state == null) {
            logger.warn("Cannot put null id or state");
            return;
        }

        try {
            KV kvClient = client.getKVClient();
            String json = mapper.writeValueAsString(state);
            String key = STATE_KEY_PREFIX + id;

            kvClient.put(
                ByteSequence.from(key, StandardCharsets.UTF_8),
                ByteSequence.from(json, StandardCharsets.UTF_8)
            ).get();

            notifyListeners(id, state);
        } catch (Exception e) {
            logger.error("Failed to put application state for " + id, e);
        }
    }

    @Override
    public ApplicationState getApplicationState(String id) {
        if (id == null) {
            return null;
        }

        try {
            KV kvClient = client.getKVClient();
            String key = STATE_KEY_PREFIX + id;

            GetResponse response = kvClient.get(
                ByteSequence.from(key, StandardCharsets.UTF_8)
            ).get();

            if (response.getKvs().isEmpty()) {
                return null;
            }

            String json = response.getKvs().get(0).getValue().toString(StandardCharsets.UTF_8);
            return mapper.readValue(json, ApplicationState.class);
        } catch (Exception e) {
            logger.error("Failed to get application state for " + id, e);
            return null;
        }
    }

    @Override
    public Map<String, ApplicationState> getAllApplicationStates() {
        Map<String, ApplicationState> states = new HashMap<>();
        try {
            KV kvClient = client.getKVClient();
            ByteSequence prefix = ByteSequence.from(STATE_KEY_PREFIX, StandardCharsets.UTF_8);

            GetResponse response = kvClient.get(prefix,
                io.etcd.jetcd.options.GetOption.newBuilder()
                    .isPrefix(true)
                    .build()
            ).get();

            for (KeyValue kv : response.getKvs()) {
                try {
                    String key = kv.getKey().toString(StandardCharsets.UTF_8);
                    String id = key.substring(STATE_KEY_PREFIX.length());
                    String json = kv.getValue().toString(StandardCharsets.UTF_8);
                    ApplicationState state = mapper.readValue(json, ApplicationState.class);
                    states.put(id, state);
                } catch (Exception e) {
                    logger.error("Failed to deserialize state for key: {}", kv.getKey(), e);
                    throw new RuntimeException(
                        "Failed to deserialize application state - cluster state may be corrupted", e);
                }
            }
        } catch (RuntimeException e) {
            throw e;  // Re-throw deserialization failures
        } catch (Exception e) {
            logger.error("Failed to get all application states", e);
            throw new RuntimeException("Failed to retrieve application states from etcd", e);
        }
        return states;
    }

    @Override
    public void putApplicationDescriptor(String id, ApplicationDescriptor desc) {
        if (id == null || desc == null) {
            logger.warn("Cannot put null id or descriptor");
            return;
        }

        try {
            KV kvClient = client.getKVClient();
            String json = mapper.writeValueAsString(desc);
            String key = DESCRIPTOR_KEY_PREFIX + id;

            kvClient.put(
                ByteSequence.from(key, StandardCharsets.UTF_8),
                ByteSequence.from(json, StandardCharsets.UTF_8)
            ).get();
        } catch (Exception e) {
            logger.error("Failed to put application descriptor for " + id, e);
        }
    }

    @Override
    public ApplicationDescriptor getApplicationDescriptor(String id) {
        if (id == null) {
            return null;
        }

        try {
            KV kvClient = client.getKVClient();
            String key = DESCRIPTOR_KEY_PREFIX + id;

            GetResponse response = kvClient.get(
                ByteSequence.from(key, StandardCharsets.UTF_8)
            ).get();

            if (response.getKvs().isEmpty()) {
                return null;
            }

            String json = response.getKvs().get(0).getValue().toString(StandardCharsets.UTF_8);
            return mapper.readValue(json, ApplicationDescriptor.class);
        } catch (Exception e) {
            logger.error("Failed to get application descriptor for " + id, e);
            return null;
        }
    }

    @Override
    public Map<String, ApplicationDescriptor> getAllApplicationDescriptors() {
        Map<String, ApplicationDescriptor> descriptors = new HashMap<>();
        try {
            KV kvClient = client.getKVClient();
            ByteSequence prefix = ByteSequence.from(DESCRIPTOR_KEY_PREFIX, StandardCharsets.UTF_8);

            GetResponse response = kvClient.get(prefix,
                io.etcd.jetcd.options.GetOption.newBuilder()
                    .isPrefix(true)
                    .build()
            ).get();

            for (KeyValue kv : response.getKvs()) {
                try {
                    String key = kv.getKey().toString(StandardCharsets.UTF_8);
                    String id = key.substring(DESCRIPTOR_KEY_PREFIX.length());
                    String json = kv.getValue().toString(StandardCharsets.UTF_8);
                    ApplicationDescriptor desc = mapper.readValue(json, ApplicationDescriptor.class);
                    descriptors.put(id, desc);
                } catch (Exception e) {
                    logger.error("Failed to deserialize descriptor for key: {}", kv.getKey(), e);
                    throw new RuntimeException(
                        "Failed to deserialize application descriptor - cluster state may be corrupted", e);
                }
            }
        } catch (RuntimeException e) {
            throw e;  // Re-throw deserialization failures
        } catch (Exception e) {
            logger.error("Failed to get all application descriptors", e);
            throw new RuntimeException("Failed to retrieve application descriptors from etcd", e);
        }
        return descriptors;
    }

    @Override
    public void subscribe(String key, StateChangeListener listener) {
        if (key == null || listener == null) {
            logger.warn("Cannot subscribe with null key or listener");
            return;
        }
        listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void unsubscribe(String key, StateChangeListener listener) {
        if (key == null || listener == null) {
            return;
        }
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
            KV kvClient = client.getKVClient();

            // Delete all states
            ByteSequence statePrefix = ByteSequence.from(STATE_KEY_PREFIX, StandardCharsets.UTF_8);
            kvClient.delete(statePrefix,
                io.etcd.jetcd.options.DeleteOption.newBuilder()
                    .isPrefix(true)
                    .build()
            ).get();

            // Delete all descriptors
            ByteSequence descPrefix = ByteSequence.from(DESCRIPTOR_KEY_PREFIX, StandardCharsets.UTF_8);
            kvClient.delete(descPrefix,
                io.etcd.jetcd.options.DeleteOption.newBuilder()
                    .isPrefix(true)
                    .build()
            ).get();
        } catch (Exception e) {
            logger.error("Failed to clear state store", e);
        }
    }
}
