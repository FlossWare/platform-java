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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Consul-based configuration source.
 * Loads configuration from Consul KV store and supports dynamic updates.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Stores configuration in Consul KV store</li>
 *   <li>Supports configuration watching via polling</li>
 *   <li>Maintains local cache for fast access</li>
 *   <li>Thread-safe operations</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections.
 *
 * @since 1.1
 */
public class ConsulConfigSource implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ConsulConfigSource.class);

    private final ConsulConfigSourceConfig config;
    private final Map<String, String> configCache;
    private final Map<String, Consumer<Map<String, String>>> listeners;
    private Consul consul;
    private ScheduledExecutorService watchExecutor;
    private volatile boolean started = false;

    /**
     * Constructs a new Consul configuration source.
     *
     * @param config the Consul configuration
     */
    public ConsulConfigSource(ConsulConfigSourceConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        this.config = config;
        this.configCache = new ConcurrentHashMap<>();
        this.listeners = new ConcurrentHashMap<>();
    }

    /**
     * Package-private constructor for testing.
     *
     * @param config the configuration
     * @param consul the Consul client
     */
    ConsulConfigSource(ConsulConfigSourceConfig config, Consul consul) {
        this.config = config;
        this.consul = consul;
        this.configCache = new ConcurrentHashMap<>();
        this.listeners = new ConcurrentHashMap<>();
        this.started = true;
    }

    /**
     * Starts the Consul configuration source.
     * Must be called before using the configuration source.
     */
    public void start() {
        if (started) {
            return;
        }

        try {
            String url = "http://" + config.getHost() + ":" + config.getPort();
            Consul.Builder builder = Consul.builder()
                .withUrl(url);

            if (config.getToken() != null && !config.getToken().isEmpty()) {
                builder.withAclToken(config.getToken());
            }

            consul = builder.build();

            loadAllConfig();

            if (config.isWatchEnabled()) {
                startWatching();
            }

            started = true;
            logger.info("Consul config source started: {}:{}", config.getHost(), config.getPort());
        } catch (Exception e) {
            logger.error("Failed to start Consul config source", e);
            throw new RuntimeException("Failed to start Consul client", e);
        }
    }

    /**
     * Loads all configuration from Consul.
     *
     * @return map of all configuration key-value pairs
     */
    public Map<String, String> loadConfig() {
        if (!started) {
            return new HashMap<>();
        }
        return new HashMap<>(configCache);
    }

    /**
     * Gets a configuration value by key.
     *
     * @param key the configuration key
     * @return the value, or null if not found
     */
    public String getConfig(String key) {
        return configCache.get(key);
    }

    /**
     * Sets a configuration value in Consul.
     *
     * @param key the configuration key
     * @param value the configuration value
     */
    public void setConfig(String key, String value) {
        if (!started) {
            throw new IllegalStateException("Config source not started");
        }

        KeyValueClient kvClient = consul.keyValueClient();
        String fullKey = buildFullKey(key);
        kvClient.putValue(fullKey, value);

        configCache.put(key, value);
        logger.debug("Set config: {} = {}", key, value);
    }

    /**
     * Deletes a configuration key from Consul.
     *
     * @param key the configuration key
     */
    public void deleteConfig(String key) {
        if (!started) {
            throw new IllegalStateException("Config source not started");
        }

        KeyValueClient kvClient = consul.keyValueClient();
        String fullKey = buildFullKey(key);
        kvClient.deleteKey(fullKey);

        configCache.remove(key);
        logger.debug("Deleted config: {}", key);
    }

    /**
     * Registers a listener for configuration changes.
     *
     * @param listenerId unique identifier for this listener
     * @param listener callback invoked when configuration changes
     */
    public void addListener(String listenerId, Consumer<Map<String, String>> listener) {
        listeners.put(listenerId, listener);
    }

    /**
     * Removes a configuration change listener.
     *
     * @param listenerId the listener identifier
     */
    public void removeListener(String listenerId) {
        listeners.remove(listenerId);
    }

    @Override
    public void close() {
        if (watchExecutor != null) {
            watchExecutor.shutdown();
            try {
                if (!watchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    watchExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                watchExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (consul != null) {
            consul.destroy();
        }

        configCache.clear();
        listeners.clear();
        started = false;

        logger.info("Consul config source closed");
    }

    /**
     * Returns the Consul client.
     *
     * @return the client
     */
    public Consul getConsul() {
        return consul;
    }

    /**
     * Returns the configuration cache.
     *
     * @return the cache
     */
    Map<String, String> getConfigCache() {
        return configCache;
    }

    private void loadAllConfig() {
        try {
            KeyValueClient kvClient = consul.keyValueClient();
            List<Value> values = kvClient.getValues(config.getKeyPrefix());

            if (values == null || values.isEmpty()) {
                logger.info("No configuration found at prefix: {}", config.getKeyPrefix());
                return;
            }

            String prefix = config.getKeyPrefix() + "/";
            for (Value value : values) {
                String fullKey = value.getKey();

                // Validate key has expected prefix
                if (!fullKey.startsWith(prefix)) {
                    logger.warn("Unexpected key '{}' not under prefix '{}', skipping", fullKey, prefix);
                    continue;
                }

                if (value.getValueAsString().isPresent()) {
                    String key = fullKey.substring(prefix.length());
                    String val = value.getValueAsString().get();
                    configCache.put(key, val);
                }
            }

            logger.debug("Loaded {} config entries from Consul", configCache.size());
        } catch (Exception e) {
            logger.warn("Failed to load config from Consul", e);
        }
    }

    private void startWatching() {
        watchExecutor = Executors.newSingleThreadScheduledExecutor();
        watchExecutor.scheduleAtFixedRate(
            this::checkForUpdates,
            config.getWatchIntervalSeconds(),
            config.getWatchIntervalSeconds(),
            TimeUnit.SECONDS
        );
    }

    private void checkForUpdates() {
        try {
            Map<String, String> oldCache = new HashMap<>(configCache);
            loadAllConfig();

            if (!configCache.equals(oldCache)) {
                notifyListeners();
            }
        } catch (Exception e) {
            logger.error("Error checking for config updates", e);
        }
    }

    private void notifyListeners() {
        Map<String, String> snapshot = new HashMap<>(configCache);
        for (Consumer<Map<String, String>> listener : listeners.values()) {
            try {
                listener.accept(snapshot);
            } catch (Exception e) {
                logger.error("Error notifying listener", e);
            }
        }
    }

    private String buildFullKey(String key) {
        return config.getKeyPrefix() + "/" + key;
    }

    private String extractKey(String fullKey) {
        String prefix = config.getKeyPrefix() + "/";
        if (fullKey.startsWith(prefix)) {
            return fullKey.substring(prefix.length());
        }
        return fullKey;
    }
}
