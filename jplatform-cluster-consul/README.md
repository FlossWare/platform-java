# JPlatform Cluster - Consul

Consul-based clustering implementation for distributed JPlatform deployments.

## Overview

`jplatform-cluster-consul` provides a Consul-backed implementation of JPlatform's `ClusterManager` and `ClusterStateStore` interfaces, enabling applications to form clusters across multiple nodes using HashiCorp Consul as the coordination service.

## Features

- **Service-based membership** - Automatic node discovery via Consul service catalog
- **Leader election** - Distributed leader election using Consul sessions and KV locks
- **Health checking** - TTL-based health checks with automatic session renewal
- **State synchronization** - Distributed state storage via Consul KV for application descriptors and state
- **Event notifications** - Cluster event listeners for membership and leadership changes
- **Session management** - Automatic session creation, renewal, and cleanup
- **Graceful shutdown** - Clean resource release on node departure

## Usage

### Maven Dependency

```xml
<dependency>
    <groupId>org.flossware.jplatform</groupId>
    <artifactId>jplatform-cluster-consul</artifactId>
    <version>1.1</version>
</dependency>
```

### Basic Example

```java
import org.flossware.jplatform.api.*;
import org.flossware.jplatform.cluster.consul.*;

// 1. Configure Consul connection
ConsulConfig consulConfig = ConsulConfig.builder()
    .consulHost("localhost")
    .consulPort(8500)
    .sessionTtl(10)  // seconds
    .serviceName("jplatform-cluster")
    .build();

// 2. Configure cluster
ClusterConfig clusterConfig = ClusterConfig.builder()
    .clusterName("production-cluster")
    .bindAddress("192.168.1.10")
    .bindPort(5701)
    .build();

// 3. Create cluster manager
ConsulClusterManager cluster = new ConsulClusterManager(consulConfig);

// 4. Join the cluster
cluster.join(clusterConfig);

// 5. Check leadership
if (cluster.isLeader()) {
    System.out.println("This node is the cluster leader");
} else {
    System.out.println("This node is a follower");
}

// 6. Get cluster members
Set<ClusterNode> nodes = cluster.getNodes();
System.out.println("Cluster has " + nodes.size() + " members");

// 7. Use state store for distributed state
ConsulStateStore stateStore = new ConsulStateStore(cluster.getConsulClient());
stateStore.putApplicationState("my-app", ApplicationState.RUNNING);

// 8. Subscribe to state changes
stateStore.subscribe("my-app", (key, value) -> {
    System.out.println("Application state changed: " + value);
});

// 9. Cleanup
cluster.close();
```

## Configuration

### ConsulConfig Options

| Option | Type | Required | Default | Description |
|--------|------|----------|---------|-------------|
| `consulHost` | String | No | "localhost" | Consul agent hostname or IP address |
| `consulPort` | int | No | 8500 | Consul HTTP API port |
| `sessionTtl` | int | No | 10 | Session TTL in seconds (range: 10-86400) |
| `serviceName` | String | No | "jplatform-cluster" | Service name for cluster membership |
| `datacenter` | String | No | null | Consul datacenter (uses default if null) |
| `token` | String | No | null | Consul ACL token for authentication |

### Configuration Example

```java
ConsulConfig config = ConsulConfig.builder()
    .consulHost("consul.example.com")
    .consulPort(8500)
    .sessionTtl(30)
    .serviceName("jplatform-prod")
    .datacenter("dc1")
    .token("your-acl-token")
    .build();
```

## Architecture

### Cluster Membership

Nodes register as Consul services with unique service IDs:

```
Service ID: jplatform-cluster-<uuid>
Service Name: jplatform-cluster (configurable)
Health Check: TTL-based, renewed every 5 seconds
```

### Leader Election

Leader election uses Consul's distributed locking mechanism:

1. Each node creates a Consul session with configured TTL
2. Nodes attempt to acquire lock on key: `jplatform/leader/<cluster-name>`
3. The node that acquires the lock becomes the leader
4. If leader fails, session expires and lock is released
5. Other nodes automatically compete for leadership

### State Storage

Application state and descriptors are stored in Consul KV:

| Data Type | Consul KV Path | Format |
|-----------|---------------|--------|
| Application State | `jplatform/state/<app-id>` | Enum name (e.g., "RUNNING") |
| Application Descriptor | `jplatform/descriptor/<app-id>` | JSON-serialized descriptor |

### Session Renewal

Sessions are automatically renewed every 5 seconds to prevent expiration:
- Session TTL: configurable (default 10 seconds)
- Renewal interval: 5 seconds (hardcoded)
- Health check pass: sent with each renewal

## Multi-Node Deployment

### Scenario: 3-Node Cluster

```
Node 1 (192.168.1.10)          Consul Server           Node 2 (192.168.1.11)
┌──────────────────┐         (192.168.1.5:8500)       ┌──────────────────┐
│   App A          │               │                  │   App B          │
│   LEADER         │─── HTTP ──────┤───── HTTP ───────│   FOLLOWER       │
│                  │               │                  │                  │
└──────────────────┘               │                  └──────────────────┘
                                   │
                           Node 3 (192.168.1.12)
                          ┌──────────────────┐
                          │   App C          │
                          │   FOLLOWER       │
                          │                  │
                          └──────────────────┘
```

All nodes:
1. Register as services in Consul
2. Create sessions for leader election
3. Compete for leader lock
4. Share state via Consul KV
5. Receive notifications when membership or leadership changes

### Configuration for Each Node

**All nodes use the same Consul server:**

```java
// Node 1 (192.168.1.10)
ClusterConfig config1 = ClusterConfig.builder()
    .clusterName("production-cluster")
    .bindAddress("192.168.1.10")
    .bindPort(5701)
    .build();

// Node 2 (192.168.1.11)
ClusterConfig config2 = ClusterConfig.builder()
    .clusterName("production-cluster")
    .bindAddress("192.168.1.11")
    .bindPort(5701)
    .build();

// Node 3 (192.168.1.12)
ClusterConfig config3 = ClusterConfig.builder()
    .clusterName("production-cluster")
    .bindAddress("192.168.1.12")
    .bindPort(5701)
    .build();

// All use same ConsulConfig pointing to consul.example.com:8500
```

## Comparison: Hazelcast vs Consul Clustering

| Feature | Hazelcast | Consul |
|---------|-----------|--------|
| **Discovery** | Multicast or TCP/IP | Service catalog |
| **Leader Election** | CP subsystem locks | Session-based locks |
| **State Storage** | In-memory replicated maps | Persistent KV store |
| **Dependencies** | Hazelcast library | Consul server + client |
| **Setup** | Embedded (no server needed) | Requires Consul cluster |
| **Performance** | Very fast (in-memory) | Moderate (network + disk) |
| **Persistence** | Optional | Always persistent |
| **Use Case** | High-performance clusters | Cloud-native, multi-datacenter |

## Consul Setup

### Consul Server (Dev Mode)

For development, run Consul in dev mode:

```bash
consul agent -dev -client=0.0.0.0
```

Dev mode features:
- No persistence (in-memory only)
- Single server (not for production)
- Accessible from localhost and network
- Web UI: http://localhost:8500/ui

### Consul Server (Production)

For production, deploy a Consul cluster (3 or 5 servers):

```bash
# Server 1 (bootstrap)
consul agent -server -bootstrap-expect=3 \
  -bind=192.168.1.5 -client=0.0.0.0 \
  -data-dir=/var/consul

# Server 2
consul agent -server -join=192.168.1.5 \
  -bind=192.168.1.6 -client=0.0.0.0 \
  -data-dir=/var/consul

# Server 3
consul agent -server -join=192.168.1.5 \
  -bind=192.168.1.7 -client=0.0.0.0 \
  -data-dir=/var/consul
```

