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

package org.flossware.jplatform.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform manager interface for application lifecycle management.
 * Provides methods for deploying, starting, stopping, and undeploying applications.
 *
 * <p>This interface allows modules to depend on an abstraction rather than the concrete
 * ApplicationManager implementation, improving testability and flexibility.</p>
 *
 * @see ApplicationDescriptor
 * @see ApplicationContext
 * @see ApplicationState
 */
public interface PlatformManager {

    /**
     * Deploys an application to the platform.
     * Creates isolated resources but does not start the application.
     *
     * @param descriptor the application descriptor containing configuration
     * @throws Exception if deployment fails
     * @throws IllegalStateException if application is already deployed
     */
    void deploy(ApplicationDescriptor descriptor) throws Exception;

    /**
     * Starts a deployed application.
     * Loads the main class and invokes start() method.
     *
     * @param applicationId the application identifier
     * @throws Exception if starting fails
     * @throws IllegalStateException if application is not deployed or in wrong state
     */
    void start(String applicationId) throws Exception;

    /**
     * Stops a running application.
     *
     * @param applicationId the application identifier
     * @throws Exception if stopping fails
     * @throws IllegalStateException if application is not deployed
     */
    void stop(String applicationId) throws Exception;

    /**
     * Undeploys an application from the platform.
     * Stops the application if running and releases all resources.
     *
     * @param applicationId the application identifier
     * @throws Exception if undeployment fails
     * @throws IllegalStateException if application is not deployed
     */
    void undeploy(String applicationId) throws Exception;

    /**
     * Reloads an application with new code without full undeploy/deploy cycle.
     *
     * @param applicationId the application to reload
     * @param newDescriptor the new descriptor with updated classpath
     * @throws Exception if reload fails
     * @throws IllegalStateException if application is not deployed
     */
    void reload(String applicationId, ApplicationDescriptor newDescriptor) throws Exception;

    /**
     * Returns the application context for a deployed application.
     *
     * @param applicationId the application identifier
     * @return the application context, or null if not deployed
     */
    ApplicationContext getApplicationContext(String applicationId);

    /**
     * Lists all deployed applications and their current states.
     *
     * @return a map of application IDs to their current states
     */
    Map<String, ApplicationState> listApplications();

    /**
     * Starts all deployed applications in dependency order.
     *
     * @throws Exception if startup order cannot be determined or starting fails
     */
    void startAll() throws Exception;

    /**
     * Returns the recommended startup order for all deployed applications.
     *
     * @return list of application IDs in dependency order
     * @throws IllegalStateException if circular dependencies exist
     */
    List<String> getStartupOrder();

    /**
     * Returns the applications that depend on the given application.
     *
     * @param applicationId the application identifier
     * @return set of dependent application IDs
     */
    Set<String> getDependentApplications(String applicationId);

    /**
     * Shuts down the platform by undeploying all applications.
     */
    void shutdown();
}
