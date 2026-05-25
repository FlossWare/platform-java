# JPlatform VM Management

Virtual machine management module for JPlatform using libvirt/KVM/QEMU.

## Overview

This module enables JPlatform to manage VMs alongside containers, Java applications, and native binaries, providing **unified orchestration across all workload types**.

**Key Features:**
- Create and manage KVM/QEMU VMs via libvirt
- Same API as other JPlatform workloads (containers, Java apps, native binaries)
- Resource quotas and monitoring for VMs
- VM dependencies (startup ordering)
- VNC console access
- Live migration support (future)

## Requirements

**System:**
- Linux with KVM support
- libvirt installed and running
- QEMU/KVM virtualization

**Check if KVM is available:**
```bash
# Check KVM module
lsmod | grep kvm

# Check if libvirt is running
systemctl status libvirtd

# Test libvirt connection
virsh list --all
```

**Install on Ubuntu/Debian:**
```bash
sudo apt install qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils
sudo systemctl enable --now libvirtd
sudo usermod -aG libvirt $USER
```

**Install on Fedora/RHEL:**
```bash
sudo dnf install qemu-kvm libvirt virt-install
sudo systemctl enable --now libvirtd
sudo usermod -aG libvirt $USER
```

## Maven Dependency

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>jplatform-vm-management</artifactId>
    <version>2.1.0</version>
</dependency>
```

## Usage

### Deploy VM via YAML Descriptor

```yaml
# database-vm.yaml
applicationId: database-vm
name: PostgreSQL Database VM

properties:
  vm.vcpu: "8"
  vm.memory: "32768"  # 32GB in MB
  vm.disk: /var/lib/jplatform/vms/db.qcow2
  vm.network: bridge
  
resources:
  cpu: 8
  memory: 32768

dependencies:
  - storage-vm
```

```bash
# Deploy via JPlatform
jplatform deploy database-vm.yaml

# Start VM
jplatform start database-vm

# View status
jplatform status database-vm

# Stop VM (graceful)
jplatform stop database-vm

# Force stop
jplatform stop database-vm --force
```

### Deploy VM via Java API

```java
import org.flossware.jplatform.api.ApplicationDescriptor;
import org.flossware.jplatform.api.ApplicationManager;

ApplicationDescriptor vmDescriptor = ApplicationDescriptor.builder()
    .applicationId("web-server-vm")
    .name("Web Server VM")
    .property("vm.vcpu", "4")
    .property("vm.memory", "8192")
    .property("vm.disk", "/var/lib/jplatform/vms/web.qcow2")
    .property("vm.network", "nat")
    .property("vm.vnc.enabled", "true")
    .build();

ApplicationManager manager = ApplicationManager.getInstance();
manager.deploy(vmDescriptor);
manager.start("web-server-vm");
```

### Same API for ALL Workloads

```java
// Deploy VM
manager.deploy(vmDescriptor);

// Deploy container
manager.deploy(containerDescriptor);

// Deploy Java app
manager.deploy(javaDescriptor);

// ALL use same lifecycle commands:
manager.start(id);
manager.stop(id);
manager.restart(id);
manager.getMetrics(id);
```

## VM Configuration Properties

| Property | Description | Default | Example |
|----------|-------------|---------|---------|
| `vm.name` | VM name | applicationId | `db-primary` |
| `vm.vcpu` | Number of vCPUs | `2` | `8` |
| `vm.memory` | RAM in MB | `4096` | `32768` |
| `vm.disk` | Path to disk image | **required** | `/var/lib/jplatform/vms/vm.qcow2` |
| `vm.disk.format` | Disk format | `qcow2` | `qcow2`, `raw` |
| `vm.network` | Network mode | `bridge` | `bridge`, `nat`, `none` |
| `vm.bridge` | Bridge name (if bridge mode) | `virbr0` | `br0` |
| `vm.vnc.enabled` | Enable VNC console | `false` | `true` |
| `vm.vnc.port` | VNC port | `-1` (auto) | `5900` |

## VM Dependencies

VMs can depend on other VMs, containers, or any JPlatform workload:

```yaml
# Application VM depends on database VM
applicationId: app-vm
dependencies:
  - database-vm        # Another VM
  - redis-container    # Container
  - auth-service       # Java app
