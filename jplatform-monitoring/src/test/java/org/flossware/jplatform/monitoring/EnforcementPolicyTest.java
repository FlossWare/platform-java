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

package org.flossware.jplatform.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EnforcementPolicy.
 */
class EnforcementPolicyTest {

    @Test
    void testConstructor_validParameters() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        assertEquals("app1", policy.getApplicationId());
        assertEquals(3, policy.getGracePeriod());
    }

    @Test
    void testConstructor_nullApplicationId() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new EnforcementPolicy(null, 3));

        assertTrue(exception.getMessage().contains("applicationId cannot be null"));
    }

    @Test
    void testConstructor_zeroGracePeriod() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new EnforcementPolicy("app1", 0));

        assertTrue(exception.getMessage().contains("gracePeriod must be positive"));
    }

    @Test
    void testConstructor_negativeGracePeriod() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new EnforcementPolicy("app1", -1));

        assertTrue(exception.getMessage().contains("gracePeriod must be positive"));
    }

    @Test
    void testRecordViolation_nullResourceType() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> policy.recordViolation(null));

        assertTrue(exception.getMessage().contains("resourceType cannot be null or empty"));
    }

    @Test
    void testRecordViolation_emptyResourceType() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> policy.recordViolation(""));

        assertTrue(exception.getMessage().contains("resourceType cannot be null or empty"));
    }

    @Test
    void testRecordViolation_whitespaceResourceType() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> policy.recordViolation("   "));

        assertTrue(exception.getMessage().contains("resourceType cannot be null or empty"));
    }

    @Test
    void testRecordViolation_singleViolation() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        boolean result = policy.recordViolation("heap");

        assertFalse(result); // Not yet at grace period
        assertEquals(1, policy.getViolationCount("heap"));
    }

    @Test
    void testRecordViolation_reachesGracePeriod() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        assertFalse(policy.recordViolation("heap")); // count = 1
        assertFalse(policy.recordViolation("heap")); // count = 2
        assertTrue(policy.recordViolation("heap"));  // count = 3, reaches grace period

        assertEquals(3, policy.getViolationCount("heap"));
    }

    @Test
    void testRecordViolation_exceedsGracePeriod() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        policy.recordViolation("heap");
        policy.recordViolation("heap");
        policy.recordViolation("heap");
        assertTrue(policy.recordViolation("heap")); // count = 4, still exceeds

        assertEquals(4, policy.getViolationCount("heap"));
    }

    @Test
    void testRecordViolation_multipleResourceTypes() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 2);

        policy.recordViolation("heap");
        policy.recordViolation("cpu");
        policy.recordViolation("heap");

        assertEquals(2, policy.getViolationCount("heap"));
        assertEquals(1, policy.getViolationCount("cpu"));
    }

    @Test
    void testClearViolation_nullResourceType() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> policy.clearViolation(null));

        assertTrue(exception.getMessage().contains("resourceType cannot be null or empty"));
    }

    @Test
    void testClearViolation_emptyResourceType() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> policy.clearViolation(""));

        assertTrue(exception.getMessage().contains("resourceType cannot be null or empty"));
    }

    @Test
    void testClearViolation_resetsCount() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        policy.recordViolation("heap");
        policy.recordViolation("heap");
        assertEquals(2, policy.getViolationCount("heap"));

        policy.clearViolation("heap");
        assertEquals(0, policy.getViolationCount("heap"));
    }

    @Test
    void testClearViolation_nonExistentResource() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        // Should not throw
        assertDoesNotThrow(() -> policy.clearViolation("heap"));
        assertEquals(0, policy.getViolationCount("heap"));
    }

    @Test
    void testGetViolationCount_nullResourceType() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> policy.getViolationCount(null));

        assertTrue(exception.getMessage().contains("resourceType cannot be null or empty"));
    }

    @Test
    void testGetViolationCount_emptyResourceType() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> policy.getViolationCount(""));

        assertTrue(exception.getMessage().contains("resourceType cannot be null or empty"));
    }

    @Test
    void testGetViolationCount_noViolations() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        assertEquals(0, policy.getViolationCount("heap"));
    }

    @Test
    void testClearAll() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 3);

        policy.recordViolation("heap");
        policy.recordViolation("cpu");
        policy.recordViolation("threads");

        assertEquals(1, policy.getViolationCount("heap"));
        assertEquals(1, policy.getViolationCount("cpu"));
        assertEquals(1, policy.getViolationCount("threads"));

        policy.clearAll();

        assertEquals(0, policy.getViolationCount("heap"));
        assertEquals(0, policy.getViolationCount("cpu"));
        assertEquals(0, policy.getViolationCount("threads"));
    }

    @Test
    void testGracePeriodOfOne() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 1);

        // First violation should trigger immediately
        assertTrue(policy.recordViolation("heap"));
        assertEquals(1, policy.getViolationCount("heap"));
    }

    @Test
    void testConcurrentResourceTypes() {
        EnforcementPolicy policy = new EnforcementPolicy("app1", 2);

        // Record violations for different resources
        policy.recordViolation("heap");
        policy.recordViolation("cpu");
        policy.recordViolation("threads");

        // Each should have independent counts
        assertEquals(1, policy.getViolationCount("heap"));
        assertEquals(1, policy.getViolationCount("cpu"));
        assertEquals(1, policy.getViolationCount("threads"));

        // Clear one shouldn't affect others
        policy.clearViolation("cpu");
        assertEquals(1, policy.getViolationCount("heap"));
        assertEquals(0, policy.getViolationCount("cpu"));
        assertEquals(1, policy.getViolationCount("threads"));
    }
}
