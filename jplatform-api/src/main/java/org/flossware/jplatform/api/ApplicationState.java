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
 * Application lifecycle states.
 *
 * <p>Normal lifecycle progression:</p>
 * <pre>
 * DEPLOYED → STARTING → RUNNING → STOPPING → STOPPED → UNDEPLOYED
 * </pre>
 *
 * <p>Error states:</p>
 * <pre>
 * Any state → FAILED
 * </pre>
 */
public enum ApplicationState {
    /** Application has been deployed but not yet started */
    DEPLOYED,

    /** Application is in the process of starting */
    STARTING,

    /** Application is running normally */
    RUNNING,

    /** Application is in the process of stopping */
    STOPPING,

    /** Application has stopped cleanly */
    STOPPED,

    /** Application has failed (start/stop failure or runtime error) */
    FAILED,

    /** Application has been completely removed from the platform */
    UNDEPLOYED
}
