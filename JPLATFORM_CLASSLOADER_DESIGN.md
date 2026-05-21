# jplatform-classloader Design

## Purpose
Platform-specific class loading that integrates with JPlatform's application lifecycle, security, and resource management.

## What jplatform-classloader Does (Platform-Specific)

### 1. ApplicationDescriptor Integration
Translates JPlatform's application configuration into jclassloader sources.

### 2. Platform API Isolation
Ensures `org.flossware.jplatform.api.*` classes are shared across all applications.

### 3. Application Lifecycle Coordination
Integrates with ApplicationManager for deploy/undeploy operations.

### 4. Resource Cleanup on Undeploy
Ensures all application resources are released when app is undeployed.

### 5. Integration with Platform Services
Coordinates with ThreadPoolExecutor, SecurityPolicy, ResourceMonitor.

## Architecture

```
IsolatedClassLoader (jplatform-specific)
    ↓ uses
JClassLoader (reusable from jclassloader)
    ↓ uses
ParentLastDelegation (reusable from jclassloader)
ResourceTrackingListener (reusable from jclassloader)
ClassSource implementations (reusable from jclassloader)
```

## Implementation

### Package: org.flossware.jplatform.classloader

```java
/**
 * Platform-specific class loader for isolated application execution.
 * Extends JClassLoader with JPlatform-specific integration.
 */
public class IsolatedClassLoader extends JClassLoader implements AutoCloseable {
    
    private final String applicationId;
    private final ApplicationDescriptor descriptor;
    private final ResourceTrackingListener resourceTracker;
    
    private IsolatedClassLoader(String applicationId,
                               ApplicationDescriptor descriptor,
                               ClassLoader platformSharedLoader,
                               ResourceTrackingListener resourceTracker,
                               JClassLoader jclassLoader) {
        // This constructor is private - use factory method
        super(jclassLoader);
        this.applicationId = applicationId;
        this.descriptor = descriptor;
        this.resourceTracker = resourceTracker;
    }
    
    /**
     * Factory method to create an isolated class loader for an application.
     * This is the platform-specific part that knows about ApplicationDescriptor.
     */
    public static IsolatedClassLoader create(String applicationId,
                                            ApplicationDescriptor descriptor,
                                            ClassLoader platformSharedLoader) {
        
        ResourceTrackingListener tracker = new ResourceTrackingListener();
        
        // Build JClassLoader with platform-specific configuration
        JClassLoader.Builder builder = JClassLoader.builder()
            .parent(platformSharedLoader)
            // Platform-specific: parent-last with JPlatform API exception
            .parentLast(
                "org.flossware.jplatform.api.",  // Platform API
                "java.", "javax.", "sun.", "jdk."  // System classes
            )
            .addListener(tracker)
            .addListener(new PlatformClassLoadListener(applicationId))
            .cache(new FileSystemCache(getCacheDir(applicationId)))
            .useCache(true);
        
        // Platform-specific: Convert ApplicationDescriptor to class sources
        addClassSourcesFromDescriptor(builder, descriptor);
        
        JClassLoader jcl = builder.build();
        
        return new IsolatedClassLoader(applicationId, descriptor, 
                                      platformSharedLoader, tracker, jcl);
    }
    
    /**
     * Platform-specific: Translate ApplicationDescriptor to JClassLoader sources.
     */
    private static void addClassSourcesFromDescriptor(
            JClassLoader.Builder builder, 
            ApplicationDescriptor descriptor) {
        
        for (URI classpathEntry : descriptor.getClasspathEntries()) {
            String scheme = classpathEntry.getScheme();
            
            switch (scheme) {
                case "file":
                    builder.addLocalSource(classpathEntry.getPath());
                    break;
                    
                case "http":
                case "https":
                    // Could add authentication from descriptor
                    AuthConfig auth = getAuthFromDescriptor(descriptor, classpathEntry);
                    if (auth != null) {
                        builder.addRemoteSource(classpathEntry.toString(), auth);
                    } else {
                        builder.addRemoteSource(classpathEntry.toString());
                    }
                    break;
                    
                case "maven":
                    // Parse: maven:groupId:artifactId:version
                    String coords = classpathEntry.getSchemeSpecificPart();
                    builder.addMavenCentral(coords);
                    break;
                    
                case "nexus":
                    // Parse: nexus://host/repo/groupId:artifactId:version
                    addNexusSource(builder, classpathEntry, descriptor);
                    break;
                    
                case "s3":
                    // Parse: s3://bucket/path/to/lib.jar
                    addS3Source(builder, classpathEntry, descriptor);
                    break;
                    
                default:
                    throw new IllegalArgumentException(
                        "Unsupported classpath scheme: " + scheme);
            }
        }
    }
    
    /**
     * Platform-specific: Get cache directory for this application.
     */
    private static String getCacheDir(String applicationId) {
        return System.getProperty("jplatform.cache.dir", "/var/jplatform/cache")
            + "/" + applicationId;
    }
    
    /**
     * Platform-specific: Extract authentication from descriptor.
     */
    private static AuthConfig getAuthFromDescriptor(
            ApplicationDescriptor descriptor, URI uri) {
        // Check descriptor properties for auth config
        Map<String, String> props = descriptor.getProperties();
        String authType = props.get("classpath." + uri.getHost() + ".auth.type");
        
        if ("basic".equals(authType)) {
            String username = props.get("classpath." + uri.getHost() + ".auth.username");
            String password = props.get("classpath." + uri.getHost() + ".auth.password");
            return AuthConfig.basic(username, password);
        } else if ("bearer".equals(authType)) {
            String token = props.get("classpath." + uri.getHost() + ".auth.token");
            return AuthConfig.bearer(token);
        }
        
        return null;
    }
    
    /**
     * Platform-specific: Get statistics for this application's class loading.
     */
    public ClassLoaderStatistics getStatistics() {
        return new ClassLoaderStatistics(
            applicationId,
            resourceTracker.getLoadedClasses().size(),
            resourceTracker.getTotalBytesLoaded()
        );
    }
    
    /**
     * Platform-specific: Cleanup when application is undeployed.
     */
    @Override
    public void close() {
        // Close all tracked resources
        resourceTracker.closeAllResources();
        
        // Clear references to aid GC
        // The parent JClassLoader doesn't have a close() method,
        // but we can suggest GC
        System.gc();
    }
    
    public String getApplicationId() {
        return applicationId;
    }
    
    public ApplicationDescriptor getDescriptor() {
        return descriptor;
    }
}

/**
 * Platform-specific listener that integrates with JPlatform monitoring.
 */
class PlatformClassLoadListener implements ClassLoaderLifecycleListener {
    private final String applicationId;
    
    PlatformClassLoadListener(String applicationId) {
        this.applicationId = applicationId;
    }
    
    @Override
    public void onClassLoaded(ClassLoadEvent event) {
        // Platform-specific: Could emit metrics, log to platform logger, etc.
        // Could integrate with ResourceMonitor
        
        // Example: Track in platform metrics
        PlatformMetrics.recordClassLoad(applicationId, 
                                       event.getClassName(),
                                       event.getLoadTimeNanos());
    }
    
    @Override
    public void onClassLoadFailed(String className, Throwable error) {
        // Platform-specific: Log to platform logger
        PlatformLogger.warn("Application {} failed to load class {}: {}",
                          applicationId, className, error.getMessage());
    }
}

/**
 * Platform-specific statistics about class loading.
 */
public class ClassLoaderStatistics {
    private final String applicationId;
    private final int classesLoaded;
    private final long totalBytesLoaded;
    
    public ClassLoaderStatistics(String applicationId, int classesLoaded, 
                                long totalBytesLoaded) {
        this.applicationId = applicationId;
        this.classesLoaded = classesLoaded;
        this.totalBytesLoaded = totalBytesLoaded;
    }
    
    public String getApplicationId() { return applicationId; }
    public int getClassesLoaded() { return classesLoaded; }
    public long getTotalBytesLoaded() { return totalBytesLoaded; }
}

// Platform-specific utilities (stubs - would integrate with platform services)
class PlatformMetrics {
    static void recordClassLoad(String appId, String className, long nanos) {
        // Integrate with jplatform-monitoring
    }
}

class PlatformLogger {
    static void warn(String format, Object... args) {
        // Integrate with platform logging
    }
}
```

