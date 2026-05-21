# JPlatform - Java Application Platform

A platform for running multiple isolated Java applications within a single JVM, similar to a JEE application server but designed for any Java application.

## Overview

JPlatform allows you to run completely unrelated Java applications (web servers, batch processors, message consumers, etc.) within a single JVM, providing isolation and resource management comparable to running them in separate processes or containers.

Think of it as running multiple Java applications in separate terminal windows, but all within one JVM with comprehensive isolation and management capabilities.

## Key Features

### Isolation
- **ClassLoader Isolation**: Each application has its own isolated ClassLoader to prevent class conflicts
- **Thread Pool Isolation**: Per-application thread pools with configurable limits
- **Security Isolation**: Configurable security policies per application
- **Resource Monitoring**: Track CPU, memory, and thread usage per application

### Deployment Mechanisms
- **File System**: Drop JARs in a directory for automatic deployment
- **Programmatic API**: Java API for runtime deployment/undeployment
- **CLI**: Command-line interface for management operations
- **Configuration Files**: Define applications in YAML/JSON/XML
- **REST API**: HTTP endpoints for deployment (planned)
- **Socket-based**: Socket interface for deployment (planned)

### Optional Inter-Application Communication
- **Message Bus**: Publish/subscribe event system for loose coupling
- **Service Registry**: Register and lookup services from other applications
- **Completely Optional**: Applications can be totally oblivious to these features

## Architecture

### Module Structure

```
jplatform/
├── jplatform-api/              # Public APIs - interfaces and contracts
├── jplatform-core/             # Core platform - ApplicationManager, lifecycle
├── jplatform-classloader/      # Isolated ClassLoader implementation
├── jplatform-threadpool/       # Managed thread pool per application
├── jplatform-security/         # Security policy enforcement
├── jplatform-monitoring/       # Resource monitoring and quotas
├── jplatform-messaging/        # Optional event bus and service registry
├── jplatform-deployment/       # Deployment mechanisms
│   ├── deployment-api/         # Deployment SPI
│   ├── deployment-fs/          # File system watcher
│   ├── deployment-cli/         # CLI interface
│   └── deployment-config/      # YAML/JSON/XML config loaders
├── jplatform-launcher/         # Platform bootstrap and main entry point
└── jplatform-samples/          # Sample applications
```

### Core Components

#### ApplicationManager
Central orchestrator for application lifecycle. Maintains registry of deployed applications and coordinates isolation subsystems.

#### ApplicationContext
Container for all application-specific resources (ClassLoader, ThreadPool, SecurityPolicy, ResourceMonitor). Provides isolated execution environment.

#### IsolatedClassLoader
Parent-last ClassLoader delegation for application classes, parent-first for platform APIs. Ensures maximum isolation while allowing shared platform interfaces.

#### ManagedThreadPool
Per-application ThreadPoolExecutor with:
- Configurable core/max pool sizes
- Named threads tagged with application ID
- Graceful shutdown coordination
- Monitoring hooks

#### SecurityPolicy
Configurable permission model supporting:
- File permissions
- Socket permissions  
- Runtime permissions
- Reflection control
- Native code control

#### ResourceMonitor
Tracks per-application resource usage:
- CPU time (via ThreadMXBean)
- Heap usage (estimated via ClassLoader)
- Thread count
- I/O metrics
- Custom metrics

## Application Packaging

### JAR Structure
```
application.jar
├── META-INF/
│   ├── MANIFEST.MF
│   └── jplatform/
│       └── application.yaml
├── com/example/
│   └── MyApp.class
└── lib/
    ├── dependency1.jar
    └── dependency2.jar
```

### Application Descriptor (application.yaml)
```yaml
applicationId: my-app
name: My Application
version: 1.0
mainClass: com.example.MyApp

dependencies:
  - lib/dependency1.jar
  - lib/dependency2.jar

threadPool:
  corePoolSize: 4
  maxPoolSize: 20
  queueCapacity: 100

security:
  allowReflection: false
  filePermissions:
    - path: /tmp/my-app
      actions: read,write
  socketPermissions:
    - host: localhost
      port: 8080
      actions: connect

resources:
  maxHeapMB: 512
  maxThreads: 50

messaging:
  enabled: true

properties:
  custom.config: value
```

