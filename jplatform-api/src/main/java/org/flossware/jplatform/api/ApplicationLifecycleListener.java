package org.flossware.jplatform.api;

/**
 * Listener for application lifecycle events.
 * All methods have default empty implementations, so you only need to override
 * the events you care about.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApplicationLifecycleListener listener = new ApplicationLifecycleListener() {
 *     @Override
 *     public void onStarted(String applicationId) {
 *         System.out.println("Application started: " + applicationId);
 *     }
 *
 *     @Override
 *     public void onError(String applicationId, Throwable error) {
 *         System.err.println("Application error: " + error.getMessage());
 *     }
 * };
 * }</pre>
 *
 * @see ApplicationState
 */
public interface ApplicationLifecycleListener {
    /**
     * Called when an application has been deployed.
     *
     * @param applicationId the application identifier
     */
    default void onDeployed(String applicationId) {}

    /**
     * Called when an application is starting.
     *
     * @param applicationId the application identifier
     */
    default void onStarting(String applicationId) {}

    /**
     * Called when an application has successfully started.
     *
     * @param applicationId the application identifier
     */
    default void onStarted(String applicationId) {}

    /**
     * Called when an application is stopping.
     *
     * @param applicationId the application identifier
     */
    default void onStopping(String applicationId) {}

    /**
     * Called when an application has stopped.
     *
     * @param applicationId the application identifier
     */
    default void onStopped(String applicationId) {}

    /**
     * Called when an application has been undeployed.
     *
     * @param applicationId the application identifier
     */
    default void onUndeployed(String applicationId) {}

    /**
     * Called when an error occurs during application lifecycle.
     *
     * @param applicationId the application identifier
     * @param error the error that occurred
     */
    default void onError(String applicationId, Throwable error) {}
}
