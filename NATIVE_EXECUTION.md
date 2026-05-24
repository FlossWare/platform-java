# Native Process Execution

JPlatform supports deploying and managing native executable applications alongside JVM applications. This enables running GraalVM native images, compiled binaries (C, C++, Rust, Go), and other native executables within the platform's lifecycle management.

## Overview

Native applications run as **separate OS processes** (not in the JVM), but are managed by JPlatform with the same lifecycle controls as JVM applications. The platform handles process launching, monitoring, graceful shutdown, and output redirection.

## Configuration

Mark an application as a native executable by setting the `nativeImage` flag:

```java
ApplicationDescriptor nativeApp = ApplicationDescriptor.builder()
    .applicationId("my-native-app")
    .mainClass("com.example.NativeApp")  // Not used for native apps
    .nativeImage(true)
    .addClasspathEntry(URI.create("file:///path/to/executable"))
    .build();
```

### Properties

Native applications support these configuration properties:

| Property | Description | Example |
|----------|-------------|---------|
| `native.executable.path` | Explicit path to the native executable | `/usr/local/bin/myapp` |
| `native.workdir` | Working directory for the process | `/var/jplatform/apps/myapp` |
| `native.args` | Command-line arguments to pass | `--server --port=8080` |
| `native.env.*` | Environment variables (strip prefix) | `native.env.DATABASE_URL=jdbc:...` |

### Executable Path Resolution

The platform resolves the native executable path in this order:

1. **Explicit property**: If `native.executable.path` is set, use that path
2. **First classpath entry**: Otherwise, use the first URI from `classpathEntries`

```java
// Option 1: Explicit path
.property("native.executable.path", "/usr/local/bin/myapp")

// Option 2: Via classpath (first entry used)
.addClasspathEntry(URI.create("file:///usr/local/bin/myapp"))
```

## Example: GraalVM Native Image

Deploy a GraalVM-compiled native image:

```java
ApplicationDescriptor graalApp = ApplicationDescriptor.builder()
    .applicationId("graal-http-server")
    .name("High-Performance HTTP Server")
    .nativeImage(true)
    .addClasspathEntry(URI.create("file:///opt/apps/http-server"))
    .property("native.workdir", "/var/apps/http-server")
    .property("native.args", "--port 8080 --threads 10")
    .property("native.env.JAVA_OPTS", "-Xmx512m")
    .build();

manager.deploy(graalApp);
manager.start("graal-http-server");
```

## Example: Compiled Binary (Rust)

Deploy a Rust-compiled executable:

```java
ApplicationDescriptor rustApp = ApplicationDescriptor.builder()
    .applicationId("rust-worker")
    .name("Rust Background Worker")
    .nativeImage(true)
    .property("native.executable.path", "/usr/local/bin/rust-worker")
    .property("native.env.RUST_LOG", "info")
    .property("native.env.WORKER_THREADS", "4")
    .build();

manager.deploy(rustApp);
manager.start("rust-worker");
```

## Lifecycle Management

### Start

When `manager.start(applicationId)` is called for a native application:

1. Platform detects `descriptor.isNativeImage() == true`
2. Resolves executable path from properties or classpath
3. Creates working directory if needed
4. Builds command with arguments and environment variables
5. Launches process via `ProcessBuilder`
6. Starts background thread to capture and log process output
7. Sets state to `RUNNING`

### Stop

When `manager.stop(applicationId)` is called:

1. Platform sends `SIGTERM` (graceful shutdown signal)
2. Waits up to 10 seconds for process to exit
3. If still running, sends `SIGKILL` (force terminate)
4. Waits up to 5 seconds for force kill
5. Sets state to `STOPPED`

### Output Redirection

Process stdout/stderr is redirected to the platform's logging system:

```
[graal-http-server] Server listening on port 8080
[graal-http-server] Accepted connection from 192.168.1.100
[rust-worker] Processing job ID 12345
```

