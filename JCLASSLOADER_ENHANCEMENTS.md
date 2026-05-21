# Proposed Enhancements to jclassloader

## Goal
Add reusable isolation and lifecycle management features to jclassloader that benefit any project needing custom class loading behavior (not just jplatform).

## Enhancements to Add

### 1. Delegation Strategies (Reusable)

**Package**: `org.flossware.jclassloader.delegation`

```java
/**
 * Strategy for class loading delegation between parent and sources.
 * Allows customizing when to delegate to parent vs. load from sources.
 */
public interface DelegationStrategy {
    /**
     * Load a class using this delegation strategy.
     * 
     * @param name The class name to load
     * @param parent The parent ClassLoader
     * @param findInSources Function to find class in configured sources
     * @return The loaded class
     * @throws ClassNotFoundException if class cannot be found
     */
    Class<?> loadClass(String name, ClassLoader parent, 
                       ClassFinder findInSources) throws ClassNotFoundException;
    
    @FunctionalInterface
    interface ClassFinder {
        Class<?> findClass(String name) throws ClassNotFoundException;
    }
}

/**
 * Standard parent-first delegation (current JClassLoader behavior).
 * Delegates to parent first, then checks sources.
 */
public class ParentFirstDelegation implements DelegationStrategy {
    @Override
    public Class<?> loadClass(String name, ClassLoader parent, ClassFinder findInSources) {
        try {
            return parent.loadClass(name);
        } catch (ClassNotFoundException e) {
            return findInSources.findClass(name);
        }
    }
}

/**
 * Parent-last delegation for application isolation.
 * Checks sources first, then falls back to parent.
 * Certain packages (java.*, javax.*) always delegate to parent.
 */
public class ParentLastDelegation implements DelegationStrategy {
    private final Set<String> alwaysParentPrefixes;
    
    public ParentLastDelegation(String... alwaysParentPrefixes) {
        this.alwaysParentPrefixes = Set.of(alwaysParentPrefixes);
    }
    
    @Override
    public Class<?> loadClass(String name, ClassLoader parent, ClassFinder findInSources) {
        // System classes and specified prefixes always from parent
        if (alwaysParentPrefixes.stream().anyMatch(name::startsWith)) {
            return parent.loadClass(name);
        }
        
        // Try sources first (parent-last)
        try {
            return findInSources.findClass(name);
        } catch (ClassNotFoundException e) {
            // Fall back to parent
            return parent.loadClass(name);
        }
    }
    
    public static ParentLastDelegation withDefaults() {
        return new ParentLastDelegation("java.", "javax.", "sun.", "jdk.");
    }
}

/**
 * Custom delegation allowing fine-grained control.
 */
public class CustomDelegation implements DelegationStrategy {
    private final Predicate<String> parentFirstPredicate;
    
    public CustomDelegation(Predicate<String> parentFirstPredicate) {
        this.parentFirstPredicate = parentFirstPredicate;
    }
    
    @Override
    public Class<?> loadClass(String name, ClassLoader parent, ClassFinder findInSources) {
        if (parentFirstPredicate.test(name)) {
            try {
                return parent.loadClass(name);
            } catch (ClassNotFoundException e) {
                return findInSources.findClass(name);
            }
        } else {
            try {
                return findInSources.findClass(name);
            } catch (ClassNotFoundException e) {
                return parent.loadClass(name);
            }
        }
    }
}
```

### 2. Lifecycle Hooks (Reusable)

**Package**: `org.flossware.jclassloader.lifecycle`

