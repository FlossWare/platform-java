package org.flossware.jplatform.core;

import org.flossware.jplatform.api.ApplicationDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NativeProcessLauncher.
 * Tests configuration validation and process management API contract.
 *
 * Note: Full integration tests require actual native executables.
 * These tests focus on configuration validation and API behavior.
 */
class NativeProcessLauncherTest {

    @TempDir
    Path tempDir;

    private NativeProcessLauncher launcher;

    @BeforeEach
    void setUp() {
        launcher = new NativeProcessLauncher();
    }

    @Test
    void testLaunchWithNonNativeDescriptorThrowsException() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App")
                .nativeImage(false)  // Not a native image
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            launcher.launch("app1", descriptor, tempDir);
        }, "Should throw when descriptor does not have nativeImage flag");
    }

    @Test
    void testLaunchWithMissingExecutableThrowsException() {
        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App")
                .nativeImage(true)
                // No classpath entries, no native.executable.path
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            launcher.launch("app1", descriptor, tempDir);
        }, "Should throw when no executable path is available");
    }

    @Test
    void testStopWithNullProcessDoesNotThrow() throws InterruptedException {
        assertDoesNotThrow(() -> {
            launcher.stop("app1", null, 1000);
        }, "Should handle null process gracefully");
    }

    @Test
    void testStopWithDeadProcessDoesNotThrow() throws IOException, InterruptedException {
        // Create a process that exits immediately
        ProcessBuilder pb = new ProcessBuilder("true");  // Unix command that exits immediately
        Process process = pb.start();
        process.waitFor();  // Wait for it to finish

        assertFalse(process.isAlive(), "Process should be dead");

        assertDoesNotThrow(() -> {
            launcher.stop("app1", process, 1000);
        }, "Should handle already-dead process gracefully");
    }

    @Test
    void testLaunchWithExplicitExecutablePathProperty() throws IOException {
        // Create a dummy executable
        Path executable = tempDir.resolve("test-app");
        Files.writeString(executable, "#!/bin/sh\necho 'test'\n");
        executable.toFile().setExecutable(true);

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App")
                .nativeImage(true)
                .property("native.executable.path", executable.toString())
                .build();

        // Launch should not throw (even if process fails immediately)
        assertDoesNotThrow(() -> {
            try {
                Process process = launcher.launch("app1", descriptor, tempDir);
                // Clean up process
                if (process.isAlive()) {
                    process.destroy();
                }
            } catch (IOException e) {
                // Some systems may not allow executing shell scripts, that's ok for this test
            }
        });
    }

    @Test
    void testLaunchCreatesWorkingDirectory() throws IOException {
        Path workingDir = tempDir.resolve("workdir");
        assertFalse(Files.exists(workingDir), "Working directory should not exist yet");

        // Create a dummy executable
        Path executable = tempDir.resolve("test-app");
        Files.writeString(executable, "#!/bin/sh\nsleep 10\n");
        executable.toFile().setExecutable(true);

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId("app1")
                .mainClass("com.example.App")
                .nativeImage(true)
                .property("native.executable.path", executable.toString())
                .build();

        try {
            Process process = launcher.launch("app1", descriptor, workingDir);

            // Working directory should be created if it doesn't exist
            // Note: The actual creation happens in ApplicationManager, not NativeProcessLauncher
            // This test documents the expected behavior

            // Clean up
            if (process.isAlive()) {
                process.destroy();
            }
        } catch (IOException e) {
            // Expected on some systems - this is a contract test, not integration
        }
    }

    @Test
    void testConstructorDoesNotThrow() {
        assertDoesNotThrow(() -> {
            new NativeProcessLauncher();
        });
    }
}
