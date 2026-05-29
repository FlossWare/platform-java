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
 * Exports application metrics to external monitoring systems.
 * Implementations provide integration with JMX, Prometheus, or other monitoring tools.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * JmxExporterConfig config = JmxExporterConfig.builder()
 *     .port(9999)
 *     .domain("org.flossware.jplatform")
 *     .build();
 *
 * MetricsExporter exporter = new JmxMetricsExporter(config);
 * exporter.start();
 *
 * // Register applications
 * exporter.registerApplication("my-app", applicationContext);
 * }</pre>
 *
 * @see JmxExporterConfig
 * @see PrometheusExporterConfig
 */
public interface MetricsExporter extends AutoCloseable {

    /**
     * Starts the metrics exporter.
     *
     * @throws Exception if the exporter cannot be started
     */
    void start() throws Exception;

    /**
     * Stops the metrics exporter.
     *
     * @throws Exception if the exporter cannot be stopped
     */
    void stop() throws Exception;

    /**
     * Registers an application for metrics export.
     *
     * @param applicationId the application identifier
     * @param context the application context containing metrics
     */
    void registerApplication(String applicationId, ApplicationContext context);

    /**
     * Unregisters an application from metrics export.
     *
     * @param applicationId the application identifier
     */
    void unregisterApplication(String applicationId);

    /**
     * Checks if the exporter is currently running.
     *
     * @return true if running, false otherwise
     */
    boolean isRunning();
}
