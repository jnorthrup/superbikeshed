package borg.trikeshed.util

/**
 * Expected class for platform-specific clock service implementations.
 */
expect class PlatformClockService() : ClockService

/**
 * Interface for a service that provides the current time.
 */
interface ClockService {
    /**
     * Returns the current time in milliseconds since the Unix epoch.
     * Similar to `java.lang.System.currentTimeMillis()`.
     */
    fun currentTimeMillis(): Long
}
