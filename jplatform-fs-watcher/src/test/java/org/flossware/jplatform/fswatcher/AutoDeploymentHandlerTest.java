package org.flossware.jplatform.fswatcher;

import org.flossware.jplatform.api.ApplicationDescriptor;
import org.flossware.jplatform.api.ApplicationDescriptorParser;
import org.flossware.jplatform.api.ParseException;
import org.flossware.jplatform.api.WatcherConfig;
import org.flossware.jplatform.core.ApplicationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AutoDeploymentHandler.
 * Tests auto-deploy, auto-start, error handling, and listener notifications.
 */
class AutoDeploymentHandlerTest {

    @TempDir
    Path tempDir;

    private ApplicationManager mockApplicationManager;
    private ApplicationDescriptorParser mockYamlParser;
    private ApplicationDescriptorParser mockJsonParser;
    private Map<String, ApplicationDescriptorParser> parsers;
    private WatcherConfig config;
    private AutoDeploymentHandler handler;

    @BeforeEach
    void setUp() {
        mockApplicationManager = mock(ApplicationManager.class);
        mockYamlParser = mock(ApplicationDescriptorParser.class);
        mockJsonParser = mock(ApplicationDescriptorParser.class);

        parsers = new HashMap<>();
        parsers.put("yaml", mockYamlParser);
        parsers.put("json", mockJsonParser);

        config = WatcherConfig.builder()
                .watchDirectory(tempDir)
                .autoDeploy(true)
                .autoStart(true)
                .debounceMillis(100)
                .build();

        handler = new AutoDeploymentHandler(mockApplicationManager, parsers, config);
    }

    @Test
    void testConstructorNullApplicationManager() {
        assertThrows(NullPointerException.class, () -> {
            new AutoDeploymentHandler(null, parsers, config);
        });
    }

    @Test
    void testConstructorNullParsers() {
        assertThrows(NullPointerException.class, () -> {
            new AutoDeploymentHandler(mockApplicationManager, null, config);
        });
    }

    @Test
    void testConstructorNullConfig() {
        assertThrows(NullPointerException.class, () -> {
            new AutoDeploymentHandler(mockApplicationManager, parsers, null);
        });
    }

    @Test
    void testConstructorCreatesRegistry() {
        assertNotNull(handler.getRegistry());
        assertEquals(0, handler.getRegistry().size());
    }

    @Test
    void testOnDescriptorDetectedWithAutoDeployAndAutoStart() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        ApplicationDescriptor mockDescriptor = mock(ApplicationDescriptor.class);
        when(mockDescriptor.getApplicationId()).thenReturn("test-app");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(mockDescriptor);

        handler.onDescriptorDetected(descriptorFile);

        verify(mockYamlParser).parseFile(descriptorFile);
        verify(mockApplicationManager).deploy(mockDescriptor);
        verify(mockApplicationManager).start("test-app");

