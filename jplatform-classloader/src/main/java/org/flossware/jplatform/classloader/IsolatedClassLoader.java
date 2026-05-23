package org.flossware.jplatform.classloader;

import org.flossware.jclassloader.AuthConfig;
import org.flossware.jclassloader.JClassLoader;
import org.flossware.jclassloader.lifecycle.ResourceTrackingListener;
import org.flossware.jplatform.api.ApplicationDescriptor;

import java.io.File;
import java.net.URI;
import java.util.Map;

/**
 * Platform-specific class loader for isolated application execution.
 * Wraps JClassLoader with JPlatform-specific integration and configuration.
 */
public class IsolatedClassLoader extends ClassLoader implements AutoCloseable {

    private final String applicationId;
    private final ApplicationDescriptor descriptor;
    private final ResourceTrackingListener resourceTracker;
    private final JClassLoader delegate;

    private IsolatedClassLoader(String applicationId,
                                ApplicationDescriptor descriptor,
                                ResourceTrackingListener resourceTracker,
                                JClassLoader delegate) {
        super(delegate.getParent());
        this.applicationId = applicationId;
        this.descriptor = descriptor;
        this.resourceTracker = resourceTracker;
        this.delegate = delegate;
    }

    /**
     * Factory method to create an isolated class loader for an application.
     * This is the platform-specific part that knows about ApplicationDescriptor.
     *
     * @param applicationId Application identifier
     * @param descriptor Application deployment descriptor
     * @param platformSharedLoader ClassLoader for shared platform APIs
     * @return Isolated class loader for the application
     */
    public static IsolatedClassLoader create(String applicationId,
                                             ApplicationDescriptor descriptor,
                                             ClassLoader platformSharedLoader) {

        ResourceTrackingListener tracker = new ResourceTrackingListener();

        // Build JClassLoader with platform-specific configuration
        JClassLoader.Builder builder = JClassLoader.builder()
                .parent(platformSharedLoader)
                // Platform-specific: parent-last with JPlatform API exception
                .parentLast(
                        "org.flossware.jplatform.api.",  // Platform API
                        "java.", "javax.", "sun.", "jdk."  // System classes
                )
                .addListener(tracker)
                .addListener(new PlatformClassLoadListener(applicationId))
                .useCache(true);

        // Platform-specific: Add cache if configured
        String cacheDir = getCacheDir(applicationId);
        if (cacheDir != null) {
            File cacheDirFile = new File(cacheDir);
            cacheDirFile.mkdirs();
            try {
                builder.cache(new org.flossware.jclassloader.cache.FileSystemCache(cacheDir));
            } catch (java.io.IOException e) {
                // Continue without cache if it fails
                System.err.println("Failed to initialize cache for " + applicationId + ": " + e.getMessage());
            }
        }

        // Platform-specific: Convert ApplicationDescriptor to class sources
        addClassSourcesFromDescriptor(builder, descriptor);

        JClassLoader jcl = builder.build();

        return new IsolatedClassLoader(applicationId, descriptor, tracker, jcl);
    }

    /**
     * Loads a class by delegating to the JClassLoader.
     *
     * @param name the name of the class to load
     * @param resolve whether to resolve the class
     * @return the loaded class
     * @throws ClassNotFoundException if the class cannot be found
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = delegate.loadClass(name);
        if (resolve) {
            resolveClass(clazz);
        }
        return clazz;
    }

    /**
     * Finds a class by delegating to the JClassLoader.
     *
     * @param name the name of the class to find
     * @return the loaded class
     * @throws ClassNotFoundException if the class cannot be found
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // Delegate uses its own findClass
        return delegate.loadClass(name);
    }

    /**
     * Platform-specific: Translate ApplicationDescriptor to JClassLoader sources.
     */
    private static void addClassSourcesFromDescriptor(
            JClassLoader.Builder builder,
            ApplicationDescriptor descriptor) {

        for (URI classpathEntry : descriptor.getClasspathEntries()) {
            String scheme = classpathEntry.getScheme();

            if (scheme == null) {
                // Assume file path
                builder.addLocalSource(classpathEntry.getPath());
                continue;
            }

            switch (scheme) {
                case "file":
                    builder.addLocalSource(new File(classpathEntry).getAbsolutePath());
                    break;

                case "http":
                case "https":
                    AuthConfig auth = getAuthFromDescriptor(descriptor, classpathEntry);
                    if (auth != null) {
                        builder.addRemoteSource(classpathEntry.toString(), auth);
                    } else {
                        builder.addRemoteSource(classpathEntry.toString());
                    }
                    break;

                case "maven":
                    // Parse: maven:groupId:artifactId:version
                    String coords = classpathEntry.getSchemeSpecificPart();
                    builder.addMavenCentral(coords);
                    break;

                default:
                    throw new IllegalArgumentException(
                            "Unsupported classpath scheme: " + scheme +
                            " in " + classpathEntry);
            }
        }
    }

    /**
     * Platform-specific: Get cache directory for this application.
     */
    private static String getCacheDir(String applicationId) {
        String baseDir = System.getProperty("jplatform.cache.dir", "/var/jplatform/cache");
        return baseDir + "/" + applicationId;
    }

    /**
     * Platform-specific: Extract authentication from descriptor properties.
     */
    private static AuthConfig getAuthFromDescriptor(
            ApplicationDescriptor descriptor, URI uri) {
        Map<String, String> props = descriptor.getProperties();
        String host = uri.getHost();
        if (host == null) {
            return null;
        }

        String authType = props.get("classpath." + host + ".auth.type");

        if ("basic".equals(authType)) {
            String username = props.get("classpath." + host + ".auth.username");
            String password = props.get("classpath." + host + ".auth.password");
            if (username != null && password != null) {
                return AuthConfig.basic(username, password);
            }
        } else if ("bearer".equals(authType)) {
            String token = props.get("classpath." + host + ".auth.token");
            if (token != null) {
                return AuthConfig.bearer(token);
            }
        }

        return null;
    }

    /**
     * Returns statistics for this application's class loading activity.
     *
     * @return class loading statistics
     */
    public ClassLoaderStatistics getStatistics() {
        return new ClassLoaderStatistics(
                applicationId,
                (int) resourceTracker.getTotalClassesLoaded(),
                resourceTracker.getTotalBytesLoaded(),
                resourceTracker.getCacheHits()
        );
    }

    /**
     * Closes the classloader and releases all tracked resources.
     * Called when the application is undeployed.
     */
    @Override
    public void close() {
        // Close all tracked resources
        resourceTracker.closeAllResources();

        // Optionally suggest GC to reclaim class memory (disabled by default)
        // Enable with -Djplatform.debug.forceGcOnClose=true
        if (Boolean.getBoolean("jplatform.debug.forceGcOnClose")) {
            System.gc();
        }
    }

    /**
     * Returns the application identifier for this classloader.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationId;
    }

    /**
     * Returns the application descriptor.
     *
     * @return the application descriptor
     */
    public ApplicationDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * Returns the resource tracker for monitoring class loading.
     *
     * @return the resource tracking listener
     */
    public ResourceTrackingListener getResourceTracker() {
        return resourceTracker;
    }
}
