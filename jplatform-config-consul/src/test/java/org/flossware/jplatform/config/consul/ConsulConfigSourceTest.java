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

package org.flossware.jplatform.config.consul;

import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.model.kv.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsulConfigSourceTest {

    @Mock
    private Consul consul;

    @Mock
    private KeyValueClient kvClient;

    private ConsulConfigSourceConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        config = ConsulConfigSourceConfig.builder()
            .host("localhost")
            .port(8500)
            .watchEnabled(false)
            .build();

        when(consul.keyValueClient()).thenReturn(kvClient);
    }

    @Test
    void testConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new ConsulConfigSource(null);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testLoadConfig() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        when(kvClient.getValues(anyString())).thenReturn(Collections.emptyList());

        Map<String, String> loadedConfig = source.loadConfig();
        assertNotNull(loadedConfig);
    }

    @Test
    void testGetConfig() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);
        source.getConfigCache().put("test.key", "test-value");

        assertEquals("test-value", source.getConfig("test.key"));
    }

    @Test
    void testGetConfigNotFound() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        assertNull(source.getConfig("nonexistent"));
    }

    @Test
    void testSetConfig() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        source.setConfig("new.key", "new-value");

        verify(kvClient).putValue(anyString(), eq("new-value"));
        assertEquals("new-value", source.getConfig("new.key"));
    }

    @Test
    void testDeleteConfig() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);
        source.getConfigCache().put("delete.key", "value");

        source.deleteConfig("delete.key");

        verify(kvClient).deleteKey(anyString());
        assertNull(source.getConfig("delete.key"));
    }

    @Test
    void testSetConfigNotStarted() {
        ConsulConfigSource source = new ConsulConfigSource(config);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            source.setConfig("key", "value");
        });
        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void testDeleteConfigNotStarted() {
        ConsulConfigSource source = new ConsulConfigSource(config);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            source.deleteConfig("key");
        });
        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void testAddListener() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        List<Map<String, String>> receivedConfigs = new ArrayList<>();
        source.addListener("test-listener", receivedConfigs::add);

        // Trigger notification manually
        source.getConfigCache().put("key", "value");
    }

    @Test
    void testRemoveListener() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        source.addListener("test-listener", cfg -> {});
        source.removeListener("test-listener");
    }

    @Test
    void testClose() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        source.close();

        verify(consul).destroy();
        assertTrue(source.getConfigCache().isEmpty());
    }

    @Test
    void testGetConsul() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        assertSame(consul, source.getConsul());
    }

    @Test
    void testLoadConfigBeforeStart() {
        ConsulConfigSource source = new ConsulConfigSource(config);

        Map<String, String> loaded = source.loadConfig();
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testStartIdempotent() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        when(kvClient.getValues(anyString())).thenReturn(Collections.emptyList());

        source.start();
        source.start();

        verify(kvClient, times(0)).getValues(anyString());
        source.close();
    }

    @Test
    void testSetConfigNullKey() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        // Throws NullPointerException when using null as key in ConcurrentHashMap
        assertThrows(NullPointerException.class, () -> {
            source.setConfig(null, "value");
        });
    }

    @Test
    void testSetConfigNullValue() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        // Throws NullPointerException when putting null value in ConcurrentHashMap
        assertThrows(NullPointerException.class, () -> {
            source.setConfig("key", null);
        });
    }

    @Test
    void testDeleteConfigNullKey() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        // Throws NullPointerException when using null as key in ConcurrentHashMap
        assertThrows(NullPointerException.class, () -> {
            source.deleteConfig(null);
        });
    }

    @Test
    void testAddListenerNullName() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        // Throws NullPointerException when using null as key in ConcurrentHashMap
        assertThrows(NullPointerException.class, () -> {
            source.addListener(null, cfg -> {});
        });
    }

    @Test
    void testAddListenerNullListener() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        // Throws NullPointerException when putting null into ConcurrentHashMap
        assertThrows(NullPointerException.class, () -> {
            source.addListener("test", null);
        });
    }

    @Test
    void testRemoveListenerNullName() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        // Throws NullPointerException when using null as key in ConcurrentHashMap
        assertThrows(NullPointerException.class, () -> {
            source.removeListener(null);
        });
    }

    @Test
    void testRemoveNonexistentListener() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        // Should not throw
        source.removeListener("nonexistent");
    }

    @Test
    void testGetConfigNullKey() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        // Throws NullPointerException when accessing ConcurrentHashMap with null key
        assertThrows(NullPointerException.class, () -> {
            source.getConfig(null);
        });
    }

    @Test
    void testCloseNotStarted() {
        ConsulConfigSource source = new ConsulConfigSource(config);

        // Should not throw
        source.close();
    }

    @Test
    void testCloseMultipleTimes() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        source.close();
        source.close();
        source.close();

        // Close is not idempotent - calls destroy every time
        verify(consul, times(3)).destroy();
    }

    @Test
    void testGetConfigCache() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        Map<String, String> cache = source.getConfigCache();
        assertNotNull(cache);
        assertTrue(cache.isEmpty());

        cache.put("test", "value");
        assertEquals("value", source.getConfig("test"));
    }

    @Test
    void testLoadConfigAfterStart() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        when(kvClient.getValues(anyString())).thenReturn(Collections.emptyList());

        source.start();
        Map<String, String> loaded = source.loadConfig();

        assertNotNull(loaded);
    }

    @Test
    void testAddMultipleListeners() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        List<Map<String, String>> received1 = new ArrayList<>();
        List<Map<String, String>> received2 = new ArrayList<>();

        source.addListener("listener1", received1::add);
        source.addListener("listener2", received2::add);

        source.getConfigCache().put("key", "value");
    }

    @Test
    void testSetConfigUpdatesCache() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        source.setConfig("update.key", "initial");
        assertEquals("initial", source.getConfig("update.key"));

        source.setConfig("update.key", "updated");
        assertEquals("updated", source.getConfig("update.key"));

        verify(kvClient, times(2)).putValue(anyString(), anyString());
    }

    @Test
    void testDeleteConfigRemovesFromCache() {
        ConsulConfigSource source = new ConsulConfigSource(config, consul);

        source.getConfigCache().put("delete.me", "value");
        assertEquals("value", source.getConfig("delete.me"));

        source.deleteConfig("delete.me");
        assertNull(source.getConfig("delete.me"));
    }
}
