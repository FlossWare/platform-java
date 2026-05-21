# Architecture Separation: jclassloader vs jplatform-classloader

## Clean Separation of Concerns

```
┌─────────────────────────────────────────────────────────────┐
│                    jplatform-classloader                    │
│                    (Platform-Specific)                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  • IsolatedClassLoader                                      │
│  • ApplicationDescriptor → ClassSource translation          │
│  • Platform API isolation (org.flossware.jplatform.api.*)   │
│  • ApplicationManager integration                           │
│  • Platform logging/metrics integration                     │
│  • Platform-specific cache directories                      │
│  • Application lifecycle coordination                       │
│                                                             │
│  Dependencies: jplatform-api, jclassloader                  │
│                                                             │
└────────────────────┬────────────────────────────────────────┘
                     │ uses
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                      jclassloader                           │
│                  (General-Purpose, Reusable)                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Core Class Loading:                                        │
│  • JClassLoader                                             │
│  • ClassSource interface + 20+ implementations              │
│  • ClassCache (filesystem, in-memory)                       │
│  • AuthConfig (Basic, Bearer)                               │
│                                                             │
│  NEW - Delegation Strategies:                               │
│  • DelegationStrategy interface                             │
│  • ParentFirstDelegation (default)                          │
│  • ParentLastDelegation (isolation)                         │
│  • CustomDelegation (custom rules)                          │
│                                                             │
│  NEW - Lifecycle Hooks:                                     │
│  • ClassLoaderLifecycleListener interface                   │
│  • ResourceTrackingListener (cleanup)                       │
│  • LoggingListener (debugging)                              │
│  • ClassLoadEvent                                           │
│                                                             │
│  Dependencies: None (pure Java + optional integrations)     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## What Goes Where

### jclassloader (Reusable Library)

**Question to ask**: "Could any project needing custom class loading use this?"

✅ **YES - Put in jclassloader:**
- Delegation strategies (parent-first, parent-last, custom)
- Lifecycle hooks and listeners
- Resource tracking utilities
- All ClassSource implementations
- Caching mechanisms
- Authentication support

❌ **NO - Don't put in jclassloader:**
- ApplicationDescriptor knowledge
- JPlatform-specific API isolation rules
- Platform service integration
- Application lifecycle management

### jplatform-classloader (Platform-Specific)

**Question to ask**: "Is this specific to JPlatform's architecture?"

✅ **YES - Put in jplatform-classloader:**
- IsolatedClassLoader (wrapper around JClassLoader)
- ApplicationDescriptor → JClassLoader configuration
- Platform API sharing (`org.flossware.jplatform.api.*`)
- Integration with ApplicationManager
- Platform logging/metrics hooks
- Platform-specific caching strategies

❌ **NO - Don't put in jplatform-classloader:**
- Generic class loading mechanics
- ClassSource implementations
- Delegation strategy logic
- General-purpose lifecycle hooks

## Code Comparison

### Example 1: Parent-Last Delegation

**❌ WRONG - In jplatform-classloader:**
```java
// NO - This is reusable, not platform-specific!
public class ParentLastDelegation {
    // Generic parent-last logic
}
```

**✅ RIGHT - In jclassloader:**
```java
// YES - Generic delegation strategy
public class ParentLastDelegation implements DelegationStrategy {
    private final Set<String> alwaysParentPrefixes;
    // Generic parent-last logic usable by ANY project
}
```

**✅ RIGHT - In jplatform-classloader (uses it):**
```java
// Platform-specific: Configure with platform API prefix
JClassLoader.builder()
    .parentLast("org.flossware.jplatform.api.")  // Platform-specific prefix
    .build();