## Writing Applications

### Platform-Aware Application (Recommended)

Implement the `Application` interface to access platform features:

```java
package com.example;

import org.flossware.jplatform.api.*;

public class MyApp implements Application {
    
    @Override
    public void start(ApplicationContext context) throws Exception {
        System.out.println("Starting " + context.getApplicationId());
        
        // Use managed thread pool
        context.getThreadPool().submit(() -> {
            // Do work in isolated thread pool
            System.out.println("Running in: " + Thread.currentThread().getName());
        });
        
        // Optional: Use messaging
        context.getMessageBus().ifPresent(bus -> {
            bus.subscribe("events", message -> {
                System.out.println("Received: " + new String(message.getPayload()));
            });
            
            bus.publish("events", Message.builder()
                    .topic("events")
                    .sourceApplicationId(context.getApplicationId())
                    .payload("Hello from MyApp".getBytes())
                    .build());
        });
        
        // Optional: Register services
        context.getServiceRegistry().ifPresent(registry -> {
            registry.registerService(MyService.class, new MyServiceImpl());
        });
    }
    
    @Override
    public void stop() throws Exception {
        System.out.println("Stopping application");
        // Cleanup resources
    }
}
```

### Legacy Application (Plain Main Method)

Any standard Java application with a `main` method works:

```java
package com.example;

public class LegacyApp {
    public static void main(String[] args) {
        System.out.println("Legacy app running in isolation");
        // Standard Java code - runs isolated but can't access platform features
    }
}
```

## Building

```bash
# Build entire platform
mvn clean install

# Build specific module
cd jplatform-core
mvn clean install

# Run tests
mvn test
```

## Running the Platform

### Using the Launcher

```bash
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar
```

You'll see an interactive console with available commands:

```
jplatform> help

Available commands:
  deploy <appId> <jarFile> <mainClass>  - Deploy an application
  start <appId>                         - Start a deployed application
  stop <appId>                          - Stop a running application
  undeploy <appId>                      - Undeploy an application
  list                                  - List all applications
  status <appId>                        - Show application status
  exit                                  - Exit platform
  help                                  - Show this help
```

### Running Sample Applications

**Deploy and run Hello World:**
```bash
jplatform> deploy hello-world jplatform-samples/hello-world/target/sample-hello-world-1.0.jar org.flossware.jplatform.samples.helloworld.HelloWorldApp
jplatform> start hello-world
jplatform> status hello-world
jplatform> stop hello-world
```

**Deploy and run Messaging App:**
```bash
jplatform> deploy messaging-app jplatform-samples/messaging-app/target/sample-messaging-app-1.0.jar org.flossware.jplatform.samples.messaging.MessagingApp
jplatform> start messaging-app
jplatform> list
```

**Run both applications together:**
```bash
jplatform> deploy hello-world jplatform-samples/hello-world/target/sample-hello-world-1.0.jar org.flossware.jplatform.samples.helloworld.HelloWorldApp
jplatform> deploy messaging-app jplatform-samples/messaging-app/target/sample-messaging-app-1.0.jar org.flossware.jplatform.samples.messaging.MessagingApp
jplatform> start hello-world
jplatform> start messaging-app
jplatform> status hello-world
jplatform> status messaging-app
```

### Programmatic Usage

```java
ApplicationManager manager = new ApplicationManager();

ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
    .applicationId("my-app")
    .mainClass("com.example.MyApp")
    .addClasspathEntry(new File("my-app.jar").toURI())
    .threadPoolConfig(ThreadPoolConfig.builder()
        .corePoolSize(4)
        .maxPoolSize(10)
        .build())
    .enableMessaging(true)
    .build();

manager.deploy(descriptor);
manager.start("my-app");

// Check status
ApplicationContext context = manager.getApplicationContext("my-app");
System.out.println("State: " + context.getState());

// Later...
manager.stop("my-app");
manager.undeploy("my-app");
manager.shutdown();
```

## Design Decisions

