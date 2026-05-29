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
 * Represents operating system and architecture combinations.
 *
 * <p>Used to select platform-specific native libraries or binaries.
 * The platform automatically detects the current OS and architecture
 * using system properties.</p>
 *
 * @since 2.0
 */
public enum Platform {
    /** Linux on x86-64 architecture */
    LINUX_X64("linux", "x86-64", ".so"),

    /** Linux on ARM64 architecture */
    LINUX_ARM64("linux", "arm64", ".so"),

    /** Windows on x86-64 architecture */
    WINDOWS_X64("windows", "x86-64", ".dll"),

    /** Windows on ARM64 architecture */
    WINDOWS_ARM64("windows", "arm64", ".dll"),

    /** macOS on x86-64 architecture */
    MACOS_X64("macos", "x86-64", ".dylib"),

    /** macOS on ARM64 (Apple Silicon) architecture */
    MACOS_ARM64("macos", "arm64", ".dylib"),

    /** Any platform (platform-independent binary) */
    ANY("any", "any", "");

    private final String os;
    private final String arch;
    private final String libExtension;

    Platform(String os, String arch, String libExtension) {
        this.os = os;
        this.arch = arch;
        this.libExtension = libExtension;
    }

    /**
     * Returns the OS name.
     *
     * @return operating system name
     */
    public String getOs() {
        return os;
    }

    /**
     * Returns the architecture name.
     *
     * @return architecture name
     */
    public String getArch() {
        return arch;
    }

    /**
     * Returns the native library file extension.
     *
     * @return library extension (including leading dot)
     */
    public String getLibExtension() {
        return libExtension;
    }

    /**
     * Detects the current platform from system properties.
     *
     * @return the current platform, or ANY if unable to detect
     */
    public static Platform detect() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osArch = System.getProperty("os.arch", "").toLowerCase();

        // Normalize architecture names
        String arch = normalizeArch(osArch);

        // Detect OS and match to enum
        if (osName.contains("linux")) {
            if (arch.equals("x86-64")) {
                return LINUX_X64;
            } else if (arch.equals("arm64")) {
                return LINUX_ARM64;
            }
        } else if (osName.contains("windows")) {
            if (arch.equals("x86-64")) {
                return WINDOWS_X64;
            } else if (arch.equals("arm64")) {
                return WINDOWS_ARM64;
            }
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            if (arch.equals("x86-64")) {
                return MACOS_X64;
            } else if (arch.equals("arm64")) {
                return MACOS_ARM64;
            }
        }

        return ANY;
    }

    /**
     * Normalizes architecture names to standard format.
     *
     * @param osArch the os.arch system property value
     * @return normalized architecture name
     */
    private static String normalizeArch(String osArch) {
        if (osArch.contains("amd64") || osArch.contains("x86_64") || osArch.equals("x64")) {
            return "x86-64";
        } else if (osArch.contains("aarch64") || osArch.equals("arm64")) {
            return "arm64";
        }
        return osArch;
    }

    /**
     * Checks if this platform matches the given OS and architecture.
     *
     * @param os the operating system
     * @param arch the architecture
     * @return true if matches, false otherwise
     * @throws NullPointerException if os or arch is null
     */
    public boolean matches(String os, String arch) {
        Objects.requireNonNull(os, "os cannot be null");
        Objects.requireNonNull(arch, "arch cannot be null");

        if (this == ANY) {
            return true;
        }
        return this.os.equalsIgnoreCase(os) && this.arch.equalsIgnoreCase(arch);
    }
}
