package org.flossware.jplatform.cluster;

import org.flossware.jplatform.api.*;
import org.flossware.jplatform.core.ApplicationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Cluster-aware extension of ApplicationManager.
 * Integrates with ClusterManager and ClusterStateStore to enable
 * distributed application deployment and lifecycle management across
 * multiple nodes.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Distributed deployment - descriptors stored in cluster state</li>
 *   <li>Leader-based scheduling - leader assigns apps to nodes</li>
 *   <li>Node-local execution - apps only start if assigned to this node</li>
 *   <li>State synchronization - state changes replicated across cluster</li>
 *   <li>Fallback to standalone - works without clustering</li>
 * </ul>
 *
 * <p>Deployment flow in clustered mode:</p>
 * <ol>
 *   <li>Application descriptor written to cluster state store</li>
 *   <li>Leader node assigns application to a target node via scheduler</li>
 *   <li>Target node detects assignment and deploys locally</li>
 *   <li>State changes are synchronized via cluster state store</li>
 * </ol>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create cluster manager
 * HazelcastClusterManager clusterManager = new HazelcastClusterManager();
 * clusterManager.join(clusterConfig);
 *
 * // Create state store
 * HazelcastStateStore stateStore = new HazelcastStateStore(
 *     clusterManager.getHazelcastInstance());
 *
 * // Create clustered application manager
 * ClusteredApplicationManager appManager = new ClusteredApplicationManager(
 *     messageBus, serviceRegistry, clusterManager, stateStore);
 *
 * // Deploy application (will be assigned by leader)
 * appManager.deploy(descriptor);
 * }</pre>
 *
 * @see ApplicationManager
 * @see ClusterManager
 * @see ClusterStateStore
 */
public class ClusteredApplicationManager extends ApplicationManager {

    private static final Logger logger = LoggerFactory.getLogger(ClusteredApplicationManager.class);

    private final ClusterManager clusterManager;
    private final ClusterStateStore stateStore;
    private final ApplicationScheduler scheduler;
    private final ClusterEventListener clusterListener;

    /**
     * Constructs a new clustered application manager.
     *
     * @param messageBus the shared message bus, or null to disable messaging
     * @param serviceRegistry the shared service registry, or null to disable
     * @param clusterManager the cluster manager for distributed coordination
     * @param stateStore the cluster state store for distributed state
     */
    public ClusteredApplicationManager(MessageBus messageBus,
                                       ServiceRegistry serviceRegistry,
                                       ClusterManager clusterManager,
                                       ClusterStateStore stateStore) {
        super(messageBus, serviceRegistry);
        this.clusterManager = clusterManager;
        this.stateStore = stateStore;

        // Create scheduler if we have a Hazelcast-based cluster
        if (clusterManager instanceof HazelcastClusterManager) {
            HazelcastClusterManager hzManager = (HazelcastClusterManager) clusterManager;
            this.scheduler = new ApplicationScheduler(
                    hzManager.getHazelcastInstance(),
                    clusterManager,
                    ApplicationScheduler.SchedulingStrategy.LEAST_LOADED
            );
        } else {
            this.scheduler = null;
        }

        // Subscribe to cluster events (only if cluster manager is present)
        if (clusterManager != null) {
            this.clusterListener = new ClusterEventListener() {
                @Override
                public void onNodeJoined(ClusterNode node) {
                    logger.info("Node joined cluster: {}", node.getNodeId());
                }

                @Override
                public void onNodeLeft(ClusterNode node) {
                    logger.warn("Node left cluster: {}", node.getNodeId());
                    handleNodeFailure(node);
                }

                @Override
                public void onLeaderChanged(ClusterNode newLeader) {
                    logger.info("New cluster leader: {}", newLeader.getNodeId());
                }
            };
            clusterManager.addListener(clusterListener);
        } else {
            this.clusterListener = null;
        }

        logger.info("ClusteredApplicationManager initialized in {} mode",
                clusterManager != null ? "clustered" : "standalone");
    }

