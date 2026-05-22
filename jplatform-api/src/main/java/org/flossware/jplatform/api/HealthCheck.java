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
