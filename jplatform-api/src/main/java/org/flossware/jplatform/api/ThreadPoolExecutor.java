package org.flossware.jplatform.api;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Isolated thread pool executor for an application.
 * Provides concurrent execution while maintaining resource isolation.
 *
 * <p>Each application has its own dedicated thread pool to prevent
 * resource contention and enable proper resource tracking.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ThreadPoolExecutor executor = context.getThreadPool();
 *
 * // Submit a task
 * Future<?> future = executor.submit(() -> {
 *     // Task code here
 *     System.out.println("Running in: " + Thread.currentThread().getName());
 * });
 *
 * // Wait for completion
 * future.get();
 * }</pre>
 *
 * @see ApplicationContext#getThreadPool()
 */
public interface ThreadPoolExecutor {
    /**
     * Submits a Runnable task for execution.
     *
     * @param task the task to execute
     * @return a Future representing pending completion of the task
     */
    Future<?> submit(Runnable task);

    /**
     * Submits a Callable task for execution and returns a Future
     * representing the pending result.
     *
     * @param <T> the type of the task's result
     * @param task the task to execute
     * @return a Future representing pending completion of the task
     */
    <T> Future<T> submit(Callable<T> task);

    /**
     * Executes the given command at some time in the future.
     *
     * @param command the runnable task
     */
    void execute(Runnable command);

    /**
     * Initiates an orderly shutdown in which previously submitted tasks
     * are executed, but no new tasks will be accepted.
     */
    void shutdown();

    /**
     * Attempts to stop all actively executing tasks and halts the
     * processing of waiting tasks.
     */
    void shutdownNow();

    /**
     * Returns true if this executor has been shut down.
     *
     * @return true if shut down
     */
    boolean isShutdown();

    /**
     * Returns true if all tasks have completed following shut down.
     *
     * @return true if terminated
     */
    boolean isTerminated();

    /**
     * Returns statistics about this thread pool's current state.
     *
     * @return current thread pool statistics
     */
    ThreadPoolStats getStats();
}
