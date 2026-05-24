package org.flossware.jplatform.registry.eureka;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EurekaRegistryConfigTest {

    @Test
    void testBuilderDefaults() {
        EurekaRegistryConfig config = EurekaRegistryConfig.builder()
            .appName("test-app")
            .build();

        assertEquals(1, config.getServiceUrls().size());
        assertEquals("http://localhost:8761/eureka", config.getServiceUrls().get(0));
        assertEquals("test-app", config.getAppName());
        assertNull(config.getInstanceId());
        assertEquals(30, config.getRenewalIntervalSeconds());
        assertEquals(90, config.getLeaseExpirationSeconds());
        assertTrue(config.isRegisterWithEureka());
        assertTrue(config.isFetchRegistry());
    }

    @Test
    void testBuilderWithAllFields() {
        EurekaRegistryConfig config = EurekaRegistryConfig.builder()
            .addServiceUrl("http://eureka1:8761/eureka")
            .appName("my-service")
            .instanceId("instance-1")
            .renewalIntervalSeconds(15)
            .leaseExpirationSeconds(45)
            .registerWithEureka(false)
            .fetchRegistry(false)
            .build();

        assertEquals(1, config.getServiceUrls().size());
        assertEquals("http://eureka1:8761/eureka", config.getServiceUrls().get(0));
        assertEquals("my-service", config.getAppName());
        assertEquals("instance-1", config.getInstanceId());
        assertEquals(15, config.getRenewalIntervalSeconds());
        assertEquals(45, config.getLeaseExpirationSeconds());
        assertFalse(config.isRegisterWithEureka());
        assertFalse(config.isFetchRegistry());
    }

    @Test
    void testAddServiceUrl() {
        EurekaRegistryConfig config = EurekaRegistryConfig.builder()
            .addServiceUrl("http://eureka1:8761/eureka")
            .addServiceUrl("http://eureka2:8761/eureka")
            .appName("app")
            .build();

        assertEquals(2, config.getServiceUrls().size());
        assertTrue(config.getServiceUrls().contains("http://eureka1:8761/eureka"));
        assertTrue(config.getServiceUrls().contains("http://eureka2:8761/eureka"));
    }

    @Test
    void testServiceUrlsReplacement() {
        EurekaRegistryConfig config = EurekaRegistryConfig.builder()
            .serviceUrls(Arrays.asList(
                "http://eureka1:8761/eureka",
                "http://eureka2:8761/eureka",
                "http://eureka3:8761/eureka"
            ))
            .appName("app")
            .build();

        assertEquals(3, config.getServiceUrls().size());
    }

    @Test
    void testRenewalIntervalValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            EurekaRegistryConfig.builder()
                .appName("app")
                .renewalIntervalSeconds(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("Renewal interval"));
    }

    @Test
    void testRenewalIntervalNegative() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            EurekaRegistryConfig.builder()
                .appName("app")
                .renewalIntervalSeconds(-5)
                .build();
        });
        assertTrue(exception.getMessage().contains("Renewal interval"));
    }

    @Test
    void testLeaseExpirationValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            EurekaRegistryConfig.builder()
                .appName("app")
                .leaseExpirationSeconds(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("Lease expiration"));
    }

    @Test
    void testLeaseExpirationNegative() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            EurekaRegistryConfig.builder()
                .appName("app")
                .leaseExpirationSeconds(-10)
                .build();
        });
        assertTrue(exception.getMessage().contains("Lease expiration"));
    }

    @Test
    void testDefaultAppName() {
        EurekaRegistryConfig config = EurekaRegistryConfig.builder().build();
        assertEquals("jplatform-app", config.getAppName());
    }

    @Test
    void testEmptyAppName() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            EurekaRegistryConfig.builder()
                .appName("  ")
                .build();
        });
        assertTrue(exception.getMessage().contains("App name"));
    }

    @Test
    void testBuilderChaining() {
        EurekaRegistryConfig.Builder builder = EurekaRegistryConfig.builder();
        assertSame(builder, builder.addServiceUrl("http://localhost"));
        assertSame(builder, builder.serviceUrls(Arrays.asList("http://localhost")));
        assertSame(builder, builder.appName("app"));
        assertSame(builder, builder.instanceId("instance"));
        assertSame(builder, builder.renewalIntervalSeconds(20));
        assertSame(builder, builder.leaseExpirationSeconds(60));
        assertSame(builder, builder.registerWithEureka(true));
        assertSame(builder, builder.fetchRegistry(true));
    }

    @Test
    void testImmutableServiceUrls() {
        EurekaRegistryConfig config = EurekaRegistryConfig.builder()
            .addServiceUrl("http://eureka:8761/eureka")
            .appName("app")
            .build();

        assertThrows(UnsupportedOperationException.class, () -> {
            config.getServiceUrls().add("http://new:8761/eureka");
        });
    }


    @Test
    void testHighAvailabilitySetup() {
        EurekaRegistryConfig config = EurekaRegistryConfig.builder()
            .serviceUrls(Arrays.asList(
                "http://eureka1:8761/eureka",
                "http://eureka2:8762/eureka",
                "http://eureka3:8763/eureka"
            ))
            .appName("ha-service")
            .renewalIntervalSeconds(10)
            .leaseExpirationSeconds(30)
            .build();

        assertEquals(3, config.getServiceUrls().size());
        assertEquals(10, config.getRenewalIntervalSeconds());
        assertEquals(30, config.getLeaseExpirationSeconds());
    }

    @Test
    void testClientOnlyMode() {
        EurekaRegistryConfig config = EurekaRegistryConfig.builder()
            .appName("client-only")
            .registerWithEureka(false)
            .fetchRegistry(true)
            .build();

        assertFalse(config.isRegisterWithEureka());
        assertTrue(config.isFetchRegistry());
    }

    @Test
    void testInstanceIdCustomization() {
        EurekaRegistryConfig config = EurekaRegistryConfig.builder()
            .appName("app")
            .instanceId("custom-instance-123")
            .build();

        assertEquals("custom-instance-123", config.getInstanceId());
    }
}
