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

import java.io.FilePermission;
import java.net.SocketPermission;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for application security policy.
 * Defines file, socket, and runtime permissions, and controls reflection and native code access.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create a custom security configuration
 * SecurityConfig config = SecurityConfig.builder()
 *     .addFilePermission(new FilePermission("/tmp/*", "read,write"))
 *     .addSocketPermission(new SocketPermission("localhost:8080", "connect"))
 *     .allowReflection(true)
 *     .allowNativeCode(false)
 *     .build();
 *
 * // Or use predefined configurations
 * SecurityConfig sandbox = SecurityConfig.sandbox();      // Strict: no reflection, no native
 * SecurityConfig permissive = SecurityConfig.permissive(); // Allows reflection, no native
 * }</pre>
 *
 * @see SecurityPolicy
 */
public class SecurityConfig {
    private final Set<FilePermission> filePermissions;
    private final Set<SocketPermission> socketPermissions;
    private final Set<RuntimePermission> runtimePermissions;
    private final boolean allowReflection;
    private final boolean allowNativeCode;

    private SecurityConfig(Builder builder) {
        this.filePermissions = builder.filePermissions != null ?
                Set.copyOf(builder.filePermissions) : Collections.emptySet();
        this.socketPermissions = builder.socketPermissions != null ?
                Set.copyOf(builder.socketPermissions) : Collections.emptySet();
        this.runtimePermissions = builder.runtimePermissions != null ?
                Set.copyOf(builder.runtimePermissions) : Collections.emptySet();
        this.allowReflection = builder.allowReflection;
        this.allowNativeCode = builder.allowNativeCode;
    }

    /**
     * Returns the file permissions granted to the application.
     *
     * @return an unmodifiable set of file permissions
     */
    public Set<FilePermission> getFilePermissions() {
        return filePermissions;
    }

    /**
     * Returns the socket permissions granted to the application.
     *
     * @return an unmodifiable set of socket permissions
     */
    public Set<SocketPermission> getSocketPermissions() {
        return socketPermissions;
    }

    /**
     * Returns the runtime permissions granted to the application.
     *
     * @return an unmodifiable set of runtime permissions
     */
    public Set<RuntimePermission> getRuntimePermissions() {
        return runtimePermissions;
    }

    /**
     * Checks if the application is allowed to use reflection.
     *
     * @return true if reflection is permitted, false otherwise
     */
    public boolean isAllowReflection() {
        return allowReflection;
    }

    /**
     * Checks if the application is allowed to load native libraries.
     *
     * @return true if native code is permitted, false otherwise
     */
    public boolean isAllowNativeCode() {
        return allowNativeCode;
    }

    /**
     * Creates a strict sandbox security configuration.
     * Disables reflection and native code, with no permissions granted.
     *
     * @return a sandbox configuration
     */
    public static SecurityConfig sandbox() {
        return builder()
                .allowReflection(false)
                .allowNativeCode(false)
                .build();
    }

    /**
     * Creates a permissive security configuration.
     * Allows reflection but disables native code, with no permissions granted.
     *
     * @return a permissive configuration
     */
    public static SecurityConfig permissive() {
        return builder()
                .allowReflection(true)
                .allowNativeCode(false)
                .build();
    }

    /**
     * Creates a new builder for constructing security configurations.
     *
     * @return a new builder instance with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing SecurityConfig instances.
     * Defaults: reflection allowed, native code disabled, no permissions granted.
     */
    public static class Builder {
        private Set<FilePermission> filePermissions = new HashSet<>();
        private Set<SocketPermission> socketPermissions = new HashSet<>();
        private Set<RuntimePermission> runtimePermissions = new HashSet<>();
        private boolean allowReflection = true;
        private boolean allowNativeCode = false;

        /**
         * Adds a file permission to the configuration.
         *
         * @param permission the file permission to grant
         * @return this builder
         * @throws NullPointerException if permission is null
         */
        public Builder addFilePermission(FilePermission permission) {
            Objects.requireNonNull(permission, "File permission cannot be null");
            this.filePermissions.add(permission);
            return this;
        }

        /**
         * Adds a socket permission to the configuration.
         *
         * @param permission the socket permission to grant
         * @return this builder
         * @throws NullPointerException if permission is null
         */
        public Builder addSocketPermission(SocketPermission permission) {
            Objects.requireNonNull(permission, "Socket permission cannot be null");
            this.socketPermissions.add(permission);
            return this;
        }

        /**
         * Adds a runtime permission to the configuration.
         *
         * @param permission the runtime permission to grant
         * @return this builder
         * @throws NullPointerException if permission is null
         */
        public Builder addRuntimePermission(RuntimePermission permission) {
            Objects.requireNonNull(permission, "Runtime permission cannot be null");
            this.runtimePermissions.add(permission);
            return this;
        }

        /**
         * Sets whether reflection is allowed.
         *
         * @param allowReflection true to permit reflection, false to deny
         * @return this builder
         */
        public Builder allowReflection(boolean allowReflection) {
            this.allowReflection = allowReflection;
            return this;
        }

        /**
         * Sets whether native code loading is allowed.
         *
         * @param allowNativeCode true to permit native code, false to deny
         * @return this builder
         */
        public Builder allowNativeCode(boolean allowNativeCode) {
            this.allowNativeCode = allowNativeCode;
            return this;
        }

        /**
         * Builds the SecurityConfig instance.
         *
         * @return a new SecurityConfig with the configured permissions
         */
        public SecurityConfig build() {
            return new SecurityConfig(this);
        }
    }
}
