# JPlatform Quick Start

This guide will get you up and running with JPlatform in 5 minutes.

## Prerequisites

- Java 11 or higher
- Maven 3.6+ (for building)

## 1. Build the Platform

```bash
cd /home/sfloess/Development/github/FlossWare/jplatform
mvn clean install
```

This builds all modules including sample applications.

## 2. Start the Platform

```bash
java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar
```

You should see:

```
[INFO] Initializing JPlatform...
[INFO] JPlatform initialized successfully
[INFO] JPlatform started
[INFO] Type 'help' for available commands

jplatform>
```

## 3. Deploy and Run the Hello World Sample

```bash
jplatform> deploy hello-world jplatform-samples/hello-world/target/sample-hello-world-1.0.jar org.flossware.jplatform.samples.helloworld.HelloWorldApp
Application deployed: hello-world

jplatform> start hello-world
Application started: hello-world
```

You'll see output like:

```
Hello from JPlatform! Message #1
Hello from JPlatform! Message #2
Hello from JPlatform! Message #3
...
```

## 4. Check Application Status

```bash
jplatform> status hello-world

Application: hello-world
  State: RUNNING
  Thread Pool: ThreadPoolStats{activeCount=1, completedTaskCount=0, queueSize=0, ...}
  Resources:
    CPU Time: 0.05s
    Heap Used: 128 MB
    Thread Count: 2
```

## 5. Deploy the Messaging Sample

Keep the hello-world app running and deploy the messaging app:

```bash
jplatform> deploy messaging-app jplatform-samples/messaging-app/target/sample-messaging-app-1.0.jar org.flossware.jplatform.samples.messaging.MessagingApp
Application deployed: messaging-app

jplatform> start messaging-app
Application started: messaging-app
```

Now you have two applications running in the same JVM!

## 6. List All Applications

```bash
jplatform> list

Applications:
  hello-world - RUNNING
  messaging-app - RUNNING
```

## 7. Stop and Undeploy

```bash
jplatform> stop hello-world
Application stopped: hello-world

jplatform> stop messaging-app
Application stopped: messaging-app

jplatform> undeploy hello-world
Application undeployed: hello-world

jplatform> undeploy messaging-app
Application undeployed: messaging-app
```

## 8. Exit the Platform

```bash
jplatform> exit
Shutting down platform...
Platform shutdown complete
```

## What Just Happened?

You just ran two completely isolated Java applications within a single JVM:

1. **ClassLoader Isolation**: Each application had its own classloader, preventing class conflicts
2. **Thread Pool Isolation**: Each application used its own dedicated thread pool
3. **Resource Monitoring**: The platform tracked CPU, memory, and thread usage per application
4. **Messaging**: The messaging-app demonstrated inter-application communication
5. **Lifecycle Management**: Applications were deployed, started, stopped, and undeployed independently

## Next Steps

- Read the full [README.md](README.md) for architecture details
- Create your own application (see "Writing Applications" section in README.md)
- Explore the sample code in `jplatform-samples/`
- Check out [JClassLoader](https://github.com/FlossWare/jclassloader) for classloading details

## Troubleshooting

**Build fails:**
```bash
# Make sure you're in the jplatform directory
cd /home/sfloess/Development/github/FlossWare/jplatform
mvn clean install
```

**Can't find sample JARs:**
```bash
# Verify samples were built
ls -la jplatform-samples/hello-world/target/
ls -la jplatform-samples/messaging-app/target/
```

**Applications don't start:**
- Check that the JAR path is correct (relative to where you started the launcher)
- Verify the main class name is correct and fully qualified
- Check the logs for exceptions

## Sample Session

Here's a complete example session:

```bash
$ java -jar jplatform-launcher/target/jplatform-launcher-1.0.jar

jplatform> deploy hello jplatform-samples/hello-world/target/sample-hello-world-1.0.jar org.flossware.jplatform.samples.helloworld.HelloWorldApp
Application deployed: hello

jplatform> deploy msg jplatform-samples/messaging-app/target/sample-messaging-app-1.0.jar org.flossware.jplatform.samples.messaging.MessagingApp
Application deployed: msg

jplatform> start hello
Application started: hello

jplatform> start msg
Application started: msg

jplatform> list
Applications:
  hello - RUNNING
  msg - RUNNING

jplatform> status hello
Application: hello
  State: RUNNING
  ...

jplatform> status msg
Application: msg
  State: RUNNING
  ...

jplatform> stop hello
Application stopped: hello

jplatform> stop msg
Application stopped: msg

jplatform> exit
Shutting down platform...
```
