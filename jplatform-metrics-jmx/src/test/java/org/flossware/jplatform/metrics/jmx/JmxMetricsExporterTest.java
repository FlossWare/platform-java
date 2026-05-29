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

package org.flossware.jplatform.metrics.jmx;

import org.flossware.jplatform.api.ApplicationContext;
import org.flossware.jplatform.api.ApplicationState;
import org.flossware.jplatform.api.JmxExporterConfig;
import org.flossware.jplatform.api.ResourceMonitor;
import org.flossware.jplatform.api.ResourceSnapshot;
import org.flossware.jplatform.api.ThreadPoolExecutor;
import org.flossware.jplatform.api.ThreadPoolStats;
import org.flossware.jplatform.core.ApplicationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JmxMetricsExporter.
 */
class JmxMetricsExporterTest {

    private JmxExporterConfig config;
    private ApplicationManager manager;
    private ApplicationContext context;
    private JmxMetricsExporter exporter;
    private MBeanServer mBeanServer;

    private ApplicationContext createMockedContext(ApplicationState state) {
        ApplicationContext ctx = mock(ApplicationContext.class);

        ResourceMonitor resourceMonitor = mock(ResourceMonitor.class);
        ResourceSnapshot snapshot = mock(ResourceSnapshot.class);
        ThreadPoolExecutor threadPool = mock(ThreadPoolExecutor.class);
        ThreadPoolStats stats = mock(ThreadPoolStats.class);

        when(ctx.getState()).thenReturn(state);
        when(ctx.getResourceMonitor()).thenReturn(resourceMonitor);
        when(ctx.getThreadPool()).thenReturn(threadPool);
        when(resourceMonitor.getCurrentSnapshot()).thenReturn(snapshot);
        when(threadPool.getStats()).thenReturn(stats);

        when(snapshot.getCpuTimeNanos()).thenReturn(1000000L);
        when(snapshot.getHeapUsedBytes()).thenReturn(100000000L);
        when(snapshot.getThreadCount()).thenReturn(10);

        when(stats.getActiveThreads()).thenReturn(5);
        when(stats.getQueuedTasks()).thenReturn(0);
        when(stats.getCompletedTasks()).thenReturn(100L);

        return ctx;
    }

    @BeforeEach
    void setUp() {
        config = JmxExporterConfig.builder()
                .enabled(true)
                .port(0) // Don't create RMI registry in tests
                .domain("org.flossware.jplatform.test")
                .build();

        manager = mock(ApplicationManager.class);
        context = mock(ApplicationContext.class);

        // Set up all required mocks for ApplicationContext
        ResourceMonitor resourceMonitor = mock(ResourceMonitor.class);
        ResourceSnapshot snapshot = mock(ResourceSnapshot.class);
        ThreadPoolExecutor threadPool = mock(ThreadPoolExecutor.class);
        ThreadPoolStats stats = mock(ThreadPoolStats.class);

        when(context.getState()).thenReturn(ApplicationState.RUNNING);
        when(context.getResourceMonitor()).thenReturn(resourceMonitor);
        when(context.getThreadPool()).thenReturn(threadPool);
        when(resourceMonitor.getCurrentSnapshot()).thenReturn(snapshot);
        when(threadPool.getStats()).thenReturn(stats);

        // Set up snapshot defaults
        when(snapshot.getCpuTimeNanos()).thenReturn(1000000L);
        when(snapshot.getHeapUsedBytes()).thenReturn(100000000L);
        when(snapshot.getThreadCount()).thenReturn(10);

        // Set up thread pool stats defaults
        when(stats.getActiveThreads()).thenReturn(5);
        when(stats.getQueuedTasks()).thenReturn(0);
        when(stats.getCompletedTasks()).thenReturn(100L);

        mBeanServer = ManagementFactory.getPlatformMBeanServer();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (exporter != null && exporter.isRunning()) {
            exporter.stop();
        }

        // Clean up any registered MBeans
        try {
            ObjectName pattern = new ObjectName("org.flossware.jplatform.test:type=Application,*");
            for (ObjectName name : mBeanServer.queryNames(pattern, null)) {
                try {
                    mBeanServer.unregisterMBean(name);
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    void testConstructorNullConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> new JmxMetricsExporter(null, manager));
    }

    @Test
    void testConstructorNullManager() {
        assertThrows(IllegalArgumentException.class,
                () -> new JmxMetricsExporter(config, null));
    }

    @Test
    void testStartSuccess() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);

        assertFalse(exporter.isRunning());

        exporter.start();

        assertTrue(exporter.isRunning());
        assertEquals("org.flossware.jplatform.test", exporter.getDomain());
        assertEquals(0, exporter.getPort());
    }

