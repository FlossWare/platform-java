package org.flossware.jplatform.metrics.prometheus;

import org.flossware.jplatform.api.ApplicationContext;
import org.flossware.jplatform.api.ApplicationState;
import org.flossware.jplatform.api.ResourceSnapshot;
import org.flossware.jplatform.api.ThreadPoolStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects metrics from an ApplicationContext and formats them for Prometheus export.
 * Converts platform metrics (CPU, memory, threads, state) into Prometheus text format.
 *
 * <p>Collected metrics include:</p>
 * <ul>
 *   <li><b>jplatform_app_cpu_time_seconds</b> (counter) - Total CPU time consumed</li>
 *   <li><b>jplatform_app_heap_used_bytes</b> (gauge) - Current heap memory usage</li>
 *   <li><b>jplatform_app_thread_count</b> (gauge) - Number of active threads</li>
 *   <li><b>jplatform_app_state</b> (gauge) - Application state (1 for current state, 0 for others)</li>
 *   <li><b>jplatform_app_threadpool_active</b> (gauge) - Active thread pool threads</li>
 *   <li><b>jplatform_app_threadpool_queued</b> (gauge) - Queued tasks in thread pool</li>
 *   <li><b>jplatform_app_threadpool_completed</b> (counter) - Total completed tasks</li>
 * </ul>
 *
 * <p>All metrics include an {@code app_id} label identifying the application.</p>
 *
 * <p>Example output:</p>
 * <pre>{@code
 * # HELP jplatform_app_cpu_time_seconds Total CPU time used by application
 * # TYPE jplatform_app_cpu_time_seconds counter
 * jplatform_app_cpu_time_seconds{app_id="my-app"} 123.45
 *
 * # HELP jplatform_app_heap_used_bytes Heap memory used by application
 * # TYPE jplatform_app_heap_used_bytes gauge
 * jplatform_app_heap_used_bytes{app_id="my-app"} 134217728
 * }</pre>
 *
 * @see ApplicationContext
 * @see PrometheusFormatter
 */