## Usage Example (Platform-Specific)

```java
// In jplatform-core - ApplicationManager
public class ApplicationManager {
    
    public ApplicationContext deploy(ApplicationDescriptor descriptor) {
        // Platform-specific: Create isolated class loader
        ClassLoader platformShared = getPlatformSharedClassLoader();
        IsolatedClassLoader appClassLoader = IsolatedClassLoader.create(
            descriptor.getApplicationId(),
            descriptor,
            platformShared
        );
        
        // Platform-specific: Create other isolated resources
        ManagedThreadPool threadPool = createThreadPool(descriptor);
        SecurityPolicy securityPolicy = createSecurityPolicy(descriptor);
        ResourceMonitor resourceMonitor = createResourceMonitor(descriptor);
        
        // Platform-specific: Create application context
        ApplicationContext context = new ApplicationContextImpl(
            descriptor.getApplicationId(),
            appClassLoader,
            threadPool,
            securityPolicy,
            resourceMonitor,
            // ... other platform services
        );
        
        // Load and instantiate the application's main class
        Class<?> mainClass = appClassLoader.loadClass(descriptor.getMainClass());
        Object appInstance = mainClass.getDeclaredConstructor().newInstance();
        
        // If it implements Application interface, it's platform-aware
        if (appInstance instanceof Application) {
            // Will be started later via ApplicationManager.start()
        } else {
            // Legacy app with main() method - handle differently
        }
        
        return context;
    }
    
    public void undeploy(String applicationId) {
        ApplicationContext context = applications.remove(applicationId);
        
        // Platform-specific: Cleanup
        context.getThreadPool().shutdown();
        
        // Close the class loader (releases resources)
        if (context.getClassLoader() instanceof IsolatedClassLoader) {
            ((IsolatedClassLoader) context.getClassLoader()).close();
        }
        
        // Suggest GC to reclaim memory
        System.gc();
    }
}
```

