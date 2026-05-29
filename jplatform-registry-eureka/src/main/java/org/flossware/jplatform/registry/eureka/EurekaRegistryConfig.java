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

package org.flossware.jplatform.registry.eureka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for Eureka-based service registry.
 * Provides connection settings for Netflix Eureka server and service registration parameters.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * EurekaRegistryConfig config = EurekaRegistryConfig.builder()
 *     .addServiceUrl("http://localhost:8761/eureka")
 *     .appName("my-service")
 *     .instanceId("my-service-1")
 *     .build();
 * }</pre>
 *
 * @see EurekaServiceRegistry
 * @since 1.1
 */
public class EurekaRegistryConfig {

    private final List<String> serviceUrls;
    private final String appName;
    private final String instanceId;
    private final int renewalIntervalSeconds;
    private final int leaseExpirationSeconds;
    private final boolean registerWithEureka;
    private final boolean fetchRegistry;

    /**
     * Package-private constructor for builder.
     *
     * @param builder the builder containing configuration values
     */
    EurekaRegistryConfig(Builder builder) {
        this.serviceUrls = Collections.unmodifiableList(new ArrayList<>(builder.serviceUrls));
        this.appName = builder.appName;
        this.instanceId = builder.instanceId;
        this.renewalIntervalSeconds = builder.renewalIntervalSeconds;
        this.leaseExpirationSeconds = builder.leaseExpirationSeconds;
        this.registerWithEureka = builder.registerWithEureka;
        this.fetchRegistry = builder.fetchRegistry;
    }

    /**
     * Returns the Eureka service URLs.
     *
     * @return immutable list of service URLs (default: ["http://localhost:8761/eureka"])
     */
    public List<String> getServiceUrls() {
        return serviceUrls;
    }

    /**
     * Returns the application name.
     * This is the name under which services are registered in Eureka.
     *
     * @return the application name
     */
    public String getAppName() {
        return appName;
    }

    /**
     * Returns the instance ID.
     * Unique identifier for this service instance.
     *
     * @return the instance ID, or null to use Eureka's default
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Returns the renewal interval in seconds.
     * How often to send heartbeats to Eureka.
     *
     * @return the renewal interval (default: 30)
     */
    public int getRenewalIntervalSeconds() {
        return renewalIntervalSeconds;
    }

    /**
     * Returns the lease expiration duration in seconds.
     * How long Eureka waits before removing a service that stops sending heartbeats.
     *
     * @return the lease expiration duration (default: 90)
     */
    public int getLeaseExpirationSeconds() {
        return leaseExpirationSeconds;
    }

    /**
     * Returns whether to register this instance with Eureka.
     *
     * @return true to register (default: true)
     */
    public boolean isRegisterWithEureka() {
        return registerWithEureka;
    }

    /**
     * Returns whether to fetch registry information from Eureka.
     *
     * @return true to fetch registry (default: true)
     */
    public boolean isFetchRegistry() {
        return fetchRegistry;
    }

    /**
     * Creates a new builder for EurekaRegistryConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for EurekaRegistryConfig instances.
     * Provides a fluent API for configuration.
     */
    public static class Builder {
        private final List<String> serviceUrls = new ArrayList<>();
        private String appName = "jplatform-app";
        private String instanceId;
        private int renewalIntervalSeconds = 30;
        private int leaseExpirationSeconds = 90;
        private boolean registerWithEureka = true;
        private boolean fetchRegistry = true;

        /**
         * Constructs a builder with default service URL.
         */
        public Builder() {
            serviceUrls.add("http://localhost:8761/eureka");
        }

        /**
         * Adds a Eureka service URL.
         *
         * @param serviceUrl the service URL (e.g., "http://localhost:8761/eureka")
         * @return this builder for chaining
         */
        public Builder addServiceUrl(String serviceUrl) {
            if (this.serviceUrls.size() == 1 && "http://localhost:8761/eureka".equals(this.serviceUrls.get(0))) {
                this.serviceUrls.clear();
            }
            this.serviceUrls.add(serviceUrl);
            return this;
        }

        /**
         * Sets the Eureka service URLs, replacing any existing ones.
         *
         * @param serviceUrls the list of service URLs
         * @return this builder for chaining
         */
        public Builder serviceUrls(List<String> serviceUrls) {
            this.serviceUrls.clear();
            this.serviceUrls.addAll(serviceUrls);
            return this;
        }

        /**
         * Sets the application name.
         *
         * @param appName the application name
         * @return this builder for chaining
         */
        public Builder appName(String appName) {
            this.appName = appName;
            return this;
        }

        /**
         * Sets the instance ID.
         *
         * @param instanceId the unique instance identifier
         * @return this builder for chaining
         */
        public Builder instanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        /**
         * Sets the renewal interval in seconds.
         *
         * @param renewalIntervalSeconds the interval (must be at least 1)
         * @return this builder for chaining
         */
        public Builder renewalIntervalSeconds(int renewalIntervalSeconds) {
            if (renewalIntervalSeconds < 1) {
                throw new IllegalArgumentException("Renewal interval must be at least 1 second");
            }
            this.renewalIntervalSeconds = renewalIntervalSeconds;
            return this;
        }

        /**
         * Sets the lease expiration duration in seconds.
         *
         * @param leaseExpirationSeconds the duration (must be at least 1)
         * @return this builder for chaining
         */
        public Builder leaseExpirationSeconds(int leaseExpirationSeconds) {
            if (leaseExpirationSeconds < 1) {
                throw new IllegalArgumentException("Lease expiration must be at least 1 second");
            }
            this.leaseExpirationSeconds = leaseExpirationSeconds;
            return this;
        }

        /**
         * Sets whether to register with Eureka.
         *
         * @param registerWithEureka true to register
         * @return this builder for chaining
         */
        public Builder registerWithEureka(boolean registerWithEureka) {
            this.registerWithEureka = registerWithEureka;
            return this;
        }

        /**
         * Sets whether to fetch registry from Eureka.
         *
         * @param fetchRegistry true to fetch
         * @return this builder for chaining
         */
        public Builder fetchRegistry(boolean fetchRegistry) {
            this.fetchRegistry = fetchRegistry;
            return this;
        }

        /**
         * Builds the EurekaRegistryConfig instance.
         *
         * @return a new EurekaRegistryConfig with the configured values
         */
        public EurekaRegistryConfig build() {
            if (serviceUrls.isEmpty()) {
                throw new IllegalStateException("At least one service URL must be specified");
            }
            if (appName == null || appName.trim().isEmpty()) {
                throw new IllegalStateException("App name must be specified");
            }
            return new EurekaRegistryConfig(this);
        }
    }
}
