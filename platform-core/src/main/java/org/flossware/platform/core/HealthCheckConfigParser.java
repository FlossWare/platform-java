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

import java.util.Map;
import java.util.Optional;

import org.flossware.platform.api.ApplicationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses health check configuration from application descriptor properties.
 *
 * <p>Supported properties:
 *
 * <ul>
 *   <li>healthcheck.enabled - "true" or "false" (default: false)
 *   <li>healthcheck.type - "application", "http", "tcp" (default: "application")
 *   <li>healthcheck.interval - check interval in seconds (default: 30)
 *   <li>healthcheck.initialDelay - initial delay before first check in seconds (default: 10)
 *   <li>healthcheck.timeout - check timeout in seconds (default: 5)
 *   <li>healthcheck.failureThreshold - consecutive failures before action (default: 3)
 *   <li>healthcheck.http.url - HTTP endpoint URL (required if type=http)
 *   <li>healthcheck.tcp.host - TCP host (required if type=tcp)
 *   <li>healthcheck.tcp.port - TCP port (required if type=tcp)
 * </ul>
 *
 * <p>Example descriptor properties:
 *
 * <pre>{@code
 * healthcheck.enabled=true
 * healthcheck.type=http
 * healthcheck.interval=60
 * healthcheck.http.url=http://localhost:8080/health
 * }</pre>
 *
 * @since 2.3
 */
class HealthCheckConfigParser {

  private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckConfigParser.class);

  private static final String PROP_ENABLED = "healthcheck.enabled";
  private static final String PROP_TYPE = "healthcheck.type";
  private static final String PROP_INTERVAL = "healthcheck.interval";
  private static final String PROP_INITIAL_DELAY = "healthcheck.initialDelay";
  private static final String PROP_TIMEOUT = "healthcheck.timeout";
  private static final String PROP_FAILURE_THRESHOLD = "healthcheck.failureThreshold";
  private static final String PROP_HTTP_URL = "healthcheck.http.url";
  private static final String PROP_TCP_HOST = "healthcheck.tcp.host";
  private static final String PROP_TCP_PORT = "healthcheck.tcp.port";

  /**
   * Parses health check configuration from application descriptor properties.
   *
   * @param descriptor the application descriptor
   * @return the parsed health check config, or empty if health checks not enabled
   */
  Optional<HealthChecker.HealthCheckConfig> parse(ApplicationDescriptor descriptor) {
    Map<String, String> props = descriptor.getProperties();

    String enabled = props.get(PROP_ENABLED);
    if (enabled == null || !enabled.trim().equalsIgnoreCase("true")) {
      // Health checks not enabled
      return Optional.empty();
    }

    try {
      HealthChecker.HealthCheckConfig.Builder builder = HealthChecker.HealthCheckConfig.builder();

      // Parse type
      String typeStr = props.get(PROP_TYPE);
      if (typeStr != null && !typeStr.trim().isEmpty()) {
        try {
          HealthChecker.HealthCheckType type =
              HealthChecker.HealthCheckType.valueOf(typeStr.trim().toUpperCase());
          builder.type(type);
        } catch (IllegalArgumentException e) {
          LOGGER.warn(
              "[{}] Unknown health check type '{}', using APPLICATION",
              descriptor.getApplicationId(),
              typeStr);
          builder.type(HealthChecker.HealthCheckType.APPLICATION);
        }
      }

      // Parse numeric values
      builder.intervalSeconds(parseLongProperty(props, PROP_INTERVAL, 30));
      builder.initialDelaySeconds(parseLongProperty(props, PROP_INITIAL_DELAY, 10));
      builder.timeoutSeconds(parseLongProperty(props, PROP_TIMEOUT, 5));
      builder.failureThreshold(parseIntProperty(props, PROP_FAILURE_THRESHOLD, 3));

      // Parse HTTP-specific properties
      String httpUrl = props.get(PROP_HTTP_URL);
      if (httpUrl != null && !httpUrl.trim().isEmpty()) {
        builder.httpUrl(httpUrl.trim());
      }

      // Parse TCP-specific properties
      String tcpHost = props.get(PROP_TCP_HOST);
      if (tcpHost != null && !tcpHost.trim().isEmpty()) {
        builder.tcpHost(tcpHost.trim());
      }

      String tcpPort = props.get(PROP_TCP_PORT);
      if (tcpPort != null && !tcpPort.trim().isEmpty()) {
        builder.tcpPort(parseIntProperty(props, PROP_TCP_PORT, 0));
      }

      return Optional.of(builder.build());

    } catch (Exception e) {
      LOGGER.error(
          "[{}] Failed to parse health check config, health checks disabled",
          descriptor.getApplicationId(),
          e);
      return Optional.empty();
    }
  }

  private int parseIntProperty(Map<String, String> props, String key, int defaultValue) {
    String value = props.get(key);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      LOGGER.warn(
          "Invalid integer value for '{}': '{}', using default {}", key, value, defaultValue);
      return defaultValue;
    }
  }

  private long parseLongProperty(Map<String, String> props, String key, long defaultValue) {
    String value = props.get(key);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      LOGGER.warn("Invalid long value for '{}': '{}', using default {}", key, value, defaultValue);
      return defaultValue;
    }
  }
}
