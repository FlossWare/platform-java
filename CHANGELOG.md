# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to X.Y semantic versioning.

## [Unreleased]

### Added
- ThreadPoolConfig.Builder now validates parameters (issue #44)
- ApplicationContext.getDeployedAt() to track actual deployment timestamp

### Fixed
- ThreadPoolConfig.Builder missing parameter validation (issue #44)
- ApplicationResponseDTO returns actual deployment time instead of current time (issue #45)

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
