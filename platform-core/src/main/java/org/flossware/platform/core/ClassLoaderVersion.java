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

package org.flossware.platform.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Tracks a specific version of an application's classloader.
 *
 * <p>Used to maintain history of classloader versions for potential rollback if a hot reload fails.
 * Each version has a number, creation timestamp, and reference to the classloader.
 *
 * @since 2.0
 */
class ClassLoaderVersion {

  private final int version;
  private final ClassLoader classLoader;
  private final Instant createdAt;
  private volatile int referenceCount;

  /**
   * Creates a new classloader version.
   *
   * @param version the version number (incrementing)
   * @param classLoader the classloader for this version
   * @throws IllegalArgumentException if version is negative
   * @throws NullPointerException if classLoader is null
   */
  ClassLoaderVersion(int version, ClassLoader classLoader) {
    if (version < 0) {
      throw new IllegalArgumentException("Version must be non-negative, got: " + version);
    }
    this.version = version;
    this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
    this.createdAt = Instant.now();
    this.referenceCount = 1;
  }

  /**
   * Returns the version number.
   *
   * @return version number
   */
  public int getVersion() {
    return version;
  }

  /**
   * Returns the classloader.
   *
   * @return the classloader
   */
  public ClassLoader getClassLoader() {
    return classLoader;
  }

  /**
   * Returns the creation timestamp.
   *
   * @return when this version was created
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Returns the current reference count.
   *
   * <p>Used to determine when this classloader can be garbage collected. When reference count
   * reaches zero, the classloader is no longer in use.
   *
   * @return reference count
   */
  public synchronized int getReferenceCount() {
    return referenceCount;
  }

  /**
   * Increments the reference count.
   *
   * @return new reference count
   */
  public synchronized int incrementReference() {
    return ++referenceCount;
  }

  /**
   * Decrements the reference count.
   *
   * @return new reference count
   */
  public synchronized int decrementReference() {
    if (referenceCount > 0) {
      referenceCount--;
    }
    return referenceCount;
  }

  /**
   * Checks if this version can be garbage collected.
   *
   * @return true if reference count is zero, false otherwise
   */
  public boolean canGarbageCollect() {
    return referenceCount == 0;
  }

  @Override
  public String toString() {
    return "ClassLoaderVersion{"
        + "version="
        + version
        + ", createdAt="
        + createdAt
        + ", referenceCount="
        + referenceCount
        + '}';
  }
}
