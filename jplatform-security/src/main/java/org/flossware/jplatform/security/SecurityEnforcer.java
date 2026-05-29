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

package org.flossware.jplatform.security;

import org.flossware.jplatform.api.SecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.Permission;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern security enforcer using StackWalker API instead of deprecated SecurityManager.
 *
 * <p>This class provides automatic security enforcement by intercepting security-sensitive
 * operations and checking them against application security policies. It uses the
 * StackWalker API (Java 9+) to identify the calling ClassLoader and apply the appropriate
 * security policy.</p>
 *
 * <p><b>Advantages over SecurityManager:</b></p>
 * <ul>
 *   <li>Not deprecated - works with Java 17+</li>
 *   <li>Better performance - no global permission checks</li>
 *   <li>More flexible - can be enabled/disabled per operation</li>
 *   <li>Cleaner stack traces - no deep SecurityManager call chains</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * // Register policy for an application
 * SecurityEnforcer enforcer = SecurityEnforcer.getInstance();
 * enforcer.registerPolicy(classLoader, policy);
 *
 * // Enforce permission check
 * enforcer.checkFileAccess("/tmp/file.txt", "read");
 * enforcer.checkSocketAccess("example.com", 80, "connect");
 * </pre>
 *
 * <p><b>Implementation Note:</b></p>
 * <p>This enforcer uses StackWalker to identify the caller's ClassLoader and look up
 * the corresponding security policy. It provides manual enforcement points that
 * applications can call, or can be integrated with bytecode instrumentation for
 * transparent enforcement.</p>
 *
 * @since 2.0
 * @see SecurityPolicy
 * @see ApplicationSecurityPolicy
 */
