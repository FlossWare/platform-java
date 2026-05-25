# JPlatform Architecture

JPlatform provides unified orchestration for VMs, containers, Java applications, and native binaries through a common platform abstraction.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       JPlatform Core                             │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              ApplicationManager (Orchestrator)              │ │
│  │  - Unified lifecycle management                             │ │
│  │  - Dependency resolution                                    │ │
│  │  - Resource enforcement                                     │ │
│  │  - Cross-workload coordination                              │ │
│  └────────────────────────────────────────────────────────────┘ │
│         │              │              │              │           │
│         ▼              ▼              ▼              ▼           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │   VM     │  │Container │  │   Java   │  │  Native  │       │
│  │Launcher  │  │ Launcher │  │ Launcher │  │ Launcher │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘       │
└──────┬─────────────┬──────────────┬──────────────┬─────────────┘
       │             │              │              │
       ▼             ▼              ▼              ▼
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│ libvirt  │  │  Docker  │  │   JVM    │  │   OS     │
│   KVM    │  │  Podman  │  │ Isolated │  │ Process  │
│   QEMU   │  │   LXC    │  │ Threads  │  │ (exec)   │
└──────────┘  └──────────┘  └──────────┘  └──────────┘
```

## Component Architecture

### Core Platform

```
jplatform-core
├── ApplicationManager
│   ├── deploy(descriptor) → Create isolated environment
│   ├── start(appId)       → Launch workload
│   ├── stop(appId)        → Shutdown workload
│   ├── undeploy(appId)    → Cleanup resources
│   └── getMetrics(appId)  → Resource usage
│
├── DependencyResolver
│   ├── addApplication(id, descriptor)
│   ├── getStartupOrder()  → Topological sort
│   └── validateDependencies()
│
├── VmLauncher (NEW - 2.1)
│   ├── launch()  → Create and start VM
│   ├── stop()    → Shutdown VM
│   ├── pause()   → Suspend VM
│   └── resume()  → Resume VM
│
├── ContainerLauncher
│   ├── launch()  → Pull image and start container
│   ├── stop()    → Stop container
│   └── remove()  → Remove container
│
└── NativeProcessLauncher
    ├── launch()  → Start native binary
    └── stop()    → Terminate process
```

### Workload Type Detection

ApplicationManager detects workload type based on descriptor properties:

```java
// VM Detection
if (properties.containsKey("vm.disk") || 
    properties.containsKey("vm.vcpu")) {
    return WorkloadType.VIRTUAL_MACHINE;
}

// Container Detection
if (properties.containsKey("container.image") || 
    properties.containsKey("container.runtime")) {
    return WorkloadType.CONTAINER;
}

// Native Binary Detection
if (descriptor.isNativeImage()) {
    return WorkloadType.NATIVE_BINARY;
}

// Default: Java Application
return WorkloadType.JAVA_APPLICATION;
```

### Unified Lifecycle

All workload types follow the same lifecycle:

```
DEPLOYED → STARTING → RUNNING → STOPPING → STOPPED
                         ↓
                      FAILED
```

**State Transitions:**
- `deploy()` → DEPLOYED
- `start()` → STARTING → RUNNING
- `stop()` → STOPPING → STOPPED
- `undeploy()` → (removed from platform)

**Error Handling:**
- Any failure during start/stop → FAILED
- Platform can retry or alert

## Isolation Mechanisms

### Virtual Machines (VMs)
```
┌─────────────────────────────────────┐
│  VM (Full Hardware Virtualization)  │
│                                     │
│  ┌──────────────────────────────┐  │
│  │  Guest OS (Ubuntu/CentOS)    │  │
│  │  ┌────────────────────────┐  │  │
│  │  │   Application          │  │  │
│  │  └────────────────────────┘  │  │
│  └──────────────────────────────┘  │
│                                     │
│  Resources: 8 vCPU, 32GB RAM       │
└─────────────────────────────────────┘
         ↓ libvirt/KVM/QEMU
    Host Kernel
```

**Isolation Level**: Complete (separate kernel, separate memory, separate network)

### Containers
```
┌─────────────────────────────────────┐
│  Container (OS Virtualization)      │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   Application                │  │
│  │   + Libraries                │  │
│  └──────────────────────────────┘  │
│                                     │
│  Resources: 2 CPU, 4GB RAM         │
└─────────────────────────────────────┘
         ↓ Docker/Podman/LXC
    Shared Kernel (namespaces + cgroups)
