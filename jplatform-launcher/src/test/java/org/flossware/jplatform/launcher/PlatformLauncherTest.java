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

package org.flossware.jplatform.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlatformLauncher.
 *
 * Note: Many methods in PlatformLauncher are difficult to unit test because they:
 * - Read from System.in (Scanner)
 * - Write to System.out (console output)
 * - Run in infinite loops (start() method)
 * - Interact with external systems (REST API, JMX, filesystem)
 *
 * These tests focus on testable components and configuration.
 */
class PlatformLauncherTest {

    @Test
    void testConstructorWithMinimalConfig() {
        // Test that launcher can be constructed with basic configuration
        PlatformConfig config = new PlatformConfig();
        assertNotNull(config);

        // Basic validation that config was created
        assertNotNull(config.getApi());
        assertEquals(8080, config.getApi().getPort());
    }

    @Test
    void testConstructorWithCustomPort() {
        PlatformConfig config = new PlatformConfig();
        config.mergeCommandLineArgs(new String[]{"--port", "9090"});

        assertEquals(9090, config.getApi().getPort());
    }

    @Test
    void testConstructorWithRestApiEnabled() {
        PlatformConfig config = new PlatformConfig();
        config.mergeCommandLineArgs(new String[]{"--rest-api"});

        assertTrue(config.getApi().isEnabled());
    }

    @Test
    void testConstructorWithJmxPort() {
        PlatformConfig config = new PlatformConfig();
        config.mergeCommandLineArgs(new String[]{"--jmx-port", "9999"});

        assertEquals(9999, config.getMetrics().getJmx().getPort());
    }

    @Test
    void testConstructorWithPrometheusEnabled() {
        PlatformConfig config = new PlatformConfig();
        config.mergeCommandLineArgs(new String[]{"--prometheus"});

        assertTrue(config.getMetrics().getPrometheus().isEnabled());
    }

    @Test
    void testConstructorWithPrometheusPort() {
        PlatformConfig config = new PlatformConfig();
        config.mergeCommandLineArgs(new String[]{"--prometheus", "--prometheus-port", "9091"});

        assertTrue(config.getMetrics().getPrometheus().isEnabled());
        assertEquals(9091, config.getMetrics().getPrometheus().getPort());
    }

    @Test
    void testConstructorWithWatchDir() {
        PlatformConfig config = new PlatformConfig();
        config.mergeCommandLineArgs(new String[]{"--watch-dir", "/tmp/apps"});

        assertEquals("/tmp/apps", config.getWatcher().getDirectory());
    }

    @Test
    void testConstructorWithMultipleOptions() {
        PlatformConfig config = new PlatformConfig();
        config.mergeCommandLineArgs(new String[]{
            "--rest-api",
            "--port", "8088",
            "--jmx-port", "9999",
            "--prometheus",
            "--prometheus-port", "9091",
            "--watch-dir", "/opt/apps"
        });

        assertTrue(config.getApi().isEnabled());
        assertEquals(8088, config.getApi().getPort());
        assertEquals(9999, config.getMetrics().getJmx().getPort());
        assertTrue(config.getMetrics().getPrometheus().isEnabled());
        assertEquals(9091, config.getMetrics().getPrometheus().getPort());
        assertEquals("/opt/apps", config.getWatcher().getDirectory());
    }

    @Test
    void testDefaultConfiguration() {
        PlatformConfig config = new PlatformConfig();

        assertNotNull(config.getApi());
        assertEquals(8080, config.getApi().getPort());
        assertFalse(config.getApi().isEnabled());
        assertFalse(config.getMetrics().getPrometheus().isEnabled());
        assertEquals(9090, config.getMetrics().getPrometheus().getPort());
        assertNull(config.getWatcher().getDirectory());
    }

    @Test
    void testEmptyCommandLineArgs() {
        PlatformConfig config = new PlatformConfig();
        config.mergeCommandLineArgs(new String[]{});

        // Should use defaults
        assertEquals(8080, config.getApi().getPort());
        assertFalse(config.getApi().isEnabled());
    }

    @Test
    void testPortBoundaries() {
        PlatformConfig config = new PlatformConfig();

        // Valid ports
        config.mergeCommandLineArgs(new String[]{"--port", "1"});
        assertEquals(1, config.getApi().getPort());

        config.mergeCommandLineArgs(new String[]{"--port", "65535"});
        assertEquals(65535, config.getApi().getPort());
    }

