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

import org.flossware.platform.api.ApplicationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Intelligent workload placement scheduler.
 *
 * <p>Automatically selects the optimal execution backend (in-JVM, container, VM, Kubernetes)
 * for each workload based on characteristics and requirements. Similar to VirtOS IaaS
 * scheduling, users describe <b>what</b> they want, and the scheduler decides <b>how</b>
 * to run it.</p>
 *
 * <p><b>Decision Factors:</b></p>
 * <ul>
 *   <li><b>Workload Type</b> - Java app, native binary, container image</li>
 *   <li><b>Resource Requirements</b> - CPU, memory, disk needs</li>
 *   <li><b>Isolation Needs</b> - Kernel access, security policies</li>
 *   <li><b>Scale Requirements</b> - Replica count, auto-scaling</li>
 *   <li><b>Infrastructure Availability</b> - Which backends are available</li>
 * </ul>
 *
 * <p><b>Decision Tree:</b></p>
 * <pre>
 * 1. Check for explicit backend override (deploymentTarget property)
 * 2. If requires VM isolation OR kernel access → VM
 * 3. If massive scale (10+ replicas) → Kubernetes (if available)
 * 4. If non-Java workload → Container or VM
 * 5. If Java + heavy (16GB+ RAM) → Container
 * 6. If Java + lightweight (&lt;4GB RAM) → In-JVM
 * 7. Default → In-JVM
 * </pre>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * WorkloadPlacementScheduler scheduler = new WorkloadPlacementScheduler();
 *
 * // Lightweight Java app
 * ApplicationDescriptor authService = ApplicationDescriptor.builder()
 *     .applicationId("auth-service")
 *     .mainClass("com.example.AuthService")
 *     .resource(ResourceConfig.builder().memory(512).build())
 *     .build();
 * PlacementDecision decision = scheduler.scheduleWorkload(authService);
 * // → IN_JVM (lightweight Java)
 *
 * // Heavy Java app
 * ApplicationDescriptor mlModel = ApplicationDescriptor.builder()
 *     .applicationId("ml-model")
 *     .mainClass("com.example.MLService")
 *     .resource(ResourceConfig.builder().memory(32768).build())
 *     .build();
 * decision = scheduler.scheduleWorkload(mlModel);
 * // → CONTAINER (heavy Java, needs isolation)
 *
 * // Native binary
 * ApplicationDescriptor database = ApplicationDescriptor.builder()
 *     .applicationId("postgres")
 *     .property("native.executable", "/usr/bin/postgres")
 *     .resource(ResourceConfig.builder().memory(16384).build())
 *     .build();
 * decision = scheduler.scheduleWorkload(database);
 * // → VIRTUAL_MACHINE (non-Java, kernel access)
 *
 * // Massive scale
 * ApplicationDescriptor frontend = ApplicationDescriptor.builder()
 *     .applicationId("web-frontend")
 *     .mainClass("com.example.Frontend")
 *     .property("replicas", "50")
 *     .build();
 * decision = scheduler.scheduleWorkload(frontend);
 * // → KUBERNETES (massive scale)
 * }</pre>
 *
 * @since 2.2
 * @see ExecutionBackend
 * @see PlacementDecision
 * @see WorkloadProfile
 */
public class WorkloadPlacementScheduler {

    private static final Logger logger = LoggerFactory.getLogger(WorkloadPlacementScheduler.class);

    private static final int LIGHTWEIGHT_MEMORY_THRESHOLD_MB = 4096;  // 4GB
    private static final int HEAVY_MEMORY_THRESHOLD_MB = 16384;       // 16GB
    private static final int MASSIVE_SCALE_REPLICA_THRESHOLD = 10;

    private final boolean kubernetesAvailable;

    /**
     * Creates a workload placement scheduler.
     */
    public WorkloadPlacementScheduler() {
        this(false);
    }

    /**
     * Creates a workload placement scheduler with explicit backend availability.
     *
     * @param kubernetesAvailable whether Kubernetes backend is available
     */
    public WorkloadPlacementScheduler(boolean kubernetesAvailable) {
        this.kubernetesAvailable = kubernetesAvailable;
        logger.info("WorkloadPlacementScheduler initialized (Kubernetes available: {})", kubernetesAvailable);
    }

    /**
     * Schedules a workload and determines the optimal execution backend.
     *
     * <p>Analyzes the application descriptor and selects the best backend
     * based on workload characteristics and infrastructure availability.</p>
     *
     * @param descriptor the application descriptor
     * @return placement decision with selected backend and rationale
     * @throws NullPointerException if descriptor is null
     */
    public PlacementDecision scheduleWorkload(ApplicationDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor cannot be null");

        String applicationId = descriptor.getApplicationId();
        logger.debug("[{}] Scheduling workload placement", applicationId);

        // Check for explicit backend override
        String explicitBackend = descriptor.getProperties().get("deploymentTarget");
        if (explicitBackend != null && !explicitBackend.isEmpty()) {
            try {
                ExecutionBackend backend = ExecutionBackend.fromId(explicitBackend);
                logger.info("[{}] Using explicit backend override: {}", applicationId, backend);
                return new PlacementDecision(applicationId, backend,
                        "Explicit deployment target override: " + explicitBackend);
            } catch (IllegalArgumentException e) {
                logger.warn("[{}] Invalid deployment target '{}', falling back to automatic selection",
                        applicationId, explicitBackend);
            }
        }

        // Analyze workload characteristics
        WorkloadProfile profile = WorkloadProfile.analyze(descriptor);
        logger.debug("[{}] Workload profile: {}", applicationId, profile);

        // Apply decision tree
        PlacementDecision decision = applyDecisionTree(profile);

        logger.info("[{}] Placement decision: {} - {}",
                applicationId, decision.getBackend(), decision.getReason());

        return decision;
    }

