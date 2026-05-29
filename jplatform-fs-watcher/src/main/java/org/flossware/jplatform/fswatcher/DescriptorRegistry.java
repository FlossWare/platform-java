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

package org.flossware.jplatform.fswatcher;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that tracks the mapping between descriptor file paths
 * and their deployed application IDs.
 *
 * <p>This registry is used by the auto-deployment system to maintain state
 * about which descriptor files have been deployed and what application ID
 * they correspond to. This is essential for handling modify and remove events,
 * where the system needs to know which application to redeploy or undeploy.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * DescriptorRegistry registry = new DescriptorRegistry();
 *
 * // After deploying an application
 * registry.put(descriptorPath, "my-app-id");
 *
 * // Later, when the descriptor is modified
 * String appId = registry.get(descriptorPath);
 * if (appId != null) {
 *     // Redeploy the application with this ID
 * }
 *
 * // When the descriptor is removed
 * String removedAppId = registry.remove(descriptorPath);
 * if (removedAppId != null) {
 *     // Undeploy the application
 * }
 * }</pre>
 *
 * <p>All operations are thread-safe and can be called concurrently from
 * multiple threads.</p>
 *
 * @see AutoDeploymentHandler
 * @see FileSystemDeploymentWatcher
 */
public class DescriptorRegistry {

    private final Map<Path, String> descriptorToAppId;

    /**
     * Creates a new descriptor registry.
     */
    public DescriptorRegistry() {
        this.descriptorToAppId = new ConcurrentHashMap<>();
    }

    /**
     * Associates a descriptor file path with an application ID.
     * If the path was already registered, the previous application ID is replaced.
     *
     * @param descriptorPath the path to the descriptor file
     * @param applicationId the application identifier
     * @return the previous application ID associated with the path, or null if none
     * @throws NullPointerException if descriptorPath or applicationId is null
     */
    public String put(Path descriptorPath, String applicationId) {
        if (descriptorPath == null) {
            throw new NullPointerException("descriptorPath cannot be null");
        }
        if (applicationId == null) {
            throw new NullPointerException("applicationId cannot be null");
        }
        return descriptorToAppId.put(descriptorPath, applicationId);
    }

    /**
     * Returns the application ID associated with the given descriptor path.
     *
     * @param descriptorPath the path to the descriptor file
     * @return the application ID, or null if the path is not registered
     * @throws NullPointerException if descriptorPath is null
     */
    public String get(Path descriptorPath) {
        if (descriptorPath == null) {
            throw new NullPointerException("descriptorPath cannot be null");
        }
        return descriptorToAppId.get(descriptorPath);
    }

    /**
     * Removes the mapping for the given descriptor path.
     *
     * @param descriptorPath the path to the descriptor file
     * @return the application ID that was associated with the path, or null if none
     * @throws NullPointerException if descriptorPath is null
     */
    public String remove(Path descriptorPath) {
        if (descriptorPath == null) {
            throw new NullPointerException("descriptorPath cannot be null");
        }
        return descriptorToAppId.remove(descriptorPath);
    }

    /**
     * Returns a copy of all registered mappings.
     * Changes to the returned map will not affect the registry.
     *
     * @return a new map containing all descriptor path to application ID mappings
     */
    public Map<Path, String> getAll() {
        return new ConcurrentHashMap<>(descriptorToAppId);
    }

    /**
     * Checks if the registry contains a mapping for the given descriptor path.
     *
     * @param descriptorPath the path to check
     * @return true if the path is registered, false otherwise
     * @throws NullPointerException if descriptorPath is null
     */
    public boolean contains(Path descriptorPath) {
        if (descriptorPath == null) {
            throw new NullPointerException("descriptorPath cannot be null");
        }
        return descriptorToAppId.containsKey(descriptorPath);
    }

    /**
     * Removes all mappings from the registry.
     */
    public void clear() {
        descriptorToAppId.clear();
    }

    /**
     * Returns the number of registered mappings.
     *
     * @return the number of descriptor-to-application mappings
     */
    public int size() {
        return descriptorToAppId.size();
    }
}
