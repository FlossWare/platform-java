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
 * Base unchecked exception for all platform-java operations.
 * All platform-specific exceptions extend this class.
 * <p>
 * This is an unchecked exception (extends RuntimeException) to provide flexibility
 * in error handling without forcing applications to catch every possible exception.
 *
 * @since 1.2
 */
public class PlatformException extends RuntimeException {

    /**
     * Constructs a new platform exception with the specified detail message.
     *
     * @param message the detail message
     */
    public PlatformException(String message) {
        super(message);
    }

    /**
     * Constructs a new platform exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public PlatformException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new platform exception with the specified cause.
     *
     * @param cause the cause
     */
    public PlatformException(Throwable cause) {
        super(cause);
    }
}
