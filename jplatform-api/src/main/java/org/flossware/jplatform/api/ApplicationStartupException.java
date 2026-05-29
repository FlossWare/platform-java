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
 * Thrown when an application fails to start.
 * This exception indicates that the application's start() method failed,
 * or that the platform could not transition the application to RUNNING state.
 *
 * @since 1.2
 */
public class ApplicationStartupException extends PlatformException {

    private final String applicationId;

    /**
     * Constructs a new application startup exception.
     *
     * @param applicationId the ID of the application that failed to start
     * @param message the detail message
     */
    public ApplicationStartupException(String applicationId, String message) {
        super(message);
        this.applicationId = applicationId;
    }

    /**
     * Constructs a new application startup exception with a cause.
     *
     * @param applicationId the ID of the application that failed to start
     * @param message the detail message
     * @param cause the underlying cause
     */
    public ApplicationStartupException(String applicationId, String message, Throwable cause) {
        super(message, cause);
        this.applicationId = applicationId;
    }

    /**
     * Returns the ID of the application that failed to start.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationId;
    }
}
