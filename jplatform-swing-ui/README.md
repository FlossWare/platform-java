# JPlatform Swing UI

Desktop management interface for JPlatform using Java Swing.

## Overview

The `jplatform-swing-ui` module provides a native desktop application for managing JPlatform instances. It offers a graphical alternative to the web console and REST API for users who prefer a desktop application.

## Features

- **Application Deployment**: Browse and select JAR files via native file chooser
- **Lifecycle Management**: Start, stop, and undeploy applications with button clicks
- **Real-Time Metrics**: View CPU time, heap usage, and thread count for running applications
- **Auto-Refresh**: Application table updates automatically every 2 seconds
- **Status Monitoring**: Visual indicators for application states (DEPLOYED, RUNNING, STOPPED, FAILED)
- **Native Look and Feel**: Uses system-native UI components

## User Interface

The Swing console consists of:

### Application Table
5-column table showing:
- Application ID
- Current State
- CPU Time (milliseconds)
- Heap Usage (megabytes)
- Thread Count

### Control Buttons
- **Deploy**: Opens file chooser to select JAR, then prompts for Application ID and Main Class
- **Start**: Starts the selected application
- **Stop**: Stops the selected application
- **Undeploy**: Undeploys the selected application (with confirmation dialog)
- **Refresh**: Manually refreshes the application list

### Status Bar
Displays current operation status and application count.

## Usage

### As a Standalone Application

Run the Swing console as the main class:

```bash
java -cp jplatform-swing-ui-1.1.jar:jplatform-api-1.1.jar:jplatform-core-1.1.jar \
    org.flossware.jplatform.swing.SwingConsole
```

Or with the JAR manifest (configured with Main-Class):

```bash
java -jar jplatform-swing-ui-1.1.jar
```

### Programmatically

```java
import org.flossware.jplatform.api.PlatformManager;
import org.flossware.jplatform.core.ApplicationManager;
import org.flossware.jplatform.swing.SwingConsole;

public class Main {
    public static void main(String[] args) {
        PlatformManager manager = new ApplicationManager();
        SwingConsole console = new SwingConsole(manager);
        console.show();
        
        // Platform runs until console is closed
    }
}
```

### Shutting Down

```java
console.shutdown(); // Stops auto-refresh and disposes the window
```

## Deployment Dialog

When deploying a new application, a dialog prompts for:

1. **Application ID**: Unique identifier for the application (required, alphanumeric)
2. **Main Class**: Fully-qualified main class name (required, validated format)

The JAR file path is displayed (read-only) for reference.

Input validation:
- Application ID: Cannot be empty
- Main Class: Must match Java class name pattern (e.g., `com.example.MyApp`)

## Testing Notes

Due to the graphical nature of Swing components, comprehensive unit testing requires a display environment. In headless CI/CD environments, tests are limited to:
- API contract validation (null parameter checks)
- Class loading verification

Full UI integration testing must be performed manually or in environments with display capabilities (X11, Wayland, etc.).

## Dependencies

- `jplatform-api` - Platform management API
- `slf4j-api` - Logging API
- Java 21+ with Swing support

## Test Coverage

- **Tests**: 2
- **Coverage**: Limited due to headless testing constraints

Manual testing covers:
- Application deployment via file chooser
- Start/stop/undeploy operations
- Metrics display and auto-refresh
- Error handling and validation dialogs

## Limitations

- Headless environments (CI/CD servers) cannot run Swing UI
- Automated UI testing requires display server or mocking frameworks
- Test coverage metrics do not reflect full manual test coverage

## See Also

- [Web Console](../jplatform-web-console/README.md) - Browser-based alternative
- [REST API](../jplatform-rest-api/README.md) - Programmatic management interface
- [Main README](../README.md) - Platform overview
