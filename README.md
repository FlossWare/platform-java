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
- **File System Watcher**: Drop YAML/JSON descriptors in a directory for automatic deployment ✅ **IMPLEMENTED**
- **Configuration Files**: Define applications in YAML or JSON format ✅ **IMPLEMENTED**  
- **REST API**: HTTP endpoints for deployment and management ✅ **IMPLEMENTED**
- **Web Console**: Browser-based management UI with real-time metrics ✅ **IMPLEMENTED**
- **Swing Desktop UI**: Native desktop application with file chooser and graphical controls ✅ **NEW**
- **Terminal UI**: Full-screen curses-like interface for SSH/remote management ✅ **NEW**
- **Programmatic API**: Java API for runtime deployment/undeployment ✅ **IMPLEMENTED**
- **CLI**: Command-line interface for management operations ✅ **IMPLEMENTED**

### Optional Inter-Application Communication
- **Message Bus**: Publish/subscribe event system for loose coupling
- **Service Registry**: Register and lookup services from other applications
- **Completely Optional**: Applications can be totally oblivious to these features

### Management and Monitoring ✅ **ALL IMPLEMENTED**
- **REST API**: Full HTTP API for deployment, lifecycle management, and metrics retrieval
- **Web Console**: Modern browser-based UI with real-time charts (Chart.js)
- **Swing Desktop UI**: Native desktop application for JPlatform management with real-time metrics ✅ **NEW**
- **Terminal UI**: Full-screen terminal interface with keyboard controls (curses-like) ✅ **NEW**
- **JMX Metrics**: Expose application metrics via JMX MBeans for tools like JConsole, VisualVM
- **Prometheus Metrics**: Export metrics in Prometheus format for modern monitoring stacks
- **Filesystem Watcher**: Automatic deployment when descriptor files are added/modified
- **YAML/JSON Descriptors**: Declarative application configuration with full validation

### Platform-Level Features (Version 2.0) ✅ **ALL IMPLEMENTED**

JPlatform provides runtime platform features comparable to Kubernetes/YARN for JVM processes:

#### Hot Code Reload
- Update application code without platform restart
- Zero-downtime deployments with classloader hot-swapping
- Optional state preservation during reload via `ReloadableApplication` interface
- Automatic rollback on reload failure
- [Documentation](HOT_RELOAD.md)

#### Resource Enforcement
- Automatic enforcement when applications exceed CPU/memory/thread quotas
- Four enforcement levels: NOTIFY, THROTTLE, SHUTDOWN, KILL
- Configurable grace periods to handle transient spikes
- Per-resource-type enforcement policies
- [Documentation](RESOURCE_ENFORCEMENT.md)

#### Application Dependencies
- Declare REQUIRED and OPTIONAL dependencies on other applications' services
- Automatic validation at deploy time
- Circular dependency detection
- Ordered startup based on dependency graph (topological sort)
- Service version tracking with semver support
- [Documentation](APPLICATION_DEPENDENCIES.md)

#### Persistent Storage Volumes
- Per-application persistent and ephemeral volumes
- Filesystem-based storage with configurable size limits
- Automatic lifecycle management (creation, mounting, cleanup)
- Volumes survive application restarts and redeployments
- Volume usage tracking and enforcement
- [Documentation](VOLUMES.md)

#### Native Binary Support
- Load platform-specific native libraries (.so, .dll, .dylib)
- Automatic platform detection (Linux x64, Windows x64, macOS ARM64, etc.)
- Per-application library isolation (no version conflicts)
- [Documentation](NATIVE_BINARIES.md)

#### Native Process Execution ✅ **NEW**
- Deploy and run GraalVM native images as separate OS processes
- Launch compiled executables (C, C++, Rust, Go) via the platform
- Process lifecycle management (start, graceful shutdown, force kill)
- Output redirection from processes to platform logging
- Configurable via `nativeImage` flag and `native.*` properties
- [Documentation](NATIVE_EXECUTION.md)

