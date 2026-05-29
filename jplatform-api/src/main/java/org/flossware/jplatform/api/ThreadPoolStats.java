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
 * Snapshot of thread pool statistics at a point in time.
 * Captures active threads, completed tasks, queue depth, and pool sizing.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ThreadPoolExecutor executor = context.getThreadPool();
 * ThreadPoolStats stats = executor.getStats();
 *
 * System.out.println("Active threads: " + stats.getActiveThreads());
 * System.out.println("Completed tasks: " + stats.getCompletedTasks());
 * System.out.println("Queued tasks: " + stats.getQueuedTasks());
 * System.out.println("Pool size: " + stats.getPoolSize() +
 *                   " (core=" + stats.getCorePoolSize() +
 *                   ", max=" + stats.getMaximumPoolSize() + ")");
 * }</pre>
 *
 * @see ThreadPoolExecutor
 * @see ThreadPoolConfig
 */
public class ThreadPoolStats {
    private final int activeThreads;
    private final long completedTasks;
    private final int queuedTasks;
    private final int poolSize;
    private final int corePoolSize;
    private final int maximumPoolSize;

    /**
     * Constructs a new thread pool statistics snapshot.
     *
     * @param activeThreads the number of threads actively executing tasks
     * @param completedTasks the total number of completed tasks
     * @param queuedTasks the number of tasks waiting in the queue
     * @param poolSize the current number of threads in the pool
     * @param corePoolSize the core pool size configuration
     * @param maximumPoolSize the maximum pool size configuration
     * @throws IllegalArgumentException if any parameter is negative, if maximumPoolSize < corePoolSize,
     *         if poolSize > maximumPoolSize, or if activeThreads > poolSize
     */
    public ThreadPoolStats(int activeThreads, long completedTasks, int queuedTasks,
                          int poolSize, int corePoolSize, int maximumPoolSize) {
        if (activeThreads < 0) {
            throw new IllegalArgumentException("activeThreads cannot be negative: " + activeThreads);
        }
        if (completedTasks < 0) {
            throw new IllegalArgumentException("completedTasks cannot be negative: " + completedTasks);
        }
        if (queuedTasks < 0) {
            throw new IllegalArgumentException("queuedTasks cannot be negative: " + queuedTasks);
        }
        if (poolSize < 0) {
            throw new IllegalArgumentException("poolSize cannot be negative: " + poolSize);
        }
        if (corePoolSize < 0) {
            throw new IllegalArgumentException("corePoolSize cannot be negative: " + corePoolSize);
        }
        if (maximumPoolSize < 0) {
            throw new IllegalArgumentException("maximumPoolSize cannot be negative: " + maximumPoolSize);
        }
        if (maximumPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                "maximumPoolSize (" + maximumPoolSize +
                ") cannot be less than corePoolSize (" + corePoolSize + ")");
        }
        if (poolSize > maximumPoolSize) {
            throw new IllegalArgumentException(
                "poolSize (" + poolSize +
                ") cannot exceed maximumPoolSize (" + maximumPoolSize + ")");
        }
        if (activeThreads > poolSize) {
            throw new IllegalArgumentException(
                "activeThreads (" + activeThreads +
                ") cannot exceed poolSize (" + poolSize + ")");
        }

        this.activeThreads = activeThreads;
        this.completedTasks = completedTasks;
        this.queuedTasks = queuedTasks;
        this.poolSize = poolSize;
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
    }

    /**
     * Returns the number of threads currently executing tasks.
     *
     * @return the active thread count
     */
    public int getActiveThreads() {
        return activeThreads;
    }

    /**
     * Returns the total number of tasks that have completed execution.
     *
     * @return the completed task count
     */
    public long getCompletedTasks() {
        return completedTasks;
    }

    /**
     * Returns the number of tasks waiting in the queue for execution.
     *
     * @return the queued task count
     */
    public int getQueuedTasks() {
        return queuedTasks;
    }

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Returns the configured core pool size.
     *
     * @return the core pool size
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Returns the configured maximum pool size.
     *
     * @return the maximum pool size
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    @Override
    public String toString() {
        return String.format("ThreadPoolStats{active=%d, completed=%d, queued=%d, " +
                           "poolSize=%d, core=%d, max=%d}",
                activeThreads, completedTasks, queuedTasks, poolSize, corePoolSize, maximumPoolSize);
    }
}