```

### Example 2: Resource Tracking

**❌ WRONG - In jplatform-classloader:**
```java
// NO - Resource tracking is generally useful!
public class ResourceTracker {
    Set<String> loadedClasses;
    List<AutoCloseable> resources;
}
```

**✅ RIGHT - In jclassloader:**
```java
// YES - Generic resource tracking listener
public class ResourceTrackingListener implements ClassLoaderLifecycleListener {
    // Generic resource tracking usable by ANY project
}
```

**✅ RIGHT - In jplatform-classloader (uses it):**
```java
// Platform-specific: Use tracker for application cleanup
ResourceTrackingListener tracker = new ResourceTrackingListener();
JClassLoader.builder()
    .addListener(tracker)
    .build();

// Later, on undeploy:
tracker.closeAllResources();  // Platform-specific cleanup timing
```

### Example 3: ClassSource from Descriptor

**❌ WRONG - In jclassloader:**
```java
// NO - jclassloader shouldn't know about ApplicationDescriptor!
public void addFromDescriptor(ApplicationDescriptor desc) {
    // ...
}
```

**✅ RIGHT - In jplatform-classloader:**
```java
// YES - Platform-specific translation
private static void addClassSourcesFromDescriptor(
        JClassLoader.Builder builder, 
        ApplicationDescriptor descriptor) {
    for (URI entry : descriptor.getClasspathEntries()) {
        // Platform-specific: Parse descriptor format
        if (entry.getScheme().equals("maven")) {
            builder.addMavenCentral(entry.getSchemeSpecificPart());
        }
        // ...
    }
}
```

## Dependency Graph

```
jplatform-launcher
    ↓
jplatform-core
    ↓
jplatform-classloader ─────→ jclassloader
    ↓                             ↓
jplatform-api              (no dependencies)
```

**Key Points:**
- jclassloader has NO dependency on jplatform
- jplatform-classloader depends on BOTH jclassloader and jplatform-api
- jclassloader remains reusable by other projects

## Use Cases

### jclassloader can be used by:
1. ✅ JPlatform (application server)
2. ✅ Plugin systems (isolated plugins)
3. ✅ OSGi containers (module isolation)
4. ✅ Testing frameworks (test isolation)
5. ✅ Multi-tenant applications (tenant isolation)
6. ✅ Hot reload systems (resource cleanup)
7. ✅ Any project needing custom class loading

### jplatform-classloader is used by:
1. ✅ JPlatform only
2. ❌ Not reusable outside JPlatform

## Benefits

### For jclassloader Project:
- ✅ Becomes more powerful (delegation strategies, hooks)
- ✅ Remains general-purpose and reusable
- ✅ Attracts wider user base
- ✅ No platform-specific coupling

### For jplatform Project:
- ✅ Doesn't reinvent class loading
- ✅ Gets 20+ class sources for free
- ✅ Thin wrapper focused on platform concerns
- ✅ Easier to maintain

### For Other Projects:
- ✅ Can use jclassloader for custom class loading needs
- ✅ Don't need JPlatform to get isolation features
- ✅ Proven, tested class loading library

## Next Steps

1. **Enhance jclassloader** (in jclassloader repo):
   - Add `DelegationStrategy` interface and implementations
   - Add `ClassLoaderLifecycleListener` interface
   - Add `ResourceTrackingListener` implementation
   - Update `JClassLoader` to support strategies and listeners
   - Add tests
   - Release new version (e.g., 2.0)

2. **Implement jplatform-classloader** (in jplatform repo):
   - Depend on jclassloader 2.0+
   - Create `IsolatedClassLoader` wrapper
   - Implement platform-specific configuration
   - Integrate with `ApplicationDescriptor`
   - Add platform-specific listeners
   - Add tests

3. **Documentation**:
   - Update jclassloader README with new features
   - Document jplatform-classloader usage
   - Create migration guide if needed

## Summary

**jclassloader = The Engine (reusable)**
- How to load class bytes from anywhere
- How to delegate (parent-first, parent-last, custom)
- How to track resources
- How to emit lifecycle events

**jplatform-classloader = The Steering Wheel (platform-specific)**
- Which classes to isolate (platform API vs application)
- When to create/destroy class loaders (application lifecycle)
- Where to cache (platform cache directories)
- What to log/monitor (platform metrics)

Perfect separation of concerns! 🎯
