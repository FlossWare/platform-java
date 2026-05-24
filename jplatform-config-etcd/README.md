# JPlatform Config - Etcd

Etcd-based configuration source implementation for JPlatform.

## Features

- **Configuration Storage**: Store and retrieve configuration from etcd
- **Dynamic Updates**: Watch for configuration changes in real-time
- **Hierarchical Keys**: Support for hierarchical configuration structure
- **Cluster Support**: Connect to multiple etcd endpoints
- **Authentication**: Optional username/password authentication
- **Thread-Safe**: Concurrent access to configuration

## Dependencies

```xml
<dependency>
    <groupId>org.flossware.jplatform</groupId>
    <artifactId>jplatform-config-etcd</artifactId>
    <version>1.1</version>
</dependency>
```

## Usage

### Basic Configuration

```java
EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder()
    .endpoints("http://localhost:2379")
    .keyPrefix("/config/myapp")
    .build();

try (EtcdConfigSource configSource = new EtcdConfigSource(config)) {
    configSource.start();
    
    // Set configuration
    configSource.setConfig("database.host", "localhost");
    configSource.setConfig("database.port", "5432");
    
    // Get configuration
    String dbHost = configSource.getConfig("database.host");
    
    // Load all configuration
    Map<String, String> allConfig = configSource.loadConfig();
}
```

### Clustered Etcd

```java
EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder()
    .endpoints("http://etcd1:2379,http://etcd2:2379,http://etcd3:2379")
    .keyPrefix("/config/myapp")
    .build();

try (EtcdConfigSource configSource = new EtcdConfigSource(config)) {
    configSource.start();
    // Use configuration...
}
```

### With Authentication

```java
EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder()
    .endpoints("http://etcd:2379")
    .username("admin")
    .password("secret")
    .keyPrefix("/config/myapp")
    .build();

try (EtcdConfigSource configSource = new EtcdConfigSource(config)) {
    configSource.start();
    // Use configuration...
}
```

### Configuration Change Listeners

```java
EtcdConfigSourceConfig config = EtcdConfigSourceConfig.builder()
    .endpoints("http://localhost:2379")
    .keyPrefix("/config/myapp")
    .watchEnabled(true)
    .build();

try (EtcdConfigSource configSource = new EtcdConfigSource(config)) {
    configSource.start();
    
    // Register listener for configuration changes
    configSource.addListener(updatedConfig -> {
        System.out.println("Configuration updated:");
        updatedConfig.forEach((key, value) -> 
            System.out.println(key + " = " + value));
    });
    
    // Configuration changes will trigger the listener
}
```

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| endpoints | http://localhost:2379 | Comma-separated list of etcd endpoints |
| keyPrefix | /config | Prefix for all configuration keys |
| username | null | Username for authentication (optional) |
| password | null | Password for authentication (optional) |
| watchEnabled | true | Enable real-time configuration watching |
| watchRetryDelaySeconds | 5 | Delay before retrying failed watch connections |

## Thread Safety

All operations are thread-safe. Multiple threads can safely read and write configuration concurrently.

## Error Handling

Failed operations throw `RuntimeException` with descriptive messages. Watch failures are logged and automatically retried.
