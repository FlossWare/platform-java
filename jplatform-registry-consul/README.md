# JPlatform Registry - Consul

Consul-based service registry implementation for distributed JPlatform deployments.

## Overview

`jplatform-registry-consul` provides a Consul-backed implementation of the `ServiceRegistry` interface, enabling service discovery across multiple JPlatform nodes.

## Features

- **Distributed service discovery** - Publish service metadata to Consul for cross-node discovery
- **Local service storage** - In-memory registry for fast local lookups
- **Health checking** - TTL-based health checks via Consul
- **Multiple implementations** - Support multiple implementations per interface
- **Thread-safe** - Concurrent access with ConcurrentHashMap

## Usage

```java
ConsulRegistryConfig config = ConsulRegistryConfig.builder()
    .consulHost("localhost")
    .consulPort(8500)
    .nodeId("node-1")
    .build();

ConsulServiceRegistry registry = new ConsulServiceRegistry(config);

// Register service
MyService impl = new MyServiceImpl();
registry.registerService(MyService.class, impl);

// Lookup service
Optional<MyService> service = registry.getService(MyService.class);

// Cleanup
registry.close();
```

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| `consulHost` | "localhost" | Consul agent hostname |
| `consulPort` | 8500 | Consul HTTP API port |
| `serviceTtl` | 30 | Service TTL in seconds |
| `nodeId` | UUID | Unique node identifier |
| `servicePrefix` | "jplatform" | Service name prefix |

## Testing

14 comprehensive unit tests with 100% core coverage:

```bash
mvn test -pl jplatform-registry-consul
```

## License

Part of the JPlatform project.
