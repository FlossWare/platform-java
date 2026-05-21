package org.flossware.jplatform.classloader;

import org.flossware.jclassloader.lifecycle.ClassLoadEvent;
import org.flossware.jclassloader.lifecycle.ClassLoaderLifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform-specific listener that integrates class loading events with JPlatform logging and monitoring.
 */
public class PlatformClassLoadListener implements ClassLoaderLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(PlatformClassLoadListener.class);

    private final String applicationId;

    /**
     * Creates a new platform class load listener for the specified application.
     *
     * @param applicationId the application identifier
     */
    public PlatformClassLoadListener(String applicationId) {
        this.applicationId = applicationId;
    }

    @Override
    public void onClassLoaded(ClassLoadEvent event) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}] Loaded class {} from {} in {}ms ({}B)",
                    applicationId,
                    event.getClassName(),
                    event.getSource().getDescription(),
                    event.getLoadTimeMillis(),
                    event.getClassSizeBytes());
        }

        // Could integrate with ResourceMonitor here to track memory
        // PlatformMetrics.recordClassLoad(applicationId, event);
    }

    @Override
    public void onClassLoadFailed(String className, Throwable error) {
        logger.warn("[{}] Failed to load class {}: {}",
                applicationId, className, error.getMessage());
    }

    @Override
    public void onClassCacheHit(String className) {
        if (logger.isTraceEnabled()) {
            logger.trace("[{}] Cache hit for class {}", applicationId, className);
        }
    }
}