public final class ApplicationMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationMetricsCollector.class);

    private ApplicationMetricsCollector() {
        // Utility class - prevent instantiation
    }

    /**
     * Collects all metrics from an ApplicationContext and formats them in Prometheus text format.
     * Includes resource metrics, thread pool stats, and application state.
     *
     * @param context the application context to collect metrics from
     * @return formatted Prometheus metrics text, or empty string if collection fails
     */
    public static String collectMetrics(ApplicationContext context) {
        try {
            StringBuilder sb = new StringBuilder();
            String appId = context.getApplicationId();

            // Create label map with app_id
            Map<String, String> labels = new HashMap<>();
            labels.put("app_id", appId);

            // Collect resource metrics if monitoring is available
            if (context.getResourceMonitor() != null) {
                try {
                    ResourceSnapshot snapshot = context.getResourceMonitor().getCurrentSnapshot();
                    collectResourceMetrics(sb, labels, snapshot);
                } catch (Exception e) {
                    logger.warn("Failed to collect resource metrics for {}: {}", appId, e.getMessage());
                }
            } else {
                logger.debug("Resource monitoring not available for {}", appId);
            }

            // Collect thread pool metrics if available
            if (context.getThreadPool() != null) {
                try {
                    ThreadPoolStats poolStats = context.getThreadPool().getStats();
                    collectThreadPoolMetrics(sb, labels, poolStats);
                } catch (Exception e) {
                    logger.warn("Failed to collect thread pool metrics for {}: {}", appId, e.getMessage());
                }
            } else {
                logger.debug("Thread pool not available for {}", appId);
            }

            // Always collect state metrics (doesn't require optional components)
            try {
                ApplicationState state = context.getState();
                collectStateMetrics(sb, labels, state);
            } catch (Exception e) {
                logger.warn("Failed to collect state metrics for {}: {}", appId, e.getMessage());
            }

            return sb.toString();

        } catch (Exception e) {
            logger.error("Failed to collect metrics for application {}: {}",
                    context.getApplicationId(), e.getMessage(), e);
            return "";
        }
    }

    /**
     * Collects resource metrics (CPU, memory, threads) from a ResourceSnapshot.
     *
     * @param sb the StringBuilder to append metrics to
     * @param labels the label map containing app_id
     * @param snapshot the resource snapshot to extract metrics from
     */
    private static void collectResourceMetrics(StringBuilder sb, Map<String, String> labels,
                                               ResourceSnapshot snapshot) {
        // CPU time in seconds (counter)
        sb.append(PrometheusFormatter.formatHelp("jplatform_app_cpu_time_seconds",
                "Total CPU time used by application"));
        sb.append(PrometheusFormatter.formatType("jplatform_app_cpu_time_seconds", "counter"));
        double cpuSeconds = snapshot.getCpuTimeNanos() / 1_000_000_000.0;
        sb.append(PrometheusFormatter.formatCounter("jplatform_app_cpu_time_seconds", labels, cpuSeconds));

        // Heap memory usage (gauge)
        sb.append(PrometheusFormatter.formatHelp("jplatform_app_heap_used_bytes",
                "Heap memory used by application"));
        sb.append(PrometheusFormatter.formatType("jplatform_app_heap_used_bytes", "gauge"));
        sb.append(PrometheusFormatter.formatGauge("jplatform_app_heap_used_bytes", labels,
                snapshot.getHeapUsedBytes()));

        // Thread count (gauge)
        sb.append(PrometheusFormatter.formatHelp("jplatform_app_thread_count",
                "Number of active threads in application"));
        sb.append(PrometheusFormatter.formatType("jplatform_app_thread_count", "gauge"));
        sb.append(PrometheusFormatter.formatGauge("jplatform_app_thread_count", labels,
                snapshot.getThreadCount()));
    }

    /**
     * Collects thread pool metrics (active, queued, completed) from ThreadPoolStats.
     *
     * @param sb the StringBuilder to append metrics to
     * @param labels the label map containing app_id
     * @param stats the thread pool statistics to extract metrics from
     */
    private static void collectThreadPoolMetrics(StringBuilder sb, Map<String, String> labels,
                                                 ThreadPoolStats stats) {
        // Active thread pool threads (gauge)
        sb.append(PrometheusFormatter.formatHelp("jplatform_app_threadpool_active",
                "Number of threads actively executing tasks"));
        sb.append(PrometheusFormatter.formatType("jplatform_app_threadpool_active", "gauge"));
        sb.append(PrometheusFormatter.formatGauge("jplatform_app_threadpool_active", labels,
                stats.getActiveThreads()));

        // Queued tasks (gauge)
        sb.append(PrometheusFormatter.formatHelp("jplatform_app_threadpool_queued",
                "Number of tasks waiting in thread pool queue"));
        sb.append(PrometheusFormatter.formatType("jplatform_app_threadpool_queued", "gauge"));
        sb.append(PrometheusFormatter.formatGauge("jplatform_app_threadpool_queued", labels,
                stats.getQueuedTasks()));

        // Completed tasks (counter)
        sb.append(PrometheusFormatter.formatHelp("jplatform_app_threadpool_completed",
                "Total number of completed tasks"));
        sb.append(PrometheusFormatter.formatType("jplatform_app_threadpool_completed", "counter"));
        sb.append(PrometheusFormatter.formatCounter("jplatform_app_threadpool_completed", labels,
                stats.getCompletedTasks()));
    }

    /**
     * Collects application state metrics.
     * Creates a gauge metric for each possible state, with value 1 for current state and 0 for others.
     *
     * @param sb the StringBuilder to append metrics to
     * @param labels the base label map containing app_id
     * @param currentState the current application state
     */
    private static void collectStateMetrics(StringBuilder sb, Map<String, String> labels,
                                           ApplicationState currentState) {
        sb.append(PrometheusFormatter.formatHelp("jplatform_app_state",
                "Application lifecycle state (1 for current state, 0 for others)"));
        sb.append(PrometheusFormatter.formatType("jplatform_app_state", "gauge"));

        // Create a gauge for each possible state
        for (ApplicationState state : ApplicationState.values()) {
            Map<String, String> stateLabels = new HashMap<>(labels);
            stateLabels.put("state", state.name().toLowerCase());

            double value = (state == currentState) ? 1.0 : 0.0;
            sb.append(PrometheusFormatter.formatGauge("jplatform_app_state", stateLabels, value));
        }
    }
}
