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

package org.flossware.platform.api;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ApiServerConfig.
 * Tests builder validation, getters, defaults, and CORS configuration.
 */
class ApiServerConfigTest {

    @Test
    void testBuilderDefaults() {
        ApiServerConfig config = ApiServerConfig.builder().build();

        assertEquals(8080, config.getPort());
        assertEquals("0.0.0.0", config.getBindAddress());
        assertFalse(config.isEnableAuth());
        assertNull(config.getApiKey());
        assertEquals("X-API-Key", config.getApiKeyHeader());
        assertEquals(20, config.getThreadPoolSize());
        assertEquals(200, config.getMaxThreadPoolSize());
        assertTrue(config.getAllowedOrigins().isEmpty(), "Default CORS policy should deny all origins");
    }

    @Test
    void testBuilderCustomValues() {
        ApiServerConfig config = ApiServerConfig.builder()
            .port(9090)
            .bindAddress("127.0.0.1")
            .enableAuth(true)
            .apiKey("test-key-123")
            .apiKeyHeader("Authorization")
            .threadPoolSize(50)
            .maxThreadPoolSize(500)
            .build();

        assertEquals(9090, config.getPort());
        assertEquals("127.0.0.1", config.getBindAddress());
        assertTrue(config.isEnableAuth());
        assertEquals("test-key-123", config.getApiKey());
        assertEquals("Authorization", config.getApiKeyHeader());
        assertEquals(50, config.getThreadPoolSize());
        assertEquals(500, config.getMaxThreadPoolSize());
    }

