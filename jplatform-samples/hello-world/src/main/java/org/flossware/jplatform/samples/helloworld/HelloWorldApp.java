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

package org.flossware.jplatform.samples.helloworld;

import org.flossware.jplatform.api.Application;
import org.flossware.jplatform.api.ApplicationContext;
import org.flossware.jplatform.api.ApplicationShutdownException;
import org.flossware.jplatform.api.ApplicationStartupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple hello world application demonstrating platform-java usage.
 * Prints a message every 5 seconds.
 * <p>
 * This sample application demonstrates the basic lifecycle of a platform-java application:
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
     * @throws ApplicationStartupException if an error occurs during startup
     */
    @Override
    public void start(ApplicationContext context) throws ApplicationStartupException {
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
     * @throws ApplicationShutdownException if an error occurs during shutdown
     */
    @Override
    public void stop() throws ApplicationShutdownException {
        logger.info("Hello World Application stopping...");
        running.set(false);

        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApplicationShutdownException("hello-world",
                        "Interrupted while waiting for worker thread to stop", e);
            }
        }

        logger.info("Hello World Application stopped");
    }
}
