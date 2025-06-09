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

<<<<<<< HEAD
@Deprecated(
    message = "Use CCEK-based crypto services (HkdfService, AesService) instead.",
    replaceWith = ReplaceWith("coroutineContext[HkdfServiceKey] or coroutineContext[AesServiceKey]", "evolution.HkdfServiceKey", "evolution.AesServiceKey")
)
actual object Crypto {
    // ... (deprecated methods remain unchanged - throwing exceptions)
    @Deprecated("Use HkdfService from CoroutineContext")
    actual fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        throw UnsupportedOperationException("Crypto.hkdfExtract is deprecated. Use suspend HkdfService.extract from context.")
    }
    @Deprecated("Use HkdfService from CoroutineContext")
    actual fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        throw UnsupportedOperationException("Crypto.hkdfExpand is deprecated. Use suspend HkdfService.expand from context.")
    }
    @Deprecated("Use AesService from CoroutineContext")
    actual fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        throw UnsupportedOperationException("Crypto.aesGcmEncrypt is deprecated. Use suspend AesService.gcmEncrypt from context.")
    }
    @Deprecated("Use AesService from CoroutineContext")
    actual fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        throw UnsupportedOperationException("Crypto.aesGcmDecrypt is deprecated. Use suspend AesService.gcmDecrypt from context.")
    }
    @Deprecated("Use AesService from CoroutineContext")
    actual fun aesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        throw UnsupportedOperationException("Crypto.aesEcbEncrypt is deprecated. Use suspend AesService.ecbEncrypt from context (which is unsupported on JS).")
    }
}
=======
// actual object Crypto block removed
>>>>>>> origin/jules_wip_6906935130323988499

internal actual suspend fun protectPacket(
    context: CoroutineContext,
    packet: QuicPacket,       // packet.header is IN (not modified by HP if ECB fails), OUT (payload encrypted)
    keys: QuicInitialKeys,
    connection: QuicConnection
): ByteArray {
    val aesService = context[AesServiceKey]
        ?: throw IllegalStateException("AesService not found in CoroutineContext.")

    // 1. Payload Encryption (AES-GCM)
    val payloadKey = keys.payloadProtectionKey(context)
    val payloadIv = keys.payloadIv(context)
<<<<<<< HEAD
    // AAD for payload encryption is the QUIC packet header.
    // Since header won't be modified if ECB fails, we can use it directly or a copy.
    // Using a copy is safer if there was any chance of modification.
=======
>>>>>>> origin/jules_wip_6906935130323988499
    val aad = packet.header.copyOf()
    packet.protectedPayload = aesService.gcmEncrypt(payloadKey, payloadIv, packet.payload, aad)

    // 2. Header Protection Attempt
    try {
        val sampleLength = 16
        if ((packet.protectedPayload?.size ?: 0) < sampleLength) {
<<<<<<< HEAD
            // Not enough data for a sample, skip HP.
            // console.warn if available: "Protected payload too short for HP sample."
        } else {
            val sample = packet.protectedPayload!!.copyOfRange(0, sampleLength)
            val hpKey = keys.headerProtectionKey(context)
            val fullHpMask = aesService.ecbEncrypt(hpKey, sample) // This line will throw on JS
=======
            // Skip HP
        } else {
            val sample = packet.protectedPayload!!.copyOfRange(0, sampleLength)
            val hpKey = keys.headerProtectionKey(context)
            val fullHpMask = aesService.ecbEncrypt(hpKey, sample)
>>>>>>> origin/jules_wip_6906935130323988499

            val hpMaskFirst5Bytes = fullHpMask.copyOfRange(0, 5)

            val headerCopy = packet.header.copyOf()
            if (headerCopy.isNotEmpty()) {
<<<<<<< HEAD
                // Simplified XOR for the first byte. Real protection is bit-specific.
=======
>>>>>>> origin/jules_wip_6906935130323988499
                headerCopy[0] = (headerCopy[0].toInt() xor hpMaskFirst5Bytes[0].toInt()).toByte()
            }
            val pnOffset = 1
            val maxPnLengthInMask = 4
            for (i in 0 until maxPnLengthInMask) {
                if (pnOffset + i < headerCopy.size) {
                    headerCopy[pnOffset + i] = (headerCopy[pnOffset + i].toInt() xor hpMaskFirst5Bytes[i + 1].toInt()).toByte()
                } else {
                    break
                }
            }
<<<<<<< HEAD
            packet.header = headerCopy // Update packet with protected header
        }
    } catch (e: UnsupportedOperationException) {
        // Expected for JsAesService.ecbEncrypt. Header protection is skipped.
        // console.warn if available: "AES-ECB for header protection not supported. Packet sent with unprotected header."
    }
    // If an error other than UnsupportedOperationException occurs, it will propagate.
=======
            packet.header = headerCopy
        }
    } catch (e: UnsupportedOperationException) {
        // Expected for JsAesService.ecbEncrypt. Header protection is skipped.
    }
>>>>>>> origin/jules_wip_6906935130323988499

    return packet.getBytes()
}

