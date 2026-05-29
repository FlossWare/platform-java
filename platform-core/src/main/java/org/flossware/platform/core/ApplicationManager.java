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

package org.flossware.platform.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.flossware.platform.api.Application;
import org.flossware.platform.api.ApplicationContext;
import org.flossware.platform.api.ApplicationDescriptor;
import org.flossware.platform.api.ApplicationState;
import org.flossware.platform.api.MessageBus;
import org.flossware.platform.api.PlatformManager;
import org.flossware.platform.api.ResourceConfig;
import org.flossware.platform.api.ResourceQuota;
import org.flossware.platform.api.ServiceRegistry;
import org.flossware.platform.api.VolumeManager;
import org.flossware.platform.classloader.IsolatedClassLoader;
import org.flossware.platform.monitoring.ApplicationResourceMonitor;
import org.flossware.platform.monitoring.ResourceEnforcer;
import org.flossware.platform.security.ApplicationSecurityPolicy;
import org.flossware.platform.storage.FileSystemVolumeManager;
import org.flossware.platform.threadpool.ManagedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core platform component that manages application lifecycle. Handles deployment, starting,
 * stopping, and undeployment of applications.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe using fine-grained per-application locking.
 * Each application has its own {@link ReentrantLock} allowing parallel operations on different
 * applications while serializing operations on the same application. The {@code applications} and
 * {@code applicationLocks} maps use {@link ConcurrentHashMap} for thread-safe access.
 */
