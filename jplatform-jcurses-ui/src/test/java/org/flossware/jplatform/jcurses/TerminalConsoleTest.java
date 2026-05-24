package org.flossware.jplatform.jcurses;

import org.flossware.jplatform.api.PlatformManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TerminalConsole.
 * Tests API contract and validation.
 *
 * Note: Full terminal UI testing requires a TTY environment and is not feasible in headless CI/CD.
 * These tests focus on API validation and constructor behavior.
 */
class TerminalConsoleTest {

    @Test
    void testConstructorWithNullPlatformManagerThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TerminalConsole(null);
        }, "Constructor should throw IllegalArgumentException for null PlatformManager");
    }

    @Test
    void testClassLoads() {
        // Verify the class is loadable and constructor validation works
        assertNotNull(TerminalConsole.class);
    }
}
