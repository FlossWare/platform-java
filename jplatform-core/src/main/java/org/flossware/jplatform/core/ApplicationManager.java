package org.flossware.jplatform.core;

import org.flossware.jplatform.api.*;
import org.flossware.jplatform.classloader.IsolatedClassLoader;
import org.flossware.jplatform.monitoring.ApplicationResourceMonitor;
import org.flossware.jplatform.security.ApplicationSecurityPolicy;
import org.flossware.jplatform.threadpool.ManagedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core platform component that manages application lifecycle.
 * Handles deployment, starting, stopping, and undeployment of applications.
 */
public class ApplicationManager {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationManager.class);

    private final Map<String, ApplicationContextImpl> applications;
    private final ClassLoader platformSharedLoader;
    private final MessageBus sharedMessageBus;
    private final ServiceRegistry sharedServiceRegistry;

    /**
     * Creates a new application manager without messaging support.
     */
    public ApplicationManager() {
        this(null, null);
    }

    /**
     * Creates a new application manager with optional messaging support.
     *
     * @param messageBus the shared message bus for inter-app communication, or null to disable
     * @param serviceRegistry the shared service registry, or null to disable
     */
    public ApplicationManager(MessageBus messageBus, ServiceRegistry serviceRegistry) {
        this.applications = new ConcurrentHashMap<>();
        this.platformSharedLoader = ApplicationManager.class.getClassLoader();
        this.sharedMessageBus = messageBus;
        this.sharedServiceRegistry = serviceRegistry;
        logger.info("ApplicationManager initialized");
    }

    /**
     * Deploys an application to the platform.
     * Creates isolated resources but does not start the application.
     *
     * @param descriptor the application descriptor containing configuration
     * @throws Exception if deployment fails
     * @throws IllegalStateException if application is already deployed
     */
    public synchronized void deploy(ApplicationDescriptor descriptor) throws Exception {
        String appId = descriptor.getApplicationId();

        if (applications.containsKey(appId)) {
            throw new IllegalStateException("Application already deployed: " + appId);
        }

        logger.info("[{}] Deploying application: {}", appId, descriptor.getName());

        try {
            // Create isolated classloader
            IsolatedClassLoader classLoader = IsolatedClassLoader.create(
                    appId,
                    descriptor,
                    platformSharedLoader
            );

            // Create thread pool
            ManagedThreadPool threadPool = new ManagedThreadPool(
                    appId,
                    descriptor.getThreadPoolConfig()
            );

            // Create security policy
            ApplicationSecurityPolicy securityPolicy = new ApplicationSecurityPolicy(
                    appId,
                    descriptor.getSecurityConfig()
            );

            // Create resource monitor
            ThreadGroup threadGroup = new ThreadGroup(appId + "-threads");
            ApplicationResourceMonitor resourceMonitor = new ApplicationResourceMonitor(
                    appId,
                    threadGroup
            );

            // Set quota if configured
            ResourceConfig resourceConfig = descriptor.getResourceConfig();
            if (resourceConfig.getMaxHeapMB().isPresent() ||
                resourceConfig.getMaxThreads().isPresent() ||
                resourceConfig.getMaxCpuTimeSeconds().isPresent()) {

                ResourceQuota.Builder quotaBuilder = ResourceQuota.builder();
                resourceConfig.getMaxHeapMB().ifPresent(mb -> quotaBuilder.maxHeapBytes(mb * 1024 * 1024));
                resourceConfig.getMaxThreads().ifPresent(quotaBuilder::maxThreadCount);
                resourceConfig.getMaxCpuTimeSeconds().ifPresent(sec -> quotaBuilder.maxCpuTimeNanos(sec * 1_000_000_000L));

                resourceMonitor.setQuota(quotaBuilder.build());
            }

            // Create application context
            ApplicationContextImpl context = ApplicationContextImpl.builder()
                    .applicationId(appId)
                    .descriptor(descriptor)
                    .classLoader(classLoader)
                    .threadPool(threadPool)
                    .securityPolicy(securityPolicy)
                    .resourceMonitor(resourceMonitor)
                    .messageBus(descriptor.isEnableMessaging() ? sharedMessageBus : null)
                    .serviceRegistry(descriptor.isEnableMessaging() ? sharedServiceRegistry : null)
                    .properties(descriptor.getProperties())
                    .build();

            applications.put(appId, context);

            logger.info("[{}] Application deployed successfully", appId);

        } catch (Exception e) {
            logger.error("[{}] Failed to deploy application", appId, e);
            throw new Exception("Failed to deploy application: " + appId, e);
        }
    }

    /**
     * Starts a deployed application.
     * Loads the main class and invokes start() method.
     *
     * @param applicationId the application identifier
     * @throws Exception if starting fails
     * @throws IllegalStateException if application is not deployed or in wrong state
     */
    public synchronized void start(String applicationId) throws Exception {
        ApplicationContextImpl context = applications.get(applicationId);

        if (context == null) {
            throw new IllegalStateException("Application not deployed: " + applicationId);
        }

        if (context.getState() != ApplicationState.DEPLOYED && context.getState() != ApplicationState.STOPPED) {
            throw new IllegalStateException(
                    "Application cannot be started from state: " + context.getState());
        }

        logger.info("[{}] Starting application", applicationId);
        context.setState(ApplicationState.STARTING);

        try {
            // Load main class using application's classloader
            ClassLoader appClassLoader = context.getClassLoader();
            Thread.currentThread().setContextClassLoader(appClassLoader);

            String mainClassName = getMainClassName(applicationId);
            Class<?> mainClass = Class.forName(mainClassName, true, appClassLoader);

            // Instantiate application
            Object instance = mainClass.getDeclaredConstructor().newInstance();
            context.setApplicationInstance(instance);

            // If implements Application interface, call start()
            if (instance instanceof Application) {
                ((Application) instance).start(context);
            } else {
                // Try to invoke main(String[]) method
                try {
                    Method mainMethod = mainClass.getMethod("main", String[].class);
                    String[] args = new String[0];
                    mainMethod.invoke(null, (Object) args);
                } catch (NoSuchMethodException e) {
                    logger.warn("[{}] Main class does not implement Application interface and has no main() method", applicationId);
                }
            }

            context.setState(ApplicationState.RUNNING);
            logger.info("[{}] Application started successfully", applicationId);

        } catch (Exception e) {
            context.setState(ApplicationState.FAILED);
            logger.error("[{}] Failed to start application", applicationId, e);
            throw new Exception("Failed to start application: " + applicationId, e);
        } finally {
            Thread.currentThread().setContextClassLoader(platformSharedLoader);
        }
    }

    /**
     * Stops a running application.
     *
     * @param applicationId the application identifier
     * @throws Exception if stopping fails
     * @throws IllegalStateException if application is not deployed
     */
    public synchronized void stop(String applicationId) throws Exception {
        ApplicationContextImpl context = applications.get(applicationId);

        if (context == null) {
            throw new IllegalStateException("Application not deployed: " + applicationId);
        }

        if (context.getState() != ApplicationState.RUNNING) {
            logger.warn("[{}] Application is not running, current state: {}", applicationId, context.getState());
            return;
        }

        logger.info("[{}] Stopping application", applicationId);
        context.setState(ApplicationState.STOPPING);

        try {
            Object instance = context.getApplicationInstance();

            if (instance instanceof Application) {
                ((Application) instance).stop();
            }

            context.setState(ApplicationState.STOPPED);
            logger.info("[{}] Application stopped successfully", applicationId);

        } catch (Exception e) {
            context.setState(ApplicationState.FAILED);
            logger.error("[{}] Failed to stop application", applicationId, e);
            throw new Exception("Failed to stop application: " + applicationId, e);
        }
    }

    /**
     * Undeploys an application from the platform.
     * Stops the application if running and releases all resources.
     *
     * @param applicationId the application identifier
     * @throws Exception if undeployment fails
     * @throws IllegalStateException if application is not deployed
     */
    public synchronized void undeploy(String applicationId) throws Exception {
        ApplicationContextImpl context = applications.get(applicationId);

        if (context == null) {
            throw new IllegalStateException("Application not deployed: " + applicationId);
        }

        logger.info("[{}] Undeploying application", applicationId);

        try {
            // Stop if running
            if (context.getState() == ApplicationState.RUNNING) {
                stop(applicationId);
            }

            // Shutdown thread pool
            context.getThreadPool().shutdown();

            // Shutdown resource monitor
            if (context.getResourceMonitor() instanceof ApplicationResourceMonitor) {
                ((ApplicationResourceMonitor) context.getResourceMonitor()).shutdown();
            }

            // Close classloader if closeable
            if (context.getClassLoader() instanceof AutoCloseable) {
                ((AutoCloseable) context.getClassLoader()).close();
            }

            context.setState(ApplicationState.UNDEPLOYED);
            applications.remove(applicationId);

            logger.info("[{}] Application undeployed successfully", applicationId);

        } catch (Exception e) {
            logger.error("[{}] Failed to undeploy application", applicationId, e);
            throw new Exception("Failed to undeploy application: " + applicationId, e);
        }
    }

    /**
     * Returns the application context for a deployed application.
     *
     * @param applicationId the application identifier
     * @return the application context, or null if not deployed
     */
    public ApplicationContext getApplicationContext(String applicationId) {
        return applications.get(applicationId);
    }

    /**
     * Lists all deployed applications and their current states.
     *
     * @return a map of application IDs to their current states
     */
    public Map<String, ApplicationState> listApplications() {
        Map<String, ApplicationState> result = new ConcurrentHashMap<>();
        applications.forEach((id, context) -> result.put(id, context.getState()));
        return result;
    }

    /**
     * Shuts down the platform by undeploying all applications.
     * Errors during individual application undeployment are logged but do not stop the shutdown process.
     */
    public synchronized void shutdown() {
        logger.info("Shutting down platform");

        for (String appId : applications.keySet()) {
            try {
                undeploy(appId);
            } catch (Exception e) {
                logger.error("Error undeploying application during shutdown: {}", appId, e);
            }
        }

        logger.info("Platform shutdown complete");
    }

    private String getMainClassName(String applicationId) {
        ApplicationContextImpl context = applications.get(applicationId);
        if (context == null) {
            throw new IllegalStateException("Application not found: " + applicationId);
        }
        return context.getDescriptor().getMainClass();
    }
}