    /**
     * Deploys an application to the cluster.
     * In clustered mode, the descriptor is written to the cluster state store,
     * and the leader assigns the application to a node.
     * In standalone mode, falls back to local deployment.
     *
     * @param descriptor the application descriptor
     * @throws Exception if deployment fails
     */
    @Override
    public synchronized void deploy(ApplicationDescriptor descriptor) throws Exception {
        String appId = descriptor.getApplicationId();

        if (clusterManager != null && clusterManager.isJoined()) {
            logger.info("[{}] Deploying application in cluster mode", appId);

            boolean clusterStateWritten = false;
            boolean assigned = false;
            boolean deployedLocally = false;

            try {
                // Step 1: Write descriptor to cluster state
                stateStore.putApplicationDescriptor(appId, descriptor);
                stateStore.putApplicationState(appId, ApplicationState.DEPLOYED);
                clusterStateWritten = true;

                // Step 2: If leader, assign to a node
                if (scheduler != null) {
                    try {
                        if (clusterManager.isLeader()) {
                            String assignedNode = scheduler.assignApplication(appId);
                            assigned = true;
                            logger.info("[{}] Leader assigned application to node: {}", appId, assignedNode);
                        }
                    } catch (IllegalStateException e) {
                        // Lost leadership during assignment, log and continue
                        logger.debug("[{}] Lost leadership during assignment: {}", appId, e.getMessage());
                    }

                    // Step 3: Check if assigned to local node and deploy locally
                    if (scheduler.isAssignedToLocalNode(appId)) {
                        logger.info("[{}] Application assigned to local node, deploying locally", appId);
                        super.deploy(descriptor);
                        deployedLocally = true;
                    } else {
                        logger.info("[{}] Application assigned to another node, skipping local deployment", appId);
                    }
                } else {
                    // No scheduler available, deploy locally
                    logger.info("[{}] No scheduler available, deploying locally", appId);
                    super.deploy(descriptor);
                    deployedLocally = true;
                }

            } catch (Exception e) {
                logger.error("[{}] Deployment failed, initiating rollback", appId, e);

                // Rollback in reverse order
                if (deployedLocally) {
                    try {
                        super.undeploy(appId);
                        logger.info("[{}] Rolled back local deployment", appId);
                    } catch (Exception rollbackEx) {
                        logger.error("[{}] Failed to rollback local deployment", appId, rollbackEx);
                    }
                }

                if (assigned && clusterManager.isLeader()) {
                    try {
                        scheduler.unassignApplication(appId);
                        logger.info("[{}] Rolled back application assignment", appId);
                    } catch (Exception rollbackEx) {
                        logger.error("[{}] Failed to rollback assignment", appId, rollbackEx);
                    }
                }

                if (clusterStateWritten) {
                    try {
                        stateStore.putApplicationState(appId, ApplicationState.FAILED);
                        logger.info("[{}] Updated cluster state to FAILED", appId);
                    } catch (Exception rollbackEx) {
                        logger.error("[{}] Failed to update cluster state to FAILED", appId, rollbackEx);
                    }
                }

                throw new Exception("Deployment failed and rollback completed: " + e.getMessage(), e);
            }
        } else {
            // Standalone mode
            logger.info("[{}] Deploying application in standalone mode", appId);
            super.deploy(descriptor);
        }
    }

    /**
     * Starts a deployed application.
     * In clustered mode, checks if the application is assigned to this node.
     * Only starts if assigned locally or in standalone mode.
     *
     * @param applicationId the application identifier
     * @throws Exception if starting fails
     */
    @Override
    public synchronized void start(String applicationId) throws Exception {
        if (clusterManager != null && clusterManager.isJoined() && scheduler != null) {
            // Check if assigned to this node
            if (!scheduler.isAssignedToLocalNode(applicationId)) {
                logger.warn("[{}] Application not assigned to this node, cannot start", applicationId);
                throw new IllegalStateException("Application not assigned to this node: " + applicationId);
            }

            logger.info("[{}] Starting application on assigned node", applicationId);
            super.start(applicationId);

            // Update cluster state
            try {
                stateStore.putApplicationState(applicationId, ApplicationState.RUNNING);
            } catch (Exception e) {
                logger.error("[{}] Failed to update cluster state to RUNNING", applicationId, e);
                // Application is already started locally, but cluster state is inconsistent
                throw new Exception("Application started but failed to update cluster state: " + e.getMessage(), e);
            }

        } else {
            // Standalone mode
            logger.info("[{}] Starting application in standalone mode", applicationId);
            super.start(applicationId);
        }
    }

    /**
     * Stops a running application.
     * In clustered mode, updates the cluster state.
     *
     * @param applicationId the application identifier
     * @throws Exception if stopping fails
     */
    @Override
    public synchronized void stop(String applicationId) throws Exception {
        if (clusterManager != null && clusterManager.isJoined() && scheduler != null) {
            // Check if assigned to this node
            if (!scheduler.isAssignedToLocalNode(applicationId)) {
                logger.warn("[{}] Application not assigned to this node, cannot stop", applicationId);
                throw new IllegalStateException("Application not assigned to this node: " + applicationId);
            }

            logger.info("[{}] Stopping application on assigned node", applicationId);
            super.stop(applicationId);

            // Update cluster state
            try {
                stateStore.putApplicationState(applicationId, ApplicationState.STOPPED);
            } catch (Exception e) {
                logger.error("[{}] Failed to update cluster state to STOPPED", applicationId, e);
                // Application is already stopped locally, but cluster state is inconsistent
                throw new Exception("Application stopped but failed to update cluster state: " + e.getMessage(), e);
            }

        } else {
            // Standalone mode
            logger.info("[{}] Stopping application in standalone mode", applicationId);
            super.stop(applicationId);
        }
    }

