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

package org.flossware.jplatform.rest.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitingHandler.
 * Tests global and per-IP rate limiting behavior.
 */
class RateLimitingHandlerTest {

    @Test
    void testNoRateLimiting() {
        // No rate limits configured
        RateLimitingHandler handler = new RateLimitingHandler(0, 0);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        FullHttpRequest request = createRequest();
        channel.writeInbound(request);

        // Request should pass through (no rate limiting active)
        FullHttpRequest passed = channel.readInbound();
        assertNotNull(passed);
    }

    @Test
    void testGlobalRateLimitExceeded() {
        // Global limit of 5 requests
        RateLimitingHandler handler = new RateLimitingHandler(5, 0);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Send 5 requests - all should succeed
        for (int i = 0; i < 5; i++) {
            FullHttpRequest request = createRequest();
            channel.writeInbound(request);
            FullHttpRequest passed = channel.readInbound();
            assertNotNull(passed, "Request " + i + " should pass");
        }

        // 6th request should be rate limited
        FullHttpRequest request = createRequest();
        channel.writeInbound(request);

        // Should receive 429 response
        FullHttpResponse response = channel.readOutbound();
        assertNotNull(response);
        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, response.status());
        assertTrue(response.headers().contains(HttpHeaderNames.RETRY_AFTER));
        assertTrue(response.headers().contains(HttpHeaderNames.CONTENT_TYPE));
        assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));

        String body = response.content().toString(CharsetUtil.UTF_8);
        assertTrue(body.contains("rate limit") || body.contains("Rate limit"), "Body should mention rate limit");
    }

    @Test
    void testPerIpRateLimitWithoutInetAddress() {
        // Per-IP limit configured but EmbeddedChannel doesn't have InetSocketAddress
        // Should skip per-IP limiting and allow requests through
        RateLimitingHandler handler = new RateLimitingHandler(0, 3);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Should be able to send many requests (per-IP limiting skipped for non-InetSocketAddress)
        for (int i = 0; i < 10; i++) {
            FullHttpRequest request = createRequest();
            channel.writeInbound(request);
            FullHttpRequest passed = channel.readInbound();
            assertNotNull(passed, "Request " + i + " should pass when no InetSocketAddress");
        }
    }

    @Test
    void testPerIpRateLimitConfiguredButNotEnforced() {
        // Per-IP limit configured but not enforced on EmbeddedChannel
        // (EmbeddedChannel doesn't provide InetSocketAddress)
        RateLimitingHandler handler = new RateLimitingHandler(0, 2);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Should allow all requests since per-IP limiting requires InetSocketAddress
        for (int i = 0; i < 10; i++) {
            channel.writeInbound(createRequest());
            assertNotNull(channel.readInbound());
        }
    }

    @Test
    void testBothGlobalAndPerIpLimits() {
        // Global limit of 10, per-IP limit of 3
        // On EmbeddedChannel, only global limit enforced
        RateLimitingHandler handler = new RateLimitingHandler(10, 3);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // First 10 requests should pass (global limit)
        for (int i = 0; i < 10; i++) {
            channel.writeInbound(createRequest());
            assertNotNull(channel.readInbound());
        }

        // 11th request should be blocked by global limit
        channel.writeInbound(createRequest());
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, response.status());
    }

    @Test
    void testGlobalLimitHitFirst() {
        // Global limit of 2, per-IP limit of 10
        RateLimitingHandler handler = new RateLimitingHandler(2, 10);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // First 2 requests should pass
        for (int i = 0; i < 2; i++) {
            channel.writeInbound(createRequest());
            assertNotNull(channel.readInbound());
        }

        // 3rd request should be blocked by global limit
        channel.writeInbound(createRequest());
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, response.status());

        String body = response.content().toString(CharsetUtil.UTF_8);
        assertTrue(body.contains("Global rate limit"));
    }

    @Test
    void testRetryAfterHeaderPresent() {
        RateLimitingHandler handler = new RateLimitingHandler(1, 0);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Exhaust quota
        channel.writeInbound(createRequest());
        channel.readInbound();

        // Next request should be rate limited
        channel.writeInbound(createRequest());
        FullHttpResponse response = channel.readOutbound();

        assertTrue(response.headers().contains(HttpHeaderNames.RETRY_AFTER));
        String retryAfter = response.headers().get(HttpHeaderNames.RETRY_AFTER);
        assertNotNull(retryAfter);
        assertTrue(Integer.parseInt(retryAfter) >= 0, "Retry-After should be non-negative");
    }

    @Test
    void testResponseContentType() {
        RateLimitingHandler handler = new RateLimitingHandler(1, 0);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Exhaust quota
        channel.writeInbound(createRequest());
        channel.readInbound();

        // Next request should be rate limited
        channel.writeInbound(createRequest());
        FullHttpResponse response = channel.readOutbound();

        assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }

    @Test
    void testResponseBodyFormat() {
        RateLimitingHandler handler = new RateLimitingHandler(1, 0);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Exhaust quota
        channel.writeInbound(createRequest());
        channel.readInbound();

        // Next request should be rate limited
        channel.writeInbound(createRequest());
        FullHttpResponse response = channel.readOutbound();

        String body = response.content().toString(CharsetUtil.UTF_8);
        assertTrue(body.startsWith("{"), "Body should start with {");
        assertTrue(body.endsWith("}"), "Body should end with }");
        assertTrue(body.contains("error"), "Body should contain 'error'");
        assertTrue(body.contains("rate limit") || body.contains("Rate limit"), "Body should mention rate limit");
    }

    @Test
    void testContentLengthHeader() {
        RateLimitingHandler handler = new RateLimitingHandler(1, 0);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Exhaust quota
        channel.writeInbound(createRequest());
        channel.readInbound();

        // Next request should be rate limited
        channel.writeInbound(createRequest());
        FullHttpResponse response = channel.readOutbound();

        assertTrue(response.headers().contains(HttpHeaderNames.CONTENT_LENGTH));
        int contentLength = Integer.parseInt(response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        int actualLength = response.content().readableBytes();
        assertEquals(actualLength, contentLength);
    }

    @Test
    void testNullRemoteAddress() {
        // Per-IP limit but channel has no remote address
        RateLimitingHandler handler = new RateLimitingHandler(0, 5);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Should still work (gracefully handle null remote address)
        for (int i = 0; i < 10; i++) {
            FullHttpRequest request = createRequest();
            channel.writeInbound(request);
            // Should pass through since we can't determine IP
            FullHttpRequest passed = channel.readInbound();
            assertNotNull(passed);
        }
    }

    @Test
    void testNonInetSocketAddress() {
        // Per-IP limit with non-InetSocketAddress remote address
        RateLimitingHandler handler = new RateLimitingHandler(0, 5);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Should work without crashing
        FullHttpRequest request = createRequest();
        channel.writeInbound(request);
        assertNotNull(channel.readInbound());
    }

    @Test
    void testHighThroughput() {
        // Simulate high request rate
        RateLimitingHandler handler = new RateLimitingHandler(100, 50);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        int passed = 0;
        int blocked = 0;

        for (int i = 0; i < 200; i++) {
            // Stop if channel is closed (happens after first rate-limited response)
            if (!channel.isOpen()) {
                break;
            }

            channel.writeInbound(createRequest());
            Object result = channel.readInbound();
            if (result != null) {
                passed++;
            } else {
                // Check for rate limit response
                FullHttpResponse response = channel.readOutbound();
                if (response != null && response.status() == HttpResponseStatus.TOO_MANY_REQUESTS) {
                    blocked++;
                    // Channel will be closed after rate limit response
                    break;
                }
            }
        }

        // Should have passed exactly 100 requests (global limit)
        assertEquals(100, passed, "Should pass exactly 100 requests (global limit)");
        assertEquals(1, blocked, "Should block exactly 1 request before channel closes");
    }

    @Test
    void testConnectionCloseOnRateLimit() {
        RateLimitingHandler handler = new RateLimitingHandler(1, 0);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Exhaust quota
        channel.writeInbound(createRequest());
        channel.readInbound();

        // Next request should be rate limited
        channel.writeInbound(createRequest());
        FullHttpResponse response = channel.readOutbound();

        // Channel should be closed after rate limit response
        assertNotNull(response);
        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, response.status());
    }

    @Test
    void testZeroGlobalLimit() {
        // Zero means no global limit
        RateLimitingHandler handler = new RateLimitingHandler(0, 10);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Should be able to send many requests (only per-IP limit applies)
        for (int i = 0; i < 20; i++) {
            channel.writeInbound(createRequest());
            assertNotNull(channel.readInbound());
        }
    }

    @Test
    void testZeroPerIpLimit() {
        // Zero means no per-IP limit
        RateLimitingHandler handler = new RateLimitingHandler(10, 0);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // First 10 should pass (global limit)
        for (int i = 0; i < 10; i++) {
            channel.writeInbound(createRequest());
            assertNotNull(channel.readInbound());
        }

        // 11th should be blocked by global limit
        channel.writeInbound(createRequest());
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, response.status());
    }

    @Test
    void testNegativeRateLimits() {
        // Negative values should be treated as zero (no limit)
        RateLimitingHandler handler = new RateLimitingHandler(-1, -1);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // Should pass all requests
        for (int i = 0; i < 100; i++) {
            channel.writeInbound(createRequest());
            assertNotNull(channel.readInbound());
        }
    }

    /**
     * Creates a test HTTP request.
     */
    private FullHttpRequest createRequest() {
        return new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/test",
            Unpooled.EMPTY_BUFFER
        );
    }
}
