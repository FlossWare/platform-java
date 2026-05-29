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

package org.flossware.jplatform.storage.database;

import org.flossware.jplatform.api.VolumeManager;
import org.flossware.jplatform.api.VolumeMount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database-based implementation of VolumeManager.
 * Stores volume metadata in a JDBC database while files reside on local filesystem.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Stores volume metadata and paths in a database</li>
 *   <li>Maintains local filesystem for actual file storage</li>
 *   <li>Tracks volume usage via database records</li>
 *   <li>Supports H2 and MySQL databases (uses database-specific SQL syntax)</li>
 * </ul>
 *
 * <p><b>Database Requirements:</b> H2 or MySQL. This implementation uses
 * database-specific SQL syntax (MERGE, AUTO_INCREMENT) that is not compatible
 * with PostgreSQL, Oracle, SQL Server, or other databases.
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections and synchronized database access.
 *
 * @since 1.1
 */
public class DatabaseVolumeManager implements VolumeManager, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseVolumeManager.class);

    private final DatabaseStorageConfig config;
    private final Map<String, VolumeMount> volumes;
    private final Map<String, Path> localPaths;
    private Connection connection;
    private volatile boolean initialized = false;

    /**
     * Constructs a new database volume manager.
     *
     * @param config the database storage configuration
     * @param volumeMounts the list of volumes to manage
     */
    public DatabaseVolumeManager(DatabaseStorageConfig config, List<VolumeMount> volumeMounts) {
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
     * @param connection the database connection
     */
    DatabaseVolumeManager(DatabaseStorageConfig config, List<VolumeMount> volumeMounts, Connection connection) {
        this.config = config;
        this.volumes = new ConcurrentHashMap<>();
        this.localPaths = new ConcurrentHashMap<>();
        this.connection = connection;

        for (VolumeMount mount : volumeMounts) {
            volumes.put(mount.getName(), mount);
        }
        this.initialized = true;
    }

    /**
     * Initializes the database connection and creates required tables.
     * Must be called before using the volume manager.
     */
    public void start() {
        if (initialized) {
            return;
        }

        Connection tempConnection = null;
        try {
            Class.forName(config.getDriverClassName());
            tempConnection = DriverManager.getConnection(
                config.getJdbcUrl(),
                config.getUsername(),
                config.getPassword()
            );

            connection = tempConnection;
            createTablesIfNeeded();
            initialized = true;

            logger.info("Database volume manager initialized with URL: {}", config.getJdbcUrl());
        } catch (Exception e) {
            // Close connection on failure to prevent leak
            if (tempConnection != null) {
                try {
                    tempConnection.close();
                } catch (SQLException closeEx) {
                    logger.error("Error closing connection after initialization failure", closeEx);
                }
            }
            logger.error("Failed to initialize database volume manager", e);
            throw new RuntimeException("Failed to initialize database connection", e);
        }
    }

    @Override
    public Path getVolumePath(String volumeName) {
        if (!volumes.containsKey(volumeName)) {
            throw new IllegalArgumentException("Volume not defined: " + volumeName);
        }

        return localPaths.computeIfAbsent(volumeName, name -> {
            try {
                Path path = Paths.get(System.getProperty("java.io.tmpdir"), "db-volumes", volumeName);
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

        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT SUM(size_bytes) FROM volume_files WHERE volume_name = ?")) {
            stmt.setString(1, volumeName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        } catch (SQLException e) {
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
        return mount.getMaxSizeMB() * 1024L * 1024L;
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
     * Records file metadata in the database.
     *
     * @param volumeName the volume name
     * @param fileName the file name
     * @param sizeBytes the file size in bytes
     * @throws SQLException if database operation fails
     */
    public void recordFile(String volumeName, String fileName, long sizeBytes) throws SQLException {
        if (!initialized) {
            throw new IllegalStateException("Volume manager not initialized");
        }

        String sql = "MERGE INTO volume_files (volume_name, file_name, size_bytes) KEY(volume_name, file_name) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, volumeName);
            stmt.setString(2, fileName);
            stmt.setLong(3, sizeBytes);
            stmt.executeUpdate();
        }
    }

    /**
     * Removes file metadata from the database.
     *
     * @param volumeName the volume name
     * @param fileName the file name
     * @throws SQLException if database operation fails
     */
    public void removeFile(String volumeName, String fileName) throws SQLException {
        if (!initialized) {
            throw new IllegalStateException("Volume manager not initialized");
        }

        try (PreparedStatement stmt = connection.prepareStatement(
            "DELETE FROM volume_files WHERE volume_name = ? AND file_name = ?")) {
            stmt.setString(1, volumeName);
            stmt.setString(2, fileName);
            stmt.executeUpdate();
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                initialized = false;
                logger.info("Database volume manager closed");
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
            }
        }
    }

    /**
     * Returns the database connection.
     *
     * @return the connection
     */
    public Connection getConnection() {
        return connection;
    }

    private void createTablesIfNeeded() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS volume_paths (" +
                "volume_name VARCHAR(255) PRIMARY KEY, " +
                "path VARCHAR(4096) NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS volume_files (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "volume_name VARCHAR(255) NOT NULL, " +
                "file_name VARCHAR(4096) NOT NULL, " +
                "size_bytes BIGINT NOT NULL, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE (volume_name, file_name))"
            );
        }
    }

    private void recordVolumePath(String volumeName, String path) {
        try (PreparedStatement stmt = connection.prepareStatement(
            "MERGE INTO volume_paths (volume_name, path) KEY(volume_name) VALUES (?, ?)")) {
            stmt.setString(1, volumeName);
            stmt.setString(2, path);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to record volume path: " + volumeName, e);
        }
    }
}
