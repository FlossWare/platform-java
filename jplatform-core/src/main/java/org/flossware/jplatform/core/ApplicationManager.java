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

package org.flossware.jplatform.core;

import org.flossware.jplatform.api.*;
import org.flossware.jplatform.classloader.IsolatedClassLoader;
import org.flossware.jplatform.monitoring.ApplicationResourceMonitor;
import org.flossware.jplatform.monitoring.ResourceEnforcer;
import org.flossware.jplatform.security.ApplicationSecurityPolicy;
import org.flossware.jplatform.storage.FileSystemVolumeManager;
import org.flossware.jplatform.threadpool.ManagedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Core platform component that manages application lifecycle.
 * Handles deployment, starting, stopping, and undeployment of applications.
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe using fine-grained per-application locking.
 * Each application has its own {@link ReentrantLock} allowing parallel operations on different
 * applications while serializing operations on the same application. The {@code applications}
 * and {@code applicationLocks} maps use {@link ConcurrentHashMap} for thread-safe access.
 */
public class ApplicationManager implements PlatformManager {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationManager.class);

    // Thread-safe maps - operations protected by per-application locks
    private final ConcurrentHashMap<String, ApplicationContextImpl> applications;
    private final ConcurrentHashMap<String, ReentrantLock> applicationLocks;
    private final ClassLoader platformSharedLoader;
    private final MessageBus sharedMessageBus;
    private final ServiceRegistry sharedServiceRegistry;
    private final DependencyResolver dependencyResolver;
    private final ApplicationReloader reloader;
    private final NativeProcessLauncher nativeProcessLauncher;
    private final ContainerLauncher containerLauncher;
    private final org.flossware.jplatform.vm.VmLauncher vmLauncher;

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
        this.applicationLocks = new ConcurrentHashMap<>();
        this.platformSharedLoader = ApplicationManager.class.getClassLoader();
        this.sharedMessageBus = messageBus;
        this.sharedServiceRegistry = serviceRegistry;
        this.dependencyResolver = new DependencyResolver(serviceRegistry);
        this.reloader = new ApplicationReloader(platformSharedLoader);
        this.nativeProcessLauncher = new NativeProcessLauncher();
        this.containerLauncher = new ContainerLauncher();

        // Initialize VM launcher
        org.flossware.jplatform.vm.VmLauncher tempVmLauncher = null;
        try {
            tempVmLauncher = new org.flossware.jplatform.vm.VmLauncher();
            logger.info("VM management initialized (libvirt connection established)");
        } catch (Exception e) {
            logger.warn("VM management not available (libvirt not accessible): {}", e.getMessage());
        }
        this.vmLauncher = tempVmLauncher;

        logger.info("ApplicationManager initialized with fine-grained locking, dependency resolution, hot reload, native process, container, and VM support");
    }

    /**
     * Deploys an application to the platform.
     * Creates isolated resources but does not start the application.
     *
     * @param descriptor the application descriptor containing configuration
     * @throws Exception if deployment fails
     * @throws IllegalStateException if application is already deployed
     */
    public void deploy(ApplicationDescriptor descriptor) throws Exception {
        String appId = descriptor.getApplicationId();

        // Create lock for this application if it doesn't exist
        ReentrantLock lock = applicationLocks.computeIfAbsent(appId, k -> new ReentrantLock());
        lock.lock();
        try {
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

            // Register security policy with enforcer (for StackWalker-based enforcement)
            org.flossware.jplatform.security.SecurityEnforcer.getInstance()
                .registerPolicy(classLoader, securityPolicy);
            logger.info("[{}] Registered security policy with enforcer", appId);

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

                // Create and attach resource enforcer
                ResourceEnforcer enforcer = new ResourceEnforcer(
                        appId,
                        resourceConfig,
                        threadGroup,  // for throttling
                        id -> {
                            try {
                                stop(id);
                            } catch (Exception e) {
                                logger.error("[{}] Enforcer failed to stop application", id, e);
                            }
                        },  // shutdown action
                        this::forceKill  // kill action
                );
                resourceMonitor.setEnforcer(enforcer);
                logger.info("[{}] Resource enforcer configured with grace period: {}",
                        appId, resourceConfig.getViolationGracePeriod());
            }

            // Create volume manager if volumes are defined
            VolumeManager volumeManager = null;
            if (!descriptor.getVolumes().isEmpty()) {
                try {
                    volumeManager = new FileSystemVolumeManager(appId, descriptor.getVolumes());
                    logger.info("[{}] Created volume manager with {} volumes", appId, descriptor.getVolumes().size());
                } catch (Exception e) {
                    logger.error("[{}] Failed to create volume manager", appId, e);
                    throw e;
                }
            }

            // Load native libraries if defined
            if (!descriptor.getNativeLibraries().isEmpty()) {
                try {
                    NativeLibraryLoader nativeLoader = new NativeLibraryLoader(appId);
                    java.nio.file.Path libDir = nativeLoader.loadLibraries(descriptor.getNativeLibraries());

                    // Note: We do NOT modify global java.library.path as that breaks isolation
                    // and doesn't work after JVM startup (ClassLoader caches the path).
                    // Applications must use System.load(absolutePath) to load these libraries.
                    // The library directory path is: libDir.toString()

                    logger.info("[{}] Loaded {} native libraries to {}",
                            appId, descriptor.getNativeLibraries().size(), libDir);
                } catch (Exception e) {
                    logger.error("[{}] Failed to load native libraries", appId, e);
                    throw e;
                }
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
                    .volumeManager(volumeManager)
                    .properties(descriptor.getProperties())
                    .build();

            applications.put(appId, context);

            // Register with dependency resolver
            dependencyResolver.addApplication(appId, descriptor);

            // Validate dependencies
            List<String> validationErrors = dependencyResolver.validateDependencies(appId);
            if (!validationErrors.isEmpty()) {
                logger.warn("[{}] Dependency validation warnings: {}", appId, validationErrors);
                // Note: We log warnings but don't fail deployment for missing optional dependencies
                // Required dependencies will cause start() to fail
            }

                logger.info("[{}] Application deployed successfully", appId);

            } catch (Exception e) {
                logger.error("[{}] Failed to deploy application", appId, e);
                throw new Exception("Failed to deploy application: " + appId, e);
            }
        } finally {
            lock.unlock();
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
    public void start(String applicationId) throws Exception {
        ReentrantLock lock = applicationLocks.get(applicationId);
        if (lock == null) {
            throw new IllegalStateException("Application not deployed: " + applicationId);
        }

        lock.lock();
        try {
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
                ApplicationDescriptor descriptor = context.getDescriptor();

                // Check if this is a virtual machine
                if (isVirtualMachine(descriptor)) {
                    if (vmLauncher == null) {
                        throw new IllegalStateException("VM management not available (libvirt not accessible)");
                    }

                    // Launch as VM
                    logger.info("[{}] Launching as virtual machine", applicationId);
                    org.flossware.jplatform.vm.VmLauncher.VmInfo vmInfo = vmLauncher.launch(applicationId, descriptor);
                    context.setVmInfo(vmInfo);

                    context.setState(ApplicationState.RUNNING);
                    logger.info("[{}] VM started successfully (UUID: {})", applicationId, vmInfo.getUuid());
                }
                // Check if this is a containerized application
                else if (isContainerized(descriptor)) {
                    // Launch as container
                    logger.info("[{}] Launching as container", applicationId);
                    ContainerLauncher.ContainerInfo containerInfo = containerLauncher.launch(applicationId, descriptor);
                    context.setContainerInfo(containerInfo);

                    context.setState(ApplicationState.RUNNING);
                    logger.info("[{}] Container started successfully (ID: {})", applicationId, containerInfo.getContainerId());
                }
                // Check if this is a native image application
                else if (descriptor.isNativeImage()) {
                    // Launch as native process
                    logger.info("[{}] Launching as native process", applicationId);
                    java.nio.file.Path workingDir = java.nio.file.Paths.get(
                            descriptor.getProperties().getOrDefault("native.workdir", "/var/jplatform/apps/" + applicationId)
                    );

                    // Create working directory if needed
                    java.nio.file.Files.createDirectories(workingDir);

                    Process process = nativeProcessLauncher.launch(applicationId, descriptor, workingDir);
                    context.setNativeProcess(process);

                    context.setState(ApplicationState.RUNNING);
                    logger.info("[{}] Native process started successfully (PID: {})", applicationId, process.pid());
                } else {
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
                }

            } catch (Exception e) {
                ApplicationContextImpl ctx = applications.get(applicationId);
                if (ctx != null) {
                    ctx.setState(ApplicationState.FAILED);
                }
                logger.error("[{}] Failed to start application", applicationId, e);
                throw new Exception("Failed to start application: " + applicationId, e);
            } finally {
                Thread.currentThread().setContextClassLoader(platformSharedLoader);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stops a running application.
     *
     * @param applicationId the application identifier
     * @throws Exception if stopping fails
     * @throws IllegalStateException if application is not deployed
     */
    public void stop(String applicationId) throws Exception {
        ReentrantLock lock = applicationLocks.get(applicationId);
        if (lock == null) {
            throw new IllegalStateException("Application not deployed: " + applicationId);
        }

        lock.lock();
        try {
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
                // Check if this is a virtual machine
                Optional<org.flossware.jplatform.vm.VmLauncher.VmInfo> vmInfo = context.getVmInfo();
                if (vmInfo.isPresent()) {
                    if (vmLauncher == null) {
                        throw new IllegalStateException("VM management not available (libvirt not accessible)");
                    }
                    // Stop VM (graceful shutdown)
                    logger.info("[{}] Stopping virtual machine", applicationId);
                    vmLauncher.stop(applicationId, vmInfo.get(), true);
                    context.setVmInfo(null);
                }
                // Check if this is a containerized application
                else {
                    Optional<ContainerLauncher.ContainerInfo> containerInfo = context.getContainerInfo();
                    if (containerInfo.isPresent()) {
                        // Stop container
                        logger.info("[{}] Stopping container", applicationId);
                        containerLauncher.stop(applicationId, containerInfo.get(), 10000); // 10 second grace period
                        context.setContainerInfo(null);
                    }
                    // Check if this is a native process
                    else {
                        Optional<Process> nativeProcess = context.getNativeProcess();
                        if (nativeProcess.isPresent()) {
                            // Stop native process
                            logger.info("[{}] Stopping native process", applicationId);
                            nativeProcessLauncher.stop(applicationId, nativeProcess.get(), 10000); // 10 second grace period
                            context.setNativeProcess(null);
                        } else {
                            // Stop JVM application
                            Object instance = context.getApplicationInstance();

                            if (instance instanceof Application) {
                                ((Application) instance).stop();
                            }
                        }
                    }
                }

                context.setState(ApplicationState.STOPPED);
                logger.info("[{}] Application stopped successfully", applicationId);

            } catch (Exception e) {
                context.setState(ApplicationState.FAILED);
                logger.error("[{}] Failed to stop application", applicationId, e);
                throw new Exception("Failed to stop application: " + applicationId, e);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forcefully terminates an application without graceful shutdown.
     * Used by ResourceEnforcer when KILL action is triggered.
     * Package-private for use by ResourceEnforcer.
     *
     * @param applicationId the application identifier
     */
    void forceKill(String applicationId) {
        ApplicationContextImpl context = applications.get(applicationId);

        if (context == null) {
            logger.warn("[{}] Cannot force kill: application not deployed", applicationId);
            return;
        }

        logger.warn("[{}] Force killing application due to resource enforcement", applicationId);

        try {
            // Immediately shutdown thread pool without waiting
            context.getThreadPool().shutdownNow();

            // Shutdown resource monitor
            if (context.getResourceMonitor() instanceof ApplicationResourceMonitor) {
                ((ApplicationResourceMonitor) context.getResourceMonitor()).shutdown();
            }

            // Close classloader
            if (context.getClassLoader() instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) context.getClassLoader()).close();
                } catch (Exception e) {
                    logger.error("[{}] Error closing classloader during force kill", applicationId, e);
                }
            }

            // Cleanup ClassLoader resources (best effort during force kill)
            if (context.getClassLoader() != null) {
                try {
                    org.flossware.jplatform.classloader.ClassLoaderCleanupUtil cleanup =
                        new org.flossware.jplatform.classloader.ClassLoaderCleanupUtil(
                            applicationId, context.getClassLoader());
                    cleanup.cleanupAll();
                } catch (Exception e) {
                    logger.error("[{}] Error during ClassLoader cleanup in force kill", applicationId, e);
                }
            }

            context.setState(ApplicationState.FAILED);
            logger.error("[{}] Application force killed", applicationId);

        } catch (Exception e) {
            logger.error("[{}] Error during force kill", applicationId, e);
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
    public void undeploy(String applicationId) throws Exception {
        ReentrantLock lock = applicationLocks.get(applicationId);
        if (lock == null) {
            throw new IllegalStateException("Application not deployed: " + applicationId);
        }

        lock.lock();
        try {
            ApplicationContextImpl context = applications.get(applicationId);

            if (context == null) {
                throw new IllegalStateException("Application not deployed: " + applicationId);
            }

            logger.info("[{}] Undeploying application", applicationId);

            try {
                // Stop if running (stop() will reacquire the lock, but ReentrantLock allows this)
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

            // Unregister security policy
            org.flossware.jplatform.security.SecurityEnforcer.getInstance()
                .unregisterPolicy(context.getClassLoader());
            logger.info("[{}] Unregistered security policy from enforcer", applicationId);

            // Comprehensive ClassLoader cleanup to prevent memory leaks
            if (context.getClassLoader() != null) {
                org.flossware.jplatform.classloader.ClassLoaderCleanupUtil cleanup =
                    new org.flossware.jplatform.classloader.ClassLoaderCleanupUtil(
                        applicationId, context.getClassLoader());
                cleanup.cleanupAll();

                // Detect leaks in non-production mode
                if (Boolean.getBoolean("jplatform.debug.detectLeaks")) {
                    cleanup.detectLeaks();
                }
            }

            // Cleanup ephemeral volumes
            context.getVolumeManager().ifPresent(vm -> {
                if (vm instanceof FileSystemVolumeManager) {
                    try {
                        ((FileSystemVolumeManager) vm).cleanupEphemeralVolumes();
                        logger.info("[{}] Cleaned up ephemeral volumes", applicationId);
                    } catch (Exception e) {
                        logger.error("[{}] Failed to cleanup ephemeral volumes", applicationId, e);
                    }
                }
            });

            // Cleanup native libraries
            if (!context.getDescriptor().getNativeLibraries().isEmpty()) {
                try {
                    NativeLibraryLoader nativeLoader = new NativeLibraryLoader(applicationId);
                    nativeLoader.cleanup();
                    logger.info("[{}] Cleaned up native libraries", applicationId);
                } catch (Exception e) {
                    logger.error("[{}] Failed to cleanup native libraries", applicationId, e);
                }
            }

            context.setState(ApplicationState.UNDEPLOYED);

            // Remove from dependency resolver
            dependencyResolver.removeApplication(applicationId);

            // Clear reload history
            reloader.clearHistory(applicationId);

                logger.info("[{}] Application undeployed successfully", applicationId);

            } catch (Exception e) {
                logger.error("[{}] Failed to undeploy application", applicationId, e);
                throw new Exception("Failed to undeploy application: " + applicationId, e);
            }
        } finally {
            lock.unlock();
            // Remove application and lock after unlock to ensure proper cleanup ordering
            applications.remove(applicationId);
            applicationLocks.remove(applicationId);
        }
    }

    /**
     * Reloads an application with new code without full undeploy/deploy cycle.
     *
     * <p>This performs a hot code reload by:</p>
     * <ul>
     *   <li>Creating a new classloader with updated JAR files</li>
     *   <li>Preserving application state (if ReloadableApplication)</li>
     *   <li>Swapping classloader atomically</li>
     *   <li>Creating new application instance</li>
     *   <li>Restoring state and restarting if was running</li>
     * </ul>
     *
     * @param applicationId the application to reload
     * @param newDescriptor the new descriptor with updated classpath
     * @throws Exception if reload fails
     * @throws IllegalStateException if application is not deployed
     */
    public void reload(String applicationId, ApplicationDescriptor newDescriptor) throws Exception {
        ReentrantLock lock = applicationLocks.get(applicationId);
        if (lock == null) {
            throw new IllegalStateException("Application not deployed: " + applicationId);
        }

        lock.lock();
        try {
            ApplicationContextImpl context = applications.get(applicationId);

            if (context == null) {
                throw new IllegalStateException("Application not deployed: " + applicationId);
            }

            logger.info("[{}] Initiating hot code reload", applicationId);

            try {
                reloader.reload(applicationId, newDescriptor, context, this);

                // Update dependency resolver with new descriptor
                dependencyResolver.removeApplication(applicationId);
                dependencyResolver.addApplication(applicationId, newDescriptor);

                logger.info("[{}] Hot reload successful - now on version {}",
                        applicationId, reloader.getCurrentVersion(applicationId));

            } catch (Exception e) {
                logger.error("[{}] Hot reload failed", applicationId, e);
                throw new Exception("Failed to reload application: " + applicationId, e);
            }
        } finally {
            lock.unlock();
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
     * Starts all deployed applications in dependency order.
     *
     * <p>Applications with no dependencies are started first, followed by applications
     * that depend on them. If any application fails to start, its dependent applications
     * will not be started.</p>
     *
     * <p>Note: Each application is locked individually during its start operation.
     * This allows parallel starts when dependency ordering permits.</p>
     *
     * @throws Exception if startup order cannot be determined (circular dependencies)
     */
    public void startAll() throws Exception {
        logger.info("Starting all applications in dependency order");

        List<String> startupOrder = dependencyResolver.getStartupOrder();
        logger.info("Startup order: {}", startupOrder);

        Set<String> runningApps = new HashSet<>();

        for (String appId : startupOrder) {
            ApplicationContextImpl context = applications.get(appId);
            if (context == null) {
                logger.warn("[{}] Application not deployed, skipping", appId);
                continue;
            }

            ApplicationState state = context.getState();
            if (state == ApplicationState.RUNNING) {
                logger.debug("[{}] Already running", appId);
                runningApps.add(appId);
                continue;
            }

            if (state != ApplicationState.DEPLOYED && state != ApplicationState.STOPPED) {
                logger.warn("[{}] Cannot start from state {}", appId, state);
                continue;
            }

            try {
                // start() method now handles its own locking
                start(appId);
                runningApps.add(appId);
                logger.info("[{}] Started successfully", appId);
            } catch (Exception e) {
                logger.error("[{}] Failed to start, skipping dependent applications", appId, e);
                // Don't start apps that depend on this one
            }
        }

        logger.info("Startup complete: {} applications running", runningApps.size());
    }

    /**
     * Returns the recommended startup order for all deployed applications.
     *
     * @return list of application IDs in dependency order
     * @throws IllegalStateException if circular dependencies exist
     */
    public List<String> getStartupOrder() {
        return dependencyResolver.getStartupOrder();
    }

    /**
     * Returns the applications that depend on the given application.
     *
     * @param applicationId the application identifier
     * @return set of dependent application IDs
     */
    public Set<String> getDependentApplications(String applicationId) {
        return dependencyResolver.getDependentApplications(applicationId);
    }

    /**
     * Shuts down the platform by undeploying all applications.
     * Errors during individual application undeployment are logged but do not stop the shutdown process.
     *
     * <p>Note: Each application is locked individually during its undeploy operation.</p>
     */
    public void shutdown() {
        logger.info("Shutting down platform");

        // Get a snapshot of application IDs to avoid concurrent modification
        List<String> appIds = new ArrayList<>(applications.keySet());

        for (String appId : appIds) {
            try {
                // undeploy() method now handles its own locking
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

    /**
     * Checks if an application descriptor specifies containerized deployment.
     *
     * @param descriptor the application descriptor
     * @return true if the application should run in a container
     */
    private boolean isContainerized(ApplicationDescriptor descriptor) {
        Map<String, String> properties = descriptor.getProperties();
        return properties.containsKey("container.runtime") || properties.containsKey("container.image");
    }

    /**
     * Checks if an application descriptor specifies VM deployment.
     *
     * @param descriptor the application descriptor
     * @return true if the application should run as a virtual machine
     */
    private boolean isVirtualMachine(ApplicationDescriptor descriptor) {
        Map<String, String> properties = descriptor.getProperties();
        return properties.containsKey("vm.disk") || properties.containsKey("vm.vcpu") || properties.containsKey("vm.memory");
    }
}
