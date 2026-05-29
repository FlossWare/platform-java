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

package org.flossware.platform.api;

import java.util.Objects;

/**
 * Defines when and how an application should be automatically restarted.
 *
 * <p>Restart policies control the platform's behavior when an application exits or crashes. The
 * policy determines whether to restart, how many times to retry, and with what backoff strategy.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Always restart, unlimited retries
 * RestartPolicy always = RestartPolicy.always();
 *
 * // Restart only on failure, max 5 attempts
 * RestartPolicy onFailure = RestartPolicy.onFailure(5, 10, 300);
 *
 * // Never restart
 * RestartPolicy never = RestartPolicy.never();
 * }</pre>
 *
 * @since 2.3
 */
public final class RestartPolicy {

  private final RestartCondition condition;
  private final int maxRetries;
  private final long initialBackoffSeconds;
  private final long maxBackoffSeconds;

  private RestartPolicy(
      RestartCondition condition,
      int maxRetries,
      long initialBackoffSeconds,
      long maxBackoffSeconds) {
    this.condition = Objects.requireNonNull(condition, "condition cannot be null");
    this.maxRetries = maxRetries;
    this.initialBackoffSeconds = initialBackoffSeconds;
    this.maxBackoffSeconds = maxBackoffSeconds;
  }

  /**
   * Creates a policy that always restarts the application.
   *
   * @return an always-restart policy with unlimited retries
   */
  public static RestartPolicy always() {
    return new RestartPolicy(RestartCondition.ALWAYS, Integer.MAX_VALUE, 5, 300);
  }

  /**
   * Creates a policy that restarts only on failure (non-zero exit code).
   *
   * @param maxRetries maximum restart attempts
   * @param initialBackoffSeconds initial delay before first restart
   * @param maxBackoffSeconds maximum delay between restarts (exponential backoff caps here)
   * @return an on-failure restart policy
   */
  public static RestartPolicy onFailure(
      int maxRetries, long initialBackoffSeconds, long maxBackoffSeconds) {
    return new RestartPolicy(
        RestartCondition.ON_FAILURE, maxRetries, initialBackoffSeconds, maxBackoffSeconds);
  }

  /**
   * Creates a policy that never restarts the application.
   *
   * @return a never-restart policy
   */
  public static RestartPolicy never() {
    return new RestartPolicy(RestartCondition.NEVER, 0, 0, 0);
  }

  /**
   * Returns the restart condition.
   *
   * @return when to restart
   */
  public RestartCondition getCondition() {
    return condition;
  }

  /**
   * Returns the maximum number of restart attempts.
   *
   * @return max retries, or Integer.MAX_VALUE for unlimited
   */
  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Returns the initial backoff delay in seconds.
   *
   * @return initial delay before first restart
   */
  public long getInitialBackoffSeconds() {
    return initialBackoffSeconds;
  }

  /**
   * Returns the maximum backoff delay in seconds.
   *
   * @return max delay between restarts
   */
  public long getMaxBackoffSeconds() {
    return maxBackoffSeconds;
  }

  /**
   * Calculates the backoff delay for a given attempt number.
   *
   * <p>Uses exponential backoff: delay = min(initialBackoff * 2^attempt, maxBackoff)
   *
   * @param attemptNumber the attempt number (0-based)
   * @return the delay in seconds
   */
  public long calculateBackoff(int attemptNumber) {
    if (attemptNumber < 0) {
      return initialBackoffSeconds;
    }
    long delay = initialBackoffSeconds * (long) Math.pow(2, attemptNumber);
    return Math.min(delay, maxBackoffSeconds);
  }

  @Override
  public String toString() {
    return "RestartPolicy{"
        + "condition="
        + condition
        + ", maxRetries="
        + (maxRetries == Integer.MAX_VALUE ? "unlimited" : maxRetries)
        + ", initialBackoff="
        + initialBackoffSeconds
        + "s, maxBackoff="
        + maxBackoffSeconds
        + "s}";
  }

  /** Defines when to restart an application. */
  public enum RestartCondition {
    /** Always restart, regardless of exit code. */
    ALWAYS,
    /** Restart only on failure (non-zero exit code or exception). */
    ON_FAILURE,
    /** Never restart automatically. */
    NEVER
  }
}
