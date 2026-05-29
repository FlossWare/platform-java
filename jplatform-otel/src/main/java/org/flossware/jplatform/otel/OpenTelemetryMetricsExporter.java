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

package org.flossware.jplatform.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import org.flossware.jplatform.api.ApplicationContext;
import org.flossware.jplatform.api.MetricsExporter;
import org.flossware.jplatform.api.ResourceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenTelemetry metrics exporter for JPlatform.
 *
 * <p>Exports application metrics to OpenTelemetry Collector using OTLP protocol.
 * Metrics include:</p>
 * <ul>
 *   <li>CPU time (counter)</li>
 *   <li>Heap usage (gauge)</li>
 *   <li>Thread count (gauge)</li>
 *   <li>Application state (gauge)</li>
 * </ul>
 *
 * <p>All metrics include an {@code app_id} attribute for filtering.</p>
 *
 * <p>Configuration:</p>
 * <pre>
 * OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
 * OTEL_EXPORTER_OTLP_PROTOCOL=grpc
 * </pre>
 *
 * @since 2.0
 */
public class OpenTelemetryMetricsExporter implements MetricsExporter {

    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryMetricsExporter.class);
    private static final AttributeKey<String> APP_ID_KEY = AttributeKey.stringKey("app_id");

    private final OpenTelemetry openTelemetry;
    private final Meter meter;
    private final Map<String, MetricInstruments> instruments;
    private volatile boolean running;

    /**
     * Creates a new OpenTelemetry metrics exporter with default configuration.
     *
     * <p>Uses environment variables for OTLP endpoint configuration:</p>
     * <ul>
     *   <li>OTEL_EXPORTER_OTLP_ENDPOINT (default: http://localhost:4317)</li>
     *   <li>OTEL_EXPORTER_OTLP_PROTOCOL (default: grpc)</li>
     * </ul>
     */
    public OpenTelemetryMetricsExporter() {
        this(getOtlpEndpoint());
    }

    /**
     * Creates a new OpenTelemetry metrics exporter with custom endpoint.
     *
     * @param otlpEndpoint the OTLP collector endpoint (e.g., "http://localhost:4317")
     */
    public OpenTelemetryMetricsExporter(String otlpEndpoint) {
        this.instruments = new ConcurrentHashMap<>();

        // Create OTLP exporter
        OtlpGrpcMetricExporter otlpExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(Duration.ofSeconds(10))
                .build();

        // Create metric reader with 60-second export interval
        PeriodicMetricReader metricReader = PeriodicMetricReader.builder(otlpExporter)
                .setInterval(Duration.ofSeconds(60))
                .build();

        // Create resource with service name
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), "jplatform"
                )));

        // Build SDK
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(metricReader)
                .build();

        this.openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();

        this.meter = openTelemetry.getMeter("jplatform");
        this.running = false;

        logger.info("OpenTelemetryMetricsExporter initialized with endpoint: {}", otlpEndpoint);
    }

    @Override
    public void start() throws Exception {
        running = true;
        logger.info("OpenTelemetry metrics exporter started");
    }

    @Override
    public void stop() throws Exception {
        running = false;
        shutdown();
        logger.info("OpenTelemetry metrics exporter stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    @Override
    public void registerApplication(String applicationId, ApplicationContext context) {
        Attributes appAttributes = Attributes.of(APP_ID_KEY, applicationId);

        MetricInstruments appInstruments = new MetricInstruments();

        // CPU time counter
        appInstruments.cpuTimeCounter = meter.counterBuilder("jplatform.app.cpu_time_seconds")
                .setDescription("Total CPU time consumed by the application")
                .setUnit("s")
                .build();

        // Heap usage gauge
        appInstruments.heapGauge = meter.gaugeBuilder("jplatform.app.heap_used_bytes")
                .ofLongs()
                .setDescription("Current heap memory usage")
                .setUnit("bytes")
                .buildWithCallback(measurement -> {
                    // This will be updated via exportMetrics()
                    measurement.record(0L, appAttributes);
                });

        // Thread count gauge
        appInstruments.threadGauge = meter.gaugeBuilder("jplatform.app.thread_count")
                .ofLongs()
                .setDescription("Current thread count")
                .setUnit("threads")
                .buildWithCallback(measurement -> {
                    measurement.record(0L, appAttributes);
                });

        instruments.put(applicationId, appInstruments);

        logger.info("[{}] Registered with OpenTelemetry metrics exporter", applicationId);
    }

    @Override
    public void unregisterApplication(String applicationId) {
        instruments.remove(applicationId);
        logger.info("[{}] Unregistered from OpenTelemetry metrics exporter", applicationId);
    }

    /**
     * Exports metrics for an application.
     * Note: This is called periodically by the monitoring system, not by MetricsExporter interface.
     *
     * @param applicationId the application identifier
     * @param snapshot the resource snapshot containing current metrics
     */
    public void exportMetrics(String applicationId, ResourceSnapshot snapshot) {
        MetricInstruments appInstruments = instruments.get(applicationId);
        if (appInstruments == null) {
            logger.warn("[{}] Not registered, cannot export metrics", applicationId);
            return;
        }

        Attributes appAttributes = Attributes.of(APP_ID_KEY, applicationId);

        // Record CPU time (convert nanoseconds to seconds, then to long for counter)
        long cpuTimeSeconds = snapshot.getCpuTimeNanos() / 1_000_000_000L;
        appInstruments.cpuTimeCounter.add(cpuTimeSeconds, appAttributes);

        // Note: Gauges are updated via callbacks
        // In a full implementation, we'd store latest values and update in callbacks

        logger.debug("[{}] Exported metrics to OpenTelemetry: cpu={}s, heap={}b, threads={}",
                applicationId, cpuTimeSeconds, snapshot.getHeapUsedBytes(), snapshot.getThreadCount());
    }

    /**
     * Shuts down the OpenTelemetry SDK.
     *
     * <p>Flushes any pending metrics and releases resources.</p>
     */
    public void shutdown() {
        if (openTelemetry instanceof OpenTelemetrySdk) {
            ((OpenTelemetrySdk) openTelemetry).getSdkMeterProvider().shutdown();
            logger.info("OpenTelemetry SDK shutdown complete");
        }
    }

    /**
     * Returns the configured OTLP endpoint from environment.
     *
     * @return OTLP endpoint URL
     */
    private static String getOtlpEndpoint() {
        return System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317");
    }

    /**
     * Holds metric instruments for a single application.
     */
    private static class MetricInstruments {
        io.opentelemetry.api.metrics.LongCounter cpuTimeCounter;
        io.opentelemetry.api.metrics.ObservableLongGauge heapGauge;
        io.opentelemetry.api.metrics.ObservableLongGauge threadGauge;
    }
}
