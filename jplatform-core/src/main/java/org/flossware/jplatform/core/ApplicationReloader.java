package org.flossware.jplatform.core;

import org.flossware.jplatform.api.*;
import org.flossware.jplatform.classloader.IsolatedClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles hot code reload for applications.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Creates new classloader with updated JARs</li>
 *   <li>Captures state from old application instance (if ReloadableApplication)</li>
 *   <li>Swaps classloader references atomically</li>
 *   <li>Starts new application instance with restored state</li>
 *   <li>Tracks classloader versions for potential rollback</li>
 * </ul>
 *
 * <p>Reload process:</p>
 * <ol>
 *   <li>Validate application is in RUNNING or STOPPED state</li>
 *   <li>Create new IsolatedClassLoader with updated descriptor</li>
 *   <li>If ReloadableApplication, call beforeReload() to capture state</li>
 *   <li>Stop old application instance</li>
 *   <li>Atomically swap classloader in ApplicationContext</li>
 *   <li>Load main class from new classloader</li>
 *   <li>Create new application instance</li>
 *   <li>If ReloadableApplication, call afterReload() with saved state</li>
 *   <li>Start new application instance</li>
 *   <li>Keep old classloader for potential rollback</li>
 * </ol>
 *
 * @since 2.0
 */
public class ApplicationReloader {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationReloader.class);

    private final Map<String, List<ClassLoaderVersion>> versionHistory;
    private final Map<String, Integer> currentVersions;
    private final ClassLoader platformSharedLoader;

    /**
     * Creates a new application reloader.
     *
     * @param platformSharedLoader the platform's shared classloader
     */
    public ApplicationReloader(ClassLoader platformSharedLoader) {
        this.versionHistory = new ConcurrentHashMap<>();
        this.currentVersions = new ConcurrentHashMap<>();
        this.platformSharedLoader = platformSharedLoader;
    }

    /**
     * Reloads an application with a new descriptor containing updated code.
     *
     * <p>This method handles the complete reload process including state preservation,
     * classloader swapping, and instance recreation.</p>
     *
     * @param applicationId the application to reload
     * @param newDescriptor the new application descriptor with updated classpath
     * @param currentContext the current application context (will be modified)
     * @param manager the application manager for lifecycle operations
     * @throws Exception if reload fails at any step
     */
    public synchronized void reload(String applicationId, ApplicationDescriptor newDescriptor,
                                   ApplicationContextImpl currentContext,
                                   ApplicationManager manager) throws Exception {

        logger.info("[{}] Starting hot reload", applicationId);

        ApplicationState state = currentContext.getState();
        if (state != ApplicationState.RUNNING && state != ApplicationState.STOPPED) {
            throw new IllegalStateException(
                    "Cannot reload from state: " + state + ". Must be RUNNING or STOPPED.");
        }

        boolean wasRunning = state == ApplicationState.RUNNING;
        IsolatedClassLoader newClassLoader = null;
        int newVersion = 0;

        try {
            // Step 1: Create new classloader with updated code
            newVersion = incrementVersion(applicationId);
            newClassLoader = IsolatedClassLoader.create(
                    applicationId,
                    newDescriptor,
                    platformSharedLoader
            );

            ClassLoaderVersion newClassLoaderVersion = new ClassLoaderVersion(newVersion, newClassLoader);
            addVersionHistory(applicationId, newClassLoaderVersion);

            logger.info("[{}] Created new classloader version {}", applicationId, newVersion);

            // Step 2: Capture state if application supports reload
            Map<String, Object> savedState = null;
            Object oldInstance = currentContext.getApplicationInstance();
            if (oldInstance instanceof ReloadableApplication) {
                logger.info("[{}] Capturing state from ReloadableApplication", applicationId);
                try {
                    savedState = ((ReloadableApplication) oldInstance).beforeReload();
                    logger.info("[{}] Captured state: {} entries", applicationId,
                            savedState != null ? savedState.size() : 0);
                } catch (Exception e) {
                    logger.error("[{}] Failed to capture state", applicationId, e);
                    throw new Exception("State capture failed", e);
                }
            }

            // Step 3: Stop old instance if running
            if (wasRunning) {
                logger.info("[{}] Stopping old instance", applicationId);
                if (oldInstance instanceof Application) {
                    ((Application) oldInstance).stop();
                }
            }

            // Step 4: Swap classloader and descriptor atomically
            ClassLoader oldClassLoader = currentContext.getClassLoader();
            currentContext.setClassLoaderAndDescriptor(newClassLoader, newDescriptor);

            logger.info("[{}] Swapped classloader from old to version {}", applicationId, newVersion);

            // Decrement reference count on old classloader
            decrementOldClassLoaderReferences(applicationId, newVersion);

            // Step 5: Load main class from new classloader
            Thread.currentThread().setContextClassLoader(newClassLoader);

            String mainClassName = newDescriptor.getMainClass();
            Class<?> mainClass = Class.forName(mainClassName, true, newClassLoader);

            // Step 6: Create new instance
            Object newInstance = mainClass.getDeclaredConstructor().newInstance();
            currentContext.setApplicationInstance(newInstance);

            logger.info("[{}] Created new application instance from updated code", applicationId);

            // Step 7: Restore state if ReloadableApplication
            if (newInstance instanceof ReloadableApplication) {
                logger.info("[{}] Restoring state to new instance", applicationId);
                try {
                    ((ReloadableApplication) newInstance).afterReload(currentContext, savedState);
                } catch (Exception e) {
                    logger.error("[{}] Failed to restore state", applicationId, e);
                    throw new Exception("State restoration failed", e);
                }
            }

            // Step 8: Start new instance if was running
            if (wasRunning) {
                logger.info("[{}] Starting new instance", applicationId);
                if (newInstance instanceof Application) {
                    ((Application) newInstance).start(currentContext);
                    currentContext.setState(ApplicationState.RUNNING);
                }
            } else {
                currentContext.setState(ApplicationState.STOPPED);
            }

            logger.info("[{}] Hot reload complete - now on version {}", applicationId, newVersion);

        } catch (Exception e) {
            logger.error("[{}] Hot reload failed", applicationId, e);

            // Cleanup new classloader on failure to prevent resource leak
            if (newClassLoader != null) {
                try {
                    newClassLoader.close();
                    logger.info("[{}] Closed failed classloader version {}", applicationId, newVersion);
                } catch (Exception closeEx) {
                    logger.warn("[{}] Failed to close classloader during rollback", applicationId, closeEx);
                }
            }

            throw new Exception("Hot reload failed for " + applicationId, e);
        } finally {
            Thread.currentThread().setContextClassLoader(platformSharedLoader);
        }
    }

    /**
     * Increments and returns the next version number for an application.
     *
     * @param applicationId the application identifier
     * @return the new version number
     */
    private int incrementVersion(String applicationId) {
        return currentVersions.compute(applicationId, (k, v) -> v == null ? 1 : v + 1);
    }

    /**
     * Adds a classloader version to the history.
     *
     * @param applicationId the application identifier
     * @param version the classloader version to add
     */
    private void addVersionHistory(String applicationId, ClassLoaderVersion version) {
        versionHistory.computeIfAbsent(applicationId, k -> new ArrayList<>()).add(version);
    }

    /**
     * Decrements reference counts on older classloader versions.
     *
     * <p>This allows old classloaders to be garbage collected if no longer referenced.
     * Keeps a few recent versions for potential rollback.</p>
     *
     * @param applicationId the application identifier
     * @param currentVersion the current version number
     */
    private void decrementOldClassLoaderReferences(String applicationId, int currentVersion) {
        List<ClassLoaderVersion> history = versionHistory.get(applicationId);
        if (history == null) {
            return;
        }

        for (ClassLoaderVersion version : history) {
            if (version.getVersion() < currentVersion) {
                version.decrementReference();
                if (version.canGarbageCollect()) {
                    logger.debug("[{}] ClassLoader version {} can be GC'd",
                            applicationId, version.getVersion());
                }
            }
        }

        // Keep only last 5 versions for rollback
        if (history.size() > 5) {
            history.subList(0, history.size() - 5).clear();
        }
    }

    /**
     * Returns the current version number for an application.
     *
     * @param applicationId the application identifier
     * @return the current version number, or 0 if never reloaded
     */
    public int getCurrentVersion(String applicationId) {
        return currentVersions.getOrDefault(applicationId, 0);
    }

    /**
     * Returns the version history for an application.
     *
     * @param applicationId the application identifier
     * @return list of classloader versions (oldest first)
     */
    public List<ClassLoaderVersion> getVersionHistory(String applicationId) {
        List<ClassLoaderVersion> history = versionHistory.get(applicationId);
        return history != null ? Collections.unmodifiableList(history) : Collections.emptyList();
    }

    /**
     * Clears version history for an application.
     * Called when application is undeployed.
     *
     * @param applicationId the application identifier
     */
    public void clearHistory(String applicationId) {
        versionHistory.remove(applicationId);
        currentVersions.remove(applicationId);
        logger.debug("[{}] Cleared reload history", applicationId);
    }
}
