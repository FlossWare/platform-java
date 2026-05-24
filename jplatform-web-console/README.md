# JPlatform Web Console

Browser-based management UI for JPlatform with real-time metrics and interactive charts.

## Overview

The `jplatform-web-console` module provides a modern web-based interface for managing JPlatform instances. Built with vanilla JavaScript and Chart.js, it offers a rich graphical experience accessible from any web browser.

## Features

- **Application Deployment**: Deploy applications via web form with validation
- **Lifecycle Management**: Start, stop, and undeploy applications with button clicks
- **Real-Time Metrics**: Live charts showing CPU usage, memory consumption, and thread count
- **Application Table**: Sortable table displaying all applications and their states
- **Platform Dashboard**: Overview of platform status and running applications
- **Auto-Refresh**: Metrics and application list update automatically
- **Responsive Design**: Works on desktop, tablet, and mobile browsers
- **No Backend Framework**: Pure HTML/CSS/JavaScript, served as static resources

## User Interface

The web console provides several sections:

### Platform Information
- Platform version
- Total applications count
- Running applications count  
- System uptime

### Application Management
- **Deploy Form**: Input fields for application ID, name, version, artifact path
- **Application Table**: Shows ID, Name, State, Version, Actions
- **Action Buttons**: Start, Stop, Undeploy per application

### Metrics Charts
- **CPU Time Chart**: Line chart showing CPU usage over time per application
- **Heap Usage Chart**: Line chart showing memory consumption trends
- **Thread Count Chart**: Line chart showing active threads

### Connection Status
- Visual indicator showing API connection status
- Auto-reconnect on connection loss

## Usage

### Starting the Web Console

The web console requires the REST API to be running:

```bash
# Start platform with REST API and web console
java -jar jplatform-launcher-1.1.jar --rest-api --web-console

# Or programmatically:
```

```java
import org.flossware.jplatform.api.PlatformManager;
import org.flossware.jplatform.core.ApplicationManager;
import org.flossware.jplatform.rest.JdkHttpApiServer;

public class Main {
    public static void main(String[] args) throws IOException {
        PlatformManager manager = new ApplicationManager();
        JdkHttpApiServer server = new JdkHttpApiServer(manager, 8080);
        server.start();
        
        System.out.println("Web console available at http://localhost:8080/console");
    }
}
```

### Accessing the Console

Open your browser to:

```
http://localhost:8080/console
```

Or if running on a custom port:

```
http://localhost:<port>/console
```

### Deploying an Application

1. Click "Deploy New Application" button
2. Fill in the deployment form:
   - **Application ID**: Unique identifier (e.g., `my-app`)
   - **Application Name**: Human-readable name
   - **Version**: Semantic version (e.g., `1.0.0`)
   - **Artifact Path**: Path to JAR file (e.g., `file:///path/to/app.jar`)
3. Click "Deploy"
4. Application appears in the table with state "DEPLOYED"

### Managing Applications

For each application in the table:
- **Start**: Click the green "Start" button (only available when DEPLOYED or STOPPED)
- **Stop**: Click the yellow "Stop" button (only available when RUNNING)
- **Undeploy**: Click the red "Undeploy" button (requires confirmation)

### Viewing Metrics

Metrics charts automatically update every 2 seconds:
- **CPU Time**: Shows accumulated CPU time in milliseconds
- **Heap Usage**: Shows current heap consumption in megabytes
- **Thread Count**: Shows active thread count per application

Each application has its own colored line in the charts.

## Architecture

### Frontend Components

- **index.html**: Main HTML structure
- **static/dashboard.css**: Stylesheet for UI components
- **static/app.js**: JavaScript application logic and API client

### Backend Components

- **WebConsoleHandler.java**: HTTP handler that serves static resources
- Integrated with `JdkHttpApiServer` at `/console` endpoint

### API Integration

The web console communicates with the REST API:
- `GET /api/applications` - Fetch application list
- `POST /api/applications` - Deploy new application
- `POST /api/applications/{id}/start` - Start application
- `POST /api/applications/{id}/stop` - Stop application
- `DELETE /api/applications/{id}` - Undeploy application
- `GET /api/applications/{id}/status` - Get application metrics

Polling interval: 2 seconds for metrics, 5 seconds for application list

## Browser Compatibility

Tested and supported on:
- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

Requires:
- JavaScript enabled
- Fetch API support
- CSS Grid support

## Configuration

### Port

The web console runs on the same port as the REST API server. Configure via:

```java
JdkHttpApiServer server = new JdkHttpApiServer(manager, 9090);
```

Web console will be available at `http://localhost:9090/console`

### Metrics Refresh Rate

To customize refresh rates, edit `static/app.js`:

```javascript
// Default: 2000ms (2 seconds)
setInterval(refreshMetrics, 2000);

// Default: 5000ms (5 seconds)  
setInterval(refreshApplicationList, 5000);
```

### Chart Configuration

Charts use Chart.js. Customize in `static/app.js`:

```javascript
const chartConfig = {
    type: 'line',
    options: {
        responsive: true,
        maintainAspectRatio: false,
        // ... more options
    }
};
```

## Security Considerations

- The web console has **no authentication** by default
- API key authentication can be added via `ApiAuthFilter`
- Recommended to run behind a reverse proxy (nginx, Apache) with HTTPS
- Consider IP whitelisting for production deployments

## Development

### File Structure

```
jplatform-web-console/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/org/flossware/jplatform/webconsole/
    │   │   └── WebConsoleHandler.java
    │   └── resources/web/
    │       ├── index.html
    │       └── static/
    │           ├── dashboard.css
    │           └── app.js
    └── test/
        └── java/org/flossware/jplatform/webconsole/
            └── WebConsoleHandlerTest.java
```

### Building

```bash
mvn clean install
```

The web resources are packaged in the JAR under `web/` directory.

### Testing

Run tests with:

```bash
mvn test
```

Test coverage includes:
- Static resource serving
- HTTP request handling
- Error responses

## Dependencies

- `jplatform-rest-api` - Provides the HTTP API backend
- Chart.js (CDN) - Metrics visualization
- No other frontend dependencies

## Limitations

- Metrics history is not persisted (resets on page refresh)
- Maximum 10 data points displayed in charts (rolling window)
- No multi-user support or user management
- No real-time WebSocket updates (uses polling)

## Future Enhancements

- WebSocket support for real-time push updates
- Metrics history persistence
- User authentication and authorization
- Application log viewer
- Configuration editor
- Cluster view for multi-node deployments

## See Also

- [REST API](../jplatform-rest-api/README.md) - Backend API documentation
- [Swing UI](../jplatform-swing-ui/README.md) - Desktop alternative
- [QUICKSTART](../QUICKSTART.md) - Quick start guide
- [Main README](../README.md) - Platform overview
