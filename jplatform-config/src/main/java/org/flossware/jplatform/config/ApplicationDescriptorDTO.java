package org.flossware.jplatform.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.flossware.jplatform.api.*;

import java.io.FilePermission;
import java.net.SocketPermission;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data Transfer Object for Jackson deserialization of ApplicationDescriptor.
 * Handles conversion from external configuration formats (YAML/JSON) to domain objects.
 *
 * <p>This DTO uses Jackson annotations to deserialize external configuration and provides
 * conversion logic to create immutable ApplicationDescriptor instances using builders.</p>
 *
 * <p>Example YAML configuration:</p>
 * <pre>{@code
 * applicationId: "my-app"
 * name: "My Application"
 * version: "1.0.0"
 * mainClass: "com.example.MyApp"
 * classpathEntries:
 *   - "file:///app/lib/app.jar"
 *   - "file:///app/lib/deps.jar"
 * threadPoolConfig:
 *   corePoolSize: 5
 *   maxPoolSize: 20
 * properties:
 *   env: "production"
 *   debug: "false"
 * enableMessaging: true
 * }</pre>
 *
 * @see ApplicationDescriptor
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationDescriptorDTO {

    @JsonProperty("applicationId")
    private String applicationId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("version")
    private String version;

    @JsonProperty("mainClass")
    private String mainClass;

    @JsonProperty("classpathEntries")
    private List<String> classpathEntries;

    @JsonProperty("threadPoolConfig")
    private ThreadPoolConfigDTO threadPoolConfig;

    @JsonProperty("securityConfig")
    private SecurityConfigDTO securityConfig;

    @JsonProperty("resourceConfig")
    private ResourceConfigDTO resourceConfig;

    @JsonProperty("properties")
    private Map<String, String> properties;

    @JsonProperty("enableMessaging")
    private Boolean enableMessaging;

    /**
     * Returns the application ID.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationId;
    }

    /**
     * Returns the main class name.
     *
     * @return the main class name
     */
    public String getMainClass() {
        return mainClass;
    }

    /**
     * Converts this DTO to an ApplicationDescriptor domain object.
     *
     * @return the ApplicationDescriptor instance
     * @throws ParseException if conversion fails (e.g., invalid URI syntax or missing required fields)
     */
    public ApplicationDescriptor toApplicationDescriptor() throws ParseException {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            throw new ParseException("applicationId field is required");
        }
        if (mainClass == null || mainClass.trim().isEmpty()) {
            throw new ParseException("mainClass field is required");
        }

        ApplicationDescriptor.Builder builder = ApplicationDescriptor.builder()
                .applicationId(applicationId)
                .mainClass(mainClass);

        if (name != null) {
            builder.name(name);
        }

        if (version != null) {
            builder.version(version);
        }

        if (classpathEntries != null) {
            for (String entry : classpathEntries) {
                try {
                    builder.addClasspathEntry(new URI(entry));
                } catch (URISyntaxException e) {
                    throw new ParseException("Invalid URI in classpathEntries: " + entry, e);
                }
            }
        }

        if (threadPoolConfig != null) {
            builder.threadPoolConfig(threadPoolConfig.toThreadPoolConfig());
        }

        if (securityConfig != null) {
            builder.securityConfig(securityConfig.toSecurityConfig());
        }

        if (resourceConfig != null) {
            builder.resourceConfig(resourceConfig.toResourceConfig());
        }

        if (properties != null) {
            builder.properties(properties);
        }

        if (enableMessaging != null) {
            builder.enableMessaging(enableMessaging);
        }

        return builder.build();
    }

    /**
     * Data Transfer Object for ThreadPoolConfig.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ThreadPoolConfigDTO {

        @JsonProperty("corePoolSize")
        private Integer corePoolSize;

        @JsonProperty("maxPoolSize")
        private Integer maxPoolSize;

        @JsonProperty("keepAliveTimeSeconds")
        private Long keepAliveTimeSeconds;

        @JsonProperty("queueCapacity")
        private Integer queueCapacity;

        /**
         * Converts this DTO to a ThreadPoolConfig domain object.
         *
         * @return the ThreadPoolConfig instance
         */
        public ThreadPoolConfig toThreadPoolConfig() {
            ThreadPoolConfig.Builder builder = ThreadPoolConfig.builder();

            if (corePoolSize != null) {
                builder.corePoolSize(corePoolSize);
            }

            if (maxPoolSize != null) {
                builder.maxPoolSize(maxPoolSize);
            }

            if (keepAliveTimeSeconds != null) {
                builder.keepAliveTimeSeconds(keepAliveTimeSeconds);
            }

            if (queueCapacity != null) {
                builder.queueCapacity(queueCapacity);
            }

            return builder.build();
        }
    }

    /**
     * Data Transfer Object for SecurityConfig.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SecurityConfigDTO {

        @JsonProperty("filePermissions")
        private List<FilePermissionDTO> filePermissions;

        @JsonProperty("socketPermissions")
        private List<SocketPermissionDTO> socketPermissions;

        @JsonProperty("runtimePermissions")
        private List<String> runtimePermissions;

        @JsonProperty("allowReflection")
        private Boolean allowReflection;

        @JsonProperty("allowNativeCode")
        private Boolean allowNativeCode;

        /**
         * Converts this DTO to a SecurityConfig domain object.
         *
         * @return the SecurityConfig instance
         */
        public SecurityConfig toSecurityConfig() {
            SecurityConfig.Builder builder = SecurityConfig.builder();

            if (filePermissions != null) {
                for (FilePermissionDTO fp : filePermissions) {
                    if (fp == null) {
                        throw new IllegalArgumentException("File permission entry cannot be null");
                    }
                    if (fp.path == null || fp.path.trim().isEmpty()) {
                        throw new IllegalArgumentException("File permission path cannot be null or empty");
                    }
                    if (fp.actions == null || fp.actions.trim().isEmpty()) {
                        throw new IllegalArgumentException("File permission actions cannot be null or empty");
                    }
                    builder.addFilePermission(new FilePermission(fp.path, fp.actions));
                }
            }

            if (socketPermissions != null) {
                for (SocketPermissionDTO sp : socketPermissions) {
                    if (sp == null) {
                        throw new IllegalArgumentException("Socket permission entry cannot be null");
                    }
                    if (sp.host == null || sp.host.trim().isEmpty()) {
                        throw new IllegalArgumentException("Socket permission host cannot be null or empty");
                    }
                    if (sp.actions == null || sp.actions.trim().isEmpty()) {
                        throw new IllegalArgumentException("Socket permission actions cannot be null or empty");
                    }
                    builder.addSocketPermission(new SocketPermission(sp.host, sp.actions));
                }
            }

            if (runtimePermissions != null) {
                for (String rp : runtimePermissions) {
                    if (rp == null || rp.trim().isEmpty()) {
                        throw new IllegalArgumentException("Runtime permission name cannot be null or empty");
                    }
                    builder.addRuntimePermission(new RuntimePermission(rp));
                }
            }

            if (allowReflection != null) {
                builder.allowReflection(allowReflection);
            }

            if (allowNativeCode != null) {
                builder.allowNativeCode(allowNativeCode);
            }

            return builder.build();
        }
    }

    /**
     * Data Transfer Object for FilePermission.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FilePermissionDTO {

        @JsonProperty("path")
        private String path;

        @JsonProperty("actions")
        private String actions;
    }

    /**
     * Data Transfer Object for SocketPermission.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SocketPermissionDTO {

        @JsonProperty("host")
        private String host;

        @JsonProperty("actions")
        private String actions;
    }

    /**
     * Data Transfer Object for ResourceConfig.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceConfigDTO {

        @JsonProperty("maxHeapMB")
        private Long maxHeapMB;

        @JsonProperty("maxThreads")
        private Integer maxThreads;

        @JsonProperty("maxCpuTimeSeconds")
        private Long maxCpuTimeSeconds;

        /**
         * Converts this DTO to a ResourceConfig domain object.
         *
         * @return the ResourceConfig instance
         */
        public ResourceConfig toResourceConfig() {
            ResourceConfig.Builder builder = ResourceConfig.builder();

            if (maxHeapMB != null) {
                builder.maxHeapMB(maxHeapMB);
            }

            if (maxThreads != null) {
                builder.maxThreads(maxThreads);
            }

            if (maxCpuTimeSeconds != null) {
                builder.maxCpuTimeSeconds(maxCpuTimeSeconds);
            }

            return builder.build();
        }
    }
}
