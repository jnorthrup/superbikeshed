package borg.trikeshed.util

/**
 * Expected class for platform-specific secure random service implementations.
 */
expect class PlatformSecureRandomService() : SecureRandomService

/**
 * Interface for a service that provides cryptographically secure random bytes.
 */
interface SecureRandomService {
    /**
     * Fills the provided byte array with cryptographically secure random bytes.
     * @param array The ByteArray to fill with random bytes.
     */
    fun nextBytes(array: ByteArray)
}
