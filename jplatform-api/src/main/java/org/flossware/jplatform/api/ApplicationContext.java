package org.flossware.jplatform.api;

import java.util.Map;
import java.util.Optional;

/**
 * Runtime context for an application running on the platform.
 * Provides access to isolated resources and optional platform features.
 *
 * <p>Each application receives its own context with isolated:</p>
 * <ul>
 *   <li>ClassLoader for class isolation</li>
 *   <li>Thread pool for concurrent execution</li>
 *   <li>Security policy for permission control</li>
 *   <li>Resource monitoring for CPU/memory tracking</li>
 * </ul>
 *
 * <p>Optional features (if enabled):</p>
 * <ul>
 *   <li>Message bus for inter-application communication</li>
 *   <li>Service registry for service discovery</li>
 * </ul>
 *
 * @see Application
 */
public interface ApplicationContext {
    /**
     * Returns the unique identifier for this application.
     *
     * @return the application ID
     */
    String getApplicationId();

    /**
     * Returns the current lifecycle state of this application.
     *
     * @return the application state
     * @see ApplicationState
     */
    ApplicationState getState();

    /**
     * Returns the isolated classloader for this application.
     * All application classes are loaded through this classloader for isolation.
     *
     * @return the application's classloader
     */
    ClassLoader getClassLoader();

    /**
     * Returns the dedicated thread pool for this application.
     * Use this for concurrent execution to maintain resource isolation.
     *
     * @return the application's thread pool
     */
    ThreadPoolExecutor getThreadPool();

    /**
     * Returns the security policy for this application.
     * Use this to check permissions before performing privileged operations.
     *
     * @return the application's security policy
     */
    SecurityPolicy getSecurityPolicy();

    /**
     * Returns the resource monitor tracking this application's resource usage.
     * Provides CPU time, memory usage, and thread count metrics.
     *
     * @return the application's resource monitor
     */
    ResourceMonitor getResourceMonitor();

    /**
     * Returns the message bus if messaging is enabled for this application.
     *
     * @return optional message bus, empty if messaging is not enabled
     */
    Optional<MessageBus> getMessageBus();

    /**
     * Returns the service registry if messaging is enabled for this application.
     *
     * @return optional service registry, empty if messaging is not enabled
     */
    Optional<ServiceRegistry> getServiceRegistry();

    /**
     * Returns custom properties configured for this application.
     *
     * @return immutable map of application properties
     */
    Map<String, String> getProperties();

    /**
     * Returns the application instance if it has been started.
     *
     * @return the application instance, or null if not yet started
     */
    Object getApplicationInstance();
}
