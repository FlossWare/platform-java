# JPlatform Core

Core platform module providing application lifecycle management, dependency resolution, and process orchestration.

## Overview

The `jplatform-core` module is the heart of JPlatform, implementing the central `ApplicationManager` and supporting components for deploying, starting, stopping, and managing applications. It orchestrates classloader isolation, thread pools, security policies, resource monitoring, and now native process and container execution.

## Key Components

### ApplicationManager

Central orchestrator for application lifecycle with fine-grained per-application locking for high-performance concurrent operations.

**Capabilities:**
- Deploy/undeploy applications
- Start/stop applications (JVM, native, or containerized)
- Hot code reload with state preservation
- Dependency resolution and validation
- Startup order calculation (topological sort)
- Graceful shutdown coordination
- Thread-safe with per-application `ReentrantLock` for parallel operations on different applications

**Thread Safety:** Uses fine-grained per-application locking introduced in version 1.1 to eliminate synchronization bottleneck. Operations on different applications can execute in parallel, while operations on the same application are serialized.

### ApplicationContextImpl

Container for all application-specific resources providing isolated execution environment:
- `ClassLoader` - Isolated classloader for application classes
- `ThreadPoolExecutor` - Per-application thread pool
- `SecurityPolicy` - Application security constraints
- `ResourceMonitor` - CPU, memory, thread usage tracking
- `MessageBus` (optional) - Inter-application messaging
- `ServiceRegistry` (optional) - Service discovery
- `VolumeManager` (optional) - Persistent storage
- `Process` (native apps) - OS process handle
- `ContainerInfo` (containerized apps) - Container runtime details

### DependencyResolver

Manages application dependencies and calculates startup order:
- Validates REQUIRED and OPTIONAL dependencies
- Detects circular dependencies
- Performs topological sort for startup order
- Tracks service version requirements (semver)

**Example:**
```java
DependencyResolver resolver = new DependencyResolver(serviceRegistry);
resolver.addApplication("app1", descriptor1);
resolver.addApplication("app2", descriptor2);

List<String> startupOrder = resolver.getStartupOrder();  // ["app1", "app2"]
List<String> errors = resolver.validateDependencies("app2");
```

### DependencyGraph

Internal graph data structure for dependency tracking:
- Directed graph with cycle detection
- Topological sort implementation
- Tracks forward and reverse edges
- Used by DependencyResolver

### ApplicationReloader

Handles hot code reload for JVM applications:
- Creates new classloader with updated JAR files
- Preserves application state via `ReloadableApplication` interface
- Swaps classloader atomically to avoid race conditions
- Tracks classloader versions for potential rollback
- Graceful degradation on reload failure

**Reload Process:**
1. Create new classloader with updated code
2. Capture state from old instance (if `ReloadableApplication`)
3. Stop old instance
4. Swap classloader in `ApplicationContext`
5. Create new instance from updated code
6. Restore state to new instance
7. Start new instance (if was running)

### NativeProcessLauncher

Manages native executable processes (GraalVM native images, compiled binaries):
- Resolves executable path from descriptor
- Launches process via `ProcessBuilder`
- Redirects stdout/stderr to platform logging
- Graceful shutdown with `SIGTERM` followed by `SIGKILL`
- Configurable via `native.*` properties

**Example:**
```java
ApplicationDescriptor nativeApp = ApplicationDescriptor.builder()
    .applicationId("graal-app")
    .nativeImage(true)
    .property("native.executable.path", "/usr/local/bin/myapp")
    .property("native.args", "--server --port=8080")
    .property("native.env.DATABASE_URL", "jdbc:postgresql://db/app")
    .build();
```

### ContainerLauncher

Manages containerized applications via Docker, Podman, or LXC:
- Multi-runtime support with automatic detection
- Automatic image pulling for Docker/Podman
- Container lifecycle: launch, stop, remove
- Port mappings, volume mounts, environment variables
- Network configuration (bridge, host, none)
- Log forwarding from container to platform

**Example:**
```java
ApplicationDescriptor containerApp = ApplicationDescriptor.builder()
    .applicationId("web-server")
    .property("container.runtime", "docker")
    .property("container.image", "nginx:alpine")
    .property("container.ports", "8080:80")
    .property("container.volumes", "/var/www:/usr/share/nginx/html")
    .build();
```

### NativeLibraryLoader

Loads platform-specific native libraries for JVM applications:
- Detects current platform (OS + architecture)
- Filters libraries by platform compatibility
- Extracts libraries to app-isolated directory
- Returns library directory for `java.library.path`

