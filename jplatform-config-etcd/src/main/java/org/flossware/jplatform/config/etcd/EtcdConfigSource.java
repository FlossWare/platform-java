package org.flossware.jplatform.config.etcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Etcd-based configuration source.
 * Loads configuration from etcd KV store and supports dynamic updates.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Stores configuration in etcd KV store</li>
 *   <li>Supports configuration watching via etcd watch API</li>
 *   <li>Maintains local cache for fast access</li>
 *   <li>Thread-safe operations</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections.
 *
 * @since 1.1
 */
public class EtcdConfigSource implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(EtcdConfigSource.class);

    private final EtcdConfigSourceConfig config;
    private final Map<String, String> configCache;
    private final List<Consumer<Map<String, String>>> listeners;
    private Client client;
    private Watch.Watcher watcher;
    private volatile boolean started = false;

    /**
     * Constructs a new etcd configuration source.
     *
     * @param config the etcd configuration
     */
    public EtcdConfigSource(EtcdConfigSourceConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        this.config = config;
        this.configCache = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Package-private constructor for testing.
     *
     * @param config the configuration
     * @param client the etcd client
     */
    EtcdConfigSource(EtcdConfigSourceConfig config, Client client) {
        this.config = config;
        this.client = client;
        this.configCache = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.started = true;
    }

    /**
     * Starts the etcd configuration source.
     * Must be called before using the configuration source.
     */
    public void start() {
        if (started) {
            return;
        }

        try {
            String[] endpointArray = config.getEndpoints().split(",");

            if (config.getUsername() != null && config.getPassword() != null) {
                client = Client.builder()
                    .endpoints(endpointArray)
                    .user(ByteSequence.from(config.getUsername(), StandardCharsets.UTF_8))
                    .password(ByteSequence.from(config.getPassword(), StandardCharsets.UTF_8))
                    .build();
            } else {
                client = Client.builder()
                    .endpoints(endpointArray)
                    .build();
            }

            loadAllConfig();

            if (config.isWatchEnabled()) {
                startWatching();
            }

            started = true;
            logger.info("Etcd config source started: {}", config.getEndpoints());
        } catch (Exception e) {
            logger.error("Failed to start etcd config source", e);
            throw new RuntimeException("Failed to start etcd client", e);
        }
    }

    /**
     * Loads all configuration from etcd.
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
     * Sets a configuration value in etcd.
     *
     * @param key the configuration key
     * @param value the configuration value
     */
    public void setConfig(String key, String value) {
        if (!started) {
            throw new IllegalStateException("Config source not started");
        }

        try {
            KV kvClient = client.getKVClient();
            String fullKey = buildFullKey(key);
            kvClient.put(
                ByteSequence.from(fullKey, StandardCharsets.UTF_8),
                ByteSequence.from(value, StandardCharsets.UTF_8)
            ).get();

            configCache.put(key, value);
            logger.debug("Set config: {} = {}", key, value);
        } catch (Exception e) {
            logger.error("Failed to set config: {}", key, e);
            throw new RuntimeException("Failed to set config", e);
        }
    }

    /**
     * Deletes a configuration key from etcd.
     *
     * @param key the configuration key
     */
    public void deleteConfig(String key) {
        if (!started) {
            throw new IllegalStateException("Config source not started");
        }

        try {
            KV kvClient = client.getKVClient();
            String fullKey = buildFullKey(key);
            kvClient.delete(ByteSequence.from(fullKey, StandardCharsets.UTF_8)).get();

            configCache.remove(key);
            logger.debug("Deleted config: {}", key);
        } catch (Exception e) {
            logger.error("Failed to delete config: {}", key, e);
            throw new RuntimeException("Failed to delete config", e);
        }
    }

    /**
     * Registers a listener for configuration changes.
     *
     * @param listener callback invoked when configuration changes
     */
    public void addListener(Consumer<Map<String, String>> listener) {
        listeners.add(listener);
    }

    /**
     * Removes a configuration change listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(Consumer<Map<String, String>> listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        if (watcher != null) {
            watcher.close();
        }

        if (client != null) {
            client.close();
        }

        configCache.clear();
        listeners.clear();
        started = false;

        logger.info("Etcd config source closed");
    }

    /**
     * Returns the etcd client.
     *
     * @return the client
     */
    public Client getClient() {
        return client;
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
            KV kvClient = client.getKVClient();
            ByteSequence prefix = ByteSequence.from(config.getKeyPrefix(), StandardCharsets.UTF_8);
            GetOption option = GetOption.builder().isPrefix(true).build();

            GetResponse response = kvClient.get(prefix, option).get();

            for (KeyValue kv : response.getKvs()) {
                String fullKey = kv.getKey().toString(StandardCharsets.UTF_8);
                String key = extractKey(fullKey);
                String value = kv.getValue().toString(StandardCharsets.UTF_8);
                configCache.put(key, value);
            }
        } catch (Exception e) {
            logger.warn("Failed to load config from etcd", e);
        }
    }

    private void startWatching() {
        try {
            Watch watchClient = client.getWatchClient();
            ByteSequence prefix = ByteSequence.from(config.getKeyPrefix(), StandardCharsets.UTF_8);
            WatchOption option = WatchOption.builder().isPrefix(true).build();

            watcher = watchClient.watch(prefix, option, response -> {
                try {
                    boolean changed = false;
                    for (WatchEvent event : response.getEvents()) {
                        String fullKey = event.getKeyValue().getKey().toString(StandardCharsets.UTF_8);
                        String key = extractKey(fullKey);

                        if (event.getEventType() == WatchEvent.EventType.DELETE) {
                            configCache.remove(key);
                            changed = true;
                        } else {
                            String value = event.getKeyValue().getValue().toString(StandardCharsets.UTF_8);
                            configCache.put(key, value);
                            changed = true;
                        }
                    }

                    if (changed) {
                        notifyListeners();
                    }
                } catch (Exception e) {
                    logger.error("Error processing watch event", e);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to start watching", e);
        }
    }

    private void notifyListeners() {
        Map<String, String> snapshot = new HashMap<>(configCache);
        for (Consumer<Map<String, String>> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (Exception e) {
                logger.error("Error notifying listener", e);
            }
        }
    }

    private String buildFullKey(String key) {
        String prefix = config.getKeyPrefix();
        if (prefix.endsWith("/")) {
            return prefix + key;
        }
        return prefix + "/" + key;
    }

    private String extractKey(String fullKey) {
        String prefix = config.getKeyPrefix();
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        if (fullKey.startsWith(prefix)) {
            return fullKey.substring(prefix.length());
        }
        return fullKey;
    }
}
