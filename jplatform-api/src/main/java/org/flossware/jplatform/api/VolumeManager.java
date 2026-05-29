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

package org.flossware.jplatform.api;

import java.nio.file.Path;
import java.util.List;

/**
 * Manages persistent and ephemeral storage volumes for an application.
 *
 * <p>The VolumeManager provides access to mounted directories that can
 * persist data across application restarts. Volumes are isolated per
 * application and can be configured with size limits.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * ApplicationContext context = ...;
 * Optional<VolumeManager> volumeMgr = context.getVolumeManager();
 *
 * if (volumeMgr.isPresent()) {
 *     Path dbPath = volumeMgr.get().getVolumePath("database");
 *     Files.write(dbPath.resolve("data.db"), data);
 *
 *     long usage = volumeMgr.get().getVolumeUsageBytes("database");
 *     System.out.println("DB volume using: " + usage + " bytes");
 * }
 * }</pre>
 *
 * @since 2.0
 * @see VolumeMount
 * @see ApplicationContext#getVolumeManager()
 */
public interface VolumeManager {

    /**
     * Returns the filesystem path for a named volume.
     *
     * <p>The returned path is the actual directory on the filesystem
     * where the application can read and write files. The volume must
     * be defined in the application descriptor.</p>
     *
     * @param volumeName the name of the volume
     * @return the filesystem path to the volume directory
     * @throws IllegalArgumentException if volume name is not defined
     */
    Path getVolumePath(String volumeName);

    /**
     * Returns all volume mounts defined for this application.
     *
     * @return immutable list of volume mounts
     */
    List<VolumeMount> getVolumes();

    /**
     * Returns the current disk usage for a volume in bytes.
     *
     * <p>Calculates the total size of all files in the volume directory
     * by walking the file tree. This may be slow for large volumes.</p>
     *
     * @param volumeName the name of the volume
     * @return the current usage in bytes
     * @throws IllegalArgumentException if volume name is not defined
     * @throws java.io.IOException if unable to calculate usage
     */
    long getVolumeUsageBytes(String volumeName) throws java.io.IOException;

    /**
     * Checks if a volume exists and is accessible.
     *
     * @param volumeName the name of the volume
     * @return true if the volume exists and is readable/writable
     */
    boolean volumeExists(String volumeName);

    /**
     * Returns the maximum size limit for a volume in bytes.
     *
     * @param volumeName the name of the volume
     * @return the size limit in bytes, or 0 if unlimited
     * @throws IllegalArgumentException if volume name is not defined
     */
    long getVolumeSizeLimit(String volumeName);

    /**
     * Returns whether a volume is persistent (survives restarts).
     *
     * @param volumeName the name of the volume
     * @return true if persistent, false if ephemeral
     * @throws IllegalArgumentException if volume name is not defined
     */
    boolean isPersistent(String volumeName);
}
