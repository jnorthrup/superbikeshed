package evolution

import kotlin.coroutines.CoroutineContext // Required for CoroutineContext parameter
<<<<<<< HEAD

@Deprecated(
    message = "Use CCEK-based crypto services (HkdfService, AesService) instead. Obtain them from CoroutineContext.",
    replaceWith = ReplaceWith("coroutineContext[HkdfServiceKey] or coroutineContext[AesServiceKey]", "evolution.HkdfServiceKey", "evolution.AesServiceKey")
)
expect object Crypto {
    // Keep the function signatures for now, but they are deprecated.
    // Actual implementations should ideally be removed or also marked deprecated.
    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray
    fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray
    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray
    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray
    fun aesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray
}

=======

// @Deprecated(...) expect object Crypto { ... } block REMOVED

>>>>>>> origin/jules_wip_6906935130323988499
// Forward declaration for QuicPacket, QuicInitialKeys, QuicConnection if not already visible
// For the purpose of this file, assume they are defined elsewhere in the 'evolution' package or imported.
// e.g. import evolution.QuicPacket (if in a different file)

internal expect suspend fun protectPacket(
    context: CoroutineContext, // Added CoroutineContext to access CCEK services
    packet: QuicPacket,       // Assuming QuicPacket is defined
    keys: QuicInitialKeys,    // Assuming QuicInitialKeys is defined
    connection: QuicConnection // Assuming QuicConnection is defined
): ByteArray

// Added for packet decryption and header unprotection
internal expect suspend fun unprotectPacket(
    context: CoroutineContext,
    protectedPacketBytes: ByteArray, // The raw bytes received from the wire
    keys: QuicInitialKeys,           // Keys for the current encryption level
    connection: QuicConnection      // Connection context, e.g., for expected PN
): QuicPacket? // Returns null if unprotection fails (e.g., GCM tag mismatch)