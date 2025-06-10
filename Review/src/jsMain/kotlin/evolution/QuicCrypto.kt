package evolution

import kotlin.coroutines.CoroutineContext
import kotlin.math.min

// Dummy QuicPacket: updated to match JVM/Native refined version
actual class QuicPacket actual constructor(
    var header: ByteArray, // Made var
    val payload: ByteArray,
    // var protectionMask: ByteArray?, // Removed
    var protectedPayload: ByteArray?
) {
    // Secondary constructor from QuicCurl.kt for compatibility
    actual constructor(
        packetType: QuicPacketType?,
        connectionId: ByteArray,
        packetNumber: ULong,
        payload: ByteArray,
        protectionMask: ByteArray?, // Param no longer used for a field
        protectedPayload: ByteArray?
    ) : this(
        header = if (packetType == null) connectionId else connectionId, // Simplified header logic
        payload = payload,
        protectedPayload = protectedPayload
    )

    // Internal fun for AAD consistency
    internal fun headerPlaceholder(): ByteArray = this.header

    actual fun getBytes(): ByteArray {
        return (header) + (protectedPayload ?: payload) // Header might be unprotected on JS
    }
}

actual class QuicInitialKeys actual constructor(val clientInitialSecret: ByteArray, val serverInitialSecret: ByteArray) {
    private val _payloadKey: ByteArray by lazy { clientInitialSecret.copyOfRange(0, 16) }
    private val _payloadIv: ByteArray by lazy { clientInitialSecret.copyOfRange(16, 16 + 12) }
    private val _headerProtectionKey: ByteArray by lazy { serverInitialSecret.copyOfRange(0, 16) }

    actual fun headerProtectionKey(context: CoroutineContext): ByteArray = _headerProtectionKey
    actual fun payloadProtectionKey(context: CoroutineContext): ByteArray = _payloadKey
    actual fun payloadIv(context: CoroutineContext): ByteArray = _payloadIv
}

actual class QuicConnection actual constructor() {
    // Connection properties
}

// Simplified QuicPacketType for dummy constructor
enum class QuicPacketType { INITIAL } // Matches other dummies

actual object Crypto {
    // ... (deprecated methods remain unchanged - throwing exceptions)
    actual fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        throw UnsupportedOperationException("Crypto.hkdfExtract is deprecated. Use suspend HkdfService.extract from context.")
    }
    actual fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        throw UnsupportedOperationException("Crypto.hkdfExpand is deprecated. Use suspend HkdfService.expand from context.")
    }
    actual fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        throw UnsupportedOperationException("Crypto.aesGcmEncrypt is deprecated. Use suspend AesService.gcmEncrypt from context.")
    }
    actual fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray)