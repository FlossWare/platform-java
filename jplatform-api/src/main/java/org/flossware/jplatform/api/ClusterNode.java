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

import java.util.Objects;

/**
 * Represents a node in the platform cluster.
 * Contains information about node identity, location, and health status.
 *
 * @see ClusterManager
 */
public class ClusterNode {
    private final String nodeId;
    private final String address;
    private final int port;
    private final NodeState state;
    private final long lastHeartbeat;

    /**
     * Constructs a new cluster node.
     *
     * @param nodeId the unique node identifier
     * @param address the node network address
     * @param port the node port
     * @param state the node state
     * @param lastHeartbeat the last heartbeat timestamp
     * @throws IllegalArgumentException if nodeId or address is null/empty, port is invalid, or lastHeartbeat is negative
     * @throws NullPointerException if state is null
     */
    public ClusterNode(String nodeId, String address, int port, NodeState state, long lastHeartbeat) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Node ID cannot be null or empty");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Address cannot be null or empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535, got: " + port);
        }
        if (lastHeartbeat < 0) {
            throw new IllegalArgumentException("lastHeartbeat cannot be negative: " + lastHeartbeat);
        }

        this.nodeId = nodeId;
        this.address = address;
        this.port = port;
        this.state = Objects.requireNonNull(state, "Node state cannot be null");
        this.lastHeartbeat = lastHeartbeat;
    }

    /**
     * Returns the unique node identifier.
     *
     * @return the node ID
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Returns the node network address.
     *
     * @return the address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Returns the node port.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the current node state.
     *
     * @return the node state
     */
    public NodeState getState() {
        return state;
    }

    /**
     * Returns the last heartbeat timestamp.
     *
     * @return the timestamp in milliseconds since epoch
     */
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    @Override
    public String toString() {
        return String.format("ClusterNode{nodeId='%s', address='%s', port=%d, state=%s}",
                nodeId, address, port, state);
    }

    /**
     * Node states in the cluster lifecycle.
     */
    public enum NodeState {
        /** Node is joining the cluster */
        JOINING,
        /** Node is active and healthy */
        ACTIVE,
        /** Node is suspected to be unhealthy */
        SUSPECT,
        /** Node is leaving the cluster */
        LEAVING,
        /** Node is dead or unreachable */
        DEAD
    }
}
