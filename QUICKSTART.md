# JPlatform Quick Start Guide

Get up and running with JPlatform in 5 minutes!

## Prerequisites

- Java 11 or later
- Maven 3.6+

## Build JPlatform

```bash
# Clone the repository
git clone https://github.com/FlossWare/jplatform.git
cd jplatform

# Build all modules (takes ~2 minutes)
mvn clean install

# Launcher JAR will be at:
# jplatform-launcher/target/jplatform-launcher-1.0.jar
```

## Quick Start: Interactive Mode

The simplest way to get started is with the interactive console:

```bash
cd jplatform-launcher/target
java -jar jplatform-launcher-1.0.jar
```

You'll see:

```
  _____ _____  _       _    __                    
  |_   _|  __ \| |     | |  / _|                   
    | | | |__) | | __ _| |_| |_ ___  _ __ _ __ ___  
    | | |  ___/| |/ _` | __|  _/ _ \| '__| '_ ` _ \ 
   _| |_| |    | | (_| | |_| || (_) | |  | | | | | |
  |_____|_|    |_|\__,_|\__|_| \___/|_|  |_| |_| |_|

JPlatform 1.0 - Java Application Platform
Type 'help' for commands

jplatform>
```

### Deploy Your First Application

Let's deploy the Hello World sample:

```bash
# Build the sample
cd ../../jplatform-samples/hello-world
mvn clean package
cd ../../jplatform-launcher/target

# Launch platform and deploy
java -jar jplatform-launcher-1.0.jar

jplatform> deploy hello-world ../../jplatform-samples/hello-world/target/sample-hello-world-1.0.jar org.flossware.jplatform.samples.helloworld.HelloWorldApp
Application deployed: hello-world

jplatform> start hello-world
[hello-world] Hello from JPlatform!
[hello-world] Application ID: hello-world
[hello-world] Running in thread: hello-world-pool-1
Application started: hello-world

jplatform> status hello-world
Application ID: hello-world
State: RUNNING
CPU Time: 1234567 ns
Heap Used: 1048576 bytes
Thread Count: 5
Active Threads: 2
Queued Tasks: 0
Completed Tasks: 1

jplatform> stop hello-world
Application stopped: hello-world

jplatform> list
Applications:
  hello-world - STOPPED

jplatform> exit
Shutting down platform...
```

## Advanced: Descriptor-Based Deployment

Create a YAML descriptor (`my-app.yaml`):

```yaml
applicationId: my-app
name: My Application
version: 1.0.0
mainClass: com.example.MyApp

classpathEntries:
  - file:///path/to/my-app.jar

threadPool:
  corePoolSize: 4
  maxPoolSize: 20

resources:
  maxHeapMB: 512

enableMessaging: true

properties:
  app.environment: production
```

Deploy it:

```bash
jplatform> deploy-yaml /path/to/my-app.yaml
Application deployed from YAML: my-app

jplatform> start my-app
```

## Enable Web Console

For a graphical management interface:

```bash
java -jar jplatform-launcher-1.0.jar --rest-api --web-console

# Open browser to:
http://localhost:8080/console
```

The web console provides:
- Application deployment via upload or paste
- Start/stop/undeploy buttons
- Real-time metrics charts (CPU, memory, threads)
- Application properties viewer

## Use Swing Desktop UI

For a native desktop management interface:

```bash
java -cp jplatform-swing-ui-1.1.jar:jplatform-api-1.1.jar:jplatform-core-1.1.jar \
    org.flossware.jplatform.swing.SwingConsole

# Or if using the executable JAR:
java -jar jplatform-swing-ui-1.1.jar
```

The Swing UI provides:
- Application deployment via native file chooser
- Start/stop/undeploy buttons
- Real-time metrics table (CPU time, heap, threads)
- Auto-refresh every 2 seconds
- Native look and feel for your operating system

**Note**: Swing UI requires a display environment (X11, Wayland, Windows, macOS). It cannot run in headless CI/CD environments.

## Enable Metrics Monitoring

### JMX Metrics (JConsole, VisualVM)

```bash
java -jar jplatform-launcher-1.0.jar --jmx-port 9999

# In another terminal:
jconsole localhost:9999

# Navigate to MBeans tab → org.flossware.jplatform
# See per-application metrics and operations
```

### Prometheus Metrics

```bash
java -jar jplatform-launcher-1.0.jar --prometheus

# Metrics endpoint:
curl http://localhost:9090/metrics
```

