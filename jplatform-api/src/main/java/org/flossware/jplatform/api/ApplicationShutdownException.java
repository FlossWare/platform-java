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
 * Thrown when an application fails to stop gracefully.
 * This exception indicates that the application's stop() method failed,
 * or that the platform could not transition the application to STOPPED state.
 *
 * @since 1.2
 */
public class ApplicationShutdownException extends PlatformException {

    private final String applicationId;

    /**
     * Constructs a new application shutdown exception.
     *
     * @param applicationId the ID of the application that failed to stop
     * @param message the detail message
     */
    public ApplicationShutdownException(String applicationId, String message) {
        super(message);
        this.applicationId = applicationId;
    }

    /**
     * Constructs a new application shutdown exception with a cause.
     *
     * @param applicationId the ID of the application that failed to stop
     * @param message the detail message
     * @param cause the underlying cause
     */
    public ApplicationShutdownException(String applicationId, String message, Throwable cause) {
        super(message, cause);
        this.applicationId = applicationId;
    }

    /**
     * Returns the ID of the application that failed to stop.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationId;
    }
}
