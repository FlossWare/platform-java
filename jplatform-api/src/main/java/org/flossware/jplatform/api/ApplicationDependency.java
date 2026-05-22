package org.flossware.jplatform.api;

import java.util.Objects;

/**
 * Describes a dependency on a service provided by another application.
 *
 * <p>Applications can declare dependencies on services to ensure proper startup ordering
 * and availability validation. The platform validates dependencies at deploy time and
 * starts applications in the correct order based on the dependency graph.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
 *     .applicationId("web-app")
 *     .addDependency(new ApplicationDependency(
 *         "com.example.DatabaseService",
 *         DependencyType.REQUIRED,
 *         "1.0.0"
 *     ))
 *     .addDependency(new ApplicationDependency(
 *         "com.example.CacheService",
 *         DependencyType.OPTIONAL,
 *         "latest"
 *     ))
 *     .build();
 * }</pre>
 *
 * @since 2.0
 */
public class ApplicationDependency {

    /**
     * Dependency type indicating whether the dependency is required or optional.
     */
    public enum DependencyType {
        /**
         * Required dependency - application start fails if service is not available.
         */
        REQUIRED,

        /**
         * Optional dependency - application starts even if service is unavailable.
         * The application should handle the missing service gracefully.
         */
        OPTIONAL
    }

    private final String serviceInterface;
    private final DependencyType type;
    private final String version;

    /**
     * Creates a new application dependency.
     *
     * @param serviceInterface the fully qualified interface name of the required service
     * @param type the dependency type (REQUIRED or OPTIONAL)
     * @param version the required version (semantic version string or "latest")
     * @throws IllegalArgumentException if serviceInterface is null or empty
     * @throws NullPointerException if type is null
     */
    public ApplicationDependency(String serviceInterface, DependencyType type, String version) {
        if (serviceInterface == null || serviceInterface.trim().isEmpty()) {
            throw new IllegalArgumentException("Service interface cannot be null or empty");
        }
        this.serviceInterface = serviceInterface;
        this.type = Objects.requireNonNull(type, "Dependency type cannot be null");
        this.version = version != null ? version : "latest";
    }

    /**
     * Returns the service interface name.
     *
     * @return the fully qualified interface name
     */
    public String getServiceInterface() {
        return serviceInterface;
    }

    /**
     * Returns the dependency type.
     *
     * @return REQUIRED or OPTIONAL
     */
    public DependencyType getType() {
        return type;
    }

    /**
     * Returns the required version.
     *
     * @return the version string (semantic version or "latest")
     */
    public String getVersion() {
        return version;
    }

    /**
     * Checks if this is a required dependency.
     *
     * @return true if dependency type is REQUIRED, false otherwise
     */
    public boolean isRequired() {
        return type == DependencyType.REQUIRED;
    }

    /**
     * Checks if this is an optional dependency.
     *
     * @return true if dependency type is OPTIONAL, false otherwise
     */
    public boolean isOptional() {
        return type == DependencyType.OPTIONAL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationDependency that = (ApplicationDependency) o;
        return Objects.equals(serviceInterface, that.serviceInterface) &&
                type == that.type &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceInterface, type, version);
    }

    @Override
    public String toString() {
        return "ApplicationDependency{" +
                "serviceInterface='" + serviceInterface + '\'' +
                ", type=" + type +
                ", version='" + version + '\'' +
                '}';
    }
}