**Supported Platforms:**
- Linux x64, ARM64
- Windows x64
- macOS x64, ARM64 (Apple Silicon)

### ClassLoaderVersion

Tracks classloader versions during hot reload:
- Version number (incrementing)
- Creation timestamp
- Reference counting for garbage collection
- Used by ApplicationReloader for version history

## Deployment Modes

JPlatform supports **three deployment modes** managed by ApplicationManager:

### 1. JVM Applications (Default)
- Run in isolated classloaders within the JVM
- Access to all platform features (message bus, service registry, thread pools)
- Hot code reload supported
- Lowest overhead for Java applications

### 2. Native Processes (`nativeImage: true`)
- Run as separate OS processes
- GraalVM native images, compiled binaries (C, Rust, Go)
- Fast startup, low memory footprint
- Limited platform feature access (no in-process messaging)
- Process lifecycle managed by platform

### 3. Containers (`container.runtime` or `container.image`)
- Run in Docker, Podman, or LXC containers
- Pre-built images from registries
- Full isolation via container namespaces
- Platform manages container lifecycle and log forwarding
- Ideal for microservices and third-party services

## Test Coverage

The module has comprehensive unit test coverage (70 tests as of version 1.1):

- `ApplicationManagerTest` (6 tests) - Basic lifecycle
- `ApplicationManagerConcurrencyTest` (4 tests) - Fine-grained locking verification
- `DependencyResolverTest` (6 tests) - Dependency validation
- `DependencyGraphTest` (11 tests) - Graph algorithms
- `ApplicationReloaderTest` (7 tests) - Hot reload version tracking
- `NativeLibraryLoaderTest` (9 tests) - Platform detection and library loading
- `ClassLoaderVersionTest` (9 tests) - Reference counting
- `NativeProcessLauncherTest` (7 tests) - Native process configuration
- `ContainerLauncherTest` (11 tests) - Container runtime and validation

Test coverage focuses on API contracts and configuration validation. Full integration tests require actual executables, containers, and running services.

## Performance Improvements

### Version 1.1 - Fine-Grained Locking

Replaced class-level `synchronized` methods with per-application `ReentrantLock`:

**Before (v1.0):**
```java
public synchronized void start(String applicationId) { ... }
public synchronized void stop(String applicationId) { ... }
```

**After (v1.1):**
```java
private final ConcurrentHashMap<String, ReentrantLock> applicationLocks;

public void start(String applicationId) {
    ReentrantLock lock = applicationLocks.get(applicationId);
    lock.lock();
    try {
        // ...
    } finally {
        lock.unlock();
    }
}
```

**Benefits:**
- Parallel operations on different applications
- Serialized operations on same application
- Significantly improved scalability and throughput
- Eliminates synchronization bottleneck

## Dependencies

- `jplatform-api` - Public interfaces and contracts
- `jplatform-classloader` - Isolated classloader implementation
- `jplatform-threadpool` - Managed thread pool per application
- `jplatform-security` - Security policy enforcement
- `jplatform-monitoring` - Resource monitoring and quotas
- `jplatform-storage` - Volume management (2.0+)

## Usage Examples

### Deploy JVM Application

```java
ApplicationManager manager = new ApplicationManager();

ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
    .applicationId("my-app")
    .name("My Application")
    .mainClass("com.example.MyApp")
    .addClasspathEntry(URI.create("file:///path/to/app.jar"))
    .build();

manager.deploy(descriptor);
manager.start("my-app");
```

### Deploy Native Process

```java
ApplicationDescriptor nativeApp = ApplicationDescriptor.builder()
    .applicationId("graal-app")
    .nativeImage(true)
    .property("native.executable.path", "/usr/local/bin/myapp")
    .build();

manager.deploy(nativeApp);
manager.start("graal-app");
```

### Deploy Container

```java
ApplicationDescriptor container = ApplicationDescriptor.builder()
    .applicationId("redis")
    .property("container.runtime", "docker")
    .property("container.image", "redis:alpine")
    .property("container.ports", "6379:6379")
    .build();

manager.deploy(container);
manager.start("redis");
```

## See Also

- [Native Execution](../NATIVE_EXECUTION.md) - Running native processes
- [Container Deployment](../CONTAINER_DEPLOYMENT.md) - Docker/Podman/LXC support
- [Hot Reload](../HOT_RELOAD.md) - Hot code reload documentation
- [Application Dependencies](../APPLICATION_DEPENDENCIES.md) - Dependency management
