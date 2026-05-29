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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Represents a semantic version following the SemVer 2.0.0 specification.
 *
 * <p>Semantic versioning uses a MAJOR.MINOR.PATCH format where:
 *
 * <ul>
 *   <li>MAJOR version indicates incompatible API changes
 *   <li>MINOR version indicates backwards-compatible functionality additions
 *   <li>PATCH version indicates backwards-compatible bug fixes
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * SemanticVersion v1 = SemanticVersion.parse("1.2.3");
 * SemanticVersion v2 = SemanticVersion.parse("1.3.0");
 *
 * if (v2.isGreaterThan(v1)) {
 *   System.out.println("v2 is newer");
 * }
 *
 * if (v2.isCompatibleWith(v1)) {
 *   System.out.println("v2 is backwards compatible with v1");
 * }
 * }</pre>
 *
 * @since 2.4
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

  private static final Pattern VERSION_PATTERN =
      Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

  private final int major;
  private final int minor;
  private final int patch;

  /**
   * Creates a new semantic version.
   *
   * @param major the major version number (must be non-negative)
   * @param minor the minor version number (must be non-negative)
   * @param patch the patch version number (must be non-negative)
   * @throws IllegalArgumentException if any version component is negative
   */
  public SemanticVersion(int major, int minor, int patch) {
    if (major < 0) {
      throw new IllegalArgumentException("major version cannot be negative: " + major);
    }
    if (minor < 0) {
      throw new IllegalArgumentException("minor version cannot be negative: " + minor);
    }
    if (patch < 0) {
      throw new IllegalArgumentException("patch version cannot be negative: " + patch);
    }

    this.major = major;
    this.minor = minor;
    this.patch = patch;
  }

  /**
   * Parses a semantic version string in the format MAJOR.MINOR.PATCH.
   *
   * @param version the version string to parse
   * @return the parsed semantic version
   * @throws NullPointerException if version is null
   * @throws IllegalArgumentException if version format is invalid
   */
  @NonNull
  public static SemanticVersion parse(@NonNull String version) {
    Objects.requireNonNull(version, "version cannot be null");

    Matcher matcher = VERSION_PATTERN.matcher(version.trim());
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Invalid semantic version format: '"
              + version
              + "'. Expected format: MAJOR.MINOR.PATCH (e.g., 1.2.3)");
    }

    try {
      int major = Integer.parseInt(matcher.group(1));
      int minor = Integer.parseInt(matcher.group(2));
      int patch = Integer.parseInt(matcher.group(3));
      return new SemanticVersion(major, minor, patch);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Version numbers too large: " + version + ". Must be valid integers.", e);
    }
  }

  /** Returns the major version number. */
  public int getMajor() {
    return major;
  }

  /** Returns the minor version number. */
  public int getMinor() {
    return minor;
  }

  /** Returns the patch version number. */
  public int getPatch() {
    return patch;
  }

  /**
   * Checks if this version is compatible with the specified version.
   *
   * <p>Two versions are compatible if they have the same major version and this version is greater
   * than or equal to the specified version. This follows semantic versioning rules where:
   *
   * <ul>
   *   <li>Different major versions are incompatible (breaking changes)
   *   <li>Same major version with higher minor/patch is compatible (backwards compatible)
   *   <li>Lower minor/patch versions are incompatible (missing features)
   * </ul>
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>2.1.0 is compatible with 2.0.0 (same major, higher minor)
   *   <li>2.0.0 is NOT compatible with 2.1.0 (lower minor, missing features)
   *   <li>2.0.0 is NOT compatible with 1.9.9 (different major)
   * </ul>
   *
   * @param requiredVersion the required minimum version
   * @return true if this version is compatible with the required version
   * @throws NullPointerException if requiredVersion is null
   */
  public boolean isCompatibleWith(@NonNull SemanticVersion requiredVersion) {
    Objects.requireNonNull(requiredVersion, "requiredVersion cannot be null");

    // Different major versions are incompatible
    if (this.major != requiredVersion.major) {
      return false;
    }

    // Same major version - must be >= required version
    return this.compareTo(requiredVersion) >= 0;
  }

  /**
   * Checks if this version is greater than the specified version.
   *
   * @param other the version to compare against
   * @return true if this version is greater than the other version
   * @throws NullPointerException if other is null
   */
  public boolean isGreaterThan(@NonNull SemanticVersion other) {
    Objects.requireNonNull(other, "other cannot be null");
    return this.compareTo(other) > 0;
  }

  /**
   * Checks if this version is less than the specified version.
   *
   * @param other the version to compare against
   * @return true if this version is less than the other version
   * @throws NullPointerException if other is null
   */
  public boolean isLessThan(@NonNull SemanticVersion other) {
    Objects.requireNonNull(other, "other cannot be null");
    return this.compareTo(other) < 0;
  }

  /**
   * Checks if this version is greater than or equal to the specified version.
   *
   * @param other the version to compare against
   * @return true if this version is greater than or equal to the other version
   * @throws NullPointerException if other is null
   */
  public boolean isGreaterThanOrEqualTo(@NonNull SemanticVersion other) {
    Objects.requireNonNull(other, "other cannot be null");
    return this.compareTo(other) >= 0;
  }

  /**
   * Checks if this version is less than or equal to the specified version.
   *
   * @param other the version to compare against
   * @return true if this version is less than or equal to the other version
   * @throws NullPointerException if other is null
   */
  public boolean isLessThanOrEqualTo(@NonNull SemanticVersion other) {
    Objects.requireNonNull(other, "other cannot be null");
    return this.compareTo(other) <= 0;
  }

  @Override
  public int compareTo(@NonNull SemanticVersion other) {
    Objects.requireNonNull(other, "other cannot be null");

    // Compare major version first
    if (this.major != other.major) {
      return Integer.compare(this.major, other.major);
    }

    // If major is equal, compare minor
    if (this.minor != other.minor) {
      return Integer.compare(this.minor, other.minor);
    }

    // If minor is equal, compare patch
    return Integer.compare(this.patch, other.patch);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SemanticVersion)) {
      return false;
    }
    SemanticVersion other = (SemanticVersion) obj;
    return this.major == other.major && this.minor == other.minor && this.patch == other.patch;
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch);
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + patch;
  }
}
