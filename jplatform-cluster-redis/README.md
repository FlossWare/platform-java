# JPlatform Cluster - Redis

Redis-based clustering implementation for JPlatform using distributed Redis primitives for leader election and membership management.

## Features

- **Leader Election**: SETNX-based leader election with automatic TTL expiration
- **Membership Tracking**: Redis hashes for tracking cluster nodes
- **State Storage**: Distributed state store using Redis hashes with JSON serialization
- **Connection Pooling**: Efficient connection management via JedisPool
- **Thread Safety**: All operations are thread-safe
- **Auto-renewal**: Periodic leader key renewal to maintain leadership
- **Event Notifications**: ClusterEventListener support for state changes

## Maven Dependency

```xml
<dependency>
    <groupId>org.flossware.jplatform</groupId>
    <artifactId>jplatform-cluster-redis</artifactId>
    <version>1.1</version>
</dependency>
```

## Quick Start

### Basic Usage

```java
// Configure Redis connection
RedisConfig config = RedisConfig.builder()
    .host("localhost")
    .port(6379)
    .leaseTtl(10)
    .build();

// Create cluster manager
RedisClusterManager clusterManager = new RedisClusterManager(config);

// Join cluster
ClusterConfig clusterConfig = new ClusterConfig("my-cluster", "192.168.1.100", 8080);
clusterManager.join(clusterConfig);

// Check leadership
if (clusterManager.isLeader()) {
    System.out.println("I am the leader!");
}

// Get cluster nodes
Set<ClusterNode> nodes = clusterManager.getNodes();

// Leave cluster when done
clusterManager.leave();
```

### With Authentication

```java
RedisConfig config = RedisConfig.builder()
    .host("redis.example.com")
    .port(6379)
    .password("secret")
    .database(1)
    .leaseTtl(15)
    .build();
```

### State Store Usage

```java
// Create state store
JedisPool pool = new JedisPool("localhost", 6379);
RedisStateStore stateStore = new RedisStateStore(pool);

// Store application state
stateStore.putApplicationState("app1", ApplicationState.RUNNING);

// Retrieve application state
ApplicationState state = stateStore.getApplicationState("app1");

// Get all states
Map<String, ApplicationState> allStates = stateStore.getAllApplicationStates();

// Subscribe to state changes
stateStore.subscribe("app1", (id, newState) -> {
    System.out.println("State changed: " + id + " -> " + newState);
});
```

### Event Listeners

```java
clusterManager.addListener(new ClusterEventListener() {
    @Override
    public void onNodeJoined(ClusterNode node) {
        System.out.println("Node joined: " + node.getId());
    }

    @Override
    public void onNodeLeft(ClusterNode node) {
        System.out.println("Node left: " + node.getId());
    }

    @Override
    public void onLeaderChanged(ClusterNode newLeader) {
        System.out.println("New leader: " + newLeader.getId());
    }
});
```

## Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| host | String | localhost | Redis server hostname |
| port | int | 6379 | Redis server port |
| leaseTtl | long | 10 | Leader lease TTL in seconds (min: 5) |
| password | String | null | Authentication password (optional) |
| database | int | 0 | Redis database number |
| timeout | int | 2000 | Connection timeout in milliseconds |

## Redis Setup

### Local Development

```bash
# Using Docker
docker run -d -p 6379:6379 redis:7-alpine

# Or with password
docker run -d -p 6379:6379 redis:7-alpine redis-server --requirepass secret
```

### Production

```bash
# Install Redis
sudo apt-get install redis-server

# Configure /etc/redis/redis.conf
bind 0.0.0.0
requirepass your-secure-password
maxmemory 256mb
maxmemory-policy allkeys-lru

# Restart
sudo systemctl restart redis
```

## Architecture

### Leader Election

The Redis cluster manager uses the SETNX (SET if Not eXists) pattern for leader election:

1. Each node attempts to set a leader key with its node ID
2. The SET command includes NX (only if not exists) and EX (expiration) flags
3. If successful, the node becomes the leader
4. Leader periodically renews the key before TTL expires
5. If leader fails, key expires and another node can claim leadership

```
Key: jplatform:leader:{clusterName}
Value: {nodeId}
TTL: {leaseTtl} seconds
```

### Membership Tracking

Member nodes are tracked in a Redis hash:

```
Key: jplatform:members:{clusterName}
Field: {nodeId}
Value: {timestamp}
TTL: 2 * {leaseTtl} seconds
```

Each node:
- Registers itself in the hash on join
- Periodically refreshes its entry
- Removes itself on graceful shutdown

### State Storage

Application state and descriptors are stored in separate Redis hashes:

```
States:      jplatform:states
Descriptors: jplatform:descriptors
```

Data is serialized to JSON using Jackson ObjectMapper.