### Docker Deployment

```bash
docker run -d \
  --name=consul \
  -p 8500:8500 \
  -p 8600:8600/udp \
  hashicorp/consul:latest \
  agent -dev -client=0.0.0.0 -ui
```

### Verify Installation

```bash
# Check cluster members
consul members

# Check services
consul catalog services

# Web UI
open http://localhost:8500/ui
```

## Testing

The module includes comprehensive unit tests (42 tests, 77% coverage):

```bash
mvn test -pl jplatform-cluster-consul
```

### Test Coverage

- **ConsulConfig**: Builder pattern, validation, defaults (7 tests)
- **ConsulClusterManager**: Join, leave, leadership, membership (18 tests)
- **ConsulStateStore**: State storage, descriptors, listeners (17 tests)

### Integration Testing

For integration tests with a real Consul server:

```java
// Start Consul in Docker
docker run -d --name consul-test -p 8500:8500 hashicorp/consul agent -dev -client=0.0.0.0

// Run integration tests
ConsulConfig config = ConsulConfig.builder()
    .consulHost("localhost")
    .consulPort(8500)
    .build();

ConsulClusterManager cluster1 = new ConsulClusterManager(config);
ConsulClusterManager cluster2 = new ConsulClusterManager(config);

// Test multi-node clustering
cluster1.join(clusterConfig1);
cluster2.join(clusterConfig2);

assertTrue(cluster1.isLeader() || cluster2.isLeader());
assertEquals(2, cluster1.getNodes().size());

// Cleanup
cluster1.close();
cluster2.close();
docker stop consul-test && docker rm consul-test
```

## Thread Safety

- **ConsulClusterManager**: Thread-safe. Uses CopyOnWriteArrayList for listeners and volatile fields for state.
- **ConsulStateStore**: Thread-safe. Uses ConcurrentHashMap for listener registry.
- **ConsulConfig**: Immutable and thread-safe.
- **Session renewal**: Runs in a ScheduledExecutorService with fixed-rate scheduling.

## Performance Considerations

1. **Session Renewal**: Occurs every 5 seconds per node, generating HTTP requests to Consul
2. **Leader Election**: Lock acquisition is attempted once on join, then maintained via session
3. **State Storage**: Each put/get operation is a synchronous HTTP call to Consul
4. **Health Checks**: TTL checks are passed during session renewal (no separate API calls)
5. **Network Latency**: All operations go through Consul server, adds network overhead

## Error Handling

- **Join Failures**: Throw `ClusterJoinException` with detailed error message
- **Leave Failures**: Throw `ClusterLeaveException`, attempt graceful cleanup
- **Session Expiration**: Automatically detected and recreated during renewal
- **Service Deregistration**: Best-effort on shutdown, errors logged but don't throw
- **State Storage Errors**: Serialization failures throw `RuntimeException`
- **Listener Errors**: Caught and logged, don't affect other listeners

## Limitations

1. **Membership Watching**: Current implementation uses periodic polling, not Consul's blocking queries
2. **Automatic Failover**: Session renewal failure triggers recreation, but there's a window where state may be stale
3. **Descriptor Serialization**: Only serializes core fields, excludes SecurityConfig and ResourceConfig
4. **Single Datacenter**: Currently designed for single-datacenter deployments

## License

Part of the JPlatform project. See parent project for license information.

## See Also

- [JPlatform Cluster - Hazelcast](../jplatform-cluster/README.md) - In-memory clustering alternative
- [Consul Documentation](https://www.consul.io/docs) - Official Consul documentation
- [ClusterManager API](../jplatform-api/src/main/java/org/flossware/jplatform/api/ClusterManager.java)
- [ClusterStateStore API](../jplatform-api/src/main/java/org/flossware/jplatform/api/ClusterStateStore.java)
