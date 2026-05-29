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

package org.flossware.jplatform.messaging;

import org.flossware.jplatform.api.Message;
import org.flossware.jplatform.api.MessageBus;
import org.flossware.jplatform.api.MessageHandler;
import org.flossware.jplatform.api.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory message bus for inter-application communication.
 * Uses asynchronous dispatch to prevent blocking publishers.
 * <p>
 * This implementation provides a topic-based publish-subscribe messaging system
 * where publishers can send messages to topics and subscribers receive messages
 * from topics they're interested in. Message delivery is asynchronous and uses
 * a fixed thread pool to dispatch messages to handlers.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe. Uses ConcurrentHashMap for subscribers
 * map and CopyOnWriteArrayList for subscriber lists. All operations are thread-safe.
 * <p>
 * Example usage:
 * {@code
 * InMemoryMessageBus messageBus = new InMemoryMessageBus();
 *
 * // Subscribe to a topic
 * Subscription subscription = messageBus.subscribe("my-topic", message -> {
 *     System.out.println("Received: " + new String(message.getPayload()));
 * });
 *
 * // Publish a message
 * Message message = Message.builder()
 *     .topic("my-topic")
 *     .sourceApplicationId("my-app")
 *     .payload("Hello, World!".getBytes())
 *     .build();
 * messageBus.publish("my-topic", message);
 *
 * // Unsubscribe
 * subscription.cancel();
 *
 * // When done
 * messageBus.shutdown();
 * }
 *
 * @see MessageBus
 * @see Message
 * @see Subscription
 */
