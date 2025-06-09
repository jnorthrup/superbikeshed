package borg.trikeshed.net.quic.crypto

import evolution.HkdfService // Assuming HkdfService is available from this package

/**
 * Implements HKDF-Expand-Label as defined in RFC 8446, Section 7.1.
 *
 * HKDF-Expand-Label(Secret, Label, Context, Length) =
 *     HKDF-Expand(Secret, HkdfLabel, Length)
 *
 * Where HkdfLabel is:
 * struct {
 *     uint16 length = Length;
 *     opaque label<7..255> = "tls13 " + Label;
 *     opaque context<0..255> = Context;
 * } HkdfLabel;
 *
 * @param hkdfService An instance of HkdfService to perform the HKDF expand operation.
 * @param secret The pseudorandom key (PRK) to expand.
 * @param labelString The label string (e.g., "client in", "quic key").
 * @param context A byte array representing the context (e.g., transcript hash).
 * @param length The desired output length in bytes.
 * @return The derived keying material (OKM) of the specified length.
 */
suspend fun hkdfExpandLabel(
    hkdfService: HkdfService,
    secret: ByteArray,
    labelString: String,
    context: ByteArray,
    length: Int
): ByteArray {
    // Construct the "tls13 " prefix + label
    val tlsLabelPrefix = "tls13 "
    val fullLabel = tlsLabelPrefix + labelString
    val labelBytes = fullLabel.encodeToByteArray()

    // Check constraints on label and context lengths (as per RFC 8446)
    // label <7..255> means (prefix + label) length. "tls13 " is 6 bytes.
    require(labelBytes.size in 7..255) {
        "Full label ('tls13 ' + label) length must be between 7 and 255 bytes. Actual: ${labelBytes.size}"
    }
    require(context.size <= 255) {
        "Context length must be at most 255 bytes. Actual: ${context.size}"
    }

    // Construct HkdfLabel:
    // uint16 length (output length)
    // opaque label<length_of_label_bytes> (length as 1 byte)
    // opaque context<length_of_context_bytes> (length as 1 byte)

    val lengthBytes = byteArrayOf(
        (length shr 8).toByte(), // High byte of output length
        length.toByte()         // Low byte of output length
    )

    val hkdfLabelInfo = ByteArray(2 + 1 + labelBytes.size + 1 + context.size)
    var offset = 0

    // Add output length (uint16)
    lengthBytes.copyInto(hkdfLabelInfo, offset, 0, 2)
    offset += 2

    // Add label (length-prefixed byte string, length is 1 byte)
    hkdfLabelInfo[offset++] = labelBytes.size.toByte()
    labelBytes.copyInto(hkdfLabelInfo, offset, 0, labelBytes.size)
    offset += labelBytes.size

    // Add context (length-prefixed byte string, length is 1 byte)
    hkdfLabelInfo[offset++] = context.size.toByte()
    context.copyInto(hkdfLabelInfo, offset, 0, context.size)
    // offset += context.size // Not strictly needed as it's the last part

    return hkdfService.expand(secret, hkdfLabelInfo, length)
}

/**
 * Computes HMAC-SHA-256.
 * This function uses HkdfService.extract for HMAC computation, as HKDF-Extract(salt, IKM)
 * is equivalent to HMAC-Hash(salt, IKM) where Hash is the underlying hash function of HKDF.
 * For an HkdfService based on SHA-256, this will be HMAC-SHA-256.
 *
 * @param hkdfService An instance of HkdfService (expected to use SHA-256).
 * @param key The key for HMAC.
 * @param data The data to authenticate.
 * @return The HMAC-SHA-256 digest.
 */
suspend fun hmacSha256(
    hkdfService: HkdfService,
    key: ByteArray,
    data: ByteArray
): ByteArray {
    // HKDF-Extract(salt, IKM) is equivalent to HMAC-Hash(salt, IKM).
    // Here, 'key' acts as the HMAC salt, and 'data' is the IKM.
    return hkdfService.extract(salt = key, ikm = data)
}