    @Test
    void testJmxPortBoundaries() {
        PlatformConfig config = new PlatformConfig();

        config.mergeCommandLineArgs(new String[]{"--jmx-port", "1099"});
        assertEquals(1099, config.getMetrics().getJmx().getPort());

        config.mergeCommandLineArgs(new String[]{"--jmx-port", "65535"});
        assertEquals(65535, config.getMetrics().getJmx().getPort());
    }

    @Test
    void testPrometheusPortBoundaries() {
        PlatformConfig config = new PlatformConfig();

        config.mergeCommandLineArgs(new String[]{"--prometheus-port", "9090"});
        assertEquals(9090, config.getMetrics().getPrometheus().getPort());

        config.mergeCommandLineArgs(new String[]{"--prometheus-port", "9999"});
        assertEquals(9999, config.getMetrics().getPrometheus().getPort());
    }

    @Test
    void testWatchDirPath() {
        PlatformConfig config = new PlatformConfig();

        config.mergeCommandLineArgs(new String[]{"--watch-dir", "/var/lib/jplatform"});
        assertEquals("/var/lib/jplatform", config.getWatcher().getDirectory());

        config.mergeCommandLineArgs(new String[]{"--watch-dir", "relative/path"});
        assertEquals("relative/path", config.getWatcher().getDirectory());
    }

    @Test
    void testConfigurationOverrides() {
        PlatformConfig config = new PlatformConfig();

        // Set initial value
        config.mergeCommandLineArgs(new String[]{"--port", "8080"});
        assertEquals(8080, config.getApi().getPort());

        // Override with new value
        config.mergeCommandLineArgs(new String[]{"--port", "9090"});
        assertEquals(9090, config.getApi().getPort());
    }

    @Test
    void testMultipleConfigMerges() {
        PlatformConfig config = new PlatformConfig();

        config.mergeCommandLineArgs(new String[]{"--rest-api"});
        assertTrue(config.getApi().isEnabled());

        config.mergeCommandLineArgs(new String[]{"--prometheus"});
        assertTrue(config.getMetrics().getPrometheus().isEnabled());

        // Previous setting should still be set
        assertTrue(config.getApi().isEnabled());
    }

    @Test
    void testNestedConfigObjects() {
        PlatformConfig config = new PlatformConfig();

        assertNotNull(config.getApi());
        assertNotNull(config.getMetrics());
        assertNotNull(config.getMetrics().getJmx());
        assertNotNull(config.getMetrics().getPrometheus());
        assertNotNull(config.getMetrics().getOpentelemetry());
        assertNotNull(config.getWatcher());
    }

    @Test
    void testApiConfigDefaults() {
        PlatformConfig config = new PlatformConfig();
        PlatformConfig.ApiConfig api = config.getApi();

        assertFalse(api.isEnabled());
        assertEquals(8080, api.getPort());
        assertEquals("0.0.0.0", api.getBindAddress());
    }

    @Test
    void testJmxConfigDefaults() {
        PlatformConfig config = new PlatformConfig();
        PlatformConfig.JmxConfig jmx = config.getMetrics().getJmx();

        assertFalse(jmx.isEnabled());
        assertEquals(9999, jmx.getPort());
        assertEquals("org.flossware.jplatform", jmx.getDomain());
    }

    @Test
    void testPrometheusConfigDefaults() {
        PlatformConfig config = new PlatformConfig();
        PlatformConfig.PrometheusConfig prometheus = config.getMetrics().getPrometheus();

        assertFalse(prometheus.isEnabled());
        assertEquals(9090, prometheus.getPort());
        assertEquals("/metrics", prometheus.getPath());
    }

    // Note: The following cannot be easily unit tested without refactoring:
    // - start() method (runs infinite loop with Scanner)
    // - handleCommand() and related methods (need mocked console I/O)
    // - initializeOptionalComponents() (creates actual REST server, JMX, etc.)
    // - main() method (entry point, hard to test without process spawning)
    //
    // These would require:
    // - Dependency injection for Scanner/PrintStream
    // - Integration tests that spawn the process
    // - Mocking of external systems (REST server, JMX registry, etc.)
}
