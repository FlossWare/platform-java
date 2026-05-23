package org.flossware.jplatform.monitoring;

import org.flossware.jplatform.api.EnforcementAction;
import org.flossware.jplatform.api.ResourceConfig;
import org.flossware.jplatform.api.ResourceQuota;
import org.flossware.jplatform.api.ResourceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Enforces resource quota limits by taking configured actions when violations occur.
 *
 * <p>This component implements the enforcement side of resource management.
 * While {@link ApplicationResourceMonitor} handles monitoring and detection,
 * ResourceEnforcer handles the action-taking when quotas are exceeded.</p>
 *
 * <p>Enforcement actions range from passive notification to active throttling
 * or shutdown of the violating application. Each resource type (CPU, memory, threads)
 * can have its own enforcement policy.</p>
 *
 * <p>Grace periods prevent transient resource spikes from triggering enforcement.
 * Actions are only taken after consecutive violations over multiple monitoring cycles.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ResourceEnforcer enforcer = new ResourceEnforcer(
 *     "my-app",
 *     resourceConfig,
 *     applicationId -> applicationManager.stop(applicationId),  // shutdown action
 *     applicationId -> applicationManager.undeploy(applicationId)  // kill action
 * );
 *
 * // Called by monitoring thread when quota exceeded
 * enforcer.enforceQuota(quota, snapshot);
 * }</pre>
 *
 * @since 2.0
 */
