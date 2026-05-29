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

package org.flossware.jplatform.config.zookeeper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZooKeeperConfigSourceConfigTest {

    @Test
    void testBuilderDefaults() {
        ZooKeeperConfigSourceConfig config = ZooKeeperConfigSourceConfig.builder()
            .build();

        assertEquals("localhost:2181", config.getConnectString());
        assertEquals("/config", config.getBasePath());
        assertEquals(60000, config.getSessionTimeoutMs());
        assertEquals(15000, config.getConnectionTimeoutMs());
        assertEquals(3, config.getRetryCount());
        assertEquals(1000, config.getRetryIntervalMs());
    }

    @Test
    void testBuilderWithAllFields() {
        ZooKeeperConfigSourceConfig config = ZooKeeperConfigSourceConfig.builder()
            .connectString("zk1:2181,zk2:2181,zk3:2181")
            .basePath("/config/myapp")
            .sessionTimeoutMs(30000)
            .connectionTimeoutMs(10000)
            .retryCount(5)
            .retryIntervalMs(2000)
            .build();

        assertEquals("zk1:2181,zk2:2181,zk3:2181", config.getConnectString());
        assertEquals("/config/myapp", config.getBasePath());
        assertEquals(30000, config.getSessionTimeoutMs());
        assertEquals(10000, config.getConnectionTimeoutMs());
        assertEquals(5, config.getRetryCount());
        assertEquals(2000, config.getRetryIntervalMs());
    }

    @Test
    void testMissingConnectString() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            ZooKeeperConfigSourceConfig.builder()
                .connectString(null)
                .build();
        });
        assertTrue(exception.getMessage().contains("Connect string"));
    }

    @Test
    void testEmptyConnectString() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            ZooKeeperConfigSourceConfig.builder()
                .connectString("  ")
                .build();
        });
        assertTrue(exception.getMessage().contains("Connect string"));
    }

    @Test
    void testMissingBasePath() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            ZooKeeperConfigSourceConfig.builder()
                .basePath(null)
                .build();
        });
        assertTrue(exception.getMessage().contains("Base path"));
    }

    @Test
    void testEmptyBasePath() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            ZooKeeperConfigSourceConfig.builder()
                .basePath("")
                .build();
        });
        assertTrue(exception.getMessage().contains("Base path"));
    }

    @Test
    void testSessionTimeoutTooSmall() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ZooKeeperConfigSourceConfig.builder()
                .sessionTimeoutMs(500)
                .build();
        });
        assertTrue(exception.getMessage().contains("Session timeout"));
    }

    @Test
    void testConnectionTimeoutTooSmall() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ZooKeeperConfigSourceConfig.builder()
                .connectionTimeoutMs(999)
                .build();
        });
        assertTrue(exception.getMessage().contains("Connection timeout"));
    }

    @Test
    void testRetryCountNegative() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ZooKeeperConfigSourceConfig.builder()
                .retryCount(-1)
                .build();
        });
        assertTrue(exception.getMessage().contains("Retry count"));
    }

    @Test
    void testRetryIntervalTooSmall() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ZooKeeperConfigSourceConfig.builder()
                .retryIntervalMs(50)
                .build();
        });
        assertTrue(exception.getMessage().contains("Retry interval"));
    }

    @Test
    void testBuilderChaining() {
        ZooKeeperConfigSourceConfig.Builder builder = ZooKeeperConfigSourceConfig.builder();
        assertSame(builder, builder.connectString("localhost:2181"));
        assertSame(builder, builder.basePath("/config"));
        assertSame(builder, builder.sessionTimeoutMs(30000));
        assertSame(builder, builder.connectionTimeoutMs(10000));
        assertSame(builder, builder.retryCount(5));
        assertSame(builder, builder.retryIntervalMs(1000));
    }

    @Test
    void testEnsembleConfiguration() {
        ZooKeeperConfigSourceConfig config = ZooKeeperConfigSourceConfig.builder()
            .connectString("zk1:2181,zk2:2181,zk3:2181")
            .build();

        assertEquals("zk1:2181,zk2:2181,zk3:2181", config.getConnectString());
    }

    @Test
    void testCustomBasePath() {
        ZooKeeperConfigSourceConfig config = ZooKeeperConfigSourceConfig.builder()
            .basePath("/myapp/config/production")
            .build();

        assertEquals("/myapp/config/production", config.getBasePath());
    }

    @Test
    void testRetryCountZero() {
        ZooKeeperConfigSourceConfig config = ZooKeeperConfigSourceConfig.builder()
            .retryCount(0)
            .build();

        assertEquals(0, config.getRetryCount());
    }

    @Test
    void testMinimumTimeouts() {
        ZooKeeperConfigSourceConfig config = ZooKeeperConfigSourceConfig.builder()
            .sessionTimeoutMs(1000)
            .connectionTimeoutMs(1000)
            .retryIntervalMs(100)
            .build();

        assertEquals(1000, config.getSessionTimeoutMs());
        assertEquals(1000, config.getConnectionTimeoutMs());
        assertEquals(100, config.getRetryIntervalMs());
    }
}
