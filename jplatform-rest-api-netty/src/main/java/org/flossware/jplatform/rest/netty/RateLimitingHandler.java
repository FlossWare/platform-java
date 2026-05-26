package org.flossware.jplatform.rest.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty handler for rate limiting HTTP requests.
 * Implements both global and per-IP rate limiting using token bucket algorithm.
 *
 * <p>Thread Safety: This class is thread-safe. Uses concurrent collections
 * and atomic operations for rate limiting state.
 *
 * @since 1.1
 */
class RateLimitingHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingHandler.class);

    private final int globalRateLimitRps;
    private final int perIpRateLimitRps;
    private final TokenBucket globalBucket;
    private final Map<InetAddress, TokenBucket> perIpBuckets;

    /**
     * Creates a new rate limiting handler.
     *
     * @param globalRateLimitRps global requests per second (0 = unlimited)
     * @param perIpRateLimitRps per-IP requests per second (0 = unlimited)
     */
    RateLimitingHandler(int globalRateLimitRps, int perIpRateLimitRps) {
        this.globalRateLimitRps = globalRateLimitRps;
        this.perIpRateLimitRps = perIpRateLimitRps;
        this.globalBucket = globalRateLimitRps > 0 ? new TokenBucket(globalRateLimitRps) : null;
        this.perIpBuckets = perIpRateLimitRps > 0 ? new ConcurrentHashMap<>() : null;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof HttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }

        // Check global rate limit
        if (globalBucket != null && !globalBucket.tryAcquire()) {
            logger.warn("Global rate limit exceeded");
            sendRateLimitResponse(ctx, "Global rate limit exceeded");
            return;
        }

        // Check per-IP rate limit
        if (perIpBuckets != null) {
            InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            if (remoteAddress != null) {
                InetAddress clientIp = remoteAddress.getAddress();
                TokenBucket ipBucket = perIpBuckets.computeIfAbsent(
                    clientIp,
                    ip -> new TokenBucket(perIpRateLimitRps)
                );

                if (!ipBucket.tryAcquire()) {
                    logger.warn("Per-IP rate limit exceeded for {}", clientIp.getHostAddress());
                    sendRateLimitResponse(ctx, "Rate limit exceeded");
                    return;
                }
            }
        }

        // Rate limits passed, forward to next handler
        ctx.fireChannelRead(msg);
    }

    /**
     * Sends HTTP 429 Too Many Requests response.
     *
     * @param ctx the channel context
     * @param message the error message
     */
    private void sendRateLimitResponse(ChannelHandlerContext ctx, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.TOO_MANY_REQUESTS,
            Unpooled.copiedBuffer("{\"error\":\"" + message + "\"}", CharsetUtil.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.RETRY_AFTER, "1");

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception in rate limiting handler", cause);
        ctx.fireExceptionCaught(cause);
    }
}
