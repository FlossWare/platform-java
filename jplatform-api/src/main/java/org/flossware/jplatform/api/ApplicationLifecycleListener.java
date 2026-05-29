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
 * Listener for application lifecycle events.
 * All methods have default empty implementations, so you only need to override
 * the events you care about.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApplicationLifecycleListener listener = new ApplicationLifecycleListener() {
 *     @Override
 *     public void onStarted(String applicationId) {
 *         System.out.println("Application started: " + applicationId);
 *     }
 *
 *     @Override
 *     public void onError(String applicationId, Throwable error) {
 *         System.err.println("Application error: " + error.getMessage());
 *     }
 * };
 * }</pre>
 *
 * @see ApplicationState
 */
public interface ApplicationLifecycleListener {
    /**
     * Called when an application has been deployed.
     *
     * @param applicationId the application identifier
     */
    default void onDeployed(String applicationId) {}

    /**
     * Called when an application is starting.
     *
     * @param applicationId the application identifier
     */
    default void onStarting(String applicationId) {}

    /**
     * Called when an application has successfully started.
     *
     * @param applicationId the application identifier
     */
    default void onStarted(String applicationId) {}

    /**
     * Called when an application is stopping.
     *
     * @param applicationId the application identifier
     */
    default void onStopping(String applicationId) {}

    /**
     * Called when an application has stopped.
     *
     * @param applicationId the application identifier
     */
    default void onStopped(String applicationId) {}

    /**
     * Called when an application has been undeployed.
     *
     * @param applicationId the application identifier
     */
    default void onUndeployed(String applicationId) {}

    /**
     * Called when an error occurs during application lifecycle.
     *
     * @param applicationId the application identifier
     * @param error the error that occurred
     */
    default void onError(String applicationId, Throwable error) {}
}
