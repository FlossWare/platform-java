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

import java.net.URI;

/**
 * Configuration for S3-based storage.
 * Supports AWS S3 and S3-compatible services like MinIO.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // AWS S3
 * S3StorageConfig config = S3StorageConfig.builder()
 *     .region("us-east-1")
 *     .accessKey("AKIA...")
 *     .secretKey("secret")
 *     .bucketName("my-bucket")
 *     .build();
 *
 * // MinIO
 * S3StorageConfig minioConfig = S3StorageConfig.builder()
 *     .endpoint("http://localhost:9000")
 *     .accessKey("minioadmin")
 *     .secretKey("minioadmin")
 *     .bucketName("jplatform")
 *     .pathStyleAccess(true)
 *     .build();
 * }</pre>
 *
 * @see S3VolumeManager
 * @since 1.1
 */
public class S3StorageConfig {

    private final String endpoint;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String bucketName;
    private final boolean pathStyleAccess;
    private final String keyPrefix;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    S3StorageConfig(Builder builder) {
        this.endpoint = builder.endpoint;
        this.region = builder.region;
        this.accessKey = builder.accessKey;
        this.secretKey = builder.secretKey;
        this.bucketName = builder.bucketName;
        this.pathStyleAccess = builder.pathStyleAccess;
        this.keyPrefix = builder.keyPrefix;
    }

    /**
     * Returns the S3 endpoint URL.
     * For AWS S3, this is null (uses default). For MinIO or other S3-compatible
     * services, this should be the full URL (e.g., "http://localhost:9000").
     *
     * @return the endpoint URL, or null for AWS S3
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Returns the AWS region.
     *
     * @return the region (default: "us-east-1")
     */
    public String getRegion() {
        return region;
    }

    /**
     * Returns the AWS access key ID.
     *
     * @return the access key
     */
    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Returns the AWS secret access key.
     *
     * @return the secret key
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Returns the S3 bucket name.
     *
     * @return the bucket name
     */
    public String getBucketName() {
        return bucketName;
    }

    /**
     * Returns whether to use path-style access.
     * Path-style: http://s3.amazonaws.com/bucket/key
     * Virtual-hosted-style: http://bucket.s3.amazonaws.com/key
     *
     * @return true for path-style access (required for MinIO)
     */
    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    /**
     * Returns the key prefix for all objects.
     * Useful for isolating different environments in the same bucket.
     *
     * @return the key prefix, or null if not set
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Creates a new builder for S3StorageConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for S3StorageConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String endpoint;
        private String region = "us-east-1";
        private String accessKey;
        private String secretKey;
        private String bucketName;
        private boolean pathStyleAccess = false;
        private String keyPrefix;

        /**
         * Sets the S3 endpoint URL.
         * Required for MinIO or other S3-compatible services.
         *
         * @param endpoint the endpoint URL
         * @return this builder for chaining
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the AWS region.
         *
         * @param region the region (e.g., "us-east-1")
         * @return this builder for chaining
         */
        public Builder region(String region) {
            this.region = region;
            return this;
        }

        /**
         * Sets the AWS access key ID.
         *
         * @param accessKey the access key
         * @return this builder for chaining
         */
        public Builder accessKey(String accessKey) {
            this.accessKey = accessKey;
            return this;
        }

        /**
         * Sets the AWS secret access key.
         *
         * @param secretKey the secret key
         * @return this builder for chaining
         */
        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        /**
         * Sets the S3 bucket name.
         *
         * @param bucketName the bucket name
         * @return this builder for chaining
         */
        public Builder bucketName(String bucketName) {
            this.bucketName = bucketName;
            return this;
        }

        /**
         * Sets whether to use path-style access.
         * Set to true for MinIO and other S3-compatible services.
         *
         * @param pathStyleAccess true for path-style access
         * @return this builder for chaining
         */
        public Builder pathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
            return this;
        }

        /**
         * Sets the key prefix for all objects.
         *
         * @param keyPrefix the prefix (e.g., "prod/" or "dev/")
         * @return this builder for chaining
         */
        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        /**
         * Builds the S3StorageConfig instance.
         *
         * @return a new S3StorageConfig with the configured values
         * @throws IllegalStateException if required fields are missing
         */
        public S3StorageConfig build() {
            if (accessKey == null || accessKey.trim().isEmpty()) {
                throw new IllegalStateException("Access key must be specified");
            }
            if (secretKey == null || secretKey.trim().isEmpty()) {
                throw new IllegalStateException("Secret key must be specified");
            }
            if (bucketName == null || bucketName.trim().isEmpty()) {
                throw new IllegalStateException("Bucket name must be specified");
            }
            return new S3StorageConfig(this);
        }
    }
}