Output:
```prometheus
# HELP jplatform_app_cpu_time_seconds Total CPU time used by application
# TYPE jplatform_app_cpu_time_seconds counter
jplatform_app_cpu_time_seconds{app_id="my-app"} 123.45

# HELP jplatform_app_heap_used_bytes Heap memory used by application
# TYPE jplatform_app_heap_used_bytes gauge
jplatform_app_heap_used_bytes{app_id="my-app"} 134217728

# HELP jplatform_app_state Application lifecycle state
# TYPE jplatform_app_state gauge
jplatform_app_state{app_id="my-app",state="running"} 1.0
jplatform_app_state{app_id="my-app",state="stopped"} 0.0
```

## Auto-Deployment via Filesystem Watcher

Enable automatic deployment when descriptor files are added:

```bash
# Create watch directory
mkdir /var/jplatform/apps

# Start launcher with watcher
java -jar jplatform-launcher-1.0.jar --watch-dir /var/jplatform/apps

# In another terminal, drop descriptor files:
cp my-app.yaml /var/jplatform/apps/

# Application automatically deploys and starts!
# Remove file to undeploy:
rm /var/jplatform/apps/my-app.yaml
```

## REST API Usage

Start with REST API enabled:

```bash
java -jar jplatform-launcher-1.0.jar --rest-api
```

### Deploy Application

```bash
curl -X POST http://localhost:8080/api/applications \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "api-app",
    "mainClass": "com.example.ApiApp",
    "classpathEntries": ["file:///path/to/app.jar"],
    "threadPool": {
      "corePoolSize": 4,
      "maxPoolSize": 20
    }
  }'
```

### List Applications

```bash
curl http://localhost:8080/api/applications
```

### Start Application

```bash
curl -X POST http://localhost:8080/api/applications/api-app/start
```

### Get Application Status

```bash
curl http://localhost:8080/api/applications/api-app/status
```

### Stop and Undeploy

```bash
# Stop
curl -X POST http://localhost:8080/api/applications/api-app/stop

# Undeploy
curl -X DELETE http://localhost:8080/api/applications/api-app
```

## Production Deployment

For production use, enable all features via command-line:

```bash
java -jar jplatform-launcher-1.0.jar \
  --rest-api \
  --port 8080 \
  --web-console \
  --jmx-port 9999 \
  --prometheus \
  --prometheus-port 9090 \
  --watch-dir /var/jplatform/apps
```

Or better yet, use a configuration file (`platform.yaml`):

```yaml
api:
  enabled: true
  port: 8080
  bindAddress: 0.0.0.0

metrics:
  jmx:
    enabled: true
    port: 9999
    domain: org.flossware.jplatform
  prometheus:
    enabled: true
    port: 9090
    path: /metrics

watcher:
  enabled: true
  watchDirectory: /var/jplatform/apps
  autoStart: true
  autoDeploy: true
```

Then launch with:

```bash
java -jar jplatform-launcher-1.0.jar --config platform.yaml
```

This gives you:
- REST API on port 8080
- Web console at http://localhost:8080/console
- JMX metrics on port 9999
- Prometheus metrics on port 9090
- Auto-deployment from /var/jplatform/apps

Command-line flags override configuration file settings:

```bash
# Override port from config file
java -jar jplatform-launcher-1.0.jar --config platform.yaml --port 9000
```

## Deployment Modes

JPlatform supports three deployment modes:

### 1. JVM Applications (Default)

Standard Java applications running in isolated classloaders:

```yaml
applicationId: my-java-app
mainClass: com.example.MyApp
classpathEntries:
  - file:///path/to/app.jar
```

### 2. Native Processes

Deploy GraalVM native images or compiled executables:

```yaml
applicationId: graal-app
nativeImage: true
classpathEntries:
  - file:///usr/local/bin/myapp

properties:
  native.args: "--server --port=8080"
  native.env.DATABASE_URL: "jdbc:postgresql://db/app"
  native.workdir: "/var/apps/graal-app"
```

See [NATIVE_EXECUTION.md](NATIVE_EXECUTION.md) for details.

### 3. Containers

Deploy applications as Docker, Podman, or LXC containers:

```yaml
applicationId: web-server
properties:
  container.runtime: docker
  container.image: nginx:alpine
  container.ports: "8080:80,8443:443"
  container.volumes: "/var/www:/usr/share/nginx/html"
  container.network: bridge
  container.env.NGINX_HOST: example.com
```

See [CONTAINER_DEPLOYMENT.md](CONTAINER_DEPLOYMENT.md) for details.

## Next Steps

- Read the [README](README.md) for architecture details
- Learn about [Native Execution](NATIVE_EXECUTION.md) - GraalVM and compiled binaries
- Learn about [Container Deployment](CONTAINER_DEPLOYMENT.md) - Docker/Podman/LXC
- Check [examples/applications](examples/applications) for sample descriptors
- Explore sample applications in [jplatform-samples](jplatform-samples)
- Review API documentation in [jplatform-api](jplatform-api)