```

**Isolation Level**: Process-level (shared kernel, isolated namespaces)

### Java Applications
```
┌─────────────────────────────────────┐
│  JVM (In-Process Isolation)         │
│                                     │
│  ┌──────────────────────────────┐  │
│  │  Isolated ClassLoader        │  │
│  │  ┌────────────────────────┐  │  │
│  │  │   Application Classes  │  │  │
│  │  └────────────────────────┘  │  │
│  │                              │  │
│  │  Isolated ThreadPool         │  │
│  │  Isolated SecurityPolicy     │  │
│  └──────────────────────────────┘  │
│                                     │
│  Resources: 4 CPU, 8GB heap        │
└─────────────────────────────────────┘
         ↓ JPlatform
    Shared JVM
```

**Isolation Level**: ClassLoader-level (shared JVM, isolated classes/threads)

### Native Binaries
```
┌─────────────────────────────────────┐
│  Native Process (OS Process)        │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   Compiled Binary            │  │
│  │   (GraalVM native-image,     │  │
│  │    C/C++/Rust/Go executable) │  │
│  └──────────────────────────────┘  │
│                                     │
│  Resources: 2 CPU, 2GB RAM         │
└─────────────────────────────────────┘
         ↓ OS exec()
    Host Kernel
```

**Isolation Level**: Process-level (separate process, OS-level isolation)

## Dependency Management

### Dependency Graph

```
Application A (no dependencies)
├─> Application B (depends on A)
│   ├─> Application D (depends on B)
│   └─> Application E (depends on B)
└─> Application C (depends on A)
    └─> Application F (depends on C)
```

### Startup Order Calculation

1. **Build dependency graph** from descriptors
2. **Detect cycles** (circular dependencies are errors)
3. **Topological sort** to determine order
4. **Start in order**, waiting for dependencies

**Example:**
```yaml
# postgres-vm (VM)
applicationId: postgres-vm
dependencies: []

# app-service (Java)
applicationId: app-service
dependencies:
  - postgres-vm

# nginx-web (Container)
applicationId: nginx-web
dependencies:
  - app-service
```

**Startup Order:**
1. postgres-vm (VM starts first)
2. app-service (waits for postgres-vm RUNNING)
3. nginx-web (waits for app-service RUNNING)

### Cross-Workload Dependencies

JPlatform allows ANY workload type to depend on ANY other:

```
VM → Java App → Container → Native Binary
↓      ↓          ↓            ↓
All managed through same DependencyResolver
```

## Resource Management

### Resource Quotas

Each workload can specify resource limits:

```yaml
resources:
  cpu: 8          # Max CPU cores/vCPUs
  memory: 32768   # Max RAM in MB
  disk: 524288    # Max disk in MB
  maxThreads: 200 # Max threads (Java apps)
```

### Resource Enforcement

```
┌──────────────────────────────────────┐
│     ApplicationResourceMonitor       │
│                                      │
│  ┌────────────────────────────────┐ │
│  │  Periodic Sampling (every 1s)  │ │
│  └────────────────────────────────┘ │
│              ↓                       │
│  ┌────────────────────────────────┐ │
│  │  Compare against Quotas        │ │
│  └────────────────────────────────┘ │
│              ↓                       │
│  ┌────────────────────────────────┐ │
│  │  Enforcement Actions:          │ │
│  │  - NOTIFY (log warning)        │ │
│  │  - THROTTLE (reduce resources) │ │
│  │  - SHUTDOWN (graceful stop)    │ │
│  │  - KILL (force terminate)      │ │
│  └────────────────────────────────┘ │
└──────────────────────────────────────┘
```

**Per-Workload Type:**
- **VMs**: libvirt CPU pinning, cgroups memory limits
- **Containers**: Docker/Podman resource constraints
- **Java Apps**: Heap limits, thread pool limits
- **Native**: OS process limits (ulimit, cgroups)

## Monitoring & Observability

### Metrics Collection

```
┌─────────────────────────────────────────────┐
│         Prometheus Metrics Export           │
├─────────────────────────────────────────────┤
│  VMs:                                       │
│  - jplatform_vm_cpu_time_seconds            │
│  - jplatform_vm_memory_mb                   │
│  - jplatform_vm_state                       │
│                                             │
│  Containers:                                │
│  - jplatform_container_cpu_usage            │
│  - jplatform_container_memory_mb            │
│  - jplatform_container_state                │
│                                             │
│  Java Apps:                                 │
│  - jplatform_app_heap_mb                    │
│  - jplatform_app_thread_count               │
│  - jplatform_app_cpu_time_seconds           │
│                                             │
│  Native Binaries:                           │
│  - jplatform_process_cpu_usage              │
│  - jplatform_process_memory_mb              │
│  - jplatform_process_state                  │
└─────────────────────────────────────────────┘
```

### OpenTelemetry Integration

```
Application → OTLP Exporter → Collector → Backend
                                           (Jaeger/Prometheus/Grafana)
