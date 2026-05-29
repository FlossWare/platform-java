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

package org.flossware.platform.storage;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.flossware.platform.api.VolumeManager;
import org.flossware.platform.api.VolumeMount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filesystem-based implementation of VolumeManager.
 *
 * <p>Creates and manages persistent and ephemeral volumes as directories on the local filesystem.
 * Volumes are stored under a base directory with per-application isolation:
 *
 * <pre>
 * /var/platform/volumes/
 *   {applicationId}/
 *     {volumeName}/
 * </pre>
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Automatic directory creation on deploy
 *   <li>Size tracking via filesystem walk
 *   <li>Persistent volumes survive restart
 *   <li>Ephemeral volumes cleaned on undeploy
 * </ul>
 *
 * @since 2.0
 */
public class FileSystemVolumeManager implements VolumeManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemVolumeManager.class);
  private static final String DEFAULT_BASE_PATH = "/var/platform/volumes";

  private final String applicationId;
  private final Path basePath;
  private final Map<String, VolumeMount> volumes;
  private final Map<String, Path> volumePaths;

  /**
   * Creates a new volume manager using the default base path.
   *
   * @param applicationId the application identifier
   * @param volumes list of volume mounts to create
   * @throws IOException if unable to create volume directories
   */
  public FileSystemVolumeManager(String applicationId, List<VolumeMount> volumes)
      throws IOException {
    this(applicationId, volumes, Paths.get(getConfiguredBasePath()));
  }

  /**
   * Creates a new volume manager with a custom base path.
   *
   * @param applicationId the application identifier
   * @param volumes list of volume mounts to create
   * @param basePath the base directory for all volumes
   * @throws IOException if unable to create volume directories
   * @throws NullPointerException if any parameter is null
   */
  public FileSystemVolumeManager(String applicationId, List<VolumeMount> volumes, Path basePath)
      throws IOException {
    this.applicationId = Objects.requireNonNull(applicationId, "applicationId cannot be null");
    this.basePath = Objects.requireNonNull(basePath, "basePath cannot be null");
    Objects.requireNonNull(volumes, "volumes cannot be null");

    this.volumes = new ConcurrentHashMap<>();
    this.volumePaths = new ConcurrentHashMap<>();

    // Register all volumes
    for (VolumeMount volume : volumes) {
      if (this.volumes.containsKey(volume.getName())) {
        throw new IllegalArgumentException("Duplicate volume name: " + volume.getName());
      }
      this.volumes.put(volume.getName(), volume);
    }

    // Create volume directories
    initializeVolumes();

    LOGGER.info(
        "FileSystemVolumeManager initialized for application {} with {} volumes",
        applicationId,
        volumes.size());
  }

  /** Initializes all volume directories. Creates the directory structure and sets up paths. */
  private void initializeVolumes() throws IOException {
    Path appVolumeBase = basePath.resolve(applicationId);

    for (VolumeMount volume : volumes.values()) {
      Path volumePath = appVolumeBase.resolve(volume.getName());

      // Create directory if it doesn't exist
      if (!Files.exists(volumePath)) {
        Files.createDirectories(volumePath);
        LOGGER.info("Created volume directory: {}", volumePath);
      } else {
        LOGGER.info("Volume directory already exists: {}", volumePath);
      }

      volumePaths.put(volume.getName(), volumePath);
    }
  }

  @Override
  public Path getVolumePath(String volumeName) {
    if (!volumes.containsKey(volumeName)) {
      throw new IllegalArgumentException("Volume not defined: " + volumeName);
    }
    return volumePaths.get(volumeName);
  }

  @Override
  public List<VolumeMount> getVolumes() {
    return Collections.unmodifiableList(new ArrayList<>(volumes.values()));
  }

  @Override
  public long getVolumeUsageBytes(String volumeName) throws IOException {
    Path volumePath = getVolumePath(volumeName);

    if (!Files.exists(volumePath)) {
      return 0;
    }

    AtomicLong size = new AtomicLong(0);

    Files.walkFileTree(
        volumePath,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            size.addAndGet(attrs.size());
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) {
            // Log but continue
            LOGGER.warn("Failed to access file during size calculation: {}", file, exc);
            return FileVisitResult.CONTINUE;
          }
        });

    return size.get();
  }

  @Override
  public boolean volumeExists(String volumeName) {
    if (!volumes.containsKey(volumeName)) {
      return false;
    }
    Path volumePath = volumePaths.get(volumeName);
    return volumePath != null
        && Files.exists(volumePath)
        && Files.isReadable(volumePath)
        && Files.isWritable(volumePath);
  }

  @Override
  public long getVolumeSizeLimit(String volumeName) {
    VolumeMount volume = volumes.get(volumeName);
    if (volume == null) {
      throw new IllegalArgumentException("Volume not defined: " + volumeName);
    }
    return (long) volume.getMaxSizeMB() * 1024L * 1024L;
  }

  @Override
  public boolean isPersistent(String volumeName) {
    VolumeMount volume = volumes.get(volumeName);
    if (volume == null) {
      throw new IllegalArgumentException("Volume not defined: " + volumeName);
    }
    return volume.isPersistent();
  }

  /**
   * Cleans up ephemeral volumes. Called during application undeploy to remove non-persistent
   * volumes.
   *
   * @throws IOException if unable to delete directories
   */
  public void cleanupEphemeralVolumes() throws IOException {
    for (VolumeMount volume : volumes.values()) {
      if (!volume.isPersistent()) {
        Path volumePath = volumePaths.get(volume.getName());
        if (volumePath != null && Files.exists(volumePath)) {
          deleteDirectory(volumePath);
          LOGGER.info("Deleted ephemeral volume: {}", volumePath);
        }
      }
    }
  }

  /**
   * Cleans up all volumes (persistent and ephemeral). Use with caution - typically only called when
   * explicitly requested.
   *
   * @throws IOException if unable to delete directories
   */
  public void cleanupAllVolumes() throws IOException {
    for (String volumeName : volumePaths.keySet()) {
      Path volumePath = volumePaths.get(volumeName);
      if (volumePath != null && Files.exists(volumePath)) {
        deleteDirectory(volumePath);
        LOGGER.info("Deleted volume: {}", volumePath);
      }
    }
  }

  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param directory the directory to delete
   * @throws IOException if deletion fails
   */
  private void deleteDirectory(Path directory) throws IOException {
    Files.walkFileTree(
        directory,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) {
              // An error occurred visiting files in this directory
              throw exc;
            }
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  /**
   * Returns the configured base path for volumes. Checks system property first, then defaults to
   * /var/platform/volumes.
   *
   * @return the base path for volumes
   */
  private static String getConfiguredBasePath() {
    return System.getProperty("platform.volumes.dir", DEFAULT_BASE_PATH);
  }

  /**
   * Returns the application ID this manager is associated with.
   *
   * @return the application identifier
   */
  public String getApplicationId() {
    return applicationId;
  }

  /**
   * Returns the base path for all volumes.
   *
   * @return the base directory path
   */
  public Path getBasePath() {
    return basePath;
  }
}
