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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ApplicationDependency.
 * Tests constructor validation, getters, isRequired(), isOptional(), equals(), hashCode(), and toString().
 */
class ApplicationDependencyTest {

    @Test
    void testConstructorValid() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );

        assertEquals("com.example.Service", dep.getServiceInterface());
        assertEquals(ApplicationDependency.DependencyType.REQUIRED, dep.getType());
        assertEquals("1.0.0", dep.getVersion());
    }

    @Test
    void testConstructorNullVersion() {
        // Null version should default to "latest"
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.OPTIONAL,
            null
        );

        assertEquals("com.example.Service", dep.getServiceInterface());
        assertEquals(ApplicationDependency.DependencyType.OPTIONAL, dep.getType());
        assertEquals("latest", dep.getVersion());
    }

    @Test
    void testConstructorNullServiceInterface() {
        assertThrows(IllegalArgumentException.class, () ->
            new ApplicationDependency(null, ApplicationDependency.DependencyType.REQUIRED, "1.0.0")
        );
    }

    @Test
    void testConstructorEmptyServiceInterface() {
        assertThrows(IllegalArgumentException.class, () ->
            new ApplicationDependency("", ApplicationDependency.DependencyType.REQUIRED, "1.0.0")
        );
    }

    @Test
    void testConstructorWhitespaceServiceInterface() {
        assertThrows(IllegalArgumentException.class, () ->
            new ApplicationDependency("   ", ApplicationDependency.DependencyType.REQUIRED, "1.0.0")
        );
    }

    @Test
    void testConstructorNullDependencyType() {
        assertThrows(NullPointerException.class, () ->
            new ApplicationDependency("com.example.Service", null, "1.0.0")
        );
    }

    @Test
    void testIsRequiredWhenRequired() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );

        assertTrue(dep.isRequired());
        assertFalse(dep.isOptional());
    }

    @Test
    void testIsOptionalWhenOptional() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.OPTIONAL,
            "1.0.0"
        );

        assertTrue(dep.isOptional());
        assertFalse(dep.isRequired());
    }

    @Test
    void testEqualsIdentical() {
        ApplicationDependency dep1 = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );
        ApplicationDependency dep2 = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );

        assertEquals(dep1, dep2);
        assertEquals(dep1.hashCode(), dep2.hashCode());
    }

    @Test
    void testEqualsSameInstance() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );

        assertEquals(dep, dep);
    }

    @Test
    void testEqualsNull() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );

        assertNotEquals(dep, null);
    }

    @Test
    void testEqualsDifferentClass() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );

        assertNotEquals(dep, "not a dependency");
    }

    @Test
    void testEqualsDifferentServiceInterface() {
        ApplicationDependency dep1 = new ApplicationDependency(
            "com.example.Service1",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );
        ApplicationDependency dep2 = new ApplicationDependency(
            "com.example.Service2",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );

        assertNotEquals(dep1, dep2);
    }

    @Test
    void testEqualsDifferentType() {
        ApplicationDependency dep1 = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );
        ApplicationDependency dep2 = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.OPTIONAL,
            "1.0.0"
        );

        assertNotEquals(dep1, dep2);
    }

    @Test
    void testEqualsDifferentVersion() {
        ApplicationDependency dep1 = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );
        ApplicationDependency dep2 = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "2.0.0"
        );

        assertNotEquals(dep1, dep2);
    }

    @Test
    void testHashCodeConsistency() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );

        int hash1 = dep.hashCode();
        int hash2 = dep.hashCode();

        assertEquals(hash1, hash2);
    }

    @Test
    void testToString() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "1.0.0"
        );

        String str = dep.toString();

        assertTrue(str.contains("com.example.Service"));
        assertTrue(str.contains("REQUIRED"));
        assertTrue(str.contains("1.0.0"));
        assertTrue(str.contains("ApplicationDependency"));
    }

    @Test
    void testDependencyTypeEnumValues() {
        ApplicationDependency.DependencyType[] types = ApplicationDependency.DependencyType.values();

        assertEquals(2, types.length);
        assertEquals(ApplicationDependency.DependencyType.REQUIRED, types[0]);
        assertEquals(ApplicationDependency.DependencyType.OPTIONAL, types[1]);
    }

    @Test
    void testDependencyTypeValueOf() {
        assertEquals(ApplicationDependency.DependencyType.REQUIRED,
            ApplicationDependency.DependencyType.valueOf("REQUIRED"));
        assertEquals(ApplicationDependency.DependencyType.OPTIONAL,
            ApplicationDependency.DependencyType.valueOf("OPTIONAL"));
    }

    @Test
    void testLatestVersionDefault() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            null
        );

        assertEquals("latest", dep.getVersion());
    }

    @Test
    void testExplicitLatestVersion() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.OPTIONAL,
            "latest"
        );

        assertEquals("latest", dep.getVersion());
    }

    @Test
    void testSemanticVersion() {
        ApplicationDependency dep = new ApplicationDependency(
            "com.example.Service",
            ApplicationDependency.DependencyType.REQUIRED,
            "2.3.4"
        );

        assertEquals("2.3.4", dep.getVersion());
    }
}
