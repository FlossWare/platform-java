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

import java.util.Collections;
import java.util.List;

/**
 * Immutable collection of resource usage snapshots over time.
 * Useful for tracking resource usage trends and analyzing application behavior.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ResourceUsageHistory history = monitor.getHistory();
 *
 * // Analyze resource usage trends
 * for (ResourceSnapshot snapshot : history.getSnapshots()) {
 *     System.out.println(snapshot.getTimestamp() + ": " +
 *                       snapshot.getHeapUsedBytes() + " bytes");
 * }
 *
 * // Check if any data collected
 * if (!history.isEmpty()) {
 *     System.out.println("Collected " + history.size() + " snapshots");
 * }
 * }</pre>
 *
 * @see ResourceSnapshot
 * @see ResourceMonitor
 */
public class ResourceUsageHistory {
    private final List<ResourceSnapshot> snapshots;

    /**
     * Constructs a new resource usage history with the given snapshots.
     * The snapshot list is copied to ensure immutability.
     *
     * @param snapshots the list of resource snapshots, or null for an empty history
     * @throws IllegalArgumentException if snapshots list contains null elements
     */
    public ResourceUsageHistory(List<ResourceSnapshot> snapshots) {
        if (snapshots == null) {
            this.snapshots = Collections.emptyList();
            return;
        }

        // Validate no null elements
        for (int i = 0; i < snapshots.size(); i++) {
            if (snapshots.get(i) == null) {
                throw new IllegalArgumentException(
                    "snapshots list cannot contain null elements (index " + i + ")");
            }
        }

        this.snapshots = List.copyOf(snapshots);
    }

    /**
     * Returns all resource snapshots in this history.
     *
     * @return an unmodifiable list of snapshots in chronological order
     */
    public List<ResourceSnapshot> getSnapshots() {
        return snapshots;
    }

    /**
     * Returns the number of snapshots in this history.
     *
     * @return the snapshot count
     */
    public int size() {
        return snapshots.size();
    }

    /**
     * Checks if this history contains any snapshots.
     *
     * @return true if no snapshots are recorded, false otherwise
     */
    public boolean isEmpty() {
        return snapshots.isEmpty();
    }
}
