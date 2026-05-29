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
 * Functional interface for handling messages received from the message bus.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * MessageHandler handler = message -> {
 *     String payload = new String(message.getPayload());
 *     System.out.println("Received: " + payload);
 * };
 *
 * messageBus.subscribe("my-topic", handler);
 * }</pre>
 *
 * @see MessageBus
 * @see Message
 */
@FunctionalInterface
public interface MessageHandler {
    /**
     * Called when a message is received on a subscribed topic.
     *
     * @param message the received message
     */
    void onMessage(Message message);
}
