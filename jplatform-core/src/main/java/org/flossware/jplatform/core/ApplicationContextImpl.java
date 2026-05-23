package org.flossware.jplatform.core;

import org.flossware.jplatform.api.*;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of ApplicationContext.
 * Provides isolated runtime environment for an application.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe. Uses volatile fields for swappable state
 * (descriptor, state, classLoader, applicationInstance). The setClassLoaderAndDescriptor()
 * method is synchronized for atomic updates during hot reload.
 */
public class ApplicationContextImpl implements ApplicationContext {

    private final String applicationId;
    private volatile ApplicationDescriptor descriptor;  // Swappable during hot reload
    private volatile ApplicationState state;
    private volatile ClassLoader classLoader;  // Swappable during hot reload
    private final ThreadPoolExecutor threadPool;
    private final SecurityPolicy securityPolicy;
    private final ResourceMonitor resourceMonitor;
    private final MessageBus messageBus;
    private final ServiceRegistry serviceRegistry;
    private final VolumeManager volumeManager;  // Added in 2.0
    private final Map<String, String> properties;
    private volatile Object applicationInstance;

    private ApplicationContextImpl(Builder builder) {
        this.applicationId = builder.applicationId;
        this.descriptor = builder.descriptor;
        this.state = ApplicationState.DEPLOYED;
        this.classLoader = builder.classLoader;
        this.threadPool = builder.threadPool;
        this.securityPolicy = builder.securityPolicy;
        this.resourceMonitor = builder.resourceMonitor;
        this.messageBus = builder.messageBus;
        this.serviceRegistry = builder.serviceRegistry;
        this.volumeManager = builder.volumeManager;
        this.properties = builder.properties != null ?
                Collections.unmodifiableMap(builder.properties) : Collections.emptyMap();
    }

    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @Override
    public ApplicationState getState() {
        return state;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public ThreadPoolExecutor getThreadPool() {
        return threadPool;
    }

    @Override
    public SecurityPolicy getSecurityPolicy() {
        return securityPolicy;
    }

    @Override
    public ResourceMonitor getResourceMonitor() {
        return resourceMonitor;
    }

    @Override
    public Optional<MessageBus> getMessageBus() {
        return Optional.ofNullable(messageBus);
    }

    @Override
    public Optional<ServiceRegistry> getServiceRegistry() {
        return Optional.ofNullable(serviceRegistry);
    }

    @Override
    public Optional<VolumeManager> getVolumeManager() {
        return Optional.ofNullable(volumeManager);
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public Object getApplicationInstance() {
        return applicationInstance;
    }

    /**
     * Updates the application state.
     * Package-private for use by ApplicationManager.
     *
     * @param state the new application state
     */
    void setState(ApplicationState state) {
        this.state = state;
    }

    /**
     * Sets the application instance after instantiation.
     * Package-private for use by ApplicationManager.
     *
     * @param instance the application instance
     */
    void setApplicationInstance(Object instance) {
        this.applicationInstance = instance;
    }

    /**
     * Returns the application descriptor.
     * Package-private for use by ApplicationManager.
     *
     * @return the application descriptor
     */
    ApplicationDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * Sets a new classloader.
     * Package-private for use by ApplicationReloader during hot reload.
     *
     * @param classLoader the new classloader
     */
    void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Sets a new descriptor.
     * Package-private for use by ApplicationReloader during hot reload.
     *
     * @param descriptor the new descriptor
     */
    void setDescriptor(ApplicationDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Atomically updates both classloader and descriptor.
     * Package-private for use by ApplicationReloader during hot reload to avoid race conditions.
     *
     * @param classLoader the new classloader
     * @param descriptor the new descriptor
     */
    synchronized void setClassLoaderAndDescriptor(ClassLoader classLoader, ApplicationDescriptor descriptor) {
        this.classLoader = classLoader;
        this.descriptor = descriptor;
    }

    /**
     * Creates a new builder for constructing application contexts.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing ApplicationContextImpl instances.
     * Used internally by ApplicationManager.
     */
    public static class Builder {
        private String applicationId;
        private ApplicationDescriptor descriptor;
        private ClassLoader classLoader;
        private ThreadPoolExecutor threadPool;
        private SecurityPolicy securityPolicy;
        private ResourceMonitor resourceMonitor;
        private MessageBus messageBus;
        private ServiceRegistry serviceRegistry;
        private VolumeManager volumeManager;
        private Map<String, String> properties;

        /**
         * Sets the application identifier.
         *
         * @param applicationId the application ID
         * @return this builder
         */
        public Builder applicationId(String applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        /**
         * Sets the application descriptor.
         *
         * @param descriptor the application descriptor
         * @return this builder
         */
        public Builder descriptor(ApplicationDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        /**
         * Sets the isolated classloader.
         *
         * @param classLoader the application's classloader
         * @return this builder
         */
        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        /**
         * Sets the isolated thread pool.
         *
         * @param threadPool the application's thread pool
         * @return this builder
         */
        public Builder threadPool(ThreadPoolExecutor threadPool) {
            this.threadPool = threadPool;
            return this;
        }

        /**
         * Sets the security policy.
         *
         * @param securityPolicy the application's security policy
         * @return this builder
         */
        public Builder securityPolicy(SecurityPolicy securityPolicy) {
            this.securityPolicy = securityPolicy;
            return this;
        }

        /**
         * Sets the resource monitor.
         *
         * @param resourceMonitor the application's resource monitor
         * @return this builder
         */
        public Builder resourceMonitor(ResourceMonitor resourceMonitor) {
            this.resourceMonitor = resourceMonitor;
            return this;
        }

        /**
         * Sets the message bus (optional).
         *
         * @param messageBus the shared message bus, or null if messaging is disabled
         * @return this builder
         */
        public Builder messageBus(MessageBus messageBus) {
            this.messageBus = messageBus;
            return this;
        }

        /**
         * Sets the service registry (optional).
         *
         * @param serviceRegistry the shared service registry, or null if disabled
         * @return this builder
         */
        public Builder serviceRegistry(ServiceRegistry serviceRegistry) {
            this.serviceRegistry = serviceRegistry;
            return this;
        }

        /**
         * Sets the volume manager (optional).
         *
         * @param volumeManager the volume manager for persistent storage, or null if no volumes
         * @return this builder
         * @since 2.0
         */
        public Builder volumeManager(VolumeManager volumeManager) {
            this.volumeManager = volumeManager;
            return this;
        }

        /**
         * Sets the application properties.
         *
         * @param properties the application configuration properties
         * @return this builder
         */
        public Builder properties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        /**
         * Builds the ApplicationContextImpl instance.
         *
         * @return a new ApplicationContextImpl with the configured values
         */
        public ApplicationContextImpl build() {
            return new ApplicationContextImpl(this);
        }
    }
}
