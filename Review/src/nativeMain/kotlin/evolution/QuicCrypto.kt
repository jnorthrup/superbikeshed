package evolution

import kotlin.coroutines.CoroutineContext
import kotlin.math.min

// Dummy QuicPacket: updated to match JVM refined version
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
        header = if (packetType == null) connectionId else connectionId, // Simplified header logic from QuicCurl.kt's QuicPacket
        payload = payload,
        protectedPayload = protectedPayload
    )

    // Internal fun for AAD consistency, matching common QuicPacket in QuicCurl.kt
    internal fun headerPlaceholder(): ByteArray = this.header

    actual fun getBytes(): ByteArray {
        return (header) + (protectedPayload ?: payload) // Header is now pre-protected
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
enum class QuicPacketType { INITIAL } // Matches JVM dummy

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
        throw UnsupportedOperationException("Crypto.aesEcbEncrypt is deprecated. Use suspend AesService.ecbEncrypt from context.")
    }
}
=======
// actual object Crypto block removed
>>>>>>> origin/jules_wip_6906935130323988499

internal actual suspend fun protectPacket(
    context: CoroutineContext,
    packet: QuicPacket,       // packet.header is IN/OUT
    keys: QuicInitialKeys,
    connection: QuicConnection
): ByteArray {
    val aesService = context[AesServiceKey]
        ?: throw IllegalStateException("AesService not found in CoroutineContext.")

    // 1. Payload Encryption (AES-GCM)
    val payloadKey = keys.payloadProtectionKey(context)
    val payloadIv = keys.payloadIv(context)
    val aad = packet.header.copyOf() // Use a copy for AAD before header is modified
    packet.protectedPayload = aesService.gcmEncrypt(payloadKey, payloadIv, packet.payload, aad)

    // 2. Header Protection
    val sampleLength = 16
    if ((packet.protectedPayload?.size ?: 0) < sampleLength) {
        throw IllegalStateException("Protected payload is too short to extract a sample for header protection.")
    }
    val sample = packet.protectedPayload!!.copyOfRange(0, sampleLength)

    val hpKey = keys.headerProtectionKey(context)
    val fullHpMask = aesService.ecbEncrypt(hpKey, sample)
    val hpMaskFirst5Bytes = fullHpMask.copyOfRange(0, 5)

    val headerCopy = packet.header.copyOf()

    if (headerCopy.isNotEmpty()) {
<<<<<<< HEAD
        // Simplified XOR for the first byte. Real protection is bit-specific.
        // E.g., ((headerCopy[0] and 0x80.toByte()) == 0x80.toByte()) for long header type.
        // val bitsToProtect = if (isLongHeader) 0x0F else 0x1F
        // headerCopy[0] = (headerCopy[0].toInt() xor (hpMaskFirst5Bytes[0].toInt() and bitsToProtect)).toByte()
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
=======
    packet.header = headerCopy
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
        ?: throw IllegalStateException("AesService not found in CoroutineContext for Native unprotection.")

<<<<<<< HEAD
    // Simplified parsing and offsets, mirroring JVM's unprotectPacket for this stage.
    val payloadCiphertextOffset = 20 // Highly_Simplified_Offset_To_Payload_Ciphertext_Start

    if (protectedPacketBytes.size < payloadCiphertextOffset + 16) { // Need 16B sample + GCM tag
=======
    val payloadCiphertextOffset = 20

    if (protectedPacketBytes.size < payloadCiphertextOffset + 16) {
>>>>>>> origin/jules_wip_6906935130323988499
        return null
    }

    val sample = protectedPacketBytes.copyOfRange(payloadCiphertextOffset, payloadCiphertextOffset + 16)
    val hpKey = keys.headerProtectionKey(context)
    val fullHpMask = aesService.ecbEncrypt(hpKey, sample)
    val hpMaskFirst5Bytes = fullHpMask.copyOfRange(0, 5)

    val headerLengthForUnmasking = minOf(payloadCiphertextOffset, protectedPacketBytes.size)
    val unprotectedHeader = protectedPacketBytes.copyOfRange(0, headerLengthForUnmasking)

<<<<<<< HEAD
    // Apply unmasking to the unprotectedHeader (which is initially a copy of the protected one)
=======
>>>>>>> origin/jules_wip_6906935130323988499
    if (unprotectedHeader.isNotEmpty()) {
        unprotectedHeader[0] = (unprotectedHeader[0].toInt() xor hpMaskFirst5Bytes[0].toInt()).toByte()
    }

<<<<<<< HEAD
    val pnOffsetInHeader = 1 // Conceptual
    for (i in 0 until 4) { // Apply next 4 mask bytes to conceptual PN field
=======
    val pnOffsetInHeader = 1
    for (i in 0 until 4) {
>>>>>>> origin/jules_wip_6906935130323988499
        if (pnOffsetInHeader + i < unprotectedHeader.size && (i + 1) < hpMaskFirst5Bytes.size) {
            unprotectedHeader[pnOffsetInHeader + i] = (unprotectedHeader[pnOffsetInHeader + i].toInt() xor hpMaskFirst5Bytes[i + 1].toInt()).toByte()
        } else {
            break
        }
    }

    val payloadKey = keys.payloadProtectionKey(context)
    val payloadIv = keys.payloadIv(context)
<<<<<<< HEAD
    val aadForPayload = unprotectedHeader.copyOf() // Use the now unprotected header as AAD
=======
    val aadForPayload = unprotectedHeader.copyOf()
>>>>>>> origin/jules_wip_6906935130323988499

    val payloadCiphertextWithTag = protectedPacketBytes.copyOfRange(payloadCiphertextOffset, protectedPacketBytes.size)

    val decryptedPayload = try {
        aesService.gcmDecrypt(payloadKey, payloadIv, payloadCiphertextWithTag, aadForPayload)
    } catch (e: Exception) {
<<<<<<< HEAD
        // NativeAesService.gcmDecrypt (OpenSSL) throws on tag mismatch or other errors.
        return null
    }

    if (decryptedPayload == null) return null // Should be caught by try-catch if impl throws.

    // Reconstruct QuicPacket with placeholders for parsed values from unprotectedHeader.
    val parsedPacketType: QuicPacketType? = QuicPacketType.INITIAL // Placeholder
    val parsedConnectionId: ByteArray = connection.connectionId     // Placeholder
    val parsedPacketNumber: ULong = 0uL                             // Placeholder

    // Use the constructor that takes individual fields, then set the header.
    // This relies on QuicPacket in native having a var header or a way to set it post-construction.
    // The dummy QuicPacket in nativeMain/evolution/QuicCrypto.kt has 'header' as var.
    return QuicPacket(
        packetType = parsedPacketType,
        connectionId = parsedConnectionId,
        packetNumber = parsedPacketNumber,
        payload = decryptedPayload,
        protectionMask = null,
        protectedPayload = null
    ).apply {
        this.header = unprotectedHeader
=======
        return null
    }

    if (decryptedPayload == null) return null

    val parsedPacketType: QuicPacketType? = QuicPacketType.INITIAL
    val parsedConnectionId: ByteArray = connection.connectionId
    val parsedPacketNumber: ULong = 0uL

    return QuicPacket(
        header = unprotectedHeader,
        payload = decryptedPayload,
        protectedPayload = null
    ).apply {
        // this.packetType = parsedPacketType // Would need these fields to be var in QuicPacket
        // this.connectionId = parsedConnectionId
        // this.packetNumber = parsedPacketNumber
>>>>>>> origin/jules_wip_6906935130323988499
    }
}