## What's Platform-Specific vs. Reusable

### Platform-Specific (stays in jplatform-classloader):
1. ✅ ApplicationDescriptor → ClassSource translation
2. ✅ Platform API isolation rules (`org.flossware.jplatform.api.*`)
3. ✅ Integration with ApplicationManager lifecycle
4. ✅ Integration with platform logging/metrics
5. ✅ Platform-specific cache directory structure
6. ✅ Authentication extraction from ApplicationDescriptor
7. ✅ ClassLoaderStatistics for platform monitoring

### Reusable (in jclassloader):
1. ✅ Delegation strategies (parent-first, parent-last)
2. ✅ Lifecycle hooks (ClassLoaderLifecycleListener)
3. ✅ Resource tracking (ResourceTrackingListener)
4. ✅ All ClassSource implementations (S3, Maven, HTTP, etc.)
5. ✅ Caching mechanisms
6. ✅ Authentication (AuthConfig)

## Benefits of This Separation

### For jclassloader:
- Remains general-purpose and reusable
- No dependencies on jplatform
- Useful for plugin systems, OSGi, testing frameworks, etc.

### For jplatform:
- Thin wrapper focused on platform concerns
- Leverages jclassloader's 20+ class sources
- Doesn't reinvent class loading mechanics
- Can focus on lifecycle, security, monitoring integration

### For maintainability:
- Clear separation of concerns
- Each project has a focused responsibility
- Changes to general class loading go to jclassloader
- Changes to platform integration go to jplatform-classloader
