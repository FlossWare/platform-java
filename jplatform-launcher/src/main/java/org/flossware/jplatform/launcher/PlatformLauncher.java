package org.flossware.jplatform.launcher;

import org.flossware.jplatform.api.*;
import org.flossware.jplatform.core.ApplicationManager;
import org.flossware.jplatform.messaging.InMemoryMessageBus;
import org.flossware.jplatform.messaging.ServiceRegistryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Main entry point for JPlatform.
 * Bootstraps the platform and provides interactive management console.
 * <p>
 * This launcher initializes the platform infrastructure (message bus, service registry,
 * and application manager) and provides an interactive command-line interface for
 * managing applications. Applications can be deployed, started, stopped, and monitored
 * through the console.
 * <p>
 * Supported commands:
 * <ul>
 * <li>deploy - Deploy an application from a JAR file</li>
 * <li>start - Start a deployed application</li>
 * <li>stop - Stop a running application</li>
 * <li>undeploy - Undeploy an application</li>
 * <li>list - List all applications and their states</li>
 * <li>status - Show detailed status of an application</li>
 * <li>exit - Exit the platform</li>
 * <li>help - Show available commands</li>
 * </ul>
 * <p>
 * Example usage:
 * {@code
 * // Start the launcher
 * java -jar jplatform-launcher.jar
 *
 * // At the prompt:
 * jplatform> deploy myapp /path/to/app.jar com.example.MyApp
 * jplatform> start myapp
 * jplatform> status myapp
 * jplatform> stop myapp
 * jplatform> exit
 * }
 *
 * @see ApplicationManager
 */
public class PlatformLauncher {

    private static final Logger logger = LoggerFactory.getLogger(PlatformLauncher.class);

    private final ApplicationManager applicationManager;
    private final InMemoryMessageBus messageBus;
    private final ServiceRegistryImpl serviceRegistry;
    private volatile boolean running = true;

    /**
     * Creates a new platform launcher.
     * <p>
     * Initializes the message bus, service registry, and application manager.
     */
    public PlatformLauncher() {
        logger.info("Initializing JPlatform...");

        this.messageBus = new InMemoryMessageBus();
        this.serviceRegistry = new ServiceRegistryImpl();
        this.applicationManager = new ApplicationManager(messageBus, serviceRegistry);

        logger.info("JPlatform initialized successfully");
    }

    /**
     * Starts the platform and begins the interactive console.
     * <p>
     * This method runs in a loop, reading commands from standard input and
     * dispatching them to the appropriate handlers. The loop continues until
     * the 'exit' or 'quit' command is received.
     */
    public void start() {
        logger.info("JPlatform started");
        logger.info("Type 'help' for available commands");

        Scanner scanner = new Scanner(System.in);

        while (running) {
            System.out.print("\njplatform> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            String[] parts = input.split("\\s+", 2);
            String command = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1] : "";

            try {
                handleCommand(command, args);
            } catch (Exception e) {
                logger.error("Error executing command: {}", command, e);
                System.err.println("Error: " + e.getMessage());
            }
        }

        scanner.close();
    }

    private void handleCommand(String command, String args) throws Exception {
        switch (command) {
            case "help":
                printHelp();
                break;

            case "deploy":
                handleDeploy(args);
                break;

            case "start":
                handleStart(args);
                break;

            case "stop":
                handleStop(args);
                break;

            case "undeploy":
                handleUndeploy(args);
                break;

            case "list":
                handleList();
                break;

            case "status":
                handleStatus(args);
                break;

            case "exit":
            case "quit":
                handleExit();
                break;

            default:
                System.out.println("Unknown command: " + command);
                System.out.println("Type 'help' for available commands");
        }
    }