```java
/**
 * Listener for class loading lifecycle events.
 * Useful for tracking, logging, security checks, resource management.
 */
public interface ClassLoaderLifecycleListener {
    
    /**
     * Called when a class is successfully loaded from a source.
     */
    default void onClassLoaded(ClassLoadEvent event) {}
    
    /**
     * Called when a class is loaded from cache.
     */
    default void onClassCacheHit(String className) {}
    
    /**
     * Called when a class is cached.
     */
    default void onClassCached(String className, byte[] classData) {}
    
    /**
     * Called when class loading fails.
     */
    default void onClassLoadFailed(String className, Throwable error) {}
    
    /**
     * Called when a resource is opened (for tracking cleanup).
     */
    default void onResourceOpened(String resourceName, AutoCloseable resource) {}
}

/**
 * Event containing details about a loaded class.
 */
public class ClassLoadEvent {
    private final String className;
    private final ClassSource source;
    private final long loadTimeNanos;
    private final int classSizeBytes;
    
    // Constructor, getters...
}

/**
 * Utility listener for tracking loaded classes and resources.
 * Useful for cleanup scenarios (e.g., when undeploying an application).
 */
public class ResourceTrackingListener implements ClassLoaderLifecycleListener {
    private final Set<String> loadedClasses = ConcurrentHashMap.newKeySet();
    private final List<AutoCloseable> openResources = new CopyOnWriteArrayList<>();
    private final AtomicLong totalClassesLoaded = new AtomicLong();
    private final AtomicLong totalBytesLoaded = new AtomicLong();
    
    @Override
    public void onClassLoaded(ClassLoadEvent event) {
        loadedClasses.add(event.getClassName());
        totalClassesLoaded.incrementAndGet();
        totalBytesLoaded.addAndGet(event.getClassSizeBytes());
    }
    
    @Override
    public void onResourceOpened(String resourceName, AutoCloseable resource) {
        openResources.add(resource);
    }
    
    public Set<String> getLoadedClasses() {
        return Collections.unmodifiableSet(loadedClasses);
    }
    
    public void closeAllResources() {
        for (AutoCloseable resource : openResources) {
            try {
                resource.close();
            } catch (Exception e) {
                // Log but don't propagate
            }
        }
        openResources.clear();
        loadedClasses.clear();
    }
    
    public long getTotalClassesLoaded() {
        return totalClassesLoaded.get();
    }
    
    public long getTotalBytesLoaded() {
        return totalBytesLoaded.get();
    }
}

/**
 * Listener for logging/debugging class loading.
 */
public class LoggingListener implements ClassLoaderLifecycleListener {
    @Override
    public void onClassLoaded(ClassLoadEvent event) {
        System.out.printf("Loaded %s from %s in %dms%n",
            event.getClassName(),
            event.getSource().getDescription(),
            event.getLoadTimeNanos() / 1_000_000);
    }
}
```

### 3. Enhanced JClassLoader

**Changes to**: `org.flossware.jclassloader.JClassLoader`

```java
public class JClassLoader extends ClassLoader {
    private final List<ClassSource> classSources;
    private final ClassCache cache;
    private final boolean useCache;
    private final DelegationStrategy delegationStrategy;  // NEW
    private final List<ClassLoaderLifecycleListener> listeners;  // NEW
    
    private JClassLoader(Builder builder) {
        super(builder.parent != null ? builder.parent : getSystemClassLoader());
        this.classSources = new ArrayList<>(builder.classSources);
        this.cache = builder.cache;
        this.useCache = builder.useCache && cache != null;
        this.delegationStrategy = builder.delegationStrategy;  // NEW
        this.listeners = new CopyOnWriteArrayList<>(builder.listeners);  // NEW
    }
    
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Check if already loaded
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }
        
        // Use delegation strategy
        c = delegationStrategy.loadClass(name, getParent(), this::findClassInternal);
        
        if (resolve) {
            resolveClass(c);
        }
        
        return c;
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return findClassInternal(name);
    }
    
    private Class<?> findClassInternal(String name) throws ClassNotFoundException {
        long startTime = System.nanoTime();
        byte[] classData = null;
        ClassSource usedSource = null;
        
        // Check cache
        if (useCache && cache.contains(name)) {
            classData = cache.get(name);
            if (classData != null) {
                fireClassCacheHit(name);
                return defineClass(name, classData, 0, classData.length);
            }
        }
        
        // Load from sources
        for (ClassSource source : classSources) {
            try {
                if (source.canLoad(name)) {
                    classData = source.loadClassData(name);
                    if (classData != null) {
                        usedSource = source;
                        
                        // Cache it
                        if (useCache) {
                            try {
                                cache.put(name, classData);
                                fireClassCached(name, classData);
                            } catch (IOException e) {
                                // Continue even if caching fails
                            }
                        }
                        
                        // Define the class
                        Class<?> clazz = defineClass(name, classData, 0, classData.length);
                        
                        // Fire event
                        long loadTime = System.nanoTime() - startTime;
                        fireClassLoaded(new ClassLoadEvent(name, usedSource, loadTime, classData.length));
                        
                        return clazz;
                    }
                }
            } catch (IOException e) {
                // Try next source
            }
        }
        
        ClassNotFoundException ex = new ClassNotFoundException(name);
        fireClassLoadFailed(name, ex);
        throw ex;
    }
    
    // NEW: Lifecycle event firing methods
    private void fireClassLoaded(ClassLoadEvent event) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoaded(event);
            } catch (Exception e) {
                // Don't let listener exceptions break class loading
            }
        }
    }
    
    private void fireClassCacheHit(String className) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCacheHit(className);
            } catch (Exception e) {
            }
        }
    }
    
    private void fireClassCached(String className, byte[] classData) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassCached(className, classData);
            } catch (Exception e) {
            }
        }
    }
    
    private void fireClassLoadFailed(String className, Throwable error) {
        for (ClassLoaderLifecycleListener listener : listeners) {
            try {
                listener.onClassLoadFailed(className, error);
            } catch (Exception e) {
            }
        }
    }
    
    public static class Builder {
        private ClassLoader parent;
        private final List<ClassSource> classSources = new ArrayList<>();
        private ClassCache cache;
        private boolean useCache = true;
        private DelegationStrategy delegationStrategy = new ParentFirstDelegation();  // NEW
        private final List<ClassLoaderLifecycleListener> listeners = new ArrayList<>();  // NEW
        
        // Existing methods...
        
        // NEW: Delegation strategy configuration
        public Builder delegationStrategy(DelegationStrategy strategy) {
            this.delegationStrategy = Objects.requireNonNull(strategy);
            return this;
        }
        
        public Builder parentFirst() {
            return delegationStrategy(new ParentFirstDelegation());
        }
        
        public Builder parentLast(String... alwaysParentPrefixes) {
            return delegationStrategy(new ParentLastDelegation(alwaysParentPrefixes));
        }
        
        public Builder customDelegation(Predicate<String> parentFirstPredicate) {
            return delegationStrategy(new CustomDelegation(parentFirstPredicate));
        }
        
        // NEW: Lifecycle listener configuration
        public Builder addListener(ClassLoaderLifecycleListener listener) {
            this.listeners.add(Objects.requireNonNull(listener));
            return this;
        }
        
        public Builder addLoggingListener() {
            return addListener(new LoggingListener());
        }
        
        public Builder trackResources() {
            return addListener(new ResourceTrackingListener());
        }
        
        // Existing build() method...
    }
}
```

