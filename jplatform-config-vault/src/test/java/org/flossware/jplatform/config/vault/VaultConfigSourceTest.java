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

package org.flossware.jplatform.config.vault;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.api.Logical;
import com.bettercloud.vault.response.LogicalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VaultConfigSourceTest {

    @Mock
    private Vault vault;

    @Mock
    private Logical logical;

    @Mock
    private LogicalResponse logicalResponse;

    private VaultConfigSourceConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        config = VaultConfigSourceConfig.builder()
            .address("http://localhost:8200")
            .token("test-token")
            .secretPath("secret/config")
            .build();

        when(vault.logical()).thenReturn(logical);
    }

    @Test
    void testConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new VaultConfigSource(null);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testLoadConfig() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        when(logical.read(anyString())).thenReturn(logicalResponse);
        when(logicalResponse.getData()).thenReturn(Collections.emptyMap());

        Map<String, String> loadedConfig = source.loadConfig();
        assertNotNull(loadedConfig);
    }

    @Test
    void testGetConfig() {
        VaultConfigSource source = new VaultConfigSource(config, vault);
        source.getConfigCache().put("test.key", "test-value");

        assertEquals("test-value", source.getConfig("test.key"));
    }

    @Test
    void testGetConfigNotFound() {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        assertNull(source.getConfig("nonexistent"));
    }

    @Test
    void testSetConfig() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        when(logical.write(anyString(), anyMap())).thenReturn(logicalResponse);

        source.setConfig("new.key", "new-value");

        verify(logical).write(anyString(), anyMap());
        assertEquals("new-value", source.getConfig("new.key"));
    }

    @Test
    void testDeleteConfig() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);
        source.getConfigCache().put("delete.key", "value");

        when(logical.delete(anyString())).thenReturn(logicalResponse);

        source.deleteConfig("delete.key");

        verify(logical).delete(anyString());
        assertNull(source.getConfig("delete.key"));
    }

    @Test
    void testSetConfigNotStarted() {
        VaultConfigSource source = new VaultConfigSource(config);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            source.setConfig("key", "value");
        });
        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void testDeleteConfigNotStarted() {
        VaultConfigSource source = new VaultConfigSource(config);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            source.deleteConfig("key");
        });
        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void testRefreshNotStarted() {
        VaultConfigSource source = new VaultConfigSource(config);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            source.refresh();
        });
        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void testClose() {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        source.close();

        assertTrue(source.getConfigCache().isEmpty());
    }

    @Test
    void testGetVault() {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        assertSame(vault, source.getVault());
    }

    @Test
    void testLoadConfigBeforeStart() {
        VaultConfigSource source = new VaultConfigSource(config);

        Map<String, String> loaded = source.loadConfig();
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testStartIdempotent() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        when(logical.read(anyString())).thenReturn(logicalResponse);
        when(logicalResponse.getData()).thenReturn(Collections.emptyMap());

        source.start();
        source.start();

        verify(logical, times(0)).read(anyString());
        source.close();
    }

    @Test
    void testLoadConfigWithValues() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        Map<String, String> secretData = new HashMap<>();
        secretData.put("database.host", "localhost");
        secretData.put("database.port", "5432");

        when(logical.read(anyString())).thenReturn(logicalResponse);
        when(logicalResponse.getData()).thenReturn(secretData);

        Map<String, String> loaded = source.loadConfig();
        assertEquals(0, loaded.size());
    }

    @Test
    void testSetConfigUpdatesCache() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        when(logical.write(anyString(), anyMap())).thenReturn(logicalResponse);

        source.setConfig("cache.key", "cache-value");

        Map<String, String> loaded = source.loadConfig();
        assertEquals("cache-value", loaded.get("cache.key"));
    }

    @Test
    void testDeleteConfigUpdatesCache() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);
        source.getConfigCache().put("to.delete", "value");

        when(logical.delete(anyString())).thenReturn(logicalResponse);

        source.deleteConfig("to.delete");

        Map<String, String> loaded = source.loadConfig();
        assertFalse(loaded.containsKey("to.delete"));
    }

    @Test
    void testSetConfigException() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        when(logical.write(anyString(), anyMap()))
            .thenThrow(new VaultException("Connection failed"));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            source.setConfig("fail.key", "value");
        });
        assertTrue(exception.getMessage().contains("Failed to set config"));
    }

    @Test
    void testDeleteConfigException() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        when(logical.delete(anyString()))
            .thenThrow(new VaultException("Connection failed"));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            source.deleteConfig("fail.key");
        });
        assertTrue(exception.getMessage().contains("Failed to delete config"));
    }

    @Test
    void testGetConfigCacheReturnsInternalCache() {
        VaultConfigSource source = new VaultConfigSource(config, vault);
        source.getConfigCache().put("internal.key", "internal-value");

        assertEquals("internal-value", source.getConfigCache().get("internal.key"));
    }

    @Test
    void testRefresh() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        Map<String, String> secretData = new HashMap<>();
        secretData.put("key", "value");

        when(logical.read(anyString())).thenReturn(logicalResponse);
        when(logicalResponse.getData()).thenReturn(secretData);

        source.refresh();

        verify(logical).read(anyString());
    }

    @Test
    void testRefreshUpdatesCache() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        Map<String, String> secretData = new HashMap<>();
        secretData.put("refreshed.key", "refreshed-value");

        when(logical.read(anyString())).thenReturn(logicalResponse);
        when(logicalResponse.getData()).thenReturn(secretData);

        source.refresh();

        assertEquals("refreshed-value", source.getConfig("refreshed.key"));
    }

    @Test
    void testLoadConfigWithNullResponse() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        when(logical.read(anyString())).thenReturn(null);

        Map<String, String> loaded = source.loadConfig();
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testLoadConfigWithNullData() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        when(logical.read(anyString())).thenReturn(logicalResponse);
        when(logicalResponse.getData()).thenReturn(null);

        Map<String, String> loaded = source.loadConfig();
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testConfigWithCustomSecretPath() throws VaultException {
        VaultConfigSourceConfig customConfig = VaultConfigSourceConfig.builder()
            .address("http://localhost:8200")
            .token("test-token")
            .secretPath("secret/myapp")
            .build();

        VaultConfigSource source = new VaultConfigSource(customConfig, vault);

        when(logical.write(anyString(), anyMap())).thenReturn(logicalResponse);

        source.setConfig("test", "value");

        verify(logical).write(eq("secret/myapp"), anyMap());
    }

    @Test
    void testMultipleConfigValues() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        when(logical.write(anyString(), anyMap())).thenReturn(logicalResponse);

        source.setConfig("key1", "value1");
        source.setConfig("key2", "value2");
        source.setConfig("key3", "value3");

        assertEquals("value1", source.getConfig("key1"));
        assertEquals("value2", source.getConfig("key2"));
        assertEquals("value3", source.getConfig("key3"));
    }

    @Test
    void testRefreshException() throws VaultException {
        VaultConfigSource source = new VaultConfigSource(config, vault);

        when(logical.read(anyString())).thenThrow(new VaultException("Connection failed"));

        assertDoesNotThrow(() -> source.refresh());
    }
}
