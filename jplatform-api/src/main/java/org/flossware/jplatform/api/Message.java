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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Message for inter-application communication via the message bus.
 *
 * <p>Messages contain:</p>
 * <ul>
 *   <li>Unique ID (auto-generated if not provided)</li>
 *   <li>Topic for routing</li>
 *   <li>Source application ID</li>
 *   <li>Custom headers (metadata)</li>
 *   <li>Binary payload (content)</li>
 *   <li>Timestamp (auto-generated if not provided)</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>{@code
 * Message msg = Message.builder()
 *     .topic("events")
 *     .sourceApplicationId("app1")
 *     .payload("Hello".getBytes())
 *     .header("priority", "high")
 *     .build();
 * }</pre>
 *
 * @see MessageBus
 * @see MessageHandler
 */
public class Message {
    private final String id;
    private final String topic;
    private final String sourceApplicationId;
    private final Map<String, Object> headers;
    private final byte[] payload;
    private final long timestamp;

    private Message(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.topic = builder.topic;
        this.sourceApplicationId = builder.sourceApplicationId;

        // Validate and copy headers
        if (builder.headers != null) {
            for (Map.Entry<String, Object> entry : builder.headers.entrySet()) {
                if (entry.getKey() == null) {
                    throw new IllegalArgumentException("Message headers cannot contain null keys");
                }
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException(
                        "Message headers cannot contain null values (key: " + entry.getKey() + ")");
                }
            }
            this.headers = new HashMap<>(builder.headers);
        } else {
            this.headers = Collections.emptyMap();
        }

        this.payload = builder.payload != null ?
                Arrays.copyOf(builder.payload, builder.payload.length) : null;
        this.timestamp = builder.timestamp > 0 ? builder.timestamp : System.currentTimeMillis();
    }

    /**
     * Returns the message identifier.
     *
     * @return the message ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the message topic.
     *
     * @return the topic name
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Returns the source application identifier.
     *
     * @return the ID of the sending application
     */
    public String getSourceApplicationId() {
        return sourceApplicationId;
    }

    /**
     * Returns the message headers.
     *
     * @return an unmodifiable map of headers
     */
    public Map<String, Object> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Returns the message payload.
     *
     * @return a defensive copy of the binary message content
     */
    public byte[] getPayload() {
        return payload != null ? Arrays.copyOf(payload, payload.length) : null;
    }

    /**
     * Returns the message timestamp.
     *
     * @return the timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Creates a new builder for constructing messages.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing Message instances.
     * ID and timestamp are auto-generated if not provided.
     */
    public static class Builder {
        private String id;
        private String topic;
        private String sourceApplicationId;
        private Map<String, Object> headers;
        private byte[] payload;
        private long timestamp;

        /**
         * Sets the message ID.
         * Auto-generated if not set.
         *
         * @param id the message identifier
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the message topic for routing.
         *
         * @param topic the topic name
         * @return this builder
         */
        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        /**
         * Sets the source application identifier.
         *
         * @param sourceApplicationId the ID of the sending application
         * @return this builder
         */
        public Builder sourceApplicationId(String sourceApplicationId) {
            this.sourceApplicationId = sourceApplicationId;
            return this;
        }

        /**
         * Sets the complete map of message headers.
         * Replaces any previously added headers.
         * Makes a defensive copy to prevent external modifications.
         *
         * @param headers the headers map
         * @return this builder
         * @throws IllegalArgumentException if headers contain null keys or values
         */
        public Builder headers(Map<String, Object> headers) {
            if (headers != null) {
                for (Map.Entry<String, Object> entry : headers.entrySet()) {
                    if (entry.getKey() == null) {
                        throw new IllegalArgumentException("Message headers cannot contain null keys");
                    }
                    if (entry.getValue() == null) {
                        throw new IllegalArgumentException(
                            "Message headers cannot contain null values (key: " + entry.getKey() + ")");
                    }
                }
            }
            this.headers = headers != null ? new HashMap<>(headers) : null;
            return this;
        }

        /**
         * Adds a single message header.
         * Can be called multiple times to build headers incrementally.
         *
         * @param key the header key
         * @param value the header value
         * @return this builder
         * @throws IllegalArgumentException if key or value is null
         */
        public Builder header(String key, Object value) {
            if (key == null) {
                throw new IllegalArgumentException("Message header key cannot be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("Message header value cannot be null");
            }
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.put(key, value);
            return this;
        }

        /**
         * Sets the message payload.
         * Makes a defensive copy to prevent external modifications.
         *
         * @param payload the binary message content
         * @return this builder
         */
        public Builder payload(byte[] payload) {
            this.payload = payload != null ? Arrays.copyOf(payload, payload.length) : null;
            return this;
        }

        /**
         * Sets the message timestamp.
         * Auto-generated to current time if not set.
         *
         * @param timestamp the timestamp in milliseconds since epoch
         * @return this builder
         */
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Builds the Message instance.
         * Auto-generates ID and timestamp if not provided.
         *
         * @return a new Message with the configured values
         * @throws IllegalStateException if topic or sourceApplicationId is not set
         */
        public Message build() {
            if (topic == null || topic.trim().isEmpty()) {
                throw new IllegalStateException("Message topic is required");
            }
            if (sourceApplicationId == null || sourceApplicationId.trim().isEmpty()) {
                throw new IllegalStateException("Message sourceApplicationId is required");
            }
            return new Message(this);
        }
    }
}
