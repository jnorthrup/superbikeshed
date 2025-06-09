package borg.trikeshed.util

import java.security.SecureRandom

/**
 * JVM implementation of [ClockService].
 * Uses `System.currentTimeMillis()`.
 */
actual class PlatformClockService : ClockService {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

/**
 * JVM implementation of [SecureRandomService].
 * Uses `java.security.SecureRandom`.
 */
actual class PlatformSecureRandomService : SecureRandomService {
    // Initialize SecureRandom lazily or once.
    // A new instance per call to nextBytes is inefficient but simple for this example.
    // For better performance, make it a member: private val random = SecureRandom()
    private val random = SecureRandom()

    override fun nextBytes(array: ByteArray) {
        if (array.isEmpty()) return
        random.nextBytes(array)
    }
}
