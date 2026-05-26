package org.flossware.jplatform.fswatcher;

import org.flossware.jplatform.api.ApplicationDescriptor;
import org.flossware.jplatform.api.ApplicationDescriptorParser;
import org.flossware.jplatform.api.DeploymentEventListener;
import org.flossware.jplatform.api.ParseException;
import org.flossware.jplatform.api.PlatformManager;
import org.flossware.jplatform.api.WatcherConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

/**
 * Automatic deployment listener that handles descriptor file events by
 * deploying, redeploying, and undeploying applications via ApplicationManager.
 *
 * <p>This listener integrates the filesystem watcher with the application
 * management system. When descriptor files are detected, modified, or removed,
 * it automatically performs the appropriate application lifecycle operations:</p>
 * <ul>
 *   <li><b>onDescriptorDetected:</b> Parse → Deploy → Optionally Start</li>
 *   <li><b>onDescriptorModified:</b> Stop → Undeploy → Redeploy → Start</li>
 *   <li><b>onDescriptorRemoved:</b> Stop → Undeploy</li>
 *   <li><b>onError:</b> Log error details</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApplicationManager manager = new ApplicationManager();
 *
 * Map<String, ApplicationDescriptorParser> parsers = Map.of(
 *     "yaml", new YamlDescriptorParser(),
 *     "json", new JsonDescriptorParser()
 * );
 *
 * WatcherConfig config = WatcherConfig.builder()
 *     .watchDirectory(Paths.get("/var/jplatform/apps"))
 *     .autoStart(true)
 *     .autoDeploy(true)
 *     .build();
 *
 * AutoDeploymentHandler handler = new AutoDeploymentHandler(manager, parsers, config);
 *
 * DeploymentWatcher watcher = new FileSystemDeploymentWatcher(config);
 * watcher.addListener(handler);
 * watcher.start();
 * }</pre>
 *
 * <p>The handler maintains a registry of which descriptor files correspond to
 * which application IDs, enabling proper tracking for redeploy and undeploy
 * operations.</p>
 *
 * @see DeploymentEventListener
 * @see PlatformManager
 * @see ApplicationDescriptorParser
 * @see DescriptorRegistry
 */
