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

/**
 * Thrown when a node fails to join a cluster.
 * This exception indicates failures during cluster initialization,
 * seed node connection, or network configuration issues.
 *
 * @since 1.2
 */
public class ClusterJoinException extends PlatformException {

    private final String clusterName;

    /**
     * Constructs a new cluster join exception.
     *
     * @param clusterName the name of the cluster
     * @param message the detail message
     */
    public ClusterJoinException(String clusterName, String message) {
        super(message);
        this.clusterName = clusterName;
    }

    /**
     * Constructs a new cluster join exception with a cause.
     *
     * @param clusterName the name of the cluster
     * @param message the detail message
     * @param cause the underlying cause
     */
    public ClusterJoinException(String clusterName, String message, Throwable cause) {
        super(message, cause);
        this.clusterName = clusterName;
    }

    /**
     * Returns the name of the cluster.
     *
     * @return the cluster name
     */
    public String getClusterName() {
        return clusterName;
    }
}