public class SecurityEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(SecurityEnforcer.class);
    private static final SecurityEnforcer INSTANCE = new SecurityEnforcer();

    private final Map<ClassLoader, SecurityPolicy> policies;
    private final StackWalker stackWalker;
    private volatile boolean enabled;

    /**
     * Private constructor for singleton pattern.
     */
    private SecurityEnforcer() {
        this.policies = new ConcurrentHashMap<>();
        this.stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        this.enabled = Boolean.getBoolean("jplatform.security.enforce");
    }

    /**
     * Returns the singleton instance of SecurityEnforcer.
     *
     * @return the singleton instance
     */
    public static SecurityEnforcer getInstance() {
        return INSTANCE;
    }

    /**
     * Enables or disables security enforcement globally.
     *
     * <p>When disabled, all security checks are bypassed. This is useful for
     * development/testing but should be enabled in production.</p>
     *
     * @param enabled true to enable enforcement, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("Security enforcement {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Returns whether security enforcement is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Registers a security policy for a ClassLoader.
     *
     * <p>All classes loaded by this ClassLoader will be subject to the
     * registered security policy.</p>
     *
     * @param classLoader the ClassLoader to associate with this policy
     * @param policy the security policy to enforce
     */
    public void registerPolicy(ClassLoader classLoader, SecurityPolicy policy) {
        policies.put(classLoader, policy);
        logger.info("Registered security policy for ClassLoader: {}", classLoader);
    }

    /**
     * Unregisters a security policy for a ClassLoader.
     *
     * @param classLoader the ClassLoader to unregister
     */
    public void unregisterPolicy(ClassLoader classLoader) {
        SecurityPolicy removed = policies.remove(classLoader);
        if (removed != null) {
            logger.info("Unregistered security policy for ClassLoader: {}", classLoader);
        }
    }

    /**
     * Checks if the caller has permission to access a file.
     *
     * <p>This method uses StackWalker to determine the calling class's ClassLoader
     * and checks the associated security policy.</p>
     *
     * @param path the file path
     * @param actions the actions (read, write, delete, execute)
     * @throws SecurityException if access is denied
     */
    public void checkFileAccess(String path, String actions) {
        if (!enabled) {
            return;
        }

        FilePermission permission = new FilePermission(path, actions);
        enforcePermission(permission);
    }

    /**
     * Checks if the caller has permission to access a network socket.
     *
     * @param host the host name or IP address
     * @param port the port number
     * @param actions the actions (connect, accept, listen, resolve)
     * @throws SecurityException if access is denied
     */
    public void checkSocketAccess(String host, int port, String actions) {
        if (!enabled) {
            return;
        }

        if (port < -1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }

        // Per SocketPermission spec: port -1 means "any port" and should be just hostname
        String permissionTarget = (port == -1) ? host : (host + ":" + port);
        SocketPermission permission = new SocketPermission(permissionTarget, actions);
        enforcePermission(permission);
    }

    /**
     * Checks if the caller has permission to use reflection.
     *
     * @throws SecurityException if reflection is not allowed
     */
    public void checkReflectionAccess() {
        if (!enabled) {
            return;
        }

        RuntimePermission permission = new RuntimePermission("accessDeclaredMembers");
        enforcePermission(permission);
    }

    /**
     * Checks if the caller has permission to load native libraries.
     *
     * @param libraryName the name of the library
     * @throws SecurityException if native code is not allowed
     */
    public void checkNativeAccess(String libraryName) {
        if (!enabled) {
            return;
        }

        RuntimePermission permission = new RuntimePermission("loadLibrary." + libraryName);
        enforcePermission(permission);
    }

    /**
     * Generic permission enforcement using the caller's security policy.
     *
     * @param permission the permission to check
     * @throws SecurityException if permission is denied
     */
    public void enforcePermission(Permission permission) {
        if (!enabled) {
            return;
        }

        ClassLoader callerClassLoader = getCallerClassLoader();

        if (callerClassLoader == null) {
            // System class, allow
            return;
        }

        SecurityPolicy policy = policies.get(callerClassLoader);

        if (policy == null) {
            // Check if this is a trusted platform classloader
            if (isPlatformClassLoader(callerClassLoader)) {
                logger.debug("Platform ClassLoader {}, allowing access", callerClassLoader);
                return;
            }

            // No policy registered for application classloader - deny by default
            logger.warn("No security policy registered for ClassLoader {}, denying {} access",
                    callerClassLoader, permission.getClass().getSimpleName());
            throw new SecurityException(
                    "No security policy registered for ClassLoader: " + callerClassLoader);
        }

        // Enforce the policy
        try {
            policy.enforce(permission);
        } catch (SecurityException e) {
            logger.warn("Security violation: {} denied for ClassLoader {}",
                    permission, callerClassLoader);
            throw e;
        }
    }

    /**
     * Uses StackWalker to determine the caller's ClassLoader.
     *
     * <p>This walks the stack and finds the first class that is not in this
     * security package, then returns its ClassLoader.</p>
     *
     * @return the caller's ClassLoader, or null if called from system class
     */
    private ClassLoader getCallerClassLoader() {
        return stackWalker.walk(frames -> {
            return frames
                    .skip(1)  // Skip this method
                    .filter(frame -> !frame.getClassName().startsWith("org.flossware.jplatform.security"))
                    .findFirst()
                    .map(frame -> frame.getDeclaringClass().getClassLoader())
                    .orElse(null);
        });
    }

    /**
     * Checks if a ClassLoader is a trusted platform classloader.
     * <p>
     * Trusted classloaders are:
     * <ul>
     *   <li>Platform classloader - loads platform modules</li>
     *   <li>System classloader - loads platform-java core classes</li>
     * </ul>
     *
     * @param cl the ClassLoader to check
     * @return true if the classloader is trusted
     */
    private boolean isPlatformClassLoader(ClassLoader cl) {
        // Allow platform and system classloaders only
        return cl == ClassLoader.getPlatformClassLoader() ||
               cl == ClassLoader.getSystemClassLoader();
    }

    /**
     * Returns the security policy for a specific ClassLoader.
     *
     * @param classLoader the ClassLoader
     * @return the security policy, or null if not registered or classLoader is null
     */
    public SecurityPolicy getPolicy(ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        return policies.get(classLoader);
    }

    /**
     * Returns the number of registered policies.
     *
     * @return the number of policies
     */
    public int getPolicyCount() {
        return policies.size();
    }

    /**
     * Clears all registered policies.
     *
     * <p>This should only be used during platform shutdown or testing.</p>
     */
    public void clearPolicies() {
        policies.clear();
        logger.info("Cleared all security policies");
    }
}
