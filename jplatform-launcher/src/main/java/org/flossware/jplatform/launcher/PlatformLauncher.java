package org.flossware.jplatform.launcher;

import org.flossware.jplatform.api.*;
import org.flossware.jplatform.config.JsonDescriptorParser;
import org.flossware.jplatform.config.YamlDescriptorParser;
import org.flossware.jplatform.core.ApplicationManager;
import org.flossware.jplatform.fswatcher.AutoDeploymentHandler;
import org.flossware.jplatform.fswatcher.FileSystemDeploymentWatcher;
import org.flossware.jplatform.messaging.InMemoryMessageBus;
import org.flossware.jplatform.messaging.ServiceRegistryImpl;
import org.flossware.jplatform.metrics.jmx.JmxMetricsExporter;
import org.flossware.jplatform.metrics.prometheus.PrometheusMetricsExporter;
import org.flossware.jplatform.rest.JdkHttpApiServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main entry point for JPlatform.
 * Bootstraps the platform and provides interactive management console with optional features.
 * <p>
 * This launcher initializes the platform infrastructure (message bus, service registry,
 * and application manager) and provides an interactive command-line interface for
 * managing applications. Applications can be deployed, started, stopped, and monitored
 * through the console.
 * <p>
 * Optional features can be enabled via command-line arguments:
 * <ul>
 * <li>--rest-api - Enable REST API server (default port 8080)</li>
 * <li>--port &lt;number&gt; - Specify port for REST API</li>
 * <li>--web-console - Enable web console (requires --rest-api)</li>
 * <li>--jmx-port &lt;number&gt; - Enable JMX metrics on specified port</li>
 * <li>--prometheus - Enable Prometheus metrics (default port 9090)</li>
 * <li>--prometheus-port &lt;number&gt; - Specify port for Prometheus metrics</li>
 * <li>--watch-dir &lt;path&gt; - Enable filesystem watcher for auto-deployment</li>
 * </ul>
 * <p>
 * Supported commands:
 * <ul>
 * <li>deploy - Deploy an application from a JAR file</li>
 * <li>deploy-yaml - Deploy an application from a YAML descriptor</li>
 * <li>deploy-json - Deploy an application from a JSON descriptor</li>
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
 * <pre>{@code
 * // Start with REST API and web console
 * java -jar jplatform-launcher.jar --rest-api --web-console
 *
 * // Start with all features
 * java -jar jplatform-launcher.jar --rest-api --web-console --jmx-port 9999 --watch-dir /var/jplatform/apps
 *
 * // At the prompt:
 * jplatform> deploy myapp /path/to/app.jar com.example.MyApp
 * jplatform> deploy-yaml /path/to/app.yaml
 * jplatform> start myapp
 * jplatform> status myapp
 * jplatform> exit
 * }</pre>
 *
 * @see PlatformManager
 */
public class PlatformLauncher {

    private static final Logger logger = LoggerFactory.getLogger(PlatformLauncher.class);

    private final PlatformManager applicationManager;
    private final InMemoryMessageBus messageBus;
    private final ServiceRegistryImpl serviceRegistry;
    private final YamlDescriptorParser yamlParser;
    private final JsonDescriptorParser jsonParser;

    // Optional components
    private JdkHttpApiServer restApiServer;
    private JmxMetricsExporter jmxExporter;
    private PrometheusMetricsExporter prometheusExporter;
    private org.flossware.jplatform.otel.OpenTelemetryMetricsExporter otelExporter;
    private FileSystemDeploymentWatcher fileWatcher;

    private volatile boolean running = true;

    // Configuration
    private final LauncherConfig config;

    /**
     * Launcher configuration parsed from command-line arguments.
     */
    private static class LauncherConfig {
        boolean restApiEnabled = false;
        int restApiPort = 8080;
        boolean webConsoleEnabled = false;
        boolean jmxEnabled = false;
        int jmxPort = 9999;
        boolean prometheusEnabled = false;
        int prometheusPort = 9090;
        boolean otelEnabled = false;
        String otelEndpoint = "http://localhost:4317";
        boolean watcherEnabled = false;
        String watchDirectory = null;

