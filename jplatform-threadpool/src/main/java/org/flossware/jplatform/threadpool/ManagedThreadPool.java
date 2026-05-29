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

package org.flossware.jplatform.threadpool;

import org.flossware.jplatform.api.ThreadPoolConfig;
import org.flossware.jplatform.api.ThreadPoolStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Managed thread pool for an application.
 * Provides isolated thread pool with monitoring and graceful shutdown.
 * <p>
 * This implementation creates application-specific thread pools with configurable
 * core/max sizes, queue capacity, and automatic exception handling. Each thread
 * is named with the application ID for easy identification in thread dumps.
 * <p>
 * Example usage:
 * {@code
 * ThreadPoolConfig config = new ThreadPoolConfig(4, 8, 60, 100);
 * ManagedThreadPool pool = new ManagedThreadPool("my-app", config);
 *
 * // Submit tasks
 * pool.submit(() -> doWork());
 *
 * // When done
 * pool.shutdown();
 * }
 *
 * @see org.flossware.jplatform.api.ThreadPoolExecutor
 * @see ThreadPoolConfig
 */
public class ManagedThreadPool implements org.flossware.jplatform.api.ThreadPoolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ManagedThreadPool.class);
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final String applicationId;
    private final java.util.concurrent.ThreadPoolExecutor executor;
    private final ThreadFactory threadFactory;
    private final AtomicInteger threadCounter = new AtomicInteger(0);
    private final long shutdownTimeoutSeconds;

    /**
     * Creates a new managed thread pool for the specified application with default shutdown timeout.
     * <p>
     * Threads are created with names in the format: {applicationId}-thread-{N}
     * and configured with an uncaught exception handler that logs errors.
     * The rejection policy is CallerRunsPolicy, which runs rejected tasks
     * in the calling thread.
     * <p>
     * Uses default shutdown timeout of 30 seconds.
     *
     * @param applicationId the unique identifier for the application
     * @param config the thread pool configuration specifying core size, max size,
     *               keep-alive time, and queue capacity
     * @throws NullPointerException if applicationId or config is null
     */
    public ManagedThreadPool(String applicationId, ThreadPoolConfig config) {
        this(applicationId, config, DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
    }

    /**
     * Creates a new managed thread pool for the specified application with custom shutdown timeout.
     * <p>
     * Threads are created with names in the format: {applicationId}-thread-{N}
     * and configured with an uncaught exception handler that logs errors.
     * The rejection policy is CallerRunsPolicy, which runs rejected tasks
     * in the calling thread.
     *
     * @param applicationId the unique identifier for the application
     * @param config the thread pool configuration specifying core size, max size,
     *               keep-alive time, and queue capacity
     * @param shutdownTimeoutSeconds the timeout in seconds to wait for tasks to complete during shutdown
     * @throws NullPointerException if applicationId or config is null
     * @throws IllegalArgumentException if shutdownTimeoutSeconds is negative
     */
    public ManagedThreadPool(String applicationId, ThreadPoolConfig config, long shutdownTimeoutSeconds) {
        this.applicationId = Objects.requireNonNull(applicationId, "applicationId cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        if (shutdownTimeoutSeconds < 0) {
            throw new IllegalArgumentException("shutdownTimeoutSeconds must be >= 0, got: " + shutdownTimeoutSeconds);
        }

        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;

        // ThreadPoolConfig.Builder.build() already validates all constraints,
        // so we can trust the config values are valid

        this.threadFactory = r -> {
            Thread t = new Thread(r, applicationId + "-thread-" + threadCounter.incrementAndGet());
            t.setDaemon(false);
            t.setUncaughtExceptionHandler((thread, throwable) -> {
                logger.error("[{}] Uncaught exception in thread {}", applicationId, thread.getName(), throwable);
            });
            return t;
        };

        this.executor = new java.util.concurrent.ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveTimeSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        logger.info("[{}] Created thread pool: core={}, max={}, queue={}, shutdownTimeout={}s",
                applicationId, config.getCorePoolSize(), config.getMaxPoolSize(),
                config.getQueueCapacity(), shutdownTimeoutSeconds);
    }

    /**
     * Submits a Runnable task for execution.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if task cannot be scheduled for execution
     * @throws NullPointerException if task is null
     */
    @Override
    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    /**
     * Submits a value-returning task for execution.
     *
     * @param <T> the type of the task's result
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if task cannot be scheduled for execution
     * @throws NullPointerException if task is null
     */
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    /**
     * Executes the given command at some time in the future.
     *
     * @param command the runnable task
     * @throws RejectedExecutionException if this task cannot be accepted for execution
     * @throws NullPointerException if command is null
     */
    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    /**
     * Initiates an orderly shutdown in which previously submitted tasks are executed,
     * but no new tasks will be accepted.
     * <p>
     * <b>This method blocks</b> and waits up to {@code shutdownTimeoutSeconds} for
     * actively executing tasks to terminate. If tasks do not complete within the
     * timeout period, forces immediate shutdown via {@link #shutdownNow()}.
     * <p>
     * If the waiting thread is interrupted, calls {@link #shutdownNow()} immediately
     * and restores the interrupt status.
     * <p>
     * Use {@link #isTerminated()} after this method returns to verify all tasks
     * have completed (especially if timeout was reached and force-shutdown occurred).
     *
     * @see #shutdownNow()
     * @see #isTerminated()
     */
    @Override
    public void shutdown() {
        logger.info("[{}] Shutting down thread pool", applicationId);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                logger.warn("[{}] Thread pool did not terminate in {} seconds, forcing shutdown",
                        applicationId, shutdownTimeoutSeconds);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("[{}] Interrupted while waiting for thread pool shutdown", applicationId);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Attempts to stop all actively executing tasks and halts the processing
     * of waiting tasks.
     * This method does not wait for actively executing tasks to terminate.
     */
    @Override
    public void shutdownNow() {
        logger.info("[{}] Force shutting down thread pool", applicationId);
        executor.shutdownNow();
    }

    /**
     * Returns true if this executor has been shut down.
     *
     * @return true if this executor has been shut down
     */
    @Override
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /**
     * Returns true if all tasks have completed following shut down.
     *
     * @return true if all tasks have completed following shut down
     */
    @Override
    public boolean isTerminated() {
        return executor.isTerminated();
    }

    /**
     * Returns current statistics for this thread pool, including active thread count,
     * completed task count, queue size, and pool size information.
     *
     * @return a snapshot of current thread pool statistics
     */
    @Override
    public ThreadPoolStats getStats() {
        return new ThreadPoolStats(
                executor.getActiveCount(),
                executor.getCompletedTaskCount(),
                executor.getQueue().size(),
                executor.getPoolSize(),
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize()
        );
    }

    /**
     * Returns the application ID associated with this thread pool.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationId;
    }
}
