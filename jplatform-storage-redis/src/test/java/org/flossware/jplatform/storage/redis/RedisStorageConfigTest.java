package org.flossware.jplatform.storage.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisStorageConfigTest {

    @Test
    void testBuilderDefaults() {
        RedisStorageConfig config = RedisStorageConfig.builder()
            .build();

        assertEquals("localhost", config.getHost());
        assertEquals(6379, config.getPort());
        assertNull(config.getPassword());
        assertEquals(0, config.getDatabase());
        assertEquals(2000, config.getConnectionTimeout());
        assertEquals(2000, config.getSocketTimeout());
        assertEquals("jplatform:volumes:", config.getKeyPrefix());
    }

    @Test
    void testBuilderWithAllFields() {
        RedisStorageConfig config = RedisStorageConfig.builder()
            .host("redis-server")
            .port(6380)
            .password("secret")
            .database(5)
            .connectionTimeout(5000)
            .socketTimeout(3000)
            .keyPrefix("myapp:")
            .build();

        assertEquals("redis-server", config.getHost());
        assertEquals(6380, config.getPort());
        assertEquals("secret", config.getPassword());
        assertEquals(5, config.getDatabase());
        assertEquals(5000, config.getConnectionTimeout());
        assertEquals(3000, config.getSocketTimeout());
        assertEquals("myapp:", config.getKeyPrefix());
    }

    @Test
    void testMissingHost() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            RedisStorageConfig.builder()
                .host(null)
                .build();
        });
        assertTrue(exception.getMessage().contains("Host"));
    }

    @Test
    void testEmptyHost() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            RedisStorageConfig.builder()
                .host("  ")
                .build();
        });
        assertTrue(exception.getMessage().contains("Host"));
    }

    @Test
    void testPortTooSmall() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            RedisStorageConfig.builder()
                .port(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("Port"));
    }

    @Test
    void testPortTooLarge() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            RedisStorageConfig.builder()
                .port(65536)
                .build();
        });
        assertTrue(exception.getMessage().contains("Port"));
    }

    @Test
    void testDatabaseNegative() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            RedisStorageConfig.builder()
                .database(-1)
                .build();
        });
        assertTrue(exception.getMessage().contains("Database"));
    }

    @Test
    void testConnectionTimeoutTooSmall() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            RedisStorageConfig.builder()
                .connectionTimeout(50)
                .build();
        });
        assertTrue(exception.getMessage().contains("Connection timeout"));
    }

    @Test
    void testSocketTimeoutTooSmall() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            RedisStorageConfig.builder()
                .socketTimeout(99)
                .build();
        });
        assertTrue(exception.getMessage().contains("Socket timeout"));
    }

    @Test
    void testBuilderChaining() {
        RedisStorageConfig.Builder builder = RedisStorageConfig.builder();
        assertSame(builder, builder.host("localhost"));
        assertSame(builder, builder.port(6379));
        assertSame(builder, builder.password("pass"));
        assertSame(builder, builder.database(1));
        assertSame(builder, builder.connectionTimeout(1000));
        assertSame(builder, builder.socketTimeout(1000));
        assertSame(builder, builder.keyPrefix("prefix:"));
    }

    @Test
    void testAuthConfiguration() {
        RedisStorageConfig config = RedisStorageConfig.builder()
            .host("redis.example.com")
            .password("secure-password")
            .build();

        assertEquals("secure-password", config.getPassword());
    }

    @Test
    void testMultipleDatabases() {
        RedisStorageConfig db0 = RedisStorageConfig.builder()
            .database(0)
            .build();

        RedisStorageConfig db1 = RedisStorageConfig.builder()
            .database(1)
            .build();

        assertEquals(0, db0.getDatabase());
        assertEquals(1, db1.getDatabase());
    }

    @Test
    void testCustomKeyPrefix() {
        RedisStorageConfig config = RedisStorageConfig.builder()
            .keyPrefix("prod:volumes:")
            .build();

        assertEquals("prod:volumes:", config.getKeyPrefix());
    }

    @Test
    void testMinimumTimeouts() {
        RedisStorageConfig config = RedisStorageConfig.builder()
            .connectionTimeout(100)
            .socketTimeout(100)
            .build();

        assertEquals(100, config.getConnectionTimeout());
        assertEquals(100, config.getSocketTimeout());
    }
}