        static LauncherConfig parse(String[] args) {
            // Check for --config flag first
            String configFile = "platform.yaml";
            for (int i = 0; i < args.length; i++) {
                if ("--config".equals(args[i]) && i + 1 < args.length) {
                    configFile = args[++i];
                    break;
                }
            }

            // Load platform configuration from file
            PlatformConfig platformConfig = PlatformConfig.load(configFile);

            // Merge command-line arguments (they override file settings)
            platformConfig.mergeCommandLineArgs(args);

            // Check for help flag
            for (String arg : args) {
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    printStartupHelp();
                    System.exit(0);
                }
            }

            // Convert to LauncherConfig
            return fromPlatformConfig(platformConfig);
        }

        static LauncherConfig fromPlatformConfig(PlatformConfig platformConfig) {
            LauncherConfig config = new LauncherConfig();

            config.restApiEnabled = platformConfig.getApi().isEnabled();
            config.restApiPort = platformConfig.getApi().getPort();

            config.jmxEnabled = platformConfig.getMetrics().getJmx().isEnabled();
            config.jmxPort = platformConfig.getMetrics().getJmx().getPort();

            config.prometheusEnabled = platformConfig.getMetrics().getPrometheus().isEnabled();
            config.prometheusPort = platformConfig.getMetrics().getPrometheus().getPort();

            config.otelEnabled = platformConfig.getMetrics().getOpentelemetry().isEnabled();
            config.otelEndpoint = platformConfig.getMetrics().getOpentelemetry().getEndpoint();

            config.watcherEnabled = platformConfig.getWatcher().isEnabled();
            config.watchDirectory = platformConfig.getWatcher().getWatchDirectory();

            // Web console is enabled if API is enabled (same as before)
            config.webConsoleEnabled = config.restApiEnabled;

            return config;
        }

