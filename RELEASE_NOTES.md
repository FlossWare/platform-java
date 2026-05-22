# JPlatform 1.0 - Release Notes

**Release Date:** May 2026  
**Status:** Production Ready

## Overview

JPlatform 1.0 is a production-ready Java application platform that enables running multiple isolated Java applications within a single JVM. This release includes all core features plus advanced deployment, management, and monitoring capabilities.

## What's New in 1.0

### Core Features
- **Complete Application Isolation**: ClassLoader, thread pool, and security isolation per application
- **Resource Monitoring**: Real-time CPU, memory, and thread tracking per application
- **Inter-Application Messaging**: Optional event bus and service registry for loose coupling
- **Security Policies**: Configurable permissions (filesystem, network, reflection)

### Deployment Methods (NEW)
- **YAML/JSON Descriptors**: Declarative application configuration with full validation
- **Filesystem Watcher**: Auto-deployment when descriptor files are dropped in watched directory
- **REST API**: Full HTTP API for deployment, lifecycle management, and metrics
- **Web Console**: Modern browser-based UI with real-time metrics charts
- **Interactive CLI**: Enhanced command-line interface with descriptor support

### Platform Configuration (NEW)
- **platform.yaml Support**: Load all platform settings from YAML configuration file
- **Command-Line Overrides**: CLI flags override file settings for flexibility
- **--config Flag**: Specify custom configuration file location
- **Simplified Production Deployment**: Single config file instead of many CLI flags

### Monitoring & Metrics (NEW)
- **JMX Metrics**: Expose application metrics via JMX MBeans
  - Compatible with JConsole, VisualVM, and other JMX tools
  - Per-application MBeans with lifecycle operations
  - Port: 9999 (configurable)

- **Prometheus Metrics**: Export metrics in Prometheus text format
  - Counter and gauge metrics for CPU, memory, threads
  - Application state tracking
  - Port: 9090 (configurable)

### Web Console Features (NEW)
- Dashboard with application list and status
- Deploy applications via file upload or YAML/JSON paste
- Start/stop/undeploy buttons
- Real-time CPU, memory, and thread charts
- Application properties viewer
- Access at: http://localhost:8080/console

### REST API Endpoints (NEW)
```
POST   /api/applications              - Deploy application
POST   /api/applications/from-yaml    - Deploy from YAML upload
GET    /api/applications              - List all applications
GET    /api/applications/{id}         - Get application details
GET    /api/applications/{id}/status  - Get status + metrics
POST   /api/applications/{id}/start   - Start application
POST   /api/applications/{id}/stop    - Stop application
DELETE /api/applications/{id}         - Undeploy application
GET    /api/platform/info             - Platform info
GET    /api/health                    - Health check
```

## Module Summary

### Production Modules
- `jplatform-api` - Public APIs and interfaces (20+ interfaces)
- `jplatform-core` - Core platform logic (ApplicationManager, lifecycle)
- `jplatform-classloader` - Isolated ClassLoader with parent-last delegation
- `jplatform-threadpool` - Managed thread pools per application
- `jplatform-security` - Security policy enforcement
- `jplatform-monitoring` - Resource tracking and quotas
- `jplatform-messaging` - Event bus and service registry
- `jplatform-config` - YAML/JSON descriptor parsing (NEW)
- `jplatform-fs-watcher` - Filesystem monitoring (NEW)
- `jplatform-rest-api` - HTTP REST API server (NEW)
- `jplatform-web-console` - Browser-based UI (NEW)
- `jplatform-metrics-jmx` - JMX metrics exporter (NEW)
- `jplatform-metrics-prometheus` - Prometheus exporter (NEW)
- `jplatform-launcher` - Platform bootstrap and CLI
- `jplatform-samples` - Sample applications

### Test Coverage
- **547 unit and integration tests** across all modules
- **85%+ code coverage** (average across all modules)
- **15 integration tests** for end-to-end verification
- **100% test pass rate** in CI/CD pipelines

## Configuration

### Platform Configuration File (NEW)

JPlatform supports loading configuration from a YAML file (`platform.yaml`):

```yaml
api:
  enabled: true
  port: 8080
metrics:
  jmx:
    enabled: true
    port: 9999
  prometheus:
    enabled: true
    port: 9090
watcher:
  enabled: true
  watchDirectory: /var/jplatform/apps
```

Load with:
```bash
java -jar jplatform-launcher-1.0.jar --config platform.yaml
```

