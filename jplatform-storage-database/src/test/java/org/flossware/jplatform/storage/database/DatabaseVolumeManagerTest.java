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

import org.flossware.jplatform.api.VolumeMount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseVolumeManagerTest {

    private DatabaseStorageConfig config;
    private List<VolumeMount> volumeMounts;
    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        config = DatabaseStorageConfig.builder()
            .jdbcUrl("jdbc:h2:mem:test" + System.nanoTime())
            .build();

        volumeMounts = Arrays.asList(
            new VolumeMount("data", "/app/data", true, 1024),
            new VolumeMount("cache", "/app/cache", false, 512),
            new VolumeMount("logs", "/app/logs", true, 0)
        );

        Class.forName(config.getDriverClassName());
        connection = DriverManager.getConnection(
            config.getJdbcUrl(),
            config.getUsername(),
            config.getPassword()
        );

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                "CREATE TABLE volume_paths (" +
                "volume_name VARCHAR(255) PRIMARY KEY, " +
                "path VARCHAR(4096) NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            stmt.execute(
                "CREATE TABLE volume_files (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "volume_name VARCHAR(255) NOT NULL, " +
                "file_name VARCHAR(4096) NOT NULL, " +
                "size_bytes BIGINT NOT NULL, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE (volume_name, file_name))"
            );
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new DatabaseVolumeManager(null, volumeMounts);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testConstructorNullVolumeMounts() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new DatabaseVolumeManager(config, null);
        });
        assertTrue(exception.getMessage().contains("Volume mounts"));
    }

    @Test
    void testGetVolumesReturnsAllMounts() {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        List<VolumeMount> volumes = manager.getVolumes();
        assertEquals(3, volumes.size());
    }

    @Test
    void testVolumeExists() {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        assertTrue(manager.volumeExists("data"));
        assertTrue(manager.volumeExists("cache"));
        assertTrue(manager.volumeExists("logs"));
        assertFalse(manager.volumeExists("nonexistent"));
    }

    @Test
    void testGetVolumePathCreatesDirectory() {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        Path volumePath = manager.getVolumePath("data");
        assertNotNull(volumePath);
        assertTrue(Files.exists(volumePath));
        assertTrue(Files.isDirectory(volumePath));
    }

    @Test
    void testGetVolumePathUndefinedVolume() {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            manager.getVolumePath("undefined");
        });
        assertTrue(exception.getMessage().contains("not defined"));
    }

    @Test
    void testGetVolumeSizeLimit() {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        assertEquals(1024 * 1024 * 1024, manager.getVolumeSizeLimit("data"));
        assertEquals(512 * 1024 * 1024, manager.getVolumeSizeLimit("cache"));
        assertEquals(0, manager.getVolumeSizeLimit("logs"));
    }

    @Test
    void testIsPersistent() {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        assertTrue(manager.isPersistent("data"));
        assertFalse(manager.isPersistent("cache"));
        assertTrue(manager.isPersistent("logs"));
    }

    @Test
    void testGetVolumeUsageBytesEmpty() throws IOException {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        long usage = manager.getVolumeUsageBytes("data");
        assertEquals(0, usage);
    }

    @Test
    void testRecordFile() throws SQLException, IOException {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        manager.recordFile("data", "test.txt", 100);
        manager.recordFile("data", "data.json", 200);

        long usage = manager.getVolumeUsageBytes("data");
        assertEquals(300, usage);
    }

    @Test
    void testRecordFileUpdate() throws SQLException, IOException {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        manager.recordFile("data", "test.txt", 100);
        manager.recordFile("data", "test.txt", 150);

        long usage = manager.getVolumeUsageBytes("data");
        assertEquals(150, usage);
    }

    @Test
    void testRemoveFile() throws SQLException, IOException {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        manager.recordFile("data", "file1.txt", 100);
        manager.recordFile("data", "file2.txt", 200);

        manager.removeFile("data", "file1.txt");

        long usage = manager.getVolumeUsageBytes("data");
        assertEquals(200, usage);
    }

    @Test
    void testRecordFileNotInitialized() {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            manager.recordFile("data", "test.txt", 100);
        });
        assertTrue(exception.getMessage().contains("not initialized"));
    }

    @Test
    void testClose() throws SQLException {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        manager.close();
        assertTrue(connection.isClosed());
    }

    @Test
    void testGetConnection() {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        assertSame(connection, manager.getConnection());
    }

    @Test
    void testMultipleVolumesIndependent() throws SQLException, IOException {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts, connection);

        manager.recordFile("data", "file.txt", 100);
        manager.recordFile("cache", "cache.txt", 200);

        assertEquals(100, manager.getVolumeUsageBytes("data"));
        assertEquals(200, manager.getVolumeUsageBytes("cache"));
    }

    @Test
    void testStart() {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts);

        manager.start();
        assertNotNull(manager.getConnection());

        manager.close();
    }

    @Test
    void testStartIdempotent() {
        DatabaseVolumeManager manager = new DatabaseVolumeManager(config, volumeMounts);

        manager.start();
        manager.start();

        manager.close();
    }
}
