package org.flossware.jplatform.api;

import java.util.Optional;

/**
 * Configuration for application resource limits and enforcement policies.
 * Uses convenient units (MB, seconds) compared to ResourceQuota (bytes, nanos).
 * All limits are optional - unconfigured resources have no limits.
 *
 * <p>Resource enforcement allows automatic action when quotas are exceeded.
 * Each resource type (heap, CPU, threads) can have its own enforcement policy.
 * Enforcement actions are triggered after a configurable grace period to avoid
 * transient spikes from causing unnecessary actions.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ResourceConfig config = ResourceConfig.builder()
 *     .maxHeapMB(512)
 *     .maxThreads(50)
 *     .maxCpuTimeSeconds(60)
 *     .memoryEnforcementAction(EnforcementAction.SHUTDOWN)
 *     .cpuEnforcementAction(EnforcementAction.THROTTLE)
 *     .threadEnforcementAction(EnforcementAction.SHUTDOWN)
 *     .violationGracePeriod(3)  // Allow 3 consecutive violations before enforcing
 *     .build();
 *
 * // Or create unlimited config
 * ResourceConfig unlimited = ResourceConfig.unlimited();
 * }</pre>
 *
 * @since 2.0 (enforcement actions added)
 * @see ResourceQuota
 * @see ResourceMonitor
 * @see EnforcementAction
 */
public class ResourceConfig {
    private final Optional<Long> maxHeapMB;
    private final Optional<Integer> maxThreads;
    private final Optional<Long> maxCpuTimeSeconds;

    // Enforcement policies (added in 2.0)
    private final EnforcementAction memoryEnforcementAction;
    private final EnforcementAction cpuEnforcementAction;
    private final EnforcementAction threadEnforcementAction;
    private final int violationGracePeriod;

    private ResourceConfig(Builder builder) {
        this.maxHeapMB = builder.maxHeapMB;
        this.maxThreads = builder.maxThreads;
        this.maxCpuTimeSeconds = builder.maxCpuTimeSeconds;
        this.memoryEnforcementAction = builder.memoryEnforcementAction;
        this.cpuEnforcementAction = builder.cpuEnforcementAction;
        this.threadEnforcementAction = builder.threadEnforcementAction;
        this.violationGracePeriod = builder.violationGracePeriod;
    }

    /**
     * Returns the maximum heap memory allowed for the application.
     *
     * @return the maximum heap size in megabytes, or empty if unlimited
     */
    public Optional<Long> getMaxHeapMB() {
        return maxHeapMB;
    }

    /**
     * Returns the maximum number of threads allowed for the application.
     *
     * @return the maximum thread count, or empty if unlimited
     */
    public Optional<Integer> getMaxThreads() {
        return maxThreads;
    }

    /**
     * Returns the maximum CPU time allowed for the application.
     *
     * @return the maximum CPU time in seconds, or empty if unlimited
     */
    public Optional<Long> getMaxCpuTimeSeconds() {
        return maxCpuTimeSeconds;
    }

    /**
     * Returns the enforcement action for memory quota violations.
     *
     * @return the memory enforcement action (never null, default is NOTIFY)
     * @since 2.0
     */
    public EnforcementAction getMemoryEnforcementAction() {
        return memoryEnforcementAction;
    }

    /**
     * Returns the enforcement action for CPU quota violations.
     *
     * @return the CPU enforcement action (never null, default is NOTIFY)
     * @since 2.0
     */
    public EnforcementAction getCpuEnforcementAction() {
        return cpuEnforcementAction;
    }

    /**
     * Returns the enforcement action for thread count quota violations.
     *
     * @return the thread enforcement action (never null, default is NOTIFY)
     * @since 2.0
     */
    public EnforcementAction getThreadEnforcementAction() {
        return threadEnforcementAction;
    }

    /**
     * Returns the number of consecutive violations required before enforcement.
     * This grace period prevents transient resource spikes from triggering
     * enforcement actions. For example, if set to 3, the application must
     * exceed its quota for 3 consecutive monitoring cycles (default: 15 seconds
     * at 5-second intervals) before enforcement occurs.
     *
     * @return the grace period in number of violations (default: 3)
     * @since 2.0
     */
    public int getViolationGracePeriod() {
        return violationGracePeriod;
    }

    /**
     * Creates a resource configuration with no limits.
     *
     * @return a configuration allowing unlimited resource usage
     */
    public static ResourceConfig unlimited() {
        return builder().build();
    }

