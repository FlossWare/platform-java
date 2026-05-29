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

package org.flossware.jplatform.cluster.zookeeper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZookeeperConfigTest {

    @Test
    void testDefaultValues() {
        ZookeeperConfig config = ZookeeperConfig.builder().build();

        assertEquals("localhost:2181", config.getConnectionString());
        assertEquals(30000, config.getSessionTimeoutMs());
        assertEquals(15000, config.getConnectionTimeoutMs());
        assertEquals(1000, config.getBaseSleepTimeMs());
        assertEquals(3, config.getMaxRetries());
        assertNull(config.getNamespace());
    }

    @Test
    void testCustomConnectionString() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .connectionString("zk1:2181,zk2:2181,zk3:2181")
            .build();

        assertEquals("zk1:2181,zk2:2181,zk3:2181", config.getConnectionString());
    }

    @Test
    void testCustomSessionTimeout() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .sessionTimeoutMs(60000)
            .build();

        assertEquals(60000, config.getSessionTimeoutMs());
    }

    @Test
    void testInvalidSessionTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
            ZookeeperConfig.builder().sessionTimeoutMs(0).build()
        );
    }

    @Test
    void testCustomConnectionTimeout() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .connectionTimeoutMs(30000)
            .build();

        assertEquals(30000, config.getConnectionTimeoutMs());
    }

    @Test
    void testInvalidConnectionTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
            ZookeeperConfig.builder().connectionTimeoutMs(-1).build()
        );
    }

    @Test
    void testCustomBaseSleepTime() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .baseSleepTimeMs(2000)
            .build();

        assertEquals(2000, config.getBaseSleepTimeMs());
    }

    @Test
    void testInvalidBaseSleepTime() {
        assertThrows(IllegalArgumentException.class, () ->
            ZookeeperConfig.builder().baseSleepTimeMs(0).build()
        );
    }

    @Test
    void testCustomMaxRetries() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .maxRetries(5)
            .build();

        assertEquals(5, config.getMaxRetries());
    }

    @Test
    void testInvalidMaxRetries() {
        assertThrows(IllegalArgumentException.class, () ->
            ZookeeperConfig.builder().maxRetries(-1).build()
        );
    }

    @Test
    void testNamespace() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .namespace("jplatform")
            .build();

        assertEquals("jplatform", config.getNamespace());
    }

    @Test
    void testBuilderChaining() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .connectionString("zk.example.com:2181")
            .sessionTimeoutMs(45000)
            .connectionTimeoutMs(20000)
            .baseSleepTimeMs(1500)
            .maxRetries(4)
            .namespace("my-namespace")
            .build();

        assertEquals("zk.example.com:2181", config.getConnectionString());
        assertEquals(45000, config.getSessionTimeoutMs());
        assertEquals(20000, config.getConnectionTimeoutMs());
        assertEquals(1500, config.getBaseSleepTimeMs());
        assertEquals(4, config.getMaxRetries());
        assertEquals("my-namespace", config.getNamespace());
    }

    @Test
    void testEmptyConnectionString() {
        assertThrows(IllegalStateException.class, () ->
            ZookeeperConfig.builder().connectionString("").build()
        );
    }

    @Test
    void testNullConnectionString() {
        assertThrows(IllegalStateException.class, () ->
            ZookeeperConfig.builder().connectionString(null).build()
        );
    }

    @Test
    void testWhitespaceConnectionString() {
        assertThrows(IllegalStateException.class, () ->
            ZookeeperConfig.builder().connectionString("   ").build()
        );
    }

    @Test
    void testNegativeSessionTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
            ZookeeperConfig.builder().sessionTimeoutMs(-1).build()
        );
    }

    @Test
    void testZeroConnectionTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
            ZookeeperConfig.builder().connectionTimeoutMs(0).build()
        );
    }

    @Test
    void testNegativeBaseSleepTime() {
        assertThrows(IllegalArgumentException.class, () ->
            ZookeeperConfig.builder().baseSleepTimeMs(-1).build()
        );
    }

    @Test
    void testZeroMaxRetries() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .maxRetries(0)
            .build();

        assertEquals(0, config.getMaxRetries());
    }

    @Test
    void testEmptyNamespace() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .namespace("")
            .build();

        assertEquals("", config.getNamespace());
    }

    @Test
    void testBuilderReuse() {
        ZookeeperConfig.Builder builder = ZookeeperConfig.builder()
            .connectionString("host1:2181");

        ZookeeperConfig config1 = builder.build();
        ZookeeperConfig config2 = builder
            .sessionTimeoutMs(45000)
            .build();

        assertEquals("host1:2181", config1.getConnectionString());
        assertEquals("host1:2181", config2.getConnectionString());
        assertEquals(30000, config1.getSessionTimeoutMs());
        assertEquals(45000, config2.getSessionTimeoutMs());
    }

    @Test
    void testMultipleHosts() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .connectionString("host1:2181,host2:2181,host3:2181,host4:2181")
            .build();

        assertEquals("host1:2181,host2:2181,host3:2181,host4:2181", config.getConnectionString());
    }

    @Test
    void testLargeTimeouts() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .sessionTimeoutMs(Integer.MAX_VALUE)
            .connectionTimeoutMs(Integer.MAX_VALUE)
            .baseSleepTimeMs(Integer.MAX_VALUE)
            .build();

        assertEquals(Integer.MAX_VALUE, config.getSessionTimeoutMs());
        assertEquals(Integer.MAX_VALUE, config.getConnectionTimeoutMs());
        assertEquals(Integer.MAX_VALUE, config.getBaseSleepTimeMs());
    }

    @Test
    void testLargeMaxRetries() {
        ZookeeperConfig config = ZookeeperConfig.builder()
            .maxRetries(Integer.MAX_VALUE)
            .build();

        assertEquals(Integer.MAX_VALUE, config.getMaxRetries());
    }
}
