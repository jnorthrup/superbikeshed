package borg.trikeshed.crypto

import borg.trikeshed.lib.Indexed

/**
 * Cryptographic hash utilities.
 */
expect object HashUtils {
    /**
     * Computes the SHA-256 hash of the input data.
     * This function may suspend if the underlying platform implementation is asynchronous (e.g., Web Crypto).
     *
     * @param input The data to hash.
     * @return An Indexed<Byte> containing the SHA-256 digest (32 bytes).
     */
    suspend fun sha256(input: Indexed<Byte>): Indexed<Byte>
}
