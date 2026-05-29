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
 * Listener for cluster membership events.
 * Notified when nodes join, leave, or when leadership changes.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ClusterEventListener listener = new ClusterEventListener() {
 *     @Override
 *     public void onNodeJoined(ClusterNode node) {
 *         System.out.println("Node joined: " + node.getNodeId());
 *     }
 *
 *     @Override
 *     public void onLeaderChanged(ClusterNode newLeader) {
 *         System.out.println("New leader: " + newLeader.getNodeId());
 *     }
 * };
 * }</pre>
 *
 * @see ClusterManager
 * @see ClusterNode
 */
public interface ClusterEventListener {

    /**
     * Called when a node joins the cluster.
     *
     * @param node the node that joined
     */
    void onNodeJoined(ClusterNode node);

    /**
     * Called when a node leaves the cluster.
     *
     * @param node the node that left
     */
    void onNodeLeft(ClusterNode node);

    /**
     * Called when cluster leadership changes.
     *
     * @param newLeader the new leader node
     */
    void onLeaderChanged(ClusterNode newLeader);
}
