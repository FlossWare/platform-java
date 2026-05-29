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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.flossware.platform.api.ApplicationDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for HealthCheckConfigParser. */
class HealthCheckConfigParserTest {

  private HealthCheckConfigParser parser;

  @BeforeEach
  void setUp() {
    parser = new HealthCheckConfigParser();
  }

  @Test
  void testParseNotEnabled() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder().applicationId("test-app").mainClass("com.example.App").build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertFalse(result.isPresent(), "Should return empty when health checks not enabled");
  }

  @Test
  void testParseEnabledWithDefaults() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("healthcheck.enabled", "true")
            .build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    HealthChecker.HealthCheckConfig config = result.get();
    assertEquals(HealthChecker.HealthCheckType.APPLICATION, config.getType());
    assertEquals(30, config.getIntervalSeconds());
    assertEquals(10, config.getInitialDelaySeconds());
    assertEquals(5, config.getTimeoutSeconds());
    assertEquals(3, config.getFailureThreshold());
  }

  @Test
  void testParseHttpHealthCheck() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("healthcheck.enabled", "true")
            .property("healthcheck.type", "http")
            .property("healthcheck.http.url", "http://localhost:8080/health")
            .build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    HealthChecker.HealthCheckConfig config = result.get();
    assertEquals(HealthChecker.HealthCheckType.HTTP, config.getType());
    assertEquals("http://localhost:8080/health", config.getHttpUrl());
  }

  @Test
  void testParseTcpHealthCheck() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("healthcheck.enabled", "true")
            .property("healthcheck.type", "tcp")
            .property("healthcheck.tcp.host", "localhost")
            .property("healthcheck.tcp.port", "8080")
            .build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    HealthChecker.HealthCheckConfig config = result.get();
    assertEquals(HealthChecker.HealthCheckType.TCP, config.getType());
    assertEquals("localhost", config.getTcpHost());
    assertEquals(8080, config.getTcpPort());
  }

  @Test
  void testParseCustomIntervals() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("healthcheck.enabled", "true")
            .property("healthcheck.interval", "60")
            .property("healthcheck.initialDelay", "30")
            .property("healthcheck.timeout", "10")
            .property("healthcheck.failureThreshold", "5")
            .build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    HealthChecker.HealthCheckConfig config = result.get();
    assertEquals(60, config.getIntervalSeconds());
    assertEquals(30, config.getInitialDelaySeconds());
    assertEquals(10, config.getTimeoutSeconds());
    assertEquals(5, config.getFailureThreshold());
  }

  @Test
  void testParseCaseInsensitiveType() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("healthcheck.enabled", "true")
            .property("healthcheck.type", "HTTP")
            .property("healthcheck.http.url", "http://localhost:8080/health")
            .build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    assertEquals(HealthChecker.HealthCheckType.HTTP, result.get().getType());
  }

  @Test
  void testParseUnknownTypeDefaultsToApplication() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("healthcheck.enabled", "true")
            .property("healthcheck.type", "unknown-type")
            .build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    assertEquals(HealthChecker.HealthCheckType.APPLICATION, result.get().getType());
  }

  @Test
  void testParseInvalidIntervalUsesDefault() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("healthcheck.enabled", "true")
            .property("healthcheck.interval", "not-a-number")
            .build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    assertEquals(30, result.get().getIntervalSeconds(), "Should use default when invalid");
  }

  @Test
  void testParseInvalidPortUsesDefault() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("healthcheck.enabled", "true")
            .property("healthcheck.type", "tcp")
            .property("healthcheck.tcp.host", "localhost")
            .property("healthcheck.tcp.port", "invalid")
            .build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    assertEquals(0, result.get().getTcpPort());
  }

  @Test
  void testParseEnabledFalse() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("healthcheck.enabled", "false")
            .build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertFalse(result.isPresent(), "Should return empty when explicitly disabled");
  }

  @Test
  void testParseTrimsWhitespace() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("healthcheck.enabled", "  true  ")
            .property("healthcheck.type", "  http  ")
            .property("healthcheck.http.url", "  http://localhost:8080/health  ")
            .build();

    Optional<HealthChecker.HealthCheckConfig> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    HealthChecker.HealthCheckConfig config = result.get();
    assertEquals(HealthChecker.HealthCheckType.HTTP, config.getType());
    assertEquals("http://localhost:8080/health", config.getHttpUrl());
  }
}
