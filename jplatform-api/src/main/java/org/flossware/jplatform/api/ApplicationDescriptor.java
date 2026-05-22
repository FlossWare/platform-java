package org.flossware.jplatform.api;

import java.net.URI;
import java.util.*;

/**
 * Descriptor containing all metadata and configuration for deploying an application.
 *
 * <p>Includes:</p>
 * <ul>
 *   <li>Application identity (ID, name, version)</li>
 *   <li>Entry point (main class)</li>
 *   <li>Classpath entries (JARs, directories)</li>
 *   <li>Resource limits (thread pool, memory, CPU)</li>
 *   <li>Security configuration (permissions)</li>
 *   <li>Optional features (messaging)</li>
 * </ul>
 *
 * <p>Use the builder to construct:</p>
 * <pre>{@code
 * ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
 *     .applicationId("my-app")
 *     .mainClass("com.example.MyApp")
 *     .addClasspathEntry(new File("app.jar").toURI())
 *     .enableMessaging(true)
 *     .build();
 * }</pre>
 */
public class ApplicationDescriptor {
    private final String applicationId;
    private final String name;
    private final String version;
    private final String mainClass;
    private final List<URI> classpathEntries;
    private final ThreadPoolConfig threadPoolConfig;
    private final SecurityConfig securityConfig;
    private final ResourceConfig resourceConfig;
    private final Map<String, String> properties;
    private final boolean enableMessaging;
    private final List<VolumeMount> volumes;  // Added in 2.0
    private final List<ApplicationDependency> dependencies;  // Added in 2.0
    private final List<NativeLibrary> nativeLibraries;  // Added in 2.0
    private final boolean nativeImage;  // Added in 2.0

    private ApplicationDescriptor(Builder builder) {
        this.applicationId = Objects.requireNonNull(builder.applicationId, "applicationId is required");
        this.name = builder.name != null ? builder.name : applicationId;
        this.version = builder.version != null ? builder.version : "1.0.0";
        this.mainClass = Objects.requireNonNull(builder.mainClass, "mainClass is required");
        this.classpathEntries = builder.classpathEntries != null ?
                List.copyOf(builder.classpathEntries) : Collections.emptyList();
        this.threadPoolConfig = builder.threadPoolConfig != null ?
                builder.threadPoolConfig : ThreadPoolConfig.defaultConfig();
        this.securityConfig = builder.securityConfig != null ?
                builder.securityConfig : SecurityConfig.permissive();
        this.resourceConfig = builder.resourceConfig != null ?
                builder.resourceConfig : ResourceConfig.unlimited();
        this.properties = builder.properties != null ?
                new HashMap<>(builder.properties) : Collections.emptyMap();
        this.enableMessaging = builder.enableMessaging;
        this.volumes = builder.volumes != null ?
                List.copyOf(builder.volumes) : Collections.emptyList();
        this.dependencies = builder.dependencies != null ?
                List.copyOf(builder.dependencies) : Collections.emptyList();
        this.nativeLibraries = builder.nativeLibraries != null ?
                List.copyOf(builder.nativeLibraries) : Collections.emptyList();
        this.nativeImage = builder.nativeImage;
    }

    /**
     * Returns the application identifier.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationId;
    }

    /**
     * Returns the application display name.
     *
     * @return the application name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the application version.
     *
     * @return the version string
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the main class name.
     *
     * @return the fully qualified main class name
     */
    public String getMainClass() {
        return mainClass;
    }

    /**
     * Returns the classpath entries for this application.
     *
     * @return an unmodifiable list of classpath URIs
     */
    public List<URI> getClasspathEntries() {
        return classpathEntries;
    }

    /**
     * Returns the thread pool configuration.
     *
     * @return the thread pool configuration
     */
    public ThreadPoolConfig getThreadPoolConfig() {
        return threadPoolConfig;
    }

    /**
     * Returns the security configuration.
     *
     * @return the security configuration
     */
    public SecurityConfig getSecurityConfig() {
        return securityConfig;
    }

    /**
     * Returns the resource limits configuration.
     *
     * @return the resource configuration
     */
    public ResourceConfig getResourceConfig() {
        return resourceConfig;
    }

    /**
     * Returns the application properties.
     *
     * @return an unmodifiable map of properties
     */
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    /**
     * Checks if messaging is enabled for this application.
     *
     * @return true if messaging is enabled, false otherwise
     */
    public boolean isEnableMessaging() {
        return enableMessaging;
    }

    /**
     * Returns the volume mounts defined for this application.
     *
     * @return immutable list of volume mounts
     * @since 2.0
     */
    public List<VolumeMount> getVolumes() {
        return volumes;
    }

    /**
     * Returns the dependencies declared for this application.
     *
     * @return immutable list of application dependencies
     * @since 2.0
     */
    public List<ApplicationDependency> getDependencies() {
        return dependencies;
    }

    /**
     * Returns the native libraries defined for this application.
     *
     * @return immutable list of native libraries
     * @since 2.0
     */
    public List<NativeLibrary> getNativeLibraries() {
        return nativeLibraries;
    }

    /**
     * Checks if this is a GraalVM native image application.
     *
     * @return true if native image, false if standard JVM application
     * @since 2.0
     */
    public boolean isNativeImage() {
        return nativeImage;
    }

