package org.flossware.jplatform.metrics.prometheus;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for formatting metrics in Prometheus text exposition format.
 * Provides static methods to convert metrics into the Prometheus text format with proper label escaping.
 *
 * <p>The Prometheus text format consists of:</p>
 * <ul>
 *   <li>HELP lines describing the metric</li>
 *   <li>TYPE lines declaring the metric type (gauge, counter, etc.)</li>
 *   <li>Metric lines with labels and values</li>
 * </ul>
 *
 * <p>Example output:</p>
 * <pre>{@code
 * # HELP jplatform_app_cpu_time_seconds Total CPU time used by application
 * # TYPE jplatform_app_cpu_time_seconds counter
 * jplatform_app_cpu_time_seconds{app_id="my-app"} 123.45
 * }</pre>
 *
 * @see <a href="https://prometheus.io/docs/instrumenting/exposition_formats/">Prometheus Exposition Formats</a>
 */
public final class PrometheusFormatter {

    private PrometheusFormatter() {
        // Utility class - prevent instantiation
    }

    /**
     * Formats a HELP line describing a metric.
     * HELP lines provide human-readable documentation for metrics.
     *
     * @param name the metric name
     * @param help the help text describing the metric
     * @return formatted HELP line
     */
    public static String formatHelp(String name, String help) {
        return "# HELP " + name + " " + help + "\n";
    }

    /**
     * Formats a TYPE line declaring a metric type.
     * Common types are: counter, gauge, histogram, summary.
     *
     * @param name the metric name
     * @param type the metric type (counter, gauge, etc.)
     * @return formatted TYPE line
     */
    public static String formatType(String name, String type) {
        return "# TYPE " + name + " " + type + "\n";
    }

    /**
     * Formats a gauge metric with labels and value.
     * Gauges represent values that can go up or down (e.g., memory usage, active connections).
     *
     * @param name the metric name
     * @param labels the label key-value pairs (may be empty or null)
     * @param value the gauge value
     * @return formatted gauge metric line
     */
    public static String formatGauge(String name, Map<String, String> labels, double value) {
        return formatMetric(name, labels, value);
    }

    /**
     * Formats a counter metric with labels and value.
     * Counters represent cumulative values that only increase (e.g., total requests, CPU time).
     *
     * @param name the metric name
     * @param labels the label key-value pairs (may be empty or null)
     * @param value the counter value
     * @return formatted counter metric line
     */
    public static String formatCounter(String name, Map<String, String> labels, double value) {
        return formatMetric(name, labels, value);
    }

    /**
     * Formats a metric line with name, labels, and value.
     *
     * @param name the metric name
     * @param labels the label key-value pairs (may be empty or null)
     * @param value the metric value
     * @return formatted metric line
     */
    private static String formatMetric(String name, Map<String, String> labels, double value) {
        StringBuilder sb = new StringBuilder();
        sb.append(name);

        if (labels != null && !labels.isEmpty()) {
            sb.append("{");
            sb.append(formatLabels(labels));
            sb.append("}");
        }

        sb.append(" ");
        sb.append(formatValue(value));
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Formats label key-value pairs with proper escaping.
     * Labels are formatted as: key1="value1",key2="value2"
     *
     * @param labels the label key-value pairs
     * @return formatted label string
     */
    private static String formatLabels(Map<String, String> labels) {
        return labels.entrySet().stream()
                .map(entry -> entry.getKey() + "=\"" + escapeLabelValue(entry.getValue()) + "\"")
                .collect(Collectors.joining(","));
    }

    /**
     * Escapes special characters in label values according to Prometheus format.
     * Escapes backslashes, double quotes, and newlines.
     *
     * @param value the label value to escape (may be null)
     * @return escaped label value, or empty string if value is null
     */
    private static String escapeLabelValue(String value) {
        if (value == null) {
            return "";  // Prometheus doesn't allow null values, use empty string
        }
        return value
                .replace("\\", "\\\\")  // Escape backslashes first
                .replace("\"", "\\\"")  // Escape double quotes
                .replace("\n", "\\n");  // Escape newlines
    }

    /**
     * Formats a numeric value for Prometheus.
     * Special handling for NaN and infinity values.
     *
     * @param value the value to format
     * @return formatted value string
     */
    private static String formatValue(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        } else if (value == Double.POSITIVE_INFINITY) {
            return "+Inf";
        } else if (value == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        } else {
            return Double.toString(value);
        }
    }
}
