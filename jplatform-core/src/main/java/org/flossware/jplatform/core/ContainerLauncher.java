package org.flossware.jplatform.core;

import org.flossware.jplatform.api.ApplicationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Launches and manages containerized applications via Docker, Podman, or LXC.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Detects container runtime (Docker, Podman, LXC)</li>
 *   <li>Builds container run command with image, ports, volumes, environment</li>
 *   <li>Pulls container images if needed</li>
 *   <li>Launches container and tracks container ID</li>
 *   <li>Monitors container lifecycle</li>
 *   <li>Stops and removes containers</li>
 * </ul>
 *
 * <p>Container Configuration via Properties:</p>
 * <pre>
 * container.runtime = docker|podman|lxc
 * container.image = image:tag
 * container.name = container-name (optional, defaults to applicationId)
 * container.ports = 8080:80,8443:443 (host:container pairs)
 * container.volumes = /host/path:/container/path,/other:/path
 * container.network = bridge|host|none
 * container.env.KEY = value (environment variables)
 * container.args = --arg1 --arg2 (additional container arguments)
 * </pre>
 *
 * @since 2.0
 */
public class ContainerLauncher {

    private static final Logger logger = LoggerFactory.getLogger(ContainerLauncher.class);

    public enum ContainerRuntime {
        DOCKER("docker"),
        PODMAN("podman"),
        LXC("lxc");

        private final String command;

        ContainerRuntime(String command) {
            this.command = command;
        }

        public String getCommand() {
            return command;
        }

        public static ContainerRuntime fromString(String runtime) {
            if (runtime == null) {
                return DOCKER; // Default to Docker
            }
            for (ContainerRuntime rt : values()) {
                if (rt.name().equalsIgnoreCase(runtime)) {
                    return rt;
                }
            }
            throw new IllegalArgumentException("Unknown container runtime: " + runtime);
        }
    }

    /**
     * Launches a containerized application.
     *
     * @param applicationId the application identifier
     * @param descriptor the application descriptor containing container configuration
     * @return ContainerInfo containing process and container ID
     * @throws IOException if container launch fails
     */
    public ContainerInfo launch(String applicationId, ApplicationDescriptor descriptor) throws IOException {
        Map<String, String> properties = descriptor.getProperties();
        ContainerRuntime runtime = ContainerRuntime.fromString(properties.get("container.runtime"));

        logger.info("[{}] Launching containerized application with runtime: {}", applicationId, runtime);

        String containerName = properties.getOrDefault("container.name", applicationId);
        String image = properties.get("container.image");

        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("container.image property is required");
        }

        // Pull image if needed (Docker/Podman only)
        if (runtime == ContainerRuntime.DOCKER || runtime == ContainerRuntime.PODMAN) {
            pullImageIfNeeded(runtime, image, applicationId);
        }

        // Build run command
        List<String> command = buildRunCommand(runtime, containerName, image, properties);
        logger.info("[{}] Container command: {}", applicationId, String.join(" ", command));

        // Launch container
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        logger.info("[{}] Container launched", applicationId);

        // Read container ID from output (Docker/Podman return container ID)
        String containerId = null;
        if (runtime == ContainerRuntime.DOCKER || runtime == ContainerRuntime.PODMAN) {
            containerId = readContainerId(process, applicationId);
        } else {
            containerId = containerName; // LXC uses container name as ID
        }

        logger.info("[{}] Container ID: {}", applicationId, containerId);

        // Start output reader thread
        startOutputReader(applicationId, runtime, containerId);