## Comparison: JVM vs. Native

| Feature | JVM Applications | Native Applications |
|---------|------------------|---------------------|
| **Execution** | In-process (classloader) | Separate OS process |
| **Startup Time** | Slower (JVM warmup) | Faster (compiled binary) |
| **Memory** | Shared JVM heap | Isolated process memory |
| **Thread Pool** | Platform-provided `ThreadPoolExecutor` | Application manages own threads |
| **Message Bus** | In-memory shared bus | IPC via sockets/HTTP (future) |
| **Service Registry** | In-process lookup | Network-based discovery (future) |
| **Hot Reload** | Classloader swap | Process restart |
| **Resource Monitoring** | JVM heap/threads | OS-level (cgroups, /proc) |

## Platform Feature Support

Native applications have limited access to platform features since they run outside the JVM:

### ✅ Supported
- Lifecycle management (deploy, start, stop, undeploy)
- Process monitoring (PID, exit code, uptime)
- Output redirection to platform logs
- Environment variable injection
- Working directory configuration

### ❌ Not Supported (Native Process Limitation)
- In-process thread pool sharing
- In-memory message bus (requires IPC)
- In-process service registry (requires network discovery)
- JVM-based resource monitoring (heap, threads)
- Hot code reload (requires process restart)

## Use Cases

### When to Use Native Execution

1. **Performance-Critical Applications**: Low-latency, high-throughput workloads
2. **Fast Startup Required**: GraalVM native images for microservices
3. **Lower Memory Footprint**: Compiled binaries vs. JVM overhead
4. **Polyglot Platform**: Run non-JVM languages (Rust, Go, C++) alongside Java
5. **Legacy Integration**: Wrap existing native binaries in platform lifecycle

### When to Use JVM Execution

1. **Standard Java Applications**: Spring Boot, Jakarta EE, typical Java apps
2. **Platform Integration**: Need message bus, service registry, shared features
3. **Hot Reload Required**: Update code without process restart
4. **Resource Sharing**: Benefit from shared JVM heap and thread pools

## Implementation Details

### NativeProcessLauncher

Core component that manages native process lifecycle:

```java
public class NativeProcessLauncher {
    public Process launch(String applicationId, 
                         ApplicationDescriptor descriptor, 
                         Path workingDir) throws IOException;
    
    public void stop(String applicationId, 
                    Process process, 
                    long gracefulTimeoutMs) throws InterruptedException;
}
```

### ApplicationContext Tracking

Native processes are tracked via `ApplicationContextImpl`:

```java
// Get native process (if any)
Optional<Process> process = context.getNativeProcess();

// Check if process is alive
if (process.isPresent() && process.get().isAlive()) {
    long pid = process.get().pid();
    // ...
}
```

## Testing Native Applications

Unit tests for native process support focus on configuration validation:

```java
@Test
void testLaunchWithNonNativeDescriptorThrowsException() {
    ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
        .applicationId("app1")
        .nativeImage(false)  // Not a native image
        .build();
    
    assertThrows(IllegalArgumentException.class, () -> {
        launcher.launch("app1", descriptor, tempDir);
    });
}
```

Full integration tests require actual native executables and platform-specific tools.

## Future Enhancements

- **Inter-Process Communication**: Enable native apps to use message bus via Unix sockets / gRPC
- **Service Discovery**: Network-based service registry for native processes
- **Resource Limits**: Apply cgroups/rlimit to native processes
- **Health Checks**: HTTP/TCP probes for native process health
- **Restart Policies**: Automatic restart on failure (like systemd)
- **Signal Handling**: Custom signals for graceful reload (SIGHUP)

## See Also

- [Container Deployment](CONTAINER_DEPLOYMENT.md) - Running applications in Docker/Podman/LXC
- [Native Binaries](NATIVE_BINARIES.md) - Loading native libraries (.so/.dll) in JVM apps
- [Application Lifecycle](LIFECYCLE.md) - General lifecycle management
