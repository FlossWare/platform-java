package org.flossware.jplatform.api;

/**
 * Application lifecycle states.
 *
 * <p>Normal lifecycle progression:</p>
 * <pre>
 * DEPLOYED → STARTING → RUNNING → STOPPING → STOPPED → UNDEPLOYED
 * </pre>
 *
 * <p>Error states:</p>
 * <pre>
 * Any state → FAILED
 * </pre>
 */
public enum ApplicationState {
    /** Application has been deployed but not yet started */
    DEPLOYED,

    /** Application is in the process of starting */
    STARTING,

    /** Application is running normally */
    RUNNING,

    /** Application is in the process of stopping */
    STOPPING,

    /** Application has stopped cleanly */
    STOPPED,

    /** Application has failed (start/stop failure or runtime error) */
    FAILED,

    /** Application has been completely removed from the platform */
    UNDEPLOYED
}
