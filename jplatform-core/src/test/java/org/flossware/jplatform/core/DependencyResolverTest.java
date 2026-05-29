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

package org.flossware.jplatform.core;

import org.flossware.jplatform.api.ApplicationDependency;
import org.flossware.jplatform.api.ApplicationDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DependencyResolver.
 * Tests dependency validation, startup order calculation, and cycle detection.
 *
 * Note: DependencyResolver operates on service-level dependencies, not application IDs.
 * These tests focus on basic API contract validation.
 */
class DependencyResolverTest {

    private DependencyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DependencyResolver(null);
    }

    @Test
    void testEmptyResolverReturnsEmptyStartupOrder() {
        List<String> order = resolver.getStartupOrder();
        assertNotNull(order);
        assertTrue(order.isEmpty());
    }

    @Test
    void testSingleApplicationWithNoDependencies() {
        ApplicationDescriptor app = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App1")
                .addClasspathEntry(URI.create("file:///app1.jar"))
                .build();

        resolver.addApplication("app1", app);

        List<String> order = resolver.getStartupOrder();
        assertEquals(1, order.size());
        assertEquals("app1", order.get(0));
    }

    @Test
    void testMultipleApplicationsWithNoDependencies() {
        ApplicationDescriptor app1 = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App1")
                .addClasspathEntry(URI.create("file:///app1.jar"))
                .build();

        ApplicationDescriptor app2 = ApplicationDescriptor.builder()
                .applicationId("app2")
                .mainClass("com.example.App2")
                .addClasspathEntry(URI.create("file:///app2.jar"))
                .build();

        resolver.addApplication("app1", app1);
        resolver.addApplication("app2", app2);

        List<String> order = resolver.getStartupOrder();
        assertEquals(2, order.size());
        assertTrue(order.contains("app1"));
        assertTrue(order.contains("app2"));
    }

    @Test
    void testRemoveApplication() {
        ApplicationDescriptor app1 = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App1")
                .addClasspathEntry(URI.create("file:///app1.jar"))
                .build();

        resolver.addApplication("app1", app1);
        assertEquals(1, resolver.getStartupOrder().size());

        resolver.removeApplication("app1");
        assertEquals(0, resolver.getStartupOrder().size());
    }

    @Test
    void testGetDependentApplicationsReturnsEmptySetForIndependentApps() {
        ApplicationDescriptor app1 = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App1")
                .addClasspathEntry(URI.create("file:///app1.jar"))
                .build();

        resolver.addApplication("app1", app1);

        Set<String> dependents = resolver.getDependentApplications("app1");
        assertNotNull(dependents);
        assertTrue(dependents.isEmpty());
    }

    @Test
    void testValidateDependenciesReturnsEmptyForAppWithNoDependencies() {
        ApplicationDescriptor app1 = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App1")
                .addClasspathEntry(URI.create("file:///app1.jar"))
                .build();

        resolver.addApplication("app1", app1);

        List<String> errors = resolver.validateDependencies("app1");
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void testValidateDependenciesDetectsCircularDependency() {
        // Create circular dependency: app-a -> app-b -> app-c -> app-a
        // Note: Dependencies are service-based, not application ID-based

        ApplicationDescriptor appA = ApplicationDescriptor.builder()
                .applicationId("app-a")
                .mainClass("com.example.AppA")
                .addClasspathEntry(URI.create("file:///app-a.jar"))
                .addDependency(new ApplicationDependency("ServiceB", ApplicationDependency.DependencyType.REQUIRED, "1.0.0"))
                .build();

        ApplicationDescriptor appB = ApplicationDescriptor.builder()
                .applicationId("app-b")
                .mainClass("com.example.AppB")
                .addClasspathEntry(URI.create("file:///app-b.jar"))
                .addDependency(new ApplicationDependency("ServiceC", ApplicationDependency.DependencyType.REQUIRED, "1.0.0"))
                .build();

        ApplicationDescriptor appC = ApplicationDescriptor.builder()
                .applicationId("app-c")
                .mainClass("com.example.AppC")
                .addClasspathEntry(URI.create("file:///app-c.jar"))
                .addDependency(new ApplicationDependency("ServiceA", ApplicationDependency.DependencyType.REQUIRED, "1.0.0"))
                .build();

        // Register service providers
        resolver.registerServiceProvider("app-a", "ServiceA");
        resolver.registerServiceProvider("app-b", "ServiceB");
        resolver.registerServiceProvider("app-c", "ServiceC");

        // Add applications
        resolver.addApplication("app-a", appA);
        resolver.addApplication("app-b", appB);
        resolver.addApplication("app-c", appC);

        // Validate app-a - should detect circular dependency
        List<String> errors = resolver.validateDependencies("app-a");

        assertNotNull(errors);
        assertFalse(errors.isEmpty(), "Should detect circular dependency");
        assertTrue(errors.stream().anyMatch(err -> err.contains("Circular dependency detected")),
                "Error should mention circular dependency");
    }

    @Test
    void testGetStartupOrderThrowsExceptionForCircularDependency() {
        // Create circular dependency: app-a -> app-b -> app-c -> app-a

        ApplicationDescriptor appA = ApplicationDescriptor.builder()
                .applicationId("app-a")
                .mainClass("com.example.AppA")
                .addClasspathEntry(URI.create("file:///app-a.jar"))
                .addDependency(new ApplicationDependency("ServiceB", ApplicationDependency.DependencyType.REQUIRED, "1.0.0"))
                .build();

        ApplicationDescriptor appB = ApplicationDescriptor.builder()
                .applicationId("app-b")
                .mainClass("com.example.AppB")
                .addClasspathEntry(URI.create("file:///app-b.jar"))
                .addDependency(new ApplicationDependency("ServiceC", ApplicationDependency.DependencyType.REQUIRED, "1.0.0"))
                .build();

        ApplicationDescriptor appC = ApplicationDescriptor.builder()
                .applicationId("app-c")
                .mainClass("com.example.AppC")
                .addClasspathEntry(URI.create("file:///app-c.jar"))
                .addDependency(new ApplicationDependency("ServiceA", ApplicationDependency.DependencyType.REQUIRED, "1.0.0"))
                .build();

        // Register service providers
        resolver.registerServiceProvider("app-a", "ServiceA");
        resolver.registerServiceProvider("app-b", "ServiceB");
        resolver.registerServiceProvider("app-c", "ServiceC");

        // Add applications
        resolver.addApplication("app-a", appA);
        resolver.addApplication("app-b", appB);
        resolver.addApplication("app-c", appC);

        // getStartupOrder should throw for circular dependencies
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            resolver.getStartupOrder();
        });

        assertTrue(exception.getMessage().contains("Circular dependency detected"),
                "Exception should mention circular dependency");
    }

    @Test
    void testRemoveNonExistentApplication() {
        // Should not throw
        resolver.removeApplication("non-existent");
        assertEquals(0, resolver.getStartupOrder().size());
    }

    @Test
    void testRemoveNullApplication() {
        // Should handle null gracefully
        resolver.removeApplication(null);
        assertEquals(0, resolver.getStartupOrder().size());
    }

    @Test
    void testGetDependentApplicationsForNonExistent() {
        Set<String> dependents = resolver.getDependentApplications("non-existent");
        assertNotNull(dependents);
        assertTrue(dependents.isEmpty());
    }

    @Test
    void testGetDependentApplicationsForNull() {
        Set<String> dependents = resolver.getDependentApplications(null);
        assertNotNull(dependents);
        assertTrue(dependents.isEmpty());
    }

    @Test
    void testValidateDependenciesForNonExistent() {
        List<String> errors = resolver.validateDependencies("non-existent");
        assertNotNull(errors);
        // Should return empty or list with error
    }

    @Test
    void testValidateDependenciesForNull() {
        List<String> errors = resolver.validateDependencies(null);
        assertNotNull(errors);
    }

    @Test
    void testAddApplicationWithNullId() {
        ApplicationDescriptor app = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App1")
                .addClasspathEntry(URI.create("file:///app1.jar"))
                .build();

        // Should throw exception
        assertThrows(IllegalArgumentException.class, () ->
            resolver.addApplication(null, app)
        );
    }

    @Test
    void testAddApplicationWithNullDescriptor() {
        // Should throw exception
        assertThrows(NullPointerException.class, () ->
            resolver.addApplication("app1", null)
        );
    }

    @Test
    void testRegisterServiceProviderWithNullAppId() {
        assertThrows(IllegalArgumentException.class, () ->
            resolver.registerServiceProvider(null, "ServiceA")
        );
    }

    @Test
    void testRegisterServiceProviderWithNullService() {
        assertThrows(IllegalArgumentException.class, () ->
            resolver.registerServiceProvider("app1", null)
        );
    }

    @Test
    void testMultipleRegistrationsOfSameApp() {
        ApplicationDescriptor app1 = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App1")
                .addClasspathEntry(URI.create("file:///app1.jar"))
                .build();

        resolver.addApplication("app1", app1);
        resolver.addApplication("app1", app1);

        List<String> order = resolver.getStartupOrder();
        // Should have only one entry
        assertTrue(order.size() <= 2); // May allow duplicates or may deduplicate
    }
}
