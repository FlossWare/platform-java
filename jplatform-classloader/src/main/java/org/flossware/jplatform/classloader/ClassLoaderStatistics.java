package org.flossware.jplatform.classloader;

/**
 * Platform-specific statistics about class loading for an application.
 */
public class ClassLoaderStatistics {
    private final String applicationId;
    private final int classesLoaded;
    private final long totalBytesLoaded;
    private final long cacheHits;

    /**
     * Creates a new class loader statistics snapshot.
     *
     * @param applicationId the application identifier
     * @param classesLoaded the total number of classes loaded
     * @param totalBytesLoaded the total bytes loaded from all sources
     * @param cacheHits the number of classes loaded from cache
     */
    public ClassLoaderStatistics(String applicationId, int classesLoaded,
                                long totalBytesLoaded, long cacheHits) {
        this.applicationId = applicationId;
        this.classesLoaded = classesLoaded;
        this.totalBytesLoaded = totalBytesLoaded;
        this.cacheHits = cacheHits;
    }

    /**
     * Returns the application identifier.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationId;
    }

    /**
     * Returns the total number of classes loaded.
     *
     * @return the class count
     */
    public int getClassesLoaded() {
        return classesLoaded;
    }

    /**
     * Returns the total bytes loaded from all sources.
     *
     * @return the total bytes
     */
    public long getTotalBytesLoaded() {
        return totalBytesLoaded;
    }

    /**
     * Returns the number of classes loaded from cache.
     *
     * @return the cache hit count
     */
    public long getCacheHits() {
        return cacheHits;
    }

    /**
     * Calculates the cache hit rate as a ratio.
     *
     * @return the cache hit rate (0.0 to 1.0), or 0.0 if no classes loaded
     */
    public double getCacheHitRate() {
        return classesLoaded > 0 ? (double) cacheHits / classesLoaded : 0.0;
    }

    @Override
    public String toString() {
        return String.format("ClassLoaderStatistics{app=%s, classes=%d, bytes=%d, cacheHits=%d, hitRate=%.2f%%}",
                applicationId, classesLoaded, totalBytesLoaded, cacheHits, getCacheHitRate() * 100);
    }
}
