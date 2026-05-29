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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.flossware.platform.api.ApplicationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launches and manages containerized applications via Docker, Podman, containerd, or LXC.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Detects container runtime (Docker, Podman, containerd, LXC)
 *   <li>Builds container run command with image, ports, volumes, environment
 *   <li>Pulls container images if needed
 *   <li>Launches container and tracks container ID
 *   <li>Monitors container lifecycle
 *   <li>Stops and removes containers
 * </ul>
 *
 * <p>Container Configuration via Properties:
 *
 * <pre>
 * container.runtime = docker|podman|containerd|lxc
 * container.image = image:tag
 * container.name = container-name (optional, defaults to applicationId)
 * container.ports = 8080:80,8443:443 (host:container pairs)
 * container.volumes = /host/path:/container/path,/other:/path
 * container.network = bridge|host|none
 * container.env.KEY = value (environment variables)
 * container.args = --arg1 --arg2 (additional container arguments)
 * container.containerd.namespace = k8s.io (containerd namespace, default: default)
 * container.containerd.snapshotter = overlayfs (containerd snapshotter, default: overlayfs)
 * </pre>
 *
 * @since 2.0
 */
public class ContainerLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerLauncher.class);

  public enum ContainerRuntime {
    DOCKER("docker"),
    PODMAN("podman"),
    CONTAINERD("nerdctl"), // Use nerdctl for Docker-compatible CLI
    LXC("lxc");

    private final String command;

    ContainerRuntime(String command) {
      this.command = command;
    }

    public String getCommand() {
      return command;
    }

    /**
     * Returns true if this runtime supports Docker-compatible commands.
     *
     * @return true for Docker, Podman, and containerd (via nerdctl)
     */
    public boolean isDockerCompatible() {
      return this == DOCKER || this == PODMAN || this == CONTAINERD;
    }

    /**
     * Converts a string to a ContainerRuntime enum.
     *
     * @param runtime the runtime name (case-insensitive)
     * @return the matching ContainerRuntime, or DOCKER as default
     */
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
  public ContainerInfo launch(String applicationId, ApplicationDescriptor descriptor)
      throws IOException {
    Map<String, String> properties = descriptor.getProperties();
    ContainerRuntime runtime = ContainerRuntime.fromString(properties.get("container.runtime"));

    LOGGER.info(
        "[{}] Launching containerized application with runtime: {}", applicationId, runtime);

    String containerName = properties.getOrDefault("container.name", applicationId);
    String image = properties.get("container.image");

    if (image == null || image.isEmpty()) {
      throw new IllegalArgumentException("container.image property is required");
    }

    // Pull image if needed (Docker-compatible runtimes)
    if (runtime.isDockerCompatible()) {
      pullImageIfNeeded(runtime, image, properties, applicationId);
    }

    // Build run command
    List<String> command = buildRunCommand(runtime, containerName, image, properties);
    LOGGER.info("[{}] Container command: {}", applicationId, String.join(" ", command));

    // Launch container
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.redirectErrorStream(true);

    Process process = processBuilder.start();
    LOGGER.info("[{}] Container launched", applicationId);

    // Read container ID from output (Docker-compatible runtimes return container ID)
    String containerId = null;
    if (runtime.isDockerCompatible()) {
      containerId = readContainerId(process, applicationId);
    } else {
      containerId = containerName; // LXC uses container name as ID
    }

    LOGGER.info("[{}] Container ID: {}", applicationId, containerId);

    // Create container info
    ContainerInfo containerInfo =
        new ContainerInfo(process, containerId, containerName, runtime, properties);

    // Start output reader thread
    startOutputReader(applicationId, runtime, containerId, properties, containerInfo);

    return containerInfo;
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

    LOGGER.info("[{}] Stopping container: {}", applicationId, containerInfo.getContainerId());

    ContainerRuntime runtime = containerInfo.getRuntime();
    String containerId = containerInfo.getContainerId();

    // Stop output reader thread first
    containerInfo.stopOutputReader();

    // Stop container
    List<String> stopCommand =
        buildStopCommand(runtime, containerId, containerInfo.getProperties());
    executeCommand(stopCommand, applicationId, "stop");

    // Wait for container to actually stop (poll status)
    long deadline = System.currentTimeMillis() + Math.min(gracefulTimeoutMs, 5000);
    boolean stopped = false;

    while (System.currentTimeMillis() < deadline) {
      try {
        List<String> statusCommand = new ArrayList<>();
        statusCommand.add(runtime.getCommand());
        statusCommand.add("inspect");
        statusCommand.add("-f");
        statusCommand.add("{{.State.Running}}");
        statusCommand.add(containerId);

        ProcessBuilder pb = new ProcessBuilder(statusCommand);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(p.getInputStream()))) {
          String running = reader.readLine();
          if ("false".equals(running)) {
            stopped = true;
            LOGGER.info("[{}] Container stopped", applicationId);
            break;
          }
        }

        p.waitFor();
        Thread.sleep(500); // Poll every 500ms
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (IOException e) {
        // Container might already be gone
        stopped = true;
        break;
      }
    }

    if (!stopped) {
      LOGGER.warn("[{}] Container did not stop within timeout", applicationId);
    }

    // Remove container
    List<String> removeCommand =
        buildRemoveCommand(runtime, containerId, containerInfo.getProperties());
    executeCommand(removeCommand, applicationId, "remove");

    LOGGER.info("[{}] Container stopped and removed", applicationId);
  }

  /** Pulls container image if not already present. */
  private void pullImageIfNeeded(
      ContainerRuntime runtime, String image, Map<String, String> properties, String applicationId)
      throws IOException {

    LOGGER.info("[{}] Checking if image exists: {}", applicationId, image);

    // Check if image exists
    List<String> inspectCommand = new ArrayList<>();
    inspectCommand.add(runtime.getCommand());

    // Add containerd namespace if using containerd
    if (runtime == ContainerRuntime.CONTAINERD) {
      String namespace = properties.getOrDefault("container.containerd.namespace", "default");
      inspectCommand.add("--namespace");
      inspectCommand.add(namespace);
    }

    inspectCommand.add("image");
    inspectCommand.add("inspect");
    inspectCommand.add(image);

    ProcessBuilder inspectBuilder = new ProcessBuilder(inspectCommand);
    inspectBuilder.redirectErrorStream(true);
    Process inspect = inspectBuilder.start();

    try {
      int exitCode = inspect.waitFor();
      if (exitCode == 0) {
        LOGGER.info("[{}] Image already exists: {}", applicationId, image);
        return;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while checking image", e);
    }

    // Pull image
    LOGGER.info("[{}] Pulling image: {}", applicationId, image);
    List<String> pullCommand = new ArrayList<>();
    pullCommand.add(runtime.getCommand());

    // Add containerd namespace if using containerd
    if (runtime == ContainerRuntime.CONTAINERD) {
      String namespace = properties.getOrDefault("container.containerd.namespace", "default");
      pullCommand.add("--namespace");
      pullCommand.add(namespace);
    }

    pullCommand.add("pull");
    pullCommand.add(image);

    executeCommand(pullCommand, applicationId, "pull");
    LOGGER.info("[{}] Image pulled successfully", applicationId);
  }

  /** Builds the container run command. */
  private List<String> buildRunCommand(
      ContainerRuntime runtime,
      String containerName,
      String image,
      Map<String, String> properties) {
    List<String> command = new ArrayList<>();

    if (runtime == ContainerRuntime.LXC) {
      return buildLxcCommand(containerName, properties);
    }

    // Docker/Podman/containerd command
    command.add(runtime.getCommand());

    // Add containerd namespace if using containerd
    if (runtime == ContainerRuntime.CONTAINERD) {
      String namespace = properties.getOrDefault("container.containerd.namespace", "default");
      command.add("--namespace");
      command.add(namespace);
    }

    command.add("run");
    command.add("-d"); // Detached mode
    command.add("--name");
    command.add(containerName);

    // containerd snapshotter option
    if (runtime == ContainerRuntime.CONTAINERD) {
      String snapshotter = properties.getOrDefault("container.containerd.snapshotter", "overlayfs");
      command.add("--snapshotter");
      command.add(snapshotter);
    }

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

  /** Builds LXC-specific command. */
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

  /** Builds stop command for the runtime. */
  private List<String> buildStopCommand(
      ContainerRuntime runtime, String containerId, Map<String, String> properties) {
    List<String> command = new ArrayList<>();

    if (runtime == ContainerRuntime.LXC) {
      command.add("lxc-stop");
      command.add("-n");
      command.add(containerId);
    } else {
      command.add(runtime.getCommand());

      // Add containerd namespace if using containerd
      if (runtime == ContainerRuntime.CONTAINERD) {
        String namespace = properties.getOrDefault("container.containerd.namespace", "default");
        command.add("--namespace");
        command.add(namespace);
      }

      command.add("stop");
      command.add(containerId);
    }

    return command;
  }

  /** Builds remove command for the runtime. */
  private List<String> buildRemoveCommand(
      ContainerRuntime runtime, String containerId, Map<String, String> properties) {
    List<String> command = new ArrayList<>();

    if (runtime == ContainerRuntime.LXC) {
      command.add("lxc-destroy");
      command.add("-n");
      command.add(containerId);
    } else {
      command.add(runtime.getCommand());

      // Add containerd namespace if using containerd
      if (runtime == ContainerRuntime.CONTAINERD) {
        String namespace = properties.getOrDefault("container.containerd.namespace", "default");
        command.add("--namespace");
        command.add(namespace);
      }

      command.add("rm");
      command.add("-f"); // Force remove
      command.add(containerId);
    }

    return command;
  }

  /** Reads container ID from process output. */
  private String readContainerId(Process process, String applicationId) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {

      String containerId = reader.readLine();
      if (containerId == null || containerId.isEmpty()) {
        throw new IOException("Failed to read container ID");
      }
      return containerId.trim();
    }
  }

  /** Starts a background thread to read and log container output. */
  private void startOutputReader(
      String applicationId,
      ContainerRuntime runtime,
      String containerId,
      Map<String, String> properties,
      ContainerInfo containerInfo) {
    Thread outputReader =
        new Thread(
            () -> {
              Process logsProcess = null;
              try {
                // Use container logs command to follow output
                List<String> logsCommand = new ArrayList<>();
                if (runtime == ContainerRuntime.LXC) {
                  logsCommand.add("lxc-console");
                  logsCommand.add("-n");
                  logsCommand.add(containerId);
                } else {
                  logsCommand.add(runtime.getCommand());

                  // Add containerd namespace if using containerd
                  if (runtime == ContainerRuntime.CONTAINERD) {
                    String namespace =
                        properties.getOrDefault("container.containerd.namespace", "default");
                    logsCommand.add("--namespace");
                    logsCommand.add(namespace);
                  }

                  logsCommand.add("logs");
                  logsCommand.add("-f"); // Follow
                  logsCommand.add(containerId);
                }

                ProcessBuilder logsBuilder = new ProcessBuilder(logsCommand);
                logsBuilder.redirectErrorStream(true);
                logsProcess = logsBuilder.start();
                containerInfo.setLogsProcess(logsProcess);

                try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(logsProcess.getInputStream()))) {

                  String line;
                  while ((line = reader.readLine()) != null
                      && !Thread.currentThread().isInterrupted()) {
                    LOGGER.info("[{}] {}", applicationId, line);
                  }
                }

              } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                  LOGGER.error("[{}] Error reading container logs", applicationId, e);
                }
              }
            },
            "container-logs-" + applicationId);

    outputReader.setDaemon(true);
    containerInfo.setOutputReaderThread(outputReader);
    outputReader.start();
  }

  /**
   * Executes a command and waits for completion. Reads output asynchronously to prevent deadlock on
   * large output.
   */
  private void executeCommand(List<String> command, String applicationId, String operation)
      throws IOException {

    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);
    Process process = builder.start();

    // Read output asynchronously to avoid deadlock
    StringBuilder output = new StringBuilder();
    Thread outputReader =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  output.append(line).append("\n");
                }
              } catch (IOException e) {
                LOGGER.debug("Error reading command output", e);
              }
            });
    outputReader.setDaemon(true);
    outputReader.start();

    try {
      int exitCode = process.waitFor();
      outputReader.join(5000); // Wait for output reader to finish

      if (exitCode != 0) {
        LOGGER.warn(
            "[{}] Container {} command failed (exit {}): {}",
            applicationId,
            operation,
            exitCode,
            output);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted during " + operation, e);
    }
  }

  /** Container information returned by launch. */
  public static class ContainerInfo {
    private final Process process;
    private final String containerId;
    private final String containerName;
    private final ContainerRuntime runtime;
    private final Map<String, String> properties;
    private Thread outputReaderThread;
    private Process logsProcess;

    /**
     * Creates container information.
     *
     * @param process the container process
     * @param containerId the container ID
     * @param containerName the container name
     * @param runtime the container runtime used
     * @param properties container properties including namespace config
     */
    public ContainerInfo(
        Process process,
        String containerId,
        String containerName,
        ContainerRuntime runtime,
        Map<String, String> properties) {
      this.process = process;
      this.containerId = containerId;
      this.containerName = containerName;
      this.runtime = runtime;
      this.properties = properties;
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

    public Map<String, String> getProperties() {
      return properties;
    }

    void setOutputReaderThread(Thread thread) {
      this.outputReaderThread = thread;
    }

    void setLogsProcess(Process process) {
      this.logsProcess = process;
    }

    void stopOutputReader() {
      if (logsProcess != null && logsProcess.isAlive()) {
        logsProcess.destroyForcibly();
      }
      if (outputReaderThread != null && outputReaderThread.isAlive()) {
        outputReaderThread.interrupt();
      }
    }
  }
}
