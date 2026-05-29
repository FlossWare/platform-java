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

package org.flossware.jplatform.classloader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for PlatformClassLoadListener.
 * Tests lifecycle event handling and logging integration.
 * Note: This listener primarily logs events, so tests verify it doesn't throw exceptions.
 */
class PlatformClassLoadListenerTest {

    private static final String TEST_APP_ID = "test-app";
    private PlatformClassLoadListener listener;

    @BeforeEach
    void setUp() {
        listener = new PlatformClassLoadListener(TEST_APP_ID);
    }

    @Test
    void testConstruction() {
        assertNotNull(listener);
    }

    @Test
    void testConstructionWithNullApplicationId() {
        // Construction with null is allowed - will just log with null appId
        assertDoesNotThrow(() -> new PlatformClassLoadListener(null));
    }

    @Test
    void testOnClassLoadFailed() {
        String className = "com.example.FailedClass";
        Throwable error = new ClassNotFoundException("Class not found");

        // Should not throw
        assertDoesNotThrow(() -> listener.onClassLoadFailed(className, error));
    }

    @Test
    void testOnClassLoadFailedWithNullClassName() {
        Throwable error = new ClassNotFoundException("Class not found");

        // Should not throw - just logs
        assertDoesNotThrow(() -> listener.onClassLoadFailed(null, error));
    }

    @Test
    void testOnClassLoadFailedWithNullError() {
        String className = "com.example.FailedClass";

        // Should throw NullPointerException when accessing error.getMessage()
        assertThrows(NullPointerException.class, () ->
            listener.onClassLoadFailed(className, null)
        );
    }

    @Test
    void testOnClassCacheHit() {
        String className = "com.example.CachedClass";

        // Should not throw
        assertDoesNotThrow(() -> listener.onClassCacheHit(className));
    }

    @Test
    void testOnClassCacheHitWithNullClassName() {
        // Should not throw - just logs
        assertDoesNotThrow(() -> listener.onClassCacheHit(null));
    }

    @Test
    void testMultipleClassLoadFailures() {
        assertDoesNotThrow(() -> {
            listener.onClassLoadFailed("com.example.Class1", new ClassNotFoundException("Not found"));
            listener.onClassLoadFailed("com.example.Class2", new NoClassDefFoundError("Missing"));
            listener.onClassLoadFailed("com.example.Class3", new Exception("Generic error"));
        });
    }

    @Test
    void testMultipleCacheHits() {
        assertDoesNotThrow(() -> {
            listener.onClassCacheHit("com.example.Class1");
            listener.onClassCacheHit("com.example.Class2");
            listener.onClassCacheHit("com.example.Class3");
        });
    }

    @Test
    void testMixedEvents() {
        assertDoesNotThrow(() -> {
            listener.onClassCacheHit("com.example.CachedClass");
            listener.onClassLoadFailed("com.example.FailedClass", new Exception("Test"));
            listener.onClassCacheHit("com.example.AnotherCached");
        });
    }

    @Test
    void testDifferentApplicationIds() {
        PlatformClassLoadListener listener1 = new PlatformClassLoadListener("app1");
        PlatformClassLoadListener listener2 = new PlatformClassLoadListener("app2");

        // Both listeners should handle events independently
        assertDoesNotThrow(() -> {
            listener1.onClassLoadFailed("com.example.Class", new Exception("App1 error"));
            listener2.onClassLoadFailed("com.example.Class", new Exception("App2 error"));
            listener1.onClassCacheHit("com.example.Cached");
            listener2.onClassCacheHit("com.example.Cached");
        });
    }
}
