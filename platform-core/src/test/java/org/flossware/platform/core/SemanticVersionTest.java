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

package org.flossware.platform.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for SemanticVersion. */
@Tag("unit")
class SemanticVersionTest {

  @Test
  void testConstructorWithValidValues() {
    SemanticVersion version = new SemanticVersion(1, 2, 3);
    assertEquals(1, version.getMajor());
    assertEquals(2, version.getMinor());
    assertEquals(3, version.getPatch());
  }

  @Test
  void testConstructorWithZeroValues() {
    SemanticVersion version = new SemanticVersion(0, 0, 0);
    assertEquals(0, version.getMajor());
    assertEquals(0, version.getMinor());
    assertEquals(0, version.getPatch());
  }

  @Test
  void testConstructorWithNegativeMajorThrows() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> new SemanticVersion(-1, 0, 0));
    assertTrue(ex.getMessage().contains("major version cannot be negative"));
  }

  @Test
  void testConstructorWithNegativeMinorThrows() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> new SemanticVersion(1, -1, 0));
    assertTrue(ex.getMessage().contains("minor version cannot be negative"));
  }

  @Test
  void testConstructorWithNegativePatchThrows() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> new SemanticVersion(1, 0, -1));
    assertTrue(ex.getMessage().contains("patch version cannot be negative"));
  }

  @Test
  void testParseValidVersion() {
    SemanticVersion version = SemanticVersion.parse("1.2.3");
    assertEquals(1, version.getMajor());
    assertEquals(2, version.getMinor());
    assertEquals(3, version.getPatch());
  }

  @Test
  void testParseVersionWithWhitespace() {
    SemanticVersion version = SemanticVersion.parse("  1.2.3  ");
    assertEquals(1, version.getMajor());
    assertEquals(2, version.getMinor());
    assertEquals(3, version.getPatch());
  }

  @Test
  void testParseVersionWithLargeNumbers() {
    SemanticVersion version = SemanticVersion.parse("100.200.300");
    assertEquals(100, version.getMajor());
    assertEquals(200, version.getMinor());
    assertEquals(300, version.getPatch());
  }

  @Test
  void testParseVersionWithZeros() {
    SemanticVersion version = SemanticVersion.parse("0.0.0");
    assertEquals(0, version.getMajor());
    assertEquals(0, version.getMinor());
    assertEquals(0, version.getPatch());
  }

  @Test
  void testParseNullVersionThrows() {
    assertThrows(NullPointerException.class, () -> SemanticVersion.parse(null));
  }

  @Test
  void testParseInvalidFormatMissingPatchThrows() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.2"));
    assertTrue(ex.getMessage().contains("Invalid semantic version format"));
  }

  @Test
  void testParseInvalidFormatExtraComponentThrows() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.2.3.4"));
    assertTrue(ex.getMessage().contains("Invalid semantic version format"));
  }

  @Test
  void testParseInvalidFormatNonNumericThrows() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.2.x"));
    assertTrue(ex.getMessage().contains("Invalid semantic version format"));
  }

  @Test
  void testParseInvalidFormatLeadingZeroThrows() {
    // Leading zeros not allowed in semver (except for 0 itself)
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("01.2.3"));
    assertTrue(ex.getMessage().contains("Invalid semantic version format"));
  }

  @Test
  void testParseEmptyStringThrows() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse(""));
    assertTrue(ex.getMessage().contains("Invalid semantic version format"));
  }

  @Test
  void testCompareToEqual() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(1, 2, 3);
    assertEquals(0, v1.compareTo(v2));
    assertEquals(0, v2.compareTo(v1));
  }

  @Test
  void testCompareToMajorDifference() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(2, 0, 0);
    assertTrue(v1.compareTo(v2) < 0);
    assertTrue(v2.compareTo(v1) > 0);
  }

  @Test
  void testCompareToMinorDifference() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(1, 3, 0);
    assertTrue(v1.compareTo(v2) < 0);
    assertTrue(v2.compareTo(v1) > 0);
  }

  @Test
  void testCompareToPatchDifference() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(1, 2, 4);
    assertTrue(v1.compareTo(v2) < 0);
    assertTrue(v2.compareTo(v1) > 0);
  }

  @Test
  void testCompareToNullThrows() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    assertThrows(NullPointerException.class, () -> v1.compareTo(null));
  }

  @Test
  void testIsGreaterThan() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 4);
    SemanticVersion v2 = new SemanticVersion(1, 2, 3);
    assertTrue(v1.isGreaterThan(v2));
    assertFalse(v2.isGreaterThan(v1));
    assertFalse(v1.isGreaterThan(v1));
  }

  @Test
  void testIsLessThan() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(1, 2, 4);
    assertTrue(v1.isLessThan(v2));
    assertFalse(v2.isLessThan(v1));
    assertFalse(v1.isLessThan(v1));
  }

  @Test
  void testIsGreaterThanOrEqualTo() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 4);
    SemanticVersion v2 = new SemanticVersion(1, 2, 3);
    SemanticVersion v3 = new SemanticVersion(1, 2, 4);
    assertTrue(v1.isGreaterThanOrEqualTo(v2));
    assertTrue(v1.isGreaterThanOrEqualTo(v3));
    assertFalse(v2.isGreaterThanOrEqualTo(v1));
  }

  @Test
  void testIsLessThanOrEqualTo() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(1, 2, 4);
    SemanticVersion v3 = new SemanticVersion(1, 2, 3);
    assertTrue(v1.isLessThanOrEqualTo(v2));
    assertTrue(v1.isLessThanOrEqualTo(v3));
    assertFalse(v2.isLessThanOrEqualTo(v1));
  }

  @Test
  void testIsCompatibleWithSameMajorHigherMinor() {
    SemanticVersion current = new SemanticVersion(2, 1, 0);
    SemanticVersion required = new SemanticVersion(2, 0, 0);
    assertTrue(current.isCompatibleWith(required));
  }

  @Test
  void testIsCompatibleWithSameMajorHigherPatch() {
    SemanticVersion current = new SemanticVersion(2, 0, 1);
    SemanticVersion required = new SemanticVersion(2, 0, 0);
    assertTrue(current.isCompatibleWith(required));
  }

  @Test
  void testIsCompatibleWithExactMatch() {
    SemanticVersion current = new SemanticVersion(2, 0, 0);
    SemanticVersion required = new SemanticVersion(2, 0, 0);
    assertTrue(current.isCompatibleWith(required));
  }

  @Test
  void testIsNotCompatibleWithDifferentMajor() {
    SemanticVersion current = new SemanticVersion(2, 0, 0);
    SemanticVersion required = new SemanticVersion(1, 9, 9);
    assertFalse(current.isCompatibleWith(required));
  }

  @Test
  void testIsNotCompatibleWithLowerMinor() {
    SemanticVersion current = new SemanticVersion(2, 0, 0);
    SemanticVersion required = new SemanticVersion(2, 1, 0);
    assertFalse(current.isCompatibleWith(required));
  }

  @Test
  void testIsNotCompatibleWithLowerPatch() {
    SemanticVersion current = new SemanticVersion(2, 0, 0);
    SemanticVersion required = new SemanticVersion(2, 0, 1);
    assertFalse(current.isCompatibleWith(required));
  }

  @Test
  void testIsCompatibleWithNullThrows() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    assertThrows(NullPointerException.class, () -> v1.isCompatibleWith(null));
  }

  @Test
  void testEqualsWithSameObject() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    assertEquals(v1, v1);
  }

  @Test
  void testEqualsWithEqualValues() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(1, 2, 3);
    assertEquals(v1, v2);
    assertEquals(v2, v1);
  }

  @Test
  void testNotEqualsWithDifferentMajor() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(2, 2, 3);
    assertNotEquals(v1, v2);
  }

  @Test
  void testNotEqualsWithDifferentMinor() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(1, 3, 3);
    assertNotEquals(v1, v2);
  }

  @Test
  void testNotEqualsWithDifferentPatch() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(1, 2, 4);
    assertNotEquals(v1, v2);
  }

  @Test
  void testNotEqualsWithNull() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    assertNotEquals(null, v1);
  }

  @Test
  void testNotEqualsWithDifferentType() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    assertNotEquals("1.2.3", v1);
  }

  @Test
  void testHashCodeConsistency() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(1, 2, 3);
    assertEquals(v1.hashCode(), v2.hashCode());
  }

  @Test
  void testHashCodeDifferentForDifferentVersions() {
    SemanticVersion v1 = new SemanticVersion(1, 2, 3);
    SemanticVersion v2 = new SemanticVersion(1, 2, 4);
    // Not guaranteed to be different, but highly likely
    assertNotEquals(v1.hashCode(), v2.hashCode());
  }

  @Test
  void testToString() {
    SemanticVersion version = new SemanticVersion(1, 2, 3);
    assertEquals("1.2.3", version.toString());
  }

  @Test
  void testToStringWithZeros() {
    SemanticVersion version = new SemanticVersion(0, 0, 0);
    assertEquals("0.0.0", version.toString());
  }

  @Test
  void testToStringWithLargeNumbers() {
    SemanticVersion version = new SemanticVersion(100, 200, 300);
    assertEquals("100.200.300", version.toString());
  }

  @Test
  void testComparisonTransitivity() {
    // If v1 < v2 and v2 < v3, then v1 < v3
    SemanticVersion v1 = new SemanticVersion(1, 0, 0);
    SemanticVersion v2 = new SemanticVersion(1, 1, 0);
    SemanticVersion v3 = new SemanticVersion(1, 2, 0);

    assertTrue(v1.compareTo(v2) < 0);
    assertTrue(v2.compareTo(v3) < 0);
    assertTrue(v1.compareTo(v3) < 0);
  }

  @Test
  void testVersionOrderingWithMultipleComponents() {
    SemanticVersion v1 = new SemanticVersion(1, 0, 0);
    SemanticVersion v2 = new SemanticVersion(1, 0, 1);
    SemanticVersion v3 = new SemanticVersion(1, 1, 0);
    SemanticVersion v4 = new SemanticVersion(2, 0, 0);

    // Verify correct ordering
    assertTrue(v1.isLessThan(v2));
    assertTrue(v2.isLessThan(v3));
    assertTrue(v3.isLessThan(v4));
  }
}
