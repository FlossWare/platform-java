package org.flossware.jplatform.core;

import org.flossware.jplatform.api.ApplicationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Launches and manages native application processes.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Resolves native executable from descriptor classpath entries</li>
 *   <li>Sets up process environment variables</li>
 *   <li>Launches process via ProcessBuilder</li>
 *   <li>Redirects stdout/stderr to logging</li>
 *   <li>Monitors process lifecycle</li>
 * </ul>
 *
 * <p>Native applications are launched as separate OS processes, not JVM applications.
 * This enables deployment of GraalVM native images and other compiled executables.</p>
 *
 * @since 2.0
 */
public class NativeProcessLauncher {

    private static final Logger logger = LoggerFactory.getLogger(NativeProcessLauncher.class);

    /**
     * Launches a native application process.
     *
     * @param applicationId the application identifier
     * @param descriptor the application descriptor containing native executable info
     * @param workingDir the working directory for the process
     * @return the launched Process
     * @throws IOException if process launch fails
     */
    public Process launch(String applicationId, ApplicationDescriptor descriptor, Path workingDir) throws IOException {
        if (!descriptor.isNativeImage()) {
            throw new IllegalArgumentException("Descriptor must have nativeImage flag set to true");
        }

        logger.info("[{}] Launching native application", applicationId);

        // Resolve executable path
        String executablePath = resolveExecutablePath(descriptor);
        logger.info("[{}] Executable path: {}", applicationId, executablePath);

        // Build command line
        List<String> command = buildCommand(executablePath, descriptor);
        logger.info("[{}] Command: {}", applicationId, String.join(" ", command));

        // Build process
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir.toFile());

        // Set environment variables
        Map<String, String> environment = processBuilder.environment();
        descriptor.getProperties().forEach((key, value) -> {
            if (key.startsWith("native.env.")) {
                String envVar = key.substring("native.env.".length());
                environment.put(envVar, value);
                logger.debug("[{}] Set environment: {}={}", applicationId, envVar, value);
            }
        });

        // Redirect output
        processBuilder.redirectErrorStream(true);

        // Launch process
        Process process = processBuilder.start();
        logger.info("[{}] Native process launched with PID: {}", applicationId, process.pid());

        // Start output reader thread
        startOutputReader(applicationId, process);

        return process;
    }

    /**
     * Stops a native application process.
     *
     * @param applicationId the application identifier
     * @param process the process to stop
     * @param gracefulTimeoutMs time to wait for graceful shutdown before force kill
     * @throws InterruptedException if interrupted while waiting
     */
    public void stop(String applicationId, Process process, long gracefulTimeoutMs) throws InterruptedException {
        if (process == null || !process.isAlive()) {
            logger.warn("[{}] Process already stopped", applicationId);
            return;
        }

        logger.info("[{}] Stopping native process (PID: {})", applicationId, process.pid());

        // Send graceful termination signal
        process.destroy();

        // Wait for graceful shutdown
        boolean exited = process.waitFor(gracefulTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        if (!exited) {
            logger.warn("[{}] Process did not exit gracefully, force killing", applicationId);
            process.destroyForcibly();
            process.waitFor(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        if (process.isAlive()) {
            logger.error("[{}] Process still alive after force kill", applicationId);
        } else {
            logger.info("[{}] Native process stopped (exit code: {})", applicationId, process.exitValue());
        }
    }

    /**
     * Resolves the native executable path from the descriptor.
     *
     * @param descriptor the application descriptor
     * @return the executable path
     */
    private String resolveExecutablePath(ApplicationDescriptor descriptor) {
        // Check for explicit native.executable.path property
        String explicitPath = descriptor.getProperties().get("native.executable.path");
        if (explicitPath != null && !explicitPath.isEmpty()) {
            return explicitPath;
        }

        // Otherwise use first classpath entry
        if (descriptor.getClasspathEntries().isEmpty()) {
            throw new IllegalArgumentException("No classpath entries found for native executable");
        }

        return Paths.get(descriptor.getClasspathEntries().get(0)).toString();
    }

    /**
     * Builds the command line for process execution.
     *
     * @param executablePath the path to the executable
     * @param descriptor the application descriptor
     * @return command line as list of strings
     */
    private List<String> buildCommand(String executablePath, ApplicationDescriptor descriptor) {
        List<String> command = new ArrayList<>();
        command.add(executablePath);

        // Add arguments from native.args property
        String argsProperty = descriptor.getProperties().get("native.args");
        if (argsProperty != null && !argsProperty.isEmpty()) {
            // Simple split by space - could be enhanced to handle quoted args
            String[] args = argsProperty.split("\\s+");
            for (String arg : args) {
                if (!arg.isEmpty()) {
                    command.add(arg);
                }
            }
        }

        return command;
    }

    /**
     * Starts a background thread to read and log process output.
     *
     * @param applicationId the application identifier
     * @param process the process to read from
     */
    private void startOutputReader(String applicationId, Process process) {
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[{}] {}", applicationId, line);
                }

            } catch (IOException e) {
                if (process.isAlive()) {
                    logger.error("[{}] Error reading process output", applicationId, e);
                }
            }
        }, "native-output-" + applicationId);

        outputReader.setDaemon(true);
        outputReader.start();
    }
}
