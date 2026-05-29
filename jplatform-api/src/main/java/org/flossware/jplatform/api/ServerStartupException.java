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
 * Thrown when a platform server (HTTP API, web console) fails to start.
 * This exception indicates failures during server initialization, port binding,
 * or other startup-related issues.
 *
 * @since 1.2
 */
public class ServerStartupException extends PlatformException {

    private final int port;

    /**
     * Constructs a new server startup exception.
     *
     * @param message the detail message
     * @param port the port the server was attempting to bind to
     */
    public ServerStartupException(String message, int port) {
        super(message);
        this.port = port;
    }

    /**
     * Constructs a new server startup exception with a cause.
     *
     * @param message the detail message
     * @param port the port the server was attempting to bind to
     * @param cause the underlying cause
     */
    public ServerStartupException(String message, int port, Throwable cause) {
        super(message, cause);
        this.port = port;
    }

    /**
     * Returns the port the server was attempting to bind to.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }
}
