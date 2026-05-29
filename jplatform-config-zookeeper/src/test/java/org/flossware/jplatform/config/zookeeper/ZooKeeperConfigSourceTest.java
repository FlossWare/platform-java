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

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ZooKeeperConfigSourceTest {

    private TestingServer zkServer;
    private ZooKeeperConfigSourceConfig config;

    @BeforeEach
    void setUp() throws Exception {
        zkServer = new TestingServer();

        config = ZooKeeperConfigSourceConfig.builder()
            .connectString(zkServer.getConnectString())
            .basePath("/test-config")
            .sessionTimeoutMs(10000)
            .connectionTimeoutMs(5000)
            .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (zkServer != null) {
            zkServer.close();
        }
    }

    @Test
    void testConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new ZooKeeperConfigSource(null);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testStartAndClose() {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);

        source.start();
        assertNotNull(source.getClient());

        source.close();
    }

    @Test
    void testSetAndGetConfig() throws Exception {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        source.setConfig("app.name", "test-app");
        source.setConfig("app.version", "1.0.0");

        assertEquals("test-app", source.getConfig("app.name"));
        assertEquals("1.0.0", source.getConfig("app.version"));

        source.close();
    }

    @Test
    void testLoadConfig() throws Exception {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        source.setConfig("key1", "value1");
        source.setConfig("key2", "value2");

        Map<String, String> allConfig = source.loadConfig();
        assertTrue(allConfig.size() >= 2);
        assertEquals("value1", allConfig.get("key1"));
        assertEquals("value2", allConfig.get("key2"));

        source.close();
    }

    @Test
    void testDeleteConfig() throws Exception {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        source.setConfig("temp.key", "temp-value");
        assertEquals("temp-value", source.getConfig("temp.key"));

        source.deleteConfig("temp.key");
        assertNull(source.getConfig("temp.key"));

        source.close();
    }

    @Test
    void testUpdateConfig() throws Exception {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        source.setConfig("update.key", "original");
        assertEquals("original", source.getConfig("update.key"));

        source.setConfig("update.key", "updated");
        assertEquals("updated", source.getConfig("update.key"));

        source.close();
    }

    @Test
    void testConfigListener() throws Exception {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        // Give time for watchers to initialize
        Thread.sleep(500);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, String>> receivedConfig = new AtomicReference<>();

        source.addListener("test-listener", cfg -> {
            receivedConfig.set(cfg);
            latch.countDown();
        });

        source.setConfig("listener.test", "value");

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(receivedConfig.get());
        assertTrue(receivedConfig.get().containsKey("listener.test"));

        source.close();
    }

    @Test
    void testRemoveListener() throws Exception {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        CountDownLatch latch = new CountDownLatch(1);

        source.addListener("test-listener", config -> latch.countDown());
        source.removeListener("test-listener");

        source.setConfig("test.key", "value");

        assertFalse(latch.await(1, TimeUnit.SECONDS));

        source.close();
    }

    @Test
    void testGetConfigNonExistent() {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        assertNull(source.getConfig("non.existent.key"));

        source.close();
    }

    @Test
    void testLoadConfigBeforeStart() {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);

        Map<String, String> config = source.loadConfig();
        assertTrue(config.isEmpty());
    }

    @Test
    void testSetConfigNotStarted() {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            source.setConfig("key", "value");
        });
        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void testDeleteConfigNotStarted() {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            source.deleteConfig("key");
        });
        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void testHierarchicalKeys() throws Exception {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        source.setConfig("app.database.host", "localhost");
        source.setConfig("app.database.port", "5432");
        source.setConfig("app.server.port", "8080");

        assertEquals("localhost", source.getConfig("app.database.host"));
        assertEquals("5432", source.getConfig("app.database.port"));
        assertEquals("8080", source.getConfig("app.server.port"));

        source.close();
    }

    @Test
    void testStartIdempotent() {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);

        source.start();
        source.start();

        source.close();
    }

    @Test
    void testMultipleListeners() throws Exception {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        CountDownLatch latch = new CountDownLatch(2);

        source.addListener("listener1", config -> latch.countDown());
        source.addListener("listener2", config -> latch.countDown());

        source.setConfig("multi.test", "value");

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        source.close();
    }

    @Test
    void testGetConfigCache() {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);

        Map<String, String> cache = source.getConfigCache();
        assertNotNull(cache);
    }

    @Test
    void testPackagePrivateConstructor() throws Exception {
        // Use package-private constructor with mock client
        CuratorFramework mockClient = org.mockito.Mockito.mock(CuratorFramework.class);

        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config, mockClient);

        assertNotNull(source);
        assertSame(mockClient, source.getClient());
        assertNotNull(source.getConfigCache());
    }

    @Test
    void testListenerException() throws Exception {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        // Add a listener that throws an exception
        source.addListener("failing-listener", cfg -> {
            throw new RuntimeException("Listener failure");
        });

        // Add a normal listener to verify others still get notified despite the exception
        CountDownLatch latch = new CountDownLatch(1);
        source.addListener("normal-listener", cfg -> latch.countDown());

        // Trigger notification by setting a value
        source.setConfig("test.key", "test-value");

        // Normal listener should still be notified despite the failing listener
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        source.close();
    }

    @Test
    void testHierarchicalConfigNested() throws Exception {
        ZooKeeperConfigSource source = new ZooKeeperConfigSource(config);
        source.start();

        // Set deeply nested configuration values to trigger recursive loading
        source.setConfig("app.database.connection.host", "localhost");
        source.setConfig("app.database.connection.port", "5432");
        source.setConfig("app.database.pool.size", "10");
        source.setConfig("app.cache.ttl", "300");

        // Reload to trigger recursive path traversal
        Map<String, String> loaded = source.loadConfig();

        assertEquals("localhost", loaded.get("app.database.connection.host"));
        assertEquals("5432", loaded.get("app.database.connection.port"));
        assertEquals("10", loaded.get("app.database.pool.size"));
        assertEquals("300", loaded.get("app.cache.ttl"));

        source.close();
    }

    @Test
    void testLoadConfigWithNonExistentBasePath() throws Exception {
        // Configure with a base path that doesn't exist
        ZooKeeperConfigSourceConfig configNoBasePath = ZooKeeperConfigSourceConfig.builder()
            .connectString(zkServer.getConnectString())
            .basePath("/nonexistent")
            .sessionTimeoutMs(10000)
            .connectionTimeoutMs(5000)
            .build();

        ZooKeeperConfigSource source = new ZooKeeperConfigSource(configNoBasePath);
        source.start();

        // Loading config should succeed but return empty since path doesn't exist
        Map<String, String> loaded = source.loadConfig();
        assertNotNull(loaded);

        source.close();
    }
}
