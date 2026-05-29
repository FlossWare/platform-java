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

package org.flossware.jplatform.config.etcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EtcdConfigSourceTest {

    @Mock
    private Client client;

    @Mock
    private KV kvClient;

    @Mock
    private GetResponse getResponse;

    @Mock
    private PutResponse putResponse;

    @Mock
    private DeleteResponse deleteResponse;

    private EtcdConfigSourceConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        config = EtcdConfigSourceConfig.builder()
            .endpoints("http://localhost:2379")
            .watchEnabled(false)
            .build();

        when(client.getKVClient()).thenReturn(kvClient);
    }

    @Test
    void testConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new EtcdConfigSource(null);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testLoadConfig() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        when(getResponse.getKvs()).thenReturn(Collections.emptyList());
        when(kvClient.get(any(ByteSequence.class), any())).thenReturn(CompletableFuture.completedFuture(getResponse));

        Map<String, String> loadedConfig = source.loadConfig();
        assertNotNull(loadedConfig);
    }

    @Test
    void testGetConfig() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);
        source.getConfigCache().put("test.key", "test-value");

        assertEquals("test-value", source.getConfig("test.key"));
    }

    @Test
    void testGetConfigNotFound() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        assertNull(source.getConfig("nonexistent"));
    }

    @Test
    void testSetConfig() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        when(kvClient.put(any(ByteSequence.class), any(ByteSequence.class)))
            .thenReturn(CompletableFuture.completedFuture(putResponse));

        source.setConfig("new.key", "new-value");

        verify(kvClient).put(any(ByteSequence.class), any(ByteSequence.class));
        assertEquals("new-value", source.getConfig("new.key"));
    }

    @Test
    void testDeleteConfig() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);
        source.getConfigCache().put("delete.key", "value");

        when(kvClient.delete(any(ByteSequence.class)))
            .thenReturn(CompletableFuture.completedFuture(deleteResponse));

        source.deleteConfig("delete.key");

        verify(kvClient).delete(any(ByteSequence.class));
        assertNull(source.getConfig("delete.key"));
    }

    @Test
    void testSetConfigNotStarted() {
        EtcdConfigSource source = new EtcdConfigSource(config);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            source.setConfig("key", "value");
        });
        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void testDeleteConfigNotStarted() {
        EtcdConfigSource source = new EtcdConfigSource(config);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            source.deleteConfig("key");
        });
        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void testAddListener() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        List<Map<String, String>> receivedConfigs = new ArrayList<>();
        source.addListener(receivedConfigs::add);

        assertTrue(receivedConfigs.isEmpty());
    }

    @Test
    void testRemoveListener() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        Consumer<Map<String, String>> listener = cfg -> {};
        source.addListener(listener);
        source.removeListener(listener);
    }

    @Test
    void testClose() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        source.close();

        verify(client).close();
        assertTrue(source.getConfigCache().isEmpty());
    }

    @Test
    void testGetClient() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        assertSame(client, source.getClient());
    }

    @Test
    void testLoadConfigBeforeStart() {
        EtcdConfigSource source = new EtcdConfigSource(config);

        Map<String, String> loaded = source.loadConfig();
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testStartIdempotent() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        when(getResponse.getKvs()).thenReturn(Collections.emptyList());
        when(kvClient.get(any(ByteSequence.class), any())).thenReturn(CompletableFuture.completedFuture(getResponse));

        source.start();
        source.start();

        verify(kvClient, times(0)).get(any(ByteSequence.class), any());
        source.close();
    }

    @Test
    void testLoadConfigWithValues() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        KeyValue kv1 = mock(KeyValue.class);
        when(kv1.getKey()).thenReturn(ByteSequence.from("/config/app.name", StandardCharsets.UTF_8));
        when(kv1.getValue()).thenReturn(ByteSequence.from("myapp", StandardCharsets.UTF_8));

        KeyValue kv2 = mock(KeyValue.class);
        when(kv2.getKey()).thenReturn(ByteSequence.from("/config/app.version", StandardCharsets.UTF_8));
        when(kv2.getValue()).thenReturn(ByteSequence.from("1.0", StandardCharsets.UTF_8));

        when(getResponse.getKvs()).thenReturn(Arrays.asList(kv1, kv2));
        when(kvClient.get(any(ByteSequence.class), any())).thenReturn(CompletableFuture.completedFuture(getResponse));

        Map<String, String> loaded = source.loadConfig();
        assertEquals(0, loaded.size());
    }

    @Test
    void testSetConfigUpdatesCache() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        when(kvClient.put(any(ByteSequence.class), any(ByteSequence.class)))
            .thenReturn(CompletableFuture.completedFuture(putResponse));

        source.setConfig("cache.key", "cache-value");

        Map<String, String> loaded = source.loadConfig();
        assertEquals("cache-value", loaded.get("cache.key"));
    }

    @Test
    void testDeleteConfigUpdatesCache() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);
        source.getConfigCache().put("to.delete", "value");

        when(kvClient.delete(any(ByteSequence.class)))
            .thenReturn(CompletableFuture.completedFuture(deleteResponse));

        source.deleteConfig("to.delete");

        Map<String, String> loaded = source.loadConfig();
        assertFalse(loaded.containsKey("to.delete"));
    }

    @Test
    void testMultipleListeners() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        List<Map<String, String>> receivedConfigs1 = new ArrayList<>();
        List<Map<String, String>> receivedConfigs2 = new ArrayList<>();

        source.addListener(receivedConfigs1::add);
        source.addListener(receivedConfigs2::add);

        assertTrue(receivedConfigs1.isEmpty());
        assertTrue(receivedConfigs2.isEmpty());
    }

    @Test
    void testSetConfigException() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        when(kvClient.put(any(ByteSequence.class), any(ByteSequence.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Connection failed")));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            source.setConfig("fail.key", "value");
        });
        assertTrue(exception.getMessage().contains("Failed to set config"));
    }

    @Test
    void testDeleteConfigException() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        when(kvClient.delete(any(ByteSequence.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Connection failed")));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            source.deleteConfig("fail.key");
        });
        assertTrue(exception.getMessage().contains("Failed to delete config"));
    }

    @Test
    void testGetConfigCacheReturnsInternalCache() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);
        source.getConfigCache().put("internal.key", "internal-value");

        assertEquals("internal-value", source.getConfigCache().get("internal.key"));
    }

    @Test
    void testCloseWithNullWatcher() {
        EtcdConfigSource source = new EtcdConfigSource(config, client);

        assertDoesNotThrow(() -> source.close());
        verify(client).close();
    }

    @Test
    void testConfigWithCustomPrefix() {
        EtcdConfigSourceConfig customConfig = EtcdConfigSourceConfig.builder()
            .keyPrefix("/custom/prefix")
            .watchEnabled(false)
            .build();

        EtcdConfigSource source = new EtcdConfigSource(customConfig, client);

        when(kvClient.put(any(ByteSequence.class), any(ByteSequence.class)))
            .thenReturn(CompletableFuture.completedFuture(putResponse));

        source.setConfig("test", "value");

        verify(kvClient).put(any(ByteSequence.class), any(ByteSequence.class));
    }

    @Test
    void testConfigWithTrailingSlashPrefix() {
        EtcdConfigSourceConfig customConfig = EtcdConfigSourceConfig.builder()
            .keyPrefix("/prefix/")
            .watchEnabled(false)
            .build();

        EtcdConfigSource source = new EtcdConfigSource(customConfig, client);

        when(kvClient.put(any(ByteSequence.class), any(ByteSequence.class)))
            .thenReturn(CompletableFuture.completedFuture(putResponse));

        source.setConfig("test", "value");

        verify(kvClient).put(any(ByteSequence.class), any(ByteSequence.class));
    }
}
