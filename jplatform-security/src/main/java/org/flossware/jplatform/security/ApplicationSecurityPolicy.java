package org.flossware.jplatform.security;

import org.flossware.jplatform.api.SecurityConfig;
import org.flossware.jplatform.api.SecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.Permission;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Security policy for an application.
 * Checks permissions against configured policy.
 * <p>
 * This implementation enforces security boundaries for applications by validating
 * requested permissions against a configured set of granted permissions. It supports
 * file permissions, socket permissions, runtime permissions, and special flags for
 * reflection and native code access.
 * <p>
 * Example usage:
 * {@code
 * SecurityConfig config = SecurityConfig.builder()
 *     .allowReflection(true)
 *     .addFilePermission(new FilePermission("/tmp/*", "read,write"))
 *     .addSocketPermission(new SocketPermission("localhost:8080", "connect"))
 *     .build();
 *
 * ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy("my-app", config);
 *
 * // Check permission
 * if (policy.checkPermission(new FilePermission("/tmp/file.txt", "read"))) {
 *     // Permission granted
 * }
 *
 * // Enforce permission (throws SecurityException if denied)
 * policy.enforce(new FilePermission("/tmp/file.txt", "write"));
 * }
 *
 * @see SecurityPolicy
 * @see SecurityConfig
 */
public class ApplicationSecurityPolicy implements SecurityPolicy {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationSecurityPolicy.class);

    private final String applicationId;
    private final Set<Permission> grantedPermissions;
    private final boolean allowReflection;
    private final boolean allowNativeCode;

    /**
     * Creates a new security policy for the specified application.
     * <p>
     * Initializes the granted permissions set with all file, socket, and runtime
     * permissions from the configuration.
     *
     * @param applicationId the unique identifier for the application
     * @param config the security configuration specifying granted permissions
     *               and feature flags
     * @throws NullPointerException if applicationId or config is null
     */
    public ApplicationSecurityPolicy(String applicationId, SecurityConfig config) {
        this.applicationId = Objects.requireNonNull(applicationId, "applicationId cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        this.grantedPermissions = new HashSet<>();
        this.allowReflection = config.isAllowReflection();
        this.allowNativeCode = config.isAllowNativeCode();

        // Add configured permissions
        grantedPermissions.addAll(config.getFilePermissions());
        grantedPermissions.addAll(config.getSocketPermissions());
        grantedPermissions.addAll(config.getRuntimePermissions());

        logger.info("[{}] Security policy created: reflection={}, nativeCode={}, permissions={}",
                applicationId, allowReflection, allowNativeCode, grantedPermissions.size());
    }

    /**
     * Checks if the specified permission is allowed by this security policy.
     * <p>
     * The check proceeds as follows:
     * <ol>
     * <li>Checks if any granted permission implies the requested permission</li>
     * <li>For RuntimePermissions, checks reflection and native code flags</li>
     * </ol>
     *
     * @param permission the permission to check
     * @return true if the permission is granted, false otherwise
     * @throws NullPointerException if permission is null
     */
    @Override
    public boolean checkPermission(Permission permission) {
        Objects.requireNonNull(permission, "permission cannot be null");

        // Check if permission is granted
        for (Permission granted : grantedPermissions) {
            if (granted.implies(permission)) {
                return true;
            }
        }

        // Check reflection permission
        if (permission instanceof RuntimePermission) {
            String name = permission.getName();
            if (name.startsWith("accessDeclaredMembers") ||
                name.startsWith("setAccessible")) {
                return allowReflection;
            }
            if (name.startsWith("loadLibrary")) {
                return allowNativeCode;
            }
        }

        return false;
    }

    /**
     * Enforces the specified permission, throwing SecurityException if denied.
     * <p>
     * This method delegates to {@link #checkPermission(Permission)} and throws
     * an exception if the permission is not granted.
     *
     * @param permission the permission to enforce
     * @throws SecurityException if the permission is denied
     * @throws NullPointerException if permission is null
     */
    @Override
    public void enforce(Permission permission) throws SecurityException {
        if (!checkPermission(permission)) {
            logger.warn("[{}] Permission denied: {}", applicationId, permission);
            throw new SecurityException("Permission denied: " + permission);
        }
    }

    /**
     * Returns a copy of the set of all granted permissions.
     * <p>
     * Modifications to the returned set do not affect the internal state.
     *
     * @return a new set containing all granted permissions
     */
    @Override
    public Set<Permission> getGrantedPermissions() {
        return new HashSet<>(grantedPermissions);
    }

    /**
     * Returns the application ID associated with this security policy.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationId;
    }
}