public class ResourceEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(ResourceEnforcer.class);

    private final String applicationId;
    private final ResourceConfig config;
    private final EnforcementPolicy policy;
    private final ThreadGroup threadGroup;

    // Actions provided by ApplicationManager
    private final Consumer<String> shutdownAction;
    private final Consumer<String> killAction;
    private final Consumer<ThreadGroup> throttleAction;

    /**
     * Creates a new resource enforcer for an application.
     *
     * @param applicationId the application identifier
     * @param config the resource configuration with enforcement actions
     * @param threadGroup the application's thread group (for throttling)
     * @param shutdownAction callback to gracefully stop the application
     * @param killAction callback to forcefully terminate the application
     */
    public ResourceEnforcer(String applicationId,
                            ResourceConfig config,
                            ThreadGroup threadGroup,
                            Consumer<String> shutdownAction,
                            Consumer<String> killAction) {
        this(applicationId, config, threadGroup, shutdownAction, killAction, null);
    }

    /**
     * Creates a new resource enforcer with throttling support.
     *
     * @param applicationId the application identifier
     * @param config the resource configuration with enforcement actions
     * @param threadGroup the application's thread group (for throttling)
     * @param shutdownAction callback to gracefully stop the application
     * @param killAction callback to forcefully terminate the application
     * @param throttleAction callback to throttle thread group execution
     */
    public ResourceEnforcer(String applicationId,
                            ResourceConfig config,
                            ThreadGroup threadGroup,
                            Consumer<String> shutdownAction,
                            Consumer<String> killAction,
                            Consumer<ThreadGroup> throttleAction) {
        this.applicationId = applicationId;
        this.config = config;
        this.threadGroup = threadGroup;
        this.shutdownAction = shutdownAction;
        this.killAction = killAction;
        this.throttleAction = throttleAction;
        this.policy = new EnforcementPolicy(applicationId, config.getViolationGracePeriod());

        logger.info("ResourceEnforcer initialized for application: {}, grace period: {}",
                applicationId, config.getViolationGracePeriod());
    }

    /**
     * Enforces resource quotas by checking current usage and taking action if needed.
     *
     * <p>This method should be called by the monitoring thread when a quota
     * violation is detected. It implements grace period logic and triggers
     * the appropriate enforcement action based on configuration.</p>
     *
     * @param quota the resource quota to enforce
     * @param snapshot the current resource snapshot
     */
    public void enforceQuota(ResourceQuota quota, ResourceSnapshot snapshot) {
        // Check each resource type
        enforceHeapQuota(quota, snapshot);
        enforceCpuQuota(quota, snapshot);
        enforceThreadQuota(quota, snapshot);
    }

    /**
     * Enforces heap memory quota.
     * Checks if current heap usage exceeds the configured limit and takes
     * the configured enforcement action after grace period.
     *
     * @param quota the resource quota
     * @param snapshot the current snapshot
     */
    private void enforceHeapQuota(ResourceQuota quota, ResourceSnapshot snapshot) {
        quota.getMaxHeapBytes().ifPresent(maxHeap -> {
            if (snapshot.getHeapUsedBytes() > maxHeap) {
                if (policy.recordViolation("heap")) {
                    logger.warn("Application {} exceeded heap quota: {} > {} (violations: {})",
                            applicationId,
                            snapshot.getHeapUsedBytes(),
                            maxHeap,
                            policy.getViolationCount("heap"));

                    executeAction(config.getMemoryEnforcementAction(), "heap");
                    policy.clearViolation("heap");  // Reset after action taken
                }
            } else {
                policy.clearViolation("heap");
            }
        });
    }

    /**
     * Enforces CPU time quota.
     * Checks if cumulative CPU time exceeds the configured limit and takes
     * the configured enforcement action after grace period.
     *
     * @param quota the resource quota
     * @param snapshot the current snapshot
     */
    private void enforceCpuQuota(ResourceQuota quota, ResourceSnapshot snapshot) {
        quota.getMaxCpuTimeNanos().ifPresent(maxCpu -> {
            if (snapshot.getCpuTimeNanos() > maxCpu) {
                if (policy.recordViolation("cpu")) {
                    logger.warn("Application {} exceeded CPU quota: {}ns > {}ns (violations: {})",
                            applicationId,
                            snapshot.getCpuTimeNanos(),
                            maxCpu,
                            policy.getViolationCount("cpu"));

                    executeAction(config.getCpuEnforcementAction(), "cpu");
                    policy.clearViolation("cpu");  // Reset after action taken
                }
            } else {
                policy.clearViolation("cpu");
            }
        });
    }

    /**
     * Enforces thread count quota.
     * Checks if current thread count exceeds the configured limit and takes
     * the configured enforcement action after grace period.
     *
     * @param quota the resource quota
     * @param snapshot the current snapshot
     */
    private void enforceThreadQuota(ResourceQuota quota, ResourceSnapshot snapshot) {
        quota.getMaxThreadCount().ifPresent(maxThreads -> {
            if (snapshot.getThreadCount() > maxThreads) {
                if (policy.recordViolation("threads")) {
                    logger.warn("Application {} exceeded thread quota: {} > {} (violations: {})",
                            applicationId,
                            snapshot.getThreadCount(),
                            maxThreads,
                            policy.getViolationCount("threads"));

                    executeAction(config.getThreadEnforcementAction(), "threads");
                    policy.clearViolation("threads");  // Reset after action taken
                }
            } else {
                policy.clearViolation("threads");
            }
        });
    }

    /**
     * Executes the configured enforcement action.
     *
     * @param action the enforcement action to take
     * @param resourceType the resource type that was violated
     */
    private void executeAction(EnforcementAction action, String resourceType) {
        logger.info("Enforcing {} action for application {} due to {} quota violation",
                action, applicationId, resourceType);

        try {
            switch (action) {
                case NOTIFY:
                    // Already logged warning above, no additional action
                    break;

                case THROTTLE:
                    if (throttleAction != null && threadGroup != null) {
                        logger.info("Throttling application {}", applicationId);
                        throttleAction.accept(threadGroup);
                    } else {
                        logger.warn("Throttle action requested but not configured for {} " +
                                "(throttleAction={}, threadGroup={})",
                                applicationId, throttleAction != null, threadGroup != null);
                    }
                    break;

                case SHUTDOWN:
                    logger.warn("Shutting down application {} due to quota violation", applicationId);
                    if (shutdownAction != null) {
                        shutdownAction.accept(applicationId);
                    }
                    break;

                case KILL:
                    logger.error("KILLING application {} due to critical quota violation", applicationId);
                    if (killAction != null) {
                        killAction.accept(applicationId);
                    }
                    break;

                default:
                    logger.error("Unknown enforcement action: {}", action);
            }
        } catch (Exception e) {
            logger.error("Failed to execute enforcement action {} for application {}",
                    action, applicationId, e);
        }
    }

    /**
     * Returns the enforcement policy for this application.
     * Useful for inspecting violation counts and grace period state.
     *
     * @return the enforcement policy
     */
    public EnforcementPolicy getPolicy() {
        return policy;
    }

    /**
     * Returns the application ID.
     *
     * @return the application identifier
     */
    public String getApplicationId() {
        return applicationId;
    }
}
