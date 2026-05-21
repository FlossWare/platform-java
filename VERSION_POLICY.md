# JPlatform Version Numbering Policy

## Version Format

JPlatform uses a **simple X.Y version numbering scheme** without patch versions or snapshot suffixes.

### Format: `X.Y`

- **X** = Major version (breaking changes, major features)
- **Y** = Minor version (new features, improvements, bug fixes)

### Examples

- `1.0` - Initial release
- `1.1` - Feature additions, improvements
- `1.2` - More features and fixes
- `2.0` - Major version with breaking changes

## No SNAPSHOT Versions

JPlatform does **not** use Maven SNAPSHOT versions. All builds are release versions.

### Rationale

1. **Simplicity**: Easier version management without snapshot/release distinction
2. **Clarity**: Every build is a release-quality build
3. **No Confusion**: No need to track snapshot vs release artifacts
4. **Cleaner**: Simpler Maven repository structure

## Version Lifecycle

### Current Version: `1.0`

This is the initial release with all core features implemented:
- Application lifecycle management
- ClassLoader isolation
- Thread pool management
- Resource monitoring
- Security policy enforcement
- Inter-application messaging
- Interactive launcher

### Future Versions

**1.1** - Planned features:
- Filesystem watcher for automatic deployment
- Configuration file support (YAML/JSON/XML)
- Additional deployment mechanisms

**1.2** - Planned features:
- REST API for remote deployment
- Web UI for management
- Enhanced monitoring

**2.0** - Major version (if needed):
- Breaking API changes (if any)
- Major architectural changes

## Maven Coordinates

```xml
<dependency>
    <groupId>org.flossware.jplatform</groupId>
    <artifactId>jplatform-api</artifactId>
    <version>1.0</version>
</dependency>
```

All JPlatform modules share the same version number.

## Artifact Naming

Built artifacts follow this pattern:

- JAR files: `{artifactId}-{version}.jar`
  - Example: `jplatform-launcher-1.0.jar`
  - Example: `sample-hello-world-1.0.jar`

- Module directories: `{artifactId}/target/`
  - Example: `jplatform-launcher/target/jplatform-launcher-1.0.jar`

## Version Compatibility

### API Compatibility

Within major version 1.x:
- Minor version updates (1.0 → 1.1) are **backward compatible**
- Applications built for 1.0 will work with 1.1, 1.2, etc.
- New features may be added but existing APIs remain unchanged

Major version updates (1.x → 2.x):
- May contain **breaking changes**
- Review changelog before upgrading
- Recompilation/retesting recommended

### Module Compatibility

All JPlatform modules are released together with the same version number.

**Do not mix versions** across modules:
- ✅ Correct: All modules at version 1.0
- ❌ Incorrect: jplatform-api at 1.0, jplatform-core at 1.1

## Release Process

1. **Development**: Work happens in main branch
2. **Version Bump**: Update all pom.xml files to next version
3. **Build & Test**: `mvn clean install` must succeed
4. **Tag Release**: Git tag with version number (e.g., `v1.0`)
5. **Publish**: Deploy to Maven repository (if applicable)
6. **Announce**: Update README.md and CHANGELOG.md

## Checking Current Version

### Via Maven

```bash
mvn help:evaluate -Dexpression=project.version -q -DforceStdout
```

### Via JAR Manifest

```bash
jar xf jplatform-launcher/target/jplatform-launcher-1.0.jar META-INF/MANIFEST.MF
grep Implementation-Version META-INF/MANIFEST.MF
```

### Via Code

All modules inherit from parent:

```xml
<parent>
    <groupId>org.flossware.jplatform</groupId>
    <artifactId>jplatform-parent</artifactId>
    <version>1.0</version>
</parent>
```

## Comparison with Other Schemes

### Semantic Versioning (X.Y.Z)
- JPlatform uses X.Y (no patch version)
- Simpler for this use case
- Patch-level changes rolled into minor versions

### Snapshot Versions (X.Y.Z-SNAPSHOT)
- JPlatform does not use snapshots
- Every build is release quality
- No distinction between development and release versions

### Date-Based Versioning (YYYY.MM.DD)
- JPlatform uses feature-based versioning
- Easier to track feature additions
- More meaningful version numbers

## Summary

JPlatform version numbering is:
- ✅ Simple (X.Y format)
- ✅ Clean (no SNAPSHOT suffix)
- ✅ Consistent (all modules same version)
- ✅ Clear (semantic major.minor meaning)

Current version: **1.0**
