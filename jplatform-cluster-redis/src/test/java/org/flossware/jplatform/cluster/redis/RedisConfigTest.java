package org.flossware.jplatform.cluster.redis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RedisConfigTest {

    @Test
    void testDefaultValues() {
        RedisConfig config = RedisConfig.builder().build();

        assertEquals("localhost", config.getHost());
        assertEquals(6379, config.getPort());
        assertEquals(10, config.getLeaseTtl());
        assertNull(config.getPassword());
        assertEquals(0, config.getDatabase());
        assertEquals(2000, config.getTimeout());
    }

    @Test
    void testCustomHost() {
        RedisConfig config = RedisConfig.builder()
            .host("redis.example.com")
            .build();

        assertEquals("redis.example.com", config.getHost());
    }

    @Test
    void testCustomPort() {
        RedisConfig config = RedisConfig.builder()
            .port(6380)
            .build();

        assertEquals(6380, config.getPort());
    }

    @Test
    void testInvalidPort_TooLow() {
        assertThrows(IllegalArgumentException.class, () ->
            RedisConfig.builder().port(0).build()
        );
    }

    @Test
    void testInvalidPort_TooHigh() {
        assertThrows(IllegalArgumentException.class, () ->
            RedisConfig.builder().port(65536).build()
        );
    }

    @Test
    void testCustomLeaseTtl() {
        RedisConfig config = RedisConfig.builder()
            .leaseTtl(30)
            .build();

        assertEquals(30, config.getLeaseTtl());
    }

    @Test
    void testInvalidLeaseTtl() {
        assertThrows(IllegalArgumentException.class, () ->
            RedisConfig.builder().leaseTtl(4).build()
        );
    }

    @Test
    void testPassword() {
        RedisConfig config = RedisConfig.builder()
            .password("secret")
            .build();

        assertEquals("secret", config.getPassword());
    }

    @Test
    void testDatabase() {
        RedisConfig config = RedisConfig.builder()
            .database(5)
            .build();

        assertEquals(5, config.getDatabase());
    }

    @Test
    void testInvalidDatabase() {
        assertThrows(IllegalArgumentException.class, () ->
            RedisConfig.builder().database(-1).build()
        );
    }

    @Test
    void testTimeout() {
        RedisConfig config = RedisConfig.builder()
            .timeout(5000)
            .build();

        assertEquals(5000, config.getTimeout());
    }

    @Test
    void testInvalidTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
            RedisConfig.builder().timeout(0).build()
        );
    }

    @Test
    void testBuilderChaining() {
        RedisConfig config = RedisConfig.builder()
            .host("redis.example.com")
            .port(6380)
            .leaseTtl(15)
            .password("secret")
            .database(1)
            .timeout(3000)
            .build();

        assertEquals("redis.example.com", config.getHost());
        assertEquals(6380, config.getPort());
        assertEquals(15, config.getLeaseTtl());
        assertEquals("secret", config.getPassword());
        assertEquals(1, config.getDatabase());
        assertEquals(3000, config.getTimeout());
    }
}
