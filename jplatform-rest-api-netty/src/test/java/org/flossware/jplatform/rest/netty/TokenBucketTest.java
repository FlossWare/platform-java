package org.flossware.jplatform.rest.netty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenBucket rate limiter.
 */
class TokenBucketTest {

    @Test
    void testInitialTokensAvailable() {
        TokenBucket bucket = new TokenBucket(10);
        assertTrue(bucket.tryAcquire());
    }

    @Test
    void testExhaustsTokens() {
        TokenBucket bucket = new TokenBucket(5);

        // Acquire all 5 tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryAcquire(), "Should acquire token " + i);
        }

        // Next acquire should fail
        assertFalse(bucket.tryAcquire());
    }

    @Test
    void testRefillsOverTime() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(10);

        // Exhaust all tokens
        for (int i = 0; i < 10; i++) {
            bucket.tryAcquire();
        }

        // No tokens left
        assertFalse(bucket.tryAcquire());

        // Wait for refill (slightly over 100ms for 1 token at 10/sec)
        Thread.sleep(150);

        // Should have refilled at least 1 token
        assertTrue(bucket.tryAcquire());
    }

    @Test
    void testHighRateLimit() {
        TokenBucket bucket = new TokenBucket(1000);

        // Should be able to acquire many tokens initially
        int acquired = 0;
        for (int i = 0; i < 1000; i++) {
            if (bucket.tryAcquire()) {
                acquired++;
            }
        }

        // Should have acquired most or all tokens
        assertTrue(acquired >= 990, "Should acquire at least 990 tokens, got " + acquired);
    }

    @Test
    void testLowRateLimit() {
        TokenBucket bucket = new TokenBucket(1);

        // First token succeeds
        assertTrue(bucket.tryAcquire());

        // Second fails
        assertFalse(bucket.tryAcquire());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(100);
        int threadCount = 10;
        int attemptsPerThread = 20;

        Thread[] threads = new Thread[threadCount];
        int[] successCounts = new int[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < attemptsPerThread; j++) {
                    if (bucket.tryAcquire()) {
                        successCounts[threadIndex]++;
                    }
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Total successes should not exceed capacity
        int totalSuccesses = 0;
        for (int count : successCounts) {
            totalSuccesses += count;
        }

        assertTrue(totalSuccesses <= 100, "Total successes should not exceed capacity");
        assertTrue(totalSuccesses > 0, "Should have some successes");
    }

    @Test
    void testDoesNotExceedCapacity() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(10);

        // Exhaust tokens
        for (int i = 0; i < 10; i++) {
            bucket.tryAcquire();
        }

        // Wait for multiple refill periods
        Thread.sleep(2000);

        // Should still only have capacity worth of tokens
        int acquired = 0;
        for (int i = 0; i < 20; i++) {
            if (bucket.tryAcquire()) {
                acquired++;
            }
        }

        assertTrue(acquired <= 10, "Should not exceed capacity");
    }
}
