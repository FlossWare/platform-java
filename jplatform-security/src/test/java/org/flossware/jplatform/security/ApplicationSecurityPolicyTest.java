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

import org.flossware.jplatform.api.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.Permission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ApplicationSecurityPolicy.
 * Tests permission checking, enforcement, and configuration.
 */
class ApplicationSecurityPolicyTest {

    private static final String APP_ID = "test-app";

    @Test
    void testConstructorNullApplicationId() {
        SecurityConfig config = SecurityConfig.builder().build();

        assertThrows(NullPointerException.class, () ->
            new ApplicationSecurityPolicy(null, config)
        );
    }

    @Test
    void testConstructorNullConfig() {
        assertThrows(NullPointerException.class, () ->
            new ApplicationSecurityPolicy(APP_ID, null)
        );
    }

    @Test
    void testConstructorValid() {
        SecurityConfig config = SecurityConfig.builder()
            .allowReflection(true)
            .allowNativeCode(false)
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertNotNull(policy);
        assertEquals(APP_ID, policy.getApplicationId());
    }

    @Test
    void testCheckPermissionNullPermission() {
        SecurityConfig config = SecurityConfig.builder().build();
        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertThrows(NullPointerException.class, () ->
            policy.checkPermission(null)
        );
    }

    @Test
    void testCheckPermissionFilePermissionGranted() {
        SecurityConfig config = SecurityConfig.builder()
            .addFilePermission(new FilePermission("/tmp/*", "read,write"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertTrue(policy.checkPermission(new FilePermission("/tmp/test.txt", "read")));
    }

    @Test
    void testCheckPermissionFilePermissionDenied() {
        SecurityConfig config = SecurityConfig.builder()
            .addFilePermission(new FilePermission("/tmp/*", "read"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertFalse(policy.checkPermission(new FilePermission("/tmp/test.txt", "write")));
    }

    @Test
    void testCheckPermissionFilePermissionWrongPath() {
        SecurityConfig config = SecurityConfig.builder()
            .addFilePermission(new FilePermission("/tmp/*", "read,write"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertFalse(policy.checkPermission(new FilePermission("/etc/config", "read")));
    }

    @Test
    void testCheckPermissionSocketPermissionGranted() {
        SecurityConfig config = SecurityConfig.builder()
            .addSocketPermission(new SocketPermission("localhost:8080", "connect,resolve"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertTrue(policy.checkPermission(new SocketPermission("localhost:8080", "connect")));
    }

    @Test
    void testCheckPermissionSocketPermissionDenied() {
        SecurityConfig config = SecurityConfig.builder()
            .addSocketPermission(new SocketPermission("localhost:8080", "connect"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertFalse(policy.checkPermission(new SocketPermission("localhost:8080", "accept")));
    }

    @Test
    void testCheckPermissionRuntimePermissionGranted() {
        SecurityConfig config = SecurityConfig.builder()
            .addRuntimePermission(new RuntimePermission("createClassLoader"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertTrue(policy.checkPermission(new RuntimePermission("createClassLoader")));
    }

    @Test
    void testCheckPermissionRuntimePermissionDenied() {
        SecurityConfig config = SecurityConfig.builder().build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertFalse(policy.checkPermission(new RuntimePermission("createClassLoader")));
    }

    @Test
    void testCheckPermissionReflectionAllowed() {
        SecurityConfig config = SecurityConfig.builder()
            .allowReflection(true)
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertTrue(policy.checkPermission(new RuntimePermission("accessDeclaredMembers")));
        assertTrue(policy.checkPermission(new RuntimePermission("setAccessible")));
    }

    @Test
    void testCheckPermissionReflectionDenied() {
        SecurityConfig config = SecurityConfig.builder()
            .allowReflection(false)
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertFalse(policy.checkPermission(new RuntimePermission("accessDeclaredMembers")));
        assertFalse(policy.checkPermission(new RuntimePermission("setAccessible")));
    }

    @Test
    void testCheckPermissionNativeCodeAllowed() {
        SecurityConfig config = SecurityConfig.builder()
            .allowNativeCode(true)
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertTrue(policy.checkPermission(new RuntimePermission("loadLibrary.native")));
        assertTrue(policy.checkPermission(new RuntimePermission("loadLibrary.example")));
    }

    @Test
    void testCheckPermissionNativeCodeDenied() {
        SecurityConfig config = SecurityConfig.builder()
            .allowNativeCode(false)
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertFalse(policy.checkPermission(new RuntimePermission("loadLibrary.native")));
        assertFalse(policy.checkPermission(new RuntimePermission("loadLibrary.example")));
    }

    @Test
    void testEnforcePermissionGranted() {
        SecurityConfig config = SecurityConfig.builder()
            .addFilePermission(new FilePermission("/tmp/*", "read"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertDoesNotThrow(() ->
            policy.enforce(new FilePermission("/tmp/test.txt", "read"))
        );
    }

    @Test
    void testEnforcePermissionDenied() {
        SecurityConfig config = SecurityConfig.builder()
            .addFilePermission(new FilePermission("/tmp/*", "read"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        SecurityException ex = assertThrows(SecurityException.class, () ->
            policy.enforce(new FilePermission("/tmp/test.txt", "write"))
        );

        assertTrue(ex.getMessage().contains("Permission denied"));
    }

    @Test
    void testEnforceNullPermission() {
        SecurityConfig config = SecurityConfig.builder().build();
        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertThrows(NullPointerException.class, () ->
            policy.enforce(null)
        );
    }

    @Test
    void testGetGrantedPermissionsEmpty() {
        SecurityConfig config = SecurityConfig.builder().build();
        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        Set<Permission> permissions = policy.getGrantedPermissions();

        assertNotNull(permissions);
        assertTrue(permissions.isEmpty());
    }

    @Test
    void testGetGrantedPermissionsWithPermissions() {
        SecurityConfig config = SecurityConfig.builder()
            .addFilePermission(new FilePermission("/tmp/*", "read"))
            .addSocketPermission(new SocketPermission("localhost:8080", "connect"))
            .addRuntimePermission(new RuntimePermission("createClassLoader"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        Set<Permission> permissions = policy.getGrantedPermissions();

        assertEquals(3, permissions.size());
    }

    @Test
    void testGetGrantedPermissionsIsDefensiveCopy() {
        SecurityConfig config = SecurityConfig.builder()
            .addFilePermission(new FilePermission("/tmp/*", "read"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        Set<Permission> permissions = policy.getGrantedPermissions();
        permissions.clear();

        // Original should still have the permission
        Set<Permission> newPermissions = policy.getGrantedPermissions();
        assertEquals(1, newPermissions.size());
    }

    @Test
    void testGetApplicationId() {
        SecurityConfig config = SecurityConfig.builder().build();
        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy("my-app-123", config);

        assertEquals("my-app-123", policy.getApplicationId());
    }

    @Test
    void testMultipleFilePermissions() {
        SecurityConfig config = SecurityConfig.builder()
            .addFilePermission(new FilePermission("/tmp/*", "read"))
            .addFilePermission(new FilePermission("/var/log/*", "write"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertTrue(policy.checkPermission(new FilePermission("/tmp/test.txt", "read")));
        assertTrue(policy.checkPermission(new FilePermission("/var/log/app.log", "write")));
        assertFalse(policy.checkPermission(new FilePermission("/etc/config", "read")));
    }

    @Test
    void testMultipleSocketPermissions() {
        SecurityConfig config = SecurityConfig.builder()
            .addSocketPermission(new SocketPermission("localhost:8080", "connect"))
            .addSocketPermission(new SocketPermission("example.com:443", "connect,resolve"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertTrue(policy.checkPermission(new SocketPermission("localhost:8080", "connect")));
        assertTrue(policy.checkPermission(new SocketPermission("example.com:443", "connect")));
        assertFalse(policy.checkPermission(new SocketPermission("badsite.com:80", "connect")));
    }

    @Test
    void testPermissionImplication() {
        // FilePermission("/tmp/*", "read,write") should imply FilePermission("/tmp/test.txt", "read")
        // Note: * only matches files in that directory, not subdirectories
        // Use /- for recursive matching
        SecurityConfig config = SecurityConfig.builder()
            .addFilePermission(new FilePermission("/tmp/*", "read,write"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertTrue(policy.checkPermission(new FilePermission("/tmp/test.txt", "read")));
        assertTrue(policy.checkPermission(new FilePermission("/tmp/test.txt", "write")));
        // /tmp/* does NOT match subdirectories
        assertFalse(policy.checkPermission(new FilePermission("/tmp/subdir/file.txt", "read")));
    }

    @Test
    void testReflectionAndNativeCodeBothAllowed() {
        SecurityConfig config = SecurityConfig.builder()
            .allowReflection(true)
            .allowNativeCode(true)
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertTrue(policy.checkPermission(new RuntimePermission("accessDeclaredMembers")));
        assertTrue(policy.checkPermission(new RuntimePermission("loadLibrary.mylib")));
    }

    @Test
    void testReflectionAndNativeCodeBothDenied() {
        SecurityConfig config = SecurityConfig.builder()
            .allowReflection(false)
            .allowNativeCode(false)
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertFalse(policy.checkPermission(new RuntimePermission("accessDeclaredMembers")));
        assertFalse(policy.checkPermission(new RuntimePermission("loadLibrary.mylib")));
    }

    @Test
    void testOtherRuntimePermissionsNotAffectedByReflectionFlag() {
        SecurityConfig config = SecurityConfig.builder()
            .allowReflection(true)
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        // Reflection permissions should be granted
        assertTrue(policy.checkPermission(new RuntimePermission("accessDeclaredMembers")));

        // But other runtime permissions should still be denied
        assertFalse(policy.checkPermission(new RuntimePermission("createClassLoader")));
    }

    @Test
    void testOtherRuntimePermissionsNotAffectedByNativeCodeFlag() {
        SecurityConfig config = SecurityConfig.builder()
            .allowNativeCode(true)
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        // Native code permissions should be granted
        assertTrue(policy.checkPermission(new RuntimePermission("loadLibrary.test")));

        // But other runtime permissions should still be denied
        assertFalse(policy.checkPermission(new RuntimePermission("createClassLoader")));
    }

    @Test
    void testExplicitRuntimePermissionGranted() {
        SecurityConfig config = SecurityConfig.builder()
            .allowReflection(false)
            .addRuntimePermission(new RuntimePermission("accessDeclaredMembers"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        // Should be granted because it's explicitly in the granted permissions set
        assertTrue(policy.checkPermission(new RuntimePermission("accessDeclaredMembers")));
    }

    @Test
    void testRecursiveFilePermission() {
        // FilePermission("/tmp/-", "read") matches all files recursively under /tmp
        SecurityConfig config = SecurityConfig.builder()
            .addFilePermission(new FilePermission("/tmp/-", "read"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        assertTrue(policy.checkPermission(new FilePermission("/tmp/test.txt", "read")));
        assertTrue(policy.checkPermission(new FilePermission("/tmp/subdir/file.txt", "read")));
        assertTrue(policy.checkPermission(new FilePermission("/tmp/a/b/c/deep.txt", "read")));
        assertFalse(policy.checkPermission(new FilePermission("/tmp/test.txt", "write")));
    }

    @Test
    void testComplexPolicyAllFeatures() {
        SecurityConfig config = SecurityConfig.builder()
            .allowReflection(true)
            .allowNativeCode(true)
            .addFilePermission(new FilePermission("/tmp/*", "read,write"))
            .addFilePermission(new FilePermission("/var/log/*", "write"))
            .addSocketPermission(new SocketPermission("localhost:*", "connect,accept"))
            .addRuntimePermission(new RuntimePermission("createClassLoader"))
            .build();

        ApplicationSecurityPolicy policy = new ApplicationSecurityPolicy(APP_ID, config);

        // File permissions
        assertTrue(policy.checkPermission(new FilePermission("/tmp/test.txt", "read")));
        assertTrue(policy.checkPermission(new FilePermission("/var/log/app.log", "write")));

        // Socket permissions
        assertTrue(policy.checkPermission(new SocketPermission("localhost:8080", "connect")));

        // Runtime permissions
        assertTrue(policy.checkPermission(new RuntimePermission("createClassLoader")));

        // Reflection
        assertTrue(policy.checkPermission(new RuntimePermission("accessDeclaredMembers")));

        // Native code
        assertTrue(policy.checkPermission(new RuntimePermission("loadLibrary.native")));

        // Denied permissions
        assertFalse(policy.checkPermission(new FilePermission("/etc/passwd", "read")));
        assertFalse(policy.checkPermission(new SocketPermission("external.com:80", "connect")));
    }
}
