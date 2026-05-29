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

package org.flossware.jplatform.storage.s3;

import org.flossware.jplatform.api.VolumeMount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3VolumeManagerTest {

    @Mock
    private S3Client s3Client;

    private S3StorageConfig config;
    private List<VolumeMount> volumeMounts;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        config = S3StorageConfig.builder()
            .accessKey("test-key")
            .secretKey("test-secret")
            .bucketName("test-bucket")
            .build();

        volumeMounts = Arrays.asList(
            new VolumeMount("data", "/app/data", true, 1024),
            new VolumeMount("cache", "/app/cache", false, 512),
            new VolumeMount("logs", "/app/logs", true, 0)
        );
    }

    @Test
    void testConstructorNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new S3VolumeManager(null, volumeMounts);
        });
        assertTrue(exception.getMessage().contains("Config"));
    }

    @Test
    void testConstructorNullVolumeMounts() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new S3VolumeManager(config, null);
        });
        assertTrue(exception.getMessage().contains("Volume mounts"));
    }

    @Test
    void testGetVolumesReturnsAllMounts() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        List<VolumeMount> volumes = manager.getVolumes();
        assertEquals(3, volumes.size());
        assertTrue(volumes.stream().anyMatch(v -> v.getName().equals("data")));
        assertTrue(volumes.stream().anyMatch(v -> v.getName().equals("cache")));
        assertTrue(volumes.stream().anyMatch(v -> v.getName().equals("logs")));
    }

    @Test
    void testVolumeExists() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        assertTrue(manager.volumeExists("data"));
        assertTrue(manager.volumeExists("cache"));
        assertTrue(manager.volumeExists("logs"));
        assertFalse(manager.volumeExists("nonexistent"));
    }

    @Test
    void testGetVolumePathCreatesDirectory() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        Path volumePath = manager.getVolumePath("data");
        assertNotNull(volumePath);
        assertTrue(Files.exists(volumePath));
        assertTrue(Files.isDirectory(volumePath));
    }

    @Test
    void testGetVolumePathUndefinedVolume() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            manager.getVolumePath("undefined");
        });
        assertTrue(exception.getMessage().contains("not defined"));
    }

    @Test
    void testGetVolumeSizeLimit() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        assertEquals(1024 * 1024 * 1024, manager.getVolumeSizeLimit("data"));
        assertEquals(512 * 1024 * 1024, manager.getVolumeSizeLimit("cache"));
        assertEquals(0, manager.getVolumeSizeLimit("logs"));
    }

    @Test
    void testGetVolumeSizeLimitUndefined() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            manager.getVolumeSizeLimit("undefined");
        });
        assertTrue(exception.getMessage().contains("not defined"));
    }

    @Test
    void testIsPersistent() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        assertTrue(manager.isPersistent("data"));
        assertFalse(manager.isPersistent("cache"));
        assertTrue(manager.isPersistent("logs"));
    }

    @Test
    void testIsPersistentUndefined() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            manager.isPersistent("undefined");
        });
        assertTrue(exception.getMessage().contains("not defined"));
    }

    @Test
    void testGetVolumeUsageBytesEmpty() throws IOException {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenReturn(ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(List.of())
                .build());

        long usage = manager.getVolumeUsageBytes("data");
        assertEquals(0, usage);
    }

    @Test
    void testGetVolumeUsageBytesWithObjects() throws IOException {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenReturn(ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(Arrays.asList(
                    S3Object.builder().key("data/file1.txt").size(100L).build(),
                    S3Object.builder().key("data/file2.txt").size(200L).build(),
                    S3Object.builder().key("data/file3.txt").size(300L).build()
                ))
                .build());

        long usage = manager.getVolumeUsageBytes("data");
        assertEquals(600, usage);
    }

    @Test
    void testGetVolumeUsageBytesUndefined() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            manager.getVolumeUsageBytes("undefined");
        });
        assertTrue(exception.getMessage().contains("not defined"));
    }

    @Test
    void testUploadFileNotInitialized() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            manager.uploadFile("data", "test.txt");
        });
        assertTrue(exception.getMessage().contains("not initialized"));
    }

    @Test
    void testUploadFileSuccess() throws IOException {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        Path volumePath = manager.getVolumePath("data");
        Path testFile = volumePath.resolve("test.txt");
        Files.writeString(testFile, "test content");

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        manager.uploadFile("data", "test.txt");

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testUploadFileNotFound() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        Exception exception = assertThrows(IOException.class, () -> {
            manager.uploadFile("data", "nonexistent.txt");
        });
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void testDownloadFileNotInitialized() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            manager.downloadFile("data", "test.txt");
        });
        assertTrue(exception.getMessage().contains("not initialized"));
    }

    @Test
    void testClose() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        manager.close();
        verify(s3Client).close();
    }

    @Test
    void testGetS3Client() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        assertSame(s3Client, manager.getS3Client());
    }

    @Test
    void testEmptyVolumeMounts() {
        S3VolumeManager manager = new S3VolumeManager(config, List.of(), s3Client);

        assertEquals(0, manager.getVolumes().size());
        assertFalse(manager.volumeExists("any"));
    }

    @Test
    void testMultipleVolumesIndependent() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        Path dataPath = manager.getVolumePath("data");
        Path cachePath = manager.getVolumePath("cache");

        assertNotEquals(dataPath, cachePath);
    }

    @Test
    void testVolumeSizeLimitZeroMeansUnlimited() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        assertEquals(0, manager.getVolumeSizeLimit("logs"));
    }

    @Test
    void testGetVolumeUsagePagination() throws IOException {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenReturn(ListObjectsV2Response.builder()
                .isTruncated(true)
                .nextContinuationToken("token1")
                .contents(Arrays.asList(
                    S3Object.builder().key("data/file1.txt").size(100L).build()
                ))
                .build())
            .thenReturn(ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(Arrays.asList(
                    S3Object.builder().key("data/file2.txt").size(200L).build()
                ))
                .build());

        long usage = manager.getVolumeUsageBytes("data");
        assertEquals(300, usage);

        verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void testDownloadFileSuccess() throws IOException {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        Path volumePath = manager.getVolumePath("data");
        Path testFile = volumePath.resolve("downloaded.txt");

        when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class)))
            .thenReturn(null); // Mocking void behavior

        manager.downloadFile("data", "downloaded.txt");

        verify(s3Client).getObject(any(GetObjectRequest.class), any(Path.class));
    }

    @Test
    void testDownloadFileS3Exception() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class)))
            .thenThrow(S3Exception.builder().message("S3 error").build());

        assertThrows(IOException.class, () ->
            manager.downloadFile("data", "test.txt")
        );
    }

    @Test
    void testDownloadFileUndefinedVolume() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        assertThrows(IllegalArgumentException.class, () ->
            manager.downloadFile("undefined", "test.txt")
        );
    }

    @Test
    void testUploadFileS3Exception() throws IOException {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        Path volumePath = manager.getVolumePath("data");
        Path testFile = volumePath.resolve("test.txt");
        Files.writeString(testFile, "content");

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(S3Exception.builder().message("S3 error").build());

        assertThrows(IOException.class, () ->
            manager.uploadFile("data", "test.txt")
        );
    }

    @Test
    void testUploadFileUndefinedVolume() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        assertThrows(IllegalArgumentException.class, () ->
            manager.uploadFile("undefined", "test.txt")
        );
    }

    // Note: start() method with actual S3Client creation cannot be easily unit tested
    // as it requires real AWS SDK client building. The method is tested via
    // integration tests or when S3Client is provided via constructor.

    @Test
    void testStartIdempotent() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        // First start - already initialized via constructor
        // Second start should be no-op
        assertDoesNotThrow(() -> manager.start());
        assertDoesNotThrow(() -> manager.start());
    }

    @Test
    void testCloseWhenNotInitialized() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts);
        assertDoesNotThrow(() -> manager.close());
    }

    @Test
    void testGetVolumeUsageBytesWithS3Exception() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenThrow(S3Exception.builder().message("S3 error").build());

        assertThrows(IOException.class, () ->
            manager.getVolumeUsageBytes("data")
        );
    }

    @Test
    void testGetVolumePathCreatesParentDirectories() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        // Get path twice - should return same cached path
        Path path1 = manager.getVolumePath("data");
        Path path2 = manager.getVolumePath("data");

        assertSame(path1, path2);
        assertTrue(Files.exists(path1));
    }

    @Test
    void testIsPersistentForAllVolumes() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        assertTrue(manager.isPersistent("data"));
        assertFalse(manager.isPersistent("cache"));
        assertTrue(manager.isPersistent("logs"));
    }

    @Test
    void testGetVolumesReturnsCopy() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        List<VolumeMount> volumes1 = manager.getVolumes();
        List<VolumeMount> volumes2 = manager.getVolumes();

        assertNotSame(volumes1, volumes2);
        assertEquals(volumes1.size(), volumes2.size());
    }

    @Test
    void testConstructorWithS3ClientSkipsInitialization() {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        // Should be able to use immediately without calling start()
        assertTrue(manager.volumeExists("data"));
        assertSame(s3Client, manager.getS3Client());
    }

    @Test
    void testUploadWithKeyPrefix() throws IOException {
        S3StorageConfig configWithPrefix = S3StorageConfig.builder()
            .accessKey("test-key")
            .secretKey("test-secret")
            .bucketName("test-bucket")
            .keyPrefix("app/prod/")
            .build();

        S3VolumeManager manager = new S3VolumeManager(configWithPrefix, volumeMounts, s3Client);

        Path volumePath = manager.getVolumePath("data");
        Path testFile = volumePath.resolve("test.txt");
        Files.writeString(testFile, "content");

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        manager.uploadFile("data", "test.txt");

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testDownloadWithKeyPrefix() throws IOException {
        S3StorageConfig configWithPrefix = S3StorageConfig.builder()
            .accessKey("test-key")
            .secretKey("test-secret")
            .bucketName("test-bucket")
            .keyPrefix("app/prod/")
            .build();

        S3VolumeManager manager = new S3VolumeManager(configWithPrefix, volumeMounts, s3Client);

        when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class)))
            .thenReturn(null);

        manager.downloadFile("data", "test.txt");

        verify(s3Client).getObject(any(GetObjectRequest.class), any(Path.class));
    }

    @Test
    void testDownloadCreatesParentDirectories() throws IOException {
        S3VolumeManager manager = new S3VolumeManager(config, volumeMounts, s3Client);

        when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class)))
            .thenReturn(null);

        manager.downloadFile("data", "subdir/nested/file.txt");

        Path volumePath = manager.getVolumePath("data");
        Path parentDir = volumePath.resolve("subdir/nested");
        assertTrue(Files.exists(parentDir));
        assertTrue(Files.isDirectory(parentDir));

        verify(s3Client).getObject(any(GetObjectRequest.class), any(Path.class));
    }

    @Test
    void testGetVolumeUsageBytesWithKeyPrefix() throws IOException {
        S3StorageConfig configWithPrefix = S3StorageConfig.builder()
            .accessKey("test-key")
            .secretKey("test-secret")
            .bucketName("test-bucket")
            .keyPrefix("app/prod/")
            .build();

        S3VolumeManager manager = new S3VolumeManager(configWithPrefix, volumeMounts, s3Client);

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenReturn(ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(Arrays.asList(
                    S3Object.builder().key("app/prod/data/file1.txt").size(100L).build(),
                    S3Object.builder().key("app/prod/data/file2.txt").size(200L).build()
                ))
                .build());

        long usage = manager.getVolumeUsageBytes("data");
        assertEquals(300, usage);
    }
}
