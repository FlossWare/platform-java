package org.flossware.jplatform.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.flossware.jplatform.api.*;
import org.flossware.jplatform.core.ApplicationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ApplicationApiHandler.
 * Tests all application management endpoints, JSON serialization, and error handling.
 */
class ApplicationApiHandlerTest {

    private ApplicationManager mockManager;
    private ApplicationApiHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockManager = mock(ApplicationManager.class);
        handler = new ApplicationApiHandler(mockManager);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testConstructorNullManager() {
        assertThrows(NullPointerException.class, () -> new ApplicationApiHandler(null));
    }

    @Test
    void testHandleListApplicationsEmpty() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications");

        when(mockManager.listApplications()).thenReturn(Collections.emptyMap());

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        assertEquals(0, responseMap.get("count"));
        assertTrue(((List<?>) responseMap.get("applications")).isEmpty());
    }

    @Test
    void testHandleListApplicationsWithApps() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications");

        Map<String, ApplicationState> apps = new HashMap<>();
        apps.put("app1", ApplicationState.RUNNING);
        apps.put("app2", ApplicationState.STOPPED);
        apps.put("app3", ApplicationState.DEPLOYED);

        when(mockManager.listApplications()).thenReturn(apps);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        assertEquals(3, responseMap.get("count"));
        List<Map<String, String>> appList = (List<Map<String, String>>) responseMap.get("applications");
        assertEquals(3, appList.size());
    }

    @Test
    void testHandleDeployApplication() throws Exception {
        String requestBody = "{"
                + "\"applicationId\":\"test-app\","
                + "\"name\":\"Test App\","
                + "\"mainClass\":\"com.example.Main\","
                + "\"classpathEntries\":[\"file:///test.jar\"],"
                + "\"enableMessaging\":false"
                + "}";

        HttpExchange exchange = createMockExchange("POST", "/api/applications", requestBody);

        ApplicationContext mockContext = createMockApplicationContext("test-app", ApplicationState.DEPLOYED);
        when(mockManager.getApplicationContext("test-app")).thenReturn(mockContext);

        handler.handle(exchange);

        ArgumentCaptor<ApplicationDescriptor> descriptorCaptor = ArgumentCaptor.forClass(ApplicationDescriptor.class);
        verify(mockManager).deploy(descriptorCaptor.capture());

        ApplicationDescriptor descriptor = descriptorCaptor.getValue();
        assertEquals("test-app", descriptor.getApplicationId());
        assertEquals("com.example.Main", descriptor.getMainClass());

        verify(exchange).sendResponseHeaders(eq(201), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        assertEquals("test-app", responseMap.get("applicationId"));
    }

    @Test
    void testHandleDeployApplicationInvalidJson() throws IOException {
        String invalidJson = "{invalid json}";

        HttpExchange exchange = createMockExchange("POST", "/api/applications", invalidJson);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());

        String response = getResponseBody(exchange);
        assertTrue(response.contains("error"));
    }

    @Test
    void testHandleGetApplication() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications/test-app");

        ApplicationContext mockContext = createMockApplicationContext("test-app", ApplicationState.RUNNING);
        when(mockManager.getApplicationContext("test-app")).thenReturn(mockContext);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        assertEquals("test-app", responseMap.get("applicationId"));
    }

    @Test
    void testHandleGetApplicationNotFound() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications/nonexistent");

        when(mockManager.getApplicationContext("nonexistent")).thenReturn(null);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());

        String response = getResponseBody(exchange);
        assertTrue(response.contains("NotFound"));
    }

    @Test
    void testHandleGetStatus() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications/test-app/status");

        ApplicationContext mockContext = createMockApplicationContext("test-app", ApplicationState.RUNNING);
        when(mockManager.getApplicationContext("test-app")).thenReturn(mockContext);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        assertEquals("test-app", responseMap.get("applicationId"));
        assertEquals("RUNNING", responseMap.get("state"));
        assertNotNull(responseMap.get("threadPool"));
        assertNotNull(responseMap.get("resources"));
    }

    @Test
    void testHandleGetStatusNotRunning() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications/test-app/status");

        ApplicationContext mockContext = createMockApplicationContext("test-app", ApplicationState.STOPPED);
        when(mockManager.getApplicationContext("test-app")).thenReturn(mockContext);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        assertEquals("STOPPED", responseMap.get("state"));
        assertNull(responseMap.get("threadPool"));
        assertNull(responseMap.get("resources"));
    }

    @Test
    void testHandleStartApplication() throws Exception {
        HttpExchange exchange = createMockExchange("POST", "/api/applications/test-app/start");

        ApplicationContext mockContext = createMockApplicationContext("test-app", ApplicationState.RUNNING);
        when(mockManager.getApplicationContext("test-app")).thenReturn(mockContext);

        handler.handle(exchange);

        verify(mockManager).start("test-app");
        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        assertEquals("test-app", responseMap.get("applicationId"));
    }

    @Test
    void testHandleStartApplicationInvalidState() throws Exception {
        HttpExchange exchange = createMockExchange("POST", "/api/applications/test-app/start");

        doThrow(new IllegalStateException("Cannot start in current state"))
                .when(mockManager).start("test-app");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());

        String response = getResponseBody(exchange);
        assertTrue(response.contains("InvalidState"));
    }

    @Test
    void testHandleStopApplication() throws Exception {
        HttpExchange exchange = createMockExchange("POST", "/api/applications/test-app/stop");

        ApplicationContext mockContext = createMockApplicationContext("test-app", ApplicationState.STOPPED);
        when(mockManager.getApplicationContext("test-app")).thenReturn(mockContext);

        handler.handle(exchange);

        verify(mockManager).stop("test-app");
        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        assertEquals("test-app", responseMap.get("applicationId"));
    }

    @Test
    void testHandleStopApplicationException() throws Exception {
        HttpExchange exchange = createMockExchange("POST", "/api/applications/test-app/stop");

        doThrow(new RuntimeException("Stop failed"))
                .when(mockManager).stop("test-app");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(500), anyLong());

        String response = getResponseBody(exchange);
        assertTrue(response.contains("StopFailed"));
    }

    @Test
    void testHandleUndeployApplication() throws Exception {
        HttpExchange exchange = createMockExchange("DELETE", "/api/applications/test-app");

        handler.handle(exchange);

        verify(mockManager).undeploy("test-app");
        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        assertEquals("test-app", responseMap.get("applicationId"));
        assertEquals("undeployed", responseMap.get("status"));
    }

    @Test
    void testHandleUndeployApplicationException() throws Exception {
        HttpExchange exchange = createMockExchange("DELETE", "/api/applications/test-app");

        doThrow(new RuntimeException("Undeploy failed"))
                .when(mockManager).undeploy("test-app");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(500), anyLong());

        String response = getResponseBody(exchange);
        assertTrue(response.contains("UndeployFailed"));
    }

    @Test
    void testHandleGetMetrics() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications/test-app/metrics");

        ApplicationContext mockContext = createMockApplicationContext("test-app", ApplicationState.RUNNING);
        ResourceMonitor mockMonitor = mock(ResourceMonitor.class);
        when(mockContext.getResourceMonitor()).thenReturn(mockMonitor);

        List<ResourceSnapshot> snapshots = createMockSnapshots(3);
        ResourceUsageHistory mockHistory = mock(ResourceUsageHistory.class);
        when(mockHistory.getSnapshots()).thenReturn(snapshots);
        when(mockMonitor.getHistory(any(Duration.class))).thenReturn(mockHistory);

        when(mockManager.getApplicationContext("test-app")).thenReturn(mockContext);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());

        String response = getResponseBody(exchange);
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        assertEquals("test-app", responseMap.get("applicationId"));
        assertEquals(3, responseMap.get("count"));
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) responseMap.get("metrics");
        assertEquals(3, metrics.size());
    }

    @Test
    void testHandleGetMetricsNotAvailable() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications/test-app/metrics");

        ApplicationContext mockContext = createMockApplicationContext("test-app", ApplicationState.DEPLOYED);
        ResourceMonitor mockMonitor = mock(ResourceMonitor.class);
        when(mockContext.getResourceMonitor()).thenReturn(mockMonitor);
        when(mockMonitor.getHistory(any(Duration.class)))
                .thenThrow(new RuntimeException("History not available"));

        when(mockManager.getApplicationContext("test-app")).thenReturn(mockContext);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());

        String response = getResponseBody(exchange);
        assertTrue(response.contains("NotAvailable"));
    }

    @Test
    void testHandleEndpointNotFound() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications/unknown/endpoint");

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());

        String response = getResponseBody(exchange);
        assertTrue(response.contains("NotFound"));
    }

    @Test
    void testHandleInternalError() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications");

        when(mockManager.listApplications()).thenThrow(new RuntimeException("Internal error"));

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(500), anyLong());

        String response = getResponseBody(exchange);
        assertTrue(response.contains("InternalError"));
    }

    @Test
    void testApplicationDescriptorDtoConversion() {
        ApplicationApiHandler.ApplicationDescriptorDTO dto = new ApplicationApiHandler.ApplicationDescriptorDTO();
        dto.applicationId = "test-app";
        dto.name = "Test Application";
        dto.version = "1.0";
        dto.mainClass = "com.example.Main";
        dto.classpathEntries = Arrays.asList("file:///test.jar");
        dto.properties = new HashMap<>();
        dto.properties.put("key1", "value1");
        dto.enableMessaging = true;

        ApplicationDescriptor descriptor = dto.toApplicationDescriptor();

        assertEquals("test-app", descriptor.getApplicationId());
        assertEquals("com.example.Main", descriptor.getMainClass());
        assertTrue(descriptor.isEnableMessaging());
    }

    @Test
    void testApplicationDescriptorDtoInvalidClasspath() {
        ApplicationApiHandler.ApplicationDescriptorDTO dto = new ApplicationApiHandler.ApplicationDescriptorDTO();
        dto.applicationId = "test-app";
        dto.mainClass = "com.example.Main";
        dto.classpathEntries = Arrays.asList("not a valid uri at all");

        assertThrows(Exception.class, () -> dto.toApplicationDescriptor());
    }

    @Test
    void testJsonContentTypeHeader() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications");

        when(mockManager.listApplications()).thenReturn(Collections.emptyMap());

        handler.handle(exchange);

        Headers headers = exchange.getResponseHeaders();
        verify(headers).set("Content-Type", "application/json; charset=UTF-8");
    }

    @Test
    void testExtractAppIdFromPath() throws IOException {
        HttpExchange exchange = createMockExchange("GET", "/api/applications/my-app-123");

        ApplicationContext mockContext = createMockApplicationContext("my-app-123", ApplicationState.DEPLOYED);
        when(mockManager.getApplicationContext("my-app-123")).thenReturn(mockContext);

        handler.handle(exchange);

        verify(mockManager).getApplicationContext("my-app-123");
    }

    /**
     * Helper method to create a mock HttpExchange.
     */
    private HttpExchange createMockExchange(String method, String path) throws IOException {
        return createMockExchange(method, path, "");
    }

    /**
     * Helper method to create a mock HttpExchange with request body.
     */
    private HttpExchange createMockExchange(String method, String path, String requestBody) throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);

        when(exchange.getRequestMethod()).thenReturn(method);
        when(exchange.getRequestURI()).thenReturn(URI.create(path));

        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestBody.getBytes());
        when(exchange.getRequestBody()).thenReturn(inputStream);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(outputStream);

        Headers responseHeaders = mock(Headers.class);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        return exchange;
    }

    /**
     * Helper method to get response body from mock exchange.
     */
    private String getResponseBody(HttpExchange exchange) {
        ByteArrayOutputStream outputStream = (ByteArrayOutputStream) exchange.getResponseBody();
        return outputStream.toString();
    }

    /**
     * Helper method to create a mock ApplicationContext.
     */
    private ApplicationContext createMockApplicationContext(String appId, ApplicationState state) {
        ApplicationContext context = mock(ApplicationContext.class);

        when(context.getApplicationId()).thenReturn(appId);
        when(context.getState()).thenReturn(state);

        Map<String, String> properties = new HashMap<>();
        properties.put("name", appId + " Application");
        when(context.getProperties()).thenReturn(properties);

        if (state == ApplicationState.RUNNING) {
            ThreadPoolExecutor mockThreadPool = mock(ThreadPoolExecutor.class);
            ThreadPoolStats mockStats = createMockThreadPoolStats();
            when(mockThreadPool.getStats()).thenReturn(mockStats);
            when(context.getThreadPool()).thenReturn(mockThreadPool);

            ResourceMonitor mockMonitor = mock(ResourceMonitor.class);
            ResourceSnapshot mockSnapshot = createMockResourceSnapshot();
            when(mockMonitor.getCurrentSnapshot()).thenReturn(mockSnapshot);
            when(context.getResourceMonitor()).thenReturn(mockMonitor);
        } else {
            when(context.getResourceMonitor()).thenReturn(mock(ResourceMonitor.class));
        }

        return context;
    }

    /**
     * Helper method to create mock ThreadPoolStats.
     */
    private ThreadPoolStats createMockThreadPoolStats() {
        ThreadPoolStats stats = mock(ThreadPoolStats.class);
        when(stats.getActiveThreads()).thenReturn(5);
        when(stats.getCompletedTasks()).thenReturn(1000L);
        when(stats.getQueuedTasks()).thenReturn(10);
        when(stats.getPoolSize()).thenReturn(8);
        when(stats.getCorePoolSize()).thenReturn(4);
        when(stats.getMaximumPoolSize()).thenReturn(16);
        return stats;
    }

    /**
     * Helper method to create mock ResourceSnapshot.
     */
    private ResourceSnapshot createMockResourceSnapshot() {
        ResourceSnapshot snapshot = mock(ResourceSnapshot.class);
        when(snapshot.getCpuTimeNanos()).thenReturn(500000000L);
        when(snapshot.getHeapUsedBytes()).thenReturn(104857600L);
        when(snapshot.getThreadCount()).thenReturn(8);
        when(snapshot.getTimestamp()).thenReturn(System.currentTimeMillis());
        return snapshot;
    }

    /**
     * Helper method to create mock resource snapshots.
     */
    private List<ResourceSnapshot> createMockSnapshots(int count) {
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            snapshots.add(createMockResourceSnapshot());
        }
        return snapshots;
    }
}
