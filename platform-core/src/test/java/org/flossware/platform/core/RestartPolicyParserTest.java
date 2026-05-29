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
import org.flossware.platform.api.RestartPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for RestartPolicyParser. */
class RestartPolicyParserTest {

  private RestartPolicyParser parser;

  @BeforeEach
  void setUp() {
    parser = new RestartPolicyParser();
  }

  @Test
  void testParseNoPolicy() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertFalse(result.isPresent(), "Should return empty when no restart policy configured");
  }

  @Test
  void testParseNever() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("restart.policy", "never")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    assertEquals(RestartPolicy.RestartCondition.NEVER, result.get().getCondition());
  }

  @Test
  void testParseAlways() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("restart.policy", "always")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    assertEquals(RestartPolicy.RestartCondition.ALWAYS, result.get().getCondition());
    assertEquals(Integer.MAX_VALUE, result.get().getMaxRetries());
  }

  @Test
  void testParseOnFailureWithDefaults() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("restart.policy", "on-failure")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    RestartPolicy policy = result.get();
    assertEquals(RestartPolicy.RestartCondition.ON_FAILURE, policy.getCondition());
    assertEquals(5, policy.getMaxRetries());
    assertEquals(5, policy.getInitialBackoffSeconds());
    assertEquals(300, policy.getMaxBackoffSeconds());
  }

  @Test
  void testParseOnFailureWithCustomValues() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("restart.policy", "on-failure")
            .property("restart.maxRetries", "10")
            .property("restart.initialBackoff", "15")
            .property("restart.maxBackoff", "600")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    RestartPolicy policy = result.get();
    assertEquals(RestartPolicy.RestartCondition.ON_FAILURE, policy.getCondition());
    assertEquals(10, policy.getMaxRetries());
    assertEquals(15, policy.getInitialBackoffSeconds());
    assertEquals(600, policy.getMaxBackoffSeconds());
  }

  @Test
  void testParseCaseInsensitive() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("restart.policy", "ON-FAILURE")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    assertEquals(RestartPolicy.RestartCondition.ON_FAILURE, result.get().getCondition());
  }

  @Test
  void testParseUnknownPolicyDefaultsToNever() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("restart.policy", "unknown-policy")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    assertEquals(RestartPolicy.RestartCondition.NEVER, result.get().getCondition());
  }

  @Test
  void testParseInvalidMaxRetriesUsesDefault() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("restart.policy", "on-failure")
            .property("restart.maxRetries", "not-a-number")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    assertEquals(5, result.get().getMaxRetries(), "Should use default when invalid");
  }

  @Test
  void testParseInvalidBackoffUsesDefault() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("restart.policy", "on-failure")
            .property("restart.initialBackoff", "invalid")
            .property("restart.maxBackoff", "also-invalid")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    RestartPolicy policy = result.get();
    assertEquals(5, policy.getInitialBackoffSeconds());
    assertEquals(300, policy.getMaxBackoffSeconds());
  }

  @Test
  void testParseEmptyPolicyString() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("restart.policy", "   ")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertFalse(result.isPresent(), "Empty policy string should be treated as no policy");
  }

  @Test
  void testParseTrimsWhitespace() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("test-app")
            .mainClass("com.example.App")
            .property("restart.policy", "  always  ")
            .property("restart.maxRetries", "  10  ")
            .build();

    Optional<RestartPolicy> result = parser.parse(descriptor);

    assertTrue(result.isPresent());
    assertEquals(RestartPolicy.RestartCondition.ALWAYS, result.get().getCondition());
  }
}
