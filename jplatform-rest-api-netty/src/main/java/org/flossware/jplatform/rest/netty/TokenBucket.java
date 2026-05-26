package org.flossware.jplatform.rest.netty;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple token bucket implementation for rate limiting.
 * Thread-safe implementation using atomic operations.
 *
 * <p>The token bucket algorithm allows bursts up to the capacity
 * while maintaining an average rate over time.
 *
 * @since 1.1
 */
class TokenBucket {
    private final long capacity;
    private final long refillRateNanos;
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTime;

    /**
     * Creates a new token bucket.
     *
     * @param tokensPerSecond the refill rate in tokens per second
     */
    TokenBucket(int tokensPerSecond) {
        this.capacity = tokensPerSecond;
        this.refillRateNanos = 1_000_000_000L / tokensPerSecond;
        this.tokens = new AtomicLong(tokensPerSecond);
        this.lastRefillTime = new AtomicLong(System.nanoTime());
    }

    /**
     * Attempts to acquire a token from the bucket.
     *
     * @return true if a token was acquired, false if rate limit exceeded
     */
    boolean tryAcquire() {
        refill();

        long currentTokens;
        do {
            currentTokens = tokens.get();
            if (currentTokens <= 0) {
                return false;
            }
        } while (!tokens.compareAndSet(currentTokens, currentTokens - 1));

        return true;
    }

    /**
     * Refills tokens based on elapsed time since last refill.
     */
    private void refill() {
        long now = System.nanoTime();
        long lastRefill = lastRefillTime.get();
        long elapsed = now - lastRefill;

        if (elapsed <= 0) {
            return;
        }

        long tokensToAdd = elapsed / refillRateNanos;
        if (tokensToAdd > 0) {
            if (lastRefillTime.compareAndSet(lastRefill, now)) {
                long currentTokens;
                long newTokens;
                do {
                    currentTokens = tokens.get();
                    newTokens = Math.min(capacity, currentTokens + tokensToAdd);
                } while (!tokens.compareAndSet(currentTokens, newTokens));
            }
        }
    }
}
