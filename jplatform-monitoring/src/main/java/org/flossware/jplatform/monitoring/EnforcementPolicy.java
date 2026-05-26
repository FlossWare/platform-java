package org.flossware.jplatform.monitoring;

import org.flossware.jplatform.api.EnforcementAction;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks quota violation history and determines when enforcement should occur.
 *
 * <p>This class implements the grace period logic - enforcement actions are only
 * triggered after a resource has been in violation for a configured number of
 * consecutive monitoring cycles. This prevents transient spikes from causing
 * unnecessary disruptions.</p>
 *
 * <p>Each application has its own enforcement policy instance that tracks
 * violation counts independently for each resource type (CPU, memory, threads).</p>
 *
 * <p>Thread-safe for concurrent access from monitoring threads.</p>
 *
 * @since 2.0
 */
public class EnforcementPolicy {

    private final String applicationId;
    private final int gracePeriod;

    // Track consecutive violation counts per resource type
    private final Map<String, Integer> violationCounts;

    /**
     * Creates a new enforcement policy for an application.
     *
     * @param applicationId the application identifier
     * @param gracePeriod the number of consecutive violations before enforcement
     * @throws NullPointerException if applicationId is null
     * @throws IllegalArgumentException if gracePeriod is not positive
     */
    public EnforcementPolicy(String applicationId, int gracePeriod) {
        this.applicationId = Objects.requireNonNull(applicationId, "applicationId cannot be null");
        if (gracePeriod <= 0) {
            throw new IllegalArgumentException("gracePeriod must be positive, got: " + gracePeriod);
        }
        this.gracePeriod = gracePeriod;
        this.violationCounts = new ConcurrentHashMap<>();
    }

    /**
     * Records a quota violation for a resource type.
     * Returns true if the grace period has been exceeded and enforcement
     * should be triggered.
     *
     * @param resourceType the resource type (e.g., "heap", "cpu", "threads")
     * @return true if enforcement should be triggered
     * @throws IllegalArgumentException if resourceType is null or empty
     */
    public boolean recordViolation(String resourceType) {
        if (resourceType == null || resourceType.trim().isEmpty()) {
            throw new IllegalArgumentException("resourceType cannot be null or empty");
        }
        int count = violationCounts.compute(resourceType, (k, v) -> v == null ? 1 : v + 1);
        return count >= gracePeriod;
    }

    /**
     * Clears the violation count for a resource type.
     * Called when a resource is back within quota limits.
     *
     * @param resourceType the resource type to clear
     * @throws IllegalArgumentException if resourceType is null or empty
     */
    public void clearViolation(String resourceType) {
        if (resourceType == null || resourceType.trim().isEmpty()) {
            throw new IllegalArgumentException("resourceType cannot be null or empty");
        }
        violationCounts.remove(resourceType);
    }

    /**
     * Returns the current violation count for a resource type.
     *
     * @param resourceType the resource type
     * @return the current violation count, or 0 if no violations
     * @throws IllegalArgumentException if resourceType is null or empty
     */
    public int getViolationCount(String resourceType) {
        if (resourceType == null || resourceType.trim().isEmpty()) {
            throw new IllegalArgumentException("resourceType cannot be null or empty");
        }
        return violationCounts.getOrDefault(resourceType, 0);
    }

    /**
     * Clears all violation counts.
     * Used when enforcement is triggered to reset the grace period.
     */
    public void clearAll() {
        violationCounts.clear();
    }

    /**
     * Returns the application ID this policy applies to.
     *
     * @return the application identifier
     */
    public String getApplicationId() {
        return applicationId;
    }

    /**
     * Returns the configured grace period.
     *
     * @return the number of violations before enforcement
     */
    public int getGracePeriod() {
        return gracePeriod;
    }
}
