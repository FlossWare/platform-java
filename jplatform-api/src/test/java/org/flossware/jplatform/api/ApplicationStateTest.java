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
 * Comprehensive unit tests for ApplicationState enum.
 * Tests all enum values and basic enum functionality.
 */
class ApplicationStateTest {

    @Test
    void testAllEnumValues() {
        ApplicationState[] states = ApplicationState.values();

        assertEquals(7, states.length);
        assertEquals(ApplicationState.DEPLOYED, states[0]);
        assertEquals(ApplicationState.STARTING, states[1]);
        assertEquals(ApplicationState.RUNNING, states[2]);
        assertEquals(ApplicationState.STOPPING, states[3]);
        assertEquals(ApplicationState.STOPPED, states[4]);
        assertEquals(ApplicationState.FAILED, states[5]);
        assertEquals(ApplicationState.UNDEPLOYED, states[6]);
    }

    @Test
    void testValueOf() {
        assertEquals(ApplicationState.DEPLOYED, ApplicationState.valueOf("DEPLOYED"));
        assertEquals(ApplicationState.STARTING, ApplicationState.valueOf("STARTING"));
        assertEquals(ApplicationState.RUNNING, ApplicationState.valueOf("RUNNING"));
        assertEquals(ApplicationState.STOPPING, ApplicationState.valueOf("STOPPING"));
        assertEquals(ApplicationState.STOPPED, ApplicationState.valueOf("STOPPED"));
        assertEquals(ApplicationState.FAILED, ApplicationState.valueOf("FAILED"));
        assertEquals(ApplicationState.UNDEPLOYED, ApplicationState.valueOf("UNDEPLOYED"));
    }

    @Test
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () ->
            ApplicationState.valueOf("INVALID")
        );
    }

    @Test
    void testValueOfNull() {
        assertThrows(NullPointerException.class, () ->
            ApplicationState.valueOf(null)
        );
    }

    @Test
    void testEnumToString() {
        assertEquals("DEPLOYED", ApplicationState.DEPLOYED.toString());
        assertEquals("STARTING", ApplicationState.STARTING.toString());
        assertEquals("RUNNING", ApplicationState.RUNNING.toString());
        assertEquals("STOPPING", ApplicationState.STOPPING.toString());
        assertEquals("STOPPED", ApplicationState.STOPPED.toString());
        assertEquals("FAILED", ApplicationState.FAILED.toString());
        assertEquals("UNDEPLOYED", ApplicationState.UNDEPLOYED.toString());
    }

    @Test
    void testEnumEquality() {
        assertSame(ApplicationState.DEPLOYED, ApplicationState.valueOf("DEPLOYED"));
        assertSame(ApplicationState.RUNNING, ApplicationState.valueOf("RUNNING"));
        assertNotSame(ApplicationState.DEPLOYED, ApplicationState.RUNNING);
    }

    @Test
    void testEnumOrdinality() {
        assertEquals(0, ApplicationState.DEPLOYED.ordinal());
        assertEquals(1, ApplicationState.STARTING.ordinal());
        assertEquals(2, ApplicationState.RUNNING.ordinal());
        assertEquals(3, ApplicationState.STOPPING.ordinal());
        assertEquals(4, ApplicationState.STOPPED.ordinal());
        assertEquals(5, ApplicationState.FAILED.ordinal());
        assertEquals(6, ApplicationState.UNDEPLOYED.ordinal());
    }
}