    private void printHelp() {
        System.out.println("\nAvailable commands:");
        System.out.println("  deploy <appId> <jarFile> <mainClass>  - Deploy an application");
        System.out.println("  start <appId>                         - Start a deployed application");
        System.out.println("  stop <appId>                          - Stop a running application");
        System.out.println("  undeploy <appId>                      - Undeploy an application");
        System.out.println("  list                                  - List all applications");
        System.out.println("  status <appId>                        - Show application status");
        System.out.println("  exit                                  - Exit platform");
        System.out.println("  help                                  - Show this help");
    }

    private void handleDeploy(String args) throws Exception {
        String[] parts = args.split("\\s+");
        if (parts.length < 3) {
            System.out.println("Usage: deploy <appId> <jarFile> <mainClass>");
            return;
        }

        String appId = parts[0];
        String jarFile = parts[1];
        String mainClass = parts[2];

        File jar = new File(jarFile);
        if (!jar.exists()) {
            System.out.println("JAR file not found: " + jarFile);
            return;
        }

        List<URI> classpath = new ArrayList<>();
        classpath.add(jar.toURI());

        ApplicationDescriptor descriptor = ApplicationDescriptor.builder()
                .applicationId(appId)
                .name(appId)
                .mainClass(mainClass)
                .classpathEntries(classpath)
                .enableMessaging(true)
                .build();

        applicationManager.deploy(descriptor);
        System.out.println("Application deployed: " + appId);
    }

    private void handleStart(String appId) throws Exception {
        if (appId.isEmpty()) {
            System.out.println("Usage: start <appId>");
            return;
        }

        applicationManager.start(appId);
        System.out.println("Application started: " + appId);
    }

    private void handleStop(String appId) throws Exception {
        if (appId.isEmpty()) {
            System.out.println("Usage: stop <appId>");
            return;
        }

        applicationManager.stop(appId);
        System.out.println("Application stopped: " + appId);
    }

    private void handleUndeploy(String appId) throws Exception {
        if (appId.isEmpty()) {
            System.out.println("Usage: undeploy <appId>");
            return;
        }

        applicationManager.undeploy(appId);
        System.out.println("Application undeployed: " + appId);
    }

    private void handleList() {
        var apps = applicationManager.listApplications();
        if (apps.isEmpty()) {
            System.out.println("No applications deployed");
            return;
        }

        System.out.println("\nApplications:");
        apps.forEach((id, state) -> System.out.println("  " + id + " - " + state));
    }

    private void handleStatus(String appId) {
        if (appId.isEmpty()) {
            System.out.println("Usage: status <appId>");
            return;
        }

        ApplicationContext context = applicationManager.getApplicationContext(appId);
        if (context == null) {
            System.out.println("Application not found: " + appId);
            return;
        }

        System.out.println("\nApplication: " + appId);
        System.out.println("  State: " + context.getState());
        System.out.println("  Thread Pool: " + context.getThreadPool().getStats());

        ResourceSnapshot snapshot = context.getResourceMonitor().getCurrentSnapshot();
        System.out.println("  Resources:");
        System.out.println("    CPU Time: " + (snapshot.getCpuTimeNanos() / 1_000_000_000.0) + "s");
        System.out.println("    Heap Used: " + (snapshot.getHeapUsedBytes() / 1024 / 1024) + " MB");
        System.out.println("    Thread Count: " + snapshot.getThreadCount());
    }

    private void handleExit() {
        System.out.println("Shutting down platform...");
        running = false;
        applicationManager.shutdown();
        messageBus.shutdown();
        System.out.println("Platform shutdown complete");
    }

    /**
     * Main entry point for the platform.
     * <p>
     * Creates and starts the platform launcher. If a fatal error occurs during
     * initialization or execution, logs the error and exits with code 1.
     *
     * @param args command-line arguments (currently unused)
     */
    public static void main(String[] args) {
        try {
            PlatformLauncher launcher = new PlatformLauncher();
            launcher.start();
        } catch (Exception e) {
            logger.error("Fatal error in platform launcher", e);
            System.exit(1);
        }
    }
}
