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

import org.flossware.jplatform.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves and validates application dependencies.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Validates that required service dependencies are available</li>
 *   <li>Builds dependency graph between applications</li>
 *   <li>Detects circular dependencies</li>
 *   <li>Computes startup order via topological sort</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * DependencyResolver resolver = new DependencyResolver(serviceRegistry);
 *
 * // Add application with dependencies
 * resolver.addApplication("web-app", descriptor);
 *
 * // Validate all dependencies
 * List<String> errors = resolver.validateDependencies("web-app");
 * if (!errors.isEmpty()) {
 *     throw new Exception("Dependency validation failed: " + errors);
 * }
 *
 * // Get startup order
 * List<String> startupOrder = resolver.getStartupOrder();
 * }</pre>
 *
 * @since 2.0
 */
public class DependencyResolver {

    private static final Logger logger = LoggerFactory.getLogger(DependencyResolver.class);

    private final ServiceRegistry serviceRegistry;
    private final DependencyGraph graph;
    private final Map<String, ApplicationDescriptor> applications;
    private final Map<String, String> serviceProviders; // service interface -> app ID

    /**
     * Creates a new dependency resolver.
     *
     * @param serviceRegistry the service registry for checking service availability (may be null)
     */
    public DependencyResolver(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        this.graph = new DependencyGraph();
        this.applications = new HashMap<>();
        this.serviceProviders = new HashMap<>();
    }

    /**
     * Adds an application to the dependency graph.
     *
     * <p>This method should be called during application deployment to register
     * the application and its dependencies.</p>
     *
     * @param applicationId the application identifier
     * @param descriptor the application descriptor containing dependencies
     * @throws IllegalArgumentException if applicationId is null or empty
     * @throws NullPointerException if descriptor is null
     */
    public void addApplication(String applicationId, ApplicationDescriptor descriptor) {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            throw new IllegalArgumentException("applicationId cannot be null or empty");
        }
        Objects.requireNonNull(descriptor, "descriptor cannot be null");

        applications.put(applicationId, descriptor);
        graph.addNode(applicationId);

