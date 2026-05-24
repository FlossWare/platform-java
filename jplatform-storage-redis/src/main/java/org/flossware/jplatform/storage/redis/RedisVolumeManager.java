package org.flossware.jplatform.storage.redis;

import org.flossware.jplatform.api.VolumeManager;
import org.flossware.jplatform.api.VolumeMount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-based implementation of VolumeManager.
 * Stores volume metadata in Redis while files reside on local filesystem.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Stores volume metadata and file sizes in Redis</li>
 *   <li>Maintains local filesystem for actual file storage</li>
 *   <li>Uses Redis hashes for efficient storage</li>
 *   <li>Supports connection pooling</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections and Redis operations are atomic.
 *
 * @since 1.1
 */
public class RedisVolumeManager implements VolumeManager, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(RedisVolumeManager.class);

    private final RedisStorageConfig config;
    private final Map<String, VolumeMount> volumes;
    private final Map<String, Path> localPaths;
    private JedisPool jedisPool;
    private volatile boolean initialized = false;

    /**
     * Constructs a new Redis volume manager.
     *
     * @param config the Redis storage configuration
     * @param volumeMounts the list of volumes to manage
     */
    public RedisVolumeManager(RedisStorageConfig config, List<VolumeMount> volumeMounts) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        if (volumeMounts == null) {
            throw new IllegalArgumentException("Volume mounts must not be null");
        }

        this.config = config;
        this.volumes = new ConcurrentHashMap<>();
        this.localPaths = new ConcurrentHashMap<>();

        for (VolumeMount mount : volumeMounts) {
            volumes.put(mount.getName(), mount);
        }
    }

    /**
     * Package-private constructor for testing.
     *
     * @param config the configuration
     * @param volumeMounts the volume mounts
     * @param jedisPool the Jedis pool
     */
    RedisVolumeManager(RedisStorageConfig config, List<VolumeMount> volumeMounts, JedisPool jedisPool) {
        this.config = config;
        this.volumes = new ConcurrentHashMap<>();
        this.localPaths = new ConcurrentHashMap<>();
        this.jedisPool = jedisPool;

        for (VolumeMount mount : volumeMounts) {
            volumes.put(mount.getName(), mount);
        }
        this.initialized = true;
    }

    /**
     * Initializes the Redis connection pool.
     * Must be called before using the volume manager.
     */
    public void start() {
        if (initialized) {
            return;
        }

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);

            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                jedisPool = new JedisPool(
                    poolConfig,
                    config.getHost(),
                    config.getPort(),
                    config.getConnectionTimeout(),
                    config.getPassword(),
                    config.getDatabase()
                );
            } else {
                jedisPool = new JedisPool(
                    poolConfig,
                    config.getHost(),
                    config.getPort(),
                    config.getConnectionTimeout()
                );
            }

            initialized = true;
            logger.info("Redis volume manager initialized: {}:{}", config.getHost(), config.getPort());
        } catch (Exception e) {
            logger.error("Failed to initialize Redis volume manager", e);
            throw new RuntimeException("Failed to initialize Redis pool", e);
        }
    }

    @Override
    public Path getVolumePath(String volumeName) {
        if (!volumes.containsKey(volumeName)) {
            throw new IllegalArgumentException("Volume not defined: " + volumeName);
        }

        return localPaths.computeIfAbsent(volumeName, name -> {
            try {
                Path path = Paths.get(System.getProperty("java.io.tmpdir"), "redis-volumes", volumeName);
                Files.createDirectories(path);

                if (initialized) {
                    recordVolumePath(volumeName, path.toString());
                }

                return path;
            } catch (IOException e) {
                logger.error("Failed to create volume directory: " + volumeName, e);
                throw new RuntimeException("Failed to create volume directory", e);
            }
        });
    }

    @Override
    public List<VolumeMount> getVolumes() {
        return new ArrayList<>(volumes.values());
    }

    @Override
    public long getVolumeUsageBytes(String volumeName) throws IOException {
        if (!volumes.containsKey(volumeName)) {
            throw new IllegalArgumentException("Volume not defined: " + volumeName);
        }

        if (!initialized) {
            return 0;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = config.getKeyPrefix() + volumeName + ":files";
            Map<String, String> files = jedis.hgetAll(key);

            long totalSize = 0;
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String fileName = entry.getKey();
                String sizeStr = entry.getValue();
                try {
                    totalSize += Long.parseLong(sizeStr);
                } catch (NumberFormatException e) {
                    logger.warn("Skipping file {} with invalid size value: {}", fileName, sizeStr);
                }
            }

            return totalSize;
        } catch (Exception e) {
            logger.error("Failed to get volume usage for: " + volumeName, e);
            throw new IOException("Failed to get volume usage", e);
        }
    }

    @Override
    public boolean volumeExists(String volumeName) {
        return volumes.containsKey(volumeName);
    }

    @Override
    public long getVolumeSizeLimit(String volumeName) {
        VolumeMount mount = volumes.get(volumeName);
        if (mount == null) {
            throw new IllegalArgumentException("Volume not defined: " + volumeName);
        }
        return ((long) mount.getMaxSizeMB()) * 1024L * 1024L;
    }

    @Override
    public boolean isPersistent(String volumeName) {
        VolumeMount mount = volumes.get(volumeName);
        if (mount == null) {
            throw new IllegalArgumentException("Volume not defined: " + volumeName);
        }
        return mount.isPersistent();
    }

    /**
     * Records file metadata in Redis.
     *
     * @param volumeName the volume name
     * @param fileName the file name
     * @param sizeBytes the file size in bytes
     */
    public void recordFile(String volumeName, String fileName, long sizeBytes) {
        if (!initialized) {
            throw new IllegalStateException("Volume manager not initialized");
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = config.getKeyPrefix() + volumeName + ":files";
            jedis.hset(key, fileName, String.valueOf(sizeBytes));
        }
    }

    /**
     * Removes file metadata from Redis.
     *
     * @param volumeName the volume name
     * @param fileName the file name
     */
    public void removeFile(String volumeName, String fileName) {
        if (!initialized) {
            throw new IllegalStateException("Volume manager not initialized");
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = config.getKeyPrefix() + volumeName + ":files";
            jedis.hdel(key, fileName);
        }
    }

    @Override
    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
            initialized = false;
            logger.info("Redis volume manager closed");
        }
    }

    /**
     * Returns the Jedis pool.
     *
     * @return the pool
     */
    public JedisPool getJedisPool() {
        return jedisPool;
    }

    private void recordVolumePath(String volumeName, String path) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = config.getKeyPrefix() + volumeName + ":path";
            jedis.set(key, path);
        } catch (Exception e) {
            logger.error("Failed to record volume path: " + volumeName, e);
        }
    }
}