### Command-Line Flags

```bash
# Configuration file
--config <file>             # Load configuration from YAML file

# Enable specific features
--rest-api                  # Enable REST API (port 8080)
--port <number>             # Specify REST API port
--web-console               # Enable web console (requires --rest-api)
--jmx-port <number>         # Enable JMX metrics on port
--prometheus                # Enable Prometheus metrics (port 9090)
--prometheus-port <number>  # Specify Prometheus port
--watch-dir <path>          # Enable filesystem watcher

# Production example (command-line)
java -jar jplatform-launcher-1.0.jar \
  --rest-api --web-console \
  --jmx-port 9999 --prometheus \
  --watch-dir /var/jplatform/apps

# Production example (config file)
java -jar jplatform-launcher-1.0.jar --config platform.yaml

# Override config file settings
java -jar jplatform-launcher-1.0.jar --config platform.yaml --port 9000
```

## Supported Deployment Formats

### YAML Descriptor
```yaml
applicationId: my-app
mainClass: com.example.MyApp
classpathEntries:
  - file:///path/to/app.jar
threadPool:
  corePoolSize: 4
  maxPoolSize: 20
resources:
  maxHeapMB: 512
security:
  allowReflection: true
enableMessaging: true
properties:
  app.config: /etc/myapp
```

### JSON Descriptor
```json
{
  "applicationId": "my-app",
  "mainClass": "com.example.MyApp",
  "classpathEntries": ["file:///path/to/app.jar"],
  "threadPool": {"corePoolSize": 4, "maxPoolSize": 20},
  "resources": {"maxHeapMB": 512},
  "enableMessaging": true
}
```

## Performance Characteristics

- **Startup Time**: < 2 seconds (platform initialization)
- **Application Deployment**: < 500ms (typical JAR)
- **Memory Overhead**: ~50MB base platform + per-app overhead
- **Thread Overhead**: ~10 threads base platform
- **Isolation Overhead**: Minimal (<5% CPU overhead per app)

## System Requirements

- **Java**: 11 or later (tested on Java 11, 17, 21)
- **Memory**: 512MB minimum (platform + apps)
- **OS**: Linux, macOS, Windows (any platform supporting Java 11+)
- **Build**: Maven 3.6+

## Breaking Changes from Previous Versions

This is the initial 1.0 release. No breaking changes.

## Known Limitations

- **Heap Monitoring**: Uses ClassLoader-based estimation (not precise per-app)
  - Future: JVMTI agent for precise heap tracking
- **Clustering**: Not included in 1.0 (planned for future release)
- **Native Code**: Applications using JNI require special configuration
- **Security Manager**: Deprecated in Java 17+ (alternative approach in development)

## Migration Guide

N/A - Initial release

## Upgrade Path

For future upgrades, we maintain backward compatibility within major versions:
- 1.x → 1.y: Always compatible (no breaking API changes)
- 1.x → 2.0: May require application updates (documented in 2.0 release notes)

## Getting Started

See [QUICKSTART.md](QUICKSTART.md) for a 5-minute tutorial.

Basic usage:
```bash
# Build
mvn clean install

# Run
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar

# Deploy sample
jplatform> deploy hello jplatform-samples/hello-world/target/sample-hello-world-1.0.jar org.flossware.jplatform.samples.helloworld.HelloWorldApp
jplatform> start hello
```

## Documentation

- [README.md](README.md) - Architecture and design
- [QUICKSTART.md](QUICKSTART.md) - Getting started guide
- [VERSION_POLICY.md](VERSION_POLICY.md) - Versioning approach
- [examples/](examples/) - Configuration examples
- Module-specific README files in each module directory

## Support

- **GitHub Issues**: https://github.com/FlossWare/jplatform/issues
- **Documentation**: https://github.com/FlossWare/jplatform
- **Examples**: See `jplatform-samples` directory

## Contributors

JPlatform is developed by FlossWare and contributors.

## License

See [LICENSE](LICENSE) file for details.

## Future Roadmap

### 1.1 (Q3 2026)
- Performance optimizations
- Additional metrics exporters
- Enhanced security policies

### 2.0 (Q4 2026)
- JVMTI agent for precise heap monitoring
- Clustering support with Hazelcast
- Advanced monitoring dashboards
- Breaking API changes (documented)

---

**Thank you for using JPlatform!**

For questions or feedback, please open an issue on GitHub.