        // Add edges for each dependency
        for (ApplicationDependency dep : descriptor.getDependencies()) {
            // Find which application provides this service
            String providerAppId = findServiceProvider(dep.getServiceInterface());
            if (providerAppId != null) {
                graph.addEdge(applicationId, providerAppId);
                logger.debug("[{}] Depends on {} for service {}",
                        applicationId, providerAppId, dep.getServiceInterface());
            } else if (dep.isRequired()) {
                logger.warn("[{}] Required service {} not found",
                        applicationId, dep.getServiceInterface());
            }
        }
    }

    /**
     * Registers that an application provides a specific service.
     *
     * <p>This should be called when an application registers a service
     * in the ServiceRegistry, so we can track which application provides
     * which service for dependency resolution.</p>
     *
     * @param applicationId the application providing the service
     * @param serviceInterface the service interface class name
     * @throws IllegalArgumentException if applicationId or serviceInterface is null or empty
     */
    public void registerServiceProvider(String applicationId, String serviceInterface) {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            throw new IllegalArgumentException("applicationId cannot be null or empty");
        }
        if (serviceInterface == null || serviceInterface.trim().isEmpty()) {
            throw new IllegalArgumentException("serviceInterface cannot be null or empty");
        }

        serviceProviders.put(serviceInterface, applicationId);
        logger.debug("[{}] Registered as provider of {}", applicationId, serviceInterface);
    }

    /**
     * Unregisters that an application provides a specific service.
     *
     * @param applicationId the application ID
     * @param serviceInterface the service interface class name
     */
    public void unregisterServiceProvider(String applicationId, String serviceInterface) {
        serviceProviders.remove(serviceInterface, applicationId);
        logger.debug("[{}] Unregistered as provider of {}", applicationId, serviceInterface);
    }

    /**
     * Removes an application from the dependency graph.
     *
     * @param applicationId the application identifier
     */
    public void removeApplication(String applicationId) {
        applications.remove(applicationId);

        // Remove all service provider mappings for this application
        serviceProviders.entrySet().removeIf(entry ->
            applicationId.equals(entry.getValue()));

        // Remove from dependency graph
        graph.removeNode(applicationId);

        logger.debug("[{}] Removed from dependency resolver", applicationId);
    }

    /**
     * Validates all dependencies for an application.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Required services are available in the ServiceRegistry</li>
     *   <li>Service versions are compatible (if specified)</li>
     *   <li>No circular dependencies exist</li>
     * </ul>
     *
     * @param applicationId the application to validate
     * @return list of validation error messages (empty if valid)
     */
    public List<String> validateDependencies(String applicationId) {
        List<String> errors = new ArrayList<>();

        ApplicationDescriptor descriptor = applications.get(applicationId);
        if (descriptor == null) {
            errors.add("Application not found: " + applicationId);
            return errors;
        }

        // Check each dependency
        for (ApplicationDependency dep : descriptor.getDependencies()) {
            validateDependency(applicationId, dep, errors);
        }

        // Check for circular dependencies
        List<String> cycle = graph.detectCycle();
        if (!cycle.isEmpty()) {
            errors.add("Circular dependency detected: " + String.join(" -> ", cycle));
        }

        return errors;
    }

    /**
     * Validates a single dependency.
     *
     * @param applicationId the dependent application
     * @param dependency the dependency to validate
     * @param errors list to collect error messages
     */
    private void validateDependency(String applicationId, ApplicationDependency dependency,
                                    List<String> errors) {
        String serviceInterface = dependency.getServiceInterface();

        // Check if service is registered
        if (serviceRegistry != null) {
            try {
                // Convert string interface name to Class
                // Use context class loader to support application-specific interfaces
                ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
                Class<?> interfaceClass = contextLoader != null
                    ? Class.forName(serviceInterface, true, contextLoader)
                    : Class.forName(serviceInterface);
                List<?> implementations = serviceRegistry.getAllServices(interfaceClass);

                if (implementations.isEmpty() && dependency.isRequired()) {
                    errors.add(String.format(
                            "[%s] Required service not available: %s",
                            applicationId, serviceInterface
                    ));
                } else if (!implementations.isEmpty()) {
                    // TODO: Validate version compatibility (requires API enhancement)
                    // Need to:
                    // 1. Add version field to ApplicationDependency
                    // 2. Add version metadata to ServiceRegistry
                    // 3. Implement semantic version comparison
                    // 4. Define compatibility rules (exact, semver range, etc.)
                    logger.debug("[{}] Found {} implementation(s) of {}",
                            applicationId, implementations.size(), serviceInterface);
                }
            } catch (ClassNotFoundException e) {
                errors.add(String.format(
                        "[%s] Service interface not found: %s",
                        applicationId, serviceInterface
                ));
            }
        } else {
            // No ServiceRegistry available, can't validate
            logger.warn("[{}] Cannot validate dependency {} - no ServiceRegistry available",
                    applicationId, serviceInterface);
        }
    }

    /**
     * Finds the application that provides a specific service.
     *
     * @param serviceInterface the service interface to find
     * @return application ID of the provider, or null if not found
     */
    private String findServiceProvider(String serviceInterface) {
        // Check if we have a registered provider for this service
        String provider = serviceProviders.get(serviceInterface);
        if (provider != null) {
            return provider;
        }

        // Fallback: check ServiceRegistry directly if available
        if (serviceRegistry == null) {
            return null;
        }

        try {
            Class<?> interfaceClass = Class.forName(serviceInterface);
            List<?> implementations = serviceRegistry.getAllServices(interfaceClass);
            if (!implementations.isEmpty()) {
                logger.warn("Service {} found in registry but provider not tracked", serviceInterface);
            }
        } catch (ClassNotFoundException e) {
            logger.debug("Service interface class not found: {}", serviceInterface);
        }

        return null;
    }

    /**
     * Computes the startup order for all applications based on dependencies.
     *
     * <p>Applications with no dependencies will be started first, followed by
     * applications that depend on them, and so on.</p>
     *
     * @return list of application IDs in startup order (dependencies first)
     * @throws IllegalStateException if circular dependencies are detected
     */
    public List<String> getStartupOrder() {
        List<String> cycle = graph.detectCycle();
        if (!cycle.isEmpty()) {
            throw new IllegalStateException(
                    "Circular dependency detected: " + String.join(" -> ", cycle)
            );
        }

        return graph.topologicalSort();
    }

    /**
     * Gets the applications that depend on the given application.
     *
     * <p>This can be used to determine which applications should be restarted
     * when a service application is updated.</p>
     *
     * @param applicationId the application identifier
     * @return set of dependent application IDs
     */
    public Set<String> getDependentApplications(String applicationId) {
        return graph.getDependents(applicationId);
    }

    /**
     * Gets the applications that the given application depends on.
     *
     * @param applicationId the application identifier
     * @return set of dependency application IDs
     */
    public Set<String> getDependencyApplications(String applicationId) {
        return graph.getDependencies(applicationId);
    }

    /**
     * Checks if an application can be started.
     *
     * <p>An application can be started if all of its required dependencies
     * are currently running.</p>
     *
     * @param applicationId the application to check
     * @param runningApplications set of currently running application IDs
     * @return true if all required dependencies are running, false otherwise
     */
    public boolean canStart(String applicationId, Set<String> runningApplications) {
        ApplicationDescriptor descriptor = applications.get(applicationId);
        if (descriptor == null) {
            return false;
        }

        for (ApplicationDependency dep : descriptor.getDependencies()) {
            if (dep.isRequired()) {
                String providerAppId = findServiceProvider(dep.getServiceInterface());

                if (providerAppId == null) {
                    // Required service has no provider at all
                    logger.debug("[{}] Cannot start: required service {} has no provider",
                            applicationId, dep.getServiceInterface());
                    return false;
                }

                if (!runningApplications.contains(providerAppId)) {
                    // Provider exists but not running
                    logger.debug("[{}] Cannot start: dependency {} not running",
                            applicationId, providerAppId);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns all applications known to this resolver.
     *
     * @return set of all application IDs
     */
    public Set<String> getAllApplications() {
        return Collections.unmodifiableSet(applications.keySet());
    }
}