public class InMemoryMessageBus implements MessageBus {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryMessageBus.class);
    private static final List<SubscriptionImpl> EMPTY_SUBSCRIBERS = Collections.emptyList();
    private static final int DEFAULT_THREAD_POOL_SIZE = 4;
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final Map<String, CopyOnWriteArrayList<SubscriptionImpl>> subscribers;
    private final ExecutorService dispatchExecutor;
    private final int shutdownTimeoutSeconds;
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    /**
     * Creates a new in-memory message bus with default configuration.
     * <p>
     * Uses 4 dispatcher threads and 10 second shutdown timeout.
     * Dispatcher threads are daemon threads to not prevent JVM shutdown.
     */
    public InMemoryMessageBus() {
        this(DEFAULT_THREAD_POOL_SIZE, DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
    }

    /**
     * Creates a new in-memory message bus with custom configuration.
     * <p>
     * Dispatcher threads are daemon threads to not prevent JVM shutdown.
     *
     * @param threadPoolSize number of dispatcher threads (must be at least 1)
     * @param shutdownTimeoutSeconds seconds to wait for pending messages during shutdown
     * @throws IllegalArgumentException if threadPoolSize < 1 or shutdownTimeoutSeconds < 0
     */
    public InMemoryMessageBus(int threadPoolSize, int shutdownTimeoutSeconds) {
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("threadPoolSize must be at least 1, got: " + threadPoolSize);
        }
        if (shutdownTimeoutSeconds < 0) {
            throw new IllegalArgumentException("shutdownTimeoutSeconds cannot be negative, got: " + shutdownTimeoutSeconds);
        }

        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
        this.subscribers = new ConcurrentHashMap<>();
        this.dispatchExecutor = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "message-bus-dispatcher-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        logger.info("InMemoryMessageBus started with {} dispatcher threads", threadPoolSize);
    }

    /**
     * Publishes a message to the specified topic.
     * <p>
     * The message is delivered asynchronously to all active subscribers of the topic.
     * If there are no subscribers, the message is discarded. Exceptions in message
     * handlers are caught and logged but do not affect other subscribers.
     *
     * @param topic the topic to publish to
     * @param message the message to publish
     * @throws NullPointerException if topic or message is null
     */
    @Override
    public void publish(String topic, Message message) {
        Objects.requireNonNull(topic, "topic cannot be null");
        Objects.requireNonNull(message, "message cannot be null");

        List<SubscriptionImpl> topicSubscribers = subscribers.get(topic);

        if (topicSubscribers == null || topicSubscribers.isEmpty()) {
            logger.debug("No subscribers for topic: {}", topic);
            return;
        }

        logger.debug("Publishing message to topic '{}': {} subscribers", topic, topicSubscribers.size());

        for (SubscriptionImpl subscription : topicSubscribers) {
            if (subscription.isActive()) {
                try {
                    dispatchExecutor.submit(() -> {
                        try {
                            subscription.getHandler().onMessage(message);
                        } catch (Exception e) {
                            logger.error("Error delivering message to subscriber on topic '{}'", topic, e);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    logger.debug("Message bus is shut down, cannot deliver message to topic '{}'", topic);
                    // Don't propagate - gracefully skip delivery during shutdown
                }
            }
        }
    }

    /**
     * Subscribes to messages on the specified topic.
     * <p>
     * The handler will be invoked asynchronously for each message published to the topic.
     * The subscription remains active until {@link Subscription#cancel()} is called.
     *
     * @param topic the topic to subscribe to
     * @param handler the handler to invoke for each message
     * @return a subscription that can be used to cancel the subscription
     * @throws NullPointerException if topic or handler is null
     */
    @Override
    public Subscription subscribe(String topic, MessageHandler handler) {
        Objects.requireNonNull(topic, "topic cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");

        SubscriptionImpl subscription = new SubscriptionImpl(topic, handler, this);

        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                .add(subscription);

        logger.info("New subscription to topic '{}'. Total subscribers: {}",
                topic, subscribers.get(topic).size());

        return subscription;
    }

    /**
     * Unsubscribes the specified subscription.
     * <p>
     * After this call, the subscription's handler will no longer receive messages.
     * This is equivalent to calling {@link Subscription#cancel()}.
     *
     * @param subscription the subscription to cancel
     */
    @Override
    public void unsubscribe(Subscription subscription) {
        if (subscription instanceof SubscriptionImpl) {
            SubscriptionImpl impl = (SubscriptionImpl) subscription;
            impl.cancel();
        }
    }

    /**
     * Internal method to remove a subscription from the subscribers map.
     * <p>
     * This is called by SubscriptionImpl when cancelled.
     *
     * @param subscription the subscription to remove
     */
    void removeSubscription(SubscriptionImpl subscription) {
        if (subscription == null) {
            logger.warn("Attempted to remove null subscription");
            return;
        }

        CopyOnWriteArrayList<SubscriptionImpl> topicSubscribers =
                subscribers.get(subscription.getTopic());

        if (topicSubscribers != null) {
            topicSubscribers.remove(subscription);
            logger.info("Unsubscribed from topic '{}'. Remaining subscribers: {}",
                    subscription.getTopic(), topicSubscribers.size());
        }
    }

    /**
     * Shuts down the message bus.
     * <p>
     * Initiates an orderly shutdown of the dispatch executor, waiting for the configured
     * timeout for pending message deliveries to complete. If the executor does not terminate
     * within the timeout, a forced shutdown is initiated.
     */
    public void shutdown() {
        logger.info("Shutting down message bus");
        dispatchExecutor.shutdown();
        try {
            if (!dispatchExecutor.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                logger.warn("Message bus did not terminate within {} seconds, forcing shutdown", shutdownTimeoutSeconds);
                dispatchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Shutdown interrupted, forcing shutdown");
            dispatchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class SubscriptionImpl implements Subscription {
        private final String id;
        private final String topic;
        private final MessageHandler handler;
        private final InMemoryMessageBus bus;
        private volatile boolean active = true;

        SubscriptionImpl(String topic, MessageHandler handler, InMemoryMessageBus bus) {
            this.id = UUID.randomUUID().toString();
            this.topic = topic;
            this.handler = handler;
            this.bus = bus;
        }

        @Override
        public String getTopic() {
            return topic;
        }

        @Override
        public void cancel() {
            if (active) {
                active = false;
                bus.removeSubscription(this);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }

        public MessageHandler getHandler() {
            return handler;
        }

        public String getId() {
            return id;
        }
    }
}