### ClassLoader Hierarchy
```
Bootstrap ClassLoader
  └── Platform ClassLoader
      └── System ClassLoader
          └── PlatformSharedClassLoader (jplatform-api)
              ├── App1ClassLoader → App1 JARs
              ├── App2ClassLoader → App2 JARs
              └── App3ClassLoader → App3 JARs
```

Platform APIs (`jplatform-api`) are loaded once in a shared ClassLoader. Application classes use parent-last delegation to achieve maximum isolation while avoiding ClassCastExceptions on shared interfaces.

### Security Model

Supports both legacy and modern Java:
- **Java 8-16**: SecurityManager with custom Policy
- **Java 17+**: Bytecode instrumentation with custom access control

Configurable at launch via `security.mode` setting.

### Resource Monitoring

- **CPU Time**: Accurate tracking via ThreadMXBean per thread
- **Heap Usage**: Approximation via ClassLoader object tracking
- **JVMTI Agent**: Optional native agent for precise heap measurement (planned)

### Thread Pool Enforcement

Applications SHOULD use the provided `ManagedThreadPool`. Direct thread creation bypasses isolation but can be detected and blocked via:
- Documentation and convention (current approach)
- SecurityManager/bytecode instrumentation (optional enforcement)

## Development Status

### ✅ Completed
- Maven multi-module structure
- Complete API definitions (`jplatform-api`)
- Core implementation (`jplatform-core`) - ApplicationManager and ApplicationContext
- ClassLoader isolation (`jplatform-classloader`) - based on FlossWare JClassLoader
- Thread pool management (`jplatform-threadpool`) - ManagedThreadPool
- Resource monitoring (`jplatform-monitoring`) - CPU, heap, thread tracking with quotas
- Security policy enforcement (`jplatform-security`) - configurable permissions
- Messaging (`jplatform-messaging`) - InMemoryMessageBus and ServiceRegistry
- Platform launcher (`jplatform-launcher`) - interactive console
- Sample applications - hello-world and messaging-app

### 📋 Planned
- Additional deployment providers (filesystem watcher, REST API, socket interface)
- Configuration file support (YAML/JSON/XML descriptors)
- Integration tests
- JVMTI agent for precise heap monitoring
- JMX/Prometheus exporters
- Web console (UI for management)
- Clustering support

## Implementation Roadmap

### Phase 1: Core Platform (MVP)
1. `jplatform-classloader` - IsolatedClassLoader implementation
2. `jplatform-threadpool` - ManagedThreadPool implementation
3. `jplatform-core` - ApplicationManager and ApplicationContext
4. `jplatform-launcher` - Basic bootstrap

### Phase 2: Deployment
5. `jplatform-deployment-api` - Deployment SPI
6. `jplatform-deployment-fs` - File system watcher
7. `jplatform-deployment-cli` - Command-line interface

### Phase 3: Isolation & Monitoring
8. `jplatform-security` - Security policy enforcement
9. `jplatform-monitoring` - Resource tracking and quotas

### Phase 4: Advanced Features
10. `jplatform-messaging` - Event bus and service registry
11. Additional deployment providers
12. Web console (UI for management)

### Phase 5: Production Readiness
13. JVMTI agent for accurate heap monitoring
14. JMX/Prometheus exporters
15. Clustering support
16. Advanced monitoring and alerting

## Version Numbering

JPlatform uses a simple **X.Y version format** without patch versions or SNAPSHOT suffixes.

- **Current version**: 1.0
- **Format**: Major.Minor (e.g., 1.0, 1.1, 2.0)
- **Consistency**: All modules share the same version number
- **No snapshots**: Every build is release quality

See [VERSION_POLICY.md](VERSION_POLICY.md) for complete versioning details.

## Contributing

This is currently in active development. Contributions welcome!

## License

See [LICENSE](LICENSE) file for details.

## Related Projects

Similar concepts:
- **OSGi**: Component framework with module system
- **JBoss Modules**: Modular classloading system
- **JEE Application Servers**: Full JEE stack with EAR/WAR deployment
- **Kubernetes**: Container orchestration (but at process level)

JPlatform focuses on simplicity and running arbitrary Java applications (not just web apps) with strong isolation guarantees within a single JVM.