```

## Security Architecture

### Multi-Level Security

```
┌────────────────────────────────────────────┐
│  Platform-Level Security                   │
│  - Authentication (RBAC)                   │
│  - Authorization (who can deploy)          │
│  - Audit logging                           │
└────────────────────────────────────────────┘
         ↓
┌────────────────────────────────────────────┐
│  Workload-Level Security                   │
│                                            │
│  VMs:                                      │
│  - SeLinux/AppArmor                        │
│  - Firewall rules                          │
│  - Disk encryption                         │
│                                            │
│  Containers:                               │
│  - Seccomp profiles                        │
│  - Capabilities dropping                   │
│  - User namespaces                         │
│                                            │
│  Java Apps:                                │
│  - SecurityManager policies                │
│  - ClassLoader restrictions                │
│  - Thread permissions                      │
│                                            │
│  Native:                                   │
│  - Process isolation                       │
│  - Capability restrictions                 │
│  - Sandbox execution                       │
└────────────────────────────────────────────┘
```

## Network Architecture

### VM Networking

```
VM → Virtual NIC (virtio) → Bridge/NAT → Host Network
                              ↓
                         virbr0 (bridge)
                         NAT (masquerade)
```

**Modes:**
- `bridge`: VM on same network as host
- `nat`: VM on private network with NAT
- `none`: No network (isolated)

### Container Networking

```
Container → veth pair → Docker/Podman Bridge → Host
                         ↓
                    docker0/cni0
```

**Modes:**
- `bridge`: Container on bridge network
- `host`: Share host network stack
- `none`: No network

### Java App Networking

```
Java App → ServerSocket → Host Network Stack
```

**Communication:**
- Binds to host interfaces
- Subject to firewall rules
- Can use JPlatform MessageBus for inter-app

## Storage Architecture

### VM Storage

```
VM → Virtual Disk (qcow2/raw) → Host Filesystem → Storage Backend
     ↓
     /var/lib/jplatform/vms/vm.qcow2
     ↓
     Local disk / NFS / Ceph / iSCSI
```

**Formats:**
- `qcow2`: Copy-on-write, snapshots, compression
- `raw`: Better performance, no features

### Container Storage

```
Container → Overlay Filesystem → Image Layers → Host
            ↓
            Union mount (overlay2)
            ↓
            /var/lib/docker or /var/lib/containers
```

**Volumes:**
- Named volumes
- Bind mounts
- tmpfs

### Java App Storage

```
Java App → JPlatform Volumes → Host Filesystem
           ↓
           /var/lib/jplatform/volumes/{app-id}/
```

**Volume Types:**
- `persistent`: Survives restarts
- `ephemeral`: Deleted on undeploy

## Deployment Flow

```
┌──────────────────────────────────────────────────────┐
│ 1. User submits YAML descriptor                      │
└────────────────┬─────────────────────────────────────┘
                 ↓
┌──────────────────────────────────────────────────────┐
│ 2. ApplicationManager.deploy(descriptor)             │
│    - Parse descriptor                                 │
│    - Detect workload type                            │
│    - Validate configuration                          │
│    - Create isolated environment:                    │
│      * ClassLoader (Java)                            │
│      * Volume mounts                                 │
│      * Security policy                               │
│    - Register with DependencyResolver                │
└────────────────┬─────────────────────────────────────┘
                 ↓
┌──────────────────────────────────────────────────────┐
│ 3. State: DEPLOYED                                   │
│    - Workload ready to start                         │
│    - Resources allocated                             │
│    - Not yet running                                 │
└────────────────┬─────────────────────────────────────┘
                 ↓
┌──────────────────────────────────────────────────────┐
│ 4. ApplicationManager.start(appId)                   │
│    - Wait for dependencies to be RUNNING             │
│    - Launch workload:                                │
│      * VM: vmLauncher.launch()                       │
│      * Container: containerLauncher.launch()         │
│      * Java: Load and instantiate main class         │
│      * Native: Execute binary                        │
│    - Start monitoring                                │
└────────────────┬─────────────────────────────────────┘
                 ↓
