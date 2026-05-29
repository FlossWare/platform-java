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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyApiServerConfigTest {

    @Test
    void testBuilderDefaults() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();

        assertEquals("0.0.0.0", config.getHost());
        assertEquals(8080, config.getPort());
        assertEquals(1, config.getBossThreads());
        assertEquals(0, config.getWorkerThreads());
        assertEquals(65536, config.getMaxContentLength());
        assertTrue(config.isKeepAlive());
        assertEquals(128, config.getBacklog());
    }

    @Test
    void testBuilderCustomValues() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .host("127.0.0.1")
            .port(9090)
            .bossThreads(2)
            .workerThreads(8)
            .maxContentLength(131072)
            .keepAlive(false)
            .backlog(256)
            .build();

        assertEquals("127.0.0.1", config.getHost());
        assertEquals(9090, config.getPort());
        assertEquals(2, config.getBossThreads());
        assertEquals(8, config.getWorkerThreads());
        assertEquals(131072, config.getMaxContentLength());
        assertFalse(config.isKeepAlive());
        assertEquals(256, config.getBacklog());
    }

    @Test
    void testBuilderMissingHost() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            NettyApiServerConfig.builder()
                .host("")
                .build();
        });
        assertTrue(exception.getMessage().contains("Host"));
    }

    @Test
    void testBuilderInvalidPort() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .port(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("between 1 and 65535"));
    }

    @Test
    void testBuilderInvalidPortTooHigh() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .port(65536)
                .build();
        });
        assertTrue(exception.getMessage().contains("between 1 and 65535"));
    }

    @Test
    void testBuilderInvalidBossThreads() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .bossThreads(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("at least 1"));
    }

    @Test
    void testBuilderInvalidWorkerThreads() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .workerThreads(-1)
                .build();
        });
        assertTrue(exception.getMessage().contains("at least 0"));
    }

    @Test
    void testBuilderInvalidMaxContentLength() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .maxContentLength(512)
                .build();
        });
        assertTrue(exception.getMessage().contains("at least 1024 bytes"));
    }

    @Test
    void testBuilderInvalidBacklog() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            NettyApiServerConfig.builder()
                .backlog(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("at least 1"));
    }

    @Test
    void testBuilderWithAutoDetectWorkers() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .workerThreads(0)
            .build();

        assertEquals(0, config.getWorkerThreads());
    }

    @Test
    void testBuilderNullHost() {
        assertThrows(IllegalStateException.class, () ->
            NettyApiServerConfig.builder().host(null).build()
        );
    }

    @Test
    void testBuilderWhitespaceHost() {
        assertThrows(IllegalStateException.class, () ->
            NettyApiServerConfig.builder().host("   ").build()
        );
    }

    @Test
    void testBuilderNegativePort() {
        assertThrows(IllegalArgumentException.class, () ->
            NettyApiServerConfig.builder().port(-1).build()
        );
    }

    @Test
    void testBuilderMinValidPort() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(1)
            .build();

        assertEquals(1, config.getPort());
    }

    @Test
    void testBuilderMaxValidPort() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .port(65535)
            .build();

        assertEquals(65535, config.getPort());
    }

    @Test
    void testBuilderNegativeBossThreads() {
        assertThrows(IllegalArgumentException.class, () ->
            NettyApiServerConfig.builder().bossThreads(-1).build()
        );
    }

    @Test
    void testBuilderMinValidMaxContentLength() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .maxContentLength(1024)
            .build();

        assertEquals(1024, config.getMaxContentLength());
    }

    @Test
    void testBuilderMaxValidMaxContentLength() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .maxContentLength(100 * 1024 * 1024)
            .build();

        assertEquals(100 * 1024 * 1024, config.getMaxContentLength());
    }

    @Test
    void testBuilderMaxContentLengthTooLarge() {
        assertThrows(IllegalArgumentException.class, () ->
            NettyApiServerConfig.builder()
                .maxContentLength(100 * 1024 * 1024 + 1)
                .build()
        );
    }

    @Test
    void testBuilderReuse() {
        NettyApiServerConfig.Builder builder = NettyApiServerConfig.builder()
            .host("localhost");

        NettyApiServerConfig config1 = builder.build();
        NettyApiServerConfig config2 = builder.port(9090).build();

        assertEquals("localhost", config1.getHost());
        assertEquals("localhost", config2.getHost());
        assertEquals(8080, config1.getPort());
        assertEquals(9090, config2.getPort());
    }

    @Test
    void testBuilderChaining() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .host("127.0.0.1")
            .port(9090)
            .bossThreads(2)
            .workerThreads(8)
            .maxContentLength(131072)
            .keepAlive(false)
            .backlog(256)
            .build();

        assertEquals("127.0.0.1", config.getHost());
        assertEquals(9090, config.getPort());
        assertEquals(2, config.getBossThreads());
        assertEquals(8, config.getWorkerThreads());
        assertEquals(131072, config.getMaxContentLength());
        assertFalse(config.isKeepAlive());
        assertEquals(256, config.getBacklog());
    }

    @Test
    void testBuilderLargeBossThreads() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .bossThreads(100)
            .build();

        assertEquals(100, config.getBossThreads());
    }

    @Test
    void testBuilderLargeWorkerThreads() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .workerThreads(1000)
            .build();

        assertEquals(1000, config.getWorkerThreads());
    }

    @Test
    void testBuilderLargeBacklog() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .backlog(10000)
            .build();

        assertEquals(10000, config.getBacklog());
    }

    @Test
    void testDefaultRateLimits() {
        NettyApiServerConfig config = NettyApiServerConfig.builder().build();

        assertEquals(1000, config.getGlobalRateLimitRps());
        assertEquals(100, config.getPerIpRateLimitRps());
    }

    @Test
    void testCustomGlobalRateLimit() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .globalRateLimit(5000)
            .build();

        assertEquals(5000, config.getGlobalRateLimitRps());
    }

    @Test
    void testCustomPerIpRateLimit() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .perIpRateLimit(50)
            .build();

        assertEquals(50, config.getPerIpRateLimitRps());
    }

    @Test
    void testUnlimitedGlobalRateLimit() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .globalRateLimit(0)
            .build();

        assertEquals(0, config.getGlobalRateLimitRps());
    }

    @Test
    void testUnlimitedPerIpRateLimit() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .perIpRateLimit(0)
            .build();

        assertEquals(0, config.getPerIpRateLimitRps());
    }

    @Test
    void testNegativeGlobalRateLimit() {
        assertThrows(IllegalArgumentException.class, () ->
            NettyApiServerConfig.builder().globalRateLimit(-1).build()
        );
    }

    @Test
    void testNegativePerIpRateLimit() {
        assertThrows(IllegalArgumentException.class, () ->
            NettyApiServerConfig.builder().perIpRateLimit(-1).build()
        );
    }

    @Test
    void testBuilderChainingWithRateLimits() {
        NettyApiServerConfig config = NettyApiServerConfig.builder()
            .host("127.0.0.1")
            .port(9090)
            .globalRateLimit(2000)
            .perIpRateLimit(200)
            .build();

        assertEquals("127.0.0.1", config.getHost());
        assertEquals(9090, config.getPort());
        assertEquals(2000, config.getGlobalRateLimitRps());
        assertEquals(200, config.getPerIpRateLimitRps());
    }
}
