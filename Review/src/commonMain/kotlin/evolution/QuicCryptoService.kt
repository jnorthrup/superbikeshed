package evolution

import kotlin.coroutines.CoroutineContext

// --- HKDF Service ---

expect class HkdfService() : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>

    // Changed to suspend
    suspend fun extract(salt: ByteArray, ikm: ByteArray): ByteArray
    suspend fun expand(prk: ByteArray, info: ByteArray, len: Int): ByteArray
}

object HkdfServiceKey : SpecializedQuicContextKey<HkdfService>("HkdfService") {
    override val operations: QuicOperationVTable<HkdfService> = QuicOperationVTable(
        process = { service, _ -> service }
    )
}

// --- AES Service ---

expect class AesService() : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>

    // Changed to suspend
    suspend fun gcmEncrypt(keyBytes: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray
    suspend fun gcmDecrypt(keyBytes: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray
    suspend fun ecbEncrypt(keyBytes: ByteArray, plaintext: ByteArray): ByteArray // ECB is sync in WebCrypto, but keep suspend for consistency
}

object AesServiceKey : SpecializedQuicContextKey<AesService>("AesService") {
    override val operations: QuicOperationVTable<AesService> = QuicOperationVTable(
        process = { service, _ -> service }
    )
}

/**
 * Thrown when an AEAD decryption operation (like AES-GCM decrypt) fails,
 * typically due to a tag mismatch or corrupted ciphertext.
 */
expect class DecryptionFailedException(message: String, cause: Throwable? = null) : Exception
