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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flossware.jplatform.api.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Netflix Eureka-based implementation of ServiceRegistry.
 * Stores service registrations in Eureka service discovery server.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Registers services with Eureka using REST API</li>
 *   <li>Sends periodic heartbeats to maintain registrations</li>
 *   <li>Maintains local cache for fast service lookups</li>
 *   <li>Publishes service availability while keeping local instances</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All mutable state is protected
 * by concurrent collections and volatile flags.
 *
 * @since 1.1
 */
public class EurekaServiceRegistry implements ServiceRegistry, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(EurekaServiceRegistry.class);

    private final EurekaRegistryConfig config;
    private final Map<Class<?>, List<Object>> localServices;
    private final Map<String, String> registeredInstances;
    private final ObjectMapper mapper;
    private ScheduledExecutorService heartbeatExecutor;
    private volatile boolean started = false;

    /**
     * Constructs a new Eureka service registry.
     *
     * @param config the Eureka registry configuration
     */
    public EurekaServiceRegistry(EurekaRegistryConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        this.config = config;
        this.localServices = new ConcurrentHashMap<>();
        this.registeredInstances = new ConcurrentHashMap<>();
        this.mapper = new ObjectMapper();
    }

    /**
     * Package-private constructor for testing.
     *
     * @param config the configuration
     * @param heartbeatExecutor the heartbeat executor
     */
    EurekaServiceRegistry(EurekaRegistryConfig config, ScheduledExecutorService heartbeatExecutor) {
        this.config = config;
        this.localServices = new ConcurrentHashMap<>();
        this.registeredInstances = new ConcurrentHashMap<>();
        this.mapper = new ObjectMapper();
        this.heartbeatExecutor = heartbeatExecutor;
        this.started = true;
    }

    /**
     * Starts the Eureka service registry.
     * Must be called before using the registry.
     */
    public void start() {
        if (started) {
            return;
        }

        if (config.isRegisterWithEureka()) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
            heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeats,
                config.getRenewalIntervalSeconds(),
                config.getRenewalIntervalSeconds(),
                TimeUnit.SECONDS
            );
        }

        started = true;
        logger.info("Eureka service registry started with app name: {}", config.getAppName());
    }

    @Override
    public <T> void registerService(Class<T> serviceInterface, T implementation) {
        if (serviceInterface == null || implementation == null) {
            throw new IllegalArgumentException("Service interface and implementation must not be null");
        }

        localServices.computeIfAbsent(serviceInterface, k -> new CopyOnWriteArrayList<>())
            .add(implementation);

        if (config.isRegisterWithEureka() && started) {
            registerWithEureka(serviceInterface);
        }

        logger.info("Registered service {} locally", serviceInterface.getName());
    }

    @Override
    public <T> Optional<T> getService(Class<T> serviceInterface) {
        List<T> services = getAllServices(serviceInterface);
        return services.isEmpty() ? Optional.empty() : Optional.of(services.get(0));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getAllServices(Class<T> serviceInterface) {
        List<Object> services = localServices.get(serviceInterface);
        if (services == null) {
            return Collections.emptyList();
        }
        return (List<T>) new ArrayList<>(services);
    }

    @Override
    public void unregisterService(Class<?> serviceInterface, Object implementation) {
        if (serviceInterface == null || implementation == null) {
            return;
        }

        List<Object> services = localServices.get(serviceInterface);
        if (services != null) {
            services.remove(implementation);
            if (services.isEmpty()) {
                localServices.remove(serviceInterface);
            }
        }

        logger.info("Unregistered service {} from local registry", serviceInterface.getName());
    }

    @Override
    public void close() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (config.isRegisterWithEureka()) {
            deregisterFromEureka();
        }

        localServices.clear();
        registeredInstances.clear();
        started = false;

        logger.info("Eureka service registry closed");
    }

    /**
     * Sends heartbeats for all registered instances.
     */
    void sendHeartbeats() {
        for (String instanceId : registeredInstances.keySet()) {
            try {
                String url = getEurekaUrl() + "/apps/" + config.getAppName() + "/" + instanceId;
                sendHttpRequest(url, "PUT", null);
                logger.debug("Sent heartbeat for instance: {}", instanceId);
            } catch (Exception e) {
                logger.error("Failed to send heartbeat for instance: " + instanceId, e);
            }
        }
    }

    /**
     * Returns the Eureka configuration.
     *
     * @return the configuration
     */
    public EurekaRegistryConfig getConfig() {
        return config;
    }

    /**
     * Returns the local services map.
     *
     * @return the local services map
     */
    Map<Class<?>, List<Object>> getLocalServices() {
        return localServices;
    }

    private void registerWithEureka(Class<?> serviceInterface) {
        try {
            String instanceId = config.getInstanceId() != null ?
                config.getInstanceId() :
                config.getAppName() + "-" + UUID.randomUUID();

            Map<String, Object> instance = new HashMap<>();
            instance.put("instanceId", instanceId);
            instance.put("app", config.getAppName());
            instance.put("ipAddr", "127.0.0.1");
            instance.put("status", "UP");
            instance.put("port", Collections.singletonMap("$", 8080));
            instance.put("dataCenterInfo", Collections.singletonMap("@class",
                "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo"));
            instance.put("metadata", Collections.singletonMap("service-class", serviceInterface.getName()));

            Map<String, Object> registration = Collections.singletonMap("instance", instance);
            String json = mapper.writeValueAsString(registration);

            String url = getEurekaUrl() + "/apps/" + config.getAppName();
            sendHttpRequest(url, "POST", json);

            registeredInstances.put(instanceId, serviceInterface.getName());

            logger.info("Registered with Eureka: {} as {}", serviceInterface.getName(), instanceId);
        } catch (Exception e) {
            logger.error("Failed to register with Eureka: " + serviceInterface.getName(), e);
        }
    }

    private void deregisterFromEureka() {
        for (String instanceId : registeredInstances.keySet()) {
            try {
                String url = getEurekaUrl() + "/apps/" + config.getAppName() + "/" + instanceId;
                sendHttpRequest(url, "DELETE", null);
                logger.info("Deregistered instance from Eureka: {}", instanceId);
            } catch (Exception e) {
                logger.error("Failed to deregister instance: " + instanceId, e);
            }
        }
    }

    private String getEurekaUrl() {
        return config.getServiceUrls().get(0);
    }

    private void sendHttpRequest(String urlString, String method, String body) throws Exception {
        URL url = new URI(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (body != null) {
            conn.setDoOutput(true);
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            logger.warn("Eureka request failed: {} {} - response code: {}", method, urlString, responseCode);
        }

        conn.disconnect();
    }
}
