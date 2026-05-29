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

package org.flossware.jplatform.messaging.jms;

import jakarta.jms.*;
import org.flossware.jplatform.api.Message;
import org.flossware.jplatform.api.MessageBus;
import org.flossware.jplatform.api.MessageHandler;
import org.flossware.jplatform.api.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JMS-backed implementation of MessageBus for distributed multi-node messaging.
 * Provides topic-based publish-subscribe messaging using a JMS broker, enabling
 * applications across multiple platform-java nodes to communicate.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Multi-node messaging via JMS broker</li>
 *   <li>Persistent messages (survive broker restarts)</li>
 *   <li>Guaranteed delivery (broker handles durability)</li>
 *   <li>Compatible with any JMS 3.0+ broker (ActiveMQ Artemis, etc.)</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Configure connection
 * JmsConfig config = JmsConfig.builder()
 *     .brokerUrl("tcp://localhost:61616")
 *     .username("admin")
 *     .password("admin")
 *     .build();
 *
 * // Create connection factory (Artemis example)
 * ConnectionFactory factory = new ActiveMQConnectionFactory(config.getBrokerUrl());
 *
 * // Create message bus
 * JmsMessageBus messageBus = new JmsMessageBus(config, factory);
 *
 * // Subscribe to messages
 * Subscription sub = messageBus.subscribe("platform.events", message -> {
 *     System.out.println("Received: " + new String(message.getPayload()));
 * });
 *
 * // Publish a message
 * Message msg = Message.builder()
 *     .topic("platform.events")
 *     .payload("Hello from node 1".getBytes())
 *     .build();
 * messageBus.publish("platform.events", msg);
 *
 * // Cleanup
 * sub.cancel();
 * messageBus.close();
 * }</pre>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Uses ConcurrentHashMap for subscriptions
 * map and CopyOnWriteArrayList for subscription lists. All JMS operations are thread-safe.</p>
 *
 * <p><b>Message Format:</b> Platform messages are serialized as JMS BytesMessage with headers:</p>
 * <ul>
 *   <li>JMSMessageID: Platform message ID</li>
 *   <li>JMSTimestamp: Platform message timestamp</li>
 *   <li>sourceApplicationId: Custom property for source app</li>
 *   <li>Custom headers: Stored as JMS properties (String keys, Object values)</li>
 *   <li>Payload: Binary data in message body</li>
 * </ul>
 *
 * @see MessageBus
 * @see JmsConfig
 * @since 1.1
 */
