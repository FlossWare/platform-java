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

package org.flossware.jplatform.metrics.jmx;

import org.flossware.jplatform.api.ApplicationContext;
import org.flossware.jplatform.api.JmxExporterConfig;
import org.flossware.jplatform.api.MetricsExporter;
import org.flossware.jplatform.api.PlatformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JMX metrics exporter that exposes application metrics via Java Management Extensions (JMX).
 * Registers MBeans for each application and optionally creates an RMI registry for remote monitoring.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Automatic MBean registration for deployed applications</li>
 *   <li>Optional RMI registry for remote JMX access</li>
 *   <li>Thread-safe registration and unregistration</li>
 *   <li>Customizable JMX domain for MBean ObjectNames</li>
 * </ul>
 *
 * <p>ObjectName pattern:</p>
 * <pre>{@code
 * {domain}:type=Application,id={applicationId}
 * }</pre>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create configuration
 * JmxExporterConfig config = JmxExporterConfig.builder()
 *     .enabled(true)
 *     .port(9999)
 *     .domain("org.flossware.jplatform")
 *     .build();
 *
 * // Create exporter with application manager
 * ApplicationManager manager = new ApplicationManager();
 * JmxMetricsExporter exporter = new JmxMetricsExporter(config, manager);
 *
 * // Start the exporter
 * exporter.start();
 *
 * // Register applications
 * exporter.registerApplication("my-app", applicationContext);
 *
 * // Access via JMX client (jconsole, JVisualVM):
 * // service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi
 * }</pre>
 *
 * <p>Thread safety: This class is thread-safe. All registration operations are protected
 * by internal synchronization using ConcurrentHashMap.</p>
 *
 * @see MetricsExporter
 * @see ApplicationMBean
 * @see org.flossware.jplatform.api.JmxExporterConfig
 */
public class JmxMetricsExporter implements MetricsExporter {

    private static final Logger logger = LoggerFactory.getLogger(JmxMetricsExporter.class);

    private final JmxExporterConfig config;
    private final PlatformManager manager;
    private final MBeanServer mBeanServer;
    private final Map<String, ObjectName> registeredMBeans;
    private volatile boolean running;

    /**
     * Constructs a new JMX metrics exporter with the specified configuration.
     * Uses the platform MBean server for MBean registration.
     *
     * @param config the JMX exporter configuration
     * @param manager the platform manager for lifecycle operations
     * @throws IllegalArgumentException if config or manager is null
     */
    public JmxMetricsExporter(JmxExporterConfig config, PlatformManager manager) {
        if (config == null) {
            throw new IllegalArgumentException("JmxExporterConfig cannot be null");
        }
        if (manager == null) {
            throw new IllegalArgumentException("PlatformManager cannot be null");
        }

        this.config = config;
        this.manager = manager;
        this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
        this.registeredMBeans = new ConcurrentHashMap<>();
        this.running = false;

        logger.info("JmxMetricsExporter created with domain: {}, port: {}",
                config.getDomain(), config.getPort());
    }

    @Override
    public void start() throws Exception {
        if (running) {
            logger.warn("JmxMetricsExporter is already running");
            return;
        }

        logger.info("Starting JmxMetricsExporter");

        try {
            // Create RMI registry if port is configured
            if (config.getPort() > 0) {
                logger.info("Creating RMI registry on port {}", config.getPort());
                LocateRegistry.createRegistry(config.getPort());

                // Set system properties for JMX remote access
                System.setProperty("com.sun.management.jmxremote.port", String.valueOf(config.getPort()));
                System.setProperty("com.sun.management.jmxremote.authenticate", "false");
                System.setProperty("com.sun.management.jmxremote.ssl", "false");

                logger.info("RMI registry created successfully. JMX endpoint: service:jmx:rmi:///jndi/rmi://localhost:{}/jmxrmi",
                        config.getPort());
            }

            running = true;
            logger.info("JmxMetricsExporter started successfully");

        } catch (Exception e) {
            logger.error("Failed to start JmxMetricsExporter", e);
            throw new Exception("Failed to start JMX metrics exporter", e);
        }
    }

