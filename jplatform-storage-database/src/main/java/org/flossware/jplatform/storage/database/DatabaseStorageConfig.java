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

package org.flossware.jplatform.storage.database;

/**
 * Configuration for JDBC-based storage.
 * Stores volume metadata in a relational database.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * DatabaseStorageConfig config = DatabaseStorageConfig.builder()
 *     .jdbcUrl("jdbc:h2:mem:volumes")
 *     .username("sa")
 *     .password("")
 *     .build();
 * }</pre>
 *
 * @see DatabaseVolumeManager
 * @since 1.1
 */
public class DatabaseStorageConfig {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String driverClassName;
    private final int maxPoolSize;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    DatabaseStorageConfig(Builder builder) {
        this.jdbcUrl = builder.jdbcUrl;
        this.username = builder.username;
        this.password = builder.password;
        this.driverClassName = builder.driverClassName;
        this.maxPoolSize = builder.maxPoolSize;
    }

    /**
     * Returns the JDBC URL.
     *
     * @return the JDBC connection URL
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Returns the database username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the database password.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the JDBC driver class name.
     *
     * @return the driver class name
     */
    public String getDriverClassName() {
        return driverClassName;
    }

    /**
     * Returns the maximum connection pool size.
     *
     * @return the max pool size (default: 10)
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * Creates a new builder for DatabaseStorageConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DatabaseStorageConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private String jdbcUrl;
        private String username = "sa";
        private String password = "";
        private String driverClassName = "org.h2.Driver";
        private int maxPoolSize = 10;

        /**
         * Sets the JDBC URL.
         *
         * @param jdbcUrl the JDBC connection URL
         * @return this builder for chaining
         */
        public Builder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }

        /**
         * Sets the database username.
         *
         * @param username the username
         * @return this builder for chaining
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the database password.
         *
         * @param password the password
         * @return this builder for chaining
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the JDBC driver class name.
         *
         * @param driverClassName the driver class name
         * @return this builder for chaining
         */
        public Builder driverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
            return this;
        }

        /**
         * Sets the maximum connection pool size.
         *
         * @param maxPoolSize the max pool size (must be at least 1)
         * @return this builder for chaining
         */
        public Builder maxPoolSize(int maxPoolSize) {
            if (maxPoolSize < 1) {
                throw new IllegalArgumentException("Max pool size must be at least 1");
            }
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        /**
         * Builds the DatabaseStorageConfig instance.
         *
         * @return a new DatabaseStorageConfig with the configured values
         * @throws IllegalStateException if required fields are missing
         */
        public DatabaseStorageConfig build() {
            if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
                throw new IllegalStateException("JDBC URL must be specified");
            }
            return new DatabaseStorageConfig(this);
        }
    }
}
