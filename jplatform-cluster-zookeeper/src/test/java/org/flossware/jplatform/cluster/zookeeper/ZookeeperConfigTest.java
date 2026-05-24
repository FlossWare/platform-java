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
}
