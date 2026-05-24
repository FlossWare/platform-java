package org.flossware.jplatform.core;

import org.flossware.jplatform.api.ApplicationDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContainerLauncher.
 * Tests configuration validation, runtime detection, and API contract.
 *
 * Note: Full integration tests require Docker/Podman/LXC installed.
 * These tests focus on configuration parsing and validation.
 */
class ContainerLauncherTest {

    private ContainerLauncher launcher;

    @BeforeEach
    void setUp() {
        launcher = new ContainerLauncher();
    }

    @Test
    void testContainerRuntimeFromString() {
        assertEquals(ContainerLauncher.ContainerRuntime.DOCKER,
                ContainerLauncher.ContainerRuntime.fromString("docker"));
        assertEquals(ContainerLauncher.ContainerRuntime.DOCKER,
                ContainerLauncher.ContainerRuntime.fromString("DOCKER"));
        assertEquals(ContainerLauncher.ContainerRuntime.PODMAN,
                ContainerLauncher.ContainerRuntime.fromString("podman"));
        assertEquals(ContainerLauncher.ContainerRuntime.LXC,
                ContainerLauncher.ContainerRuntime.fromString("lxc"));
    }

    @Test
    void testContainerRuntimeFromStringWithNull() {
        // Null defaults to Docker
        assertEquals(ContainerLauncher.ContainerRuntime.DOCKER,
                ContainerLauncher.ContainerRuntime.fromString(null));
    }

    @Test
    void testContainerRuntimeFromStringWithInvalidRuntime() {
        assertThrows(IllegalArgumentException.class, () -> {
            ContainerLauncher.ContainerRuntime.fromString("invalid");
        }, "Should throw for unknown runtime");
    }

    @Test
    void testContainerRuntimeGetCommand() {
        assertEquals("docker", ContainerLauncher.ContainerRuntime.DOCKER.getCommand());
        assertEquals("podman", ContainerLauncher.ContainerRuntime.PODMAN.getCommand());
        assertEquals("lxc", ContainerLauncher.ContainerRuntime.LXC.getCommand());
    }

    @Test
    void testLaunchWithMissingImageThrowsException() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App")
                .property("container.runtime", "docker")
                // Missing container.image
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            launcher.launch("app1", descriptor);
        }, "Should throw when container.image is missing");
    }

    @Test
    void testLaunchWithEmptyImageThrowsException() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App")
                .property("container.runtime", "docker")
                .property("container.image", "")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            launcher.launch("app1", descriptor);
        }, "Should throw when container.image is empty");
    }

    @Test
    void testContainerInfoGetters() {
        Process mockProcess = ProcessHandle.current().parent()
                .map(ProcessHandle::pid)
                .flatMap(ProcessHandle::of)
                .map(ProcessHandle::info)
                .map(info -> (Process) null)
                .orElse(null);

        ContainerLauncher.ContainerInfo info = new ContainerLauncher.ContainerInfo(
                mockProcess,
                "abc123",
                "my-container",
                ContainerLauncher.ContainerRuntime.DOCKER
        );

        assertEquals(mockProcess, info.getProcess());
        assertEquals("abc123", info.getContainerId());
        assertEquals("my-container", info.getContainerName());
        assertEquals(ContainerLauncher.ContainerRuntime.DOCKER, info.getRuntime());
    }

    @Test
    void testConstructorDoesNotThrow() {
        assertDoesNotThrow(() -> {
            new ContainerLauncher();
        });
    }

    @Test
    void testContainerRuntimeEnumValues() {
        ContainerLauncher.ContainerRuntime[] runtimes = ContainerLauncher.ContainerRuntime.values();
        assertEquals(3, runtimes.length, "Should have exactly 3 runtime types");

        assertTrue(java.util.Arrays.asList(runtimes).contains(ContainerLauncher.ContainerRuntime.DOCKER));
        assertTrue(java.util.Arrays.asList(runtimes).contains(ContainerLauncher.ContainerRuntime.PODMAN));
        assertTrue(java.util.Arrays.asList(runtimes).contains(ContainerLauncher.ContainerRuntime.LXC));
    }

    @Test
    void testContainerRuntimeValueOf() {
        assertEquals(ContainerLauncher.ContainerRuntime.DOCKER,
                ContainerLauncher.ContainerRuntime.valueOf("DOCKER"));
        assertEquals(ContainerLauncher.ContainerRuntime.PODMAN,
                ContainerLauncher.ContainerRuntime.valueOf("PODMAN"));
        assertEquals(ContainerLauncher.ContainerRuntime.LXC,
                ContainerLauncher.ContainerRuntime.valueOf("LXC"));
    }

    @Test
    void testContainerRuntimeFromStringCaseInsensitive() {
        assertEquals(ContainerLauncher.ContainerRuntime.DOCKER,
                ContainerLauncher.ContainerRuntime.fromString("Docker"));
        assertEquals(ContainerLauncher.ContainerRuntime.PODMAN,
                ContainerLauncher.ContainerRuntime.fromString("Podman"));
        assertEquals(ContainerLauncher.ContainerRuntime.LXC,
                ContainerLauncher.ContainerRuntime.fromString("LXC"));
        assertEquals(ContainerLauncher.ContainerRuntime.LXC,
                ContainerLauncher.ContainerRuntime.fromString("Lxc"));
    }
}