```

**JPlatform ensures:**
1. `database-vm` starts first
2. `redis-container` starts second
3. `auth-service` starts third
4. `app-vm` starts last (after all dependencies ready)

## Resource Quotas

JPlatform monitors and enforces resource limits for VMs:

```yaml
resources:
  cpu: 8          # Max 8 vCPUs
  memory: 32768   # Max 32GB RAM
  disk: 524288    # Max 500GB disk
```

**Enforcement:**
- CPU: Libvirt CPU pinning and shares
- Memory: Hard memory limit via cgroups
- Disk: Disk quota enforcement

## Monitoring

VMs export metrics to Prometheus like other JPlatform workloads:

```
# VM metrics
jplatform_vm_cpu_time_seconds{vm="database-vm"}
jplatform_vm_memory_mb{vm="database-vm"}
jplatform_vm_state{vm="database-vm", state="running"}
```

**View metrics:**
```bash
# Via CLI
jplatform metrics database-vm

# Via REST API
curl http://localhost:8080/metrics

# Via Prometheus
http://localhost:9090/graph
```

## VNC Console Access

Enable VNC to access VM console:

```yaml
properties:
  vm.vnc.enabled: "true"
  vm.vnc.port: "5900"  # or -1 for auto-assign
```

**Connect via VNC client:**
```bash
# Find VNC port
virsh vncdisplay database-vm

# Connect with VNC viewer
vncviewer localhost:5900
```

## Creating VM Disk Images

**Option 1: Use existing cloud image**
```bash
# Download Ubuntu cloud image
wget https://cloud-images.ubuntu.com/releases/22.04/release/ubuntu-22.04-server-cloudimg-amd64.img

# Copy as VM disk
cp ubuntu-22.04-server-cloudimg-amd64.img /var/lib/jplatform/vms/myvm.qcow2

# Resize if needed
qemu-img resize /var/lib/jplatform/vms/myvm.qcow2 50G
```

**Option 2: Create new empty disk**
```bash
# Create 50GB qcow2 disk
qemu-img create -f qcow2 /var/lib/jplatform/vms/myvm.qcow2 50G
```

**Option 3: Convert from other format**
```bash
# Convert VMDK to qcow2
qemu-img convert -f vmdk -O qcow2 source.vmdk /var/lib/jplatform/vms/myvm.qcow2
```

## Integration with VirtOS

When JPlatform runs on VirtOS, VM management is pre-configured:

```bash
# VirtOS includes JPlatform with VM support
virtos-tui
  → Workloads (JPlatform)
     → Virtual Machines
        → Deploy VM
        → List VMs
        → VM Monitoring

# All VMs managed via JPlatform
# VirtOS provides the infrastructure (storage, networking, multi-cloud)
# JPlatform provides the orchestration
```

## Architecture

```
┌─────────────────────────────────────────┐
│         JPlatform (Orchestrator)        │
│                                         │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ │
│  │ VMs  │ │Ctnrs │ │Java  │ │Native│ │
│  │QEMU  │ │Docker│ │Apps  │ │Binary│ │
│  └──────┘ └──────┘ └──────┘ └──────┘ │
│                                         │
│  Unified: API, quotas, monitoring, deps │
└─────────────────────────────────────────┘
          ↓ uses
