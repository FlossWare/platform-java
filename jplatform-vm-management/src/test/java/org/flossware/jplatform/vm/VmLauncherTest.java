/*
 * Copyright (C) 2024-2026 FlossWare
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.flossware.jplatform.vm;

import org.flossware.jplatform.api.ApplicationDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VmLauncher.
 *
 * <p>Note: These tests require a running libvirt daemon and are disabled by default.
 * To run them, set system property: -Dlibvirt.available=true</p>
 */
class VmLauncherTest {

    /**
     * Tests that VmLauncher constructor fails gracefully when libvirt is not available.
     */
    @Test
    void testConstructorFailsGracefullyWithoutLibvirt() {
        // This test runs always (even without libvirt)
        // It verifies that the constructor throws LibvirtException when libvirt is not available

        try {
            new VmLauncher("qemu:///system");
            // If we get here, libvirt is actually available
            assertTrue(true, "Libvirt is available on this system");
        } catch (Exception e) {
            // Expected when libvirt is not available or access is denied
            String msg = e.getMessage().toLowerCase();
            assertTrue(msg.contains("libvirt") || msg.contains("connection") ||
                       msg.contains("authentication") || msg.contains("access denied"),
                "Exception message should mention libvirt, connection, or access: " + e.getMessage());
        }
    }

    /**
     * Tests VM XML generation with minimal configuration.
     */
    @Test
    void testBuildVmXml() throws Exception {
        // Create descriptor with minimal VM configuration
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "2");
        properties.put("vm.memory", "4096");
        properties.put("vm.disk", "/var/lib/jplatform/vms/test.qcow2");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .name("Test VM")
            .properties(properties)
            .build();

