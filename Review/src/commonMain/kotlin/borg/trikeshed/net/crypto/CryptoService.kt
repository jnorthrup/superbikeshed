package borg.trikeshed.net.crypto

// This file defines a simplified CryptoService interface for this subtask.
// It replaces any previous CryptoService definition for the purpose of this implementation.

/**
 * Defines the result of a cryptographic operation, indicating success or failure.
 */
sealed class CryptoResult {
    /**
     * Indicates a successful cryptographic operation.
     * @property data The resulting data (e.g., ciphertext, plaintext, mask).
     */
    data class Success(val data: ByteArray) : CryptoResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Success
            return data.contentEquals(other.data)
        }
        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    /**
     * Indicates a failed cryptographic operation.
     * @property message A descriptive error message.
     * @property cause An optional underlying exception that caused the failure.
     */
    data class Error(val message: String, val cause: Throwable? = null) : CryptoResult()
}

/**
 * Interface for core cryptographic operations required by QUIC.
 * This version focuses on AEAD and Header Protection mask generation, returning [CryptoResult].
 */
interface CryptoService {
    /**
     * Encrypts plaintext using AEAD (AES-128-GCM for QUIC).
     * The output ciphertext includes the authentication tag.
     *
     * @param key The secret key (e.g., 16 bytes for AES-128).
     * @param nonce The nonce or IV (e.g., 12 bytes for AES-GCM).
     * @param plaintext The data to encrypt.
     * @param associatedData Additional data to authenticate but not encrypt.
     * @return [CryptoResult.Success] with ciphertext (including tag) or [CryptoResult.Error] on failure.
     */
    fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray): CryptoResult

    /**
     * Decrypts ciphertext using AEAD (AES-128-GCM for QUIC).
     * Expects ciphertextWithTag to contain both the ciphertext and its authentication tag.
     *
     * @param key The secret key.
     * @param nonce The nonce or IV.
     * @param ciphertextWithTag The data to decrypt (ciphertext || tag).
     * @param associatedData Additional data that was authenticated.
     * @return [CryptoResult.Success] with plaintext if decryption and authentication are successful,
     *         or [CryptoResult.Error] otherwise (e.g., tag mismatch).
     */
    fun aeadDecrypt(key: ByteArray, nonce: ByteArray, ciphertextWithTag: ByteArray, associatedData: ByteArray): CryptoResult

    /**
     * Generates a 5-byte header protection mask using AES-128-ECB (for QUIC).
     *
     * @param hpKey The header protection key (e.g., 16 bytes for AES-128).
     * @param sample A 16-byte sample taken from the packet ciphertext (typically from the packet number field's ciphertext).
     * @return [CryptoResult.Success] with the 5-byte mask, or [CryptoResult.Error] on failure.
     */
    fun generateHeaderProtectionMask(hpKey: ByteArray, sample: ByteArray): CryptoResult
}
