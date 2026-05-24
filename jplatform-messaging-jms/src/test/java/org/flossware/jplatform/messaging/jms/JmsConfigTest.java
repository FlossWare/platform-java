package org.flossware.jplatform.messaging.jms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JmsConfig.
 */
class JmsConfigTest {

    @Test
    void testBuilder_withAllFields() {
        JmsConfig config = JmsConfig.builder()
                .brokerUrl("tcp://localhost:61616")
                .username("admin")
                .password("secret")
                .clientId("test-client")
                .acknowledgeMode(2)
                .transacted(true)
                .build();

        assertEquals("tcp://localhost:61616", config.getBrokerUrl());
        assertEquals("admin", config.getUsername());
        assertEquals("secret", config.getPassword());
        assertEquals("test-client", config.getClientId());
        assertEquals(2, config.getAcknowledgeMode());
        assertTrue(config.isTransacted());
    }

    @Test
    void testBuilder_withDefaults() {
        JmsConfig config = JmsConfig.builder()
                .brokerUrl("tcp://localhost:61616")
                .build();

        assertEquals("tcp://localhost:61616", config.getBrokerUrl());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
        assertNull(config.getClientId());
        assertEquals(1, config.getAcknowledgeMode()); // AUTO_ACKNOWLEDGE
        assertFalse(config.isTransacted());
    }

    @Test
    void testBuilder_nullBrokerUrl() {
        JmsConfig.Builder builder = JmsConfig.builder();

        assertThrows(NullPointerException.class, builder::build,
                "Should throw NullPointerException when brokerUrl is null");
    }

    @Test
    void testBuilder_onlyBrokerUrl() {
        JmsConfig config = JmsConfig.builder()
                .brokerUrl("vm://localhost")
                .build();

        assertNotNull(config);
        assertEquals("vm://localhost", config.getBrokerUrl());
    }

    @Test
    void testBuilder_builderReuse() {
        JmsConfig.Builder builder = JmsConfig.builder()
                .brokerUrl("tcp://localhost:61616")
                .username("user1");

        JmsConfig config1 = builder.build();

        // Modify builder and build again
        builder.username("user2").password("pass2");
        JmsConfig config2 = builder.build();

        // Both configs should have same broker but different credentials
        assertEquals("tcp://localhost:61616", config1.getBrokerUrl());
        assertEquals("user1", config1.getUsername());
        assertNull(config1.getPassword());

        assertEquals("tcp://localhost:61616", config2.getBrokerUrl());
        assertEquals("user2", config2.getUsername());
        assertEquals("pass2", config2.getPassword());
    }

    @Test
    void testBuilder_acknowledgeModeBoundaryValues() {
        JmsConfig config1 = JmsConfig.builder()
                .brokerUrl("tcp://localhost:61616")
                .acknowledgeMode(0)
                .build();
        assertEquals(0, config1.getAcknowledgeMode());

        JmsConfig config2 = JmsConfig.builder()
                .brokerUrl("tcp://localhost:61616")
                .acknowledgeMode(Integer.MAX_VALUE)
                .build();
        assertEquals(Integer.MAX_VALUE, config2.getAcknowledgeMode());
    }

    @Test
    void testBuilder_transactedToggle() {
        JmsConfig.Builder builder = JmsConfig.builder()
                .brokerUrl("tcp://localhost:61616");

        JmsConfig config1 = builder.transacted(false).build();
        assertFalse(config1.isTransacted());

        JmsConfig config2 = builder.transacted(true).build();
        assertTrue(config2.isTransacted());
    }

    @Test
    void testBuilder_chainedCalls() {
        JmsConfig config = JmsConfig.builder()
                .brokerUrl("tcp://localhost:61616")
                .username("admin")
                .password("admin")
                .clientId("client1")
                .acknowledgeMode(3)
                .transacted(true)
                .build();

        assertAll("All fields should be set correctly",
                () -> assertEquals("tcp://localhost:61616", config.getBrokerUrl()),
                () -> assertEquals("admin", config.getUsername()),
                () -> assertEquals("admin", config.getPassword()),
                () -> assertEquals("client1", config.getClientId()),
                () -> assertEquals(3, config.getAcknowledgeMode()),
                () -> assertTrue(config.isTransacted())
        );
    }

    @Test
    void testBuilder_overwriteValues() {
        JmsConfig config = JmsConfig.builder()
                .brokerUrl("tcp://old:61616")
                .brokerUrl("tcp://new:61616")
                .username("old-user")
                .username("new-user")
                .build();

        assertEquals("tcp://new:61616", config.getBrokerUrl());
        assertEquals("new-user", config.getUsername());
    }
}
