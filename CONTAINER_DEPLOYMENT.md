# Container Deployment

JPlatform supports deploying applications as Docker, Podman, or LXC containers. This enables containerized workloads to be managed by the platform alongside JVM applications and native processes.

## Overview

Containerized applications run as **managed container instances**, with the platform handling container lifecycle (launch, stop, remove), image management, and log forwarding. The platform supports three container runtimes:

- **Docker** - Industry-standard container runtime
- **Podman** - Daemonless, rootless container engine
- **LXC** - Linux Containers for system-level virtualization

## Configuration

Mark an application as containerized by setting `container.runtime` or `container.image` properties:

```java
ApplicationDescriptor webApp = ApplicationDescriptor.builder()
    .applicationId("nginx-server")
    .name("NGINX Web Server")
    .property("container.runtime", "docker")
    .property("container.image", "nginx:latest")
    .property("container.ports", "8080:80")
    .build();
```

### Required Properties

| Property | Description | Example |
|----------|-------------|---------|
| `container.image` | Container image name and tag (**required**) | `nginx:latest`, `myapp:1.2.3` |

### Optional Properties

| Property | Description | Example |
|----------|-------------|---------|
| `container.runtime` | Runtime to use (defaults to `docker`) | `docker`, `podman`, `lxc` |
| `container.name` | Container name (defaults to applicationId) | `my-web-server` |
| `container.ports` | Port mappings (host:container, comma-separated) | `8080:80,8443:443` |
| `container.volumes` | Volume mounts (host:container, comma-separated) | `/data:/app/data,/logs:/app/logs` |
| `container.network` | Network mode | `bridge`, `host`, `none` |
| `container.env.*` | Environment variables (strip `container.env.` prefix) | `container.env.DB_HOST=postgres` |
| `container.args` | Additional arguments to pass to container | `--verbose --debug` |
| `container.lxc.config` | LXC config file path (LXC only) | `/etc/lxc/myapp.conf` |

## Example: Docker Web Server

Deploy NGINX as a Docker container:

```java
ApplicationDescriptor nginx = ApplicationDescriptor.builder()
    .applicationId("web-server")
    .name("NGINX Web Server")
    .property("container.runtime", "docker")
    .property("container.image", "nginx:alpine")
    .property("container.ports", "8080:80,8443:443")
    .property("container.volumes", "/var/www:/usr/share/nginx/html")
    .property("container.network", "bridge")
    .property("container.env.NGINX_HOST", "example.com")
    .property("container.env.NGINX_PORT", "80")
    .build();

manager.deploy(nginx);
manager.start("web-server");
```

This executes:
```bash
docker run -d \
  --name web-server \
  -p 8080:80 \
  -p 8443:443 \
  -v /var/www:/usr/share/nginx/html \
  --network bridge \
  -e NGINX_HOST=example.com \
  -e NGINX_PORT=80 \
  nginx:alpine
```

## Example: Podman Database

Deploy PostgreSQL using Podman (rootless):

```java
ApplicationDescriptor postgres = ApplicationDescriptor.builder()
    .applicationId("database")
    .name("PostgreSQL Database")
    .property("container.runtime", "podman")
    .property("container.image", "postgres:15-alpine")
    .property("container.ports", "5432:5432")
    .property("container.volumes", "/var/lib/postgresql/data:/var/lib/postgresql/data")
    .property("container.env.POSTGRES_USER", "appuser")
    .property("container.env.POSTGRES_PASSWORD", "secret")
    .property("container.env.POSTGRES_DB", "appdb")
    .build();

manager.deploy(postgres);
manager.start("database");
```

## Example: LXC System Container

Deploy an LXC container for system-level isolation:

```java
ApplicationDescriptor lxcApp = ApplicationDescriptor.builder()
    .applicationId("ubuntu-container")
    .name("Ubuntu LXC Container")
    .property("container.runtime", "lxc")
    .property("container.image", "ubuntu")  // Not used for LXC pull
    .property("container.lxc.config", "/etc/lxc/ubuntu.conf")
    .build();

manager.deploy(lxcApp);
manager.start("ubuntu-container");
```

## Lifecycle Management

### Deploy & Start

