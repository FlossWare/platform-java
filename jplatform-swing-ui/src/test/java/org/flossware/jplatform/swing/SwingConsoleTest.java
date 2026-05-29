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
