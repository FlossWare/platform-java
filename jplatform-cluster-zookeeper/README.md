# JPlatform Cluster - ZooKeeper

ZooKeeper-based clustering implementation for JPlatform using Apache Curator for distributed coordination.

## Features

- **Leader Election**: Curator LeaderSelector with ephemeral sequential znodes
- **Membership Tracking**: Ephemeral znodes for tracking active cluster members
- **State Storage**: Persistent znodes for distributed state with JSON serialization
- **Automatic Retry**: Exponential backoff retry policy for transient failures
- **Thread Safety**: All operations are thread-safe
- **Session Management**: Automatic session renewal via Curator
- **Event Notifications**: ClusterEventListener support for leadership changes

## Maven Dependency

```xml
<dependency>
    <groupId>org.flossware.jplatform</groupId>
    <artifactId>jplatform-cluster-zookeeper</artifactId>
    <version>1.1</version>
</dependency>
```

## Quick Start

### Basic Usage

```java
// Configure ZooKeeper connection
ZookeeperConfig config = ZookeeperConfig.builder()
    .connectionString("localhost:2181")
    .sessionTimeoutMs(30000)
    .build();

// Create cluster manager
ZookeeperClusterManager clusterManager = new ZookeeperClusterManager(config);

// Join cluster
ClusterConfig clusterConfig = ClusterConfig.builder()
    .clusterName("my-cluster")
    .bindAddress("192.168.1.100")
    .bindPort(8080)
    .build();
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

### ZooKeeper Ensemble

```java
ZookeeperConfig config = ZookeeperConfig.builder()
    .connectionString("zk1:2181,zk2:2181,zk3:2181")
    .sessionTimeoutMs(30000)
    .connectionTimeoutMs(15000)
    .baseSleepTimeMs(1000)
    .maxRetries(3)
    .namespace("jplatform")
    .build();
```

## Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| connectionString | String | localhost:2181 | ZooKeeper server addresses (comma-separated) |
| sessionTimeoutMs | int | 30000 | Session timeout in milliseconds |
| connectionTimeoutMs | int | 15000 | Connection timeout in milliseconds |
| baseSleepTimeMs | int | 1000 | Base sleep time for retry policy |
| maxRetries | int | 3 | Maximum number of retry attempts |
| namespace | String | null | Namespace prefix for all znodes (optional) |

## ZooKeeper Setup

### Local Development

```bash
# Using Docker
docker run -d -p 2181:2181 zookeeper:3.8
```

### Production Ensemble

```bash
# Configure /etc/zookeeper/conf/zoo.cfg on each server
tickTime=2000
initLimit=10
syncLimit=5
dataDir=/var/lib/zookeeper
clientPort=2181
server.1=zk1:2888:3888
server.2=zk2:2888:3888
server.3=zk3:2888:3888
```

## Architecture

### Leader Election

Uses Curator's LeaderSelector recipe with ephemeral sequential znodes:

```
Leader Path: /jplatform/leader/{clusterName}
```

### Membership Tracking

Ephemeral znodes for each member:

```
Member Path: /jplatform/members/{clusterName}/{nodeId}
```

### State Storage

Persistent znodes with JSON serialization:

```
States:      /jplatform/states/{appId}
Descriptors: /jplatform/descriptors/{appId}
```

## Comparison with Other Backends

| Feature | ZooKeeper | Consul | etcd | Redis |
|---------|-----------|--------|------|-------|
| Setup Complexity | Medium | Medium | Medium | Low |
| Leader Election | Ephemeral Nodes | Sessions | Leases | SETNX |
| Maturity | Very High | High | High | Medium |
| Best For | Enterprise, Hadoop ecosystem | Service mesh | Kubernetes | Fast KV |

## Testing

```bash
mvn test -pl jplatform-cluster-zookeeper
```

## License

This module is part of JPlatform and uses the same license as the parent project.

## See Also

- [jplatform-cluster](../jplatform-cluster) - Hazelcast-based clustering
- [jplatform-cluster-consul](../jplatform-cluster-consul) - Consul-based clustering
- [jplatform-cluster-etcd](../jplatform-cluster-etcd) - etcd-based clustering
- [jplatform-cluster-redis](../jplatform-cluster-redis) - Redis-based clustering
- [Apache Curator Documentation](https://curator.apache.org/)
- [ZooKeeper Documentation](https://zookeeper.apache.org/doc/current/)
