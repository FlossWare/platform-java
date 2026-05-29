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

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for SimpleServiceRegistry. */
@Tag("unit")
class SimpleServiceRegistryTest {

  private SimpleServiceRegistry registry;

  // Test interfaces and implementations
  interface TestService {
    String getName();
  }

  static class TestServiceImpl implements TestService {
    private final String name;

    TestServiceImpl(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  interface AnotherService {
    int getValue();
  }

  static class AnotherServiceImpl implements AnotherService {
    @Override
    public int getValue() {
      return 42;
    }
  }

  @BeforeEach
  void setUp() {
    registry = new SimpleServiceRegistry();
  }

  @Test
  void testRegisterAndGetService() {
    TestService service = new TestServiceImpl("test1");
    registry.registerService(TestService.class, service);

    Optional<TestService> retrieved = registry.getService(TestService.class);

    assertTrue(retrieved.isPresent());
    assertEquals("test1", retrieved.get().getName());
  }

  @Test
  void testGetServiceNotFound() {
    Optional<TestService> retrieved = registry.getService(TestService.class);

    assertFalse(retrieved.isPresent());
  }

  @Test
  void testRegisterMultipleServices() {
    TestService service1 = new TestServiceImpl("test1");
    TestService service2 = new TestServiceImpl("test2");

    registry.registerService(TestService.class, service1);
    registry.registerService(TestService.class, service2);

    List<TestService> services = registry.getAllServices(TestService.class);

    assertEquals(2, services.size());
    assertEquals("test1", services.get(0).getName());
    assertEquals("test2", services.get(1).getName());
  }

  @Test
  void testGetServiceReturnsFirst() {
    TestService service1 = new TestServiceImpl("first");
    TestService service2 = new TestServiceImpl("second");

    registry.registerService(TestService.class, service1);
    registry.registerService(TestService.class, service2);

    Optional<TestService> retrieved = registry.getService(TestService.class);

    assertTrue(retrieved.isPresent());
    assertEquals("first", retrieved.get().getName());
  }

  @Test
  void testUnregisterService() {
    TestService service = new TestServiceImpl("test1");
    registry.registerService(TestService.class, service);

    registry.unregisterService(TestService.class, service);

    Optional<TestService> retrieved = registry.getService(TestService.class);
    assertFalse(retrieved.isPresent());
  }

  @Test
  void testUnregisterOneOfMultiple() {
    TestService service1 = new TestServiceImpl("test1");
    TestService service2 = new TestServiceImpl("test2");

    registry.registerService(TestService.class, service1);
    registry.registerService(TestService.class, service2);

    registry.unregisterService(TestService.class, service1);

    List<TestService> services = registry.getAllServices(TestService.class);
    assertEquals(1, services.size());
    assertEquals("test2", services.get(0).getName());
  }

  @Test
  void testClearAllServices() {
    TestService testService = new TestServiceImpl("test");
    AnotherService anotherService = new AnotherServiceImpl();

    registry.registerService(TestService.class, testService);
    registry.registerService(AnotherService.class, anotherService);

    registry.clear();

    assertFalse(registry.getService(TestService.class).isPresent());
    assertFalse(registry.getService(AnotherService.class).isPresent());
  }

  @Test
  void testGetRegisteredInterfaces() {
    registry.registerService(TestService.class, new TestServiceImpl("test"));
    registry.registerService(AnotherService.class, new AnotherServiceImpl());

    List<Class<?>> interfaces = registry.getRegisteredInterfaces();

    assertEquals(2, interfaces.size());
    assertTrue(interfaces.contains(TestService.class));
    assertTrue(interfaces.contains(AnotherService.class));
  }

  @Test
  void testGetAllServicesReturnsEmptyList() {
    List<TestService> services = registry.getAllServices(TestService.class);

    assertNotNull(services);
    assertTrue(services.isEmpty());
  }

  @Test
  void testRegisterNullInterfaceThrows() {
    TestService service = new TestServiceImpl("test");

    assertThrows(
        NullPointerException.class,
        () -> {
          registry.registerService(null, service);
        });
  }

  @Test
  void testRegisterNullImplementationThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          registry.registerService(TestService.class, null);
        });
  }

  @Test
  void testRegisterNonInterfaceThrows() {
    String notAnInterface = "test";

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          registry.registerService((Class) String.class, notAnInterface);
        });
  }

  @Test
  void testRegisterIncompatibleImplementationThrows() {
    AnotherService wrongImpl = new AnotherServiceImpl();

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          registry.registerService((Class) TestService.class, wrongImpl);
        });
  }

  @Test
  void testGetServiceWithNullInterfaceThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          registry.getService(null);
        });
  }

  @Test
  void testGetAllServicesWithNullInterfaceThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          registry.getAllServices(null);
        });
  }

  @Test
  void testGetServiceCount() {
    assertEquals(0, registry.getServiceCount());

    registry.registerService(TestService.class, new TestServiceImpl("test1"));
    registry.registerService(TestService.class, new TestServiceImpl("test2"));

    // getServiceCount returns total service count across all interfaces
    assertEquals(2, registry.getServiceCount());

    registry.registerService(AnotherService.class, new AnotherServiceImpl());
    assertEquals(3, registry.getServiceCount());
  }

  @Test
  void testGetInterfaceCount() {
    assertEquals(0, registry.getInterfaceCount());

    registry.registerService(TestService.class, new TestServiceImpl("test"));
    assertEquals(1, registry.getInterfaceCount());

    registry.registerService(AnotherService.class, new AnotherServiceImpl());
    assertEquals(2, registry.getInterfaceCount());

    // Registering another implementation of same interface doesn't increase count
    registry.registerService(TestService.class, new TestServiceImpl("test2"));
    assertEquals(2, registry.getInterfaceCount());
  }

  // Version-related tests

  @Test
  void testRegisterServiceWithVersion() {
    TestService service = new TestServiceImpl("versioned");
    registry.registerService(TestService.class, service, "1.2.3");

    Optional<TestService> retrieved = registry.getService(TestService.class);
    assertTrue(retrieved.isPresent());
    assertEquals("versioned", retrieved.get().getName());
  }

  @Test
  void testGetServiceWithCompatibleVersion() {
    TestService service = new TestServiceImpl("v1.5.0");
    registry.registerService(TestService.class, service, "1.5.0");

    // Request version 1.2.0 - should get 1.5.0 (compatible)
    Optional<TestService> retrieved = registry.getService(TestService.class, "1.2.0");
    assertTrue(retrieved.isPresent());
    assertEquals("v1.5.0", retrieved.get().getName());
  }

  @Test
  void testGetServiceWithIncompatibleMajorVersion() {
    TestService service = new TestServiceImpl("v1.5.0");
    registry.registerService(TestService.class, service, "1.5.0");

    // Request version 2.0.0 - different major version
    Optional<TestService> retrieved = registry.getService(TestService.class, "2.0.0");
    assertFalse(retrieved.isPresent());
  }

  @Test
  void testGetServiceWithTooHighMinorVersion() {
    TestService service = new TestServiceImpl("v1.2.0");
    registry.registerService(TestService.class, service, "1.2.0");

    // Request version 1.5.0 - service version too old
    Optional<TestService> retrieved = registry.getService(TestService.class, "1.5.0");
    assertFalse(retrieved.isPresent());
  }

  @Test
  void testGetServiceWithExactVersionMatch() {
    TestService service = new TestServiceImpl("v2.3.4");
    registry.registerService(TestService.class, service, "2.3.4");

    Optional<TestService> retrieved = registry.getService(TestService.class, "2.3.4");
    assertTrue(retrieved.isPresent());
    assertEquals("v2.3.4", retrieved.get().getName());
  }

  @Test
  void testGetServiceFindsFirstCompatibleVersion() {
    TestService service1 = new TestServiceImpl("v1.0.0");
    TestService service2 = new TestServiceImpl("v1.5.0");
    TestService service3 = new TestServiceImpl("v2.0.0");

    registry.registerService(TestService.class, service1, "1.0.0");
    registry.registerService(TestService.class, service2, "1.5.0");
    registry.registerService(TestService.class, service3, "2.0.0");

    // Request 1.2.0 - should get 1.5.0 (first compatible)
    Optional<TestService> retrieved = registry.getService(TestService.class, "1.2.0");
    assertTrue(retrieved.isPresent());
    assertEquals("v1.5.0", retrieved.get().getName());
  }

  @Test
  void testGetServiceWithVersionIgnoresUnversionedServices() {
    TestService unversioned = new TestServiceImpl("unversioned");
    TestService versioned = new TestServiceImpl("v1.5.0");

    registry.registerService(TestService.class, unversioned); // No version
    registry.registerService(TestService.class, versioned, "1.5.0");

    Optional<TestService> retrieved = registry.getService(TestService.class, "1.2.0");
    assertTrue(retrieved.isPresent());
    assertEquals("v1.5.0", retrieved.get().getName());
  }

  @Test
  void testGetServiceWithVersionReturnsEmptyWhenNoVersionedServices() {
    TestService unversioned = new TestServiceImpl("unversioned");
    registry.registerService(TestService.class, unversioned); // No version

    Optional<TestService> retrieved = registry.getService(TestService.class, "1.0.0");
    assertFalse(retrieved.isPresent());
  }

  @Test
  void testRegisterServiceWithInvalidVersionThrows() {
    TestService service = new TestServiceImpl("test");
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          registry.registerService(TestService.class, service, "invalid");
        });
  }

  @Test
  void testGetServiceWithInvalidMinVersionThrows() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          registry.getService(TestService.class, "invalid");
        });
  }

  @Test
  void testGetServiceWithNullMinVersionThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          registry.getService(TestService.class, null);
        });
  }

  @Test
  void testUnregisterVersionedService() {
    TestService service = new TestServiceImpl("v1.0.0");
    registry.registerService(TestService.class, service, "1.0.0");

    registry.unregisterService(TestService.class, service);

    Optional<TestService> retrieved = registry.getService(TestService.class, "1.0.0");
    assertFalse(retrieved.isPresent());
  }

  @Test
  void testGetAllServicesIncludesVersionedServices() {
    TestService service1 = new TestServiceImpl("v1.0.0");
    TestService service2 = new TestServiceImpl("v2.0.0");

    registry.registerService(TestService.class, service1, "1.0.0");
    registry.registerService(TestService.class, service2, "2.0.0");

    List<TestService> services = registry.getAllServices(TestService.class);
    assertEquals(2, services.size());
  }
}
