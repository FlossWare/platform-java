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

package org.flossware.jplatform.api;

/**
 * Defines the enforcement actions that can be taken when an application
 * exceeds its resource quotas (CPU, memory, threads).
 *
 * <p>The platform monitors resource usage at regular intervals (default: 5 seconds).
 * When a quota is exceeded, the configured enforcement action is triggered after
 * a grace period (default: 3 consecutive violations).</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * ResourceConfig config = ResourceConfig.builder()
 *     .maxHeapMB(512)
 *     .maxThreads(50)
 *     .maxCpuTimeSeconds(300)
 *     .memoryEnforcementAction(EnforcementAction.SHUTDOWN)
 *     .cpuEnforcementAction(EnforcementAction.THROTTLE)
 *     .threadEnforcementAction(EnforcementAction.SHUTDOWN)
 *     .build();
 * }</pre>
 *
 * @since 2.0
 * @see ResourceConfig
 * @see ResourceQuota
 */
public enum EnforcementAction {

    /**
     * Only log the quota violation and notify listeners.
     * This is the default behavior and does not take any corrective action.
     * The application continues running even after exceeding quotas.
     *
     * <p>Use this for monitoring-only scenarios where you want to track
     * quota violations without impacting application behavior.</p>
     */
    NOTIFY,

    /**
     * Throttle application execution to stay within quota limits.
     * Slows down thread execution by introducing sleep periods proportional
     * to the quota overage.
     *
     * <p>Example: If the application is using 120% of its CPU quota,
     * threads will sleep ~20% of the time to bring usage back under quota.</p>
     *
     * <p>This is a soft enforcement mechanism that allows the application
     * to continue running with degraded performance.</p>
     */
    THROTTLE,

    /**
     * Gracefully stop the application when quota is exceeded.
     * Calls {@code ApplicationManager.stop(applicationId)} which:
     * <ul>
     *   <li>Invokes the application's {@code stop()} method</li>
     *   <li>Waits for threads to complete (with timeout)</li>
     *   <li>Transitions to STOPPED state</li>
     *   <li>Preserves deployment (can be restarted)</li>
     * </ul>
     *
     * <p>This is the recommended enforcement for memory quota violations
     * as it prevents OOM errors while preserving the deployment.</p>
     */
    SHUTDOWN,

    /**
     * Immediately terminate the application when quota is exceeded.
     * Forcefully stops all application threads via {@code ThreadGroup.stop()}
     * and then undeploys the application entirely.
     *
     * <p><b>Warning</b>: This is a destructive action that:
     * <ul>
     *   <li>Interrupts all running threads immediately</li>
     *   <li>Does NOT call the application's {@code stop()} method</li>
     *   <li>Removes the application deployment completely</li>
     *   <li>Cannot be restarted (must be redeployed)</li>
     * </ul></p>
     *
     * <p>Use this only for critical quota violations where resource
     * exhaustion threatens platform stability.</p>
     */
    KILL;

    /**
     * Returns whether this enforcement action is destructive (stops the application).
     *
     * @return true if this action stops or kills the application
     */
    public boolean isDestructive() {
        return this == SHUTDOWN || this == KILL;
    }

    /**
     * Returns whether this enforcement action is graceful (allows cleanup).
     *
     * @return true if this action allows graceful application shutdown
     */
    public boolean isGraceful() {
        return this != KILL;
    }
}
