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

package org.flossware.jplatform.messaging.jms;

import java.util.Objects;

/**
 * Configuration for JMS message bus connection.
 * Contains connection parameters for connecting to a JMS broker.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * JmsConfig config = JmsConfig.builder()
 *     .brokerUrl("tcp://localhost:61616")
 *     .username("admin")
 *     .password("admin")
 *     .clientId("jplatform-node-1")
 *     .build();
 *
 * JmsMessageBus messageBus = new JmsMessageBus(config, connectionFactory);
 * }</pre>
 *
 * <p><b>Thread Safety:</b> This class is immutable and thread-safe.</p>
 *
 * @see JmsMessageBus
 * @since 1.1
 */
public class JmsConfig {

    private final String brokerUrl;
    private final String username;
    private final String password;
    private final String clientId;
    private final int acknowledgeMode;
    private final boolean transacted;

    private JmsConfig(Builder builder) {
        this.brokerUrl = Objects.requireNonNull(builder.brokerUrl, "brokerUrl is required");
        this.username = builder.username;
        this.password = builder.password;
        this.clientId = builder.clientId;
        this.acknowledgeMode = builder.acknowledgeMode;
        this.transacted = builder.transacted;
    }

    /**
     * Returns the JMS broker URL.
     *
     * @return the broker URL (e.g., "tcp://localhost:61616")
     */
    public String getBrokerUrl() {
        return brokerUrl;
    }

    /**
     * Returns the JMS username for authentication.
     *
     * @return the username, or null if no authentication
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the JMS password for authentication.
     *
     * @return the password, or null if no authentication
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the JMS client identifier.
     *
     * @return the client ID, or null if not set
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Returns the JMS session acknowledge mode.
     *
     * @return the acknowledge mode constant (e.g., Session.AUTO_ACKNOWLEDGE)
     */
    public int getAcknowledgeMode() {
        return acknowledgeMode;
    }

    /**
     * Checks if JMS sessions should be transacted.
     *
     * @return true if sessions are transacted, false otherwise
     */
    public boolean isTransacted() {
        return transacted;
    }

    /**
     * Creates a new builder for constructing JmsConfig instances.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing JmsConfig instances.
     */
    public static class Builder {
        private String brokerUrl;
        private String username;
        private String password;
        private String clientId;
        private int acknowledgeMode = 1; // Session.AUTO_ACKNOWLEDGE
        private boolean transacted = false;

        /**
         * Sets the JMS broker URL.
         *
         * @param brokerUrl the broker URL (e.g., "tcp://localhost:61616")
         * @return this builder
         * @throws IllegalArgumentException if brokerUrl is null or empty
         */
        public Builder brokerUrl(String brokerUrl) {
            if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("brokerUrl cannot be null or empty");
            }
            this.brokerUrl = brokerUrl;
            return this;
        }

        /**
         * Sets the JMS username for authentication.
         *
         * @param username the username
         * @return this builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the JMS password for authentication.
         *
         * @param password the password
         * @return this builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the JMS client identifier.
         *
         * @param clientId the client ID
         * @return this builder
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Sets the JMS session acknowledge mode.
         *
         * @param acknowledgeMode the acknowledge mode constant
         * @return this builder
         */
        public Builder acknowledgeMode(int acknowledgeMode) {
            this.acknowledgeMode = acknowledgeMode;
            return this;
        }

        /**
         * Sets whether JMS sessions should be transacted.
         *
         * @param transacted true for transacted sessions
         * @return this builder
         */
        public Builder transacted(boolean transacted) {
            this.transacted = transacted;
            return this;
        }

        /**
         * Builds the JmsConfig instance.
         *
         * @return a new JmsConfig with the configured values
         * @throws NullPointerException if brokerUrl is null
         */
        public JmsConfig build() {
            return new JmsConfig(this);
        }
    }
}
