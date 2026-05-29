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

package org.flossware.jplatform.api;

import java.util.Map;

/**
 * Distributed state store for cluster-wide application state.
 * Provides synchronized access to application descriptors and state
 * across all nodes in the cluster.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ClusterStateStore store = cluster.getStateStore();
 *
 * // Store application descriptor
 * store.putApplicationDescriptor("my-app", descriptor);
 *
 * // Update application state
 * store.putApplicationState("my-app", ApplicationState.RUNNING);
 *
 * // Subscribe to state changes
 * store.subscribe("apps.my-app.state", (key, value) -> {
 *     System.out.println("State changed: " + value);
 * });
 * }</pre>
 *
 * @see ClusterManager
 */
public interface ClusterStateStore {

    /**
     * Stores application state in the distributed store.
     *
     * @param applicationId the application identifier
     * @param state the application state
     */
    void putApplicationState(String applicationId, ApplicationState state);

    /**
     * Retrieves application state from the distributed store.
     *
     * @param applicationId the application identifier
     * @return the application state, or null if not found
     */
    ApplicationState getApplicationState(String applicationId);

    /**
     * Returns all application states in the cluster.
     *
     * @return a map of application ID to state
     */
    Map<String, ApplicationState> getAllApplicationStates();

    /**
     * Stores an application descriptor in the distributed store.
     *
     * @param applicationId the application identifier
     * @param descriptor the application descriptor
     */
    void putApplicationDescriptor(String applicationId, ApplicationDescriptor descriptor);

    /**
     * Retrieves an application descriptor from the distributed store.
     *
     * @param applicationId the application identifier
     * @return the application descriptor, or null if not found
     */
    ApplicationDescriptor getApplicationDescriptor(String applicationId);

    /**
     * Returns all application descriptors in the cluster.
     *
     * @return a map of application ID to descriptor
     */
    Map<String, ApplicationDescriptor> getAllApplicationDescriptors();

    /**
     * Subscribes to changes for a specific key.
     *
     * @param key the key to watch
     * @param listener the listener to notify on changes
     */
    void subscribe(String key, StateChangeListener listener);

    /**
     * Unsubscribes from changes for a specific key.
     *
     * @param key the key to stop watching
     * @param listener the listener to remove
     */
    void unsubscribe(String key, StateChangeListener listener);

    /**
     * Listener for state changes in the distributed store.
     */
    @FunctionalInterface
    interface StateChangeListener {
        /**
         * Called when a state value changes.
         *
         * @param key the key that changed
         * @param value the new value
         */
        void onStateChanged(String key, Object value);
    }
}
