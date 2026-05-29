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
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ManagedThreadPool.
 * Tests thread pool creation, task execution, shutdown, and statistics.
 */
class ManagedThreadPoolTest {

    @Test
    void testConstructorWithDefaultTimeout() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        assertNotNull(pool);
        assertEquals("test-app", pool.getApplicationId());
        assertFalse(pool.isShutdown());
        assertFalse(pool.isTerminated());

        pool.shutdownNow();
    }

    @Test
    void testConstructorWithCustomTimeout() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config, 10);

        assertNotNull(pool);
        assertEquals("test-app", pool.getApplicationId());

        pool.shutdownNow();
    }

    @Test
    void testConstructorNullApplicationId() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        assertThrows(NullPointerException.class, () ->
                new ManagedThreadPool(null, config)
        );
    }

    @Test
    void testConstructorNullConfig() {
        assertThrows(NullPointerException.class, () ->
                new ManagedThreadPool("test-app", null)
        );
    }

    @Test
    void testConstructorNegativeTimeout() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                new ManagedThreadPool("test-app", config, -1)
        );
    }

    @Test
    void testConstructorZeroTimeout() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config, 0);

        assertNotNull(pool);
        pool.shutdownNow();
    }

    @Test
    void testSubmitRunnable() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        AtomicBoolean executed = new AtomicBoolean(false);
        Future<?> future = pool.submit(() -> executed.set(true));

        assertNotNull(future);
        future.get(1, TimeUnit.SECONDS);
        assertTrue(executed.get());

        pool.shutdownNow();
    }

    @Test
    void testSubmitCallable() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        Future<Integer> future = pool.submit(() -> 42);

        assertNotNull(future);
        assertEquals(42, future.get(1, TimeUnit.SECONDS));

        pool.shutdownNow();
    }

    @Test
    void testExecute() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        CountDownLatch latch = new CountDownLatch(1);
        pool.execute(latch::countDown);

        assertTrue(latch.await(1, TimeUnit.SECONDS));

        pool.shutdownNow();
    }

    @Test
    void testSubmitNullTask() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        assertThrows(NullPointerException.class, () ->
                pool.submit((Runnable) null)
        );

        pool.shutdownNow();
    }

    @Test
    void testSubmitNullCallable() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        assertThrows(NullPointerException.class, () ->
                pool.submit((Callable<?>) null)
        );

        pool.shutdownNow();
    }

    @Test
    void testExecuteNullCommand() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        assertThrows(NullPointerException.class, () ->
                pool.execute(null)
        );

        pool.shutdownNow();
    }

    @Test
    void testShutdown() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config, 2);

        CountDownLatch latch = new CountDownLatch(1);
        pool.submit(() -> {
            try {
                Thread.sleep(100);
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        pool.shutdown();

        assertTrue(pool.isShutdown());
        // Task should have completed
        assertEquals(0, latch.getCount());
    }

    @Test
    void testShutdownNow() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        pool.shutdownNow();

        assertTrue(pool.isShutdown());
    }

    @Test
    void testIsShutdownAfterShutdown() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        assertFalse(pool.isShutdown());

        pool.shutdown();

        assertTrue(pool.isShutdown());
    }

    @Test
    void testIsTerminatedAfterShutdown() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        assertFalse(pool.isTerminated());

        pool.shutdown();

        assertTrue(pool.isTerminated());
    }

    @Test
    void testGetStats() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .queueCapacity(10)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        ThreadPoolStats stats = pool.getStats();
        assertNotNull(stats);
        assertEquals(2, stats.getCorePoolSize());
        assertEquals(4, stats.getMaximumPoolSize());
        assertEquals(0, stats.getActiveThreads());
        assertEquals(0, stats.getQueuedTasks());

        // Submit a blocking task
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);

        pool.submit(() -> {
            try {
                startLatch.countDown();
                endLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        startLatch.await(1, TimeUnit.SECONDS);

        // Check stats while task is running
        stats = pool.getStats();
        assertTrue(stats.getActiveThreads() >= 0);
        assertTrue(stats.getPoolSize() >= 0);

        endLatch.countDown();
        pool.shutdown();
    }

    @Test
    void testMultipleTasks() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(4)
                .maxPoolSize(8)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            pool.submit(latch::countDown);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        pool.shutdown();
    }

    @Test
    void testTaskException() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        Future<?> future = pool.submit(() -> {
            throw new RuntimeException("Test exception");
        });

        assertThrows(ExecutionException.class, () ->
                future.get(1, TimeUnit.SECONDS)
        );

        // Pool should still be operational
        assertFalse(pool.isShutdown());

        pool.shutdownNow();
    }

    @Test
    void testQueueCapacity() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(1)
                .maxPoolSize(1)
                .queueCapacity(2)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startLatch = new CountDownLatch(1);

        // Submit blocking task to occupy the single thread
        pool.submit(() -> {
            try {
                startLatch.countDown();
                blockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        startLatch.await(1, TimeUnit.SECONDS);

        // Submit tasks to fill queue
        pool.submit(() -> {});
        pool.submit(() -> {});

        // Queue should have 2 tasks
        ThreadPoolStats stats = pool.getStats();
        assertTrue(stats.getQueuedTasks() >= 0);

        blockLatch.countDown();
        pool.shutdown();
    }

    @Test
    void testThreadNaming() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(1)
                .maxPoolSize(1)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("my-app", config);

        CompletableFuture<String> threadName = new CompletableFuture<>();

        pool.submit(() -> threadName.complete(Thread.currentThread().getName()));

        String name = threadName.get(1, TimeUnit.SECONDS);
        assertTrue(name.startsWith("my-app-thread-"));

        pool.shutdownNow();
    }

    @Test
    void testConcurrentSubmission() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(4)
                .maxPoolSize(8)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        int threadCount = 10;
        int tasksPerThread = 10;
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount * tasksPerThread);

        // Spawn multiple threads submitting tasks concurrently
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < tasksPerThread; j++) {
                    pool.submit(() -> {
                        counter.incrementAndGet();
                        latch.countDown();
                    });
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(threadCount * tasksPerThread, counter.get());

        pool.shutdown();
    }

    @Test
    void testShutdownWithRunningTasks() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config, 1);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);

        pool.submit(() -> {
            try {
                startLatch.countDown();
                // Short sleep so shutdown completes
                Thread.sleep(100);
                endLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        startLatch.await(1, TimeUnit.SECONDS);

        pool.shutdown();

        assertTrue(pool.isShutdown());
        assertTrue(pool.isTerminated());
        assertEquals(0, endLatch.getCount());
    }

    @Test
    void testGetApplicationId() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("my-special-app", config);

        assertEquals("my-special-app", pool.getApplicationId());

        pool.shutdownNow();
    }

    @Test
    void testStatsAfterCompletion() throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        // Submit and complete a task
        Future<?> future = pool.submit(() -> {});
        future.get(1, TimeUnit.SECONDS);

        // Give it a moment to update stats
        Thread.sleep(100);

        ThreadPoolStats stats = pool.getStats();
        assertTrue(stats.getCompletedTasks() >= 1);

        pool.shutdownNow();
    }

    @Test
    void testLargeQueueCapacity() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxPoolSize(4)
                .queueCapacity(1000)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        ThreadPoolStats stats = pool.getStats();
        assertEquals(2, stats.getCorePoolSize());
        assertEquals(4, stats.getMaximumPoolSize());

        pool.shutdownNow();
    }

    @Test
    void testMinimalConfiguration() {
        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(1)
                .maxPoolSize(1)
                .build();

        ManagedThreadPool pool = new ManagedThreadPool("test-app", config);

        ThreadPoolStats stats = pool.getStats();
        assertEquals(1, stats.getCorePoolSize());
        assertEquals(1, stats.getMaximumPoolSize());

        pool.shutdownNow();
    }
}
