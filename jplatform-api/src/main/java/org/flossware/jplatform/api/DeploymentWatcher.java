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

/**
 * Watches a directory for application descriptor files and triggers deployment events.
 * Implementations monitor filesystem changes and notify registered listeners.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * WatcherConfig config = WatcherConfig.builder()
 *     .watchDirectory(Paths.get("/var/jplatform/apps"))
 *     .autoStart(true)
 *     .autoDeploy(true)
 *     .addFileExtension("yaml")
 *     .build();
 *
 * DeploymentWatcher watcher = new FileSystemDeploymentWatcher(config);
 * watcher.addListener(new AutoDeploymentHandler(applicationManager));
 * watcher.start();
 * }</pre>
 *
 * @see DeploymentEventListener
 * @see WatcherConfig
 */
public interface DeploymentWatcher extends AutoCloseable {

    /**
     * Starts watching the configured directory for changes.
     *
     * @throws Exception if the watcher cannot be started
     */
    void start() throws Exception;

    /**
     * Stops watching the directory.
     *
     * @throws Exception if the watcher cannot be stopped
     */
    void stop() throws Exception;

    /**
     * Adds a listener to be notified of deployment events.
     *
     * @param listener the listener to add
     */
    void addListener(DeploymentEventListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(DeploymentEventListener listener);

    /**
     * Checks if the watcher is currently running.
     *
     * @return true if running, false otherwise
     */
    boolean isRunning();
}
