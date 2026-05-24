package org.flossware.jplatform.swing;

import org.flossware.jplatform.api.PlatformManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SwingConsole.
 * Tests API contract and validation.
 *
 * Note: Full UI testing requires a display environment and is not feasible in headless CI/CD.
 * These tests focus on API validation and constructor behavior.
 */
class SwingConsoleTest {

    @Test
    void testConstructorWithNullPlatformManagerThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SwingConsole(null);
        }, "Constructor should throw IllegalArgumentException for null PlatformManager");
    }
}
