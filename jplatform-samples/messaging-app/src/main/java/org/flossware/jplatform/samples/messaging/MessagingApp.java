package org.flossware.jplatform.samples.messaging;

import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sample application demonstrating messaging capabilities.
 * Publishes and subscribes to messages on the platform message bus.
 * <p>
 * This sample application demonstrates JPlatform's messaging features by:
 * <ul>
 * <li>Subscribing to a topic to receive messages</li>
 * <li>Publishing messages to the same topic every 3 seconds</li>
 * <li>Handling message delivery asynchronously</li>
 * <li>Properly cleaning up subscriptions on shutdown</li>
 * </ul>
 * <p>
 * When multiple instances of this application run simultaneously, they will
 * all receive each other's messages, demonstrating inter-application communication.
 * <p>
 * Example deployment:
 * {@code
 * // Build the application
 * mvn clean package
 *
 * // Deploy and start via platform launcher
 * jplatform> deploy messaging-app target/messaging-app-1.0.0.jar org.flossware.jplatform.samples.messaging.MessagingApp
 * jplatform> start messaging-app
 *
 * // You can deploy multiple instances to see them communicate:
 * jplatform> deploy messaging-app-2 target/messaging-app-1.0.0.jar org.flossware.jplatform.samples.messaging.MessagingApp
 * jplatform> start messaging-app-2
 * }
 *
 * @see Application
 * @see MessageBus
 */
public class MessagingApp implements Application {

    private static final Logger logger = LoggerFactory.getLogger(MessagingApp.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread publisherThread;
    private Subscription subscription;
    private ApplicationContext context;

    /**
     * Starts the application.
     * <p>
     * Subscribes to the "platform.events" topic and starts a background thread
     * that publishes messages every 3 seconds. If the message bus is not available
     * in the context, logs a warning and returns without starting.
     *
     * @param context the application context providing access to platform services
     * @throws Exception if an error occurs during startup
     */
    @Override
    public void start(ApplicationContext context) throws Exception {
        this.context = context;
        logger.info("Messaging Application starting!");

        // Check if messaging is available
        if (!context.getMessageBus().isPresent()) {
            logger.warn("MessageBus not available - application will run without messaging");
            return;
        }

        MessageBus messageBus = context.getMessageBus().get();

        // Subscribe to messages
        subscription = messageBus.subscribe("platform.events", message -> {
            String payload = message.getPayload() != null ?
                    new String(message.getPayload()) : "null";
            logger.info("Received message: id={}, payload={}",
                    message.getId(), payload);
            System.out.println("Received: " + payload);
        });

        running.set(true);

        // Publish messages periodically
        publisherThread = new Thread(() -> {
            int count = 0;
            while (running.get()) {
                count++;

                String payload = String.format("{\"count\":%d,\"appId\":\"%s\",\"timestamp\":%d}",
                        count, context.getApplicationId(), System.currentTimeMillis());

                Message message = Message.builder()
                        .topic("platform.events")
                        .sourceApplicationId(context.getApplicationId())
                        .payload(payload.getBytes())
                        .build();

                messageBus.publish("platform.events", message);

                logger.info("Published message #{}", count);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    logger.info("Publisher thread interrupted");
                    break;
                }
            }
        }, "messaging-publisher");

        publisherThread.start();
        logger.info("Messaging Application started successfully");
    }

    /**
     * Stops the application.
     * <p>
     * Signals the publisher thread to stop, waits up to 5 seconds for it to terminate,
     * and cancels the message subscription. This demonstrates proper cleanup of
     * messaging resources.
     *
     * @throws Exception if an error occurs during shutdown
     */
    @Override
    public void stop() throws Exception {
        logger.info("Messaging Application stopping...");
        running.set(false);

        if (publisherThread != null) {
            publisherThread.interrupt();
            publisherThread.join(5000);
        }

        if (subscription != null && subscription.isActive()) {
            subscription.cancel();
        }

        logger.info("Messaging Application stopped");
    }
}
