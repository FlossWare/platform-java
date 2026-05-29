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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.flossware.platform.api.RestartPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages automatic restarts for applications based on configured policies.
 *
 * <p>Monitors application lifecycle and triggers restarts according to the {@link RestartPolicy}.
 * Implements exponential backoff to avoid restart storms and tracks restart history for
 * observability.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * RestartPolicy policy = RestartPolicy.onFailure(5, 10, 300);
 * RestartManager manager = new RestartManager(context, policy, applicationManager);
 * manager.start();
 *
 * // Later, when app exits with error:
 * manager.onApplicationExit(1); // Exit code 1 = failure
 * }</pre>
 *
 * @since 2.3
 */
public class RestartManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(RestartManager.class);

  private final ApplicationContextImpl context;
  private final RestartPolicy policy;
  private final ApplicationManager applicationManager;
  private final ScheduledExecutorService scheduler;
  private final AtomicInteger restartCount;
  private volatile Instant lastRestartAttempt;
  private volatile boolean active;

  /**
   * Creates a restart manager.
   *
   * @param context the application context
   * @param policy the restart policy
   * @param applicationManager the application manager for performing restarts
   */
  public RestartManager(
      ApplicationContextImpl context, RestartPolicy policy, ApplicationManager applicationManager) {
    this.context = Objects.requireNonNull(context, "context cannot be null");
    this.policy = Objects.requireNonNull(policy, "policy cannot be null");
    this.applicationManager =
        Objects.requireNonNull(applicationManager, "applicationManager cannot be null");
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "restart-manager-" + context.getApplicationId());
              t.setDaemon(true);
              return t;
            });
    this.restartCount = new AtomicInteger(0);
    this.active = false;
  }

  /**
   * Starts the restart manager.
   *
   * <p>The manager begins monitoring the application and will trigger restarts according to the
   * configured policy.
   */
  public void start() {
    active = true;
    LOGGER.info("[{}] Restart manager started with policy: {}", context.getApplicationId(), policy);
  }

  /**
   * Stops the restart manager.
   *
   * <p>Cancels any pending restart attempts and shuts down the scheduler.
   */
  public void stop() {
    active = false;
    scheduler.shutdownNow();
    LOGGER.info("[{}] Restart manager stopped", context.getApplicationId());
  }

  /**
   * Called when the application exits.
   *
   * <p>Evaluates the restart policy and schedules a restart if appropriate.
   *
   * @param exitCode the application exit code (0 = success, non-zero = failure)
   */
  public void onApplicationExit(int exitCode) {
    if (!active) {
      LOGGER.debug(
          "[{}] Restart manager inactive, ignoring exit code {}",
          context.getApplicationId(),
          exitCode);
      return;
    }

    boolean isFailure = (exitCode != 0);
    boolean shouldRestart = shouldRestart(isFailure);

    if (shouldRestart) {
      scheduleRestart(isFailure);
    } else {
      LOGGER.info(
          "[{}] Not restarting (policy={}, exitCode={}, restartCount={})",
          context.getApplicationId(),
          policy.getCondition(),
          exitCode,
          restartCount.get());
    }
  }

  /**
   * Called when the application throws an unhandled exception.
   *
   * <p>Treats this as a failure and evaluates restart policy accordingly.
   *
   * @param throwable the uncaught exception
   */
  public void onApplicationException(Throwable throwable) {
    if (!active) {
      return;
    }

    LOGGER.error(
        "[{}] Application threw unhandled exception", context.getApplicationId(), throwable);

    boolean shouldRestart = shouldRestart(true); // Exception = failure
    if (shouldRestart) {
      scheduleRestart(true);
    }
  }

  private boolean shouldRestart(boolean isFailure) {
    switch (policy.getCondition()) {
      case NEVER:
        return false;

      case ALWAYS:
        if (restartCount.get() >= policy.getMaxRetries()) {
          LOGGER.warn(
              "[{}] Max restart attempts reached ({}/{})",
              context.getApplicationId(),
              restartCount.get(),
              policy.getMaxRetries());
          return false;
        }
        return true;

      case ON_FAILURE:
        if (!isFailure) {
          LOGGER.info(
              "[{}] Application exited successfully (exit code 0), not restarting",
              context.getApplicationId());
          return false;
        }
        if (restartCount.get() >= policy.getMaxRetries()) {
          LOGGER.warn(
              "[{}] Max restart attempts reached ({}/{})",
              context.getApplicationId(),
              restartCount.get(),
              policy.getMaxRetries());
          return false;
        }
        return true;

      default:
        return false;
    }
  }

  private void scheduleRestart(boolean wasFailure) {
    int attempt = restartCount.getAndIncrement();
    long delaySeconds = policy.calculateBackoff(attempt);

    LOGGER.info(
        "[{}] Scheduling restart attempt {}/{} in {}s (reason: {})",
        context.getApplicationId(),
        attempt + 1,
        policy.getMaxRetries() == Integer.MAX_VALUE ? "unlimited" : policy.getMaxRetries(),
        delaySeconds,
        wasFailure ? "failure" : "normal exit");

    scheduler.schedule(
        () -> {
          try {
            performRestart();
          } catch (Exception e) {
            LOGGER.error("[{}] Restart failed", context.getApplicationId(), e);
            // Could trigger another restart attempt here if policy allows
          }
        },
        delaySeconds,
        TimeUnit.SECONDS);
  }

  private void performRestart() {
    lastRestartAttempt = Instant.now();

    LOGGER.info(
        "[{}] Performing restart (attempt {}/{})",
        context.getApplicationId(),
        restartCount.get(),
        policy.getMaxRetries() == Integer.MAX_VALUE ? "unlimited" : policy.getMaxRetries());

    try {
      // Stop the application
      applicationManager.stop(context.getApplicationId());

      // Give it a moment to clean up
      Thread.sleep(1000);

      // Start the application
      applicationManager.start(context.getApplicationId());

      LOGGER.info("[{}] Restart completed successfully", context.getApplicationId());
    } catch (Exception e) {
      LOGGER.error("[{}] Restart failed", context.getApplicationId(), e);
      throw new RuntimeException("Restart failed", e);
    }
  }

  /**
   * Returns the number of restart attempts made.
   *
   * @return restart count
   */
  public int getRestartCount() {
    return restartCount.get();
  }

  /**
   * Returns when the last restart was attempted.
   *
   * @return timestamp of last restart, or null if never restarted
   */
  public Instant getLastRestartAttempt() {
    return lastRestartAttempt;
  }

  /**
   * Returns the configured restart policy.
   *
   * @return the policy
   */
  public RestartPolicy getPolicy() {
    return policy;
  }

  /**
   * Resets the restart counter.
   *
   * <p>Call this after a successful period of uptime to allow fresh restart attempts if the app
   * fails later.
   */
  public void resetRestartCount() {
    int oldCount = restartCount.getAndSet(0);
    if (oldCount > 0) {
      LOGGER.info("[{}] Restart counter reset from {} to 0", context.getApplicationId(), oldCount);
    }
  }
}
