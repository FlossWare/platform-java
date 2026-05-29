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

package org.flossware.jplatform.storage.redis;

import org.flossware.jplatform.api.VolumeMount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedisVolumeManagerTest {

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    private RedisStorageConfig config;
    private List<VolumeMount> volumeMounts;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        config = RedisStorageConfig.builder()
            .host("localhost")
            .port(6379)
            .build();

        volumeMounts = Arrays.asList(
            new VolumeMount("data", "/app/data", true, 1024),
            new VolumeMount("cache", "/app/cache", false, 512),
            new VolumeMount("logs", "/app/logs", true, 0)
        );

        when(jedisPool.getResource()).thenReturn(jedis);
    }

    @Test
    void testConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new RedisVolumeManager(null, volumeMounts);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testConstructorNullVolumeMounts() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new RedisVolumeManager(config, null);
        });
        assertTrue(exception.getMessage().contains("Volume mounts"));
    }

    @Test
    void testGetVolumesReturnsAllMounts() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        List<VolumeMount> volumes = manager.getVolumes();
        assertEquals(3, volumes.size());
    }

    @Test
    void testVolumeExists() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        assertTrue(manager.volumeExists("data"));
        assertTrue(manager.volumeExists("cache"));
        assertTrue(manager.volumeExists("logs"));
        assertFalse(manager.volumeExists("nonexistent"));
    }

    @Test
    void testGetVolumePathCreatesDirectory() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        Path volumePath = manager.getVolumePath("data");
        assertNotNull(volumePath);
        assertTrue(Files.exists(volumePath));
        assertTrue(Files.isDirectory(volumePath));
    }

    @Test
    void testGetVolumePathUndefinedVolume() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            manager.getVolumePath("undefined");
        });
        assertTrue(exception.getMessage().contains("not defined"));
    }

    @Test
    void testGetVolumeSizeLimit() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        assertEquals(1024 * 1024 * 1024, manager.getVolumeSizeLimit("data"));
        assertEquals(512 * 1024 * 1024, manager.getVolumeSizeLimit("cache"));
        assertEquals(0, manager.getVolumeSizeLimit("logs"));
    }

    @Test
    void testIsPersistent() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        assertTrue(manager.isPersistent("data"));
        assertFalse(manager.isPersistent("cache"));
        assertTrue(manager.isPersistent("logs"));
    }

    @Test
    void testGetVolumeUsageBytesEmpty() throws IOException {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        when(jedis.hgetAll(anyString())).thenReturn(new HashMap<>());

        long usage = manager.getVolumeUsageBytes("data");
        assertEquals(0, usage);
    }

    @Test
    void testGetVolumeUsageBytesWithFiles() throws IOException {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        Map<String, String> files = new HashMap<>();
        files.put("file1.txt", "100");
        files.put("file2.txt", "200");
        files.put("file3.txt", "300");

        when(jedis.hgetAll(anyString())).thenReturn(files);

        long usage = manager.getVolumeUsageBytes("data");
        assertEquals(600, usage);
    }

    @Test
    void testRecordFile() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        manager.recordFile("data", "test.txt", 1024);

        verify(jedis).hset(anyString(), eq("test.txt"), eq("1024"));
    }

    @Test
    void testRemoveFile() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        manager.removeFile("data", "test.txt");

        verify(jedis).hdel(anyString(), eq("test.txt"));
    }

    @Test
    void testRecordFileNotInitialized() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            manager.recordFile("data", "test.txt", 100);
        });
        assertTrue(exception.getMessage().contains("not initialized"));
    }

    @Test
    void testRemoveFileNotInitialized() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            manager.removeFile("data", "test.txt");
        });
        assertTrue(exception.getMessage().contains("not initialized"));
    }

    @Test
    void testClose() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        manager.close();
        verify(jedisPool).close();
    }

    @Test
    void testGetJedisPool() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        assertSame(jedisPool, manager.getJedisPool());
    }

    @Test
    void testMultipleVolumesIndependent() throws IOException {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        Map<String, String> dataFiles = new HashMap<>();
        dataFiles.put("file.txt", "100");

        Map<String, String> cacheFiles = new HashMap<>();
        cacheFiles.put("cache.txt", "200");

        when(jedis.hgetAll(contains("data"))).thenReturn(dataFiles);
        when(jedis.hgetAll(contains("cache"))).thenReturn(cacheFiles);

        assertEquals(100, manager.getVolumeUsageBytes("data"));
        assertEquals(200, manager.getVolumeUsageBytes("cache"));
    }

    @Test
    void testStartIdempotent() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts);

        manager.start();
        manager.start();

        manager.close();
    }

    @Test
    void testRecordMultipleFiles() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        manager.recordFile("data", "file1.txt", 100);
        manager.recordFile("data", "file2.txt", 200);
        manager.recordFile("data", "file3.txt", 300);

        verify(jedis, times(3)).hset(anyString(), anyString(), anyString());
    }

    @Test
    void testGetVolumeUsageBytesUndefined() {
        RedisVolumeManager manager = new RedisVolumeManager(config, volumeMounts, jedisPool);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            manager.getVolumeUsageBytes("undefined");
        });
        assertTrue(exception.getMessage().contains("not defined"));
    }
}