        assertEquals("test-app", handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorDetectedWithAutoDeployOnly() throws Exception {
        config = WatcherConfig.builder()
                .watchDirectory(tempDir)
                .autoDeploy(true)
                .autoStart(false)
                .build();
        handler = new AutoDeploymentHandler(mockApplicationManager, parsers, config);

        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        ApplicationDescriptor mockDescriptor = mock(ApplicationDescriptor.class);
        when(mockDescriptor.getApplicationId()).thenReturn("test-app");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(mockDescriptor);

        handler.onDescriptorDetected(descriptorFile);

        verify(mockYamlParser).parseFile(descriptorFile);
        verify(mockApplicationManager).deploy(mockDescriptor);
        verify(mockApplicationManager, never()).start(anyString());

        assertEquals("test-app", handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorDetectedWithAutoDeployDisabled() throws Exception {
        config = WatcherConfig.builder()
                .watchDirectory(tempDir)
                .autoDeploy(false)
                .autoStart(true)
                .build();
        handler = new AutoDeploymentHandler(mockApplicationManager, parsers, config);

        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        handler.onDescriptorDetected(descriptorFile);

        verify(mockYamlParser, never()).parseFile(any());
        verify(mockApplicationManager, never()).deploy(any());
        verify(mockApplicationManager, never()).start(anyString());

        assertNull(handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorDetectedJsonFile() throws Exception {
        Path descriptorFile = tempDir.resolve("app.json");
        Files.createFile(descriptorFile);

        ApplicationDescriptor mockDescriptor = mock(ApplicationDescriptor.class);
        when(mockDescriptor.getApplicationId()).thenReturn("json-app");
        when(mockJsonParser.parseFile(descriptorFile)).thenReturn(mockDescriptor);

        handler.onDescriptorDetected(descriptorFile);

        verify(mockJsonParser).parseFile(descriptorFile);
        verify(mockApplicationManager).deploy(mockDescriptor);
        verify(mockApplicationManager).start("json-app");

        assertEquals("json-app", handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorDetectedParseException() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        ParseException parseException = new ParseException("Invalid YAML");
        when(mockYamlParser.parseFile(descriptorFile)).thenThrow(parseException);

        handler.onDescriptorDetected(descriptorFile);

        verify(mockYamlParser).parseFile(descriptorFile);
        verify(mockApplicationManager, never()).deploy(any());
        verify(mockApplicationManager, never()).start(anyString());

        assertNull(handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorDetectedDeployException() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        ApplicationDescriptor mockDescriptor = mock(ApplicationDescriptor.class);
        when(mockDescriptor.getApplicationId()).thenReturn("test-app");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(mockDescriptor);
        doThrow(new RuntimeException("Deploy failed")).when(mockApplicationManager).deploy(mockDescriptor);

        handler.onDescriptorDetected(descriptorFile);

        verify(mockYamlParser).parseFile(descriptorFile);
        verify(mockApplicationManager).deploy(mockDescriptor);
        verify(mockApplicationManager, never()).start(anyString());

        assertNull(handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorDetectedNoParserForExtension() throws Exception {
        Path descriptorFile = tempDir.resolve("app.xml");
        Files.createFile(descriptorFile);

        handler.onDescriptorDetected(descriptorFile);

        verify(mockYamlParser, never()).parseFile(any());
        verify(mockJsonParser, never()).parseFile(any());
        verify(mockApplicationManager, never()).deploy(any());
        verify(mockApplicationManager, never()).start(anyString());
    }

    @Test
    void testOnDescriptorDetectedNoExtension() throws Exception {
        Path descriptorFile = tempDir.resolve("app");
        Files.createFile(descriptorFile);

        handler.onDescriptorDetected(descriptorFile);

        verify(mockYamlParser, never()).parseFile(any());
        verify(mockJsonParser, never()).parseFile(any());
        verify(mockApplicationManager, never()).deploy(any());
        verify(mockApplicationManager, never()).start(anyString());
    }

    @Test
    void testOnDescriptorModifiedWithExistingApp() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        // Pre-register the app
        handler.getRegistry().put(descriptorFile, "old-app");

        ApplicationDescriptor mockDescriptor = mock(ApplicationDescriptor.class);
        when(mockDescriptor.getApplicationId()).thenReturn("new-app");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(mockDescriptor);

        handler.onDescriptorModified(descriptorFile);

        verify(mockApplicationManager).stop("old-app");
        verify(mockApplicationManager).undeploy("old-app");
        verify(mockYamlParser).parseFile(descriptorFile);
        verify(mockApplicationManager).deploy(mockDescriptor);
        verify(mockApplicationManager).start("new-app");

        assertEquals("new-app", handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorModifiedWithoutExistingApp() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        ApplicationDescriptor mockDescriptor = mock(ApplicationDescriptor.class);
        when(mockDescriptor.getApplicationId()).thenReturn("new-app");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(mockDescriptor);

        handler.onDescriptorModified(descriptorFile);

        verify(mockApplicationManager, never()).stop(anyString());
        verify(mockApplicationManager, never()).undeploy(anyString());
        verify(mockYamlParser).parseFile(descriptorFile);
        verify(mockApplicationManager).deploy(mockDescriptor);
        verify(mockApplicationManager).start("new-app");

        assertEquals("new-app", handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorModifiedStopFailure() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        handler.getRegistry().put(descriptorFile, "old-app");

        ApplicationDescriptor mockDescriptor = mock(ApplicationDescriptor.class);
        when(mockDescriptor.getApplicationId()).thenReturn("new-app");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(mockDescriptor);
        doThrow(new RuntimeException("Stop failed")).when(mockApplicationManager).stop("old-app");

        handler.onDescriptorModified(descriptorFile);

        verify(mockApplicationManager).stop("old-app");
        verify(mockApplicationManager).undeploy("old-app");
        verify(mockApplicationManager).deploy(mockDescriptor);
        verify(mockApplicationManager).start("new-app");

        assertEquals("new-app", handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorModifiedUndeployFailure() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        handler.getRegistry().put(descriptorFile, "old-app");

        ApplicationDescriptor mockDescriptor = mock(ApplicationDescriptor.class);
        when(mockDescriptor.getApplicationId()).thenReturn("new-app");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(mockDescriptor);
        doThrow(new RuntimeException("Undeploy failed")).when(mockApplicationManager).undeploy("old-app");

        handler.onDescriptorModified(descriptorFile);

        verify(mockApplicationManager).stop("old-app");
        verify(mockApplicationManager).undeploy("old-app");
        verify(mockApplicationManager).deploy(mockDescriptor);
        verify(mockApplicationManager).start("new-app");

        assertEquals("new-app", handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorModifiedParseException() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        handler.getRegistry().put(descriptorFile, "old-app");

        ParseException parseException = new ParseException("Invalid YAML");
        when(mockYamlParser.parseFile(descriptorFile)).thenThrow(parseException);

        handler.onDescriptorModified(descriptorFile);

        verify(mockYamlParser).parseFile(descriptorFile);
        verify(mockApplicationManager, never()).stop(anyString());
        verify(mockApplicationManager, never()).undeploy(anyString());
        verify(mockApplicationManager, never()).deploy(any());

        // Registry should still have the old app
        assertEquals("old-app", handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorModifiedWithAutoDeployDisabled() throws Exception {
        config = WatcherConfig.builder()
                .watchDirectory(tempDir)
                .autoDeploy(false)
                .autoStart(true)
                .build();
        handler = new AutoDeploymentHandler(mockApplicationManager, parsers, config);

        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        handler.getRegistry().put(descriptorFile, "old-app");

        ApplicationDescriptor mockDescriptor = mock(ApplicationDescriptor.class);
        when(mockDescriptor.getApplicationId()).thenReturn("new-app");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(mockDescriptor);

        handler.onDescriptorModified(descriptorFile);

        // When auto-deploy is disabled, no operations should be performed
        verify(mockApplicationManager, never()).stop(anyString());
        verify(mockApplicationManager, never()).undeploy(anyString());
        verify(mockApplicationManager, never()).deploy(any());
        verify(mockApplicationManager, never()).start(anyString());

        // Registry should remain unchanged
        assertEquals("old-app", handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorRemoved() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        handler.getRegistry().put(descriptorFile, "test-app");

        handler.onDescriptorRemoved(descriptorFile);

        verify(mockApplicationManager).stop("test-app");
        verify(mockApplicationManager).undeploy("test-app");

        assertNull(handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorRemovedNotRegistered() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        handler.onDescriptorRemoved(descriptorFile);

        verify(mockApplicationManager, never()).stop(anyString());
        verify(mockApplicationManager, never()).undeploy(anyString());
    }

    @Test
    void testOnDescriptorRemovedStopFailure() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        handler.getRegistry().put(descriptorFile, "test-app");
        doThrow(new RuntimeException("Stop failed")).when(mockApplicationManager).stop("test-app");

        handler.onDescriptorRemoved(descriptorFile);

        verify(mockApplicationManager).stop("test-app");
        verify(mockApplicationManager).undeploy("test-app");

        assertNull(handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnDescriptorRemovedUndeployFailure() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        handler.getRegistry().put(descriptorFile, "test-app");
        doThrow(new RuntimeException("Undeploy failed")).when(mockApplicationManager).undeploy("test-app");

        handler.onDescriptorRemoved(descriptorFile);

        verify(mockApplicationManager).stop("test-app");
        verify(mockApplicationManager).undeploy("test-app");

        assertNull(handler.getRegistry().get(descriptorFile));
    }

    @Test
    void testOnError() {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Exception error = new RuntimeException("Test error");

        // Should not throw
        assertDoesNotThrow(() -> {
            handler.onError(descriptorFile, error);
        });
    }

    @Test
    void testMultipleDescriptorFiles() throws Exception {
        Path yamlFile = tempDir.resolve("app1.yaml");
        Path jsonFile = tempDir.resolve("app2.json");
        Files.createFile(yamlFile);
        Files.createFile(jsonFile);

        ApplicationDescriptor yamlDescriptor = mock(ApplicationDescriptor.class);
        when(yamlDescriptor.getApplicationId()).thenReturn("yaml-app");
        when(mockYamlParser.parseFile(yamlFile)).thenReturn(yamlDescriptor);

        ApplicationDescriptor jsonDescriptor = mock(ApplicationDescriptor.class);
        when(jsonDescriptor.getApplicationId()).thenReturn("json-app");
        when(mockJsonParser.parseFile(jsonFile)).thenReturn(jsonDescriptor);

        handler.onDescriptorDetected(yamlFile);
        handler.onDescriptorDetected(jsonFile);

        verify(mockYamlParser).parseFile(yamlFile);
        verify(mockJsonParser).parseFile(jsonFile);
        verify(mockApplicationManager).deploy(yamlDescriptor);
        verify(mockApplicationManager).deploy(jsonDescriptor);
        verify(mockApplicationManager).start("yaml-app");
        verify(mockApplicationManager).start("json-app");

        assertEquals(2, handler.getRegistry().size());
        assertEquals("yaml-app", handler.getRegistry().get(yamlFile));
        assertEquals("json-app", handler.getRegistry().get(jsonFile));
    }

    @Test
    void testCaseInsensitiveExtension() throws Exception {
        Path descriptorFile = tempDir.resolve("app.YAML");
        Files.createFile(descriptorFile);

        ApplicationDescriptor mockDescriptor = mock(ApplicationDescriptor.class);
        when(mockDescriptor.getApplicationId()).thenReturn("test-app");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(mockDescriptor);

        handler.onDescriptorDetected(descriptorFile);

        verify(mockYamlParser).parseFile(descriptorFile);
        verify(mockApplicationManager).deploy(mockDescriptor);
        verify(mockApplicationManager).start("test-app");
    }

    @Test
    void testFullLifecycle() throws Exception {
        Path descriptorFile = tempDir.resolve("app.yaml");
        Files.createFile(descriptorFile);

        ApplicationDescriptor descriptor1 = mock(ApplicationDescriptor.class);
        when(descriptor1.getApplicationId()).thenReturn("app-v1");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(descriptor1);

        // Detected
        handler.onDescriptorDetected(descriptorFile);
        verify(mockApplicationManager).deploy(descriptor1);
        verify(mockApplicationManager).start("app-v1");
        assertEquals("app-v1", handler.getRegistry().get(descriptorFile));

        // Modified
        ApplicationDescriptor descriptor2 = mock(ApplicationDescriptor.class);
        when(descriptor2.getApplicationId()).thenReturn("app-v2");
        when(mockYamlParser.parseFile(descriptorFile)).thenReturn(descriptor2);

        handler.onDescriptorModified(descriptorFile);
        verify(mockApplicationManager).stop("app-v1");
        verify(mockApplicationManager).undeploy("app-v1");
        verify(mockApplicationManager).deploy(descriptor2);
        verify(mockApplicationManager).start("app-v2");
        assertEquals("app-v2", handler.getRegistry().get(descriptorFile));

        // Removed
        handler.onDescriptorRemoved(descriptorFile);
        verify(mockApplicationManager).stop("app-v2");
        verify(mockApplicationManager).undeploy("app-v2");
        assertNull(handler.getRegistry().get(descriptorFile));
    }
}