## Why These Are Reusable

### DelegationStrategy
- **Use case 1**: OSGi-like module systems
- **Use case 2**: Plugin systems needing isolation
- **Use case 3**: Multi-tenant applications
- **Use case 4**: Testing frameworks needing class isolation
- **Use case 5**: JPlatform application isolation

### Lifecycle Hooks
- **Use case 1**: Security auditing (track what classes are loaded)
- **Use case 2**: Performance monitoring (class load times)
- **Use case 3**: Resource cleanup (track open resources)
- **Use case 4**: License compliance (track loaded libraries)
- **Use case 5**: Debugging (log class loading issues)

### Resource Tracking
- **Use case 1**: Application servers undeploying apps
- **Use case 2**: Plugin systems unloading plugins
- **Use case 3**: Testing frameworks cleaning up between tests
- **Use case 4**: Hot reload scenarios
- **Use case 5**: Memory leak detection

## Backward Compatibility

All enhancements are **backward compatible**:
- Default delegation strategy is `ParentFirstDelegation` (current behavior)
- Listeners are optional (empty list by default)
- Existing builder methods still work
- No breaking changes to existing API

## Example Usage (General-Purpose)

```java
// Plugin system with parent-last isolation
JClassLoader pluginLoader = JClassLoader.builder()
    .addLocalSource("/plugins/my-plugin")
    .parentLast("com.myapp.api.")  // API classes from parent
    .addLoggingListener()
    .build();

// Multi-tenant app with resource tracking
ResourceTrackingListener tracker = new ResourceTrackingListener();
JClassLoader tenantLoader = JClassLoader.builder()
    .addRemoteSource("https://tenant1.example.com/classes/")
    .parentLast()
    .addListener(tracker)
    .build();

// Later: cleanup
tracker.closeAllResources();

// OSGi-like module system
JClassLoader moduleLoader = JClassLoader.builder()
    .addLocalSource("/modules/foo")
    .customDelegation(name -> 
        name.startsWith("org.osgi.") || name.startsWith("java."))
    .build();
```

## Benefits

### For jclassloader users:
- ✅ More flexible delegation strategies
- ✅ Better resource management
- ✅ Debugging and monitoring capabilities
- ✅ Still simple for basic use cases

### For jplatform:
- ✅ Gets parent-last isolation for free
- ✅ Gets resource tracking for cleanup
- ✅ No need to reimplement class loading
- ✅ Can focus on platform-specific concerns

### For other projects:
- ✅ Plugin systems get isolation
- ✅ Test frameworks get cleanup hooks
- ✅ Security systems get audit hooks
- ✅ Any project needing custom class loading