    @Test
    void testStartAlreadyRunning() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);

        exporter.start();
        assertTrue(exporter.isRunning());

        // Starting again should just log warning
        exporter.start();
        assertTrue(exporter.isRunning());
    }

    @Test
    void testStopSuccess() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);

        exporter.start();
        assertTrue(exporter.isRunning());

        exporter.stop();
        assertFalse(exporter.isRunning());
    }

    @Test
    void testStopNotRunning() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);

        assertFalse(exporter.isRunning());

        // Stopping when not running should just log warning
        exporter.stop();
        assertFalse(exporter.isRunning());
    }

    @Test
    void testRegisterApplication() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        exporter.registerApplication("test-app", context);

        assertEquals(1, exporter.getRegisteredApplicationCount());

        // Verify MBean is registered (applicationId is quoted to escape JMX special characters)
        String quotedAppId = ObjectName.quote("test-app");
        ObjectName objectName = new ObjectName("org.flossware.jplatform.test:type=Application,id=" + quotedAppId);
        assertTrue(mBeanServer.isRegistered(objectName));
    }

    @Test
    void testRegisterApplicationNotRunning() {
        exporter = new JmxMetricsExporter(config, manager);

        // Should not throw but should log warning
        exporter.registerApplication("test-app", context);

        assertEquals(0, exporter.getRegisteredApplicationCount());
    }

    @Test
    void testRegisterApplicationNullId() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        assertThrows(IllegalArgumentException.class,
                () -> exporter.registerApplication(null, context));
    }

    @Test
    void testRegisterApplicationEmptyId() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        assertThrows(IllegalArgumentException.class,
                () -> exporter.registerApplication("", context));
    }

    @Test
    void testRegisterApplicationNullContext() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        assertThrows(IllegalArgumentException.class,
                () -> exporter.registerApplication("test-app", null));
    }

    @Test
    void testRegisterApplicationTwice() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        exporter.registerApplication("test-app", context);
        assertEquals(1, exporter.getRegisteredApplicationCount());

        // Registering again should unregister first, then register
        exporter.registerApplication("test-app", context);
        assertEquals(1, exporter.getRegisteredApplicationCount());

        // MBean should still be registered (applicationId is quoted to escape JMX special characters)
        String quotedAppId = ObjectName.quote("test-app");
        ObjectName objectName = new ObjectName("org.flossware.jplatform.test:type=Application,id=" + quotedAppId);
        assertTrue(mBeanServer.isRegistered(objectName));
    }

    @Test
    void testUnregisterApplication() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        exporter.registerApplication("test-app", context);
        assertEquals(1, exporter.getRegisteredApplicationCount());

        exporter.unregisterApplication("test-app");
        assertEquals(0, exporter.getRegisteredApplicationCount());

        // Verify MBean is unregistered
        ObjectName objectName = new ObjectName("org.flossware.jplatform.test:type=Application,id=test-app");
        assertFalse(mBeanServer.isRegistered(objectName));
    }

    @Test
    void testUnregisterApplicationNotRegistered() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        // Should not throw, just log warning
        exporter.unregisterApplication("non-existent");
        assertEquals(0, exporter.getRegisteredApplicationCount());
    }

    @Test
    void testUnregisterApplicationNullId() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        assertThrows(IllegalArgumentException.class,
                () -> exporter.unregisterApplication(null));
    }

    @Test
    void testUnregisterApplicationEmptyId() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        assertThrows(IllegalArgumentException.class,
                () -> exporter.unregisterApplication(""));
    }

    @Test
    void testStopUnregistersAllApplications() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        exporter.registerApplication("app1", context);
        exporter.registerApplication("app2", context);
        exporter.registerApplication("app3", context);

        assertEquals(3, exporter.getRegisteredApplicationCount());

        exporter.stop();

        assertEquals(0, exporter.getRegisteredApplicationCount());

        // Verify all MBeans are unregistered
        ObjectName objectName1 = new ObjectName("org.flossware.jplatform.test:type=Application,id=app1");
        ObjectName objectName2 = new ObjectName("org.flossware.jplatform.test:type=Application,id=app2");
        ObjectName objectName3 = new ObjectName("org.flossware.jplatform.test:type=Application,id=app3");

        assertFalse(mBeanServer.isRegistered(objectName1));
        assertFalse(mBeanServer.isRegistered(objectName2));
        assertFalse(mBeanServer.isRegistered(objectName3));
    }

    @Test
    void testClose() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        assertTrue(exporter.isRunning());

        exporter.close();

        assertFalse(exporter.isRunning());
    }

    @Test
    void testCloseNotRunning() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);

        assertFalse(exporter.isRunning());

        // Closing when not running should not throw
        exporter.close();

        assertFalse(exporter.isRunning());
    }

    @Test
    void testMultipleApplications() throws Exception {
        exporter = new JmxMetricsExporter(config, manager);
        exporter.start();

        ApplicationContext context1 = createMockedContext(ApplicationState.RUNNING);
        ApplicationContext context2 = createMockedContext(ApplicationState.STOPPED);
        ApplicationContext context3 = createMockedContext(ApplicationState.DEPLOYED);

        exporter.registerApplication("app1", context1);
        exporter.registerApplication("app2", context2);
        exporter.registerApplication("app3", context3);

        assertEquals(3, exporter.getRegisteredApplicationCount());

        exporter.unregisterApplication("app2");

        assertEquals(2, exporter.getRegisteredApplicationCount());

        // Verify correct MBeans remain (applicationId is quoted to escape JMX special characters)
        String quotedAppId1 = ObjectName.quote("app1");
        String quotedAppId2 = ObjectName.quote("app2");
        String quotedAppId3 = ObjectName.quote("app3");
        ObjectName objectName1 = new ObjectName("org.flossware.jplatform.test:type=Application,id=" + quotedAppId1);
        ObjectName objectName2 = new ObjectName("org.flossware.jplatform.test:type=Application,id=" + quotedAppId2);
        ObjectName objectName3 = new ObjectName("org.flossware.jplatform.test:type=Application,id=" + quotedAppId3);

        assertTrue(mBeanServer.isRegistered(objectName1));
        assertFalse(mBeanServer.isRegistered(objectName2));
        assertTrue(mBeanServer.isRegistered(objectName3));
    }

    @Test
    void testGetDomain() {
        exporter = new JmxMetricsExporter(config, manager);
        assertEquals("org.flossware.jplatform.test", exporter.getDomain());
    }

    @Test
    void testGetPort() {
        exporter = new JmxMetricsExporter(config, manager);
        assertEquals(0, exporter.getPort());

        JmxExporterConfig configWithPort = JmxExporterConfig.builder()
                .enabled(true)
                .port(9999)
                .domain("test")
                .build();

        JmxMetricsExporter exporterWithPort = new JmxMetricsExporter(configWithPort, manager);
        assertEquals(9999, exporterWithPort.getPort());
    }
}
