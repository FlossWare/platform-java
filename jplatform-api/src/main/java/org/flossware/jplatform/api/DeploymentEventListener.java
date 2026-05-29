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

import java.nio.file.Path;

/**
 * Listener for deployment-related filesystem events.
 * Implementations are notified when application descriptor files are detected,
 * modified, or removed from the watched directory.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * DeploymentEventListener listener = new DeploymentEventListener() {
 *     @Override
 *     public void onDescriptorDetected(Path descriptorFile) {
 *         System.out.println("New app detected: " + descriptorFile);
 *         // Parse and deploy application
 *     }
 *
 *     @Override
 *     public void onDescriptorRemoved(Path descriptorFile) {
 *         System.out.println("App removed: " + descriptorFile);
 *         // Undeploy application
 *     }
 * };
 * }</pre>
 *
 * @see DeploymentWatcher
 */
public interface DeploymentEventListener {

    /**
     * Called when a new descriptor file is detected in the watched directory.
     *
     * @param descriptorFile the path to the descriptor file
     */
    void onDescriptorDetected(Path descriptorFile);

    /**
     * Called when an existing descriptor file is modified.
     *
     * @param descriptorFile the path to the modified descriptor file
     */
    void onDescriptorModified(Path descriptorFile);

    /**
     * Called when a descriptor file is removed from the watched directory.
     *
     * @param descriptorFile the path to the removed descriptor file
     */
    void onDescriptorRemoved(Path descriptorFile);

    /**
     * Called when an error occurs while processing a descriptor file.
     *
     * @param file the file that caused the error
     * @param error the error that occurred
     */
    void onError(Path file, Exception error);
}
