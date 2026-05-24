package org.flossware.jplatform.config.etcd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EtcdConfigSourceConfigTest {

    @Test
    void testBuilderDefaults() {
        EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder().build();

        assertEquals("http://localhost:2379", config.getEndpoints());
        assertEquals("/config", config.getKeyPrefix());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertTrue(config.isWatchEnabled());
        assertEquals(5, config.getWatchRetryDelaySeconds());
    }

    @Test
    void testBuilderCustomValues() {
        EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder()
            .endpoints("http://etcd1:2379,http://etcd2:2379")
            .keyPrefix("/app/config")
            .username("admin")
            .password("secret")
            .watchEnabled(false)
            .watchRetryDelaySeconds(10)
            .build();

        assertEquals("http://etcd1:2379,http://etcd2:2379", config.getEndpoints());
        assertEquals("/app/config", config.getKeyPrefix());
        assertEquals("admin", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertFalse(config.isWatchEnabled());
        assertEquals(10, config.getWatchRetryDelaySeconds());
    }

    @Test
    void testBuilderMissingEndpoints() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            EtcdConfigSourceConfig.builder()
                .endpoints("")
                .build();
        });
        assertTrue(exception.getMessage().contains("Endpoints"));
    }

    @Test
    void testBuilderMissingKeyPrefix() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            EtcdConfigSourceConfig.builder()
                .keyPrefix("")
                .build();
        });
        assertTrue(exception.getMessage().contains("Key prefix"));
    }

    @Test
    void testBuilderInvalidWatchRetryDelay() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            EtcdConfigSourceConfig.builder()
                .watchRetryDelaySeconds(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("at least 1"));
    }

    @Test
    void testBuilderWithAuthentication() {
        EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder()
            .username("testuser")
            .password("testpass")
            .build();

        assertEquals("testuser", config.getUsername());
        assertEquals("testpass", config.getPassword());
    }
}