internal actual suspend fun unprotectPacket(
    context: CoroutineContext,
    protectedPacketBytes: ByteArray,
    keys: QuicInitialKeys,
    connection: QuicConnection
): QuicPacket? {
    val aesService = context[AesServiceKey]
        ?: throw IllegalStateException("AesService not found in CoroutineContext for JS unprotection.")

<<<<<<< HEAD
    // Simplified parsing and offsets, similar to JVM/Native.
    val payloadCiphertextOffset = 20 // Highly_Simplified_Offset_To_Payload_Ciphertext_Start

    if (protectedPacketBytes.size < payloadCiphertextOffset + 16) { // Need 16B for GCM tag minimum in payload part
        return null
    }

    var headerForAad = protectedPacketBytes.copyOfRange(0, payloadCiphertextOffset) // Initially, this is the protected header.

    // Attempt Header Unprotection (expected to fail gracefully on JS)
    try {
        val sample = protectedPacketBytes.copyOfRange(payloadCiphertextOffset, payloadCiphertextOffset + 16)
        val hpKey = keys.headerProtectionKey(context)
        // This call will throw UnsupportedOperationException with JsAesService
        val fullHpMask = aesService.ecbEncrypt(hpKey, sample)
        val hpMaskFirst5Bytes = fullHpMask.copyOfRange(0, 5)

        // If ecbEncrypt succeeded (it won't on JS), unmask headerForAad
=======
    val payloadCiphertextOffset = 20

    if (protectedPacketBytes.size < payloadCiphertextOffset + 16) {
        return null
    }

    var headerForAad = protectedPacketBytes.copyOfRange(0, payloadCiphertextOffset)

    try {
        val sample = protectedPacketBytes.copyOfRange(payloadCiphertextOffset, payloadCiphertextOffset + 16)
        val hpKey = keys.headerProtectionKey(context)
        val fullHpMask = aesService.ecbEncrypt(hpKey, sample)
        val hpMaskFirst5Bytes = fullHpMask.copyOfRange(0, 5)

>>>>>>> origin/jules_wip_6906935130323988499
        val tempUnmaskedHeader = headerForAad.copyOf()
        if (tempUnmaskedHeader.isNotEmpty()) {
            tempUnmaskedHeader[0] = (tempUnmaskedHeader[0].toInt() xor hpMaskFirst5Bytes[0].toInt()).toByte()
        }
        val pnOffsetInHeader = 1
        for (i in 0 until 4) {
            if (pnOffsetInHeader + i < tempUnmaskedHeader.size && (i + 1) < hpMaskFirst5Bytes.size) {
                tempUnmaskedHeader[pnOffsetInHeader + i] = (tempUnmaskedHeader[pnOffsetInHeader + i].toInt() xor hpMaskFirst5Bytes[i + 1].toInt()).toByte()
            } else {
                break
            }
        }
<<<<<<< HEAD
        headerForAad = tempUnmaskedHeader // This line will not be reached if ECB fails.
    } catch (e: UnsupportedOperationException) {
        // Expected: AES-ECB for header protection is not supported.
        // Header remains protected. AAD for payload decryption will be the protected header.
        // console.warn if available: "AES-ECB for header unprotection not supported. Using protected header as AAD."
    }
    // Any other exception from ECB (if it somehow didn't throw UnsupportedOperationException but failed) would propagate.

    // Payload Decryption
    val payloadKey = keys.payloadProtectionKey(context)
    val payloadIv = keys.payloadIv(context)
    // Use headerForAad (which is still the protected header on JS if ECB failed) as AAD.
=======
        headerForAad = tempUnmaskedHeader
    } catch (e: UnsupportedOperationException) {
        // Header remains protected. AAD for payload decryption will be the protected header.
    }

    val payloadKey = keys.payloadProtectionKey(context)
    val payloadIv = keys.payloadIv(context)
>>>>>>> origin/jules_wip_6906935130323988499
    val aadForPayload = headerForAad

    val payloadCiphertextWithTag = protectedPacketBytes.copyOfRange(payloadCiphertextOffset, protectedPacketBytes.size)

    val decryptedPayload = try {
        aesService.gcmDecrypt(payloadKey, payloadIv, payloadCiphertextWithTag, aadForPayload)
    } catch (e: Exception) {
<<<<<<< HEAD
        // JsAesService.gcmDecrypt throws on failure.
=======
>>>>>>> origin/jules_wip_6906935130323988499
        return null
    }

    if (decryptedPayload == null) return null

<<<<<<< HEAD
    // Reconstruct QuicPacket.
    val parsedPacketType: QuicPacketType? = QuicPacketType.INITIAL // Placeholder
    val parsedConnectionId: ByteArray = connection.connectionId     // Placeholder
    val parsedPacketNumber: ULong = 0uL                             // Placeholder

    return QuicPacket(
        packetType = parsedPacketType,
        connectionId = parsedConnectionId,
        packetNumber = parsedPacketNumber,
        payload = decryptedPayload,
        protectionMask = null,
        protectedPayload = null
    ).apply {
        // On JS, this 'header' will be the one that could not be unprotected by ECB.
        this.header = headerForAad
=======
    val parsedPacketType: QuicPacketType? = QuicPacketType.INITIAL
    val parsedConnectionId: ByteArray = connection.connectionId
    val parsedPacketNumber: ULong = 0uL

    return QuicPacket(
        header = headerForAad, // On JS, this 'header' will be the one that could not be unprotected by ECB.
        payload = decryptedPayload,
        protectedPayload = null
    ).apply {
        // this.packetType = parsedPacketType // Would need these to be var in common QuicPacket
        // this.connectionId = parsedConnectionId
        // this.packetNumber = parsedPacketNumber
>>>>>>> origin/jules_wip_6906935130323988499
    }
}