    /**
     * Creates a new builder for constructing application descriptors.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing ApplicationDescriptor instances.
     * Required fields: applicationId, mainClass.
     * All other fields have sensible defaults.
     */
    public static class Builder {
        private String applicationId;
        private String name;
        private String version;
        private String mainClass;
        private List<URI> classpathEntries;
        private ThreadPoolConfig threadPoolConfig;
        private SecurityConfig securityConfig;
        private ResourceConfig resourceConfig;
        private Map<String, String> properties;
        private boolean enableMessaging;
        private List<VolumeMount> volumes;  // Added in 2.0
        private List<ApplicationDependency> dependencies;  // Added in 2.0
        private List<NativeLibrary> nativeLibraries;  // Added in 2.0
        private boolean nativeImage;  // Added in 2.0

        /**
         * Sets the application identifier (required).
         *
         * @param applicationId the unique application identifier
         * @return this builder
         */
        public Builder applicationId(String applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        /**
         * Sets the application display name.
         * Defaults to applicationId if not set.
         *
         * @param name the application name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the application version.
         * Defaults to "1.0.0" if not set.
         *
         * @param version the application version
         * @return this builder
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the main class name (required).
         * Must implement the Application interface.
         *
         * @param mainClass the fully qualified main class name
         * @return this builder
         */
        public Builder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        /**
         * Sets the complete list of classpath entries.
         * Replaces any previously added entries.
         *
         * @param classpathEntries the list of classpath URIs
         * @return this builder
         */
        public Builder classpathEntries(List<URI> classpathEntries) {
            this.classpathEntries = classpathEntries;
            return this;
        }

        /**
         * Adds a single classpath entry.
         * Can be called multiple times to build the classpath incrementally.
         *
         * @param entry the classpath URI to add
         * @return this builder
         */
        public Builder addClasspathEntry(URI entry) {
            if (this.classpathEntries == null) {
                this.classpathEntries = new ArrayList<>();
            }
            this.classpathEntries.add(entry);
            return this;
        }

        /**
         * Sets the thread pool configuration.
         * Defaults to ThreadPoolConfig.defaultConfig() if not set.
         *
         * @param threadPoolConfig the thread pool configuration
         * @return this builder
         */
        public Builder threadPoolConfig(ThreadPoolConfig threadPoolConfig) {
            this.threadPoolConfig = threadPoolConfig;
            return this;
        }

        /**
         * Sets the security configuration.
         * Defaults to SecurityConfig.permissive() if not set.
         *
         * @param securityConfig the security configuration
         * @return this builder
         */
        public Builder securityConfig(SecurityConfig securityConfig) {
            this.securityConfig = securityConfig;
            return this;
        }

        /**
         * Sets the resource limits configuration.
         * Defaults to ResourceConfig.unlimited() if not set.
         *
         * @param resourceConfig the resource configuration
         * @return this builder
         */
        public Builder resourceConfig(ResourceConfig resourceConfig) {
            this.resourceConfig = resourceConfig;
            return this;
        }

        /**
         * Sets the complete map of application properties.
         * Replaces any previously added properties.
         *
         * @param properties the properties map
         * @return this builder
         */
        public Builder properties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        /**
         * Adds a single application property.
         * Can be called multiple times to build properties incrementally.
         *
         * @param key the property key
         * @param value the property value
         * @return this builder
         */
        public Builder property(String key, String value) {
            if (this.properties == null) {
                this.properties = new HashMap<>();
            }
            this.properties.put(key, value);
            return this;
        }

        /**
         * Sets whether messaging should be enabled for this application.
         * Defaults to false if not set.
         *
         * @param enableMessaging true to enable messaging, false to disable
         * @return this builder
         */
        public Builder enableMessaging(boolean enableMessaging) {
            this.enableMessaging = enableMessaging;
            return this;
        }

        /**
         * Adds a volume mount to this application.
         * Volumes provide persistent or ephemeral storage accessible to the application.
         *
         * @param volume the volume mount configuration
         * @return this builder
         * @since 2.0
         */
        public Builder addVolume(VolumeMount volume) {
            if (this.volumes == null) {
                this.volumes = new ArrayList<>();
            }
            this.volumes.add(volume);
            return this;
        }

        /**
         * Adds a dependency on a service provided by another application.
         * Dependencies are validated at deploy time and determine startup order.
         *
         * @param dependency the application dependency
         * @return this builder
         * @since 2.0
         */
        public Builder addDependency(ApplicationDependency dependency) {
            if (this.dependencies == null) {
                this.dependencies = new ArrayList<>();
            }
            this.dependencies.add(dependency);
            return this;
        }

        /**
         * Adds a native library to load for this application.
         * The platform will load libraries matching the current OS and architecture.
         *
         * @param library the native library descriptor
         * @return this builder
         * @since 2.0
         */
        public Builder addNativeLibrary(NativeLibrary library) {
            if (this.nativeLibraries == null) {
                this.nativeLibraries = new ArrayList<>();
            }
            this.nativeLibraries.add(library);
            return this;
        }

        /**
         * Sets whether this application is a GraalVM native image.
         * Native image applications are launched as separate processes, not JVM applications.
         *
         * @param nativeImage true if this is a native image, false for standard JVM application
         * @return this builder
         * @since 2.0
         */
        public Builder nativeImage(boolean nativeImage) {
            this.nativeImage = nativeImage;
            return this;
        }

        /**
         * Builds the ApplicationDescriptor instance.
         * Validates that required fields (applicationId, mainClass) are set.
         *
         * @return a new ApplicationDescriptor with the configured values
         * @throws NullPointerException if applicationId or mainClass is not set
         */
        public ApplicationDescriptor build() {
            return new ApplicationDescriptor(this);
        }
    }
}
