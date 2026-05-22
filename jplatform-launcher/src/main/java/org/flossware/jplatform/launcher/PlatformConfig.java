package org.flossware.jplatform.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Platform configuration loaded from platform.yaml file.
 * Supports loading configuration from YAML file with command-line overrides.
 */
public class PlatformConfig {

    private static final Logger logger = LoggerFactory.getLogger(PlatformConfig.class);
    private static final String DEFAULT_CONFIG_FILE = "platform.yaml";

    private ApiConfig api = new ApiConfig();
    private MetricsConfig metrics = new MetricsConfig();
    private WatcherConfig watcher = new WatcherConfig();

    public ApiConfig getApi() {
        return api;
    }

    public void setApi(ApiConfig api) {
        this.api = api;
    }

    public MetricsConfig getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsConfig metrics) {
        this.metrics = metrics;
    }

    public WatcherConfig getWatcher() {
        return watcher;
    }

    public void setWatcher(WatcherConfig watcher) {
        this.watcher = watcher;
    }

    public static class ApiConfig {
        private boolean enabled = false;
        private int port = 8080;
        private String bindAddress = "0.0.0.0";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getBindAddress() {
            return bindAddress;
        }

        public void setBindAddress(String bindAddress) {
            this.bindAddress = bindAddress;
        }
    }

    public static class MetricsConfig {
        private JmxConfig jmx = new JmxConfig();
        private PrometheusConfig prometheus = new PrometheusConfig();

        public JmxConfig getJmx() {
            return jmx;
        }

        public void setJmx(JmxConfig jmx) {
            this.jmx = jmx;
        }

        public PrometheusConfig getPrometheus() {
            return prometheus;
        }

        public void setPrometheus(PrometheusConfig prometheus) {
            this.prometheus = prometheus;
        }
    }

    public static class JmxConfig {
        private boolean enabled = false;
        private int port = 9999;
        private String domain = "org.flossware.jplatform";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }
    }

    public static class PrometheusConfig {
        private boolean enabled = false;
        private int port = 9090;
        private String path = "/metrics";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class WatcherConfig {
        private boolean enabled = false;
        private String watchDirectory = null;
        private boolean autoStart = true;
        private boolean autoDeploy = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWatchDirectory() {
            return watchDirectory;
        }

        public void setWatchDirectory(String watchDirectory) {
            this.watchDirectory = watchDirectory;
        }

        public boolean isAutoStart() {
            return autoStart;
        }

        public void setAutoStart(boolean autoStart) {
            this.autoStart = autoStart;
        }

        public boolean isAutoDeploy() {
            return autoDeploy;
        }

        public void setAutoDeploy(boolean autoDeploy) {
            this.autoDeploy = autoDeploy;
        }
    }

    /**
     * Load configuration from platform.yaml file if it exists.
     * Returns default configuration if file doesn't exist.
     *
     * @return loaded configuration or default
     */
    public static PlatformConfig load() {
        return load(DEFAULT_CONFIG_FILE);
    }

    /**
     * Load configuration from specified file.
     * Returns default configuration if file doesn't exist.
     *
     * @param configFile path to configuration file
     * @return loaded configuration or default
     */
    public static PlatformConfig load(String configFile) {
        Path configPath = Paths.get(configFile);

        if (!Files.exists(configPath)) {
            logger.info("Configuration file {} not found, using defaults", configFile);
            return new PlatformConfig();
        }

        try {
            logger.info("Loading configuration from {}", configFile);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            PlatformConfig config = mapper.readValue(new File(configFile), PlatformConfig.class);
            logger.info("Configuration loaded successfully");
            return config;
        } catch (IOException e) {
            logger.error("Failed to load configuration from {}: {}", configFile, e.getMessage());
            logger.warn("Using default configuration");
            return new PlatformConfig();
        }
    }

    /**
     * Merge command-line arguments into this configuration.
     * Command-line arguments override file settings.
     *
     * @param args command-line arguments
     */
    public void mergeCommandLineArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--rest-api":
                    api.setEnabled(true);
                    break;

                case "--port":
                    if (i + 1 < args.length) {
                        api.setPort(Integer.parseInt(args[++i]));
                        api.setEnabled(true);
                    }
                    break;

                case "--web-console":
                    api.setEnabled(true);
                    break;

                case "--jmx-port":
                    if (i + 1 < args.length) {
                        metrics.getJmx().setPort(Integer.parseInt(args[++i]));
                        metrics.getJmx().setEnabled(true);
                    }
                    break;

                case "--prometheus":
                    metrics.getPrometheus().setEnabled(true);
                    break;

                case "--prometheus-port":
                    if (i + 1 < args.length) {
                        metrics.getPrometheus().setPort(Integer.parseInt(args[++i]));
                        metrics.getPrometheus().setEnabled(true);
                    }
                    break;

                case "--watch-dir":
                    if (i + 1 < args.length) {
                        watcher.setWatchDirectory(args[++i]);
                        watcher.setEnabled(true);
                    }
                    break;

                case "--config":
                    // Config file already loaded, skip
                    if (i + 1 < args.length) {
                        i++; // Skip the config file path
                    }
                    break;
            }
        }
    }
}