public class JmsMessageBus implements MessageBus, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(JmsMessageBus.class);
    private static final String SOURCE_APP_PROPERTY = "sourceApplicationId";
    private static final String HEADERS_PROPERTY = "platformHeaders";
    private static final String MESSAGE_ID_PROPERTY = "platformMessageId";
    private static final String TIMESTAMP_PROPERTY = "platformTimestamp";

    private final JmsConfig config;
    private final ConnectionFactory connectionFactory;
    private final Connection connection;
    private final Session publishSession;
    private final Map<String, CopyOnWriteArrayList<JmsSubscriptionImpl>> subscriptions;
    private volatile boolean closed = false;

    /**
     * Creates a new JMS message bus with the specified configuration and connection factory.
     *
     * @param config the JMS configuration
     * @param connectionFactory the JMS connection factory
     * @throws JMSException if connection to the broker fails
     * @throws NullPointerException if config or connectionFactory is null
     */
    public JmsMessageBus(JmsConfig config, ConnectionFactory connectionFactory) throws JMSException {
        this.config = Objects.requireNonNull(config, "JmsConfig is required");
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "ConnectionFactory is required");
        this.subscriptions = new ConcurrentHashMap<>();

        logger.info("Connecting to JMS broker: {}", config.getBrokerUrl());

        // Create connection with authentication if provided
        if (config.getUsername() != null && config.getPassword() != null) {
            this.connection = connectionFactory.createConnection(config.getUsername(), config.getPassword());
        } else {
            this.connection = connectionFactory.createConnection();
        }

        // Set client ID if provided
        if (config.getClientId() != null) {
            connection.setClientID(config.getClientId());
        }

        // Create session for publishing
        this.publishSession = connection.createSession(config.isTransacted(), config.getAcknowledgeMode());

        // Start connection to receive messages
        connection.start();

        logger.info("JMS MessageBus connected successfully");
    }

    /**
     * Publishes a message to the specified topic via JMS.
     * The message is sent to all subscribers across all nodes connected to the JMS broker.
     *
     * @param topic the topic to publish to
     * @param message the message to publish
     * @throws IllegalStateException if the message bus is closed
     * @throws NullPointerException if topic or message is null
     */
    @Override
    public void publish(String topic, Message message) {
        Objects.requireNonNull(topic, "Topic is required");
        Objects.requireNonNull(message, "Message is required");

        if (closed) {
            throw new java.lang.IllegalStateException("MessageBus is closed");
        }

        try {
            synchronized (publishSession) {
                // Create JMS topic
                Topic jmsTopic = publishSession.createTopic(topic);

                // Create message producer
                MessageProducer producer = publishSession.createProducer(jmsTopic);
                try {
                    // Create bytes message
                    BytesMessage jmsMessage = publishSession.createBytesMessage();

                    // Store platform message ID and timestamp as custom properties
                    // (Don't call setJMSMessageID/setJMSTimestamp - those are provider-only methods)
                    jmsMessage.setStringProperty(MESSAGE_ID_PROPERTY, message.getId());
                    jmsMessage.setLongProperty(TIMESTAMP_PROPERTY, message.getTimestamp());

                    // Set platform-specific properties
                    if (message.getSourceApplicationId() != null) {
                        jmsMessage.setStringProperty(SOURCE_APP_PROPERTY, message.getSourceApplicationId());
                    }

                    // Serialize headers as a map
                    if (message.getHeaders() != null && !message.getHeaders().isEmpty()) {
                        jmsMessage.setObjectProperty(HEADERS_PROPERTY, serializeHeaders(message.getHeaders()));
                    }

                    // Set payload
                    if (message.getPayload() != null) {
                        jmsMessage.writeBytes(message.getPayload());
                    }

                    // Send message
                    producer.send(jmsMessage);

                    logger.debug("Published message to topic '{}': id={}", topic, message.getId());
                } finally {
                    producer.close();
                }
            }
        } catch (JMSException | IOException e) {
            logger.error("Failed to publish message to topic '{}'", topic, e);
            throw new RuntimeException("Failed to publish message", e);
        }
    }

    /**
     * Subscribes to receive messages on the specified topic.
     * Creates a JMS MessageConsumer that receives messages from all publishers
     * across all nodes connected to the broker.
     *
     * @param topic the topic to subscribe to
     * @param handler the handler to invoke for each received message
     * @return a subscription that can be used to cancel the subscription
     * @throws IllegalStateException if the message bus is closed
     * @throws NullPointerException if topic or handler is null
     */
    @Override
    public Subscription subscribe(String topic, MessageHandler handler) {
        Objects.requireNonNull(topic, "Topic is required");
        Objects.requireNonNull(handler, "MessageHandler is required");

        if (closed) {
            throw new java.lang.IllegalStateException("MessageBus is closed");
        }

        try {
            // Create a new session for this subscription
            Session session = connection.createSession(config.isTransacted(), config.getAcknowledgeMode());

            // Create JMS topic and consumer
            Topic jmsTopic = session.createTopic(topic);
            MessageConsumer consumer = session.createConsumer(jmsTopic);

            // Create subscription wrapper
            JmsSubscriptionImpl subscription = new JmsSubscriptionImpl(
                    topic, handler, session, consumer, this);

            // Set message listener
            consumer.setMessageListener(jmsMessage -> {
                if (subscription.isActive()) {
                    try {
                        // Convert JMS message to platform message
                        Message platformMessage = fromJmsMessage(jmsMessage, topic);

                        // Invoke handler
                        handler.onMessage(platformMessage);

                    } catch (Exception e) {
                        logger.error("Error handling message on topic '{}'", topic, e);
                    }
                }
            });

            // Track subscription
            subscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                    .add(subscription);

            logger.info("Subscribed to topic '{}'. Total local subscriptions: {}",
                    topic, subscriptions.get(topic).size());

            return subscription;

        } catch (JMSException e) {
            logger.error("Failed to subscribe to topic '{}'", topic, e);
            throw new RuntimeException("Failed to subscribe", e);
        }
    }

    /**
     * Unsubscribes the specified subscription.
     * Closes the JMS MessageConsumer and session associated with the subscription.
     *
     * @param subscription the subscription to cancel
     */
    @Override
    public void unsubscribe(Subscription subscription) {
        if (subscription instanceof JmsSubscriptionImpl) {
            ((JmsSubscriptionImpl) subscription).cancel();
        }
    }

    /**
     * Internal method to remove a subscription from the subscriptions map.
     * Called by JmsSubscriptionImpl when cancelled.
     *
     * @param subscription the subscription to remove
     */
    void removeSubscription(JmsSubscriptionImpl subscription) {
        CopyOnWriteArrayList<JmsSubscriptionImpl> topicSubscriptions =
                subscriptions.get(subscription.getTopic());

        if (topicSubscriptions != null) {
            topicSubscriptions.remove(subscription);
            logger.info("Unsubscribed from topic '{}'. Remaining local subscriptions: {}",
                    subscription.getTopic(), topicSubscriptions.size());
        }
    }

    /**
     * Closes the JMS message bus and releases all resources.
     * Cancels all subscriptions, closes all sessions, and closes the connection.
     *
     * @throws JMSException if an error occurs during shutdown
     */
    @Override
    public void close() throws JMSException {
        if (closed) {
            return;
        }

        logger.info("Closing JMS MessageBus");
        closed = true;

        // Cancel all subscriptions
        for (CopyOnWriteArrayList<JmsSubscriptionImpl> topicSubs : subscriptions.values()) {
            for (JmsSubscriptionImpl sub : topicSubs) {
                try {
                    sub.cancel();
                } catch (Exception e) {
                    logger.warn("Error cancelling subscription", e);
                }
            }
        }
        subscriptions.clear();

        // Close publish session
        if (publishSession != null) {
            try {
                publishSession.close();
            } catch (JMSException e) {
                logger.warn("Error closing publish session", e);
            }
        }

        // Close connection
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                logger.warn("Error closing connection", e);
            }
        }

        logger.info("JMS MessageBus closed");
    }

    /**
     * Checks if the message bus is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Converts a JMS message to a platform Message.
     *
     * @param jmsMessage the JMS message
     * @param topic the topic name
     * @return the platform message
     * @throws JMSException if an error occurs reading the message
     * @throws IOException if header deserialization fails
     * @throws ClassNotFoundException if the serialized header class cannot be found
     */
    private Message fromJmsMessage(jakarta.jms.Message jmsMessage, String topic) throws JMSException, IOException, ClassNotFoundException {
        if (!(jmsMessage instanceof BytesMessage)) {
            throw new IllegalArgumentException("Expected BytesMessage, got " + jmsMessage.getClass());
        }

        BytesMessage bytesMessage = (BytesMessage) jmsMessage;

        // Read platform message ID and timestamp from custom properties
        String id = jmsMessage.getStringProperty(MESSAGE_ID_PROPERTY);
        long timestamp = jmsMessage.getLongProperty(TIMESTAMP_PROPERTY);

        // If not present (backward compatibility with old messages), fall back to JMS provider values
        if (id == null) {
            id = jmsMessage.getJMSMessageID();
        }
        if (timestamp == 0) {
            timestamp = jmsMessage.getJMSTimestamp();
        }

        // Read source application ID
        String sourceAppId = jmsMessage.getStringProperty(SOURCE_APP_PROPERTY);

        // Read headers
        Map<String, Object> headers = null;
        byte[] headersBytes = (byte[]) jmsMessage.getObjectProperty(HEADERS_PROPERTY);
        if (headersBytes != null) {
            headers = deserializeHeaders(headersBytes);
        }

        // Read payload
        byte[] payload = null;
        long bodyLength = bytesMessage.getBodyLength();
        if (bodyLength > 0) {
            if (bodyLength > Integer.MAX_VALUE) {
                throw new java.lang.IllegalStateException(
                    "Message body too large: " + bodyLength + " bytes (max: " + Integer.MAX_VALUE + ")"
                );
            }
            payload = new byte[(int) bodyLength];
            bytesMessage.readBytes(payload);
        }

        // Build platform message
        Message.Builder builder = Message.builder()
                .id(id)
                .topic(topic)
                .timestamp(timestamp)
                .sourceApplicationId(sourceAppId)
                .payload(payload);

        if (headers != null) {
            builder.headers(headers);
        }

        return builder.build();
    }

    /**
     * Serializes headers map to byte array.
     *
     * @param headers the headers map
     * @return serialized headers
     * @throws IOException if serialization fails
     */
    private byte[] serializeHeaders(Map<String, Object> headers) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(new HashMap<>(headers));
            return baos.toByteArray();
        }
    }

    /**
     * Deserializes byte array to headers map.
     *
     * @param bytes the serialized headers
     * @return the headers map
     * @throws IOException if deserialization fails
     * @throws ClassNotFoundException if the serialized class cannot be found
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeHeaders(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Map<String, Object>) ois.readObject();
        }
    }

    /**
     * JMS-backed Subscription implementation.
     */
    private static class JmsSubscriptionImpl implements Subscription {
        private final String id;
        private final String topic;
        private final MessageHandler handler;
        private final Session session;
        private final MessageConsumer consumer;
        private final JmsMessageBus bus;
        private volatile boolean active = true;

        JmsSubscriptionImpl(String topic, MessageHandler handler, Session session,
                            MessageConsumer consumer, JmsMessageBus bus) {
            this.id = UUID.randomUUID().toString();
            this.topic = topic;
            this.handler = handler;
            this.session = session;
            this.consumer = consumer;
            this.bus = bus;
        }

        @Override
        public String getTopic() {
            return topic;
        }

        @Override
        public void cancel() {
            if (!active) {
                return;
            }

            active = false;
            bus.removeSubscription(this);

            try {
                if (consumer != null) {
                    consumer.close();
                }
            } catch (JMSException e) {
                logger.warn("Error closing consumer", e);
            }

            try {
                if (session != null) {
                    session.close();
                }
            } catch (JMSException e) {
                logger.warn("Error closing session", e);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }

        public String getId() {
            return id;
        }

        public MessageHandler getHandler() {
            return handler;
        }
    }
}