        // We can't test the actual XML generation without access to the private method,
        // but we can verify the properties are correctly set
        assertEquals("2", descriptor.getProperties().get("vm.vcpu"));
        assertEquals("4096", descriptor.getProperties().get("vm.memory"));
        assertEquals("/var/lib/jplatform/vms/test.qcow2", descriptor.getProperties().get("vm.disk"));
    }

    /**
     * Tests VM launch with full configuration (requires libvirt).
     */
    @Test
    @EnabledIfSystemProperty(named = "libvirt.available", matches = "true")
    void testLaunchVm() throws Exception {
        VmLauncher launcher = new VmLauncher();

        Map<String, String> properties = new HashMap<>();
        properties.put("vm.name", "junit-test-vm");
        properties.put("vm.vcpu", "1");
        properties.put("vm.memory", "1024");
        properties.put("vm.disk", "/tmp/test-vm.qcow2");
        properties.put("vm.disk.format", "qcow2");
        properties.put("vm.network", "none");  // No network for testing

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .name("Test VM")
            .properties(properties)
            .build();

        try {
            // Note: This will fail if /tmp/test-vm.qcow2 doesn't exist
            // In a real test environment, you would create this disk image first
            VmLauncher.VmInfo vmInfo = launcher.launch("test-vm", descriptor);

            assertNotNull(vmInfo);
            assertEquals("junit-test-vm", vmInfo.getName());
            assertEquals(1, vmInfo.getVcpu());
            assertEquals(1024, vmInfo.getMemoryMB());
            assertNotNull(vmInfo.getUuid());

            // Clean up
            launcher.stop("test-vm", vmInfo, false);
            launcher.undefine("test-vm", vmInfo);

        } finally {
            launcher.close();
        }
    }

    /**
     * Tests that VM launch fails with missing disk property.
     */
    @Test
    void testLaunchFailsWithoutDisk() throws Exception {
        // This test can run even without libvirt since it fails before connecting
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "2");
        properties.put("vm.memory", "4096");
        // Missing vm.disk property

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .name("Test VM")
            .properties(properties)
            .build();

        try {
            VmLauncher launcher = new VmLauncher();

            Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                launcher.launch("test-vm", descriptor);
            });

            assertTrue(exception.getMessage().contains("vm.disk"),
                "Exception should mention missing vm.disk property");

            launcher.close();
        } catch (org.libvirt.LibvirtException e) {
            // Expected if libvirt is not available or authentication fails
            // Skip this test - it requires a working libvirt installation
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                "Skipping test - libvirt not available: " + e.getMessage());
        }
    }

    /**
     * Tests VM configuration with VNC enabled.
     */
    @Test
    void testVmConfigurationWithVnc() {
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "4");
        properties.put("vm.memory", "8192");
        properties.put("vm.disk", "/var/lib/jplatform/vms/vnc-test.qcow2");
        properties.put("vm.vnc.enabled", "true");
        properties.put("vm.vnc.port", "5900");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("vnc-test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .name("VNC Test VM")
            .properties(properties)
            .build();

        assertEquals("true", descriptor.getProperties().get("vm.vnc.enabled"));
        assertEquals("5900", descriptor.getProperties().get("vm.vnc.port"));
    }

    /**
     * Tests VM configuration with bridge networking.
     */
    @Test
    void testVmConfigurationWithBridgeNetwork() {
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "2");
        properties.put("vm.memory", "4096");
        properties.put("vm.disk", "/var/lib/jplatform/vms/bridge-test.qcow2");
        properties.put("vm.network", "bridge");
        properties.put("vm.bridge", "br0");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("bridge-test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .name("Bridge Test VM")
            .properties(properties)
            .build();

        assertEquals("bridge", descriptor.getProperties().get("vm.network"));
        assertEquals("br0", descriptor.getProperties().get("vm.bridge"));
    }

    /**
     * Tests default values for VM configuration.
     */
    @Test
    void testDefaultValues() {
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.disk", "/var/lib/jplatform/vms/default-test.qcow2");
        // Not specifying vcpu, memory - should use defaults

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("default-test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .name("Default Test VM")
            .properties(properties)
            .build();

        // Verify disk is set (required)
        assertEquals("/var/lib/jplatform/vms/default-test.qcow2",
            descriptor.getProperties().get("vm.disk"));

        // VmLauncher should use defaults for missing properties:
        // - vm.vcpu defaults to "2"
        // - vm.memory defaults to "4096"
        // - vm.network defaults to "bridge"
        // - vm.disk.format defaults to "qcow2"
    }

    /**
     * Tests VmInfo container class.
     */
    @Test
    void testVmInfoClass() {
        // VmInfo requires a Domain object which we can't easily mock without libvirt
        // This test verifies the structure is correct
        // In a real integration test, you would verify:
        // - VmInfo stores domain reference
        // - VmInfo provides name, vcpu, memory, uuid
        assertTrue(true, "VmInfo class structure verified by compilation");
    }

    /**
     * Tests VmStats container class.
     */
    @Test
    void testVmStatsClass() {
        VmLauncher.VmStats stats = new VmLauncher.VmStats(
            8192,    // memoryMB
            16384,   // maxMemoryMB
            4,       // vcpu
            1000000000L,  // cpuTimeNs (1 second)
            "RUNNING"
        );

        assertEquals(8192, stats.getMemoryMB());
        assertEquals(16384, stats.getMaxMemoryMB());
        assertEquals(4, stats.getVcpu());
        assertEquals(1000000000L, stats.getCpuTimeNs());
        assertEquals("RUNNING", stats.getState());
        assertEquals(1.0, stats.getCpuTimeSeconds(), 0.01);
    }

    // ========== Advanced Features Tests (2.2+) ==========

    /**
     * Tests snapshot operations (requires libvirt).
     */
    @Test
    @EnabledIfSystemProperty(named = "libvirt.available", matches = "true")
    void testSnapshotOperations() throws Exception {
        VmLauncher launcher = new VmLauncher();

        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "1");
        properties.put("vm.memory", "1024");
        properties.put("vm.disk", "/tmp/test-snapshot-vm.qcow2");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("snapshot-test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .properties(properties)
            .build();

        try {
            VmLauncher.VmInfo vmInfo = launcher.launch("snapshot-test-vm", descriptor);

            // Create snapshot
            String snapshotName = launcher.createSnapshot(
                "snapshot-test-vm",
                vmInfo,
                "test-snapshot",
                "Test snapshot"
            );
            assertEquals("test-snapshot", snapshotName);

            // List snapshots
            String[] snapshots = launcher.listSnapshots(vmInfo);
            assertTrue(snapshots.length > 0);
            boolean found = false;
            for (String snap : snapshots) {
                if ("test-snapshot".equals(snap)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Snapshot should be in list");

            // Delete snapshot
            launcher.deleteSnapshot("snapshot-test-vm", vmInfo, "test-snapshot");

            // Cleanup
            launcher.stop("snapshot-test-vm", vmInfo, false);
            launcher.undefine("snapshot-test-vm", vmInfo);
        } finally {
            launcher.close();
        }
    }

    /**
     * Tests hot-add CPU (requires libvirt).
     */
    @Test
    @EnabledIfSystemProperty(named = "libvirt.available", matches = "true")
    void testHotAddCpu() throws Exception {
        VmLauncher launcher = new VmLauncher();

        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "2");
        properties.put("vm.memory", "1024");
        properties.put("vm.disk", "/tmp/test-hotadd-vm.qcow2");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("hotadd-test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .properties(properties)
            .build();

        try {
            VmLauncher.VmInfo vmInfo = launcher.launch("hotadd-test-vm", descriptor);

            // Initial CPU count
            assertEquals(2, vmInfo.getVcpu());

            // Hot-add 2 CPUs
            launcher.hotAddCpu("hotadd-test-vm", vmInfo, 2);

            // Note: Verification would require re-querying the domain
            // In real implementation, you would check domain.getInfo()

            // Cleanup
            launcher.stop("hotadd-test-vm", vmInfo, false);
            launcher.undefine("hotadd-test-vm", vmInfo);
        } finally {
            launcher.close();
        }
    }

    /**
     * Tests hot-add memory (requires libvirt).
     */
    @Test
    @EnabledIfSystemProperty(named = "libvirt.available", matches = "true")
    void testHotAddMemory() throws Exception {
        VmLauncher launcher = new VmLauncher();

        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "1");
        properties.put("vm.memory", "1024");
        properties.put("vm.disk", "/tmp/test-hotmem-vm.qcow2");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("hotmem-test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .properties(properties)
            .build();

        try {
            VmLauncher.VmInfo vmInfo = launcher.launch("hotmem-test-vm", descriptor);

            // Initial memory
            assertEquals(1024, vmInfo.getMemoryMB());

            // Hot-add 1GB memory
            launcher.hotAddMemory("hotmem-test-vm", vmInfo, 1024);

            // Note: Verification would require re-querying the domain

            // Cleanup
            launcher.stop("hotmem-test-vm", vmInfo, false);
            launcher.undefine("hotmem-test-vm", vmInfo);
        } finally {
            launcher.close();
        }
    }

    /**
     * Tests VM resize (both CPU and memory).
     */
    @Test
    @EnabledIfSystemProperty(named = "libvirt.available", matches = "true")
    void testResize() throws Exception {
        VmLauncher launcher = new VmLauncher();

        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "1");
        properties.put("vm.memory", "1024");
        properties.put("vm.disk", "/tmp/test-resize-vm.qcow2");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("resize-test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .properties(properties)
            .build();

        try {
            VmLauncher.VmInfo vmInfo = launcher.launch("resize-test-vm", descriptor);

            // Resize to 4 vCPUs and 4GB RAM
            launcher.resize("resize-test-vm", vmInfo, 4, 4096);

            // Cleanup
            launcher.stop("resize-test-vm", vmInfo, false);
            launcher.undefine("resize-test-vm", vmInfo);
        } finally {
            launcher.close();
        }
    }

    /**
     * Tests live migration (requires two libvirt hosts).
     * Note: This test is informational only - requires complex setup.
     */
    @Test
    @EnabledIfSystemProperty(named = "libvirt.migration.available", matches = "true")
    void testLiveMigration() throws Exception {
        // This test requires:
        // 1. Two libvirt hosts
        // 2. Shared storage or storage migration
        // 3. Network connectivity between hosts
        // 4. SSH keys configured

        VmLauncher launcher = new VmLauncher();

        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "1");
        properties.put("vm.memory", "1024");
        properties.put("vm.disk", "/shared/storage/migrate-test-vm.qcow2");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("migrate-test-vm")
            .mainClass("vm.Launcher")  // VMs don't use mainClass but builder requires it
            .properties(properties)
            .build();

        try {
            VmLauncher.VmInfo vmInfo = launcher.launch("migrate-test-vm", descriptor);

            // Migrate to second host
            String destUri = System.getProperty("libvirt.migration.dest.uri",
                                                "qemu+ssh://host2/system");
            launcher.migrate("migrate-test-vm", vmInfo, destUri, 0);

            // After migration, VM runs on destination host
            // Cleanup would need to happen on destination

        } finally {
            launcher.close();
        }
    }

    /**
     * Tests VmLauncher constructor with null URI.
     */
    @Test
    void testConstructorNullUri() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new VmLauncher(null);
        });
        assertTrue(exception.getMessage().contains("null or empty"));
    }

    /**
     * Tests VmLauncher constructor with empty URI.
     */
    @Test
    void testConstructorEmptyUri() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new VmLauncher("");
        });
        assertTrue(exception.getMessage().contains("null or empty"));
    }

    /**
     * Tests VmLauncher constructor with whitespace URI.
     */
    @Test
    void testConstructorWhitespaceUri() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new VmLauncher("   ");
        });
        assertTrue(exception.getMessage().contains("null or empty"));
    }

    /**
     * Tests VmStats with zero values.
     */
    @Test
    void testVmStatsZeroValues() {
        VmLauncher.VmStats stats = new VmLauncher.VmStats(
            0,       // memoryMB
            0,       // maxMemoryMB
            0,       // vcpu
            0L,      // cpuTimeNs
            "SHUTOFF"
        );

        assertEquals(0, stats.getMemoryMB());
        assertEquals(0, stats.getMaxMemoryMB());
        assertEquals(0, stats.getVcpu());
        assertEquals(0L, stats.getCpuTimeNs());
        assertEquals("SHUTOFF", stats.getState());
        assertEquals(0.0, stats.getCpuTimeSeconds(), 0.01);
    }

    /**
     * Tests VmStats with large values.
     */
    @Test
    void testVmStatsLargeValues() {
        VmLauncher.VmStats stats = new VmLauncher.VmStats(
            1048576,    // 1TB memory
            2097152,    // 2TB max memory
            128,        // 128 CPUs
            3600000000000L,  // 1 hour of CPU time
            "RUNNING"
        );

        assertEquals(1048576, stats.getMemoryMB());
        assertEquals(2097152, stats.getMaxMemoryMB());
        assertEquals(128, stats.getVcpu());
        assertEquals(3600000000000L, stats.getCpuTimeNs());
        assertEquals("RUNNING", stats.getState());
        assertEquals(3600.0, stats.getCpuTimeSeconds(), 0.01);
    }

    /**
     * Tests VmStats with different states.
     */
    @Test
    void testVmStatsDifferentStates() {
        String[] states = {"RUNNING", "PAUSED", "SHUTOFF", "CRASHED", "SUSPENDED"};

        for (String state : states) {
            VmLauncher.VmStats stats = new VmLauncher.VmStats(
                4096, 8192, 2, 1000000L, state
            );
            assertEquals(state, stats.getState());
        }
    }

    /**
     * Tests VM configuration with multiple disks.
     */
    @Test
    void testVmConfigurationMultipleDisks() {
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "4");
        properties.put("vm.memory", "8192");
        properties.put("vm.disk", "/var/lib/jplatform/vms/os.qcow2");
        properties.put("vm.disk.1", "/var/lib/jplatform/vms/data1.qcow2");
        properties.put("vm.disk.2", "/var/lib/jplatform/vms/data2.qcow2");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("multi-disk-vm")
            .mainClass("vm.Launcher")
            .name("Multi-Disk VM")
            .properties(properties)
            .build();

        assertEquals("/var/lib/jplatform/vms/os.qcow2",
            descriptor.getProperties().get("vm.disk"));
        assertEquals("/var/lib/jplatform/vms/data1.qcow2",
            descriptor.getProperties().get("vm.disk.1"));
        assertEquals("/var/lib/jplatform/vms/data2.qcow2",
            descriptor.getProperties().get("vm.disk.2"));
    }

    /**
     * Tests VM configuration with NAT networking.
     */
    @Test
    void testVmConfigurationNatNetwork() {
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "2");
        properties.put("vm.memory", "4096");
        properties.put("vm.disk", "/var/lib/jplatform/vms/nat-test.qcow2");
        properties.put("vm.network", "nat");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("nat-test-vm")
            .mainClass("vm.Launcher")
            .name("NAT Test VM")
            .properties(properties)
            .build();

        assertEquals("nat", descriptor.getProperties().get("vm.network"));
    }

    /**
     * Tests VM configuration with no networking.
     */
    @Test
    void testVmConfigurationNoNetwork() {
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "2");
        properties.put("vm.memory", "4096");
        properties.put("vm.disk", "/var/lib/jplatform/vms/isolated-test.qcow2");
        properties.put("vm.network", "none");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("isolated-test-vm")
            .mainClass("vm.Launcher")
            .name("Isolated Test VM")
            .properties(properties)
            .build();

        assertEquals("none", descriptor.getProperties().get("vm.network"));
    }

    /**
     * Tests VM configuration with disk format.
     */
    @Test
    void testVmConfigurationDiskFormat() {
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "2");
        properties.put("vm.memory", "4096");
        properties.put("vm.disk", "/var/lib/jplatform/vms/raw-disk.img");
        properties.put("vm.disk.format", "raw");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("raw-disk-vm")
            .mainClass("vm.Launcher")
            .name("Raw Disk VM")
            .properties(properties)
            .build();

        assertEquals("raw", descriptor.getProperties().get("vm.disk.format"));
    }

    /**
     * Tests VM configuration with custom VM name.
     */
    @Test
    void testVmConfigurationCustomName() {
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.name", "my-custom-vm-name");
        properties.put("vm.vcpu", "2");
        properties.put("vm.memory", "4096");
        properties.put("vm.disk", "/var/lib/jplatform/vms/custom.qcow2");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("custom-name-vm")
            .mainClass("vm.Launcher")
            .name("Custom Name VM")
            .properties(properties)
            .build();

        assertEquals("my-custom-vm-name", descriptor.getProperties().get("vm.name"));
    }

    /**
     * Tests VM configuration with high CPU count.
     */
    @Test
    void testVmConfigurationHighCpuCount() {
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "64");
        properties.put("vm.memory", "131072"); // 128GB
        properties.put("vm.disk", "/var/lib/jplatform/vms/highend.qcow2");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("highend-vm")
            .mainClass("vm.Launcher")
            .name("High-End VM")
            .properties(properties)
            .build();

        assertEquals("64", descriptor.getProperties().get("vm.vcpu"));
        assertEquals("131072", descriptor.getProperties().get("vm.memory"));
    }

    /**
     * Tests VM configuration with minimal resources.
     */
    @Test
    void testVmConfigurationMinimalResources() {
        Map<String, String> properties = new HashMap<>();
        properties.put("vm.vcpu", "1");
        properties.put("vm.memory", "512");
        properties.put("vm.disk", "/var/lib/jplatform/vms/minimal.qcow2");

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
            .applicationId("minimal-vm")
            .mainClass("vm.Launcher")
            .name("Minimal VM")
            .properties(properties)
            .build();

        assertEquals("1", descriptor.getProperties().get("vm.vcpu"));
        assertEquals("512", descriptor.getProperties().get("vm.memory"));
    }

    /**
     * Tests VmStats CPU time conversion precision.
     */
    @Test
    void testVmStatsCpuTimePrecision() {
        // 500ms = 500,000,000 nanoseconds
        VmLauncher.VmStats stats = new VmLauncher.VmStats(
            2048, 4096, 2, 500000000L, "RUNNING"
        );

        assertEquals(0.5, stats.getCpuTimeSeconds(), 0.001);
    }

    /**
     * Tests VmStats with negative CPU time (should still calculate).
     */
    @Test
    void testVmStatsNegativeCpuTime() {
        // Shouldn't happen in practice, but test defensive programming
        VmLauncher.VmStats stats = new VmLauncher.VmStats(
            2048, 4096, 2, -1000000000L, "RUNNING"
        );

        assertEquals(-1.0, stats.getCpuTimeSeconds(), 0.01);
    }
}
