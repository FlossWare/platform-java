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
 * Represents an active subscription to a message bus topic.
 * Use to manage the subscription lifecycle and check subscription status.
 *
 * <p>Example:</p>
 * <pre>{@code
 * Subscription sub = messageBus.subscribe("events", msg -> {...});
 * // ... receive messages ...
 * sub.cancel();  // Stop receiving messages
 * }</pre>
 *
 * @see MessageBus
 */
public interface Subscription {
    /**
     * Returns the topic this subscription is listening to.
     *
     * @return the topic name
     */
    String getTopic();

    /**
     * Cancels this subscription, stopping all future message delivery.
     * After calling this method, {@link #isActive()} will return false.
     */
    void cancel();

    /**
     * Checks if this subscription is still active and receiving messages.
     *
     * @return true if active, false if cancelled
     */
    boolean isActive();
}
