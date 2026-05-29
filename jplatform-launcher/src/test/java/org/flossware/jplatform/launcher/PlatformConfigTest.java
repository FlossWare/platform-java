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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlatformConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void testDefaultConfiguration() {
        PlatformConfig config = new PlatformConfig();

        assertNotNull(config.getApi());
        assertNotNull(config.getMetrics());
        assertNotNull(config.getWatcher());

        assertFalse(config.getApi().isEnabled());
        assertEquals(8080, config.getApi().getPort());

        assertFalse(config.getMetrics().getJmx().isEnabled());
        assertEquals(9999, config.getMetrics().getJmx().getPort());

        assertFalse(config.getMetrics().getPrometheus().isEnabled());
        assertEquals(9090, config.getMetrics().getPrometheus().getPort());

        assertFalse(config.getWatcher().isEnabled());
    }

    @Test
    void testLoadNonExistentFile() {
        PlatformConfig config = PlatformConfig.load("nonexistent.yaml");

        assertNotNull(config);
        assertFalse(config.getApi().isEnabled());
    }

    @Test
    void testLoadValidYaml() throws Exception {
        String yaml = "api:\n" +
            "  enabled: true\n" +
            "  port: 9000\n" +
            "metrics:\n" +
            "  jmx:\n" +
            "    enabled: true\n" +
            "    port: 10000\n" +
            "  prometheus:\n" +
            "    enabled: true\n" +
            "    port: 10001\n" +
            "watcher:\n" +
            "  enabled: true\n" +
            "  watchDirectory: /tmp/watch\n";

        Path configFile = tempDir.resolve("test-config.yaml");
        Files.writeString(configFile, yaml);

        PlatformConfig config = PlatformConfig.load(configFile.toString());

        assertTrue(config.getApi().isEnabled());
        assertEquals(9000, config.getApi().getPort());

        assertTrue(config.getMetrics().getJmx().isEnabled());
        assertEquals(10000, config.getMetrics().getJmx().getPort());

        assertTrue(config.getMetrics().getPrometheus().isEnabled());
        assertEquals(10001, config.getMetrics().getPrometheus().getPort());

        assertTrue(config.getWatcher().isEnabled());
        assertEquals("/tmp/watch", config.getWatcher().getWatchDirectory());
    }

    @Test
    void testMergeCommandLineArgs() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {"--rest-api", "--port", "8888", "--jmx-port", "9998"};
        config.mergeCommandLineArgs(args);

        assertTrue(config.getApi().isEnabled());
        assertEquals(8888, config.getApi().getPort());

        assertTrue(config.getMetrics().getJmx().isEnabled());
        assertEquals(9998, config.getMetrics().getJmx().getPort());
    }

    @Test
    void testCommandLineOverridesFile() throws Exception {
        String yaml = "api:\n" +
            "  enabled: false\n" +
            "  port: 8080\n";

        Path configFile = tempDir.resolve("test-config.yaml");
        Files.writeString(configFile, yaml);

        PlatformConfig config = PlatformConfig.load(configFile.toString());

        // File says disabled
        assertFalse(config.getApi().isEnabled());
        assertEquals(8080, config.getApi().getPort());

        // Command line enables and changes port
        String[] args = {"--rest-api", "--port", "9999"};
        config.mergeCommandLineArgs(args);

        assertTrue(config.getApi().isEnabled());
        assertEquals(9999, config.getApi().getPort());
    }

    @Test
    void testPrometheusFlags() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {"--prometheus", "--prometheus-port", "9091"};
        config.mergeCommandLineArgs(args);

        assertTrue(config.getMetrics().getPrometheus().isEnabled());
        assertEquals(9091, config.getMetrics().getPrometheus().getPort());
    }

    @Test
    void testWatchDirFlag() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {"--watch-dir", "/var/jplatform/apps"};
        config.mergeCommandLineArgs(args);

        assertTrue(config.getWatcher().isEnabled());
        assertEquals("/var/jplatform/apps", config.getWatcher().getWatchDirectory());
    }

    @Test
    void testInvalidYaml() throws Exception {
        String invalidYaml = "invalid: yaml: : : content";

        Path configFile = tempDir.resolve("invalid.yaml");
        Files.writeString(configFile, invalidYaml);

        PlatformConfig config = PlatformConfig.load(configFile.toString());

        // Should return defaults on parse error
        assertNotNull(config);
        assertFalse(config.getApi().isEnabled());
    }

    @Test
    void testPartialConfiguration() throws Exception {
        String yaml = "api:\n" +
            "  enabled: true\n";

        Path configFile = tempDir.resolve("partial.yaml");
        Files.writeString(configFile, yaml);

        PlatformConfig config = PlatformConfig.load(configFile.toString());

        assertTrue(config.getApi().isEnabled());
        assertEquals(8080, config.getApi().getPort()); // Default port

        assertNotNull(config.getMetrics());
        assertFalse(config.getMetrics().getJmx().isEnabled()); // Not specified, so default
    }

    @Test
    void testWebConsoleFlag() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {"--web-console"};
        config.mergeCommandLineArgs(args);

        // Web console should enable API
        assertTrue(config.getApi().isEnabled());
    }

    @Test
    void testConfigFileFlag() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {"--config", "custom.yaml", "--rest-api"};
        config.mergeCommandLineArgs(args);

        // Should still process other flags
        assertTrue(config.getApi().isEnabled());
    }

    @Test
    void testEmptyCommandLineArgs() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {};
        config.mergeCommandLineArgs(args);

        assertFalse(config.getApi().isEnabled());
        assertEquals(8080, config.getApi().getPort());
    }

    @Test
    void testNullCommandLineArgs() {
        PlatformConfig config = new PlatformConfig();

        assertThrows(NullPointerException.class, () ->
            config.mergeCommandLineArgs(null)
        );
    }

    @Test
    void testMultipleFlags() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {
            "--rest-api",
            "--port", "8888",
            "--jmx-port", "9998",
            "--prometheus",
            "--prometheus-port", "9091",
            "--watch-dir", "/apps"
        };
        config.mergeCommandLineArgs(args);

        assertTrue(config.getApi().isEnabled());
        assertEquals(8888, config.getApi().getPort());

        assertTrue(config.getMetrics().getJmx().isEnabled());
        assertEquals(9998, config.getMetrics().getJmx().getPort());

        assertTrue(config.getMetrics().getPrometheus().isEnabled());
        assertEquals(9091, config.getMetrics().getPrometheus().getPort());

        assertTrue(config.getWatcher().isEnabled());
        assertEquals("/apps", config.getWatcher().getWatchDirectory());
    }

    @Test
    void testInvalidPortValue() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {"--port", "invalid"};
        assertThrows(IllegalArgumentException.class, () ->
            config.mergeCommandLineArgs(args)
        );
    }

    @Test
    void testMissingPortValue() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {"--port"};
        // Missing value is ignored, no exception
        assertDoesNotThrow(() -> config.mergeCommandLineArgs(args));
        // Port should remain default
        assertEquals(8080, config.getApi().getPort());
    }

    @Test
    void testLoadNullPath() {
        assertThrows(NullPointerException.class, () ->
            PlatformConfig.load(null)
        );
    }

    @Test
    void testGetApiConfig() {
        PlatformConfig config = new PlatformConfig();

        assertNotNull(config.getApi());
        assertSame(config.getApi(), config.getApi());
    }

    @Test
    void testGetMetricsConfig() {
        PlatformConfig config = new PlatformConfig();

        assertNotNull(config.getMetrics());
        assertSame(config.getMetrics(), config.getMetrics());
    }

    @Test
    void testGetWatcherConfig() {
        PlatformConfig config = new PlatformConfig();

        assertNotNull(config.getWatcher());
        assertSame(config.getWatcher(), config.getWatcher());
    }

    @Test
    void testDefaultPortValues() {
        PlatformConfig config = new PlatformConfig();

        assertEquals(8080, config.getApi().getPort());
        assertEquals(9999, config.getMetrics().getJmx().getPort());
        assertEquals(9090, config.getMetrics().getPrometheus().getPort());
    }

    @Test
    void testYamlWithOnlyMetrics() throws Exception {
        String yaml = "metrics:\n" +
            "  jmx:\n" +
            "    enabled: true\n";

        Path configFile = tempDir.resolve("metrics-only.yaml");
        Files.writeString(configFile, yaml);

        PlatformConfig config = PlatformConfig.load(configFile.toString());

        assertTrue(config.getMetrics().getJmx().isEnabled());
        assertFalse(config.getApi().isEnabled());
    }

    @Test
    void testYamlWithOnlyWatcher() throws Exception {
        String yaml = "watcher:\n" +
            "  enabled: true\n" +
            "  watchDirectory: /watch\n";

        Path configFile = tempDir.resolve("watcher-only.yaml");
        Files.writeString(configFile, yaml);

        PlatformConfig config = PlatformConfig.load(configFile.toString());

        assertTrue(config.getWatcher().isEnabled());
        assertEquals("/watch", config.getWatcher().getWatchDirectory());
        assertFalse(config.getApi().isEnabled());
    }

    @Test
    void testDuplicateFlags() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {"--port", "8000", "--port", "9000"};
        config.mergeCommandLineArgs(args);

        // Last one wins
        assertEquals(9000, config.getApi().getPort());
    }

    @Test
    void testUnknownFlags() {
        PlatformConfig config = new PlatformConfig();

        String[] args = {"--unknown-flag", "--rest-api"};
        assertDoesNotThrow(() -> config.mergeCommandLineArgs(args));

        assertTrue(config.getApi().isEnabled());
    }
}
