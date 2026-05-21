package org.flossware.jplatform.threadpool;

import org.flossware.jplatform.api.ThreadPoolConfig;
import org.flossware.jplatform.api.ThreadPoolStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final String applicationId;
    private final java.util.concurrent.ThreadPoolExecutor executor;
    private final ThreadFactory threadFactory;
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    /**
     * Creates a new managed thread pool for the specified application.
     * <p>
     * Threads are created with names in the format: {applicationId}-thread-{N}
     * and configured with an uncaught exception handler that logs errors.
     * The rejection policy is CallerRunsPolicy, which runs rejected tasks
     * in the calling thread.
     *
     * @param applicationId the unique identifier for the application
     * @param config the thread pool configuration specifying core size, max size,
     *               keep-alive time, and queue capacity
     * @throws NullPointerException if applicationId or config is null
     */
    public ManagedThreadPool(String applicationId, ThreadPoolConfig config) {
        this.applicationId = applicationId;

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

        logger.info("[{}] Created thread pool: core={}, max={}, queue={}",
                applicationId, config.getCorePoolSize(), config.getMaxPoolSize(), config.getQueueCapacity());
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
     * but no new tasks will be accepted. Waits up to 30 seconds for termination,
     * then forces shutdown if necessary.
     * <p>
     * This method does not wait for actively executing tasks to terminate.
     * Use {@link #isTerminated()} to check completion.
     */
    @Override
    public void shutdown() {
        logger.info("[{}] Shutting down thread pool", applicationId);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("[{}] Thread pool did not terminate in 30 seconds, forcing shutdown", applicationId);
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