┌──────────────────────────────────────────────────────┐
│ 5. State: RUNNING                                    │
│    - Workload executing                              │
│    - Metrics being collected                         │
│    - Health checks active                            │
│    - Resource enforcement enabled                    │
└──────────────────────────────────────────────────────┘
```

## Integration Points

### With VirtOS

```
┌────────────────────────────────────────────────┐
│              VirtOS (Infrastructure)            │
│  - KVM/QEMU hypervisor                         │
│  - Storage management (Btrfs/ZFS/LVM)          │
│  - Network configuration (bridges/VLANs)       │
│  - Multi-cloud federation                      │
└────────────────┬───────────────────────────────┘
                 ↓ provides infrastructure
┌────────────────────────────────────────────────┐
│           JPlatform (Orchestration)            │
│  - Unified workload management                 │
│  - Cross-workload dependencies                 │
│  - Resource quotas and enforcement             │
│  - Monitoring and observability                │
└────────────────────────────────────────────────┘
```

**Integration:**
- VirtOS provides libvirt for VM management
- VirtOS provides container runtimes
- VirtOS provides storage backends
- JPlatform provides orchestration layer

### API Layers

```
┌────────────────────────────────────────────────┐
│  REST API (jplatform-rest-api)                 │
│  HTTP endpoints for remote management          │
└────────────────┬───────────────────────────────┘
                 ↓
┌────────────────────────────────────────────────┐
│  Java API (jplatform-api)                      │
│  ApplicationManager, Descriptors, Contexts     │
└────────────────┬───────────────────────────────┘
                 ↓
┌────────────────────────────────────────────────┐
│  CLI (jplatform-launcher)                      │
│  Command-line interface                        │
└────────────────────────────────────────────────┘
```

## Scalability

### Horizontal Scaling

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  JPlatform   │  │  JPlatform   │  │  JPlatform   │
│  Instance 1  │  │  Instance 2  │  │  Instance 3  │
└──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       │                 │                 │
       └─────────────────┴─────────────────┘
                         ↓
              ┌────────────────────┐
              │  Shared State      │
              │  (Consul/etcd/     │
              │   Hazelcast)       │
              └────────────────────┘
```

**Clustering Support:**
- jplatform-cluster: Multi-node coordination
- Service discovery across instances
- Shared configuration
- Distributed monitoring

### Vertical Scaling

- Increase resources per workload
- Dynamic resource allocation
- Hot-add CPU/memory (VMs)
- Scale up JVM heap (Java apps)

## Performance Characteristics

### Startup Time

| Workload Type | Typical Startup | Notes |
|---------------|----------------|-------|
| VM | 30-60s | OS boot time |
| Container | 1-5s | Image already pulled |
| Java App | 2-10s | ClassLoader + initialization |
| Native Binary | <1s | Direct execution |

### Resource Overhead

| Workload Type | Memory Overhead | CPU Overhead |
|---------------|-----------------|--------------|
| VM | ~128MB (hypervisor) | ~2-5% |
| Container | ~10MB (runtime) | ~1-2% |
| Java App | Shared JVM | Minimal |
| Native Binary | None | None |

### Throughput

- **VMs**: Depends on allocated vCPUs
- **Containers**: Near-native performance
- **Java Apps**: JVM optimization (JIT)
- **Native**: Native code performance

## Future Architecture Enhancements

### Planned Features

1. **VM Live Migration** (2.2)
   - Move running VMs between hosts
   - Zero-downtime maintenance
   - Load balancing

2. **VM Snapshots** (2.2)
   - Point-in-time VM state
   - Quick rollback
   - Backup integration

3. **GPU Passthrough** (2.3)
   - Direct GPU access for VMs
   - ML/AI workload acceleration
   - Graphics-intensive applications

4. **Multi-Host Clustering** (2.3)
   - Distribute workloads across hosts
   - High availability
   - Resource pooling

5. **Service Mesh Integration** (3.0)
   - Istio/Linkerd support
   - Traffic management
   - Security policies

## Conclusion

JPlatform provides a unified platform for orchestrating diverse workload types while maintaining appropriate isolation and resource management for each. The architecture is designed for:

- **Flexibility**: Support any workload type
- **Consistency**: Same API for all types
- **Isolation**: Appropriate boundaries per type
- **Performance**: Minimal overhead
- **Scalability**: Horizontal and vertical
- **Observability**: Comprehensive monitoring

This makes JPlatform suitable for:
- Development environments (mixed workloads)
- Production deployments (resource efficiency)
- Edge computing (unified management)
- Hybrid cloud (cross-environment consistency)