/**
 * Applies QUIC Header Protection.
 *
 * @param aesService Service to perform AES-ECB encryption for the mask.
 * @param hpKey The Header Protection Key.
 * @param packetHeaderBytes The unprotected QUIC packet header bytes (up to and including the packet number).
 * @param encryptedPayloadBytes The encrypted payload of the QUIC packet. The sample for HP is taken from here.
 * @param packetNumberOffset The starting offset of the Packet Number field within `packetHeaderBytes`.
 * @param pnLengthBytes The length of the Packet Number field in bytes (1 to 4).
 * @return A new byte array containing the header with protection applied.
 * @throws IllegalArgumentException if inputs are invalid (e.g., insufficient payload for sampling).
 */
suspend fun applyHeaderProtection(
    aesService: evolution.AesService,
    hpKey: ByteArray,
    packetHeaderBytes: ByteArray, // Unprotected header (clear PN)
    encryptedPayloadCiphertext: ByteArray, // Ciphertext of payload (PN + Frames for Long, Frames for Short)
                                         // For Long headers, PN is part of this for sampling.
                                         // For Short headers, PN is NOT part of this for sampling.
                                         // Sample is always from *payload ciphertext*.
    packetNumberOffsetInHeader: Int, // Offset of PN *within packetHeaderBytes*
    pnLengthBytes: Int,
    isShortHeader: Boolean
): ByteArray {
    // Validate offsets and lengths
    if (packetNumberOffsetInHeader < 0 || packetNumberOffsetInHeader + pnLengthBytes > packetHeaderBytes.size) {
        throw IllegalArgumentException("Packet number offset/length is out of bounds for the provided header.")
    }

    val sampleLength = 16
    val samplePayloadActualOffset = 4

    val sample = if (encryptedPayloadCiphertext.size >= samplePayloadActualOffset + sampleLength) {
        encryptedPayloadCiphertext.copyOfRange(samplePayloadActualOffset, samplePayloadActualOffset + sampleLength)
    } else {
        val availableData = encryptedPayloadCiphertext.drop(samplePayloadActualOffset).toByteArray()
        availableData + ByteArray(sampleLength - availableData.size)
    }

    val mask = aesService.ecbEncrypt(hpKey, sample)
        ?: throw RuntimeException("AES-ECB encryption for header protection mask failed")

    if (mask.size < 1 + pnLengthBytes.coerceAtLeast(1)) {
        throw IllegalStateException("Generated HP mask is too short (${mask.size} bytes) for PN length $pnLengthBytes")
    }

    val protectedHeader = packetHeaderBytes.clone()
    val firstByteProtectionMask = if (isShortHeader) 0x07 else 0x0F // KPP for short, RRPP for long

    protectedHeader[0] = (protectedHeader[0].toInt() xor (mask[0].toInt() and firstByteProtectionMask)).toByte()

    for (i in 0 until pnLengthBytes) {
        if (packetNumberOffset + i < protectedHeader.size && (1 + i) < mask.size) {
            protectedHeader[packetNumberOffset + i] =
                (protectedHeader[packetNumberOffset + i].toInt() xor mask[1 + i].toInt()).toByte()
        } else {
            throw IllegalStateException("HP Mask too short or PN offset/length incorrect during PN protection.")
        }
    }
    return protectedHeader
}

/**
 * Removes QUIC Header Protection.
 *
 * @param aesService Service to perform AES-ECB encryption for the mask.
 * @param hpKey The Header Protection Key.
 * @param protectedPacketBytes The received QUIC packet bytes, starting with the protected header.
 * @param encryptedPayloadSampleOffset The offset within `protectedPacketBytes` where the 16-byte sample for the HP mask should be taken from.
 *                                     This sample is part of the (still encrypted) payload.
 *                                     RFC 9001, 5.4.2: "The sample is taken from the UDP datagram payload, starting 4 bytes
 *                                     after the packet number field ends."
 *                                     This means `encryptedPayloadSampleOffset` should point to `PN_offset_in_UDP_payload + PN_length + 4`.
 * @param knownPacketNumberOffset If the packet number offset within the header is already known (e.g. for short headers after DCID),
 *                                provide it. Otherwise, it will be determined after unmasking the first byte for long headers.
 * @return A Pair containing the unprotected full header (ByteArray) and the decoded Packet Number (Long), or null on failure.
 *         The returned header is a new array; the input array is not modified.
 * @throws IllegalArgumentException if inputs are invalid.
 */