        return new ContainerInfo(process, containerId, containerName, runtime);
    }

    /**
     * Stops and removes a container.
     *
     * @param applicationId the application identifier
     * @param containerInfo the container information
     * @param gracefulTimeoutMs time to wait for graceful shutdown
     * @throws InterruptedException if interrupted while waiting
     */
    public void stop(String applicationId, ContainerInfo containerInfo, long gracefulTimeoutMs)
            throws InterruptedException, IOException {

        logger.info("[{}] Stopping container: {}", applicationId, containerInfo.getContainerId());

        ContainerRuntime runtime = containerInfo.getRuntime();
        String containerId = containerInfo.getContainerId();

        // Stop container
        List<String> stopCommand = buildStopCommand(runtime, containerId);
        executeCommand(stopCommand, applicationId, "stop");

        // Wait for stop
        Thread.sleep(Math.min(gracefulTimeoutMs, 5000));

        // Remove container
        List<String> removeCommand = buildRemoveCommand(runtime, containerId);
        executeCommand(removeCommand, applicationId, "remove");

        logger.info("[{}] Container stopped and removed", applicationId);
    }

    /**
     * Pulls container image if not already present.
     */
    private void pullImageIfNeeded(ContainerRuntime runtime, String image, String applicationId)
            throws IOException {

        logger.info("[{}] Checking if image exists: {}", applicationId, image);

        // Check if image exists
        List<String> inspectCommand = new ArrayList<>();
        inspectCommand.add(runtime.getCommand());
        inspectCommand.add("image");
        inspectCommand.add("inspect");
        inspectCommand.add(image);

        ProcessBuilder inspectBuilder = new ProcessBuilder(inspectCommand);
        inspectBuilder.redirectErrorStream(true);
        Process inspect = inspectBuilder.start();

        try {
            int exitCode = inspect.waitFor();
            if (exitCode == 0) {
                logger.info("[{}] Image already exists: {}", applicationId, image);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking image", e);
        }

        // Pull image
        logger.info("[{}] Pulling image: {}", applicationId, image);
        List<String> pullCommand = new ArrayList<>();
        pullCommand.add(runtime.getCommand());
        pullCommand.add("pull");
        pullCommand.add(image);

        executeCommand(pullCommand, applicationId, "pull");
        logger.info("[{}] Image pulled successfully", applicationId);
    }

    /**
     * Builds the container run command.
     */
    private List<String> buildRunCommand(ContainerRuntime runtime, String containerName,
                                         String image, Map<String, String> properties) {
        List<String> command = new ArrayList<>();

        if (runtime == ContainerRuntime.LXC) {
            return buildLxcCommand(containerName, properties);
        }

        // Docker/Podman command
        command.add(runtime.getCommand());
        command.add("run");
        command.add("-d"); // Detached mode
        command.add("--name");
        command.add(containerName);

        // Ports
        String ports = properties.get("container.ports");
        if (ports != null && !ports.isEmpty()) {
            for (String portMapping : ports.split(",")) {
                command.add("-p");
                command.add(portMapping.trim());
            }
        }

        // Volumes
        String volumes = properties.get("container.volumes");
        if (volumes != null && !volumes.isEmpty()) {
            for (String volumeMapping : volumes.split(",")) {
                command.add("-v");
                command.add(volumeMapping.trim());
            }
        }

        // Network
        String network = properties.get("container.network");
        if (network != null && !network.isEmpty()) {
            command.add("--network");
            command.add(network);
        }

        // Environment variables
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("container.env.")) {
                String envVar = entry.getKey().substring("container.env.".length());
                command.add("-e");
                command.add(envVar + "=" + entry.getValue());
            }
        }

        // Image
        command.add(image);

        // Additional arguments
        String args = properties.get("container.args");
        if (args != null && !args.isEmpty()) {
            // Parse arguments with proper quote handling
            command.addAll(ArgumentParser.parseArguments(args));
        }

        return command;
    }

    /**
     * Builds LXC-specific command.
     */
    private List<String> buildLxcCommand(String containerName, Map<String, String> properties) {
        List<String> command = new ArrayList<>();
        command.add("lxc-start");
        command.add("-n");
        command.add(containerName);
        command.add("-d"); // Daemon mode

        // LXC config file if specified
        String configFile = properties.get("container.lxc.config");
        if (configFile != null && !configFile.isEmpty()) {
            command.add("-f");
            command.add(configFile);
        }

        return command;
    }

    /**
     * Builds stop command for the runtime.
     */
    private List<String> buildStopCommand(ContainerRuntime runtime, String containerId) {
        List<String> command = new ArrayList<>();

        if (runtime == ContainerRuntime.LXC) {
            command.add("lxc-stop");
            command.add("-n");
            command.add(containerId);
        } else {
            command.add(runtime.getCommand());
            command.add("stop");
            command.add(containerId);
        }

        return command;
    }

    /**
     * Builds remove command for the runtime.
     */
    private List<String> buildRemoveCommand(ContainerRuntime runtime, String containerId) {
        List<String> command = new ArrayList<>();

        if (runtime == ContainerRuntime.LXC) {
            command.add("lxc-destroy");
            command.add("-n");
            command.add(containerId);
        } else {
            command.add(runtime.getCommand());
            command.add("rm");
            command.add("-f"); // Force remove
            command.add(containerId);
        }

        return command;
    }

    /**
     * Reads container ID from process output.
     */
    private String readContainerId(Process process, String applicationId) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String containerId = reader.readLine();
            if (containerId == null || containerId.isEmpty()) {
                throw new IOException("Failed to read container ID");
            }
            return containerId.trim();
        }
    }

    /**
     * Starts a background thread to read and log container output.
     */
    private void startOutputReader(String applicationId, ContainerRuntime runtime, String containerId) {
        Thread outputReader = new Thread(() -> {
            try {
                // Use container logs command to follow output
                List<String> logsCommand = new ArrayList<>();
                if (runtime == ContainerRuntime.LXC) {
                    logsCommand.add("lxc-console");
                    logsCommand.add("-n");
                    logsCommand.add(containerId);
                } else {
                    logsCommand.add(runtime.getCommand());
                    logsCommand.add("logs");
                    logsCommand.add("-f"); // Follow
                    logsCommand.add(containerId);
                }

                ProcessBuilder logsBuilder = new ProcessBuilder(logsCommand);
                logsBuilder.redirectErrorStream(true);
                Process logsProcess = logsBuilder.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(logsProcess.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("[{}] {}", applicationId, line);
                    }
                }

            } catch (IOException e) {
                logger.error("[{}] Error reading container logs", applicationId, e);
            }
        }, "container-logs-" + applicationId);

        outputReader.setDaemon(true);
        outputReader.start();
    }

    /**
     * Executes a command and waits for completion.
     */
    private void executeCommand(List<String> command, String applicationId, String operation)
            throws IOException {

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Read error output
                StringBuilder error = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                }
                logger.warn("[{}] Container {} command failed (exit {}): {}",
                        applicationId, operation, exitCode, error);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during " + operation, e);
        }
    }

    /**
     * Container information returned by launch.
     */
    public static class ContainerInfo {
        private final Process process;
        private final String containerId;
        private final String containerName;
        private final ContainerRuntime runtime;

        public ContainerInfo(Process process, String containerId, String containerName,
                           ContainerRuntime runtime) {
            this.process = process;
            this.containerId = containerId;
            this.containerName = containerName;
            this.runtime = runtime;
        }

        public Process getProcess() {
            return process;
        }

        public String getContainerId() {
            return containerId;
        }

        public String getContainerName() {
            return containerName;
        }

        public ContainerRuntime getRuntime() {
            return runtime;
        }
    }
}
