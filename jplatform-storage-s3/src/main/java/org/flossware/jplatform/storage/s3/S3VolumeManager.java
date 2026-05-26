package org.flossware.jplatform.storage.s3;

import org.flossware.jplatform.api.VolumeManager;
import org.flossware.jplatform.api.VolumeMount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S3-based implementation of VolumeManager.
 * Provides persistent storage using AWS S3 or S3-compatible services like MinIO.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Maps each volume to a prefix in an S3 bucket</li>
 *   <li>Maintains a local cache directory for file operations</li>
 *   <li>Synchronizes files between local cache and S3</li>
 *   <li>Tracks object sizes for usage calculations</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections.
 *
 * @since 1.1
 */
public class S3VolumeManager implements VolumeManager, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(S3VolumeManager.class);

    private final S3StorageConfig config;
    private final Map<String, VolumeMount> volumes;
    private final Map<String, Path> localCachePaths;
    private S3Client s3Client;
    private volatile boolean initialized = false;

    /**
     * Constructs a new S3 volume manager.
     *
     * @param config the S3 storage configuration
     * @param volumeMounts the list of volumes to manage
     */
    public S3VolumeManager(S3StorageConfig config, List<VolumeMount> volumeMounts) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        if (volumeMounts == null) {
            throw new IllegalArgumentException("Volume mounts must not be null");
        }

        this.config = config;
        this.volumes = new ConcurrentHashMap<>();
        this.localCachePaths = new ConcurrentHashMap<>();

        for (VolumeMount mount : volumeMounts) {
            volumes.put(mount.getName(), mount);
        }
    }

    /**
     * Package-private constructor for testing.
     *
     * @param config the configuration
     * @param volumeMounts the volume mounts
     * @param s3Client the S3 client
     */
    S3VolumeManager(S3StorageConfig config, List<VolumeMount> volumeMounts, S3Client s3Client) {
        this.config = config;
        this.s3Client = s3Client;
        this.volumes = new ConcurrentHashMap<>();
        this.localCachePaths = new ConcurrentHashMap<>();

        for (VolumeMount mount : volumeMounts) {
            volumes.put(mount.getName(), mount);
        }
        this.initialized = true;
    }

    /**
     * Initializes the S3 client and creates the bucket if needed.
     * Must be called before using the volume manager.
     */
    public void start() {
        if (initialized) {
            return;
        }

        try {
            S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())
                ));

            if (config.getEndpoint() != null) {
                builder.endpointOverride(URI.create(config.getEndpoint()));
            }

            if (config.isPathStyleAccess()) {
                builder.forcePathStyle(true);
            }

            s3Client = builder.build();

            // Ensure bucket exists
            ensureBucketExists();

            initialized = true;
            logger.info("S3 volume manager initialized with bucket: {}", config.getBucketName());
        } catch (Exception e) {
            logger.error("Failed to initialize S3 volume manager", e);
            throw new RuntimeException("Failed to initialize S3 client", e);
        }
    }

    @Override
    public Path getVolumePath(String volumeName) {
        if (!volumes.containsKey(volumeName)) {
            throw new IllegalArgumentException("Volume not defined: " + volumeName);
        }

        return localCachePaths.computeIfAbsent(volumeName, name -> {
            try {
                Path cachePath = Paths.get(System.getProperty("java.io.tmpdir"), "s3-volumes", volumeName);
                Files.createDirectories(cachePath);
                return cachePath;
            } catch (IOException e) {
                logger.error("Failed to create cache directory for volume: " + volumeName, e);
                throw new RuntimeException("Failed to create volume cache directory", e);
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

        try {
            String prefix = buildS3Prefix(volumeName);
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(config.getBucketName())
                .prefix(prefix)
                .build();

            long totalSize = 0;
            ListObjectsV2Response response;

            do {
                response = s3Client.listObjectsV2(request);
                for (S3Object object : response.contents()) {
                    totalSize += object.size();
                }

                request = ListObjectsV2Request.builder()
                    .bucket(config.getBucketName())
                    .prefix(prefix)
                    .continuationToken(response.nextContinuationToken())
                    .build();

            } while (response.isTruncated());

            return totalSize;
        } catch (Exception e) {
            logger.error("Failed to calculate volume usage for: " + volumeName, e);
            throw new IOException("Failed to calculate volume usage", e);
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
        return (long) mount.getMaxSizeMB() * 1024L * 1024L;
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
     * Uploads a file to S3 from the local cache.
     *
     * @param volumeName the volume name
     * @param relativePath the relative path within the volume
     * @throws IOException if upload fails
     */
    public void uploadFile(String volumeName, String relativePath) throws IOException {
        if (!initialized) {
            throw new IllegalStateException("Volume manager not initialized");
        }

        Path localPath = getVolumePath(volumeName).resolve(relativePath);

        try {
            String s3Key = buildS3Key(volumeName, relativePath);

            // Let RequestBody.fromFile() handle file existence check
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(s3Key)
                    .build(),
                RequestBody.fromFile(localPath)
            );

            logger.debug("Uploaded file to S3: {}", s3Key);
        } catch (Exception e) {
            // Check if file was missing (may be wrapped in SDK exception)
            Throwable cause = e.getCause();
            if (e instanceof java.nio.file.NoSuchFileException ||
                (cause instanceof java.nio.file.NoSuchFileException)) {
                throw new IOException("File not found: " + localPath, e);
            }
            logger.error("Failed to upload file to S3: " + relativePath, e);
            throw new IOException("Failed to upload file to S3", e);
        }
    }

    /**
     * Downloads a file from S3 to the local cache.
     *
     * @param volumeName the volume name
     * @param relativePath the relative path within the volume
     * @throws IOException if download fails
     */
    public void downloadFile(String volumeName, String relativePath) throws IOException {
        if (!initialized) {
            throw new IllegalStateException("Volume manager not initialized");
        }

        Path localPath = getVolumePath(volumeName).resolve(relativePath);
        Files.createDirectories(localPath.getParent());

        try {
            String s3Key = buildS3Key(volumeName, relativePath);
            s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(s3Key)
                    .build(),
                localPath
            );

            logger.debug("Downloaded file from S3: {}", s3Key);
        } catch (Exception e) {
            logger.error("Failed to download file from S3: " + relativePath, e);
            throw new IOException("Failed to download file", e);
        }
    }

    @Override
    public void close() {
        if (s3Client != null) {
            s3Client.close();
            initialized = false;
            logger.info("S3 volume manager closed");
        }
    }

    /**
     * Returns the S3 client.
     *
     * @return the S3 client
     */
    public S3Client getS3Client() {
        return s3Client;
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(config.getBucketName())
                .build());
        } catch (NoSuchBucketException e) {
            logger.info("Creating bucket: {}", config.getBucketName());
            try {
                s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(config.getBucketName())
                    .build());
                logger.info("Successfully created bucket: {}", config.getBucketName());
            } catch (Exception ex) {
                logger.error("Failed to create bucket: " + config.getBucketName(), ex);
                throw new RuntimeException("Failed to create S3 bucket: " + config.getBucketName() +
                    ". Check credentials and permissions.", ex);
            }
        } catch (Exception e) {
            logger.error("Failed to verify bucket existence: " + config.getBucketName(), e);
            throw new RuntimeException("Failed to verify S3 bucket: " + config.getBucketName(), e);
        }
    }

    private String buildS3Prefix(String volumeName) {
        String prefix = config.getKeyPrefix() != null ? config.getKeyPrefix() : "";
        return prefix + volumeName + "/";
    }

    private String buildS3Key(String volumeName, String relativePath) {
        return buildS3Prefix(volumeName) + relativePath;
    }
}
