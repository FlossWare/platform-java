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

package org.flossware.jplatform.storage.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseStorageConfigTest {

    @Test
    void testBuilderDefaults() {
        DatabaseStorageConfig config = DatabaseStorageConfig.builder()
            .jdbcUrl("jdbc:h2:mem:test")
            .build();

        assertEquals("jdbc:h2:mem:test", config.getJdbcUrl());
        assertEquals("sa", config.getUsername());
        assertEquals("", config.getPassword());
        assertEquals("org.h2.Driver", config.getDriverClassName());
        assertEquals(10, config.getMaxPoolSize());
    }

    @Test
    void testBuilderWithAllFields() {
        DatabaseStorageConfig config = DatabaseStorageConfig.builder()
            .jdbcUrl("jdbc:postgresql://localhost:5432/mydb")
            .username("dbuser")
            .password("dbpass")
            .driverClassName("org.postgresql.Driver")
            .maxPoolSize(20)
            .build();

        assertEquals("jdbc:postgresql://localhost:5432/mydb", config.getJdbcUrl());
        assertEquals("dbuser", config.getUsername());
        assertEquals("dbpass", config.getPassword());
        assertEquals("org.postgresql.Driver", config.getDriverClassName());
        assertEquals(20, config.getMaxPoolSize());
    }

    @Test
    void testMissingJdbcUrl() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            DatabaseStorageConfig.builder().build();
        });
        assertTrue(exception.getMessage().contains("JDBC URL"));
    }

    @Test
    void testEmptyJdbcUrl() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            DatabaseStorageConfig.builder()
                .jdbcUrl("  ")
                .build();
        });
        assertTrue(exception.getMessage().contains("JDBC URL"));
    }

    @Test
    void testMaxPoolSizeValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DatabaseStorageConfig.builder()
                .jdbcUrl("jdbc:h2:mem:test")
                .maxPoolSize(0)
                .build();
        });
        assertTrue(exception.getMessage().contains("Max pool size"));
    }

    @Test
    void testMaxPoolSizeNegative() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            DatabaseStorageConfig.builder()
                .jdbcUrl("jdbc:h2:mem:test")
                .maxPoolSize(-5)
                .build();
        });
        assertTrue(exception.getMessage().contains("Max pool size"));
    }

    @Test
    void testBuilderChaining() {
        DatabaseStorageConfig.Builder builder = DatabaseStorageConfig.builder();
        assertSame(builder, builder.jdbcUrl("jdbc:h2:mem:test"));
        assertSame(builder, builder.username("user"));
        assertSame(builder, builder.password("pass"));
        assertSame(builder, builder.driverClassName("org.h2.Driver"));
        assertSame(builder, builder.maxPoolSize(5));
    }

    @Test
    void testH2Configuration() {
        DatabaseStorageConfig config = DatabaseStorageConfig.builder()
            .jdbcUrl("jdbc:h2:mem:volumes")
            .build();

        assertEquals("org.h2.Driver", config.getDriverClassName());
    }

    @Test
    void testPostgreSQLConfiguration() {
        DatabaseStorageConfig config = DatabaseStorageConfig.builder()
            .jdbcUrl("jdbc:postgresql://localhost/volumes")
            .username("postgres")
            .password("secret")
            .driverClassName("org.postgresql.Driver")
            .build();

        assertEquals("jdbc:postgresql://localhost/volumes", config.getJdbcUrl());
        assertEquals("postgres", config.getUsername());
        assertEquals("org.postgresql.Driver", config.getDriverClassName());
    }

    @Test
    void testMySQLConfiguration() {
        DatabaseStorageConfig config = DatabaseStorageConfig.builder()
            .jdbcUrl("jdbc:mysql://localhost:3306/volumes")
            .username("root")
            .password("password")
            .driverClassName("com.mysql.cj.jdbc.Driver")
            .maxPoolSize(15)
            .build();

        assertEquals(15, config.getMaxPoolSize());
        assertEquals("com.mysql.cj.jdbc.Driver", config.getDriverClassName());
    }
}
