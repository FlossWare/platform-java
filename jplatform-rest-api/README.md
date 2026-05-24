# JPlatform REST API

HTTP REST API server for JPlatform management using JDK's built-in `com.sun.net.httpserver`.

## Overview

The `jplatform-rest-api` module provides a complete HTTP REST API for managing JPlatform instances. It offers programmatic access to deployment, lifecycle management, and metrics retrieval.

## Features

- **Full CRUD Operations**: Deploy, list, start, stop, undeploy applications
- **Metrics Retrieval**: Get real-time resource usage (CPU, memory, threads)
- **Status Monitoring**: Query application states and health
- **JSON API**: Request and response bodies use JSON format
- **Lightweight**: Uses JDK's built-in HTTP server (no external dependencies)
- **Authentication**: Optional API key authentication via `ApiAuthFilter`

## API Endpoints

### Application Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/applications` | Deploy a new application |
| GET | `/api/applications` | List all applications |
| GET | `/api/applications/{id}` | Get application details |
| GET | `/api/applications/{id}/status` | Get application status and metrics |
| POST | `/api/applications/{id}/start` | Start an application |
| POST | `/api/applications/{id}/stop` | Stop an application |
| DELETE | `/api/applications/{id}` | Undeploy an application |
| GET | `/api/applications/{id}/metrics` | Get resource metrics history |

### Platform Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/platform/status` | Get platform status and summary |
| GET | `/api/platform/health` | Health check endpoint |

## Usage

### Starting the API Server

```java
import org.flossware.jplatform.api.PlatformManager;
import org.flossware.jplatform.core.ApplicationManager;
import org.flossware.jplatform.rest.JdkHttpApiServer;

public class Main {
    public static void main(String[] args) throws IOException {
        PlatformManager manager = new ApplicationManager();
        JdkHttpApiServer server = new JdkHttpApiServer(manager, 8080);
        server.start();
        
        System.out.println("API server running on http://localhost:8080");
    }
}
```

### Deploy Application

```bash
curl -X POST http://localhost:8080/api/applications \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "my-app",
    "name": "My Application",
    "mainClass": "com.example.MyApp",
    "classpathEntries": ["file:///path/to/app.jar"],
    "threadPool": {
      "corePoolSize": 4,
      "maxPoolSize": 20
    }
  }'
```

**Response** (201 Created):
```json
{
  "applicationId": "my-app",
  "name": "My Application",
  "mainClass": "com.example.MyApp",
  "state": "DEPLOYED",
  "deployedAt": "2024-05-24T10:30:00Z",
  "metrics": {
    "cpuTimeNanos": 0,
    "heapUsedBytes": 0,
    "threadCount": 0
  }
}
```

### List Applications

```bash
curl http://localhost:8080/api/applications
```

**Response** (200 OK):
```json
[
  {
    "applicationId": "my-app",
    "name": "My Application",
    "mainClass": "com.example.MyApp",
    "state": "DEPLOYED",
    "deployedAt": "2024-05-24T10:30:00Z",
    "metrics": { ... }
  }
]
```

### Start Application

```bash
curl -X POST http://localhost:8080/api/applications/my-app/start
```

**Response** (200 OK):
```json
{
  "applicationId": "my-app",
  "state": "RUNNING",
  ...
}
```

### Get Application Status

```bash
curl http://localhost:8080/api/applications/my-app/status
```

**Response** (200 OK):
```json
{
  "applicationId": "my-app",
  "state": "RUNNING",
  "deployedAt": "2024-05-24T10:30:00Z",
  "metrics": {
    "cpuTimeNanos": 1234567890,
    "heapUsedBytes": 52428800,
    "threadCount": 15
  }
}
```

### Stop Application

```bash
curl -X POST http://localhost:8080/api/applications/my-app/stop
```

**Response** (200 OK)

### Undeploy Application

```bash
curl -X DELETE http://localhost:8080/api/applications/my-app
```

**Response** (204 No Content)

### Get Platform Status

```bash
curl http://localhost:8080/api/platform/status
```

**Response** (200 OK):
```json
{
  "totalApplications": 3,
  "runningApplications": 2,
  "stoppedApplications": 1,
  "failedApplications": 0,
  "uptime": "2h 15m 30s"
}
```

## Error Responses

All errors return a consistent JSON structure:

```json
{
  "error": "Application not found",
  "status": 404,
  "timestamp": "2024-05-24T10:30:00Z"
}
```

Common status codes:
- `400` - Bad Request (invalid JSON, missing required fields)
- `404` - Not Found (application does not exist)
- `409` - Conflict (application already exists, invalid state transition)
- `500` - Internal Server Error

## Authentication

Enable API key authentication:

```java
JdkHttpApiServer server = new JdkHttpApiServer(manager, 8080);
server.setApiKey("your-secret-api-key");
server.start();
```

Clients must include the API key in the `X-API-Key` header:

```bash
curl -H "X-API-Key: your-secret-api-key" \
  http://localhost:8080/api/applications
```

## Configuration

### Port

Default port is 8080. Override in constructor:

```java
JdkHttpApiServer server = new JdkHttpApiServer(manager, 9090);
```

### CORS

CORS is enabled by default for all origins. To restrict:

```java
// Custom CORS configuration requires modifying ApiAuthFilter
```

## Components

- **JdkHttpApiServer**: Main server class, manages HTTP server lifecycle
- **ApplicationApiHandler**: Handles `/api/applications/*` endpoints
- **PlatformApiHandler**: Handles `/api/platform/*` endpoints
- **ApiAuthFilter**: Optional authentication filter
- **ApplicationResponseDTO**: JSON response model for applications
- **ErrorResponseDTO**: JSON response model for errors

## Dependencies

- `jplatform-api` - Platform management API
- `jackson-databind` - JSON serialization
- `slf4j-api` - Logging
- JDK 21+ (HttpServer is part of JDK)

## Testing

Run tests with:

```bash
mvn test
```

Test coverage includes:
- All HTTP endpoints (deploy, start, stop, undeploy, list, status)
- Error handling and validation
- Authentication filter
- JSON serialization/deserialization

## See Also

- [Web Console](../jplatform-web-console/README.md) - Browser-based UI built on this API
- [Swing UI](../jplatform-swing-ui/README.md) - Desktop UI using this API
- [QUICKSTART](../QUICKSTART.md) - Quick start guide with API examples
- [Main README](../README.md) - Platform overview
