package org.flossware.jplatform.messaging.jms;

import jakarta.jms.*;
import org.flossware.jplatform.api.Message;
import org.flossware.jplatform.api.MessageHandler;
import org.flossware.jplatform.api.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JmsMessageBus.
 */
class JmsMessageBusTest {

    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session publishSession;
    private Session subscribeSession;
    private MessageProducer producer;
    private MessageConsumer consumer;
    private Topic topic;
    private BytesMessage bytesMessage;

    private JmsConfig config;
    private JmsMessageBus messageBus;

    @BeforeEach
    void setUp() throws JMSException {
        // Initialize mocks
        connectionFactory = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        publishSession = mock(Session.class);
        subscribeSession = mock(Session.class);
        producer = mock(MessageProducer.class);
        consumer = mock(MessageConsumer.class);
        topic = mock(Topic.class);
        bytesMessage = mock(BytesMessage.class);

        config = JmsConfig.builder()
                .brokerUrl("tcp://localhost:61616")
                .username("admin")
                .password("admin")
                .clientId("test-client")
                .build();

        // Setup connection factory mock
        when(connectionFactory.createConnection(anyString(), anyString())).thenReturn(connection);
        when(connection.createSession(anyBoolean(), anyInt())).thenReturn(publishSession, subscribeSession);
    }

    @AfterEach
    void tearDown() throws JMSException {
        if (messageBus != null && !messageBus.isClosed()) {
            messageBus.close();
        }
    }

