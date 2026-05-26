package org.flossware.jplatform.vm;

import org.flossware.jplatform.api.ApplicationDescriptor;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Launches and manages virtual machines via libvirt/KVM/QEMU.
 *
 * <p>Enables JPlatform to manage VMs alongside containers, Java apps, and native binaries,
 * providing unified orchestration across all workload types.</p>
 *
 * <p>VM Configuration via Properties:</p>
 * <pre>
 * vm.name = vm-name (optional, defaults to applicationId)
 * vm.vcpu = 4 (number of virtual CPUs)
 * vm.memory = 8192 (RAM in MB)
 * vm.disk = /path/to/disk.qcow2 (existing disk image)
 * vm.disk.size = 50G (for creating new disk)
 * vm.disk.format = qcow2|raw (default: qcow2)
 * vm.image = ubuntu-22.04 (cloud image to use)
 * vm.network = bridge|nat|none (default: bridge)
 * vm.bridge = virbr0 (bridge name for bridge network)
 * vm.vnc.enabled = true|false (enable VNC console)
 * vm.vnc.port = 5900 (VNC port, auto if not specified)
 * </pre>
 *
 * <p>Example Deployment:</p>
 * <pre>
 * ApplicationDescriptor vmDescriptor = ApplicationDescriptor.builder()
 *     .applicationId("database-vm")
 *     .name("PostgreSQL Database VM")
 *     .property("vm.vcpu", "8")
 *     .property("vm.memory", "32768")
 *     .property("vm.disk", "/var/lib/jplatform/vms/db.qcow2")
 *     .property("vm.network", "bridge")
 *     .dependency("storage-vm")  // VM dependencies work!
 *     .build();
 *
 * manager.deploy(vmDescriptor);
 * manager.start("database-vm");
 * </pre>
 *
 * @since 2.1
 * @author FlossWare
 */
public class VmLauncher {

    private static final Logger logger = LoggerFactory.getLogger(VmLauncher.class);

    private final Connect connection;

    /**
     * Creates a new VmLauncher connected to the local libvirt daemon.
     *
     * @throws LibvirtException if connection to libvirt fails
     */
    public VmLauncher() throws LibvirtException {
        this("qemu:///system");
    }

    /**
     * Creates a new VmLauncher with custom libvirt URI.
     *
     * @param libvirtUri the libvirt connection URI (e.g., "qemu:///system", "qemu+ssh://host/system")
     * @throws LibvirtException if connection to libvirt fails
     */
    public VmLauncher(String libvirtUri) throws LibvirtException {
        if (libvirtUri == null || libvirtUri.trim().isEmpty()) {
            throw new IllegalArgumentException("libvirtUri cannot be null or empty");
        }
        this.connection = new Connect(libvirtUri, false);
        logger.info("Connected to libvirt URI: {} (version: {})", libvirtUri, connection.getLibVirVersion());
    }

