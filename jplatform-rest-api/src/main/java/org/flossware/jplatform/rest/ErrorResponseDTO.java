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

package org.flossware.jplatform.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data transfer object for error responses in the REST API.
 * Returns structured error information with HTTP status, message, and timestamp.
 *
 * <p>Example JSON response:</p>
 * <pre>{@code
 * {
 *   "error": "ApplicationNotFound",
 *   "message": "Application not deployed: my-app",
 *   "status": 404,
 *   "timestamp": 1716345600000
 * }
 * }</pre>
 *
 * @see ApplicationApiHandler
 * @see PlatformApiHandler
 */
public class ErrorResponseDTO {

    @JsonProperty("error")
    private final String error;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("status")
    private final int status;

    @JsonProperty("timestamp")
    private final long timestamp;

    /**
     * Constructs a new error response DTO.
     *
     * @param error the error type or category
     * @param message the detailed error message
     * @param status the HTTP status code
     * @param timestamp the timestamp in milliseconds since epoch
     */
    public ErrorResponseDTO(String error, String message, int status, long timestamp) {
        this.error = error;
        this.message = message;
        this.status = status;
        this.timestamp = timestamp;
    }

    /**
     * Creates an error response for the current time.
     *
     * @param error the error type
     * @param message the error message
     * @param status the HTTP status code
     * @return a new error response DTO
     */
    public static ErrorResponseDTO create(String error, String message, int status) {
        return new ErrorResponseDTO(error, message, status, System.currentTimeMillis());
    }

    /**
     * Returns the error type or category.
     *
     * @return the error type
     */
    public String getError() {
        return error;
    }

    /**
     * Returns the detailed error message.
     *
     * @return the error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return the status code
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the timestamp when the error occurred.
     *
     * @return the timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }
}