When `manager.start(applicationId)` is called for a containerized application:

1. Platform detects `container.runtime` or `container.image` property
2. Determines container runtime (Docker/Podman/LXC)
3. **For Docker/Podman**: Checks if image exists locally
4. **If image missing**: Pulls image via `docker pull` or `podman pull`
5. Builds container run command with ports, volumes, env vars, network
6. Launches container in detached mode (`-d`)
7. Captures container ID from command output
8. Starts background thread to follow container logs
9. Sets state to `RUNNING`

### Stop

When `manager.stop(applicationId)` is called:

1. Platform sends stop command (`docker stop`, `podman stop`, `lxc-stop`)
2. Waits up to 10 seconds for graceful shutdown
3. Removes container (`docker rm -f`, `podman rm -f`, `lxc-destroy`)
4. Sets state to `STOPPED`

### Log Forwarding

Container logs are streamed to the platform's logging system:

```
[web-server] 192.168.1.100 - - [23/May/2026:22:15:43 +0000] "GET / HTTP/1.1" 200 612
[database] 2026-05-23 22:15:45.123 UTC [1] LOG:  database system is ready to accept connections
```

## Container Runtime Detection

The platform determines the container runtime in this order:

1. **Explicit `container.runtime` property** - Use specified runtime
2. **Default to Docker** - If only `container.image` is set

```java
// Explicit runtime
.property("container.runtime", "podman")

// Defaults to Docker
.property("container.image", "myapp:latest")
```

## Image Management

### Automatic Image Pulling

For Docker and Podman, the platform **automatically pulls missing images**:

```java
.property("container.image", "postgres:15-alpine")
```

Before launching the container, the platform:
1. Checks if image exists: `docker image inspect postgres:15-alpine`
2. If missing, pulls: `docker pull postgres:15-alpine`
3. Then launches: `docker run ...`

### Private Registries

To use private registries, authenticate the runtime before deploying:

```bash
# Docker
docker login registry.example.com

# Podman
podman login registry.example.com
```

Then reference private images:

```java
.property("container.image", "registry.example.com/myapp:1.2.3")
```

## Port Mappings

Map host ports to container ports using comma-separated `host:container` pairs:

```java
// Single port
.property("container.ports", "8080:80")

// Multiple ports
.property("container.ports", "8080:80,8443:443,9090:9090")
```

Executed as:
```bash
docker run -p 8080:80 -p 8443:443 -p 9090:9090 ...
```

## Volume Mounts

Mount host directories into containers using comma-separated `host:container` paths:

```java
// Single volume
.property("container.volumes", "/var/data:/app/data")

// Multiple volumes
.property("container.volumes", "/var/data:/app/data,/var/logs:/app/logs,/etc/config:/app/config")
```

Executed as:
```bash
docker run -v /var/data:/app/data -v /var/logs:/app/logs -v /etc/config:/app/config ...
```

## Environment Variables

Set container environment variables using `container.env.*` properties:

```java
.property("container.env.DATABASE_URL", "jdbc:postgresql://db:5432/app")
.property("container.env.REDIS_HOST", "localhost")
.property("container.env.LOG_LEVEL", "INFO")
```

Executed as:
```bash
docker run -e DATABASE_URL=jdbc:postgresql://db:5432/app -e REDIS_HOST=localhost -e LOG_LEVEL=INFO ...
```

## Network Configuration

Specify the container network mode:

```java
// Bridge network (default)
.property("container.network", "bridge")

// Host network (use host's network stack)
.property("container.network", "host")

// No network
.property("container.network", "none")
```

## Comparison: Container vs. JVM vs. Native

| Feature | JVM Apps | Native Processes | Containers |
|---------|----------|------------------|------------|
| **Isolation** | Classloader | OS process | OS process + namespaces |
| **Resource Limits** | JVM heap config | OS limits | cgroups (future) |
| **Image Management** | JAR files | Binaries | Container images |
| **Startup Time** | Medium (JVM warmup) | Fast (compiled) | Fast (container overhead) |
| **Portability** | JVM required | Platform-specific | Runtime-agnostic |
| **Ecosystem** | Maven/Gradle | Custom | Docker Hub, registries |