    @Test
    void testPortValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().port(-1).build()
        );
    }

    @Test
    void testPortTooHigh() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().port(65536).build()
        );
    }

    @Test
    void testPortMinValid() {
        ApiServerConfig config = ApiServerConfig.builder().port(0).build();
        assertEquals(0, config.getPort());
    }

    @Test
    void testPortMaxValid() {
        ApiServerConfig config = ApiServerConfig.builder().port(65535).build();
        assertEquals(65535, config.getPort());
    }

    @Test
    void testBindAddressNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().bindAddress(null).build()
        );
    }

    @Test
    void testBindAddressEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().bindAddress("").build()
        );
    }

    @Test
    void testBindAddressWhitespace() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().bindAddress("   ").build()
        );
    }

    @Test
    void testEnableAuthWithoutApiKey() {
        assertThrows(IllegalStateException.class, () ->
            ApiServerConfig.builder().enableAuth(true).build()
        );
    }

    @Test
    void testEnableAuthWithEmptyApiKey() {
        assertThrows(IllegalStateException.class, () ->
            ApiServerConfig.builder()
                .enableAuth(true)
                .apiKey("")
                .build()
        );
    }

    @Test
    void testEnableAuthWithWhitespaceApiKey() {
        assertThrows(IllegalStateException.class, () ->
            ApiServerConfig.builder()
                .enableAuth(true)
                .apiKey("   ")
                .build()
        );
    }

    @Test
    void testEnableAuthWithValidApiKey() {
        ApiServerConfig config = ApiServerConfig.builder()
            .enableAuth(true)
            .apiKey("valid-key")
            .build();

        assertTrue(config.isEnableAuth());
        assertEquals("valid-key", config.getApiKey());
    }

    @Test
    void testApiKeyHeaderNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().apiKeyHeader(null).build()
        );
    }

    @Test
    void testApiKeyHeaderEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().apiKeyHeader("").build()
        );
    }

    @Test
    void testApiKeyHeaderWhitespace() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().apiKeyHeader("   ").build()
        );
    }

    @Test
    void testAddAllowedOrigin() {
        ApiServerConfig config = ApiServerConfig.builder()
            .addAllowedOrigin("http://localhost:3000")
            .build();

        assertTrue(config.getAllowedOrigins().contains("http://localhost:3000"));
    }

    @Test
    void testAddAllowedOriginNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().addAllowedOrigin(null).build()
        );
    }

    @Test
    void testAllowedOriginsReplacesDefaults() {
        Set<String> origins = new HashSet<>();
        origins.add("http://app1.com");
        origins.add("http://app2.com");

        ApiServerConfig config = ApiServerConfig.builder()
            .allowedOrigins(origins)
            .build();

        assertEquals(2, config.getAllowedOrigins().size());
        assertTrue(config.getAllowedOrigins().contains("http://app1.com"));
        assertTrue(config.getAllowedOrigins().contains("http://app2.com"));
        assertFalse(config.getAllowedOrigins().contains("*"));
    }

    @Test
    void testAllowedOriginsNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().allowedOrigins(null).build()
        );
    }

    @Test
    void testAllowedOriginsContainsNull() {
        Set<String> origins = new HashSet<>();
        origins.add("http://app1.com");
        origins.add(null);

        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().allowedOrigins(origins).build()
        );
    }

    @Test
    void testGetAllowedOriginsIsUnmodifiable() {
        ApiServerConfig config = ApiServerConfig.builder().build();

        assertThrows(UnsupportedOperationException.class, () ->
            config.getAllowedOrigins().add("http://hacker.com")
        );
    }

    @Test
    void testThreadPoolSizeZero() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().threadPoolSize(0).build()
        );
    }

    @Test
    void testThreadPoolSizeNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().threadPoolSize(-1).build()
        );
    }

    @Test
    void testThreadPoolSizeValid() {
        ApiServerConfig config = ApiServerConfig.builder()
            .threadPoolSize(100)
            .maxThreadPoolSize(200)
            .build();

        assertEquals(100, config.getThreadPoolSize());
    }

    @Test
    void testMaxThreadPoolSizeZero() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().maxThreadPoolSize(0).build()
        );
    }

    @Test
    void testMaxThreadPoolSizeNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            ApiServerConfig.builder().maxThreadPoolSize(-1).build()
        );
    }

    @Test
    void testMaxThreadPoolSizeLessThanThreadPoolSize() {
        assertThrows(IllegalStateException.class, () ->
            ApiServerConfig.builder()
                .threadPoolSize(100)
                .maxThreadPoolSize(50)
                .build()
        );
    }

    @Test
    void testMaxThreadPoolSizeEqualToThreadPoolSize() {
        ApiServerConfig config = ApiServerConfig.builder()
            .threadPoolSize(100)
            .maxThreadPoolSize(100)
            .build();

        assertEquals(100, config.getThreadPoolSize());
        assertEquals(100, config.getMaxThreadPoolSize());
    }

    @Test
    void testBuilderChaining() {
        ApiServerConfig config = ApiServerConfig.builder()
            .port(8443)
            .bindAddress("localhost")
            .enableAuth(true)
            .apiKey("secret")
            .apiKeyHeader("X-Auth")
            .addAllowedOrigin("http://app.com")
            .threadPoolSize(30)
            .maxThreadPoolSize(300)
            .build();

        assertEquals(8443, config.getPort());
        assertEquals("localhost", config.getBindAddress());
        assertTrue(config.isEnableAuth());
        assertEquals("secret", config.getApiKey());
        assertEquals("X-Auth", config.getApiKeyHeader());
        assertTrue(config.getAllowedOrigins().contains("http://app.com"));
        assertEquals(30, config.getThreadPoolSize());
        assertEquals(300, config.getMaxThreadPoolSize());
    }

    @Test
    void testMultipleAllowedOrigins() {
        ApiServerConfig config = ApiServerConfig.builder()
            .addAllowedOrigin("http://app1.com")
            .addAllowedOrigin("http://app2.com")
            .addAllowedOrigin("http://app3.com")
            .build();

        assertEquals(3, config.getAllowedOrigins().size());
        assertTrue(config.getAllowedOrigins().contains("http://app1.com"));
        assertTrue(config.getAllowedOrigins().contains("http://app2.com"));
        assertTrue(config.getAllowedOrigins().contains("http://app3.com"));
    }

    @Test
    void testAuthDisabledAllowsNullApiKey() {
        ApiServerConfig config = ApiServerConfig.builder()
            .enableAuth(false)
            .build();

        assertFalse(config.isEnableAuth());
        assertNull(config.getApiKey());
    }

    @Test
    void testHighThreadPoolSizes() {
        ApiServerConfig config = ApiServerConfig.builder()
            .threadPoolSize(1000)
            .maxThreadPoolSize(10000)
            .build();

        assertEquals(1000, config.getThreadPoolSize());
        assertEquals(10000, config.getMaxThreadPoolSize());
    }

    @Test
    void testIpv6BindAddress() {
        ApiServerConfig config = ApiServerConfig.builder()
            .bindAddress("::1")
            .build();

        assertEquals("::1", config.getBindAddress());
    }

    @Test
    void testDefaultCorsIsRestrictive() {
        ApiServerConfig config = ApiServerConfig.builder().build();

        assertTrue(config.getAllowedOrigins().isEmpty(),
                "Default CORS should deny all origins for security");
    }

    @Test
    void testWildcardOriginCanBeExplicitlySet() {
        ApiServerConfig config = ApiServerConfig.builder()
                .addAllowedOrigin("*")
                .build();

        assertTrue(config.getAllowedOrigins().contains("*"),
                "Wildcard can be explicitly set (though discouraged)");
    }

    @Test
    void testCustomPortRange() {
        ApiServerConfig config = ApiServerConfig.builder()
            .port(8000)
            .build();

        assertEquals(8000, config.getPort());
    }
}
