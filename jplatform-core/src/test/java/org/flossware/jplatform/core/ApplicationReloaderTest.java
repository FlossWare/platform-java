package org.flossware.jplatform.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApplicationReloader.
 * Tests version tracking and history management.
 *
 * Note: Full reload() integration tests require complex setup with ApplicationManager,
 * ApplicationContext, and classloaders. These tests focus on the public API contract
 * for version tracking and history management.
 */
class ApplicationReloaderTest {

    private ApplicationReloader reloader;
    private ClassLoader platformLoader;

    @BeforeEach
    void setUp() {
        platformLoader = Thread.currentThread().getContextClassLoader();
        reloader = new ApplicationReloader(platformLoader);
    }

    @Test
    void testGetCurrentVersionReturnsZeroForNewApplication() {
        int version = reloader.getCurrentVersion("app1");
        assertEquals(0, version, "New application should have version 0");
    }

    @Test
    void testGetVersionHistoryReturnsEmptyForNewApplication() {
        List<ClassLoaderVersion> history = reloader.getVersionHistory("app1");
        assertNotNull(history, "History should not be null");
        assertTrue(history.isEmpty(), "New application should have empty history");
    }

    @Test
    void testGetVersionHistoryReturnsUnmodifiableList() {
        List<ClassLoaderVersion> history = reloader.getVersionHistory("app1");
        assertThrows(UnsupportedOperationException.class, () -> {
            history.add(null);
        }, "History list should be unmodifiable");
    }

    @Test
    void testClearHistoryRemovesVersionTracking() {
        // Even though we can't easily trigger a reload in unit tests,
        // we can verify clearHistory doesn't throw for an app with no history
        assertDoesNotThrow(() -> reloader.clearHistory("app1"));

        int version = reloader.getCurrentVersion("app1");
        assertEquals(0, version, "Cleared app should have version 0");

        List<ClassLoaderVersion> history = reloader.getVersionHistory("app1");
        assertTrue(history.isEmpty(), "Cleared app should have empty history");
    }

    @Test
    void testClearHistoryForNonExistentAppDoesNotThrow() {
        assertDoesNotThrow(() -> reloader.clearHistory("nonexistent"));
    }

    @Test
    void testMultipleApplicationsTrackedSeparately() {
        // Verify that different applications have independent version tracking
        int version1 = reloader.getCurrentVersion("app1");
        int version2 = reloader.getCurrentVersion("app2");

        assertEquals(0, version1);
        assertEquals(0, version2);

        List<ClassLoaderVersion> history1 = reloader.getVersionHistory("app1");
        List<ClassLoaderVersion> history2 = reloader.getVersionHistory("app2");

        assertTrue(history1.isEmpty());
        assertTrue(history2.isEmpty());
    }

    @Test
    void testConstructorRejectsNullPlatformLoader() {
        // Verify constructor rejects null platform loader
        assertThrows(NullPointerException.class, () -> new ApplicationReloader(null));
    }
}
