package org.flossware.jplatform.samples.helloworld;

import org.flossware.jplatform.api.Application;
import org.flossware.jplatform.api.ApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple hello world application demonstrating JPlatform usage.
 * Prints a message every 5 seconds.
 * <p>
 * This sample application demonstrates the basic lifecycle of a JPlatform application:
 * starting, running in a background thread, and graceful shutdown. It serves as a
 * minimal example for new application development.
 * <p>
 * Example deployment:
 * {@code
 * // Build the application
 * mvn clean package
 *
 * // Deploy and start via platform launcher
 * jplatform> deploy hello-world target/hello-world-1.0.0.jar org.flossware.jplatform.samples.helloworld.HelloWorldApp
 * jplatform> start hello-world
 * jplatform> status hello-world
 * jplatform> stop hello-world
 * }
 *
 * @see Application
 */
public class HelloWorldApp implements Application {

    private static final Logger logger = LoggerFactory.getLogger(HelloWorldApp.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    /**
     * Starts the application.
     * <p>
     * Creates a background worker thread that prints a message every 5 seconds.
     * The worker thread continues until {@link #stop()} is called.
     *
     * @param context the application context providing access to platform services
     * @throws Exception if an error occurs during startup
     */
    @Override
    public void start(ApplicationContext context) throws Exception {
        logger.info("Hello World Application starting!");
        logger.info("Application ID: {}", context.getApplicationId());
        logger.info("Classpath: {}", System.getProperty("java.class.path"));

        running.set(true);

        workerThread = new Thread(() -> {
            int count = 0;
            while (running.get()) {
                count++;
                logger.info("Hello from JPlatform! Message #{}", count);
                System.out.println("Hello from JPlatform! Message #" + count);

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    logger.info("Worker thread interrupted");
                    break;
                }
            }
        }, "hello-world-worker");

        workerThread.start();
        logger.info("Hello World Application started successfully");
    }

    /**
     * Stops the application.
     * <p>
     * Signals the worker thread to stop and waits up to 5 seconds for it to terminate.
     * This demonstrates graceful shutdown of background threads.
     *
     * @throws Exception if an error occurs during shutdown
     */
    @Override
    public void stop() throws Exception {
        logger.info("Hello World Application stopping...");
        running.set(false);

        if (workerThread != null) {
            workerThread.interrupt();
            workerThread.join(5000);
        }

        logger.info("Hello World Application stopped");
    }
}