    /**
     * Undeploys an application from the cluster.
     * In clustered mode, removes from cluster state and scheduler.
     *
     * @param applicationId the application identifier
     * @throws Exception if undeployment fails
     */
    @Override
    public synchronized void undeploy(String applicationId) throws Exception {
        if (clusterManager != null && clusterManager.isJoined() && scheduler != null) {
            boolean undeployedLocally = false;
            boolean unassigned = false;
            ApplicationDescriptor descriptor = null;

            try {
                // Save descriptor for potential rollback
                if (scheduler.isAssignedToLocalNode(applicationId)) {
                    descriptor = stateStore.getApplicationDescriptor(applicationId);
                }

                // Step 1: Undeploy locally if assigned to this node
                if (scheduler.isAssignedToLocalNode(applicationId)) {
                    logger.info("[{}] Undeploying application from assigned node", applicationId);
                    super.undeploy(applicationId);
                    undeployedLocally = true;
                }

                // Step 2: If leader, remove from scheduler and cluster state
                if (clusterManager.isLeader()) {
                    scheduler.unassignApplication(applicationId);
                    unassigned = true;
                    stateStore.putApplicationState(applicationId, ApplicationState.UNDEPLOYED);
                    logger.info("[{}] Leader removed application from cluster", applicationId);
                }

            } catch (Exception e) {
                logger.error("[{}] Undeploy failed, initiating rollback", applicationId, e);

                // Rollback in reverse order
                if (unassigned && clusterManager.isLeader() && descriptor != null) {
                    try {
                        scheduler.assignApplication(applicationId);
                        logger.info("[{}] Rolled back application unassignment", applicationId);
                    } catch (Exception rollbackEx) {
                        logger.error("[{}] Failed to rollback unassignment", applicationId, rollbackEx);
                    }
                }

                if (undeployedLocally && descriptor != null) {
                    try {
                        super.deploy(descriptor);
                        logger.info("[{}] Rolled back local undeploy by redeploying", applicationId);
                    } catch (Exception rollbackEx) {
                        logger.error("[{}] Failed to rollback local undeploy", applicationId, rollbackEx);
                    }
                }

                throw new Exception("Undeploy failed and rollback attempted: " + e.getMessage(), e);
            }

        } else {
            // Standalone mode
            logger.info("[{}] Undeploying application in standalone mode", applicationId);
            super.undeploy(applicationId);
        }
    }

    /**
     * Lists all applications in the cluster.
     * In clustered mode, returns applications from cluster state.
     * In standalone mode, returns local applications.
     *
     * @return a map of application IDs to their current states
     */
    @Override
    public Map<String, ApplicationState> listApplications() {
        if (clusterManager != null && clusterManager.isJoined() && stateStore != null) {
            // Return cluster-wide applications
            return stateStore.getAllApplicationStates();
        } else {
            // Return local applications
            return super.listApplications();
        }
    }

    /**
     * Returns the cluster manager.
     *
     * @return the cluster manager, or null if not clustered
     */
    public ClusterManager getClusterManager() {
        return clusterManager;
    }

    /**
     * Returns the cluster state store.
     *
     * @return the state store, or null if not clustered
     */
    public ClusterStateStore getStateStore() {
        return stateStore;
    }

    /**
     * Returns the application scheduler.
     *
     * @return the scheduler, or null if not clustered
     */
    public ApplicationScheduler getScheduler() {
        return scheduler;
    }

    /**
     * Handles node failure by reassigning applications if this is the leader.
     *
     * @param failedNode the failed cluster node
     */
    private void handleNodeFailure(ClusterNode failedNode) {
        if (clusterManager.isLeader() && scheduler != null) {
            logger.warn("Handling failure of node: {}", failedNode.getNodeId());
            try {
                int reassignedCount = scheduler.reassignFromFailedNode(failedNode.getNodeId());
                logger.info("Reassigned {} applications from failed node", reassignedCount);
            } catch (Exception e) {
                logger.error("Error reassigning applications from failed node", e);
            }
        }
    }

    /**
     * Shuts down the clustered application manager.
     * Removes cluster event listener before delegating to parent shutdown.
     */
    @Override
    public void shutdown() {
        // Remove cluster event listener to prevent resource leak
        if (clusterManager != null && clusterListener != null) {
            clusterManager.removeListener(clusterListener);
            logger.debug("Removed cluster event listener");
        }
        super.shutdown();
    }
}
