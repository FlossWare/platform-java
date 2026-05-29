# Contributing to platform-java

Thank you for your interest in contributing to platform-java! We welcome contributions from the community.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How to Contribute](#how-to-contribute)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Features](#suggesting-features)
- [Pull Request Process](#pull-request-process)
- [Development Setup](#development-setup)
- [Code Style](#code-style)
- [Testing Requirements](#testing-requirements)
- [Commit Message Guidelines](#commit-message-guidelines)
- [License](#license)

## Code of Conduct

This project adheres to a [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

## How to Contribute

There are many ways to contribute to platform-java:

- **Report bugs** - Help us identify and fix issues
- **Suggest features** - Share ideas for improvements
- **Write code** - Submit pull requests with bug fixes or new features
- **Improve documentation** - Help make our docs clearer and more comprehensive
- **Review pull requests** - Provide feedback on open PRs
- **Answer questions** - Help other users in discussions and issues

## Reporting Bugs

Before creating a bug report, please check the [existing issues](https://github.com/FlossWare/platform-java/issues) to avoid duplicates.

When filing a bug report, please include:

- **Clear title** - Brief description of the problem
- **Description** - Detailed explanation of the issue
- **Steps to reproduce** - Numbered steps to recreate the bug
- **Expected behavior** - What you expected to happen
- **Actual behavior** - What actually happened
- **Environment** - OS, Java version, platform-java version
- **Logs/Screenshots** - Any relevant output or error messages

**Example**:

```markdown
**Title**: ApplicationManager throws NPE when deploying with null descriptor

**Description**: Calling `manager.deploy(null)` causes a NullPointerException instead of throwing IllegalArgumentException.

**Steps to Reproduce**:
1. Create ApplicationManager instance
2. Call `manager.deploy(null)`
3. Observe NPE

**Expected**: Should throw IllegalArgumentException with message "descriptor cannot be null"

**Actual**: Throws NPE at line 123 of ApplicationManager.java

**Environment**:
- OS: Ubuntu 22.04
- Java: OpenJDK 21.0.1
- platform-java: 1.1

**Logs**:
```
java.lang.NullPointerException
    at org.flossware.jplatform.core.ApplicationManager.deploy(ApplicationManager.java:123)
```
```

## Suggesting Features

Feature requests are welcome! Before creating a feature request:

1. Check [existing issues](https://github.com/FlossWare/platform-java/issues) for similar requests
2. Consider if it fits the project's scope and goals
3. Think about backwards compatibility

When suggesting a feature, please include:

- **Use case** - Why is this feature needed?
- **Proposed solution** - How should it work?
- **Alternatives** - What other approaches did you consider?
- **Examples** - Code examples or mockups if applicable

## Pull Request Process

### 1. Fork and Clone

```bash
# Fork the repository on GitHub, then:
git clone https://github.com/YOUR-USERNAME/platform-java.git
cd platform-java
git remote add upstream https://github.com/FlossWare/platform-java.git
```

### 2. Create a Branch

Use a descriptive branch name:

```bash
git checkout -b fix/issue-123-npe-in-deploy
# or
git checkout -b feature/add-jwt-authentication
```

Branch naming convention:
- `fix/issue-XXX-description` - Bug fixes
- `feature/description` - New features
- `docs/description` - Documentation changes
- `refactor/description` - Code refactoring

### 3. Make Your Changes

- Write clean, readable code
- Follow the [code style](#code-style) guidelines
- Add tests for new functionality
- Update documentation as needed
- Keep commits focused and atomic

### 4. Test Your Changes

```bash
# Run all tests
mvn clean verify

# Check code coverage (should be ≥93%)
mvn clean test jacoco:report
open target/site/jacoco/index.html

# Run quality checks
mvn checkstyle:check pmd:check spotbugs:check
```

All quality gates must pass before submitting a PR.

### 5. Commit Your Changes

Follow the [commit message guidelines](#commit-message-guidelines):

```bash
git add .
git commit -m "fix: resolve NPE when deploying null descriptor (#123)"
```

### 6. Push and Create PR

```bash
git push origin fix/issue-123-npe-in-deploy
```

Then create a Pull Request on GitHub with:

- **Clear title** - Summarize the change
- **Description** - Explain what and why
- **Issue reference** - Link to related issue(s)
- **Testing** - Describe how you tested
- **Screenshots** - For UI changes

**PR Template**:

```markdown
## Description
Fixes #123 - ApplicationManager now validates descriptor parameter and throws IllegalArgumentException instead of NPE.

## Changes
- Added null check in `ApplicationManager.deploy()`
- Added unit test for null descriptor
- Updated JavaDoc

## Testing
- Added `ApplicationManagerTest.testDeployNullDescriptor()`
- All existing tests pass
- Coverage: 94% (increased from 93%)

## Checklist
- [x] Tests added/updated
- [x] Documentation updated
- [x] Code follows style guidelines
- [x] All quality checks pass
```

### 7. Respond to Review Feedback

- Address all review comments
- Push additional commits to the same branch
- Request re-review when ready

### 8. Squash and Merge

Maintainers will squash commits when merging. Your PR title becomes the commit message, so make it clear and descriptive.

## Development Setup

### Prerequisites

- **Java 21+** - OpenJDK or Oracle JDK
- **Maven 3.9+** - Build tool
- **Git** - Version control

### Building from Source

```bash
# Clone the repository
git clone https://github.com/FlossWare/platform-java.git
cd platform-java

# Build all modules
mvn clean install

# Build specific module
cd jplatform-core
mvn clean install

# Skip tests (faster, but not recommended)
mvn clean install -DskipTests

# Run with all quality checks
mvn clean verify
```

### Running the Platform

```bash
# Basic launcher
java -jar jplatform-launcher/target/jplatform-launcher-1.1.jar

# With REST API
java -jar jplatform-launcher/target/jplatform-launcher-1.1.jar --rest-api

# With all features
java -jar jplatform-launcher/target/jplatform-launcher-1.1.jar \
  --rest-api --web-console --jmx-port 9999 --prometheus
```

### IDE Setup

#### IntelliJ IDEA

1. Open project: `File → Open → Select platform-java directory`
2. Import as Maven project
3. Enable annotation processing: `Settings → Build → Compiler → Annotation Processors`
4. Install Checkstyle plugin: `Settings → Plugins → Checkstyle-IDEA`
5. Configure Checkstyle: `Settings → Tools → Checkstyle → Configuration File → checkstyle.xml`

#### Eclipse

1. `File → Import → Maven → Existing Maven Projects`
2. Select `platform-java` directory
3. Install Checkstyle plugin from Marketplace
4. Configure: `Window → Preferences → Checkstyle → New → checkstyle.xml`

#### VS Code

1. Install extensions:
   - Language Support for Java
   - Maven for Java
   - Checkstyle for Java
2. Open folder: `File → Open Folder → platform-java`

## Code Style

### Java Code Style

We follow standard Java conventions with some specific rules:

#### 1. Formatting
- **Indentation**: 4 spaces (no tabs)
- **Line length**: 120 characters max
- **Braces**: K&R style (opening brace on same line)

```java
// Good
public void myMethod() {
    if (condition) {
        doSomething();
    }
}

// Bad
public void myMethod()
{
    if (condition)
    {
        doSomething();
    }
}
```

#### 2. Naming Conventions
- **Classes**: `PascalCase` (e.g., `ApplicationManager`)
- **Methods**: `camelCase` (e.g., `deployApplication()`)
- **Constants**: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRY_COUNT`)
- **Packages**: lowercase, no underscores (e.g., `org.flossware.jplatform.core`)

#### 3. Code Organization
- **One class per file**
- **Package-private by default**, public only when necessary
- **Final by default** for local variables and parameters
- **Static imports** only for constants and utility methods

#### 4. Comments and Documentation
- **JavaDoc** for all public classes and methods
- **No commented-out code** (use git history instead)
- **Explain WHY, not WHAT** - code should be self-documenting

```java
// Good - explains why
// Retry 3 times because network may be temporarily unavailable
private static final int MAX_RETRY_COUNT = 3;

// Bad - restates the obvious
// Maximum retry count is 3
private static final int MAX_RETRY_COUNT = 3;
```

#### 5. Error Handling
- **Don't catch generic Exception** - catch specific types
- **Don't swallow exceptions** - log or rethrow
- **Use meaningful error messages**

```java
// Good
try {
    deployApplication(descriptor);
} catch (ClassNotFoundException e) {
    throw new DeploymentException("Main class not found: " + descriptor.getMainClass(), e);
}

// Bad
try {
    deployApplication(descriptor);
} catch (Exception e) {
    // ignore
}
```

#### 6. Logging
- **Use SLF4J**, not System.out
- **Appropriate log levels**: DEBUG, INFO, WARN, ERROR
- **Parameterized messages** for better performance

```java
// Good
logger.info("Deploying application: {}", appId);

// Bad
System.out.println("Deploying application: " + appId);
```

### Checkstyle

All code must pass Checkstyle validation:

```bash
mvn checkstyle:check
```

Configuration: `checkstyle.xml` (based on Google Java Style)

Common violations to avoid:
- Wildcard imports (`import java.util.*;`)
- Missing `@Override` annotations
- Incorrect indentation
- Line too long (>120 chars)
- Missing JavaDoc on public methods

## Testing Requirements

### Test Coverage

- **Minimum coverage**: 93% instruction coverage (enforced by JaCoCo)
- **All new code must have tests**
- **Tests must be meaningful**, not just coverage boosters

### Writing Tests

#### 1. Test Structure

Follow the **Arrange-Act-Assert** pattern:

```java
@Test
void shouldDeployApplicationSuccessfully() {
    // Arrange
    ApplicationManager manager = new ApplicationManager();
    ApplicationDescriptor descriptor = createTestDescriptor();
    
    // Act
    manager.deploy(descriptor);
    
    // Assert
    assertEquals(ApplicationState.DEPLOYED, 
                 manager.getApplicationContext("test-app").getState());
}
```

#### 2. Test Naming

Use descriptive names following `should<ExpectedBehavior>When<StateUnderTest>` pattern:

```java
@Test
void shouldThrowExceptionWhenDescriptorIsNull() { ... }

@Test
void shouldStartApplicationWhenInDeployedState() { ... }

@Test
void shouldRejectDuplicateApplicationId() { ... }
```

#### 3. Test Organization

- **One test class per production class**
- **Group related tests** with `@Nested` classes
- **Use `@BeforeEach`** for common setup

```java
class ApplicationManagerTest {
    
    private ApplicationManager manager;
    
    @BeforeEach
    void setUp() {
        manager = new ApplicationManager();
    }
    
    @Nested
    class DeploymentTests {
        @Test
        void shouldDeployApplication() { ... }
        
        @Test
        void shouldRejectDuplicateId() { ... }
    }
    
    @Nested
    class LifecycleTests {
        @Test
        void shouldStartDeployedApplication() { ... }
    }
}
```

#### 4. Mocking

Use Mockito for mocking dependencies:

```java
@ExtendWith(MockitoExtension.class)
class ApplicationManagerTest {
    
    @Mock
    private ClassLoaderFactory classLoaderFactory;
    
    @InjectMocks
    private ApplicationManager manager;
    
    @Test
    void shouldCreateIsolatedClassLoader() {
        when(classLoaderFactory.createClassLoader(any()))
            .thenReturn(mock(ClassLoader.class));
        
        manager.deploy(descriptor);
        
        verify(classLoaderFactory).createClassLoader(any());
    }
}
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ApplicationManagerTest

# Run specific test method
mvn test -Dtest=ApplicationManagerTest#shouldDeployApplication

# Run tests with coverage
mvn clean test jacoco:report

# Skip tests (not recommended)
mvn install -DskipTests
```

## Commit Message Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style (formatting, no logic change)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks (build, dependencies)
- `perf`: Performance improvements

### Scope (Optional)

The module or component affected: `core`, `api`, `security`, `monitoring`, etc.

### Examples

```
feat(api): add JWT authentication support

Implements JWT-based authentication for REST API endpoints.
- Add JwtAuthFilter for token validation
- Add JwtConfig for configuration
- Update REST API documentation

Closes #311
```

```
fix(core): resolve NPE in ApplicationManager.deploy()

Add null check for descriptor parameter and throw
IllegalArgumentException with descriptive message.

Fixes #123
```

```
docs(readme): update quick start guide

Add Docker deployment instructions and update
REST API examples.
```

### Co-authored Commits

When pair programming or collaborating:

```
feat(security): implement role-based access control

Co-authored-by: Jane Doe <jane@example.com>
```

## License

By contributing to platform-java, you agree that your contributions will be licensed under the [GNU General Public License v3.0](LICENSE).

All contributed code must:
- Include appropriate copyright headers
- Be your original work or properly attributed
- Not violate any third-party licenses

---

## Questions?

- **Documentation**: See [README.md](README.md) and [docs/](docs/)
- **Issues**: Browse [existing issues](https://github.com/FlossWare/platform-java/issues)
- **Discussions**: Ask questions in [GitHub Discussions](https://github.com/FlossWare/platform-java/discussions)

Thank you for contributing to platform-java! 🎉