## Thread Safety

All classes are thread-safe:
- **RedisClusterManager**: Uses volatile flags and CopyOnWriteArrayList for listeners
- **RedisStateStore**: Uses ConcurrentHashMap for listeners
- **Jedis Resources**: Auto-closeable with try-with-resources pattern

## Performance Considerations

- **Connection Pooling**: JedisPool manages connections efficiently
- **Lease Renewal**: Scheduled at `leaseTtl / 2` interval to avoid expiration
- **Local Caching**: State store can be extended with local caching if needed
- **Network Latency**: Keep Redis server close to application nodes
- **Redis Memory**: Monitor memory usage for large state stores

## Comparison with Other Backends

| Feature | Redis | Consul | etcd | Hazelcast |
|---------|-------|--------|------|-----------|
| Setup Complexity | Low | Medium | Medium | Low |
| Leader Election | SETNX | Sessions | Leases | In-process |
| Membership | Hash | Catalog | KV | Built-in |
| State Storage | Hash | KV | KV | IMap |
| External Dependency | Yes | Yes | Yes | No |
| Performance | Excellent | Good | Good | Excellent |
| Best For | Fast KV, Caching | Service mesh | Kubernetes | Embedded clustering |

## Error Handling

The Redis cluster manager handles various error scenarios:

### Connection Failures

```java
try {
    clusterManager.join(config);
} catch (ClusterJoinException e) {
    logger.error("Failed to join cluster", e);
    // Implement retry logic
}
```

### Leader Election Failures

Leader election attempts are logged at DEBUG level and do not throw exceptions. The node will continue attempting to become leader on the next scheduled run.

### State Store Errors

State store operations log errors but do not throw exceptions to prevent disrupting the application:

```java
stateStore.putApplicationState("app1", state);
// Errors logged, operation continues
```

## Testing

### Unit Tests

```bash
mvn test -pl jplatform-cluster-redis
```

### Integration Tests

For integration testing with a real Redis instance:

```java
@Test
void testRealRedis() throws Exception {
    // Start Redis via Testcontainers
    GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
    redis.start();

    RedisConfig config = RedisConfig.builder()
        .host(redis.getHost())
        .port(redis.getMappedPort(6379))
        .build();

    RedisClusterManager manager = new RedisClusterManager(config);
    ClusterConfig clusterConfig = new ClusterConfig("test", "localhost", 8080);
    
    manager.join(clusterConfig);
    assertTrue(manager.isJoined());
    
    manager.leave();
}
```

## Examples

### Multi-Node Deployment

**Node 1:**
```java
RedisConfig config = RedisConfig.builder()
    .host("redis.cluster.local")
    .build();

RedisClusterManager manager = new RedisClusterManager(config);
manager.join(new ClusterConfig("prod-cluster", "10.0.1.10", 8080));
```

**Node 2:**
```java
RedisConfig config = RedisConfig.builder()
    .host("redis.cluster.local")
    .build();

RedisClusterManager manager = new RedisClusterManager(config);
manager.join(new ClusterConfig("prod-cluster", "10.0.1.11", 8080));
```

Both nodes will coordinate via Redis. One will become leader, both will see each other as members.

### High Availability

For HA deployments:

1. **Redis Sentinel**: Use Redis Sentinel for automatic failover
2. **Redis Cluster**: Use Redis Cluster mode for sharding and HA
3. **Backup Redis**: Configure secondary Redis with replication

```java
// Using Jedis with Sentinel
Set<String> sentinels = new HashSet<>();
sentinels.add("sentinel1:26379");
sentinels.add("sentinel2:26379");

JedisSentinelPool pool = new JedisSentinelPool("mymaster", sentinels);
RedisClusterManager manager = new RedisClusterManager(config, pool);
```

## Troubleshooting

### Connection Refused

```
Check Redis is running: redis-cli ping
Check firewall rules: telnet redis.host 6379
Verify host/port in config
```

### Authentication Failed

```
Verify password in Redis config
Check password in RedisConfig.builder().password()
Test with redis-cli: redis-cli -a password ping
```

### Leader Election Not Working

```
Check TTL is sufficient (min 5 seconds)
Verify network connectivity to Redis
Check Redis logs for errors
Monitor key expiration: redis-cli TTL jplatform:leader:cluster-name
```

## License

This module is part of JPlatform and uses the same license as the parent project.

## See Also

- [jplatform-cluster](../jplatform-cluster) - Hazelcast-based clustering
- [jplatform-cluster-consul](../jplatform-cluster-consul) - Consul-based clustering
- [jplatform-cluster-etcd](../jplatform-cluster-etcd) - etcd-based clustering
- [Redis Documentation](https://redis.io/documentation)
- [Jedis GitHub](https://github.com/redis/jedis)
