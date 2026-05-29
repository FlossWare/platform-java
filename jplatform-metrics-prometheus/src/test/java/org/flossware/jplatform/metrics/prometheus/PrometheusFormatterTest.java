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

package org.flossware.jplatform.metrics.prometheus;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrometheusFormatterTest {

    @Test
    void testFormatHelp() {
        String result = PrometheusFormatter.formatHelp("my_metric", "This is a test metric");
        assertEquals("# HELP my_metric This is a test metric\n", result);
    }

    @Test
    void testFormatType() {
        String result = PrometheusFormatter.formatType("my_metric", "gauge");
        assertEquals("# TYPE my_metric gauge\n", result);
    }

    @Test
    void testFormatGaugeNoLabels() {
        String result = PrometheusFormatter.formatGauge("my_gauge", null, 42.5);
        assertEquals("my_gauge 42.5\n", result);
    }

    @Test
    void testFormatGaugeEmptyLabels() {
        Map<String, String> labels = new HashMap<>();
        String result = PrometheusFormatter.formatGauge("my_gauge", labels, 42.5);
        assertEquals("my_gauge 42.5\n", result);
    }

    @Test
    void testFormatGaugeWithSingleLabel() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app_id", "test-app");

        String result = PrometheusFormatter.formatGauge("my_gauge", labels, 100.0);
        assertEquals("my_gauge{app_id=\"test-app\"} 100.0\n", result);
    }

    @Test
    void testFormatGaugeWithMultipleLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app_id", "test-app");
        labels.put("environment", "production");

        String result = PrometheusFormatter.formatGauge("my_gauge", labels, 200.0);

        assertTrue(result.startsWith("my_gauge{"));
        assertTrue(result.contains("app_id=\"test-app\""));
        assertTrue(result.contains("environment=\"production\""));
        assertTrue(result.endsWith(" 200.0\n"));
    }

    @Test
    void testFormatCounterNoLabels() {
        String result = PrometheusFormatter.formatCounter("my_counter", null, 1234.0);
        assertEquals("my_counter 1234.0\n", result);
    }

    @Test
    void testFormatCounterWithLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app_id", "test-app");

        String result = PrometheusFormatter.formatCounter("my_counter", labels, 5678.0);
        assertEquals("my_counter{app_id=\"test-app\"} 5678.0\n", result);
    }

    @Test
    void testLabelValueEscaping() {
        Map<String, String> labels = new HashMap<>();
        labels.put("message", "Hello \"World\"\nNew Line\\Path");

        String result = PrometheusFormatter.formatGauge("my_metric", labels, 1.0);
        assertEquals("my_metric{message=\"Hello \\\"World\\\"\\nNew Line\\\\Path\"} 1.0\n", result);
    }

    @Test
    void testFormatValueNaN() {
        String result = PrometheusFormatter.formatGauge("my_metric", null, Double.NaN);
        assertEquals("my_metric NaN\n", result);
    }

    @Test
    void testFormatValuePositiveInfinity() {
        String result = PrometheusFormatter.formatGauge("my_metric", null, Double.POSITIVE_INFINITY);
        assertEquals("my_metric +Inf\n", result);
    }

    @Test
    void testFormatValueNegativeInfinity() {
        String result = PrometheusFormatter.formatGauge("my_metric", null, Double.NEGATIVE_INFINITY);
        assertEquals("my_metric -Inf\n", result);
    }

    @Test
    void testFormatValueInteger() {
        String result = PrometheusFormatter.formatGauge("my_metric", null, 42.0);
        assertEquals("my_metric 42.0\n", result);
    }

    @Test
    void testFormatValueDecimal() {
        String result = PrometheusFormatter.formatGauge("my_metric", null, 3.14159);
        assertEquals("my_metric 3.14159\n", result);
    }

    @Test
    void testFormatValueNegative() {
        String result = PrometheusFormatter.formatGauge("my_metric", null, -123.45);
        assertEquals("my_metric -123.45\n", result);
    }

    @Test
    void testFormatValueZero() {
        String result = PrometheusFormatter.formatGauge("my_metric", null, 0.0);
        assertEquals("my_metric 0.0\n", result);
    }

    @Test
    void testCompleteMetricFormatting() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app_id", "my-app");
        labels.put("state", "running");

        StringBuilder sb = new StringBuilder();
        sb.append(PrometheusFormatter.formatHelp("jplatform_app_state", "Application state"));
        sb.append(PrometheusFormatter.formatType("jplatform_app_state", "gauge"));
        sb.append(PrometheusFormatter.formatGauge("jplatform_app_state", labels, 1.0));

        String result = sb.toString();

        assertTrue(result.contains("# HELP jplatform_app_state Application state\n"));
        assertTrue(result.contains("# TYPE jplatform_app_state gauge\n"));
        assertTrue(result.contains("jplatform_app_state{"));
        assertTrue(result.contains("app_id=\"my-app\""));
        assertTrue(result.contains("state=\"running\""));
        assertTrue(result.contains(" 1.0\n"));
    }

    @Test
    void testLabelValueNull() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app_id", "test-app");
        labels.put("version", null);  // null label value

        // Should not throw NullPointerException, null should be converted to empty string
        String result = PrometheusFormatter.formatGauge("my_metric", labels, 1.0);

        assertNotNull(result);
        assertTrue(result.startsWith("my_metric{"));
        assertTrue(result.contains("app_id=\"test-app\""));
        assertTrue(result.contains("version=\"\""));  // null becomes empty string
        assertTrue(result.endsWith(" 1.0\n"));
    }
}
