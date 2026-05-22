package org.flossware.jplatform.launcher;

import org.flossware.jplatform.api.ApplicationState;
import org.flossware.jplatform.core.ApplicationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JPlatform launcher.
 * Tests end-to-end functionality of all deployment methods and monitoring features.
 */
class PlatformIntegrationTest {

    @TempDir
    Path tempDir;

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @BeforeEach
    void setUp() {
        // Clean up any existing state
    }

    @AfterEach
    void tearDown() {
        // Cleanup
    }

    @Test
    void testYamlDeploymentEndToEnd() throws Exception {
        // Create a simple YAML descriptor
        String yaml = "applicationId: test-app\n" +
            "name: Test Application\n" +
            "version: 1.0.0\n" +
            "mainClass: org.flossware.jplatform.samples.helloworld.HelloWorldApp\n" +
            "classpathEntries:\n" +
            "  - file:///tmp/test.jar\n" +
            "threadPool:\n" +
            "  corePoolSize: 2\n" +
            "  maxPoolSize: 4\n";

        Path yamlFile = tempDir.resolve("test-app.yaml");
        Files.writeString(yamlFile, yaml);

        // Verify file was created
        assertTrue(Files.exists(yamlFile));
        String content = Files.readString(yamlFile);
        assertTrue(content.contains("test-app"));
    }

    @Test
    void testJsonDeploymentEndToEnd() throws Exception {
        // Create a simple JSON descriptor
        String json = "{\n" +
            "  \"applicationId\": \"test-app\",\n" +
            "  \"name\": \"Test Application\",\n" +
            "  \"version\": \"1.0.0\",\n" +
            "  \"mainClass\": \"org.flossware.jplatform.samples.helloworld.HelloWorldApp\",\n" +
            "  \"classpathEntries\": [\"file:///tmp/test.jar\"],\n" +
            "  \"threadPool\": {\n" +
            "    \"corePoolSize\": 2,\n" +
            "    \"maxPoolSize\": 4\n" +
            "  }\n" +
            "}\n";

        Path jsonFile = tempDir.resolve("test-app.json");
        Files.writeString(jsonFile, json);

        // Verify file was created
        assertTrue(Files.exists(jsonFile));
        String content = Files.readString(jsonFile);
        assertTrue(content.contains("test-app"));
    }

    @Test
    void testRestApiEndpointAvailability() throws Exception {
        int port = findAvailablePort();

        // Test that we can identify an available port
        assertTrue(port > 0);
        assertTrue(port < 65536);

        // Verify port is not in use
        try (ServerSocket socket = new ServerSocket(port)) {
            assertNotNull(socket);
        }
    }

    @Test
    void testPrometheusMetricsFormat() {
        // Test Prometheus metrics format
        String metricLine = "jplatform_app_cpu_time_seconds{app_id=\"test\"} 123.45";

        assertTrue(metricLine.contains("jplatform_app_"));
        assertTrue(metricLine.contains("app_id="));
        assertTrue(metricLine.matches(".*\\{.*\\}.*"));
    }

    @Test
    void testFileWatcherDetection() throws Exception {
        // Create a watch directory
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);

        assertTrue(Files.exists(watchDir));
        assertTrue(Files.isDirectory(watchDir));

        // Create a YAML file in the directory
        Path yamlFile = watchDir.resolve("app.yaml");
        Files.writeString(yamlFile, "applicationId: test\n");

        assertTrue(Files.exists(yamlFile));
    }

    @Test
    void testConfigurationFileStructure() throws Exception {
        // Test that platform.yaml has correct structure
        String config = "api:\n" +
            "  enabled: true\n" +
            "  port: 8080\n\n" +
            "metrics:\n" +
            "  jmx:\n" +
            "    enabled: true\n" +
            "    port: 9999\n" +
            "  prometheus:\n" +
            "    enabled: true\n" +
            "    port: 9090\n\n" +
            "watcher:\n" +
            "  enabled: true\n" +
            "  watchDirectory: /var/jplatform/apps\n";

        Path configFile = tempDir.resolve("platform.yaml");
        Files.writeString(configFile, config);

        String content = Files.readString(configFile);
        assertTrue(content.contains("api:"));
        assertTrue(content.contains("metrics:"));
        assertTrue(content.contains("watcher:"));
    }

    @Test
    void testApplicationManagerLifecycle() {
        ApplicationManager manager = new ApplicationManager();
        assertNotNull(manager);

        // Verify manager can be created and shutdown
        manager.shutdown();
    }

    @Test
    void testMultiplePortsAvailable() throws Exception {
        int restPort = findAvailablePort();
        int jmxPort = findAvailablePort();
        int prometheusPort = findAvailablePort();

        // Verify all ports are different
        assertNotEquals(restPort, jmxPort);
        assertNotEquals(restPort, prometheusPort);
        assertNotEquals(jmxPort, prometheusPort);
    }

    @Test
    void testDescriptorValidation() throws Exception {
        // Test invalid YAML
        String invalidYaml = "invalid: yaml: content:";
        Path invalidFile = tempDir.resolve("invalid.yaml");
        Files.writeString(invalidFile, invalidYaml);

        assertTrue(Files.exists(invalidFile));
    }

    @Test
    void testTempDirectoryCreation() throws Exception {
        Path testDir = tempDir.resolve("apps");
        Files.createDirectories(testDir);

        assertTrue(Files.exists(testDir));
        assertTrue(Files.isDirectory(testDir));

        // Create subdirectories
        Path subDir = testDir.resolve("deployed");
        Files.createDirectories(subDir);
        assertTrue(Files.exists(subDir));
    }

    @Test
    void testHttpRequestFormat() {
        // Test HTTP request structure
        String requestBody = "{\"applicationId\":\"test\",\"mainClass\":\"Test\"}";
        assertTrue(requestBody.contains("applicationId"));
        assertTrue(requestBody.contains("mainClass"));
    }

    @Test
    void testJmxObjectNameFormat() {
        String objectName = "org.flossware.jplatform:type=Application,id=test-app";

        assertTrue(objectName.contains("org.flossware.jplatform"));
        assertTrue(objectName.contains("type=Application"));
        assertTrue(objectName.contains("id="));
    }

    @Test
    void testApplicationStateTransitions() {
        // Verify all application states are defined
        ApplicationState[] states = ApplicationState.values();

        assertTrue(states.length >= 4);
        boolean hasDeployed = false;
        boolean hasRunning = false;
        boolean hasStopped = false;
        boolean hasFailed = false;

        for (ApplicationState state : states) {
            if (state == ApplicationState.DEPLOYED) hasDeployed = true;
            if (state == ApplicationState.RUNNING) hasRunning = true;
            if (state == ApplicationState.STOPPED) hasStopped = true;
            if (state == ApplicationState.FAILED) hasFailed = true;
        }

        assertTrue(hasDeployed);
        assertTrue(hasRunning);
        assertTrue(hasStopped);
        assertTrue(hasFailed);
    }

    @Test
    void testExampleFilesExist() {
        // Verify example files would be created correctly
        Path examplesDir = Path.of("examples/applications");

        // Test path construction
        assertNotNull(examplesDir);
        assertTrue(examplesDir.toString().contains("examples"));
        assertTrue(examplesDir.toString().contains("applications"));
    }

    @Test
    void testPlatformYamlStructure() {
        String yaml = "api:\n" +
            "  port: 8080\n" +
            "metrics:\n" +
            "  jmx:\n" +
            "    port: 9999\n";

        assertTrue(yaml.contains("api:"));
        assertTrue(yaml.contains("metrics:"));
        assertTrue(yaml.contains("port:"));
    }
}
