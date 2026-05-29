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
 * Listener for resource monitoring events.
 * Notified when resource quotas are exceeded or thresholds are crossed.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ResourceEventListener listener = new ResourceEventListener() {
 *     @Override
 *     public void onQuotaExceeded(String appId, ResourceQuota quota, ResourceSnapshot snapshot) {
 *         System.err.println("Quota exceeded for " + appId);
 *         System.err.println("Heap: " + snapshot.getHeapUsedBytes() + " bytes");
 *     }
 *
 *     @Override
 *     public void onThresholdCrossed(String appId, String metric, double threshold, double current) {
 *         System.out.println(metric + " threshold crossed: " + current + " > " + threshold);
 *     }
 * };
 *
 * monitor.addListener(listener);
 * }</pre>
 *
 * @see ResourceMonitor
 * @see ResourceQuota
 */
public interface ResourceEventListener {
    /**
     * Called when an application exceeds its resource quota.
     *
     * @param applicationId the application identifier
     * @param quota the quota that was exceeded
     * @param snapshot the current resource usage
     */
    void onQuotaExceeded(String applicationId, ResourceQuota quota, ResourceSnapshot snapshot);

    /**
     * Called when a resource metric crosses a configured threshold.
     *
     * @param applicationId the application identifier
     * @param metric the metric name (e.g., "cpu", "memory")
     * @param threshold the threshold value
     * @param currentValue the current metric value
     */
    void onThresholdCrossed(String applicationId, String metric, double threshold, double currentValue);
}
