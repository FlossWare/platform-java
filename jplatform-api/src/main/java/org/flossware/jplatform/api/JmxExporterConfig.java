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
 * Configuration for JMX metrics exporter.
 * Specifies RMI port and MBean domain for exposing application metrics via JMX.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * JmxExporterConfig config = JmxExporterConfig.builder()
 *     .enabled(true)
 *     .port(9999)
 *     .domain("org.flossware.jplatform")
 *     .build();
 * }</pre>
 *
 * @see MetricsExporter
 */
public class JmxExporterConfig {
    private final boolean enabled;
    private final int port;
    private final String domain;

    private JmxExporterConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.port = builder.port;
        this.domain = builder.domain;
    }

    /**
     * Checks if JMX export is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the RMI registry port.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the JMX domain name for MBeans.
     *
     * @return the domain name
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Creates a new builder for constructing JMX exporter configurations.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for JmxExporterConfig.
     */
    public static class Builder {
        private boolean enabled = true;
        private int port = 9999;
        private String domain = "org.flossware.jplatform";

        /**
         * Sets whether JMX export is enabled.
         *
         * @param enabled true to enable
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the RMI registry port.
         *
         * @param port the port number (0 = auto-assign)
         * @return this builder
         * @throws IllegalArgumentException if port is not in valid range (0-65535)
         */
        public Builder port(int port) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException(
                    "Port must be between 0 and 65535, got: " + port);
            }
            this.port = port;
            return this;
        }

        /**
         * Sets the JMX domain name for MBeans.
         *
         * @param domain the domain name
         * @return this builder
         * @throws IllegalArgumentException if domain is null or empty
         */
        public Builder domain(String domain) {
            if (domain == null || domain.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "JMX domain cannot be null or empty");
            }
            this.domain = domain;
            return this;
        }

        /**
         * Builds the JmxExporterConfig instance.
         *
         * @return a new JmxExporterConfig
         */
        public JmxExporterConfig build() {
            return new JmxExporterConfig(this);
        }
    }
}