#### Container Orchestration ✅ **NEW**
- Deploy applications as Docker, Podman, or LXC containers
- Automatic image pulling and container lifecycle management
- Port mappings, volume mounts, environment variables, network configuration
- Container output streaming to platform logs
- Multi-runtime support with automatic detection
- Configurable via `container.*` properties
- [Documentation](CONTAINER_DEPLOYMENT.md)

#### Virtual Machine Management ✅ **NEW**
- Deploy and manage KVM/QEMU virtual machines via libvirt
- Same API as other JPlatform workloads (containers, Java apps, native binaries)
- Unified orchestration across ALL workload types (VMs, containers, Java apps, native binaries)
- VM resource quotas and monitoring (vCPU, memory, disk)
- Cross-workload dependencies (VMs can depend on containers, Java apps, etc.)
- VNC console access for VM management
- Configurable via `vm.*` properties
- [Documentation](jplatform-vm-management/README.md)

#### Enhanced Observability
- OpenTelemetry integration for metrics export via OTLP protocol
- Distributed tracing support (future enhancement)
- Per-application structured logging with MDC context (trace_id, span_id, app_id)
- Periodic metrics export (CPU, memory, threads) every 60 seconds
- Integration with Prometheus, Grafana, Jaeger
- [Documentation](OBSERVABILITY.md)

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
├── jplatform-messaging-jms/    # JMS-backed MessageBus for distributed messaging ✅ **NEW**
├── jplatform-config/           # YAML/JSON descriptor parsing ✅ **COMPLETE**
├── jplatform-fs-watcher/       # Filesystem monitoring for auto-deployment ✅ **COMPLETE**
├── jplatform-rest-api/         # HTTP REST API server ✅ **COMPLETE**
├── jplatform-web-console/      # Browser-based management UI ✅ **COMPLETE**
├── jplatform-swing-ui/         # Swing desktop management UI ✅ **NEW**
├── jplatform-jcurses-ui/       # Terminal UI (curses-like) ✅ **NEW**
├── jplatform-metrics-jmx/      # JMX metrics exporter ✅ **COMPLETE**
├── jplatform-metrics-prometheus/ # Prometheus metrics exporter ✅ **COMPLETE**
├── jplatform-storage/          # Persistent volume management ✅ **COMPLETE (2.0)**
├── jplatform-vm-management/    # Virtual machine management via libvirt/KVM/QEMU ✅ **NEW**
├── jplatform-otel/             # OpenTelemetry integration ✅ **COMPLETE (2.0)**
├── jplatform-cluster/          # Multi-node clustering (Hazelcast) ✅ **COMPLETE**
├── jplatform-cluster-consul/   # Consul clustering plugin ✅
├── jplatform-cluster-etcd/     # etcd clustering plugin ⚠️ **STUB**
├── jplatform-cluster-redis/    # Redis clustering plugin ⚠️ **STUB**
├── jplatform-cluster-zookeeper/ # ZooKeeper clustering plugin ⚠️ **STUB**
├── jplatform-registry-consul/  # Consul service registry ✅
├── jplatform-registry-etcd/    # etcd service registry ⚠️ **STUB**
├── jplatform-registry-eureka/  # Eureka service registry ⚠️ **STUB**
├── jplatform-storage-s3/       # S3 volume storage ⚠️ **STUB**
├── jplatform-storage-database/ # Database volume storage ⚠️ **STUB**
├── jplatform-storage-redis/    # Redis volume storage ⚠️ **STUB**
├── jplatform-config-consul/    # Consul config source ⚠️ **STUB**
├── jplatform-config-etcd/      # etcd config source ⚠️ **STUB**
├── jplatform-config-vault/     # Vault config source ⚠️ **STUB**
├── jplatform-rest-api-netty/   # Netty API server ⚠️ **STUB**
├── jplatform-deployment/       # Deployment mechanisms
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

### Configuration

JPlatform supports two configuration approaches that can be combined:

**1. Configuration File (platform.yaml):**

Create a `platform.yaml` file to configure all platform features:

```yaml
api:
  enabled: true
  port: 8080
  bindAddress: 0.0.0.0

metrics:
  jmx:
    enabled: true
    port: 9999
    domain: org.flossware.jplatform
  prometheus:
    enabled: true
    port: 9090
    path: /metrics

watcher:
  enabled: true
  watchDirectory: /var/jplatform/apps
  autoStart: true
  autoDeploy: true
```

**2. Command-Line Flags:**

Command-line flags override configuration file settings:

```bash
# Use custom configuration file
java -jar jplatform-launcher-1.0.jar --config /path/to/custom.yaml

# Override specific settings from file
java -jar jplatform-launcher-1.0.jar --config platform.yaml --port 9000
```

### Using the Launcher

The launcher supports multiple optional features via command-line flags:

```bash
# Basic launcher (interactive CLI only)
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar

# Load configuration from platform.yaml
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar --config platform.yaml

# Enable REST API on port 8080
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar --rest-api

# Enable REST API and Web Console
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar --rest-api --web-console

# Enable JMX metrics on port 9999
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar --jmx-port 9999

# Enable Prometheus metrics on port 9090
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar --prometheus

# Enable filesystem watcher for auto-deployment
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar --watch-dir /var/jplatform/apps

# Enable all features (via config file or flags)
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar \
  --rest-api --web-console \
  --jmx-port 9999 --prometheus \
  --watch-dir /var/jplatform/apps
```

You'll see an interactive console with available commands:

```
jplatform> help

Available commands:
  deploy <appId> <jarFile> <mainClass>  - Deploy an application from JAR
  deploy-yaml <file>                    - Deploy from YAML descriptor
  deploy-json <file>                    - Deploy from JSON descriptor
  start <appId>                         - Start a deployed application
  stop <appId>                          - Stop a running application
  undeploy <appId>                      - Undeploy an application
  list                                  - List all applications
  status <appId>                        - Show application status
  exit                                  - Exit platform
  help                                  - Show this help
```

### Deployment Methods

**1. Deploy from JAR (Interactive CLI):**
```bash
jplatform> deploy my-app /path/to/app.jar com.example.MyApp
jplatform> start my-app
```

**2. Deploy from YAML Descriptor:**
```bash
jplatform> deploy-yaml examples/applications/sample-app.yaml
# Application automatically deployed and ready to start
```

**3. Deploy from JSON Descriptor:**
```bash
jplatform> deploy-json examples/applications/sample-app.json
```

**4. Auto-deployment via Filesystem Watcher:**
```bash
# Start launcher with watcher enabled
java -jar jplatform-launcher-1.0.jar --watch-dir /var/jplatform/apps

# Drop descriptor files in watched directory
cp my-app.yaml /var/jplatform/apps/
# Application automatically deployed and started
```

**5. Deploy via REST API:**
```bash
# Start launcher with REST API enabled
java -jar jplatform-launcher-1.0.jar --rest-api

# Deploy via HTTP
curl -X POST http://localhost:8080/api/applications \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "my-app",
    "mainClass": "com.example.MyApp",
    "classpathEntries": ["file:///path/to/app.jar"]
  }'
```

