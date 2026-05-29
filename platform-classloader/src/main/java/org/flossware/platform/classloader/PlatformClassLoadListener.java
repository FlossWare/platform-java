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

package org.flossware.platform.classloader;

import org.flossware.classloader.lifecycle.ClassLoadEvent;
import org.flossware.classloader.lifecycle.ClassLoaderLifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform-specific listener that integrates class loading events with platform-java logging and
 * monitoring.
 */
public class PlatformClassLoadListener implements ClassLoaderLifecycleListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(PlatformClassLoadListener.class);

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
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "[{}] Loaded class {} from {} in {}ms ({}B)",
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
    LOGGER.warn("[{}] Failed to load class {}: {}", applicationId, className, error.getMessage());
  }

  @Override
  public void onClassCacheHit(String className) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("[{}] Cache hit for class {}", applicationId, className);
    }
  }
}