    /**
     * Launches a virtual machine.
     *
     * @param applicationId the application identifier
     * @param descriptor the application descriptor containing VM configuration
     * @return VmInfo containing domain and metadata
     * @throws LibvirtException if VM creation fails
     */
    public VmInfo launch(String applicationId, ApplicationDescriptor descriptor) throws LibvirtException {
        Map<String, String> properties = descriptor.getProperties();

        String vmName = properties.getOrDefault("vm.name", applicationId);

        int vcpu;
        try {
            vcpu = Integer.parseInt(properties.getOrDefault("vm.vcpu", "2"));
            if (vcpu <= 0) {
                throw new IllegalArgumentException("vm.vcpu must be positive, got: " + vcpu);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("vm.vcpu must be a valid integer, got: " +
                properties.get("vm.vcpu"), e);
        }

        int memoryMB;
        try {
            memoryMB = Integer.parseInt(properties.getOrDefault("vm.memory", "4096"));
            if (memoryMB <= 0) {
                throw new IllegalArgumentException("vm.memory must be positive, got: " + memoryMB);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("vm.memory must be a valid integer, got: " +
                properties.get("vm.memory"), e);
        }

        String diskPath = properties.get("vm.disk");
        String networkMode = properties.getOrDefault("vm.network", "bridge");

        logger.info("[{}] Creating VM: {} (vCPU: {}, RAM: {}MB, disk: {})",
            applicationId, vmName, vcpu, memoryMB, diskPath);

        // Validate configuration
        if (diskPath == null || diskPath.isEmpty()) {
            throw new IllegalArgumentException("vm.disk property is required");
        }

        // Build libvirt XML configuration
        String xmlConfig = buildVmXml(vmName, vcpu, memoryMB, diskPath, networkMode, properties);

        logger.debug("[{}] VM XML configuration:\n{}", applicationId, xmlConfig);

        // Create and start domain
        Domain domain = connection.domainDefineXML(xmlConfig);
        domain.create();

        int domainId = domain.getID();
        String uuid = domain.getUUIDString();

        logger.info("[{}] VM started successfully. Domain ID: {}, UUID: {}", applicationId, domainId, uuid);

        return new VmInfo(domain, vmName, vcpu, memoryMB, uuid);
    }

    /**
     * Stops a virtual machine.
     *
     * @param applicationId the application identifier (for logging)
     * @param vmInfo the VM information
     * @param graceful true for graceful shutdown (ACPI), false for force destroy
     * @throws LibvirtException if shutdown fails
     */
    public void stop(String applicationId, VmInfo vmInfo, boolean graceful) throws LibvirtException {
        Domain domain = vmInfo.getDomain();

        if (graceful) {
            logger.info("[{}] Gracefully shutting down VM: {} (ACPI shutdown)", applicationId, vmInfo.getName());
            domain.shutdown();
        } else {
            logger.info("[{}] Force destroying VM: {}", applicationId, vmInfo.getName());
            domain.destroy();
        }
    }

    /**
     * Pauses (suspends) a running VM.
     *
     * @param applicationId the application identifier
     * @param vmInfo the VM information
     * @throws LibvirtException if pause fails
     */
    public void pause(String applicationId, VmInfo vmInfo) throws LibvirtException {
        logger.info("[{}] Pausing VM: {}", applicationId, vmInfo.getName());
        vmInfo.getDomain().suspend();
    }

    /**
     * Resumes a paused VM.
     *
     * @param applicationId the application identifier
     * @param vmInfo the VM information
     * @throws LibvirtException if resume fails
     */
    public void resume(String applicationId, VmInfo vmInfo) throws LibvirtException {
        logger.info("[{}] Resuming VM: {}", applicationId, vmInfo.getName());
        vmInfo.getDomain().resume();
    }

    /**
     * Undefines (removes) a VM definition.
     *
     * @param applicationId the application identifier
     * @param vmInfo the VM information
     * @throws LibvirtException if undefine fails
     */
    public void undefine(String applicationId, VmInfo vmInfo) throws LibvirtException {
        logger.info("[{}] Undefining VM: {}", applicationId, vmInfo.getName());
        Domain domain = vmInfo.getDomain();

        // Stop if running
        if (domain.isActive() == 1) {
            logger.debug("[{}] VM is active, destroying before undefine", applicationId);
            domain.destroy();
        }

        // Undefine
        domain.undefine();
        logger.info("[{}] VM undefined successfully", applicationId);
    }

    /**
     * Gets VM resource usage statistics.
     *
     * @param vmInfo the VM information
     * @return VmStats with current resource usage
     * @throws LibvirtException if stats retrieval fails
     */
    public VmStats getStats(VmInfo vmInfo) throws LibvirtException {
        Domain domain = vmInfo.getDomain();

        // Get domain info (memory, CPU count, state)
        org.libvirt.DomainInfo info = domain.getInfo();

        long memoryKB = info.memory;
        long maxMemoryKB = info.maxMem;
        int nrVirtCpu = info.nrVirtCpu;
        long cpuTimeNs = info.cpuTime;

        // State
        org.libvirt.DomainInfo.DomainState state = info.state;
        String stateStr = state.toString();

        return new VmStats(
            memoryKB / 1024,           // Convert to MB
            maxMemoryKB / 1024,         // Convert to MB
            nrVirtCpu,
            cpuTimeNs,
            stateStr
        );
    }

    // ========== Advanced Features (2.2+) ==========

    /**
     * Live migrate VM to another host.
     * Migrates a running VM to another libvirt host with minimal downtime.
     *
     * @param applicationId the application identifier
     * @param vmInfo the VM information
     * @param destinationUri the destination libvirt URI (e.g., "qemu+ssh://host2/system")
     * @param flags migration flags (0 for live migration)
     * @throws LibvirtException if migration fails
     * @since 2.2
     */
    public void migrate(String applicationId, VmInfo vmInfo, String destinationUri, long flags)
            throws LibvirtException {
        logger.info("[{}] Starting live migration to {}", applicationId, destinationUri);

        Domain domain = vmInfo.getDomain();

        // Connect to destination
        Connect destConnection = new Connect(destinationUri, false);

        try {
            // Perform live migration
            // flags = 0 for default live migration
            // VIR_MIGRATE_LIVE = 1 for live migration
            Domain migratedDomain = domain.migrate(destConnection, flags, null, null, 0);

            logger.info("[{}] Migration completed successfully", applicationId);

            // Close migrated domain reference
            migratedDomain.free();
        } finally {
            destConnection.close();
        }
    }

    /**
     * Create a snapshot of the VM.
     * Captures the current state of the VM including memory and disk.
     *
     * @param applicationId the application identifier
     * @param vmInfo the VM information
     * @param snapshotName the name for the snapshot
     * @param description optional description
     * @return snapshot name
     * @throws LibvirtException if snapshot creation fails
     * @since 2.2
     */
    public String createSnapshot(String applicationId, VmInfo vmInfo, String snapshotName,
                                  String description) throws LibvirtException {
        logger.info("[{}] Creating snapshot: {}", applicationId, snapshotName);

        Domain domain = vmInfo.getDomain();

        // Build snapshot XML
        StringBuilder xml = new StringBuilder();
        xml.append("<domainsnapshot>\n");
        xml.append("  <name>").append(escapeXml(snapshotName)).append("</name>\n");
        if (description != null && !description.isEmpty()) {
            xml.append("  <description>").append(escapeXml(description)).append("</description>\n");
        }
        xml.append("</domainsnapshot>\n");

        // Create snapshot (0 = default flags)
        domain.snapshotCreateXML(xml.toString(), 0);

        logger.info("[{}] Snapshot created: {}", applicationId, snapshotName);
        return snapshotName;
    }

    /**
     * List all snapshots for a VM.
     *
     * @param vmInfo the VM information
     * @return array of snapshot names
     * @throws LibvirtException if listing fails
     * @since 2.2
     */
    public String[] listSnapshots(VmInfo vmInfo) throws LibvirtException {
        Domain domain = vmInfo.getDomain();
        return domain.snapshotListNames();
    }

    /**
     * Revert VM to a snapshot.
     * Restores the VM to the state captured in the snapshot.
     *
     * @param applicationId the application identifier
     * @param vmInfo the VM information
     * @param snapshotName the snapshot to revert to
     * @throws LibvirtException if revert fails
     * @since 2.2
     */
    public void revertToSnapshot(String applicationId, VmInfo vmInfo, String snapshotName)
            throws LibvirtException {
        logger.info("[{}] Reverting to snapshot: {}", applicationId, snapshotName);

        Domain domain = vmInfo.getDomain();

        // Lookup snapshot
        org.libvirt.DomainSnapshot snapshot = domain.snapshotLookupByName(snapshotName);

        // Revert to snapshot (0 = default flags)
        domain.revertToSnapshot(snapshot);

        logger.info("[{}] Reverted to snapshot: {}", applicationId, snapshotName);
    }

    /**
     * Delete a snapshot.
     *
     * @param applicationId the application identifier
     * @param vmInfo the VM information
     * @param snapshotName the snapshot to delete
     * @throws LibvirtException if deletion fails
     * @since 2.2
     */
    public void deleteSnapshot(String applicationId, VmInfo vmInfo, String snapshotName)
            throws LibvirtException {
        logger.info("[{}] Deleting snapshot: {}", applicationId, snapshotName);

        Domain domain = vmInfo.getDomain();

        // Lookup and delete snapshot
        org.libvirt.DomainSnapshot snapshot = domain.snapshotLookupByName(snapshotName);
        snapshot.delete(0);  // 0 = default flags

        logger.info("[{}] Snapshot deleted: {}", applicationId, snapshotName);
    }

    /**
     * Hot-add vCPUs to a running VM.
     * Dynamically increases the number of CPUs without stopping the VM.
     *
     * @param applicationId the application identifier
     * @param vmInfo the VM information
     * @param additionalCpus number of CPUs to add
     * @throws LibvirtException if hot-add fails
     * @since 2.2
     */
    public void hotAddCpu(String applicationId, VmInfo vmInfo, int additionalCpus)
            throws LibvirtException {
        if (additionalCpus <= 0) {
            throw new IllegalArgumentException("additionalCpus must be positive, got: " + additionalCpus);
        }
        logger.info("[{}] Hot-adding {} vCPUs", applicationId, additionalCpus);

        Domain domain = vmInfo.getDomain();

        // Get current vCPU count
        org.libvirt.DomainInfo info = domain.getInfo();
        int currentCpus = info.nrVirtCpu;
        int newCpuCount = currentCpus + additionalCpus;

        // Set new CPU count (live)
        // VIR_DOMAIN_AFFECT_LIVE = 1 for live change
        domain.setVcpus(newCpuCount);

        logger.info("[{}] vCPUs increased from {} to {}", applicationId, currentCpus, newCpuCount);
    }

    /**
     * Hot-add memory to a running VM.
     * Dynamically increases memory without stopping the VM.
     *
     * @param applicationId the application identifier
     * @param vmInfo the VM information
     * @param additionalMemoryMB memory to add in MB
     * @throws LibvirtException if hot-add fails
     * @since 2.2
     */
    public void hotAddMemory(String applicationId, VmInfo vmInfo, long additionalMemoryMB)
            throws LibvirtException {
        if (additionalMemoryMB <= 0) {
            throw new IllegalArgumentException("additionalMemoryMB must be positive, got: " + additionalMemoryMB);
        }
        logger.info("[{}] Hot-adding {} MB of memory", applicationId, additionalMemoryMB);

        Domain domain = vmInfo.getDomain();

        // Get current memory
        org.libvirt.DomainInfo info = domain.getInfo();
        long currentMemoryKB = info.memory;
        long currentMemoryMB = currentMemoryKB / 1024;

        long newMemoryMB = currentMemoryMB + additionalMemoryMB;
        long newMemoryKB = newMemoryMB * 1024;

        // Set new memory (live)
        domain.setMemory(newMemoryKB);

        logger.info("[{}] Memory increased from {} MB to {} MB",
                   applicationId, currentMemoryMB, newMemoryMB);
    }

    /**
     * Resize VM resources (CPU and/or memory).
     * Convenience method to adjust multiple resources at once.
     *
     * @param applicationId the application identifier
     * @param vmInfo the VM information
     * @param newVcpu new vCPU count (or -1 to keep current)
     * @param newMemoryMB new memory in MB (or -1 to keep current)
     * @throws LibvirtException if resize fails
     * @since 2.2
     */
    public void resize(String applicationId, VmInfo vmInfo, int newVcpu, long newMemoryMB)
            throws LibvirtException {
        logger.info("[{}] Resizing VM (vCPU: {}, Memory: {} MB)",
                   applicationId, newVcpu, newMemoryMB);

        Domain domain = vmInfo.getDomain();

        // Update vCPU if requested
        if (newVcpu > 0) {
            domain.setVcpus(newVcpu);
            logger.info("[{}] vCPUs set to {}", applicationId, newVcpu);
        }

        // Update memory if requested
        if (newMemoryMB > 0) {
            long newMemoryKB = newMemoryMB * 1024;
            domain.setMemory(newMemoryKB);
            logger.info("[{}] Memory set to {} MB", applicationId, newMemoryMB);
        }

        logger.info("[{}] VM resized successfully", applicationId);
    }

    /**
     * Builds libvirt XML configuration for the VM.
     *
     * @param name VM name
     * @param vcpu number of virtual CPUs
     * @param memoryMB memory in MB
     * @param diskPath path to disk image
     * @param networkMode network mode (bridge, nat, none)
     * @param properties additional VM properties
     * @return libvirt XML configuration string
     */
    private String buildVmXml(String name, int vcpu, int memoryMB, String diskPath,
                              String networkMode, Map<String, String> properties) {

        long memoryKB = memoryMB * 1024L;
        String diskFormat = properties.getOrDefault("vm.disk.format", "qcow2");

        StringBuilder xml = new StringBuilder();
        xml.append("<domain type='kvm'>\n");
        xml.append("  <name>").append(escapeXml(name)).append("</name>\n");
        xml.append("  <memory unit='KiB'>").append(memoryKB).append("</memory>\n");
        xml.append("  <currentMemory unit='KiB'>").append(memoryKB).append("</currentMemory>\n");
        xml.append("  <vcpu placement='static'>").append(vcpu).append("</vcpu>\n");

        // OS configuration
        xml.append("  <os>\n");
        xml.append("    <type arch='x86_64' machine='pc'>hvm</type>\n");
        xml.append("    <boot dev='hd'/>\n");
        xml.append("  </os>\n");

        // Features (ACPI, APIC for modern OS support)
        xml.append("  <features>\n");
        xml.append("    <acpi/>\n");
        xml.append("    <apic/>\n");
        xml.append("  </features>\n");

        // CPU mode (host-passthrough for best performance)
        xml.append("  <cpu mode='host-passthrough'/>\n");

        // Clock
        xml.append("  <clock offset='utc'>\n");
        xml.append("    <timer name='rtc' tickpolicy='catchup'/>\n");
        xml.append("    <timer name='pit' tickpolicy='delay'/>\n");
        xml.append("    <timer name='hpet' present='no'/>\n");
        xml.append("  </clock>\n");

        // Power management
        xml.append("  <on_poweroff>destroy</on_poweroff>\n");
        xml.append("  <on_reboot>restart</on_reboot>\n");
        xml.append("  <on_crash>destroy</on_crash>\n");

        // Devices
        xml.append("  <devices>\n");

        // Disk
        xml.append("    <disk type='file' device='disk'>\n");
        xml.append("      <driver name='qemu' type='").append(diskFormat).append("' cache='writeback'/>\n");
        xml.append("      <source file='").append(escapeXml(diskPath)).append("'/>\n");
        xml.append("      <target dev='vda' bus='virtio'/>\n");
        xml.append("    </disk>\n");

        // Network interface
        if (!"none".equals(networkMode)) {
            if ("bridge".equals(networkMode)) {
                String bridge = properties.getOrDefault("vm.bridge", "virbr0");
                xml.append("    <interface type='bridge'>\n");
                xml.append("      <source bridge='").append(escapeXml(bridge)).append("'/>\n");
                xml.append("      <model type='virtio'/>\n");
                xml.append("    </interface>\n");
            } else if ("nat".equals(networkMode)) {
                xml.append("    <interface type='network'>\n");
                xml.append("      <source network='default'/>\n");
                xml.append("      <model type='virtio'/>\n");
                xml.append("    </interface>\n");
            }
        }

        // Serial console
        xml.append("    <serial type='pty'>\n");
        xml.append("      <target type='isa-serial' port='0'/>\n");
        xml.append("    </serial>\n");
        xml.append("    <console type='pty'>\n");
        xml.append("      <target type='serial' port='0'/>\n");
        xml.append("    </console>\n");

        // VNC graphics (if enabled)
        if ("true".equals(properties.get("vm.vnc.enabled"))) {
            String vncPort = properties.getOrDefault("vm.vnc.port", "-1"); // -1 = auto
            xml.append("    <graphics type='vnc' port='").append(vncPort).append("' autoport='yes' listen='0.0.0.0'>\n");
            xml.append("      <listen type='address' address='0.0.0.0'/>\n");
            xml.append("    </graphics>\n");

            // Video device for VNC
            xml.append("    <video>\n");
            xml.append("      <model type='vga' vram='16384' heads='1'/>\n");
            xml.append("    </video>\n");
        }

        xml.append("  </devices>\n");
        xml.append("</domain>\n");

        return xml.toString();
    }

    /**
     * Escapes XML special characters.
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    /**
     * Closes the libvirt connection.
     *
     * @throws LibvirtException if close fails
     */
    public void close() throws LibvirtException {
        if (connection != null) {
            logger.info("Closing libvirt connection");
            connection.close();
        }
    }

    /**
     * Container for VM information.
     */
    public static class VmInfo {
        private final Domain domain;
        private final String name;
        private final int vcpu;
        private final int memoryMB;
        private final String uuid;

        public VmInfo(Domain domain, String name, int vcpu, int memoryMB, String uuid) {
            this.domain = domain;
            this.name = name;
            this.vcpu = vcpu;
            this.memoryMB = memoryMB;
            this.uuid = uuid;
        }

        public Domain getDomain() { return domain; }
        public String getName() { return name; }
        public int getVcpu() { return vcpu; }
        public int getMemoryMB() { return memoryMB; }
        public String getUuid() { return uuid; }
    }

    /**
     * Container for VM statistics.
     */
    public static class VmStats {
        private final long memoryMB;
        private final long maxMemoryMB;
        private final int vcpu;
        private final long cpuTimeNs;
        private final String state;

        public VmStats(long memoryMB, long maxMemoryMB, int vcpu, long cpuTimeNs, String state) {
            this.memoryMB = memoryMB;
            this.maxMemoryMB = maxMemoryMB;
            this.vcpu = vcpu;
            this.cpuTimeNs = cpuTimeNs;
            this.state = state;
        }

        public long getMemoryMB() { return memoryMB; }
        public long getMaxMemoryMB() { return maxMemoryMB; }
        public int getVcpu() { return vcpu; }
        public long getCpuTimeNs() { return cpuTimeNs; }
        public String getState() { return state; }

        public double getCpuTimeSeconds() {
            return cpuTimeNs / 1_000_000_000.0;
        }
    }
}
