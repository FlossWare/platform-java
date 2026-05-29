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

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

import org.flossware.classloader.ApplicationClassLoader;
import org.flossware.classloader.AuthConfig;
import org.flossware.classloader.lifecycle.ResourceTrackingListener;
import org.flossware.platform.api.ApplicationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform-specific class loader for isolated application execution. Wraps ApplicationClassLoader
 * with JPlatform-specific integration and configuration.
 */
public final class IsolatedClassLoader extends ClassLoader implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(IsolatedClassLoader.class);

  private final String applicationId;
  private final ApplicationDescriptor descriptor;
  private final ResourceTrackingListener resourceTracker;
  private final ApplicationClassLoader delegate;

  private IsolatedClassLoader(
      String applicationId,
      ApplicationDescriptor descriptor,
      ResourceTrackingListener resourceTracker,
      ApplicationClassLoader delegate) {
    super(delegate.getParent());
    this.applicationId = applicationId;
    this.descriptor = descriptor;
    this.resourceTracker = resourceTracker;
    this.delegate = delegate;
  }

  /**
   * Factory method to create an isolated class loader for an application. This is the
   * platform-specific part that knows about ApplicationDescriptor.
   *
   * @param applicationId Application identifier
   * @param descriptor Application deployment descriptor
   * @param platformSharedLoader ClassLoader for shared platform APIs
   * @return Isolated class loader for the application
   */
  public static IsolatedClassLoader create(
      String applicationId, ApplicationDescriptor descriptor, ClassLoader platformSharedLoader) {
    Objects.requireNonNull(applicationId, "applicationId cannot be null");
    Objects.requireNonNull(descriptor, "descriptor cannot be null");
    Objects.requireNonNull(platformSharedLoader, "platformSharedLoader cannot be null");

    ResourceTrackingListener tracker = new ResourceTrackingListener();

    // Build ApplicationClassLoader with platform-specific configuration
    ApplicationClassLoader.Builder builder =
        ApplicationClassLoader.builder()
            .parent(platformSharedLoader)
            // Platform-specific: parent-last with platform-java API exception
            .parentLast(
                "org.flossware.platform.api.", // Platform API
                "java.",
                "javax.",
                "sun.",
                "jdk." // System classes
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
        builder.cache(new org.flossware.classloader.cache.FileSystemCache(cacheDir));
        LOGGER.info("[{}] Initialized class cache at: {}", applicationId, cacheDir);
      } catch (java.io.IOException e) {
        LOGGER.warn(
            "[{}] Failed to initialize class cache, continuing without cache", applicationId, e);
        // Continue without cache
      }
    }

    // Platform-specific: Convert ApplicationDescriptor to class sources
    addClassSourcesFromDescriptor(builder, descriptor);

    ApplicationClassLoader jcl = builder.build();

    return new IsolatedClassLoader(applicationId, descriptor, tracker, jcl);
  }

  /**
   * Loads a class by delegating to the ApplicationClassLoader.
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
   * Finds a class by delegating to the ApplicationClassLoader.
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

  /** Platform-specific: Translate ApplicationDescriptor to ApplicationClassLoader sources. */
  private static void addClassSourcesFromDescriptor(
      ApplicationClassLoader.Builder builder, ApplicationDescriptor descriptor) {

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
          // Parse: maven:groupId:artifactId:version[:classifier][:packaging]
          String coords = classpathEntry.getSchemeSpecificPart();
          if (coords == null || coords.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Maven URI must specify coordinates: maven:groupId:artifactId:version");
          }
          String[] parts = coords.split(":", -1); // -1 to include trailing empty strings
          if (parts.length < 3) {
            throw new IllegalArgumentException(
                "Maven coordinates must have format groupId:artifactId:version, got: " + coords);
          }
          // Validate required parts are non-empty
          if (parts[0].trim().isEmpty()) {
            throw new IllegalArgumentException("Maven groupId cannot be empty in: " + coords);
          }
          if (parts[1].trim().isEmpty()) {
            throw new IllegalArgumentException("Maven artifactId cannot be empty in: " + coords);
          }
          if (parts[2].trim().isEmpty()) {
            throw new IllegalArgumentException("Maven version cannot be empty in: " + coords);
          }
          builder.addMavenCentral(coords);
          break;

        default:
          throw new IllegalArgumentException(
              "Unsupported classpath scheme: " + scheme + " in " + classpathEntry);
      }
    }
  }

  /** Platform-specific: Get cache directory for this application. */
  private static String getCacheDir(String applicationId) {
    String baseDir = System.getProperty("platform.cache.dir", "/var/platform/cache");
    return baseDir + "/" + applicationId;
  }

  /** Platform-specific: Extract authentication from descriptor properties. */
  private static AuthConfig getAuthFromDescriptor(ApplicationDescriptor descriptor, URI uri) {
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
        resourceTracker.getCacheHits());
  }

  /**
   * Closes the classloader and releases all tracked resources. Called when the application is
   * undeployed.
   */
  @Override
  public void close() {
    // Close all tracked resources
    resourceTracker.closeAllResources();

    // Optionally suggest GC to reclaim class memory (disabled by default)
    // Enable with -Djplatform.debug.forceGcOnClose=true
    if (Boolean.getBoolean("platform.debug.forceGcOnClose")) {
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