## Platform Feature Support

Containerized applications have limited access to platform features since they run in isolated containers:

### ✅ Supported
- Lifecycle management (deploy, start, stop, undeploy)
- Container monitoring (ID, status, uptime)
- Log forwarding to platform logs
- Port mappings and volume mounts
- Environment variable injection
- Network configuration

### ❌ Not Supported (Container Isolation)
- In-process thread pool sharing
- In-memory message bus (requires network bridge)
- In-process service registry (requires external discovery)
- JVM-based resource monitoring (use container stats)
- Hot code reload (requires container rebuild)

## Use Cases

### When to Use Containers

1. **Existing Container Images**: Pre-built Docker images from Docker Hub
2. **Microservices**: Lightweight, isolated services with network communication
3. **Third-Party Services**: Databases (PostgreSQL, Redis), message queues (RabbitMQ)
4. **Multi-Language Stack**: Run non-JVM apps (Python, Node.js, Go) alongside Java
5. **DevOps Alignment**: Leverage existing container build pipelines

### When to Use JVM Execution

1. **Standard Java Applications**: Spring Boot, Jakarta EE apps
2. **Platform Integration**: Need message bus, service registry
3. **Resource Efficiency**: Share JVM heap across apps
4. **Hot Reload**: Update code without restart

### When to Use Native Processes

1. **GraalVM Native Images**: Fast startup, low memory
2. **Compiled Binaries**: C, Rust, Go executables
3. **No Container Overhead**: Direct process execution

## Implementation Details

### ContainerLauncher

Core component that manages container lifecycle:

```java
public class ContainerLauncher {
    public ContainerInfo launch(String applicationId, 
                                ApplicationDescriptor descriptor) throws IOException;
    
    public void stop(String applicationId, 
                    ContainerInfo containerInfo, 
                    long gracefulTimeoutMs) throws InterruptedException, IOException;
    
    public enum ContainerRuntime { DOCKER, PODMAN, LXC }
    
    public static class ContainerInfo {
        public String getContainerId();
        public String getContainerName();
        public ContainerRuntime getRuntime();
        public Process getProcess();
    }
}
```

### ApplicationContext Tracking

Containers are tracked via `ApplicationContextImpl`:

```java
// Get container info (if any)
Optional<ContainerLauncher.ContainerInfo> info = context.getContainerInfo();

// Check container status
if (info.isPresent()) {
    String containerId = info.get().getContainerId();
    ContainerRuntime runtime = info.get().getRuntime();
    // ...
}
```

## Testing Containerized Applications

Unit tests for container support focus on configuration validation and runtime detection:

```java
@Test
void testContainerRuntimeFromString() {
    assertEquals(ContainerRuntime.DOCKER, 
                ContainerRuntime.fromString("docker"));
    assertEquals(ContainerRuntime.PODMAN, 
                ContainerRuntime.fromString("podman"));
    assertEquals(ContainerRuntime.LXC, 
                ContainerRuntime.fromString("lxc"));
}

@Test
void testLaunchWithMissingImageThrowsException() {
    ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
        .applicationId("app1")
        .property("container.runtime", "docker")
        // Missing container.image
        .build();
    
    assertThrows(IllegalArgumentException.class, () -> {
        launcher.launch("app1", descriptor);
    });
}
```

Full integration tests require Docker/Podman/LXC installed and running.

## Future Enhancements

- **Health Checks**: HTTP/TCP probes for container health monitoring
- **Resource Limits**: Apply CPU/memory limits via container runtime
- **Restart Policies**: Automatic restart on failure (like Kubernetes)
- **Multi-Container Deployments**: Docker Compose / Pod-like grouping
- **Service Networking**: Automatic DNS/service discovery between containers
- **Volume Lifecycle**: Platform-managed volumes with size limits
- **Image Building**: Build images from Dockerfile within platform

## See Also

- [Native Execution](NATIVE_EXECUTION.md) - Running native processes (GraalVM, compiled binaries)
- [Application Lifecycle](LIFECYCLE.md) - General lifecycle management
- [Resource Enforcement](RESOURCE_ENFORCEMENT.md) - Resource quotas and limits