┌─────────────────────────────────────────┐
│         libvirt/KVM/QEMU                │
│  - VM lifecycle management              │
│  - Resource allocation                  │
│  - Monitoring                           │
└─────────────────────────────────────────┘
```

## Comparison: JPlatform VM vs. Direct libvirt

| Feature | Direct libvirt | JPlatform VM |
|---------|----------------|--------------|
| **Create VM** | `virsh define`, `virsh start` | `jplatform deploy vm.yaml` |
| **Resource Quotas** | Manual XML config | Automatic enforcement |
| **Monitoring** | virsh commands | Prometheus metrics |
| **Dependencies** | Manual | Automatic ordering |
| **Health Checks** | External scripts | Built-in |
| **REST API** | Custom | Unified API |
| **Unified Management** | VMs only | VMs + containers + apps |

## Examples

See `src/main/resources/examples/` for:
- `database-vm.yaml` - High-resource database VM
- `web-server-vm.yaml` - Lightweight web server VM

## Advanced Features (2.2+)

### Live Migration

Migrate running VMs between hosts with minimal downtime:

```java
import org.flossware.jplatform.vm.VmLauncher;

VmLauncher launcher = new VmLauncher();
VmLauncher.VmInfo vmInfo = launcher.launch("app-vm", descriptor);

// Migrate to another host
String destUri = "qemu+ssh://host2.example.com/system";
launcher.migrate("app-vm", vmInfo, destUri, 0);
```

**Use cases:**
- Load balancing across hosts
- Hardware maintenance without downtime
- Resource optimization

### VM Snapshots

Create point-in-time snapshots for backup and rollback:

```java
// Create snapshot
String snapshotName = launcher.createSnapshot(
    "app-vm", 
    vmInfo, 
    "before-upgrade", 
    "Snapshot before applying updates"
);

// List all snapshots
String[] snapshots = launcher.listSnapshots(vmInfo);
for (String snap : snapshots) {
    System.out.println("Snapshot: " + snap);
}

// Revert to snapshot
launcher.revertToSnapshot("app-vm", vmInfo, "before-upgrade");

// Delete snapshot
launcher.deleteSnapshot("app-vm", vmInfo, "before-upgrade");
```

**Use cases:**
- Backup before changes
- Testing and rollback
- Development environments

### Hot-Add Resources

Dynamically add CPU and memory to running VMs:

```java
// Add 2 more vCPUs
launcher.hotAddCpu("app-vm", vmInfo, 2);

// Add 4GB more RAM
launcher.hotAddMemory("app-vm", vmInfo, 4096);

// Resize both at once
launcher.resize("app-vm", vmInfo, 8, 16384);  // 8 vCPUs, 16GB RAM
```

**Use cases:**
- Scale up under load
- Adjust to workload demands
- Optimize resource allocation

### CLI Usage

```bash
# Live migration
jplatform migrate app-vm --destination qemu+ssh://host2/system

# Snapshots
jplatform snapshot create app-vm --name before-upgrade
jplatform snapshot list app-vm
jplatform snapshot revert app-vm --name before-upgrade
jplatform snapshot delete app-vm --name before-upgrade

# Hot-add resources
jplatform resize app-vm --vcpu 8 --memory 16384
```

## Feature Status

**Version 2.1:**
- ✅ VM creation and lifecycle (create, start, stop, destroy)
- ✅ Resource configuration (vCPU, memory, disk)
- ✅ Networking (bridge, NAT)
- ✅ VNC console access
- ✅ Resource monitoring

**Version 2.2:**
- ✅ Live migration
- ✅ VM snapshots (create, list, revert, delete)
- ✅ Hot-add resources (CPU, memory)

**Planned (2.3+):**
- ❌ Multi-host clustering
- ❌ GPU passthrough
- ❌ Storage live migration
- ❌ VM templates

## Troubleshooting

**"Cannot connect to libvirt"**
```bash
# Check libvirt is running
sudo systemctl status libvirtd

# Check permissions
sudo usermod -aG libvirt $USER
# Log out and back in
```

**"KVM not available"**
```bash
# Check KVM module
lsmod | grep kvm

# Load KVM module
sudo modprobe kvm_intel  # or kvm_amd for AMD
```

**"Disk image not found"**
```bash
# Check disk path exists
ls -lh /var/lib/jplatform/vms/

# Create if needed
qemu-img create -f qcow2 /var/lib/jplatform/vms/myvm.qcow2 50G
```

## License

MIT License - See LICENSE file for details.

## Contributing

See main JPlatform CONTRIBUTING.md for contribution guidelines.
