# JPlatform Messaging - JMS

JMS-backed MessageBus implementation for distributed multi-node messaging.

## Overview

`jplatform-messaging-jms` provides a JMS 3.0+ implementation of the JPlatform `MessageBus` interface, enabling applications to communicate across multiple JPlatform nodes via a JMS broker.

## Features

- **Multi-node messaging** - Applications on different JPlatform instances can exchange messages
- **Persistent messages** - Messages survive broker restarts
- **Guaranteed delivery** - JMS broker handles message durability and acknowledgment
- **Broker-agnostic** - Works with any JMS 3.0+ broker (ActiveMQ Artemis, etc.)
- **Topic-based pub/sub** - Standard MessageBus semantics over JMS topics
- **Configurable** - Flexible connection and session configuration

## Usage

### Maven Dependency

```xml
<dependency>
    <groupId>org.flossware.jplatform</groupId>
    <artifactId>jplatform-messaging-jms</artifactId>
    <version>1.1</version>
</dependency>
```

### Basic Example

```java
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.flossware.jplatform.api.Message;
import org.flossware.jplatform.api.Subscription;
import org.flossware.jplatform.messaging.jms.JmsConfig;
import org.flossware.jplatform.messaging.jms.JmsMessageBus;

// 1. Configure JMS connection
JmsConfig config = JmsConfig.builder()
    .brokerUrl("tcp://localhost:61616")
    .username("admin")
    .password("admin")
    .clientId("jplatform-node-1")
    .build();

// 2. Create connection factory (Artemis example)
ConnectionFactory factory = new ActiveMQConnectionFactory(config.getBrokerUrl());

// 3. Create JMS message bus
JmsMessageBus messageBus = new JmsMessageBus(config, factory);

// 4. Subscribe to messages
Subscription subscription = messageBus.subscribe("platform.events", message -> {
    String payload = new String(message.getPayload());
    System.out.println("Received: " + payload);
    System.out.println("From: " + message.getSourceApplicationId());
});

// 5. Publish messages
Message message = Message.builder()
    .topic("platform.events")
    .sourceApplicationId("app1")
    .payload("Hello from node 1".getBytes())
    .header("priority", "high")
    .build();

messageBus.publish("platform.events", message);

// 6. Cleanup
subscription.cancel();
messageBus.close();
```

## Configuration

### JmsConfig Options

| Option | Type | Required | Default | Description |
|--------|------|----------|---------|-------------|
| `brokerUrl` | String | Yes | - | JMS broker connection URL (e.g., "tcp://localhost:61616") |
| `username` | String | No | null | Username for broker authentication |
| `password` | String | No | null | Password for broker authentication |
| `clientId` | String | No | null | JMS client identifier for durable subscriptions |
| `acknowledgeMode` | int | No | 1 | Session acknowledgment mode (AUTO_ACKNOWLEDGE=1, CLIENT_ACKNOWLEDGE=2, etc.) |
| `transacted` | boolean | No | false | Whether to use transacted sessions |

### Configuration Example

```java
JmsConfig config = JmsConfig.builder()
    .brokerUrl("tcp://broker.example.com:61616")
    .username("jplatform")
    .password("secret")
    .clientId("jplatform-prod-node-1")
    .acknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
    .transacted(false)
    .build();
```

## Message Format

Platform `Message` objects are converted to JMS `BytesMessage` with the following mapping:

| Platform Field | JMS Field | Description |
|----------------|-----------|-------------|
| `id` | `JMSMessageID` | Message identifier |
| `timestamp` | `JMSTimestamp` | Message timestamp |
| `sourceApplicationId` | Property: `sourceApplicationId` | Source application ID |
| `headers` | Property: `platformHeaders` | Serialized headers map |
| `payload` | Message body | Binary message content |
| `topic` | Destination | JMS topic name |

## Multi-Node Deployment

### Scenario: 3-Node Cluster

```
Node 1 (192.168.1.10)          JMS Broker              Node 2 (192.168.1.11)
┌──────────────────┐         (192.168.1.5)           ┌──────────────────┐
│   App A          │               │                 │   App B          │
│   App B (copy)   │───── tcp ────┤───── tcp ───────│   App C          │
│                  │               │                 │                  │
└──────────────────┘               │                 └──────────────────┘
                                   │
                           Node 3 (192.168.1.12)
                          ┌──────────────────┐
                          │   App A (copy)   │
                          │   App C (copy)   │
                          │                  │
                          └──────────────────┘
```

