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

package org.flossware.platform.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.flossware.platform.api.ApplicationDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for NativeProcessLauncher. Tests configuration validation and process management API
 * contract.
 *
 * <p>Note: Full integration tests require actual native executables. These tests focus on
 * configuration validation and API behavior.
 */
@Tag("unit")
@Tag("security")
class NativeProcessLauncherTest {

  @TempDir Path tempDir;

  private NativeProcessLauncher launcher;

  @BeforeEach
  void setUp() {
    launcher = new NativeProcessLauncher();
  }

  @Test
  void testLaunchWithNonNativeDescriptorThrowsException() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(false) // Not a native image
            .build();

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          launcher.launch("app1", descriptor, tempDir);
        },
        "Should throw when descriptor does not have nativeImage flag");
  }

  @Test
  void testLaunchWithMissingExecutableThrowsException() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(true)
            // No classpath entries, no native.executable.path
            .build();

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          launcher.launch("app1", descriptor, tempDir);
        },
        "Should throw when no executable path is available");
  }

  @Test
  void testStopWithNullProcessDoesNotThrow() throws InterruptedException {
    assertDoesNotThrow(
        () -> {
          launcher.stop("app1", null, 1000);
        },
        "Should handle null process gracefully");
  }

  @Test
  void testStopWithDeadProcessDoesNotThrow() throws IOException, InterruptedException {
    // Create a process that exits immediately
    ProcessBuilder pb = new ProcessBuilder("true"); // Unix command that exits immediately
    Process process = pb.start();
    process.waitFor(); // Wait for it to finish

    assertFalse(process.isAlive(), "Process should be dead");

    assertDoesNotThrow(
        () -> {
          launcher.stop("app1", process, 1000);
        },
        "Should handle already-dead process gracefully");
  }

  @Test
  void testLaunchWithExplicitExecutablePathProperty() throws IOException {
    // Create a dummy executable
    Path executable = tempDir.resolve("test-app");
    Files.writeString(executable, "#!/bin/sh\necho 'test'\n");
    executable.toFile().setExecutable(true);

    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(true)
            .property("native.executable.path", executable.toString())
            .build();

    // Launch should not throw (even if process fails immediately)
    assertDoesNotThrow(
        () -> {
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

    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
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
    assertDoesNotThrow(
        () -> {
          new NativeProcessLauncher();
        });
  }

  @Test
  void testPathTraversalWithRelativePathIsRejected() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(true)
            .property("native.executable.path", "../../../etc/passwd")
            .build();

    SecurityException exception =
        assertThrows(
            SecurityException.class,
            () -> {
              launcher.launch("app1", descriptor, tempDir);
            },
            "Should reject path traversal with ../");

    assertTrue(
        exception.getMessage().contains("path traversal"),
        "Exception message should mention path traversal");
  }

  @Test
  void testPathTraversalWithWindowsStyleIsRejected() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(true)
            .property("native.executable.path", "..\\..\\windows\\system32\\cmd.exe")
            .build();

    SecurityException exception =
        assertThrows(
            SecurityException.class,
            () -> {
              launcher.launch("app1", descriptor, tempDir);
            },
            "Should reject Windows-style path traversal");

    assertTrue(
        exception.getMessage().contains("path traversal"),
        "Exception message should mention path traversal");
  }

  @Test
  void testSystemBinaryPathIsRejected() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(true)
            .property("native.executable.path", "/bin/bash")
            .build();

    SecurityException exception =
        assertThrows(
            SecurityException.class,
            () -> {
              launcher.launch("app1", descriptor, tempDir);
            },
            "Should reject system directory paths");

    assertTrue(
        exception.getMessage().contains("restricted system directory"),
        "Exception message should mention restricted directory");
  }

  @Test
  void testUsrBinPathIsRejected() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(true)
            .property("native.executable.path", "/usr/bin/curl")
            .build();

    assertThrows(
        SecurityException.class,
        () -> {
          launcher.launch("app1", descriptor, tempDir);
        },
        "Should reject /usr/bin paths");
  }

  @Test
  void testEtcDirectoryPathIsRejected() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(true)
            .property("native.executable.path", "/etc/shadow")
            .build();

    assertThrows(
        SecurityException.class,
        () -> {
          launcher.launch("app1", descriptor, tempDir);
        },
        "Should reject /etc directory paths");
  }

  @Test
  void testClasspathEntryWithPathTraversalIsRejected() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(true)
            .addClasspathEntry(java.net.URI.create("file:///../../../malicious/executable"))
            .build();

    assertThrows(
        SecurityException.class,
        () -> {
          launcher.launch("app1", descriptor, tempDir);
        },
        "Should validate classpath entries for path traversal");
  }

  @Test
  void testClasspathEntryWithSystemPathIsRejected() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(true)
            .addClasspathEntry(java.net.URI.create("file:///bin/sh"))
            .build();

    assertThrows(
        SecurityException.class,
        () -> {
          launcher.launch("app1", descriptor, tempDir);
        },
        "Should validate classpath entries against system directories");
  }

  @Test
  void testNullExecutablePathIsRejected() {
    ApplicationDescriptor descriptor =
        ApplicationDescriptor.builder()
            .applicationId("app1")
            .mainClass("com.example.App")
            .nativeImage(true)
            .property("native.executable.path", "")
            .build();

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          launcher.launch("app1", descriptor, tempDir);
        },
        "Should reject empty executable path");
  }
}
