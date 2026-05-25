# JPlatform Troubleshooting Guide

This guide helps diagnose and resolve common issues with JPlatform workloads.

## Table of Contents

- [General Issues](#general-issues)
- [Virtual Machine Issues](#virtual-machine-issues)
- [Container Issues](#container-issues)
- [Java Application Issues](#java-application-issues)
- [Native Binary Issues](#native-binary-issues)
- [Dependency Issues](#dependency-issues)
- [Resource Issues](#resource-issues)
- [Networking Issues](#networking-issues)
- [Monitoring & Debugging](#monitoring--debugging)

---

## General Issues

### JPlatform Command Not Found

**Symptom:**
```
bash: jplatform: command not found
```

**Diagnosis:**
```bash
which jplatform
echo $PATH
```

**Solution:**
```bash
# If installed but not in PATH
export PATH=$PATH:/usr/local/bin

# If not installed
cd jplatform-launcher
mvn clean package
sudo cp target/jplatform-launcher-*.jar /usr/local/lib/jplatform/

# Create wrapper script
sudo tee /usr/local/bin/jplatform > /dev/null << 'SCRIPT'
#!/bin/sh
java -jar /usr/local/lib/jplatform/jplatform-launcher.jar "$@"
SCRIPT
sudo chmod +x /usr/local/bin/jplatform
```

### Workload Stuck in STARTING State

**Symptom:**
```
ID: my-app
State: STARTING (for > 5 minutes)
```

**Diagnosis:**
```bash
# Check logs
jplatform logs my-app

# Check dependencies
jplatform dependencies my-app

# Check resource availability
jplatform metrics my-app
```

**Common Causes:**
1. Waiting for dependencies that failed to start
2. Port already in use
3. Missing configuration files
4. Insufficient resources

**Solution:**
```bash
# Check dependency status
jplatform status

# If dependency failed, check its logs
jplatform logs <dependency-id>

# Force stop and retry
jplatform stop my-app
jplatform start my-app
```

### Permission Denied Errors

**Symptom:**
```
ERROR: Permission denied accessing /var/lib/jplatform
```

**Solution:**
```bash
# Check ownership
ls -la /var/lib/jplatform

# Fix permissions
sudo chown -R $(whoami):$(whoami) /var/lib/jplatform
sudo chmod -R 755 /var/lib/jplatform

# For VMs specifically
sudo usermod -aG libvirt $(whoami)
# Log out and back in for group changes
```

---

## Virtual Machine Issues

### Cannot Connect to libvirt

**Symptom:**
```
ERROR: VM management not available (libvirt not accessible)
```

**Diagnosis:**
```bash
# Check libvirt service
systemctl status libvirtd

# Test connection
virsh list --all

# Check socket permissions
ls -la /var/run/libvirt/libvirt-sock
```

**Solution:**
```bash
# Start libvirt
sudo systemctl start libvirtd
sudo systemctl enable libvirtd

# Add user to libvirt group
sudo usermod -aG libvirt $(whoami)

# Restart session (or log out/in)
newgrp libvirt

# Verify
virsh list --all
```

### KVM Not Available

**Symptom:**
```
ERROR: KVM module not loaded
```

**Diagnosis:**
```bash
# Check KVM module
lsmod | grep kvm

# Check CPU virtualization support
egrep -c '(vmx|svm)' /proc/cpuinfo
# Should return > 0

# Check if enabled in BIOS
cat /proc/cpuinfo | grep -E 'vmx|svm'
```

**Solution:**
```bash
# Load KVM module
sudo modprobe kvm_intel  # For Intel CPUs
# OR
sudo modprobe kvm_amd    # For AMD CPUs

# Make permanent
echo "kvm_intel" | sudo tee -a /etc/modules  # Intel
# OR
echo "kvm_amd" | sudo tee -a /etc/modules    # AMD

# If CPU doesn't support virtualization
# - Enable VT-x/AMD-V in BIOS
# - Use containers instead of VMs
```

### VM Disk Image Not Found

**Symptom:**
```
ERROR: vm.disk property is required
ERROR: Disk image not found: /var/lib/jplatform/vms/my-vm.qcow2
```

**Solution:**
```bash
# Create directory
sudo mkdir -p /var/lib/jplatform/vms

# Create new disk image
sudo qemu-img create -f qcow2 /var/lib/jplatform/vms/my-vm.qcow2 50G

# OR download cloud image
wget https://cloud-images.ubuntu.com/releases/22.04/release/ubuntu-22.04-server-cloudimg-amd64.img
sudo mv ubuntu-22.04-server-cloudimg-amd64.img /var/lib/jplatform/vms/my-vm.qcow2

# Set permissions
sudo chown libvirt-qemu:kvm /var/lib/jplatform/vms/my-vm.qcow2
sudo chmod 660 /var/lib/jplatform/vms/my-vm.qcow2
```

### VM Won't Start

**Diagnosis:**
```bash
# Check VM status in libvirt
virsh list --all

# Get VM details
virsh dominfo <vm-name>

# Check libvirt logs
sudo journalctl -u libvirtd -n 50

# Check for resource conflicts
virsh list --all | grep running
```

**Common Causes:**
1. Insufficient host resources
2. Port conflicts
3. Disk image corrupted
4. Network bridge missing

**Solution:**
```bash
# Force stop any conflicting VMs
virsh destroy <conflicting-vm>

# Check disk image
qemu-img check /var/lib/jplatform/vms/my-vm.qcow2

# Recreate VM definition
virsh undefine <vm-name>
jplatform deploy my-vm.yaml

# Restart libvirt
sudo systemctl restart libvirtd
```

### VNC Connection Refused

**Symptom:**
```
vncviewer: unable to connect to localhost:5900
```

**Diagnosis:**
```bash
# Check VNC port
virsh vncdisplay <vm-name>

# Check if port is listening
netstat -tlnp | grep 5900

# Check firewall
sudo iptables -L -n | grep 5900
```

**Solution:**
```bash
# Ensure VNC is enabled in descriptor
cat my-vm.yaml | grep vnc
# Should show:
#   vm.vnc.enabled: "true"
#   vm.vnc.port: "5900"

# Redeploy with VNC enabled
jplatform undeploy my-vm
jplatform deploy my-vm.yaml

# Allow VNC through firewall
sudo firewall-cmd --add-port=5900/tcp --permanent
sudo firewall-cmd --reload
```

---

## Container Issues

### Docker/Podman Not Found

**Symptom:**
```
ERROR: Container runtime not found
```

**Diagnosis:**
```bash
which docker
which podman
systemctl status docker
```

**Solution:**
```bash
# Install Docker
sudo apt install docker.io
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker $(whoami)

# OR install Podman
sudo apt install podman

# Verify
docker --version  # or podman --version
```

### Image Pull Failed

**Symptom:**
```
ERROR: Failed to pull image nginx:alpine
```

**Diagnosis:**
```bash
# Test manually
docker pull nginx:alpine

# Check network
ping docker.io

# Check Docker Hub login
docker login
```

**Solution:**
```bash
# Pull image manually first
docker pull nginx:alpine

# Check image name is correct
# Correct: nginx:alpine
# Wrong: nginx-alpine

# If private registry, login first
docker login registry.example.com
```

### Port Already in Use

**Symptom:**
```
ERROR: Bind for 0.0.0.0:8080 failed: port is already allocated
```

**Diagnosis:**
```bash
# Find what's using the port
sudo lsof -i :8080
sudo netstat -tlnp | grep 8080

# Check other containers
docker ps | grep 8080
```

**Solution:**
```bash
# Stop conflicting service
sudo systemctl stop <service>

# OR use different port in descriptor
properties:
  container.ports: "8081:80"  # Use 8081 instead

# OR stop conflicting container
docker stop <container-id>
```

### Container Exits Immediately

**Symptom:**
```
Container started but immediately exits (code 137, 1, etc.)
```

**Diagnosis:**
```bash
# Check container logs
docker logs <container-id>

# Check exit code
docker ps -a | grep <container-name>

# Inspect container
docker inspect <container-id>
```

**Common Exit Codes:**
- `0`: Normal exit
- `1`: Application error
- `137`: Killed (OOM or manual kill)
- `139`: Segmentation fault
- `143`: Terminated (SIGTERM)

**Solution:**
```bash
# For exit code 137 (OOM killed)
# Increase memory in descriptor
resources:
  memory: 4096  # Increase from 2048

# For exit code 1 (app error)
# Check application logs
docker logs <container-id>

# Run container interactively for debugging
docker run -it --entrypoint /bin/sh <image>
```

---

## Java Application Issues

### ClassNotFoundException

**Symptom:**
```
ERROR: java.lang.ClassNotFoundException: com.example.Main
```

**Diagnosis:**
```bash
# Check JAR file exists
ls -la /var/lib/jplatform/apps/my-app/

# Inspect JAR
jar tf /var/lib/jplatform/apps/my-app/app.jar | grep Main

# Check classpath in descriptor
cat my-app.yaml | grep classpath
```

**Solution:**
```bash
# Ensure mainClass matches JAR
mainClass: "com.example.Main"  # Must match package.Class

# Verify JAR contains class
jar tf app.jar | grep com/example/Main.class

# Check classpath includes JAR
classpath:
  - "/var/lib/jplatform/apps/my-app/app.jar"
```

### OutOfMemoryError

**Symptom:**
```
ERROR: java.lang.OutOfMemoryError: Java heap space
```

**Diagnosis:**
```bash
# Check current heap usage
jplatform metrics my-app | grep heap

# Check configured limit
cat my-app.yaml | grep maxHeapMB
```

**Solution:**
```bash
# Increase heap in descriptor
resources:
  maxHeapMB: 8192  # Increase from 4096

# Redeploy
jplatform undeploy my-app
jplatform deploy my-app.yaml
jplatform start my-app
```

### Port Binding Failed

**Symptom:**
```
ERROR: Address already in use (Bind failed)
```

**Solution:**
```bash
# Find process using port
sudo lsof -i :8080

# Kill process or use different port
# Update descriptor
properties:
  server.port: "8081"
```

### Application Hangs on Shutdown

**Symptom:**
```
Application stuck in STOPPING state
```

**Diagnosis:**
```bash
# Check thread dump
jstack <pid>

# Check for non-daemon threads
jplatform logs my-app | grep "non-daemon"
```

**Solution:**
```bash
# Force kill
jplatform stop my-app --force

# Fix application code
# - Use daemon threads
# - Implement proper shutdown hooks
# - Close resources in finally blocks
```

---

## Native Binary Issues

### Executable Not Found

**Symptom:**
```
ERROR: Cannot execute: /path/to/binary: No such file or directory
```

**Solution:**
```bash
# Check file exists
ls -la /var/lib/jplatform/apps/my-binary/

# Make executable
chmod +x /var/lib/jplatform/apps/my-binary/app

# Check file path in descriptor
cat my-binary.yaml | grep nativeImage
```

### Missing Shared Libraries

**Symptom:**
```
ERROR: error while loading shared libraries: libssl.so.1.1
```

**Diagnosis:**
```bash
# Check library dependencies
ldd /path/to/binary

# Find missing libraries
ldconfig -p | grep libssl
```

**Solution:**
```bash
# Install missing library
sudo apt install libssl1.1

# OR build with static linking
# GraalVM native-image --static

# OR set LD_LIBRARY_PATH in descriptor
properties:
  native.env: "LD_LIBRARY_PATH=/usr/local/lib"
```

---

## Dependency Issues

### Circular Dependencies Detected

**Symptom:**
```
ERROR: Circular dependency detected: A → B → C → A
```

**Diagnosis:**
```bash
# View dependency graph
jplatform startup-order
jplatform dependencies A
jplatform dependencies B
jplatform dependencies C
```

**Solution:**
```bash
# Break the cycle
# Remove one dependency to create DAG
# Example: Remove C → A dependency

# Update descriptor
cat > C.yaml << EOF
dependencies:
  - B  # Remove dependency on A