All applications subscribe to the same topics via the JMS broker. When App A on Node 1 publishes a message, all App A and App B instances across all nodes receive it (if subscribed to that topic).

### Configuration for Each Node

**All nodes use the same broker URL:**

```java
JmsConfig config = JmsConfig.builder()
    .brokerUrl("tcp://192.168.1.5:61616")
    .username("admin")
    .password("admin")
    .clientId("jplatform-node-" + nodeId)  // Unique per node
    .build();
```

## Comparison: InMemoryMessageBus vs JmsMessageBus

| Feature | InMemoryMessageBus | JmsMessageBus |
|---------|-------------------|---------------|
| **Multi-node** | ❌ Single node only | ✅ Multiple nodes |
| **Persistence** | ❌ Messages lost on restart | ✅ Broker persists messages |
| **Dependencies** | ✅ None | ❌ Requires JMS broker |
| **Setup** | ✅ Zero configuration | ❌ Broker installation needed |
| **Performance** | ✅ Faster (in-memory) | ❌ Slower (network + broker) |
| **Use Case** | Development, single-node | Production, distributed |

## JMS Broker Setup

### ActiveMQ Artemis (Recommended)

1. **Download and Install:**
   ```bash
   wget https://archive.apache.org/dist/activemq/activemq-artemis/2.31.2/apache-artemis-2.31.2-bin.tar.gz
   tar -xzf apache-artemis-2.31.2-bin.tar.gz
   cd apache-artemis-2.31.2
   ```

2. **Create Broker Instance:**
   ```bash
   bin/artemis create mybroker --user admin --password admin --allow-anonymous
   ```

3. **Start Broker:**
   ```bash
   cd mybroker
   bin/artemis run
   ```

4. **Verify:**
   - Web Console: http://localhost:8161/console
   - Acceptor URL: tcp://localhost:61616

### Docker Deployment

```bash
docker run -d \
  --name artemis \
  -p 8161:8161 \
  -p 61616:61616 \
  -e ARTEMIS_USERNAME=admin \
  -e ARTEMIS_PASSWORD=admin \
  apache/activemq-artemis:2.31.2
```

## Testing

The module includes comprehensive unit tests (38 tests, 77%+ coverage):

```bash
mvn test -pl jplatform-messaging-jms
```

### Integration Testing

For integration tests with an embedded broker:

```java
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

// Start embedded broker
EmbeddedActiveMQ embedded = new EmbeddedActiveMQ();
embedded.setConfiguration(new ConfigurationImpl()
    .setPersistenceEnabled(false)
    .setSecurityEnabled(false)
    .addAcceptorConfiguration("tcp", "tcp://localhost:61616"));
embedded.start();

// Use in tests...

// Cleanup
embedded.stop();
```

## Thread Safety

- **JmsMessageBus**: Thread-safe. Uses JMS thread-safe operations and ConcurrentHashMap for subscriptions.
- **JmsConfig**: Immutable and thread-safe.
- **Subscriptions**: Thread-safe cancellation via volatile boolean flag.

## Performance Considerations

1. **Connection Pooling**: Consider using a JMS connection pool for high-throughput scenarios
2. **Session Per Subscription**: Each subscription creates its own JMS session
3. **Synchronization**: Publishing synchronizes on the publish session
4. **Network Latency**: JMS adds network overhead compared to in-memory messaging

## Error Handling

- **Connection Failures**: Throw `JMSException` during construction
- **Publish Errors**: Wrap `JMSException` in `RuntimeException`
- **Subscribe Errors**: Wrap `JMSException` in `RuntimeException`
- **Handler Errors**: Caught and logged, don't affect other subscribers
- **Close Errors**: Logged but don't throw

## License

Part of the JPlatform project. See parent project for license information.

## See Also

- [JPlatform Messaging](../jplatform-messaging/README.md) - In-memory MessageBus implementation
- [MessageBus API](../jplatform-api/src/main/java/org/flossware/jplatform/api/MessageBus.java)
- [ActiveMQ Artemis](https://activemq.apache.org/components/artemis/)
