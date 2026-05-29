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
 * Optional interface for services to report their health status.
 *
 * <p>Services can implement this interface to provide health information beyond
 * basic availability. The platform can periodically check service health and notify
 * dependent applications when a service becomes unavailable.</p>
 *
 * <p>Example implementation:</p>
 * <pre>{@code
 * public class DatabaseService implements HealthCheck {
 *     private Connection connection;
 *
 *     @Override
 *     public boolean isHealthy() {
 *         try {
 *             return connection != null && connection.isValid(1);
 *         } catch (SQLException e) {
 *             return false;
 *         }
 *     }
 *
 *     @Override
 *     public String getHealthStatus() {
 *         if (connection == null) {
 *             return "Not connected to database";
 *         }
 *         try {
 *             return connection.isValid(1) ? "Connected" : "Connection lost";
 *         } catch (SQLException e) {
 *             return "Error checking connection: " + e.getMessage();
 *         }
 *     }
 * }
 * }</pre>
 *
 * @since 2.0
 */
public interface HealthCheck {

    /**
     * Checks if the service is currently healthy and able to handle requests.
     *
     * <p>This method should return quickly (typically within 1 second) and not
     * perform expensive operations. It should verify that the service has all
     * required resources and dependencies available.</p>
     *
     * @return true if the service is healthy, false otherwise
     */
    boolean isHealthy();

    /**
     * Returns a human-readable description of the current health status.
     *
     * <p>This can provide additional context beyond the boolean health flag,
     * such as error messages, resource availability, or performance metrics.</p>
     *
     * @return a status message describing the service health
     */
    String getHealthStatus();
}
