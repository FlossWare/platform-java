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

/**
 * Describes a persistent or ephemeral storage volume for an application.
 *
 * <p>Volumes provide isolated directory storage that can persist across
 * application restarts and redeployments. Each volume has a name, mount path,
 * persistence flag, and optional size limit.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * // Persistent database volume
 * VolumeMount dbVolume = new VolumeMount("database", "/var/myapp/db", true, 1024);
 *
 * // Ephemeral cache volume
 * VolumeMount cacheVolume = new VolumeMount("cache", "/var/myapp/cache", false, 512);
 *
 * ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
 *     .applicationId("my-app")
 *     .mainClass("com.example.MyApp")
 *     .addVolume(dbVolume)
 *     .addVolume(cacheVolume)
 *     .build();
 * }</pre>
 *
 * @since 2.0
 * @see VolumeManager
 */
public class VolumeMount {

    private final String name;
    private final String mountPath;
    private final boolean persistent;
    private final long maxSizeMB;

    /**
     * Creates a new volume mount configuration.
     *
     * @param name the volume name (used for identification)
     * @param mountPath the path where the volume appears to the application
     * @param persistent true if volume should survive restarts
     * @param maxSizeMB maximum size in megabytes, or 0 for unlimited
     */
    public VolumeMount(String name, String mountPath, boolean persistent, long maxSizeMB) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Volume name cannot be null or empty");
        }
        if (mountPath == null || mountPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Mount path cannot be null or empty");
        }
        if (maxSizeMB < 0) {
            throw new IllegalArgumentException("Max size cannot be negative");
        }

        this.name = name;
        this.mountPath = mountPath;
        this.persistent = persistent;
        this.maxSizeMB = maxSizeMB;
    }

    /**
     * Returns the volume name.
     *
     * @return the volume name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the mount path visible to the application.
     *
     * @return the mount path
     */
    public String getMountPath() {
        return mountPath;
    }

    /**
     * Returns whether this volume persists across restarts.
     *
     * @return true if persistent, false if ephemeral
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Returns the maximum size limit in megabytes.
     *
     * @return the max size in MB, or 0 for unlimited
     */
    public long getMaxSizeMB() {
        return maxSizeMB;
    }

    /**
     * Returns whether this volume has a size limit.
     *
     * @return true if maxSizeMB &gt; 0
     */
    public boolean hasSizeLimit() {
        return maxSizeMB > 0;
    }

    @Override
    public String toString() {
        return "VolumeMount{" +
                "name='" + name + '\'' +
                ", mountPath='" + mountPath + '\'' +
                ", persistent=" + persistent +
                ", maxSizeMB=" + maxSizeMB +
                '}';
    }
}
