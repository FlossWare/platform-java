# JPlatform Registry - Eureka

Netflix Eureka service discovery integration for JPlatform. Provides dynamic service registration and discovery using Eureka servers.

## Features

- Netflix Eureka service registration
- Automatic heartbeat management
- Local service caching for performance
- High availability with multiple Eureka servers
- Thread-safe implementation
- Configurable renewal and expiration intervals
- Client-only or server modes

## Maven Dependency

```xml
<dependency>
    <groupId>org.flossware.jplatform</groupId>
    <artifactId>jplatform-registry-eureka</artifactId>
    <version>1.1</version>
</dependency>
```

## Quick Start

### Basic Configuration

```java
EurekaRegistryConfig config = EurekaRegistryConfig.builder()
    .addServiceUrl("http://localhost:8761/eureka")
    .appName("my-service")
    .build();

EurekaServiceRegistry registry = new EurekaServiceRegistry(config);
registry.start();

MyService service = new MyServiceImpl();
registry.registerService(MyService.class, service);

Optional<MyService> found = registry.getService(MyService.class);
```

### High Availability Setup

```java
EurekaRegistryConfig config = EurekaRegistryConfig.builder()
    .serviceUrls(Arrays.asList(
        "http://eureka1:8761/eureka",
        "http://eureka2:8762/eureka",
        "http://eureka3:8763/eureka"
    ))
    .appName("ha-service")
    .renewalIntervalSeconds(15)
    .leaseExpirationSeconds(45)
    .build();

EurekaServiceRegistry registry = new EurekaServiceRegistry(config);
registry.start();
```

### Client-Only Mode

```java
EurekaRegistryConfig config = EurekaRegistryConfig.builder()
    .addServiceUrl("http://localhost:8761/eureka")
    .appName("client-service")
    .registerWithEureka(false)
    .fetchRegistry(true)
    .build();

EurekaServiceRegistry registry = new EurekaServiceRegistry(config);
registry.start();
```

## Configuration

### EurekaRegistryConfig Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| serviceUrls | List<String> | ["http://localhost:8761/eureka"] | Eureka server URLs |
| appName | String | jplatform-app | Application name in Eureka |
| instanceId | String | auto-generated | Unique instance identifier |
| renewalIntervalSeconds | int | 30 | Heartbeat interval in seconds |
| leaseExpirationSeconds | int | 90 | Time before Eureka removes inactive instance |
| registerWithEureka | boolean | true | Register this instance with Eureka |
| fetchRegistry | boolean | true | Fetch service registry from Eureka |

## Architecture

### Service Registration

1. Service registered locally in ConcurrentHashMap
2. Registration sent to Eureka server via REST API
3. Periodic heartbeats maintain registration
4. Deregistration on shutdown

### Heartbeat Management

- Background thread sends heartbeats at configured interval
- Default: every 30 seconds
- Eureka removes instances after 90 seconds without heartbeat
- Heartbeat interval = renewalIntervalSeconds
- Expiration = leaseExpirationSeconds

### Thread Safety

- All mutable state uses ConcurrentHashMap and CopyOnWriteArrayList
- Volatile flags for started state
- ScheduledExecutorService for background tasks
- Safe concurrent registration and lookup

## Usage Patterns

### Basic Service Operations

```java
registry.registerService(MyService.class, new MyServiceImpl());

Optional<MyService> service = registry.getService(MyService.class);

List<MyService> allServices = registry.getAllServices(MyService.class);

registry.unregisterService(MyService.class, implementation);
```

### Resource Cleanup

```java
try (EurekaServiceRegistry registry = new EurekaServiceRegistry(config)) {
    registry.start();
    // Use registry
}
```

### Custom Instance ID

```java
EurekaRegistryConfig config = EurekaRegistryConfig.builder()
    .appName("my-service")
    .instanceId("my-service-instance-1")
    .build();
```

### Multiple Service Types

```java
registry.registerService(AuthService.class, authImpl);
registry.registerService(DataService.class, dataImpl);
registry.registerService(CacheService.class, cacheImpl);

Optional<AuthService> auth = registry.getService(AuthService.class);
Optional<DataService> data = registry.getService(DataService.class);
```

## Testing

The module includes comprehensive tests with 60%+ coverage:
- Configuration validation tests
- Service registration and lookup tests
- Heartbeat management tests
- Thread safety tests
- Error handling tests

Run tests:
```bash
mvn test -pl jplatform-registry-eureka
```

## Integration with Eureka Server

This implementation uses Eureka's REST API:
- POST /apps/{appName} - Register instance
- PUT /apps/{appName}/{instanceId} - Send heartbeat
- DELETE /apps/{appName}/{instanceId} - Deregister instance

Compatible with:
- Spring Cloud Eureka Server
- Netflix Eureka standalone server

## Status

Production-ready Eureka service registry implementation.