        private static void printStartupHelp() {
            System.out.println("JPlatform Launcher");
            System.out.println();
            System.out.println("Usage: java -jar jplatform-launcher.jar [options]");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --config <file>         Load configuration from YAML file (default: platform.yaml)");
            System.out.println("  --rest-api              Enable REST API server (default port 8080)");
            System.out.println("  --port <number>         Specify port for REST API (implies --rest-api)");
            System.out.println("  --web-console           Enable web console (implies --rest-api)");
            System.out.println("  --jmx-port <number>     Enable JMX metrics on specified port");
            System.out.println("  --prometheus            Enable Prometheus metrics (default port 9090)");
            System.out.println("  --prometheus-port <n>   Specify port for Prometheus metrics");
            System.out.println("  --watch-dir <path>      Enable filesystem watcher for auto-deployment");
            System.out.println("  --help, -h              Show this help message");
            System.out.println();
            System.out.println("Examples:");
            System.out.println("  java -jar jplatform-launcher.jar");
            System.out.println("  java -jar jplatform-launcher.jar --config production.yaml");
            System.out.println("  java -jar jplatform-launcher.jar --rest-api --web-console");
            System.out.println("  java -jar jplatform-launcher.jar --rest-api --port 9090 --jmx-port 9999");
            System.out.println("  java -jar jplatform-launcher.jar --prometheus --watch-dir /var/jplatform/apps");
        }
    }

    /**
     * Creates a new platform launcher with the specified configuration.
     *
     * @param config the launcher configuration
     */
    public PlatformLauncher(LauncherConfig config) {
        this.config = config;

        logger.info("Initializing JPlatform...");

        // Initialize core components
        this.messageBus = new InMemoryMessageBus();
        this.serviceRegistry = new ServiceRegistryImpl();
        this.applicationManager = new ApplicationManager(messageBus, serviceRegistry);

        // Initialize parsers
        this.yamlParser = new YamlDescriptorParser();
        this.jsonParser = new JsonDescriptorParser();

        // Initialize optional components
        initializeOptionalComponents();

        logger.info("JPlatform initialized successfully");
    }

    /**
     * Initializes optional components based on configuration.
     */
    private void initializeOptionalComponents() {
        // Initialize REST API server
        if (config.restApiEnabled) {
            try {
                logger.info("Initializing REST API on port {}...", config.restApiPort);

                ApiServerConfig apiConfig = ApiServerConfig.builder()
                        .port(config.restApiPort)
                        .bindAddress("0.0.0.0")
                        .enableAuth(false) // Disabled for simplicity
                        .build();

                restApiServer = new JdkHttpApiServer(apiConfig, applicationManager);
                restApiServer.start();

                logger.info("REST API started at http://localhost:{}/api", config.restApiPort);

                if (config.webConsoleEnabled) {
                    logger.info("Web Console available at http://localhost:{}/console", config.restApiPort);
                }
            } catch (Exception e) {
                logger.error("Failed to start REST API server", e);
                throw new RuntimeException("Failed to start REST API server", e);
            }
        }

        // Initialize JMX metrics exporter
        if (config.jmxEnabled) {
            try {
                logger.info("Initializing JMX metrics on port {}...", config.jmxPort);

                JmxExporterConfig jmxConfig = JmxExporterConfig.builder()
                        .enabled(true)
                        .port(config.jmxPort)
                        .domain("org.flossware.jplatform")
                        .build();

                jmxExporter = new JmxMetricsExporter(jmxConfig, applicationManager);
                jmxExporter.start();

                // Register lifecycle hooks to auto-register/unregister applications
                addJmxLifecycleHooks();

                logger.info("JMX metrics started. Connect with: jconsole localhost:{}", config.jmxPort);
            } catch (Exception e) {
                logger.error("Failed to start JMX metrics exporter", e);
                throw new RuntimeException("Failed to start JMX metrics exporter", e);
            }
        }

        // Initialize Prometheus metrics exporter
        if (config.prometheusEnabled) {
            try {
                logger.info("Initializing Prometheus metrics on port {}...", config.prometheusPort);

                PrometheusExporterConfig prometheusConfig = PrometheusExporterConfig.builder()
                        .port(config.prometheusPort)
                        .path("/metrics")
                        .build();

                prometheusExporter = new PrometheusMetricsExporter(prometheusConfig);
                prometheusExporter.start();

                logger.info("Prometheus metrics started. Metrics available at: http://localhost:{}/metrics",
                        config.prometheusPort);
            } catch (Exception e) {
                logger.error("Failed to start Prometheus metrics exporter", e);
                throw new RuntimeException("Failed to start Prometheus metrics exporter", e);
            }
        }

        // Initialize OpenTelemetry metrics exporter
        if (config.otelEnabled) {
            try {
                logger.info("Initializing OpenTelemetry metrics with endpoint: {}", config.otelEndpoint);

                otelExporter = new org.flossware.jplatform.otel.OpenTelemetryMetricsExporter(config.otelEndpoint);
                otelExporter.start();

                logger.info("OpenTelemetry metrics started. Exporting to: {}", config.otelEndpoint);
            } catch (Exception e) {
                logger.error("Failed to start OpenTelemetry metrics exporter", e);
                throw new RuntimeException("Failed to start OpenTelemetry metrics exporter", e);
            }
        }

        // Initialize filesystem watcher
        if (config.watcherEnabled) {
            try {
                logger.info("Initializing filesystem watcher for directory: {}", config.watchDirectory);

                Path watchPath = Paths.get(config.watchDirectory);

                WatcherConfig watcherConfig = WatcherConfig.builder()
                        .watchDirectory(watchPath)
                        .autoStart(true)
                        .autoDeploy(true)
                        .fileExtensions(Set.of("yaml", "json"))
                        .debounceMillis(500)
                        .build();

                fileWatcher = new FileSystemDeploymentWatcher(watcherConfig);

                // Set up auto-deployment handler with parser map
                Map<String, ApplicationDescriptorParser> parsers = new HashMap<>();
                parsers.put("yaml", yamlParser);
                parsers.put("json", jsonParser);

                AutoDeploymentHandler handler = new AutoDeploymentHandler(
                        applicationManager,
                        parsers,
                        watcherConfig
                );

                fileWatcher.addListener(handler);
                fileWatcher.start();

                logger.info("Filesystem watcher started. Drop YAML/JSON files in: {}", config.watchDirectory);
            } catch (Exception e) {
                logger.error("Failed to start filesystem watcher", e);
                throw new RuntimeException("Failed to start filesystem watcher", e);
            }
        }
    }

    /**
     * Adds lifecycle hooks to automatically register/unregister applications with JMX.
     */
    private void addJmxLifecycleHooks() {
        // Note: This would require adding lifecycle listener support to ApplicationManager
        // For now, we'll register applications manually in the deploy commands
        // TODO: Add ApplicationLifecycleListener interface to jplatform-api
    }

    /**
     * Starts the platform and begins the interactive console.
     * <p>
     * This method runs in a loop, reading commands from standard input and
     * dispatching them to the appropriate handlers. The loop continues until
     * the 'exit' or 'quit' command is received.
     */
    public void start() {
        printWelcome();

        Scanner scanner = new Scanner(System.in);

        while (running) {
            System.out.print("\njplatform> ");
            System.out.flush();

            // Check if input is available (handles EOF/closed stdin)
            if (!scanner.hasNextLine()) {
                logger.info("Standard input closed, entering server mode");
                System.out.println("\nNo interactive console available. Platform running in server mode.");
                System.out.println("Use REST API or web console to manage applications.");
                System.out.println("Press Ctrl+C to shutdown.");
                break;
            }

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

        // If console exited due to EOF, keep platform running in server mode
        if (running) {
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                logger.info("Platform interrupted, shutting down...");
                handleExit();
            }
        }
    }

    private void printWelcome() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("  JPlatform v1.0");
        System.out.println("========================================");
        System.out.println();

        if (config.restApiEnabled) {
            System.out.println("  REST API:     http://localhost:" + config.restApiPort + "/api");
        }
        if (config.webConsoleEnabled) {
            System.out.println("  Web Console:  http://localhost:" + config.restApiPort + "/console");
        }
        if (config.jmxEnabled) {
            System.out.println("  JMX:          localhost:" + config.jmxPort);
        }
        if (config.watcherEnabled) {
            System.out.println("  Watch Dir:    " + config.watchDirectory);
        }

        System.out.println();
        System.out.println("Type 'help' for available commands");
    }

    private void handleCommand(String command, String args) throws Exception {
        switch (command) {
            case "help":
                printHelp();
                break;

            case "deploy":
                handleDeploy(args);
                break;

            case "deploy-yaml":
                handleDeployYaml(args);
                break;

            case "deploy-json":
                handleDeployJson(args);
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
        System.out.println("  deploy <appId> <jarFile> <mainClass>  - Deploy an application from JAR");
        System.out.println("  deploy-yaml <file>                    - Deploy from YAML descriptor");
        System.out.println("  deploy-json <file>                    - Deploy from JSON descriptor");
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

        // Register with JMX if enabled
        if (jmxExporter != null) {
            ApplicationContext context = applicationManager.getApplicationContext(appId);
            jmxExporter.registerApplication(appId, context);
        }

        // Register with Prometheus if enabled
        if (prometheusExporter != null) {
            ApplicationContext context = applicationManager.getApplicationContext(appId);
            prometheusExporter.registerApplication(appId, context);
        }

        System.out.println("Application deployed: " + appId);
    }

    private void handleDeployYaml(String args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Usage: deploy-yaml <file>");
            return;
        }

        String filePath = args.trim();
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("File not found: " + filePath);
            return;
        }

        ApplicationDescriptor descriptor = yamlParser.parseFile(file.toPath());
        applicationManager.deploy(descriptor);

        // Register with JMX if enabled
        if (jmxExporter != null) {
            ApplicationContext context = applicationManager.getApplicationContext(descriptor.getApplicationId());
            jmxExporter.registerApplication(descriptor.getApplicationId(), context);
        }

        // Register with Prometheus if enabled
        if (prometheusExporter != null) {
            ApplicationContext context = applicationManager.getApplicationContext(descriptor.getApplicationId());
            prometheusExporter.registerApplication(descriptor.getApplicationId(), context);
        }

        System.out.println("Application deployed from YAML: " + descriptor.getApplicationId());
    }

    private void handleDeployJson(String args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Usage: deploy-json <file>");
            return;
        }

        String filePath = args.trim();
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("File not found: " + filePath);
            return;
        }

        ApplicationDescriptor descriptor = jsonParser.parseFile(file.toPath());
        applicationManager.deploy(descriptor);

        // Register with JMX if enabled
        if (jmxExporter != null) {
            ApplicationContext context = applicationManager.getApplicationContext(descriptor.getApplicationId());
            jmxExporter.registerApplication(descriptor.getApplicationId(), context);
        }

        // Register with Prometheus if enabled
        if (prometheusExporter != null) {
            ApplicationContext context = applicationManager.getApplicationContext(descriptor.getApplicationId());
            prometheusExporter.registerApplication(descriptor.getApplicationId(), context);
        }

        System.out.println("Application deployed from JSON: " + descriptor.getApplicationId());
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

        // Unregister from JMX if enabled
        if (jmxExporter != null) {
            jmxExporter.unregisterApplication(appId);
        }

        // Unregister from Prometheus if enabled
        if (prometheusExporter != null) {
            prometheusExporter.unregisterApplication(appId);
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

        // Check if thread pool is available
        if (context.getThreadPool() != null) {
            System.out.println("  Thread Pool: " + context.getThreadPool().getStats());
        } else {
            System.out.println("  Thread Pool: Not configured");
        }

        // Check if resource monitoring is available
        if (context.getResourceMonitor() != null) {
            ResourceSnapshot snapshot = context.getResourceMonitor().getCurrentSnapshot();
            System.out.println("  Resources:");
            System.out.println("    CPU Time: " + (snapshot.getCpuTimeNanos() / 1_000_000_000.0) + "s");
            System.out.println("    Heap Used: " + (snapshot.getHeapUsedBytes() / 1024 / 1024) + " MB");
            System.out.println("    Thread Count: " + snapshot.getThreadCount());
        } else {
            System.out.println("  Resources: Monitoring not enabled");
        }
    }

    private void handleExit() {
        System.out.println("Shutting down platform...");
        running = false;

        // Shutdown in reverse order of initialization
        try {
            if (fileWatcher != null) {
                logger.info("Stopping filesystem watcher...");
                fileWatcher.stop();
                fileWatcher.close();
            }

            if (jmxExporter != null) {
                logger.info("Stopping JMX metrics exporter...");
                jmxExporter.stop();
            }

            if (prometheusExporter != null) {
                logger.info("Stopping Prometheus metrics exporter...");
                prometheusExporter.stop();
            }

            if (otelExporter != null) {
                logger.info("Stopping OpenTelemetry metrics exporter...");
                otelExporter.stop();
            }

            if (restApiServer != null) {
                logger.info("Stopping REST API server...");
                restApiServer.stop();
            }

            logger.info("Shutting down application manager...");
            applicationManager.shutdown();

            logger.info("Shutting down message bus...");
            messageBus.shutdown();

            System.out.println("Platform shutdown complete");

        } catch (Exception e) {
            logger.error("Error during shutdown", e);
            System.err.println("Error during shutdown: " + e.getMessage());
        }
    }

    /**
     * Main entry point for the platform.
     * <p>
     * Creates and starts the platform launcher. If a fatal error occurs during
     * initialization or execution, logs the error and exits with code 1.
     *
     * @param args command-line arguments for platform configuration
     */
    public static void main(String[] args) {
        try {
            LauncherConfig config = LauncherConfig.parse(args);
            PlatformLauncher launcher = new PlatformLauncher(config);
            launcher.start();
        } catch (Exception e) {
            logger.error("Fatal error in platform launcher", e);
            System.err.println("Fatal error: " + e.getMessage());
            System.exit(1);
        }
    }
}
