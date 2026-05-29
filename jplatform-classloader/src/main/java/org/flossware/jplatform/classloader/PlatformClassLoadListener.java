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

package org.flossware.jplatform.classloader;

import org.flossware.jclassloader.lifecycle.ClassLoadEvent;
import org.flossware.jclassloader.lifecycle.ClassLoaderLifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform-specific listener that integrates class loading events with platform-java logging and monitoring.
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
