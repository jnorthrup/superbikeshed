package evolution

actual class QuicCrypto // Actual implementation for JVM
import kotlin.coroutines.CoroutineContext
import kotlin.math.min // For minOf

// Dummy QuicPacket: getBytes now just combines header and protectedPayload.
// protectionMask field is removed as protectPacket will directly modify the header.
actual class QuicPacket actual constructor(
    var header: ByteArray, // Made var to allow modification by protectPacket
    val payload: ByteArray,
    // var protectionMask: ByteArray?, // Removed
    var protectedPayload: ByteArray?
) {
    // Constructor from QuicCurl.kt for compatibility with how it's created there
    actual constructor(
        packetType: QuicPacketType?,
        connectionId: ByteArray,
        packetNumber: ULong,
        payload: ByteArray,
        protectionMask: ByteArray?, // This param is no longer used for a field
        protectedPayload: ByteArray?
    ) : this(
        // Simplified header construction for the dummy. In reality, header is built based on these.
        // For this dummy, let's assume the 'header' passed to primary constructor is pre-formed.
        // Or, if we need to use packetType etc., this dummy needs more logic.
        // To keep it simple for protectPacket, the header field will be used directly.
        // The QuicCurl.kt test passes a 'sampleHeader' when it creates QuicPacket for protectPacket test.
        // Let's assume the primary constructor is used with a pre-formed header.
        header = if (packetType == QuicPacketType.INITIAL) connectionId else connectionId, // Placeholder for actual header construction
        payload = payload,
        protectedPayload = protectedPayload
    ) {
        // This secondary constructor needs to provide a 'header' byte array.
        // The one in QuicCurl.kt is:
        // internal fun headerPlaceholder(): ByteArray = if (packetType == null) connectionId else connectionId
        // Let's use that logic here for the 'header' field if this constructor is called.
        // However, the primary constructor already does this. This logic is now part of the primary constructor's default for 'header'.
        // For alignment with commonTest, which constructs QuicPacket using the commonMain definition (which doesn't have a direct header field but headerPlaceholder),
        // this dummy actual class needs to ensure its 'header' field is properly initialized when called from commonTest context.
        // The primary constructor above uses packetType and connectionId. This is what QuicCurl.kt's QuicPacket uses for its headerPlaceholder().
        // So, this.header is appropriately set by the primary constructor based on packetType/connectionId.
    }


    // This internal fun is from the common QuicPacket in QuicCurl.kt
    // For the actual implementation of protectPacket, AAD is derived from packet.header directly.
    // So, this dummy's headerPlaceholder should reflect that for test consistency.
    internal fun headerPlaceholder(): ByteArray = this.header


    actual fun getBytes(): ByteArray {
        return (header) + (protectedPayload ?: payload) // Header is now pre-protected by protectPacket
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
// This should ideally be an import evolution.QuicPacketType if defined in commonMain/QuicCurl.kt
// For dummy class to compile, it's defined here.
enum class QuicPacketType { INITIAL }


@Deprecated(
    message = "Use CCEK-based crypto services (HkdfService, AesService) instead.",
    replaceWith = ReplaceWith("coroutineContext[HkdfServiceKey] or coroutineContext[AesServiceKey]", "evolution.HkdfServiceKey", "evolution.AesServiceKey")
)
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



internal actual suspend fun protectPacket(
    context: CoroutineContext,
    packet: QuicPacket,       // packet.header is IN/OUT (will be modified)
    keys: QuicInitialKeys,
    connection: QuicConnection
): ByteArray {
    val aesService = context[AesServiceKey]
        ?: throw IllegalStateException("AesService not found in CoroutineContext.")

    // 1. Payload Encryption (AES-GCM)
    val payloadKey = keys.payloadProtectionKey(context)
    val payloadIv = keys.payloadIv(context)

    // AAD for payload encryption is the QUIC packet header (original, unprotected version)
    // IMPORTANT: Use a copy of the header for AAD if the header itself will be modified by HP.
    val aad = packet.header.copyOf() // Use a copy for AAD
    packet.protectedPayload = aesService.gcmEncrypt(payloadKey, payloadIv, packet.payload, aad)

    // 2. Header Protection
    val sampleLength = 16
    if ((packet.protectedPayload?.size ?: 0) < sampleLength) {
        throw IllegalStateException("Protected payload is too short to extract a sample for header protection.")
    }
    val sample = packet.protectedPayload!!.copyOfRange(0, sampleLength)

    val hpKey = keys.headerProtectionKey(context)
    val fullHpMask = aesService.ecbEncrypt(hpKey, sample)

    // QUIC uses the first 5 bytes of the ECB output as the mask.
    val hpMaskFirst5Bytes = fullHpMask.copyOfRange(0, 5)

    // Apply the mask to the packet header (modifies packet.header directly)
    // This is a simplified application. Real QUIC protection is bit-specific.

    // Determine Packet Number Length (PNL) and offset from header's first byte.
    // For Long Headers (first bit is 1): PNL = (packet.header[0] & 0x03) + 1
    // For Short Headers (first bit is 0): PNL = (packet.header[0] & 0x03) + 1 (careful, different bits might be protected)
    // For simplicity, assume PNL is known or fixed for this dummy, and PN starts after some fixed offset.

    val headerCopy = packet.header.copyOf() // Work on a copy

    // Protect first byte (flags):
    // Long header: mask bit 4 (0x10) if PNL protects type, or bits 0-3 if PNL protects PN length.
    // Short header: mask bit 5 (0x20) if it protects key phase, or bits 0-3 if PNL protects PN length.
    // Simplified: XOR relevant bits of first byte with hpMaskFirst5Bytes[0]
    // Example: headerCopy[0] = (headerCopy[0].toInt() xor (hpMaskFirst5Bytes[0].toInt() & 0x0F)).toByte() // Protects lower 4 bits (PN length)
    // A more specific mask would be needed based on header type (long/short)
    // For now, a simple XOR of the first byte:
    if (headerCopy.isNotEmpty()) {
        // The actual QUIC spec:
        // if ((headerCopy[0] and 0x80.toByte()) == 0x80.toByte()) { // Long Header
        //    headerCopy[0] = (headerCopy[0].toInt() xor (hpMaskFirst5Bytes[0].toInt() and 0x0F)).toByte()
        // } else { // Short Header
        //    headerCopy[0] = (headerCopy[0].toInt() xor (hpMaskFirst5Bytes[0].toInt() and 0x1F)).toByte()
        // }
        // For this simplified example, just XOR the whole byte.
        headerCopy[0] = (headerCopy[0].toInt() xor hpMaskFirst5Bytes[0].toInt()).toByte()
    }

    // Protect Packet Number (PN)
    // Assume PN starts at a conceptual offset `pnOffset` and has length `pnActualLength`.
    // This is highly dependent on the actual header structure.
    // For this dummy, let's assume PN is at offset 1 and is up to 4 bytes.
    val pnOffset = 1 // Conceptual offset of Packet Number field in the header
    val maxPnLengthInMask = 4 // hpMaskFirst5Bytes[1] to hpMaskFirst5Bytes[4] cover up to 4 bytes of PN
=======
>>>>>>> origin/jules_wip_6906935130323988499

    for (i in 0 until maxPnLengthInMask) {
        if (pnOffset + i < headerCopy.size) {
            headerCopy[pnOffset + i] = (headerCopy[pnOffset + i].toInt() xor hpMaskFirst5Bytes[i + 1].toInt()).toByte()
        } else {
            break // Packet number field in header is shorter than 4 bytes
            break
        }
    }

    packet.header = headerCopy
>>>>>>> origin/jules_wip_6906935130323988499

    return packet.getBytes()
}

internal actual suspend fun unprotectPacket(
    context: CoroutineContext,
    protectedPacketBytes: ByteArray,
    keys: QuicInitialKeys,
    connection: QuicConnection // Used for PN reconstruction, error reporting etc.
    connection: QuicConnection
): QuicPacket? {
    val aesService = context[AesServiceKey]
        ?: throw IllegalStateException("AesService not found in CoroutineContext for unprotection.")

    // Simplified parsing: Assume a fixed conceptual header length for this example.
    // This is a major simplification point for this subtask.
    // A real implementation would parse the header structure first.

    // Simplification: Assume the header to be unprotected is the first 'X' bytes,
    // and the rest is payloadCiphertextWithTag. 'X' would be known by a parser.
    // Let's say, for this example, a parser has identified that the packet number field ends at byte `pnEndOffset`.
    // The sample for HP is after this. No, sample is from *payload* ciphertext.

    // To break circular dependency for HP mask & PN unmasking:
    // 1. The PN length is encoded in the first header byte.
    // 2. This first byte itself is protected.
    // 3. The mask for the first byte depends on the HP key and a sample of the *payload ciphertext*.

    // Assume a preliminary parse can give us the start of the payload ciphertext.
    // This is often after version, CIDs, token, and payload length fields in a long header.
    // For this subtask, this is a MAJOR simplification.
    val payloadCiphertextOffset = 20 // Highly_Simplified_Offset_To_Payload_Ciphertext_Start. Assume this is where payload ciphertext begins.
                                     // This implies the header is payloadCiphertextOffset bytes long.

    if (protectedPacketBytes.size < payloadCiphertextOffset + 16) { // Need at least 16 bytes for GCM tag on payload, and sample comes from this.
        return null // Not enough data for sample or payload
    }

    // Extract sample for HP from the *protected* payload (ciphertext)
=======

    if (protectedPacketBytes.size < payloadCiphertextOffset + 16) {
        return null
    }

>>>>>>> origin/jules_wip_6906935130323988499
    val sample = protectedPacketBytes.copyOfRange(payloadCiphertextOffset, payloadCiphertextOffset + 16)
    val hpKey = keys.headerProtectionKey(context)
    val fullHpMask = aesService.ecbEncrypt(hpKey, sample)
    val hpMaskFirst5Bytes = fullHpMask.copyOfRange(0, 5)

    // Now unmask the header part.
    // The header part is from byte 0 to payloadCiphertextOffset - 1.
    val headerCandidate = protectedPacketBytes.copyOfRange(0, payloadCiphertextOffset)

    // The first byte and the PN field need to be unmasked.
=======
    val headerCandidate = protectedPacketBytes.copyOfRange(0, payloadCiphertextOffset)

>>>>>>> origin/jules_wip_6906935130323988499
    if (headerCandidate.isNotEmpty()) {
        headerCandidate[0] = (headerCandidate[0].toInt() xor hpMaskFirst5Bytes[0].toInt()).toByte()
    }

    // Unmask conceptual PN (e.g., at offset 1 for up to 4 bytes)
    val pnOffsetInHeader = 1 // Conceptual
    for (i in 0 until 4) { // Unmask up to 4 bytes for PN
=======
    val pnOffsetInHeader = 1
    for (i in 0 until 4) {
>>>>>>> origin/jules_wip_6906935130323988499
        if (pnOffsetInHeader + i < headerCandidate.size && (i + 1) < hpMaskFirst5Bytes.size) {
            headerCandidate[pnOffsetInHeader + i] = (headerCandidate[pnOffsetInHeader + i].toInt() xor hpMaskFirst5Bytes[i + 1].toInt()).toByte()
        } else {
            break
        }
    }
    // Now, `headerCandidate` is the unprotected header.
    // A full parser would now use this to determine actual PN length, packet type, CIDs, etc.

    val payloadKey = keys.payloadProtectionKey(context)
    val payloadIv = keys.payloadIv(context)
    val aadForPayload = headerCandidate.copyOf() // Use the now unprotected header as AAD
=======

    val payloadKey = keys.payloadProtectionKey(context)
    val payloadIv = keys.payloadIv(context)
    val aadForPayload = headerCandidate.copyOf()
>>>>>>> origin/jules_wip_6906935130323988499

    val payloadCiphertextWithTag = protectedPacketBytes.copyOfRange(payloadCiphertextOffset, protectedPacketBytes.size)

    val decryptedPayload = try {
        aesService.gcmDecrypt(payloadKey, payloadIv, payloadCiphertextWithTag, aadForPayload)
    } catch (e: Exception) {
        // GCM decryption failure (e.g., tag mismatch)
        return null // Indicate unprotection failure
    }

    // Reconstruct QuicPacket. This needs proper parsing of the unprotectedHeader.
    // Using placeholders for these parsed values:
    val parsedPacketType: QuicPacketType? = QuicPacketType.INITIAL // Placeholder - would parse from headerCandidate[0]
    val parsedConnectionId: ByteArray = connection.connectionId     // Placeholder - would parse from headerCandidate
    val parsedPacketNumber: ULong = 0uL                             // Placeholder - would parse from unmasked PN in headerCandidate
                                                                    // and reconstruct using connection.largestAcked etc.

    // Create QuicPacket using the primary constructor that takes a direct header ByteArray
    return QuicPacket(
        header = headerCandidate,
        payload = decryptedPayload,
        protectedPayload = null // This field is for outgoing packets
    ).apply {
        // The secondary constructor for QuicPacket that takes packetType, CIDs etc. is more for constructing
        // packets for sending. For received packets, we've now got the actual header bytes.
        // Ensure the QuicPacket's internal `packetType`, `connectionId`, `packetNumber` could be set if needed by parsing `headerCandidate`.
=======
        return null
    }

    val parsedPacketType: QuicPacketType? = QuicPacketType.INITIAL
    val parsedConnectionId: ByteArray = connection.connectionId
    val parsedPacketNumber: ULong = 0uL

    return QuicPacket(
        header = headerCandidate,
        payload = decryptedPayload,
        protectedPayload = null
    ).apply {
        // this.packetType = parsedPacketType // Would need QuicPacket to have these as var
        // this.connectionId = parsedConnectionId
        // this.packetNumber = parsedPacketNumber
>>>>>>> origin/jules_wip_6906935130323988499
    }
}
