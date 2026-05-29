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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class S3StorageConfigTest {

    @Test
    void testBuilderDefaults() {
        S3StorageConfig config = S3StorageConfig.builder()
            .accessKey("test-key")
            .secretKey("test-secret")
            .bucketName("test-bucket")
            .build();

        assertNull(config.getEndpoint());
        assertEquals("us-east-1", config.getRegion());
        assertEquals("test-key", config.getAccessKey());
        assertEquals("test-secret", config.getSecretKey());
        assertEquals("test-bucket", config.getBucketName());
        assertFalse(config.isPathStyleAccess());
        assertNull(config.getKeyPrefix());
    }

    @Test
    void testBuilderWithAllFields() {
        S3StorageConfig config = S3StorageConfig.builder()
            .endpoint("http://localhost:9000")
            .region("eu-west-1")
            .accessKey("minio-key")
            .secretKey("minio-secret")
            .bucketName("jplatform")
            .pathStyleAccess(true)
            .keyPrefix("prod/")
            .build();

        assertEquals("http://localhost:9000", config.getEndpoint());
        assertEquals("eu-west-1", config.getRegion());
        assertEquals("minio-key", config.getAccessKey());
        assertEquals("minio-secret", config.getSecretKey());
        assertEquals("jplatform", config.getBucketName());
        assertTrue(config.isPathStyleAccess());
        assertEquals("prod/", config.getKeyPrefix());
    }

    @Test
    void testBuilderMissingAccessKey() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            S3StorageConfig.builder()
                .secretKey("secret")
                .bucketName("bucket")
                .build();
        });
        assertTrue(exception.getMessage().contains("Access key"));
    }

    @Test
    void testBuilderEmptyAccessKey() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            S3StorageConfig.builder()
                .accessKey("  ")
                .secretKey("secret")
                .bucketName("bucket")
                .build();
        });
        assertTrue(exception.getMessage().contains("Access key"));
    }

    @Test
    void testBuilderMissingSecretKey() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            S3StorageConfig.builder()
                .accessKey("key")
                .bucketName("bucket")
                .build();
        });
        assertTrue(exception.getMessage().contains("Secret key"));
    }

    @Test
    void testBuilderEmptySecretKey() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            S3StorageConfig.builder()
                .accessKey("key")
                .secretKey("")
                .bucketName("bucket")
                .build();
        });
        assertTrue(exception.getMessage().contains("Secret key"));
    }

    @Test
    void testBuilderMissingBucketName() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            S3StorageConfig.builder()
                .accessKey("key")
                .secretKey("secret")
                .build();
        });
        assertTrue(exception.getMessage().contains("Bucket name"));
    }

    @Test
    void testBuilderEmptyBucketName() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            S3StorageConfig.builder()
                .accessKey("key")
                .secretKey("secret")
                .bucketName("   ")
                .build();
        });
        assertTrue(exception.getMessage().contains("Bucket name"));
    }

    @Test
    void testMinioConfiguration() {
        S3StorageConfig config = S3StorageConfig.builder()
            .endpoint("http://localhost:9000")
            .accessKey("minioadmin")
            .secretKey("minioadmin")
            .bucketName("test")
            .pathStyleAccess(true)
            .build();

        assertEquals("http://localhost:9000", config.getEndpoint());
        assertTrue(config.isPathStyleAccess());
    }

    @Test
    void testAwsConfiguration() {
        S3StorageConfig config = S3StorageConfig.builder()
            .region("us-west-2")
            .accessKey("AKIA...")
            .secretKey("secret")
            .bucketName("my-app-bucket")
            .build();

        assertNull(config.getEndpoint());
        assertEquals("us-west-2", config.getRegion());
        assertFalse(config.isPathStyleAccess());
    }

    @Test
    void testBuilderChaining() {
        S3StorageConfig.Builder builder = S3StorageConfig.builder();
        assertSame(builder, builder.endpoint("http://localhost"));
        assertSame(builder, builder.region("us-east-1"));
        assertSame(builder, builder.accessKey("key"));
        assertSame(builder, builder.secretKey("secret"));
        assertSame(builder, builder.bucketName("bucket"));
        assertSame(builder, builder.pathStyleAccess(true));
        assertSame(builder, builder.keyPrefix("prefix/"));
    }

    @Test
    void testKeyPrefixUsage() {
        S3StorageConfig devConfig = S3StorageConfig.builder()
            .accessKey("key")
            .secretKey("secret")
            .bucketName("shared-bucket")
            .keyPrefix("dev/")
            .build();

        S3StorageConfig prodConfig = S3StorageConfig.builder()
            .accessKey("key")
            .secretKey("secret")
            .bucketName("shared-bucket")
            .keyPrefix("prod/")
            .build();

        assertEquals("dev/", devConfig.getKeyPrefix());
        assertEquals("prod/", prodConfig.getKeyPrefix());
    }
}
