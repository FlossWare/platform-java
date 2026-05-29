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

package org.flossware.jplatform.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for EnforcementAction enum.
 * Tests all enum values, isDestructive(), and isGraceful() methods.
 */
class EnforcementActionTest {

    @Test
    void testAllEnumValues() {
        EnforcementAction[] actions = EnforcementAction.values();

        assertEquals(4, actions.length);
        assertEquals(EnforcementAction.NOTIFY, actions[0]);
        assertEquals(EnforcementAction.THROTTLE, actions[1]);
        assertEquals(EnforcementAction.SHUTDOWN, actions[2]);
        assertEquals(EnforcementAction.KILL, actions[3]);
    }

    @Test
    void testValueOf() {
        assertEquals(EnforcementAction.NOTIFY, EnforcementAction.valueOf("NOTIFY"));
        assertEquals(EnforcementAction.THROTTLE, EnforcementAction.valueOf("THROTTLE"));
        assertEquals(EnforcementAction.SHUTDOWN, EnforcementAction.valueOf("SHUTDOWN"));
        assertEquals(EnforcementAction.KILL, EnforcementAction.valueOf("KILL"));
    }

    @Test
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () ->
            EnforcementAction.valueOf("INVALID")
        );
    }

    @Test
    void testValueOfNull() {
        assertThrows(NullPointerException.class, () ->
            EnforcementAction.valueOf(null)
        );
    }

    @Test
    void testIsDestructiveNotify() {
        assertFalse(EnforcementAction.NOTIFY.isDestructive());
    }

    @Test
    void testIsDestructiveThrottle() {
        assertFalse(EnforcementAction.THROTTLE.isDestructive());
    }

    @Test
    void testIsDestructiveShutdown() {
        assertTrue(EnforcementAction.SHUTDOWN.isDestructive());
    }

    @Test
    void testIsDestructiveKill() {
        assertTrue(EnforcementAction.KILL.isDestructive());
    }

    @Test
    void testIsGracefulNotify() {
        assertTrue(EnforcementAction.NOTIFY.isGraceful());
    }

    @Test
    void testIsGracefulThrottle() {
        assertTrue(EnforcementAction.THROTTLE.isGraceful());
    }

    @Test
    void testIsGracefulShutdown() {
        assertTrue(EnforcementAction.SHUTDOWN.isGraceful());
    }

    @Test
    void testIsGracefulKill() {
        assertFalse(EnforcementAction.KILL.isGraceful());
    }

    @Test
    void testEnumToString() {
        assertEquals("NOTIFY", EnforcementAction.NOTIFY.toString());
        assertEquals("THROTTLE", EnforcementAction.THROTTLE.toString());
        assertEquals("SHUTDOWN", EnforcementAction.SHUTDOWN.toString());
        assertEquals("KILL", EnforcementAction.KILL.toString());
    }

    @Test
    void testEnumEquality() {
        assertSame(EnforcementAction.NOTIFY, EnforcementAction.valueOf("NOTIFY"));
        assertSame(EnforcementAction.SHUTDOWN, EnforcementAction.valueOf("SHUTDOWN"));
        assertNotSame(EnforcementAction.NOTIFY, EnforcementAction.SHUTDOWN);
    }

    @Test
    void testEnumOrdinality() {
        assertEquals(0, EnforcementAction.NOTIFY.ordinal());
        assertEquals(1, EnforcementAction.THROTTLE.ordinal());
        assertEquals(2, EnforcementAction.SHUTDOWN.ordinal());
        assertEquals(3, EnforcementAction.KILL.ordinal());
    }

    @Test
    void testDestructiveVsGraceful() {
        // KILL is the only destructive AND non-graceful action
        assertTrue(EnforcementAction.KILL.isDestructive());
        assertFalse(EnforcementAction.KILL.isGraceful());

        // SHUTDOWN is destructive but graceful
        assertTrue(EnforcementAction.SHUTDOWN.isDestructive());
        assertTrue(EnforcementAction.SHUTDOWN.isGraceful());

        // NOTIFY and THROTTLE are non-destructive and graceful
        assertFalse(EnforcementAction.NOTIFY.isDestructive());
        assertTrue(EnforcementAction.NOTIFY.isGraceful());
        assertFalse(EnforcementAction.THROTTLE.isDestructive());
        assertTrue(EnforcementAction.THROTTLE.isGraceful());
    }
}
