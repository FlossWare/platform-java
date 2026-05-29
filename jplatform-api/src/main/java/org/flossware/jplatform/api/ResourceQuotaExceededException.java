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
 * Exception thrown when an application exceeds its resource quota.
 * This is a runtime exception to allow quota enforcement without
 * requiring explicit exception handling.
 *
 * @see ResourceQuota
 * @see ResourceMonitor
 */
public class ResourceQuotaExceededException extends RuntimeException {
    /**
     * Constructs a new resource quota exceeded exception with the specified message.
     *
     * @param message the detail message
     */
    public ResourceQuotaExceededException(String message) {
        super(message);
    }

    /**
     * Constructs a new resource quota exceeded exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public ResourceQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
