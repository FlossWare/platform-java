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

import java.util.Objects;

/**
 * Descriptor for a platform-specific native library.
 *
 * <p>Native libraries (.so, .dll, .dylib) can be bundled with applications
 * and loaded automatically by the platform. Each library can target specific
 * operating systems and architectures.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
 *     .applicationId("native-app")
 *     .addNativeLibrary(new NativeLibrary(
 *         "sqlite",
 *         Platform.LINUX_X64,
 *         "file:///libs/libsqlite3.so"
 *     ))
 *     .addNativeLibrary(new NativeLibrary(
 *         "sqlite",
 *         Platform.WINDOWS_X64,
 *         "file:///libs/sqlite3.dll"
 *     ))
 *     .build();
 * }</pre>
 *
 * @since 2.0
 */
public class NativeLibrary {

    private final String name;
    private final Platform platform;
    private final String libraryPath;

    /**
     * Creates a new native library descriptor.
     *
     * @param name the library name (without platform-specific prefix/suffix)
     * @param platform the target platform
     * @param libraryPath the library file path (file:// or http:// URL)
     * @throws IllegalArgumentException if name or libraryPath is null/empty
     * @throws NullPointerException if platform is null
     */
    public NativeLibrary(String name, Platform platform, String libraryPath) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Library name cannot be null or empty");
        }
        if (libraryPath == null || libraryPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Library path cannot be null or empty");
        }
        this.name = name;
        this.platform = Objects.requireNonNull(platform, "Platform cannot be null");
        this.libraryPath = libraryPath;
    }

    /**
     * Returns the library name.
     *
     * @return library name (e.g., "sqlite", "opencv")
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the target platform.
     *
     * @return the platform this library is for
     */
    public Platform getPlatform() {
        return platform;
    }

    /**
     * Returns the library file path.
     *
     * @return the library path (file:// or http:// URL)
     */
    public String getLibraryPath() {
        return libraryPath;
    }

    /**
     * Checks if this library is compatible with the current platform.
     *
     * @return true if compatible, false otherwise
     */
    public boolean isCompatibleWithCurrentPlatform() {
        if (platform == Platform.ANY) {
            return true;
        }
        Platform current = Platform.detect();
        return platform == current;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NativeLibrary that = (NativeLibrary) o;
        return Objects.equals(name, that.name) &&
                platform == that.platform &&
                Objects.equals(libraryPath, that.libraryPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, platform, libraryPath);
    }

    @Override
    public String toString() {
        return "NativeLibrary{" +
                "name='" + name + '\'' +
                ", platform=" + platform +
                ", libraryPath='" + libraryPath + '\'' +
                '}';
    }
}