    /**
     * Creates a new builder for constructing resource configurations.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing ResourceConfig instances.
     * All resource limits are optional. Enforcement actions default to NOTIFY.
     */
    public static class Builder {
        private Optional<Long> maxHeapMB = Optional.empty();
        private Optional<Integer> maxThreads = Optional.empty();
        private Optional<Long> maxCpuTimeSeconds = Optional.empty();
        private EnforcementAction memoryEnforcementAction = EnforcementAction.NOTIFY;
        private EnforcementAction cpuEnforcementAction = EnforcementAction.NOTIFY;
        private EnforcementAction threadEnforcementAction = EnforcementAction.NOTIFY;
        private int violationGracePeriod = 3;  // 3 consecutive violations (15 seconds at 5s intervals)

        /**
         * Sets the maximum heap memory allowed.
         *
         * @param mb the maximum heap size in megabytes
         * @return this builder
         * @throws IllegalArgumentException if mb is not positive
         */
        public Builder maxHeapMB(long mb) {
            if (mb <= 0) {
                throw new IllegalArgumentException("Max heap MB must be positive, got: " + mb);
            }
            this.maxHeapMB = Optional.of(mb);
            return this;
        }

        /**
         * Sets the maximum number of threads allowed.
         *
         * @param threads the maximum thread count
         * @return this builder
         * @throws IllegalArgumentException if threads is not positive
         */
        public Builder maxThreads(int threads) {
            if (threads <= 0) {
                throw new IllegalArgumentException("Max threads must be positive, got: " + threads);
            }
            this.maxThreads = Optional.of(threads);
            return this;
        }

        /**
         * Sets the maximum CPU time allowed.
         *
         * @param seconds the maximum CPU time in seconds
         * @return this builder
         * @throws IllegalArgumentException if seconds is not positive
         */
        public Builder maxCpuTimeSeconds(long seconds) {
            if (seconds <= 0) {
                throw new IllegalArgumentException("Max CPU time seconds must be positive, got: " + seconds);
            }
            this.maxCpuTimeSeconds = Optional.of(seconds);
            return this;
        }

        /**
         * Sets the enforcement action for memory quota violations.
         * If not set, defaults to {@link EnforcementAction#NOTIFY}.
         *
         * @param action the enforcement action (must not be null)
         * @return this builder
         * @throws IllegalArgumentException if action is null
         * @since 2.0
         */
        public Builder memoryEnforcementAction(EnforcementAction action) {
            if (action == null) {
                throw new IllegalArgumentException("Enforcement action cannot be null");
            }
            this.memoryEnforcementAction = action;
            return this;
        }

        /**
         * Sets the enforcement action for CPU quota violations.
         * If not set, defaults to {@link EnforcementAction#NOTIFY}.
         *
         * @param action the enforcement action (must not be null)
         * @return this builder
         * @throws IllegalArgumentException if action is null
         * @since 2.0
         */
        public Builder cpuEnforcementAction(EnforcementAction action) {
            if (action == null) {
                throw new IllegalArgumentException("Enforcement action cannot be null");
            }
            this.cpuEnforcementAction = action;
            return this;
        }

        /**
         * Sets the enforcement action for thread count quota violations.
         * If not set, defaults to {@link EnforcementAction#NOTIFY}.
         *
         * @param action the enforcement action (must not be null)
         * @return this builder
         * @throws IllegalArgumentException if action is null
         * @since 2.0
         */
        public Builder threadEnforcementAction(EnforcementAction action) {
            if (action == null) {
                throw new IllegalArgumentException("Enforcement action cannot be null");
            }
            this.threadEnforcementAction = action;
            return this;
        }

        /**
         * Sets the number of consecutive quota violations required before
         * enforcement actions are triggered. This provides a grace period
         * for transient resource spikes.
         *
         * <p>Default is 3 violations, which equals 15 seconds at the default
         * 5-second monitoring interval.</p>
         *
         * @param violations the number of consecutive violations (must be &gt;= 1)
         * @return this builder
         * @throws IllegalArgumentException if violations &lt; 1
         * @since 2.0
         */
        public Builder violationGracePeriod(int violations) {
            if (violations < 1) {
                throw new IllegalArgumentException("Violation grace period must be >= 1");
            }
            this.violationGracePeriod = violations;
            return this;
        }

        /**
         * Builds the ResourceConfig instance.
         *
         * @return a new ResourceConfig with the configured limits
         */
        public ResourceConfig build() {
            return new ResourceConfig(this);
        }
    }
}