public class AutoDeploymentHandler implements DeploymentEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AutoDeploymentHandler.class);

    private final PlatformManager applicationManager;
    private final Map<String, ApplicationDescriptorParser> parsers;
    private final WatcherConfig config;
    private final DescriptorRegistry registry;

    /**
     * Creates a new auto-deployment handler.
     *
     * @param applicationManager the platform manager for lifecycle operations
     * @param parsers map of file extensions to their corresponding parsers
     * @param config the watcher configuration
     * @throws NullPointerException if applicationManager or parsers is null
     */
    public AutoDeploymentHandler(
            PlatformManager applicationManager,
            Map<String, ApplicationDescriptorParser> parsers,
            WatcherConfig config) {

        if (applicationManager == null) {
            throw new NullPointerException("platformManager cannot be null");
        }
        if (parsers == null) {
            throw new NullPointerException("parsers cannot be null");
        }
        if (config == null) {
            throw new NullPointerException("config cannot be null");
        }

        this.applicationManager = applicationManager;
        this.parsers = parsers;
        this.config = config;
        this.registry = new DescriptorRegistry();
    }

    @Override
    public void onDescriptorDetected(Path descriptorFile) {
        if (!config.isAutoDeploy()) {
            logger.info("Auto-deploy is disabled, skipping: {}", descriptorFile);
            return;
        }

        logger.info("Descriptor detected: {}", descriptorFile);

        try {
            // Parse descriptor
            ApplicationDescriptor descriptor = parseDescriptor(descriptorFile);
            String appId = descriptor.getApplicationId();

            logger.info("Deploying application: {} (from {})", appId, descriptorFile);

            // Deploy application
            applicationManager.deploy(descriptor);
            registry.put(descriptorFile, appId);

            logger.info("Application deployed successfully: {}", appId);

            // Start if auto-start is enabled
            if (config.isAutoStart()) {
                logger.info("Auto-starting application: {}", appId);
                applicationManager.start(appId);
                logger.info("Application started successfully: {}", appId);
            }

        } catch (ParseException e) {
            logger.error("Failed to parse descriptor: {}", descriptorFile, e);
            onError(descriptorFile, e);
        } catch (Exception e) {
            logger.error("Failed to deploy application from: {}", descriptorFile, e);
            onError(descriptorFile, e);
        }
    }

    @Override
    public void onDescriptorModified(Path descriptorFile) {
        logger.info("Descriptor modified: {}", descriptorFile);

        // Check auto-deploy setting first to avoid undeploying without redeploying
        if (!config.isAutoDeploy()) {
            logger.info("Auto-deploy is disabled, skipping redeploy for: {}", descriptorFile);
            return;
        }

        String existingAppId = registry.get(descriptorFile);

        try {
            // Parse the updated descriptor
            ApplicationDescriptor descriptor = parseDescriptor(descriptorFile);
            String newAppId = descriptor.getApplicationId();

            // If there was a previous deployment, undeploy it first
            if (existingAppId != null) {
                logger.info("Undeploying existing application: {}", existingAppId);

                try {
                    applicationManager.stop(existingAppId);
                } catch (Exception e) {
                    logger.warn("Failed to stop application (may not be running): {}", existingAppId, e);
                }

                try {
                    applicationManager.undeploy(existingAppId);
                } catch (Exception e) {
                    logger.error("Failed to undeploy existing application: {}", existingAppId, e);
                }
            }

            // Deploy new version
            logger.info("Deploying updated application: {} (from {})", newAppId, descriptorFile);
            applicationManager.deploy(descriptor);
            registry.put(descriptorFile, newAppId);
            logger.info("Application deployed successfully: {}", newAppId);

            // Start if auto-start is enabled
            if (config.isAutoStart()) {
                logger.info("Auto-starting application: {}", newAppId);
                applicationManager.start(newAppId);
                logger.info("Application started successfully: {}", newAppId);
            }

        } catch (ParseException e) {
            logger.error("Failed to parse modified descriptor: {}", descriptorFile, e);
            onError(descriptorFile, e);
            // Keep old app running if parse fails
        } catch (Exception e) {
            logger.error("Failed to redeploy application from: {}", descriptorFile, e);
            onError(descriptorFile, e);
        }
    }

    @Override
    public void onDescriptorRemoved(Path descriptorFile) {
        logger.info("Descriptor removed: {}", descriptorFile);

        String appId = registry.remove(descriptorFile);

        if (appId == null) {
            logger.warn("No application registered for removed descriptor: {}", descriptorFile);
            return;
        }

        try {
            logger.info("Stopping application: {}", appId);

            try {
                applicationManager.stop(appId);
                logger.info("Application stopped: {}", appId);
            } catch (Exception e) {
                logger.warn("Failed to stop application (may not be running): {}", appId, e);
            }

            logger.info("Undeploying application: {}", appId);
            applicationManager.undeploy(appId);
            logger.info("Application undeployed successfully: {}", appId);

        } catch (Exception e) {
            logger.error("Failed to undeploy application: {}", appId, e);
            onError(descriptorFile, e);
        }
    }

    @Override
    public void onError(Path file, Exception error) {
        logger.error("Error processing descriptor file: {}", file, error);

        // Additional error handling could be added here, such as:
        // - Sending notifications
        // - Recording metrics
        // - Triggering alerts
    }

    /**
     * Parses a descriptor file using the appropriate parser based on file extension.
     *
     * @param descriptorFile the descriptor file to parse
     * @return the parsed ApplicationDescriptor
     * @throws ParseException if parsing fails or no parser is available for the file extension
     */
    private ApplicationDescriptor parseDescriptor(Path descriptorFile) throws ParseException {
        Path fileNamePath = descriptorFile.getFileName();
        if (fileNamePath == null) {
            throw new ParseException("Cannot determine filename from path: " + descriptorFile);
        }

        String filename = fileNamePath.toString().toLowerCase();
        String extension = getFileExtension(filename);

        if (extension == null || extension.isEmpty()) {
            throw new ParseException("Cannot determine file extension for: " + descriptorFile);
        }

        ApplicationDescriptorParser parser = parsers.get(extension);
        if (parser == null) {
            throw new ParseException("No parser available for extension: " + extension);
        }

        logger.debug("Using {} parser for: {}", extension, descriptorFile);
        return parser.parseFile(descriptorFile);
    }

    /**
     * Extracts the file extension (without leading dot) from a filename.
     *
     * @param filename the filename
     * @return the file extension in lowercase, or null if no extension
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return null;
    }

    /**
     * Returns the descriptor registry used by this handler.
     * Useful for testing and diagnostics.
     *
     * @return the descriptor registry
     */
    public DescriptorRegistry getRegistry() {
        return registry;
    }
}
