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

package org.flossware.platform.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves environment variable references in configuration strings.
 *
 * <p>Supports multiple formats:
 *
 * <ul>
 *   <li><code>${VAR_NAME}</code> - Shell/YAML style
 *   <li><code>${VAR_NAME:default}</code> - With default value
 *   <li><code>$VAR_NAME</code> - Simple style (alphanumeric and underscore only)
 * </ul>
 *
 * <p><strong>Security Note:</strong> This class helps prevent credentials from being stored in
 * plaintext configuration files by allowing references to environment variables that are set at
 * runtime.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // In YAML descriptor:
 * properties:
 *   database.url: ${DATABASE_URL}
 *   database.password: ${DB_PASSWORD:default_password}
 *   api.key: ${API_KEY}
 *
 * // Resolution:
 * EnvironmentVariableResolver resolver = new EnvironmentVariableResolver();
 * String url = resolver.resolve("${DATABASE_URL}");  // Reads from environment
 * String pwd = resolver.resolve("${DB_PASSWORD:secret}");  // Uses default if not set
 * }</pre>
 *
 * @since 2.0
 */
public class EnvironmentVariableResolver {

  // Pattern for ${VAR_NAME} or ${VAR_NAME:default}
  private static final Pattern BRACED_PATTERN =
      Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?\\}");

  // Pattern for $VAR_NAME (simple style, no default support)
  private static final Pattern SIMPLE_PATTERN = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

  private final Map<String, String> environmentOverrides;
  private final boolean failOnMissing;

  /** Creates a resolver that uses system environment variables. */
  public EnvironmentVariableResolver() {
    this(new HashMap<>(), false);
  }

  /**
   * Creates a resolver with custom configuration.
   *
   * @param environmentOverrides custom environment variable mappings (for testing)
   * @param failOnMissing if true, throw exception when variable is not found
   */
  public EnvironmentVariableResolver(
      Map<String, String> environmentOverrides, boolean failOnMissing) {
    this.environmentOverrides = new HashMap<>(environmentOverrides);
    this.failOnMissing = failOnMissing;
  }

  /**
   * Resolves all environment variable references in a string.
   *
   * @param input the string potentially containing variable references
   * @return the string with all variables resolved
   * @throws IllegalArgumentException if a required variable is missing and failOnMissing is true
   */
  public String resolve(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }

    // First resolve braced variables (${VAR} or ${VAR:default})
    String result = resolveBracedVariables(input);

    // Then resolve simple variables ($VAR)
    result = resolveSimpleVariables(result);

    return result;
  }

  /**
   * Resolves all environment variable references in a map of properties.
   *
   * @param properties the properties map to resolve
   * @return a new map with all values resolved
   */
  public Map<String, String> resolveMap(Map<String, String> properties) {
    if (properties == null || properties.isEmpty()) {
      return properties;
    }

    Map<String, String> resolved = new HashMap<>();
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      resolved.put(entry.getKey(), resolve(entry.getValue()));
    }
    return resolved;
  }

  /** Resolves ${VAR_NAME} and ${VAR_NAME:default} patterns. */
  private String resolveBracedVariables(String input) {
    Matcher matcher = BRACED_PATTERN.matcher(input);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
      String varName = matcher.group(1);
      String defaultValue = matcher.group(2); // May be null

      String value = getEnvironmentVariable(varName, defaultValue);
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }

    matcher.appendTail(result);
    return result.toString();
  }

  /** Resolves $VAR_NAME patterns (simple style, no default support). */
  private String resolveSimpleVariables(String input) {
    Matcher matcher = SIMPLE_PATTERN.matcher(input);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
      String varName = matcher.group(1);
      String value = getEnvironmentVariable(varName, null);
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }

    matcher.appendTail(result);
    return result.toString();
  }

  /**
   * Gets an environment variable value with optional default.
   *
   * @param varName the variable name
   * @param defaultValue the default value (may be null)
   * @return the variable value, default value, or original reference
   * @throws IllegalArgumentException if variable is missing and failOnMissing is true
   */
  private String getEnvironmentVariable(String varName, String defaultValue) {
    // Check overrides first (for testing)
    if (environmentOverrides.containsKey(varName)) {
      String value = environmentOverrides.get(varName);
      return value;
    }

    // Check system environment
    String value = System.getenv(varName);
    if (value != null) {
      return value;
    }

    // Use default value if provided
    if (defaultValue != null) {
      return defaultValue;
    }

    // Variable not found and no default
    if (failOnMissing) {
      throw new IllegalArgumentException(
          "Environment variable not found: " + varName + " and no default value provided");
    }

    return "${" + varName + "}"; // Return original reference
  }

  /** Masks sensitive values in logs (show first 3 chars only). */
  private String maskValue(String value) {
    if (value == null || value.isEmpty()) {
      return "empty";
    }
    if (value.length() <= 3) {
      return "***";
    }
    return value.substring(0, 3) + "***";
  }

  /**
   * Checks if a string contains environment variable references.
   *
   * @param input the string to check
   * @return true if the string contains variable references
   */
  public static boolean containsVariables(String input) {
    if (input == null || input.isEmpty()) {
      return false;
    }
    return BRACED_PATTERN.matcher(input).find() || SIMPLE_PATTERN.matcher(input).find();
  }
}