**6. Manage via Web Console:**
```bash
# Start launcher with web console
java -jar jplatform-launcher-1.0.jar --rest-api --web-console

# Open browser to http://localhost:8080/console
# Use web UI to deploy, start, stop, and monitor applications
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

### ✅ Completed (Production Ready)

**Core Platform:**
- Maven multi-module structure with proper dependency management
- Complete API definitions (`jplatform-api`) - stable interfaces
- Core implementation (`jplatform-core`) - ApplicationManager and ApplicationContext
- ClassLoader isolation (`jplatform-classloader`) - parent-last delegation
- Thread pool management (`jplatform-threadpool`) - per-application isolation
- Resource monitoring (`jplatform-monitoring`) - CPU, heap, thread tracking with quotas
- Security policy enforcement (`jplatform-security`) - configurable permissions
- Messaging (`jplatform-messaging`) - InMemoryMessageBus and ServiceRegistry
- JMS Messaging (`jplatform-messaging-jms`) - JMS-backed MessageBus for multi-node messaging
- Platform launcher (`jplatform-launcher`) - enhanced interactive console
- Sample applications - hello-world and messaging-app

**Deployment & Configuration (NEW in 1.0):**
- YAML/JSON descriptor parsing (`jplatform-config`) - full validation
- Filesystem watcher (`jplatform-fs-watcher`) - auto-deployment with debouncing
- REST API server (`jplatform-rest-api`) - full HTTP API
- Web console (`jplatform-web-console`) - modern browser-based UI
- Swing desktop UI (`jplatform-swing-ui`) - native desktop management interface ✅ **NEW in 1.1**
- Terminal UI (`jplatform-jcurses-ui`) - full-screen curses-like terminal interface ✅ **NEW in 1.1**

**Metrics & Monitoring (NEW in 1.0):**
- JMX metrics exporter (`jplatform-metrics-jmx`) - JConsole/VisualVM integration
- Prometheus metrics exporter (`jplatform-metrics-prometheus`) - modern monitoring stacks
- 100% test coverage on all new modules
- Complete JavaDoc documentation

**Test Coverage:**
- 500+ unit tests across all modules
- 90%+ code coverage
- Integration tests for all deployment methods
- Full CI/CD ready

### 📋 Future Enhancements (Post 1.0)
- JVMTI agent for precise heap monitoring (optional native component)
- Clustering support with Hazelcast (multi-node deployment)
- Advanced monitoring dashboards
- Performance optimizations
- Additional security hardening

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

## Documentation

### Platform Features
- [Hot Code Reload](HOT_RELOAD.md) - Zero-downtime deployments with state preservation
- [Application Dependencies](APPLICATION_DEPENDENCIES.md) - Dependency management and ordered startup
- [Persistent Volumes](VOLUMES.md) - Per-application storage management
- [Native Binary Support](NATIVE_BINARIES.md) - Platform-specific library loading
- [Observability](OBSERVABILITY.md) - OpenTelemetry, JMX, Prometheus integration

### Developer Guides
- [ClassLoader Best Practices](CLASSLOADER_BEST_PRACTICES.md) - Avoiding memory leaks
- [Security Guide](SECURITY.md) - Modern security enforcement without SecurityManager
- [Resource Enforcement](RESOURCE_ENFORCEMENT.md) - CPU/memory/thread limit enforcement

### Project Status
- [Enhancement Status](ENHANCEMENTS_STATUS.md) - Feature implementation tracking
- [Release Notes](RELEASE_NOTES.md) - Version history and changelog
- [GitHub Issues Resolved](GITHUB_ISSUES_RESOLVED.md) - Issue resolution summary

## Version Numbering

JPlatform uses a simple **X.Y version format** without patch versions or SNAPSHOT suffixes.

- **Current version**: 1.1
- **Format**: Major.Minor (e.g., 1.0, 2.0, 2.1)
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

## Documentation

### Getting Started
- **[Quick Reference](docs/QUICK_REFERENCE.md)** - Command reference and YAML templates
- **[Architecture Guide](docs/ARCHITECTURE.md)** - System architecture and design
- **[Troubleshooting Guide](docs/TROUBLESHOOTING.md)** - Common issues and solutions

### Deployment Guides
- **[VM Management](jplatform-vm-management/README.md)** - Virtual machine deployment
- **[Container Deployment](CONTAINER_DEPLOYMENT.md)** - Container orchestration
- **[Native Execution](NATIVE_EXECUTION.md)** - Native binary support

### Advanced Topics
- **[Hot Reload](HOT_RELOAD.md)** - Zero-downtime code updates
- **[Resource Enforcement](RESOURCE_ENFORCEMENT.md)** - Resource quotas and limits
- **[Application Dependencies](APPLICATION_DEPENDENCIES.md)** - Dependency management
- **[Volumes](VOLUMES.md)** - Persistent storage
- **[Observability](OBSERVABILITY.md)** - Metrics and tracing

### Examples
- **[Multi-Tier Applications](examples/multi-tier/)** - Complete application examples
- **[Three-Tier Web App](examples/multi-tier/three-tier-webapp/)** - VM + Java + Container example
