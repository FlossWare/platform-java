package org.flossware.jplatform.api;

/**
 * Exception thrown when an application exceeds its resource quota.
 * This is a runtime exception to allow quota enforcement without
 * requiring explicit exception handling.
 *
 * @see ResourceQuota
 * @see ResourceMonitor
 */
public class ResourceQuotaExceededException extends RuntimeException {
    /**
     * Constructs a new resource quota exceeded exception with the specified message.
     *
     * @param message the detail message
     */
    public ResourceQuotaExceededException(String message) {
        super(message);
    }

    /**
     * Constructs a new resource quota exceeded exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public ResourceQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