    @Override
    public void stop() throws Exception {
        if (!running) {
            logger.warn("JmxMetricsExporter is not running");
            return;
        }

        logger.info("Stopping JmxMetricsExporter");

        try {
            // Unregister all MBeans
            for (Map.Entry<String, ObjectName> entry : registeredMBeans.entrySet()) {
                String appId = entry.getKey();
                ObjectName objectName = entry.getValue();

                try {
                    mBeanServer.unregisterMBean(objectName);
                    logger.debug("Unregistered MBean for application: {}", appId);
                } catch (InstanceNotFoundException e) {
                    logger.warn("MBean not found during shutdown for application: {}", appId);
                } catch (Exception e) {
                    logger.error("Error unregistering MBean for application: {}", appId, e);
                }
            }

            registeredMBeans.clear();
            running = false;

            logger.info("JmxMetricsExporter stopped successfully");

        } catch (Exception e) {
            logger.error("Failed to stop JmxMetricsExporter", e);
            throw new Exception("Failed to stop JMX metrics exporter", e);
        }
    }

    @Override
    public void registerApplication(String applicationId, ApplicationContext context) {
        if (!running) {
            logger.warn("Cannot register application: JmxMetricsExporter is not running");
            return;
        }

        if (applicationId == null || applicationId.isEmpty()) {
            throw new IllegalArgumentException("Application ID cannot be null or empty");
        }
        if (context == null) {
            throw new IllegalArgumentException("ApplicationContext cannot be null");
        }

        logger.info("[{}] Registering application with JMX", applicationId);

        try {
            // Create ObjectName with configured domain
            // Quote applicationId to escape JMX special characters (:=,"*?)
            String quotedAppId = ObjectName.quote(applicationId);
            String objectNameStr = String.format("%s:type=Application,id=%s",
                    config.getDomain(), quotedAppId);
            ObjectName objectName = new ObjectName(objectNameStr);

            // Check if already registered
            if (registeredMBeans.containsKey(applicationId)) {
                logger.warn("[{}] Application is already registered, unregistering first", applicationId);
                unregisterApplication(applicationId);
            }

            // Create and register MBean wrapped in StandardMBean
            ApplicationMBeanImpl mbeanImpl = new ApplicationMBeanImpl(applicationId, context, manager);
            StandardMBean mbean = new StandardMBean(mbeanImpl, ApplicationMBean.class);
            mBeanServer.registerMBean(mbean, objectName);

            registeredMBeans.put(applicationId, objectName);

            logger.info("[{}] Application registered successfully with JMX: {}", applicationId, objectNameStr);

        } catch (InstanceAlreadyExistsException e) {
            logger.error("[{}] MBean already exists with this ObjectName", applicationId, e);
            throw new RuntimeException("Failed to register application: MBean already exists", e);
        } catch (Exception e) {
            logger.error("[{}] Failed to register application with JMX", applicationId, e);
            throw new RuntimeException("Failed to register application with JMX: " + applicationId, e);
        }
    }

    @Override
    public void unregisterApplication(String applicationId) {
        if (applicationId == null || applicationId.isEmpty()) {
            throw new IllegalArgumentException("Application ID cannot be null or empty");
        }

        logger.info("[{}] Unregistering application from JMX", applicationId);

        ObjectName objectName = registeredMBeans.remove(applicationId);

        if (objectName == null) {
            logger.warn("[{}] Application is not registered with JMX", applicationId);
            return;
        }

        try {
            mBeanServer.unregisterMBean(objectName);
            logger.info("[{}] Application unregistered successfully from JMX", applicationId);

        } catch (InstanceNotFoundException e) {
            logger.warn("[{}] MBean not found during unregistration", applicationId);
        } catch (Exception e) {
            logger.error("[{}] Failed to unregister application from JMX", applicationId, e);
            throw new RuntimeException("Failed to unregister application from JMX: " + applicationId, e);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() throws Exception {
        if (running) {
            stop();
        }
    }

    /**
     * Returns the number of currently registered applications.
     *
     * @return the number of registered MBeans
     */
    public int getRegisteredApplicationCount() {
        return registeredMBeans.size();
    }

    /**
     * Returns the JMX domain used for MBean ObjectNames.
     *
     * @return the JMX domain
     */
    public String getDomain() {
        return config.getDomain();
    }

    /**
     * Returns the RMI registry port if configured.
     *
     * @return the port number, or 0 if not configured
     */
    public int getPort() {
        return config.getPort();
    }
}