    /**
     * Applies the decision tree to select the optimal backend.
     *
     * @param profile the analyzed workload profile
     * @return placement decision
     */
    private PlacementDecision applyDecisionTree(WorkloadProfile profile) {
        String appId = profile.getApplicationId();

        // Rule 1: VM isolation required
        if (profile.requiresVmIsolation()) {
            return new PlacementDecision(appId, ExecutionBackend.VIRTUAL_MACHINE,
                    "VM isolation explicitly required via policy");
        }

        // Rule 2: Kernel access required
        if (profile.needsKernelAccess()) {
            return new PlacementDecision(appId, ExecutionBackend.VIRTUAL_MACHINE,
                    "Workload requires kernel-level access");
        }

        // Rule 3: Massive scale (if Kubernetes available)
        if (profile.requiresMassiveScale() && kubernetesAvailable) {
            return new PlacementDecision(appId, ExecutionBackend.KUBERNETES,
                    String.format("Massive scale deployment (%d replicas) requires Kubernetes",
                            profile.getRequiredReplicas()));
        }

        // Rule 4: Non-Java workload
        if (!profile.isJavaApp()) {
            if (profile.isContainerImage()) {
                return new PlacementDecision(appId, ExecutionBackend.CONTAINER,
                        "Non-Java workload with container image specified");
            } else if (profile.isNativeApp()) {
                return new PlacementDecision(appId, ExecutionBackend.VIRTUAL_MACHINE,
                        "Native binary workload requires VM isolation");
            } else {
                return new PlacementDecision(appId, ExecutionBackend.CONTAINER,
                        "Non-Java workload defaults to container");
            }
        }

        // Rule 5: Heavy Java workload
        if (profile.isJavaApp() && profile.getMemoryMB() >= HEAVY_MEMORY_THRESHOLD_MB) {
            return new PlacementDecision(appId, ExecutionBackend.CONTAINER,
                    String.format("Heavy Java workload (%dMB memory) requires container isolation",
                            profile.getMemoryMB()));
        }

        // Rule 6: Moderate Java workload (4GB-16GB)
        if (profile.isJavaApp() && profile.getMemoryMB() >= LIGHTWEIGHT_MEMORY_THRESHOLD_MB) {
            return new PlacementDecision(appId, ExecutionBackend.CONTAINER,
                    String.format("Moderate Java workload (%dMB memory) benefits from container isolation",
                            profile.getMemoryMB()));
        }

        // Rule 7: Lightweight Java workload (< 4GB)
        if (profile.isJavaApp() && profile.getMemoryMB() < LIGHTWEIGHT_MEMORY_THRESHOLD_MB) {
            return new PlacementDecision(appId, ExecutionBackend.IN_JVM,
                    String.format("Lightweight Java workload (%dMB memory, %d CPUs) optimal for in-JVM execution",
                            profile.getMemoryMB(), profile.getCpuCores()));
        }

        // Default: In-JVM for Java apps
        if (profile.isJavaApp()) {
            return new PlacementDecision(appId, ExecutionBackend.IN_JVM,
                    "Java application defaults to in-JVM execution (lowest overhead)");
        }

        // Ultimate fallback: Container
        return new PlacementDecision(appId, ExecutionBackend.CONTAINER,
                "Default fallback to container execution");
    }

    /**
     * Checks if Kubernetes backend is available.
     *
     * @return true if Kubernetes can be used for placement
     */
    public boolean isKubernetesAvailable() {
        return kubernetesAvailable;
    }

    /**
     * Returns the memory threshold for lightweight workloads.
     *
     * @return threshold in MB (default: 4096)
     */
    public static int getLightweightMemoryThresholdMB() {
        return LIGHTWEIGHT_MEMORY_THRESHOLD_MB;
    }

    /**
     * Returns the memory threshold for heavy workloads.
     *
     * @return threshold in MB (default: 16384)
     */
    public static int getHeavyMemoryThresholdMB() {
        return HEAVY_MEMORY_THRESHOLD_MB;
    }

    /**
     * Returns the replica threshold for massive scale.
     *
     * @return threshold (default: 10)
     */
    public static int getMassiveScaleReplicaThreshold() {
        return MASSIVE_SCALE_REPLICA_THRESHOLD;
    }
}