public class ApplicationManager implements PlatformManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationManager.class);

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
  private final org.flossware.platform.vm.VmLauncher vmLauncher;
  private final RestartPolicyParser restartPolicyParser;
  private final HealthCheckConfigParser healthCheckConfigParser;

  /** Creates a new application manager without messaging support. */
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
    this.restartPolicyParser = new RestartPolicyParser();
    this.healthCheckConfigParser = new HealthCheckConfigParser();

    // Initialize VM launcher
    org.flossware.platform.vm.VmLauncher tempVmLauncher = null;
    try {
      tempVmLauncher = new org.flossware.platform.vm.VmLauncher();
      LOGGER.info("VM management initialized (libvirt connection established)");
    } catch (Exception e) {
      LOGGER.warn("VM management not available (libvirt not accessible): {}", e.getMessage());
    }
    this.vmLauncher = tempVmLauncher;

    LOGGER.info(
        "ApplicationManager initialized with fine-grained locking, "
            + "dependency resolution, hot reload, native process, container, and VM support");
  }

  /**
   * Deploys an application to the platform. Creates isolated resources but does not start the
   * application.
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

      LOGGER.info("[{}] Deploying application: {}", appId, descriptor.getName());

      try {
        // Create isolated classloader
        IsolatedClassLoader classLoader =
            IsolatedClassLoader.create(appId, descriptor, platformSharedLoader);

        // Create thread pool
        ManagedThreadPool threadPool =
            new ManagedThreadPool(appId, descriptor.getThreadPoolConfig());

        // Create security policy
        ApplicationSecurityPolicy securityPolicy =
            new ApplicationSecurityPolicy(appId, descriptor.getSecurityConfig());

        // Register security policy with enforcer (for StackWalker-based enforcement)
        org.flossware.platform.security.SecurityEnforcer.getInstance()
            .registerPolicy(classLoader, securityPolicy);
        LOGGER.info("[{}] Registered security policy with enforcer", appId);

        // Create resource monitor
        ThreadGroup threadGroup = new ThreadGroup(appId + "-threads");
        ApplicationResourceMonitor resourceMonitor =
            new ApplicationResourceMonitor(appId, threadGroup);

        // Set quota if configured
        ResourceConfig resourceConfig = descriptor.getResourceConfig();
        if (resourceConfig.getMaxHeapMB().isPresent()
            || resourceConfig.getMaxThreads().isPresent()
            || resourceConfig.getMaxCpuTimeSeconds().isPresent()) {

          ResourceQuota.Builder quotaBuilder = ResourceQuota.builder();
          resourceConfig
              .getMaxHeapMB()
              .ifPresent(mb -> quotaBuilder.maxHeapBytes(mb * 1024 * 1024));
          resourceConfig.getMaxThreads().ifPresent(quotaBuilder::maxThreadCount);
          resourceConfig
              .getMaxCpuTimeSeconds()
              .ifPresent(sec -> quotaBuilder.maxCpuTimeNanos(sec * 1_000_000_000L));

          resourceMonitor.setQuota(quotaBuilder.build());

          // Create and attach resource enforcer
          ResourceEnforcer enforcer =
              new ResourceEnforcer(
                  appId,
                  resourceConfig,
                  threadGroup, // for throttling
                  id -> {
                    try {
                      stop(id);
                    } catch (Exception e) {
                      LOGGER.error("[{}] Enforcer failed to stop application", id, e);
                    }
                  }, // shutdown action
                  this::forceKill // kill action
                  );
          resourceMonitor.setEnforcer(enforcer);
          LOGGER.info(
              "[{}] Resource enforcer configured with grace period: {}",
              appId,
              resourceConfig.getViolationGracePeriod());
        }

        // Create volume manager if volumes are defined
        VolumeManager volumeManager = null;
        if (!descriptor.getVolumes().isEmpty()) {
          try {
            volumeManager = new FileSystemVolumeManager(appId, descriptor.getVolumes());
            LOGGER.info(
                "[{}] Created volume manager with {} volumes",
                appId,
                descriptor.getVolumes().size());
          } catch (Exception e) {
            LOGGER.error("[{}] Failed to create volume manager", appId, e);
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

            LOGGER.info(
                "[{}] Loaded {} native libraries to {}",
                appId,
                descriptor.getNativeLibraries().size(),
                libDir);
          } catch (Exception e) {
            LOGGER.error("[{}] Failed to load native libraries", appId, e);
            throw e;
          }
        }

        // Create application context
        ApplicationContextImpl context =
            ApplicationContextImpl.builder()
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

        // Create and configure restart manager if restart policy is configured
        Optional<org.flossware.platform.api.RestartPolicy> restartPolicyOpt =
            restartPolicyParser.parse(descriptor);
        if (restartPolicyOpt.isPresent()) {
          org.flossware.platform.api.RestartPolicy restartPolicy = restartPolicyOpt.get();
          RestartManager restartManager = new RestartManager(context, restartPolicy, this);
          context.setRestartManager(restartManager);
          restartManager.start();
          LOGGER.info("[{}] Restart manager configured: {}", appId, restartPolicy);
        }

        // Create and configure health checker if health checks are enabled
        Optional<HealthChecker.HealthCheckConfig> healthCheckConfigOpt =
            healthCheckConfigParser.parse(descriptor);
        if (healthCheckConfigOpt.isPresent()) {
          HealthChecker.HealthCheckConfig healthCheckConfig = healthCheckConfigOpt.get();
          HealthChecker healthChecker = new HealthChecker(context, healthCheckConfig);
          context.setHealthChecker(healthChecker);
          healthChecker.start();
          LOGGER.info(
              "[{}] Health checks configured: type={}, interval={}s",
              appId,
              healthCheckConfig.getType(),
              healthCheckConfig.getIntervalSeconds());
        }

        // Register with dependency resolver
        dependencyResolver.addApplication(appId, descriptor);

        // Validate dependencies
        List<String> validationErrors = dependencyResolver.validateDependencies(appId);
        if (!validationErrors.isEmpty()) {
          LOGGER.warn("[{}] Dependency validation warnings: {}", appId, validationErrors);
          // Note: We log warnings but don't fail deployment for missing optional dependencies
          // Required dependencies will cause start() to fail
        }

        LOGGER.info("[{}] Application deployed successfully", appId);

      } catch (Exception e) {
        LOGGER.error("[{}] Failed to deploy application", appId, e);
        throw new Exception("Failed to deploy application: " + appId, e);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Starts a deployed application. Loads the main class and invokes start() method.
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

      if (context.getState() != ApplicationState.DEPLOYED
          && context.getState() != ApplicationState.STOPPED) {
        throw new IllegalStateException(
            "Application cannot be started from state: " + context.getState());
      }

      LOGGER.info("[{}] Starting application", applicationId);
      context.setState(ApplicationState.STARTING);

      try {
        ApplicationDescriptor descriptor = context.getDescriptor();

        // Check if this is a virtual machine
        if (isVirtualMachine(descriptor)) {
          if (vmLauncher == null) {
            throw new IllegalStateException("VM management not available (libvirt not accessible)");
          }

          // Launch as VM
          LOGGER.info("[{}] Launching as virtual machine", applicationId);
          org.flossware.platform.vm.VmLauncher.VmInfo vmInfo =
              vmLauncher.launch(applicationId, descriptor);
          context.setVmInfo(vmInfo);

          context.setState(ApplicationState.RUNNING);
          LOGGER.info("[{}] VM started successfully (UUID: {})", applicationId, vmInfo.getUuid());
        } else if (isContainerized(descriptor)) {
          // Check if this is a containerized application
          // Launch as container
          LOGGER.info("[{}] Launching as container", applicationId);
          ContainerLauncher.ContainerInfo containerInfo =
              containerLauncher.launch(applicationId, descriptor);
          context.setContainerInfo(containerInfo);

          // Monitor container exit for restart manager
          context
              .getRestartManager()
              .ifPresent(
                  restartManager -> {
                    Process containerProcess = containerInfo.getProcess();
                    if (containerProcess != null) {
                      context
                          .getThreadPool()
                          .submit(
                              () -> {
                                try {
                                  int exitCode = containerProcess.waitFor();
                                  LOGGER.info(
                                      "[{}] Container exited with code {}",
                                      applicationId,
                                      exitCode);
                                  restartManager.onApplicationExit(exitCode);
                                } catch (InterruptedException e) {
                                  Thread.currentThread().interrupt();
                                  LOGGER.debug("[{}] Container monitor interrupted", applicationId);
                                }
                              });
                    }
                  });

          context.setState(ApplicationState.RUNNING);
          LOGGER.info(
              "[{}] Container started successfully (ID: {})",
              applicationId,
              containerInfo.getContainerId());
        } else if (descriptor.isNativeImage()) {
          // Check if this is a native image application
          // Launch as native process
          LOGGER.info("[{}] Launching as native process", applicationId);
          java.nio.file.Path workingDir =
              java.nio.file.Paths.get(
                  descriptor
                      .getProperties()
                      .getOrDefault("native.workdir", "/var/platform/apps/" + applicationId));

          // Create working directory if needed
          java.nio.file.Files.createDirectories(workingDir);

          Process process = nativeProcessLauncher.launch(applicationId, descriptor, workingDir);
          context.setNativeProcess(process);

          // Monitor process exit for restart manager
          context
              .getRestartManager()
              .ifPresent(
                  restartManager -> {
                    context
                        .getThreadPool()
                        .submit(
                            () -> {
                              try {
                                int exitCode = process.waitFor();
                                LOGGER.info(
                                    "[{}] Native process exited with code {}",
                                    applicationId,
                                    exitCode);
                                restartManager.onApplicationExit(exitCode);
                              } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                LOGGER.debug("[{}] Process monitor interrupted", applicationId);
                              }
                            });
                  });

          context.setState(ApplicationState.RUNNING);
          LOGGER.info(
              "[{}] Native process started successfully (PID: {})", applicationId, process.pid());
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
              LOGGER.warn(
                  "[{}] Main class does not implement Application interface and has no main() method",
                  applicationId);
            }
          }

          context.setState(ApplicationState.RUNNING);
          LOGGER.info("[{}] Application started successfully", applicationId);
        }

      } catch (Exception e) {
        ApplicationContextImpl ctx = applications.get(applicationId);
        if (ctx != null) {
          ctx.setState(ApplicationState.FAILED);
        }
        LOGGER.error("[{}] Failed to start application", applicationId, e);
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
        LOGGER.warn(
            "[{}] Application is not running, current state: {}",
            applicationId,
            context.getState());
        return;
      }

      LOGGER.info("[{}] Stopping application", applicationId);
      context.setState(ApplicationState.STOPPING);

      try {
        // Check if this is a virtual machine
        Optional<org.flossware.platform.vm.VmLauncher.VmInfo> vmInfo = context.getVmInfo();
        if (vmInfo.isPresent()) {
          if (vmLauncher == null) {
            throw new IllegalStateException("VM management not available (libvirt not accessible)");
          }
          // Stop VM (graceful shutdown)
          LOGGER.info("[{}] Stopping virtual machine", applicationId);
          vmLauncher.stop(applicationId, vmInfo.get(), true);
          context.setVmInfo(null);
        } else {
          // Check if this is a containerized application
          Optional<ContainerLauncher.ContainerInfo> containerInfo = context.getContainerInfo();
          if (containerInfo.isPresent()) {
            // Stop container
            LOGGER.info("[{}] Stopping container", applicationId);
            containerLauncher.stop(
                applicationId, containerInfo.get(), 10000); // 10 second grace period
            context.setContainerInfo(null);
          } else {
            // Check if this is a native process
            Optional<Process> nativeProcess = context.getNativeProcess();
            if (nativeProcess.isPresent()) {
              // Stop native process
              LOGGER.info("[{}] Stopping native process", applicationId);
              nativeProcessLauncher.stop(
                  applicationId, nativeProcess.get(), 10000); // 10 second grace period
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
        LOGGER.info("[{}] Application stopped successfully", applicationId);

      } catch (Exception e) {
        context.setState(ApplicationState.FAILED);
        LOGGER.error("[{}] Failed to stop application", applicationId, e);
        throw new Exception("Failed to stop application: " + applicationId, e);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Forcefully terminates an application without graceful shutdown. Used by ResourceEnforcer when
   * KILL action is triggered. Package-private for use by ResourceEnforcer.
   *
   * @param applicationId the application identifier
   */
  void forceKill(String applicationId) {
    ApplicationContextImpl context = applications.get(applicationId);

    if (context == null) {
      LOGGER.warn("[{}] Cannot force kill: application not deployed", applicationId);
      return;
    }

    LOGGER.warn("[{}] Force killing application due to resource enforcement", applicationId);

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
          LOGGER.error("[{}] Error closing classloader during force kill", applicationId, e);
        }
      }

      // Cleanup ClassLoader resources (best effort during force kill)
      if (context.getClassLoader() != null) {
        try {
          org.flossware.platform.classloader.ClassLoaderCleanupUtil cleanup =
              new org.flossware.platform.classloader.ClassLoaderCleanupUtil(
                  applicationId, context.getClassLoader());
          cleanup.cleanupAll();
        } catch (Exception e) {
          LOGGER.error("[{}] Error during ClassLoader cleanup in force kill", applicationId, e);
        }
      }

      context.setState(ApplicationState.FAILED);
      LOGGER.error("[{}] Application force killed", applicationId);

    } catch (Exception e) {
      LOGGER.error("[{}] Error during force kill", applicationId, e);
    }
  }

  /**
   * Undeploys an application from the platform. Stops the application if running and releases all
   * resources.
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

      LOGGER.info("[{}] Undeploying application", applicationId);

      try {
        // Stop if running (stop() will reacquire the lock, but ReentrantLock allows this)
        if (context.getState() == ApplicationState.RUNNING) {
          stop(applicationId);
        }

        // Shutdown thread pool
        context.getThreadPool().shutdown();

        // Shutdown restart manager if present
        context
            .getRestartManager()
            .ifPresent(
                restartManager -> {
                  restartManager.stop();
                  LOGGER.info("[{}] Restart manager stopped", applicationId);
                });

        // Shutdown health checker if present
        context
            .getHealthChecker()
            .ifPresent(
                healthChecker -> {
                  healthChecker.stop();
                  LOGGER.info("[{}] Health checker stopped", applicationId);
                });

        // Shutdown resource monitor
        if (context.getResourceMonitor() instanceof ApplicationResourceMonitor) {
          ((ApplicationResourceMonitor) context.getResourceMonitor()).shutdown();
        }

        // Close classloader if closeable
        if (context.getClassLoader() instanceof AutoCloseable) {
          ((AutoCloseable) context.getClassLoader()).close();
        }

        // Unregister security policy
        org.flossware.platform.security.SecurityEnforcer.getInstance()
            .unregisterPolicy(context.getClassLoader());
        LOGGER.info("[{}] Unregistered security policy from enforcer", applicationId);

        // Comprehensive ClassLoader cleanup to prevent memory leaks
        if (context.getClassLoader() != null) {
          org.flossware.platform.classloader.ClassLoaderCleanupUtil cleanup =
              new org.flossware.platform.classloader.ClassLoaderCleanupUtil(
                  applicationId, context.getClassLoader());
          cleanup.cleanupAll();

          // Detect leaks in non-production mode
          if (Boolean.getBoolean("platform.debug.detectLeaks")) {
            cleanup.detectLeaks();
          }
        }

        // Cleanup ephemeral volumes
        context
            .getVolumeManager()
            .ifPresent(
                vm -> {
                  if (vm instanceof FileSystemVolumeManager) {
                    try {
                      ((FileSystemVolumeManager) vm).cleanupEphemeralVolumes();
                      LOGGER.info("[{}] Cleaned up ephemeral volumes", applicationId);
                    } catch (Exception e) {
                      LOGGER.error("[{}] Failed to cleanup ephemeral volumes", applicationId, e);
                    }
                  }
                });

        // Cleanup native libraries
        if (!context.getDescriptor().getNativeLibraries().isEmpty()) {
          try {
            NativeLibraryLoader nativeLoader = new NativeLibraryLoader(applicationId);
            nativeLoader.cleanup();
            LOGGER.info("[{}] Cleaned up native libraries", applicationId);
          } catch (Exception e) {
            LOGGER.error("[{}] Failed to cleanup native libraries", applicationId, e);
          }
        }

        context.setState(ApplicationState.UNDEPLOYED);

        // Remove from dependency resolver
        dependencyResolver.removeApplication(applicationId);

        // Clear reload history
        reloader.clearHistory(applicationId);

        LOGGER.info("[{}] Application undeployed successfully", applicationId);

      } catch (Exception e) {
        LOGGER.error("[{}] Failed to undeploy application", applicationId, e);
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
   * <p>This performs a hot code reload by:
   *
   * <ul>
   *   <li>Creating a new classloader with updated JAR files
   *   <li>Preserving application state (if ReloadableApplication)
   *   <li>Swapping classloader atomically
   *   <li>Creating new application instance
   *   <li>Restoring state and restarting if was running
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

      LOGGER.info("[{}] Initiating hot code reload", applicationId);

      try {
        reloader.reload(applicationId, newDescriptor, context, this);

        // Update dependency resolver with new descriptor
        dependencyResolver.removeApplication(applicationId);
        dependencyResolver.addApplication(applicationId, newDescriptor);

        LOGGER.info(
            "[{}] Hot reload successful - now on version {}",
            applicationId,
            reloader.getCurrentVersion(applicationId));

      } catch (Exception e) {
        LOGGER.error("[{}] Hot reload failed", applicationId, e);
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
   * <p>Applications with no dependencies are started first, followed by applications that depend on
   * them. If any application fails to start, its dependent applications will not be started.
   *
   * <p>Note: Each application is locked individually during its start operation. This allows
   * parallel starts when dependency ordering permits.
   *
   * @throws Exception if startup order cannot be determined (circular dependencies)
   */
  public void startAll() throws Exception {
    LOGGER.info("Starting all applications in dependency order");

    List<String> startupOrder = dependencyResolver.getStartupOrder();
    LOGGER.info("Startup order: {}", startupOrder);

    Set<String> runningApps = new HashSet<>();

    for (String appId : startupOrder) {
      ApplicationContextImpl context = applications.get(appId);
      if (context == null) {
        LOGGER.warn("[{}] Application not deployed, skipping", appId);
        continue;
      }

      ApplicationState state = context.getState();
      if (state == ApplicationState.RUNNING) {
        LOGGER.debug("[{}] Already running", appId);
        runningApps.add(appId);
        continue;
      }

      if (state != ApplicationState.DEPLOYED && state != ApplicationState.STOPPED) {
        LOGGER.warn("[{}] Cannot start from state {}", appId, state);
        continue;
      }

      try {
        // start() method now handles its own locking
        start(appId);
        runningApps.add(appId);
        LOGGER.info("[{}] Started successfully", appId);
      } catch (Exception e) {
        LOGGER.error("[{}] Failed to start, skipping dependent applications", appId, e);
        // Don't start apps that depend on this one
      }
    }

    LOGGER.info("Startup complete: {} applications running", runningApps.size());
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
   * Shuts down the platform by undeploying all applications. Errors during individual application
   * undeployment are logged but do not stop the shutdown process.
   *
   * <p>Note: Each application is locked individually during its undeploy operation.
   */
  public void shutdown() {
    LOGGER.info("Shutting down platform");

    // Get a snapshot of application IDs to avoid concurrent modification
    List<String> appIds = new ArrayList<>(applications.keySet());

    for (String appId : appIds) {
      try {
        // undeploy() method now handles its own locking
        undeploy(appId);
      } catch (Exception e) {
        LOGGER.error("Error undeploying application during shutdown: {}", appId, e);
      }
    }

    LOGGER.info("Platform shutdown complete");
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
    return properties.containsKey("vm.disk")
        || properties.containsKey("vm.vcpu")
        || properties.containsKey("vm.memory");
  }
}