suspend fun removeHeaderProtection(
    aesService: evolution.AesService,
    hpKey: ByteArray,
    protectedPacketBytes: ByteArray, // Entire received QUIC packet (or at least header + sample region)
    encryptedPayloadSampleOffset: Int, // Offset from start of protectedPacketBytes to where sample begins
    // For this simplified version, we will require packetNumberOffset and pnLength to be determined *after* unmasking first byte,
    // or passed in if known (e.g. for Short header where DCID length is known from connection context).
    // To make it more self-contained for now for the test:
    // We will assume for long headers, the PN offset is fixed after CID lens, token len, overall len.
    // For short headers, it's fixed after DCID len.
    // This is still a simplification. A full parser is needed for robust PN offset.
    // Let's assume the caller provides the *initial* guess for packetNumberOffset (e.g., after CIDs for long header).
    initialPacketNumberOffsetGuess: Int
): Pair<ByteArray, Long>? {
    val sampleLength = 16
    if (encryptedPayloadSampleOffset < 0 || encryptedPayloadSampleOffset + sampleLength > protectedPacketBytes.size) {
        // Try to pad if actual payload is shorter than sample, but sample must start within bounds
        if (encryptedPayloadSampleOffset > protectedPacketBytes.size) {
             // println("Warning: HP sample offset $encryptedPayloadSampleOffset is beyond packet size ${protectedPacketBytes.size}")
            return null
        }
        val availableData = protectedPacketBytes.drop(encryptedPayloadSampleOffset).toByteArray()
        if (availableData.size < sampleLength && availableData.isNotEmpty()) { // Allow if some data is there for sample
             // println("Warning: HP sample data is shorter than $sampleLength bytes, padding with zeros.")
            // Ok to proceed with padded sample if at least some of encrypted payload is available for sample start
        } else if (availableData.isEmpty() && sampleLength > 0) {
            // println("Error: No data available at HP sample offset $encryptedPayloadSampleOffset for sample of length $sampleLength.")
            return null
        }
    }

    val sample = if (protectedPacketBytes.size >= encryptedPayloadSampleOffset + sampleLength) {
        protectedPacketBytes.copyOfRange(encryptedPayloadSampleOffset, encryptedPayloadSampleOffset + sampleLength)
    } else {
        val availableData = protectedPacketBytes.drop(encryptedPayloadSampleOffset).toByteArray()
        if (availableData.isEmpty() && sampleLength > 0) return null // Cannot sample if no data at offset
        availableData + ByteArray(sampleLength - availableData.size) // Pad with zeros
    }

    val mask = aesService.ecbEncrypt(hpKey, sample)
        ?: run { println("HP Mask generation failed"); return null }

    if (mask.isEmpty()) { println("HP Mask is empty"); return null }

    val unprotectedHeader = protectedPacketBytes.clone() // Clone to unprotect; size might be more than just header

    // Unmask first byte to determine PN length and header type
    val firstByteProtected = unprotectedHeader[0].toInt()
    val isShortHeader = (firstByteProtected and 0x80) == 0

    val firstByteProtectionMask = if (isShortHeader) 0x07 else 0x0F // KPP for short, RRPP for long
    val firstByteUnmasked = (firstByteProtected xor (mask[0].toInt() and firstByteProtectionMask)).toByte()
    unprotectedHeader[0] = firstByteUnmasked

    val pnLengthBytes = (firstByteUnmasked.toInt() and 0x03) + 1

    if (mask.size < 1 + pnLengthBytes) {
        println("HP Mask too short (${mask.size}) for PN length $pnLengthBytes")
        return null
    }

    // Now determine actual packetNumberOffset based on header type and unmasked first byte (if needed)
    // This is the complex part. For the test, we will assume `initialPacketNumberOffsetGuess` is accurate enough
    // to point to the start of PN field, or slightly before (e.g. start of Length for Long Headers).
    // A real implementation needs to parse CIDs, TokenLen, Length to find PN start.
    // For this test, we will assume `initialPacketNumberOffsetGuess` is the *actual* start of PN.
    val actualPacketNumberOffset = initialPacketNumberOffsetGuess

    if (actualPacketNumberOffset < 0 || actualPacketNumberOffset + pnLengthBytes > unprotectedHeader.size) {
         println("PN offset $actualPacketNumberOffset or length $pnLengthBytes out of bounds for header size ${unprotectedHeader.size}")
        return null
    }

    // Unmask the Packet Number
    var decodedPacketNumber: Long = 0
    for (i in 0 until pnLengthBytes) {
        val originalPnByte = unprotectedHeader[actualPacketNumberOffset + i]
        val unmaskedPnByte = (originalPnByte.toInt() xor mask[1 + i].toInt()).toByte()
        unprotectedHeader[actualPacketNumberOffset + i] = unmaskedPnByte
        decodedPacketNumber = (decodedPacketNumber shl 8) + (unmaskedPnByte.toLong() and 0xFFL)
    }

    // The returned 'unprotectedHeader' here is the initially passed `protectedPacketBytes` but with its
    // first byte and PN field unmasked. The caller needs to know how long the actual header is.
    // For simplicity, we return the whole array which might be more than header.
    return Pair(unprotectedHeader, decodedPacketNumber)
}

