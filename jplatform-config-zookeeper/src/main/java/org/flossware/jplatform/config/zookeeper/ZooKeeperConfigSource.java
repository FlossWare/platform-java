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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ZooKeeper-based configuration source.
 * Loads configuration from ZooKeeper and supports dynamic updates via watchers.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Stores configuration in ZooKeeper hierarchical znodes</li>
 *   <li>Supports dynamic configuration updates via TreeCache</li>
 *   <li>Maintains local cache for fast access</li>
 *   <li>Automatically reconnects on connection loss</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections.
 *
 * @since 1.1
 */
public class ZooKeeperConfigSource implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ZooKeeperConfigSource.class);

    private final ZooKeeperConfigSourceConfig config;
    private final Map<String, String> configCache;
    private final Map<String, Consumer<Map<String, String>>> listeners;
    private CuratorFramework client;
    private TreeCache treeCache;
    private volatile boolean started = false;

    /**
     * Constructs a new ZooKeeper configuration source.
     *
     * @param config the ZooKeeper configuration
     */
    public ZooKeeperConfigSource(ZooKeeperConfigSourceConfig config) {
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
     * @param client the Curator client
     */
    ZooKeeperConfigSource(ZooKeeperConfigSourceConfig config, CuratorFramework client) {
        this.config = config;
        this.client = client;
        this.configCache = new ConcurrentHashMap<>();
        this.listeners = new ConcurrentHashMap<>();
        this.started = true;
    }

    /**
     * Starts the ZooKeeper configuration source.
     * Must be called before using the configuration source.
     */
    public void start() {
        if (started) {
            return;
        }

        try {
            client = CuratorFrameworkFactory.builder()
                .connectString(config.getConnectString())
                .sessionTimeoutMs(config.getSessionTimeoutMs())
                .connectionTimeoutMs(config.getConnectionTimeoutMs())
                .retryPolicy(new ExponentialBackoffRetry(
                    config.getRetryIntervalMs(),
                    config.getRetryCount()
                ))
                .build();

            client.start();

            ensureBasePath();
            loadAllConfig();
            startWatching();

            started = true;
            logger.info("ZooKeeper config source started: {}", config.getConnectString());
        } catch (Exception e) {
            logger.error("Failed to start ZooKeeper config source", e);
            throw new RuntimeException("Failed to start ZooKeeper client", e);
        }
    }

    /**
     * Loads all configuration from ZooKeeper.
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
     * Sets a configuration value in ZooKeeper.
     *
     * @param key the configuration key
     * @param value the configuration value
     * @throws Exception if ZooKeeper operation fails
     */
    public void setConfig(String key, String value) throws Exception {
        if (!started) {
            throw new IllegalStateException("Config source not started");
        }

        String path = buildPath(key);
        byte[] data = value.getBytes(StandardCharsets.UTF_8);

        if (client.checkExists().forPath(path) != null) {
            client.setData().forPath(path, data);
        } else {
            client.create()
                .creatingParentsIfNeeded()
                .forPath(path, data);
        }

        configCache.put(key, value);
        logger.debug("Set config: {} = {}", key, value);
    }

    /**
     * Deletes a configuration key from ZooKeeper.
     *
     * @param key the configuration key
     * @throws Exception if ZooKeeper operation fails
     */
    public void deleteConfig(String key) throws Exception {
        if (!started) {
            throw new IllegalStateException("Config source not started");
        }

        String path = buildPath(key);
        if (client.checkExists().forPath(path) != null) {
            client.delete().forPath(path);
        }

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
        if (treeCache != null) {
            treeCache.close();
        }

        if (client != null) {
            client.close();
        }

        configCache.clear();
        listeners.clear();
        started = false;

        logger.info("ZooKeeper config source closed");
    }

    /**
     * Returns the Curator client.
     *
     * @return the client
     */
    public CuratorFramework getClient() {
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

    private void ensureBasePath() throws Exception {
        if (client.checkExists().forPath(config.getBasePath()) == null) {
            client.create()
                .creatingParentsIfNeeded()
                .forPath(config.getBasePath());
        }
    }

    private void loadAllConfig() throws Exception {
        if (client.checkExists().forPath(config.getBasePath()) == null) {
            return;
        }

        // Load into temporary map first to ensure atomic cache update
        Map<String, String> tempCache = new HashMap<>();
        loadConfigRecursive(config.getBasePath(), tempCache);

        // Only update cache if all loading succeeded
        configCache.clear();
        configCache.putAll(tempCache);
    }

    private void loadConfigRecursive(String path, Map<String, String> target) throws Exception {
        byte[] data = client.getData().forPath(path);
        if (data != null && data.length > 0) {
            String key = pathToKey(path);
            String value = new String(data, StandardCharsets.UTF_8);
            target.put(key, value);
        }

        for (String child : client.getChildren().forPath(path)) {
            loadConfigRecursive(path + "/" + child, target);
        }
    }

    private void startWatching() throws Exception {
        treeCache = TreeCache.newBuilder(client, config.getBasePath())
            .setCacheData(true)
            .build();

        treeCache.getListenable().addListener((curatorFramework, event) -> {
            try {
                handleCacheEvent(event);
            } catch (Exception e) {
                logger.error("Error handling cache event", e);
            }
        });

        treeCache.start();
    }

    private void handleCacheEvent(org.apache.curator.framework.recipes.cache.TreeCacheEvent event) {
        ChildData data = event.getData();
        if (data == null) {
            return;
        }

        String path = data.getPath();
        String key = pathToKey(path);

        switch (event.getType()) {
            case NODE_ADDED:
            case NODE_UPDATED:
                byte[] bytes = data.getData();
                if (bytes != null && bytes.length > 0) {
                    String value = new String(bytes, StandardCharsets.UTF_8);
                    configCache.put(key, value);
                    notifyListeners();
                }
                break;

            case NODE_REMOVED:
                configCache.remove(key);
                notifyListeners();
                break;

            default:
                break;
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

    private String buildPath(String key) {
        return config.getBasePath() + "/" + key.replace('.', '/');
    }

    private String pathToKey(String path) {
        String relative = path.substring(config.getBasePath().length());
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative.replace('/', '.');
    }
}
