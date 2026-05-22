package org.flossware.jplatform.core;

import org.flossware.jplatform.api.NativeLibrary;
import org.flossware.jplatform.api.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads native libraries for applications.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Detects current platform (OS + architecture)</li>
 *   <li>Filters libraries by platform compatibility</li>
 *   <li>Extracts libraries to app-isolated directory</li>
 *   <li>Returns library directory for adding to java.library.path</li>
 * </ul>
 *
 * <p>Libraries are extracted to:</p>
 * <pre>
 * /var/jplatform/natives/{applicationId}/
 * </pre>
 *
 * @since 2.0
 */
public class NativeLibraryLoader {

    private static final Logger logger = LoggerFactory.getLogger(NativeLibraryLoader.class);
    private static final String DEFAULT_BASE_PATH = "/var/jplatform/natives";

    private final String applicationId;
    private final Path basePath;
    private final Platform currentPlatform;

    /**
     * Creates a new native library loader.
     *
     * @param applicationId the application identifier
     */
    public NativeLibraryLoader(String applicationId) {
        this(applicationId, Paths.get(getConfiguredBasePath()));
    }

    /**
     * Creates a new native library loader with custom base path.
     *
     * @param applicationId the application identifier
     * @param basePath the base directory for native libraries
     */
    public NativeLibraryLoader(String applicationId, Path basePath) {
        this.applicationId = applicationId;
        this.basePath = basePath;
        this.currentPlatform = Platform.detect();

        logger.info("[{}] NativeLibraryLoader created for platform: {}", applicationId, currentPlatform);
    }

    /**
     * Loads native libraries for the current platform.
     *
     * <p>Filters the library list to only those compatible with the current platform,
     * then extracts them to the application's native library directory.</p>
     *
     * @param libraries the list of native libraries from the descriptor
     * @return the directory containing extracted native libraries
     * @throws IOException if extraction fails
     */
    public Path loadLibraries(List<NativeLibrary> libraries) throws IOException {
        Path libDir = basePath.resolve(applicationId);

        // Create directory if it doesn't exist
        if (!Files.exists(libDir)) {
            Files.createDirectories(libDir);
            logger.info("[{}] Created native library directory: {}", applicationId, libDir);
        }

        // Filter libraries for current platform
        List<NativeLibrary> compatibleLibs = filterByPlatform(libraries);

        logger.info("[{}] Found {} compatible native libraries for {}",
                applicationId, compatibleLibs.size(), currentPlatform);

        // Extract each library
        for (NativeLibrary lib : compatibleLibs) {
            extractLibrary(lib, libDir);
        }

        return libDir;
    }

    /**
     * Filters libraries to only those compatible with the current platform.
     *
     * @param libraries all native libraries
     * @return filtered list of compatible libraries
     */
    private List<NativeLibrary> filterByPlatform(List<NativeLibrary> libraries) {
        List<NativeLibrary> compatible = new ArrayList<>();

        for (NativeLibrary lib : libraries) {
            if (lib.getPlatform() == Platform.ANY || lib.getPlatform() == currentPlatform) {
                compatible.add(lib);
            } else {
                logger.debug("[{}] Skipping incompatible library {} ({})",
                        applicationId, lib.getName(), lib.getPlatform());
            }
        }

        return compatible;
    }

    /**
     * Extracts a single library to the target directory.
     *
     * @param library the library to extract
     * @param targetDir the directory to extract to
     * @throws IOException if extraction fails
     */
    private void extractLibrary(NativeLibrary library, Path targetDir) throws IOException {
        String libraryPath = library.getLibraryPath();
        URI libraryUri;

        try {
            libraryUri = URI.create(libraryPath);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid library path: " + libraryPath, e);
        }

        // Determine filename
        String filename = getFilename(libraryUri, library);
        Path targetFile = targetDir.resolve(filename);

        // Skip if already exists
        if (Files.exists(targetFile)) {
            logger.debug("[{}] Library already exists: {}", applicationId, targetFile);
            return;
        }

        // Copy library file
        if ("file".equals(libraryUri.getScheme())) {
            Path sourcePath = Paths.get(libraryUri);
            Files.copy(sourcePath, targetFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("[{}] Extracted native library: {} -> {}",
                    applicationId, library.getName(), targetFile);
        } else if ("http".equals(libraryUri.getScheme()) || "https".equals(libraryUri.getScheme())) {
            // Download from HTTP
            try (InputStream in = libraryUri.toURL().openStream()) {
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("[{}] Downloaded native library: {} from {}",
                        applicationId, library.getName(), libraryUri);
            }
        } else {
            throw new IOException("Unsupported library URI scheme: " + libraryUri.getScheme());
        }

        // Make executable on Unix-like systems
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            targetFile.toFile().setExecutable(true);
        }
    }

    /**
     * Determines the filename for a library.
     *
     * @param uri the library URI
     * @param library the library descriptor
     * @return the filename to use
     */
    private String getFilename(URI uri, NativeLibrary library) {
        // Try to get filename from URI path
        String path = uri.getPath();
        if (path != null && path.lastIndexOf('/') >= 0) {
            String filename = path.substring(path.lastIndexOf('/') + 1);
            if (!filename.isEmpty()) {
                return filename;
            }
        }

        // Construct from library name and platform
        String prefix = getLibraryPrefix();
        String extension = library.getPlatform().getLibExtension();
        return prefix + library.getName() + extension;
    }

    /**
     * Returns the platform-specific library prefix.
     *
     * @return library prefix ("lib" on Unix, "" on Windows)
     */
    private String getLibraryPrefix() {
        if (currentPlatform.getOs().equals("windows")) {
            return "";
        }
        return "lib";
    }

    /**
     * Cleans up native libraries for this application.
     *
     * @throws IOException if cleanup fails
     */
    public void cleanup() throws IOException {
        Path libDir = basePath.resolve(applicationId);

        if (Files.exists(libDir)) {
            Files.walk(libDir)
                    .sorted((a, b) -> b.compareTo(a))  // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.warn("[{}] Failed to delete: {}", applicationId, path, e);
                        }
                    });

            logger.info("[{}] Cleaned up native libraries", applicationId);
        }
    }

    /**
     * Returns the configured base path for native libraries.
     *
     * @return the base path
     */
    private static String getConfiguredBasePath() {
        return System.getProperty("jplatform.natives.dir", DEFAULT_BASE_PATH);
    }

    /**
     * Returns the current platform.
     *
     * @return detected platform
     */
    public Platform getCurrentPlatform() {
        return currentPlatform;
    }
}
