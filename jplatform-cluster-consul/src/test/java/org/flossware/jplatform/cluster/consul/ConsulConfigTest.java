package org.flossware.jplatform.cluster.consul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConsulConfig.
 */
class ConsulConfigTest {

    @Test
    void testBuilder_DefaultValues() {
        ConsulConfig config = ConsulConfig.builder().build();

        assertEquals("localhost", config.getConsulHost());
        assertEquals(8500, config.getConsulPort());
        assertEquals(10, config.getSessionTtl());
        assertEquals("jplatform-cluster", config.getServiceName());
        assertNull(config.getDatacenter());
        assertNull(config.getToken());
    }

    @Test
    void testBuilder_CustomValues() {
        ConsulConfig config = ConsulConfig.builder()
            .consulHost("192.168.1.10")
            .consulPort(8600)
            .sessionTtl(30)
            .serviceName("my-cluster")
            .datacenter("dc1")
            .token("secret-token")
            .build();

        assertEquals("192.168.1.10", config.getConsulHost());
        assertEquals(8600, config.getConsulPort());
        assertEquals(30, config.getSessionTtl());
        assertEquals("my-cluster", config.getServiceName());
        assertEquals("dc1", config.getDatacenter());
        assertEquals("secret-token", config.getToken());
    }

    @Test
    void testBuilder_SessionTtlTooLow() {
        assertThrows(IllegalArgumentException.class, () -> {
            ConsulConfig.builder().sessionTtl(5).build();
        });
    }

    @Test
    void testBuilder_SessionTtlTooHigh() {
        assertThrows(IllegalArgumentException.class, () -> {
            ConsulConfig.builder().sessionTtl(100000).build();
        });
    }

    @Test
    void testBuilder_SessionTtlBoundaries() {
        // Test minimum valid TTL
        ConsulConfig config1 = ConsulConfig.builder().sessionTtl(10).build();
        assertEquals(10, config1.getSessionTtl());

        // Test maximum valid TTL
        ConsulConfig config2 = ConsulConfig.builder().sessionTtl(86400).build();
        assertEquals(86400, config2.getSessionTtl());
    }

    @Test
    void testBuilder_Chaining() {
        ConsulConfig.Builder builder = ConsulConfig.builder();
        assertSame(builder, builder.consulHost("localhost"));
        assertSame(builder, builder.consulPort(8500));
        assertSame(builder, builder.sessionTtl(15));
        assertSame(builder, builder.serviceName("test"));
        assertSame(builder, builder.datacenter("dc1"));
        assertSame(builder, builder.token("token"));
    }

    @Test
    void testBuilder_MultipleBuilds() {
        ConsulConfig.Builder builder = ConsulConfig.builder()
            .consulHost("host1")
            .sessionTtl(20);

        ConsulConfig config1 = builder.build();
        ConsulConfig config2 = builder.build();

        // Each build should create a new instance
        assertNotSame(config1, config2);
        assertEquals(config1.getConsulHost(), config2.getConsulHost());
        assertEquals(config1.getSessionTtl(), config2.getSessionTtl());
    }
}
