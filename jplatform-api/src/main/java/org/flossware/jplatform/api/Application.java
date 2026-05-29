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
 * Platform-aware application interface.
 * Applications implementing this interface gain access to platform features like
 * thread pools, messaging, and resource monitoring.
 *
 * <p>Applications can also use a standard {@code main(String[] args)} method instead,
 * but will not have access to platform features.</p>
 *
 * @see ApplicationContext
 * @since 1.0
 */
public interface Application {
    /**
     * Called when the application is started by the platform.
     *
     * @param context the application context providing access to platform features
     * @throws ApplicationStartupException if the application fails to start
     * @since 1.0
     */
    void start(ApplicationContext context) throws ApplicationStartupException;

    /**
     * Called when the application is stopped by the platform.
     * Implementations should clean up resources and shut down gracefully.
     *
     * @throws ApplicationShutdownException if the application fails to stop cleanly
     * @since 1.0
     */
    void stop() throws ApplicationShutdownException;
}
