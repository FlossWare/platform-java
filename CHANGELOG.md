# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to X.Y semantic versioning.

## [Unreleased]

### Added
- ThreadPoolConfig.Builder now validates parameters (issue #44)
- ApplicationContext.getDeployedAt() to track actual deployment timestamp
- PlatformManager interface for dependency inversion (issue #36)
- Native application execution support (issue #46)
  - NativeProcessLauncher for managing native executable processes
  - ApplicationManager now detects descriptor.isNativeImage() flag and routes to process launcher
  - ApplicationContextImpl tracks native Process for native image applications
  - Native process lifecycle management (start, stop with graceful shutdown)
  - Output redirection from native processes to platform logging
  - Support for GraalVM native images and other compiled executables
- Container orchestration support (Docker/Podman/LXC)
  - ContainerLauncher for managing containerized applications
  - Support for Docker, Podman, and LXC container runtimes
  - Automatic image pulling for Docker/Podman
  - Container lifecycle management (launch, stop, remove)
  - Port mappings, volume mounts, environment variables, network configuration
  - Container output redirection to platform logging
  - Configuration via properties: container.runtime, container.image, container.ports, container.volumes
- Swing Desktop UI (jplatform-swing-ui module)
  - Native desktop management interface using Java Swing
  - Application deployment via file chooser dialog
  - Lifecycle controls: Deploy, Start, Stop, Undeploy
  - Real-time metrics table (CPU time, heap usage, thread count)
  - Auto-refresh every 2 seconds
  - Input validation for application ID and main class
  - Native look and feel for the operating system

### Fixed
- ThreadPoolConfig.Builder missing parameter validation (issue #44)
- ApplicationResponseDTO returns actual deployment time instead of current time (issue #45)
- Tight coupling - ApplicationManager now implements PlatformManager interface (issue #36)
- Updated all dependent modules to use PlatformManager interface instead of concrete ApplicationManager (issue #36)
- ApplicationManager synchronization bottleneck - replaced class-level locking with fine-grained per-application locking (issue #37)

### Performance
- ApplicationManager now uses per-application ReentrantLock instead of synchronized methods
- Enables parallel operations on different applications
- Significantly improved scalability and throughput

### Testing
- Added comprehensive unit tests for jplatform-core module
- Created ApplicationManagerConcurrencyTest to verify fine-grained locking
- Added tests for DependencyResolver, ApplicationReloader, NativeLibraryLoader, ClassLoaderVersion
- Added tests for NativeProcessLauncher and ContainerLauncher
- Added tests for jplatform-swing-ui module (SwingConsoleTest, DeployDialogTest)
- Test coverage at 39% instruction coverage (72 tests across 11 test classes)
- Focus on API contracts and configuration validation
- Integration tests require actual executables/containers
- Swing UI tests limited to API validation due to headless environment constraints

### Documentation
- NATIVE_EXECUTION.md - Complete guide to native process deployment (GraalVM, Rust, Go, C++)
- CONTAINER_DEPLOYMENT.md - Complete guide to container orchestration (Docker/Podman/LXC)
- jplatform-core/README.md - Module documentation with all components and deployment modes
- jplatform-swing-ui/README.md - Module documentation for Swing desktop UI
- Updated README.md with Swing UI in management interfaces and deployment mechanisms
- Updated QUICKSTART.md with Swing UI launch instructions
- All classes have complete Javadoc (NativeProcessLauncher, ContainerLauncher, SwingConsole, DeployDialog)

### Changed
- CI/CD pipeline now active with automated version bumping and artifact publishing

## [1.1] - 2024-05-23

### Added
- Initial plugin ecosystem implementation
- Storage plugins: S3, Database, Redis
- Service registry plugins: Consul, Etcd, Eureka
- Configuration plugins: Consul, Etcd, Vault, ZooKeeper
- Cluster plugins: Consul, Etcd, Redis, ZooKeeper
- REST API plugin: Netty-based high-performance server
- Comprehensive README documentation for all modules
- Test coverage documentation explaining coverage percentages
- CI/CD pipeline with automatic versioning and deployment
- GitHub Actions workflow for build, test, and deploy
- PackageCloud.io integration for artifact publishing

### Changed
- Removed all wildcard imports, replaced with explicit imports
- Added X.Y semantic versioning with auto-increment on main branch

### Documentation
- All modules have comprehensive JavaDoc coverage
- README files for all 9 plugin modules
- Test coverage explanations for integration modules
- CI/CD documentation in ci/README.md

## [1.0] - Earlier

### Added
- Core platform modules
- Classloader isolation
- Thread pool management
- Security framework
- Monitoring infrastructure
- JMS messaging support
- Configuration system
- File system watcher
- Deployment management
- REST API framework
- Web console
- JMX metrics
- Prometheus metrics
- OpenTelemetry integration
- Application launcher
- Sample applications
