package org.flossware.jplatform.api;

import java.security.Permission;
import java.util.Set;

/**
 * Security policy for an application.
 * Controls what permissions are granted to the application.
 *
 * <p>Use this to check permissions before performing privileged operations:</p>
 * <pre>{@code
 * SecurityPolicy policy = context.getSecurityPolicy();
 *
 * // Check permission
 * if (policy.checkPermission(new FilePermission("/tmp/file", "read"))) {
 *     // Proceed with file access
 * }
 *
 * // Or enforce (throws SecurityException if denied)
 * policy.enforce(new FilePermission("/tmp/file", "write"));
 * }</pre>
 *
 * @see ApplicationContext#getSecurityPolicy()
 */
public interface SecurityPolicy {
    /**
     * Checks whether the specified permission is granted.
     *
     * @param permission the permission to check
     * @return true if the permission is granted, false otherwise
     */
    boolean checkPermission(Permission permission);

    /**
     * Enforces that the specified permission is granted.
     * Throws SecurityException if the permission is denied.
     *
     * @param permission the permission to enforce
     * @throws SecurityException if the permission is denied
     */
    void enforce(Permission permission) throws SecurityException;

    /**
     * Returns all permissions explicitly granted to this application.
     *
     * @return set of granted permissions
     */
    Set<Permission> getGrantedPermissions();
}