    @Test
    void testConstructor_withAuthentication() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        verify(connectionFactory).createConnection("admin", "admin");
        verify(connection).setClientID("test-client");
        verify(connection).start();
        assertFalse(messageBus.isClosed());
    }

    @Test
    void testConstructor_withoutAuthentication() throws JMSException {
        JmsConfig noAuthConfig = JmsConfig.builder()
                .brokerUrl("tcp://localhost:61616")
                .build();

        when(connectionFactory.createConnection()).thenReturn(connection);

        messageBus = new JmsMessageBus(noAuthConfig, connectionFactory);

        verify(connectionFactory).createConnection();
        verify(connection, never()).setClientID(anyString());
        verify(connection).start();
    }

    @Test
    void testConstructor_nullConfig() {
        assertThrows(NullPointerException.class,
                () -> new JmsMessageBus(null, connectionFactory));
    }

    @Test
    void testConstructor_nullConnectionFactory() {
        assertThrows(NullPointerException.class,
                () -> new JmsMessageBus(config, null));
    }

    @Test
    void testConstructor_connectionFailure() throws JMSException {
        when(connectionFactory.createConnection(anyString(), anyString()))
                .thenThrow(new JMSException("Connection failed"));

        assertThrows(JMSException.class,
                () -> new JmsMessageBus(config, connectionFactory));
    }

    @Test
    void testPublish_success() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(publishSession.createTopic("test-topic")).thenReturn(topic);
        when(publishSession.createProducer(topic)).thenReturn(producer);
        when(publishSession.createBytesMessage()).thenReturn(bytesMessage);

        Message message = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("app1")
                .payload("Hello".getBytes())
                .header("key1", "value1")
                .build();

        messageBus.publish("test-topic", message);

        verify(publishSession).createTopic("test-topic");
        verify(publishSession).createProducer(topic);
        verify(publishSession).createBytesMessage();
        verify(bytesMessage).setJMSMessageID(message.getId());
        verify(bytesMessage).setJMSTimestamp(message.getTimestamp());
        verify(bytesMessage).setStringProperty("sourceApplicationId", "app1");
        verify(bytesMessage).writeBytes("Hello".getBytes());
        verify(producer).send(bytesMessage);
        verify(producer).close();
    }

    @Test
    void testPublish_nullTopic() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        Message message = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("test".getBytes())
                .build();

        assertThrows(NullPointerException.class,
                () -> messageBus.publish(null, message));
    }

    @Test
    void testPublish_nullMessage() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        assertThrows(NullPointerException.class,
                () -> messageBus.publish("test-topic", null));
    }

    @Test
    void testPublish_whenClosed() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);
        messageBus.close();

        Message message = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("test".getBytes())
                .build();

        assertThrows(java.lang.IllegalStateException.class,
                () -> messageBus.publish("test-topic", message));
    }

    @Test
    void testPublish_jmsException() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(publishSession.createTopic("test-topic")).thenThrow(new JMSException("Failed"));

        Message message = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload("test".getBytes())
                .build();

        assertThrows(RuntimeException.class,
                () -> messageBus.publish("test-topic", message));
    }

    @Test
    void testPublish_withNullPayload() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(publishSession.createTopic("test-topic")).thenReturn(topic);
        when(publishSession.createProducer(topic)).thenReturn(producer);
        when(publishSession.createBytesMessage()).thenReturn(bytesMessage);

        Message message = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .payload(null)
                .build();

        messageBus.publish("test-topic", message);

        verify(bytesMessage, never()).writeBytes(any());
        verify(producer).send(bytesMessage);
    }

    @Test
    void testPublish_withHeaders() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(publishSession.createTopic("test-topic")).thenReturn(topic);
        when(publishSession.createProducer(topic)).thenReturn(producer);
        when(publishSession.createBytesMessage()).thenReturn(bytesMessage);

        Map<String, Object> headers = new HashMap<>();
        headers.put("priority", "high");
        headers.put("retryCount", 3);

        Message message = Message.builder()
                .topic("test-topic")
                .sourceApplicationId("test-app")
                .headers(headers)
                .payload("test".getBytes())
                .build();

        messageBus.publish("test-topic", message);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(bytesMessage).setObjectProperty(eq("platformHeaders"), captor.capture());
        assertNotNull(captor.getValue());
        assertTrue(captor.getValue().length > 0);
    }

    @Test
    void testSubscribe_success() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(subscribeSession.createTopic("test-topic")).thenReturn(topic);
        when(subscribeSession.createConsumer(topic)).thenReturn(consumer);

        MessageHandler handler = message -> { /* no-op */ };

        Subscription subscription = messageBus.subscribe("test-topic", handler);

        assertNotNull(subscription);
        assertEquals("test-topic", subscription.getTopic());
        assertTrue(subscription.isActive());

        verify(subscribeSession).createTopic("test-topic");
        verify(subscribeSession).createConsumer(topic);
        verify(consumer).setMessageListener(any(MessageListener.class));
    }

    @Test
    void testSubscribe_nullTopic() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        MessageHandler handler = message -> { };

        assertThrows(NullPointerException.class,
                () -> messageBus.subscribe(null, handler));
    }

    @Test
    void testSubscribe_nullHandler() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        assertThrows(NullPointerException.class,
                () -> messageBus.subscribe("test-topic", null));
    }

    @Test
    void testSubscribe_whenClosed() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);
        messageBus.close();

        MessageHandler handler = message -> { };

        assertThrows(java.lang.IllegalStateException.class,
                () -> messageBus.subscribe("test-topic", handler));
    }

    @Test
    void testSubscribe_jmsException() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(subscribeSession.createTopic("test-topic")).thenThrow(new JMSException("Failed"));

        MessageHandler handler = message -> { };

        assertThrows(RuntimeException.class,
                () -> messageBus.subscribe("test-topic", handler));
    }

    @Test
    void testSubscribe_messageReceived() throws JMSException, InterruptedException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(subscribeSession.createTopic("test-topic")).thenReturn(topic);
        when(subscribeSession.createConsumer(topic)).thenReturn(consumer);

        // Setup mock JMS message
        when(bytesMessage.getJMSMessageID()).thenReturn("msg-123");
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        when(bytesMessage.getStringProperty("sourceApplicationId")).thenReturn("app1");
        when(bytesMessage.getBodyLength()).thenReturn(5L);
        doAnswer(invocation -> {
            byte[] dest = invocation.getArgument(0);
            byte[] src = "Hello".getBytes();
            System.arraycopy(src, 0, dest, 0, src.length);
            return src.length;
        }).when(bytesMessage).readBytes(any(byte[].class));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> receivedMessage = new AtomicReference<>();

        MessageHandler handler = message -> {
            receivedMessage.set(message);
            latch.countDown();
        };

        // Capture the MessageListener
        ArgumentCaptor<MessageListener> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);

        messageBus.subscribe("test-topic", handler);

        verify(consumer).setMessageListener(listenerCaptor.capture());

        // Simulate message reception
        MessageListener listener = listenerCaptor.getValue();
        listener.onMessage(bytesMessage);

        // Wait for message handling
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        Message msg = receivedMessage.get();
        assertNotNull(msg);
        assertEquals("msg-123", msg.getId());
        assertEquals("test-topic", msg.getTopic());
        assertEquals("app1", msg.getSourceApplicationId());
        assertArrayEquals("Hello".getBytes(), msg.getPayload());
    }

    @Test
    void testUnsubscribe_success() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(subscribeSession.createTopic("test-topic")).thenReturn(topic);
        when(subscribeSession.createConsumer(topic)).thenReturn(consumer);

        MessageHandler handler = message -> { };
        Subscription subscription = messageBus.subscribe("test-topic", handler);

        assertTrue(subscription.isActive());

        messageBus.unsubscribe(subscription);

        assertFalse(subscription.isActive());
        verify(consumer).close();
        verify(subscribeSession).close();
    }

    @Test
    void testSubscriptionCancel_success() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(subscribeSession.createTopic("test-topic")).thenReturn(topic);
        when(subscribeSession.createConsumer(topic)).thenReturn(consumer);

        MessageHandler handler = message -> { };
        Subscription subscription = messageBus.subscribe("test-topic", handler);

        subscription.cancel();

        assertFalse(subscription.isActive());
        verify(consumer).close();
        verify(subscribeSession).close();
    }

    @Test
    void testSubscriptionCancel_idempotent() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(subscribeSession.createTopic("test-topic")).thenReturn(topic);
        when(subscribeSession.createConsumer(topic)).thenReturn(consumer);

        MessageHandler handler = message -> { };
        Subscription subscription = messageBus.subscribe("test-topic", handler);

        subscription.cancel();
        subscription.cancel(); // Second cancel should be no-op

        verify(consumer, times(1)).close();
        verify(subscribeSession, times(1)).close();
    }

    @Test
    void testClose_success() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(subscribeSession.createTopic("test-topic")).thenReturn(topic);
        when(subscribeSession.createConsumer(topic)).thenReturn(consumer);

        // Create subscription
        messageBus.subscribe("test-topic", message -> { });

        messageBus.close();

        assertTrue(messageBus.isClosed());
        verify(consumer).close();
        verify(subscribeSession).close();
        verify(publishSession).close();
        verify(connection).close();
    }

    @Test
    void testClose_idempotent() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        messageBus.close();
        messageBus.close(); // Second close should be no-op

        verify(publishSession, times(1)).close();
        verify(connection, times(1)).close();
    }

    @Test
    void testClose_withExceptions() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(subscribeSession.createTopic("test-topic")).thenReturn(topic);
        when(subscribeSession.createConsumer(topic)).thenReturn(consumer);

        messageBus.subscribe("test-topic", message -> { });

        // Make close operations throw exceptions
        doThrow(new JMSException("Consumer close failed")).when(consumer).close();
        doThrow(new JMSException("Session close failed")).when(subscribeSession).close();
        doThrow(new JMSException("Publish session close failed")).when(publishSession).close();
        doThrow(new JMSException("Connection close failed")).when(connection).close();

        // Should not throw, just log warnings
        assertDoesNotThrow(() -> messageBus.close());
        assertTrue(messageBus.isClosed());
    }

    @Test
    void testMultipleSubscriptions_sameTopic() throws JMSException {
        Session session2 = mock(Session.class);
        MessageConsumer consumer2 = mock(MessageConsumer.class);

        when(subscribeSession.createTopic("test-topic")).thenReturn(topic);
        when(subscribeSession.createConsumer(topic)).thenReturn(consumer);

        when(session2.createTopic("test-topic")).thenReturn(topic);
        when(session2.createConsumer(topic)).thenReturn(consumer2);

        // Reset mock to return all three sessions in order
        when(connection.createSession(anyBoolean(), anyInt()))
                .thenReturn(publishSession, subscribeSession, session2);

        // Now create the message bus (will consume publishSession)
        messageBus = new JmsMessageBus(config, connectionFactory);

        MessageHandler handler1 = message -> { };
        MessageHandler handler2 = message -> { };

        // First subscribe will get subscribeSession, second will get session2
        Subscription sub1 = messageBus.subscribe("test-topic", handler1);
        Subscription sub2 = messageBus.subscribe("test-topic", handler2);

        assertTrue(sub1.isActive());
        assertTrue(sub2.isActive());

        sub1.cancel();

        assertFalse(sub1.isActive());
        assertTrue(sub2.isActive());
    }

    @Test
    void testIsClosed_initiallyFalse() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        assertFalse(messageBus.isClosed());
    }

    @Test
    void testIsClosed_afterClose() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        messageBus.close();

        assertTrue(messageBus.isClosed());
    }

    @Test
    void testMessageHandling_errorInHandler() throws JMSException, InterruptedException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(subscribeSession.createTopic("test-topic")).thenReturn(topic);
        when(subscribeSession.createConsumer(topic)).thenReturn(consumer);

        when(bytesMessage.getJMSMessageID()).thenReturn("msg-123");
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        when(bytesMessage.getStringProperty("sourceApplicationId")).thenReturn("test-app");
        when(bytesMessage.getBodyLength()).thenReturn(0L);

        CountDownLatch latch = new CountDownLatch(1);

        MessageHandler handler = message -> {
            latch.countDown();
            throw new RuntimeException("Handler error");
        };

        ArgumentCaptor<MessageListener> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);

        messageBus.subscribe("test-topic", handler);

        verify(consumer).setMessageListener(listenerCaptor.capture());

        // Should not throw, error should be caught and logged
        assertDoesNotThrow(() -> listenerCaptor.getValue().onMessage(bytesMessage));

        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    void testMessageHandling_inactiveSubscription() throws JMSException {
        messageBus = new JmsMessageBus(config, connectionFactory);

        when(subscribeSession.createTopic("test-topic")).thenReturn(topic);
        when(subscribeSession.createConsumer(topic)).thenReturn(consumer);

        CountDownLatch latch = new CountDownLatch(1);

        MessageHandler handler = message -> {
            latch.countDown();
        };

        ArgumentCaptor<MessageListener> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);

        Subscription subscription = messageBus.subscribe("test-topic", handler);

        verify(consumer).setMessageListener(listenerCaptor.capture());

        // Cancel subscription before message arrives
        subscription.cancel();

        // Message should not be delivered to handler
        listenerCaptor.getValue().onMessage(bytesMessage);

        assertEquals(1, latch.getCount()); // Handler should not have been called
    }
}