/**
 * Computes the AEAD nonce for QUIC packet protection as per RFC 9001, Section 5.3.
 * The nonce is formed by XORing the packet protection IV with the packet number.
 * The packet number is left-padded with zeros to the length of the IV (typically 12 bytes).
 *
 * @param baseIv The packet protection IV (e.g., from QuicSecrets.iv), expected to be 12 bytes.
 * @param packetNumber The packet number.
 * @return A 12-byte nonce.
 * @throws IllegalArgumentException if the baseIv is not 12 bytes long.
 */
fun computeNonce(baseIv: ByteArray, packetNumber: Long): ByteArray {
    if (baseIv.size != 12) {
        throw IllegalArgumentException("Base IV must be 12 bytes long. Was: ${baseIv.size}")
    }

    val nonce = baseIv.clone() // Start with a copy of the base IV to XOR into

    // Left-pad packet number to 12 bytes (same length as IV)
    // Iterate from the rightmost byte of the nonce array and XOR with packet number bytes
    // The packet number is a Long (8 bytes). We need to place its bytes at the end of a conceptual 12-byte array.
    // So, bytes 0-3 of the padded PN are 0, bytes 4-11 are the PN itself.
    // Nonce construction: IV[i] XOR PaddedPN[i]
    // PaddedPN = 0...0 || PN_bytes (PN is 8 bytes, so 4 leading zeros for 12 byte total)

    // Effectively, we XOR the last 8 bytes of the IV with the 8 bytes of the packet number.
    // The first 4 bytes of the IV remain as they are (XORed with 0).
    var pn = packetNumber
    for (i in 11 downTo 4) { // XOR the last 8 bytes (indices 4 through 11)
        if (pn == 0L && i < 11 - Long.SIZE_BYTES +1) { // Optimization: if PN is all processed, rest are XOR with 0
             // This condition might be off, let's do full 8 bytes of PN
        }
        nonce[i] = (nonce[i].toInt() xor (pn and 0xFF).toInt()).toByte()
        pn = pn shr 8
        if (pn == 0L && i <= 4) break // All bytes of packet number processed
    }
    // If packetNumber was > 0 and filled less than 8 bytes after shifting,
    // the remaining high bytes of the conceptual padded packet number are 0.
    // The XOR with 0 leaves those IV bytes unchanged, which is correct.
    // Example: IV = IV0..IV11, PN_padded = 0,0,0,0,PN0,PN1,PN2,PN3,PN4,PN5,PN6,PN7 (PN7 is LSB)
    // nonce[11] = IV[11] XOR PN7
    // nonce[10] = IV[10] XOR PN6
    // ...
    // nonce[4] = IV[4] XOR PN0
    // nonce[0..3] = IV[0..3] XOR 0 = IV[0..3]

    return nonce
}
