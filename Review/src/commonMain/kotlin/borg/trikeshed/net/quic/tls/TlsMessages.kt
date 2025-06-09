package borg.trikeshed.net.quic.tls

import borg.trikeshed.net.quic.utils.writeShort
import borg.trikeshed.net.quic.utils.writeShortLengthPrefixed
import borg.trikeshed.net.quic.utils.writeByteLengthPrefixed

// TLS Versions
const val TLS_VERSION_1_3: UShort = 0x0304u
const val TLS_VERSION_1_2: UShort = 0x0303u // For Supported Versions extension

// Cipher Suites
const val TLS_AES_128_GCM_SHA256: UShort = 0x1301u
const val TLS_AES_256_GCM_SHA384: UShort = 0x1302u
const val TLS_CHACHA20_POLY1305_SHA256: UShort = 0x1303u

// Key Exchange Groups
const val X25519_GROUP: UShort = 0x001Du // Curve25519
const val P256_GROUP: UShort = 0x0017u // NIST P-256
const val P384_GROUP: UShort = 0x0018u // NIST P-384

// Extension Types (subset from RFC 8446 and RFC 9001)
object TlsExtensionType {
    const val SERVER_NAME: UShort = 0x0000u
    const val SUPPORTED_GROUPS: UShort = 0x000au
    const val SIGNATURE_ALGORITHMS: UShort = 0x000du
    const val KEY_SHARE: UShort = 0x0033u
    const val PRE_SHARED_KEY: UShort = 0x0029u
    const val SUPPORTED_VERSIONS: UShort = 0x002bu
    const val QUIC_TRANSPORT_PARAMETERS: UShort = 0x39u // From RFC 9001 (was 0xffa5 in drafts)
    // SignatureScheme values from RFC 8446, Section 4.2.3
    const val RSA_PKCS1_SHA256: UShort = 0x0401u
    const val RSA_PSS_RSAE_SHA256: UShort = 0x0804u
    const val ECDSA_SECP256R1_SHA256: UShort = 0x0403u
    const val ECDSA_SECP384R1_SHA384: UShort = 0x0503u
    const val ED25519: UShort = 0x0807u
}

/**
 * TLS SignatureScheme values.
 * RFC 8446, Section 4.2.3.
 */
object TlsSignatureScheme {
    const val RSA_PKCS1_SHA256: UShort = 0x0401u
    const val RSA_PKCS1_SHA384: UShort = 0x0501u
    const val RSA_PKCS1_SHA512: UShort = 0x0601u

    const val ECDSA_SECP256R1_SHA256: UShort = 0x0403u
    const val ECDSA_SECP384R1_SHA384: UShort = 0x0503u
    const val ECDSA_SECP521R1_SHA512: UShort = 0x0603u

    const val RSA_PSS_RSAE_SHA256: UShort = 0x0804u
    const val RSA_PSS_RSAE_SHA384: UShort = 0x0805u
    const val RSA_PSS_RSAE_SHA512: UShort = 0x0806u

    const val ED25519: UShort = 0x0807u
    const val ED448: UShort = 0x0808u
    // For TLS 1.2 and earlier, if needed:
    // const val RSA_PKCS1_SHA1: UShort = 0x0201u
    // const val ECDSA_SHA1: UShort = 0x0203u
}


data class KeyShareEntry(
    val group: UShort,
    val keyExchange: ByteArray
) {
    fun serialize(): ByteArray {
        var result = byteArrayOf()
        result += group.writeShort()
        result += keyExchange.size.toUShort().writeShort() // Length of key_exchange_octet_string
        result += keyExchange
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as KeyShareEntry

        if (group != other.group) return false
        if (!keyExchange.contentEquals(other.keyExchange)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = group.hashCode()
        result = 31 * result + keyExchange.contentHashCode()
        return result
    }
}

data class ClientHelloData(
    val version: UShort = TLS_VERSION_1_3, // Legacy version field, typically 0x0303 for TLS 1.3
    val clientRandom: ByteArray, // 32 bytes
    val sessionId: ByteArray = byteArrayOf(), // Optional, can be empty for TLS 1.3 (max 32 bytes)
    val cipherSuites: List<UShort>, // List of offered cipher suites
    val compressionMethods: ByteArray = byteArrayOf(0x00), // Must contain 0x00 (null compression)
    // Extensions
    val keyShareEntries: List<KeyShareEntry>,
    val quicTransportParameters: ByteArray, // Serialized QUIC transport parameters
    val supportedVersions: List<UShort> = listOf(TLS_VERSION_1_3), // For supported_versions extension
    val supportedGroups: List<UShort> = listOf(X25519_GROUP, P256_GROUP), // For supported_groups extension
    val serverName: String? = null // For Server Name Indication (SNI) extension
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ClientHelloData
        return version == other.version &&
                clientRandom.contentEquals(other.clientRandom) &&
                sessionId.contentEquals(other.sessionId) &&
                cipherSuites == other.cipherSuites &&
                compressionMethods.contentEquals(other.compressionMethods) &&
                keyShareEntries == other.keyShareEntries &&
                quicTransportParameters.contentEquals(other.quicTransportParameters) &&
                supportedVersions == other.supportedVersions &&
                supportedGroups == other.supportedGroups &&
                serverName == other.serverName
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + clientRandom.contentHashCode()
        result = 31 * result + sessionId.contentHashCode()
        result = 31 * result + cipherSuites.hashCode()
        result = 31 * result + compressionMethods.contentHashCode()
        result = 31 * result + keyShareEntries.hashCode()
        result = 31 * result + quicTransportParameters.contentHashCode()
        result = 31 * result + supportedVersions.hashCode()
        result = 31 * result + supportedGroups.hashCode()
        result = 31 * result + (serverName?.hashCode() ?: 0)
        return result
    }
}

data class ServerHelloData(
    val version: UShort, // Legacy version (TLS 1.2) or selected version for TLS 1.3 (in extension)
    val serverRandom: ByteArray, // 32 bytes
    val sessionId: ByteArray, // Echoed from ClientHello or new for session resumption
    val cipherSuite: UShort,
    val compressionMethod: UByte = 0x00u, // Should be 0x00 for TLS 1.3
    // Extensions
    val keyShareEntry: KeyShareEntry?, // Only one for ServerHello
    val supportedVersion: UShort? // From supported_versions extension if present
) {
     override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ServerHelloData
        return version == other.version &&
                serverRandom.contentEquals(other.serverRandom) &&
                sessionId.contentEquals(other.sessionId) &&
                cipherSuite == other.cipherSuite &&
                compressionMethod == other.compressionMethod &&
                keyShareEntry == other.keyShareEntry &&
                supportedVersion == other.supportedVersion
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + serverRandom.contentHashCode()
        result = 31 * result + sessionId.contentHashCode()
        result = 31 * result + cipherSuite.hashCode()
        result = 31 * result + compressionMethod.hashCode()
        result = 31 * result + (keyShareEntry?.hashCode() ?: 0)
        result = 31 * result + (supportedVersion?.hashCode() ?: 0)
        return result
    }
}

data class EncryptedExtensionsData(
    val extensions: Map<UShort, ByteArray> = emptyMap()
    // Example: val serverName: String? = null (from Server Name Indication extension)
    // In a real implementation, this would contain a list of extensions.
)

data class FinishedData(
    val verifyData: ByteArray // Typically 32 bytes for SHA256-based HMAC
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as FinishedData
        return verifyData.contentEquals(other.verifyData)
    }

    override fun hashCode(): Int = verifyData.contentHashCode()
}

// --- Serialization Helpers ---

internal fun serializeExtension(type: UShort, data: ByteArray): ByteArray {
    var result = byteArrayOf()
    result += type.writeShort()
    result += data.size.toUShort().writeShort() // Extension data length
    result += data
    return result
}

internal fun serializeSupportedVersionsExtension(versions: List<UShort>): ByteArray {
    var data = byteArrayOf()
    data += (versions.size * 2).toUByte().toByte() // List length in bytes (1 byte for list length)
    for (version in versions) {
        data += version.writeShort()
    }
    return serializeExtension(TlsExtensionType.SUPPORTED_VERSIONS, data)
}

internal fun serializeSupportedGroupsExtension(groups: List<UShort>): ByteArray {
    var data = byteArrayOf()
    data += (groups.size * 2).toUShort().writeShort() // List length in bytes (2 bytes for list length)
    for (group in groups) {
        data += group.writeShort()
    }
    return serializeExtension(TlsExtensionType.SUPPORTED_GROUPS, data)
}

internal fun serializeKeyShareExtension(entries: List<KeyShareEntry>, forClientHello: Boolean): ByteArray {
    var clientKeyShareBody = byteArrayOf()
    for (entry in entries) {
        clientKeyShareBody += entry.serialize()
    }

    // For ClientHello, the KeyShare extension data itself has a length field for all entries.
    // For ServerHello, there's no outer length for the single entry (it's directly the entry).
    val data = if (forClientHello) {
        clientKeyShareBody.size.toUShort().writeShort() + clientKeyShareBody
    } else {
        clientKeyShareBody // Should only be one entry for ServerHello, already serialized
    }
    return serializeExtension(TlsExtensionType.KEY_SHARE, data)
}

internal fun serializeSniExtension(serverName: String): ByteArray {
    // SNI extension data:
    // NameList length (2 bytes)
    //  NameType (1 byte, 0x00 for host_name)
    //  HostName length (2 bytes)
    //  HostName (variable)
    val serverNameBytes = serverName.encodeToByteArray()
    var sniData = byteArrayOf()
    sniData += 0x00.toByte() // NameType: host_name
    sniData += serverNameBytes.size.toUShort().writeShort() // HostName length
    sniData += serverNameBytes // HostName

    var nameList = byteArrayOf()
    nameList += sniData.size.toUShort().writeShort() // NameList length
    nameList += sniData

    return serializeExtension(TlsExtensionType.SERVER_NAME, nameList)
}

internal fun serializeQuicTransportParametersExtension(params: ByteArray): ByteArray {
    // The params are already serialized by `serializeQuicTransportParameters`
    return serializeExtension(TlsExtensionType.QUIC_TRANSPORT_PARAMETERS, params)
}


fun serializeClientHello(clientHello: ClientHelloData): ByteArray {
    var payload = byteArrayOf()

    // Legacy Version (fixed to 0x0303 for TLS 1.3)
    payload += (if (clientHello.version == TLS_VERSION_1_3) TLS_VERSION_1_2 else clientHello.version).writeShort()
    // Random
    payload += clientHello.clientRandom // Must be 32 bytes

    // Session ID (length-prefixed)
    payload += clientHello.sessionId.writeByteLengthPrefixed()

    // Cipher Suites (length-prefixed list of UShorts)
    var cipherSuitesBytes = byteArrayOf()
    for (suite in clientHello.cipherSuites) {
        cipherSuitesBytes += suite.writeShort()
    }
    payload += cipherSuitesBytes.writeShortLengthPrefixed() // Total length of cipher suites block

    // Compression Methods (length-prefixed list of Bytes)
    payload += clientHello.compressionMethods.writeByteLengthPrefixed() // Typically [0x01, 0x00] -> 1 byte of 0x00

    // Extensions
    var extensionsBytes = byteArrayOf()

    // Supported Versions (TLS 1.3 specific)
    extensionsBytes += serializeSupportedVersionsExtension(clientHello.supportedVersions)

    // Supported Groups
    extensionsBytes += serializeSupportedGroupsExtension(clientHello.supportedGroups)

    // Key Share
    extensionsBytes += serializeKeyShareExtension(clientHello.keyShareEntries, forClientHello = true)

    // QUIC Transport Parameters
    extensionsBytes += serializeQuicTransportParametersExtension(clientHello.quicTransportParameters)

    // Server Name Indication (SNI)
    clientHello.serverName?.let {
        extensionsBytes += serializeSniExtension(it)
    }

    // TODO: Add other extensions like signature_algorithms, pre_shared_key, etc. if needed

    // Add overall extensions length
    payload += extensionsBytes.size.toUShort().writeShort()
    payload += extensionsBytes

    // The entire ClientHello message is then wrapped in a Handshake message structure:
    // HandshakeType (1 byte, 0x01 for ClientHello)
    // Length (3 bytes, length of the ClientHello payload)
    // Payload (variable)
    var handshakeMessage = byteArrayOf()
    handshakeMessage += 0x01.toByte() // HandshakeType: ClientHello
    handshakeMessage += byteArrayOf(0x00.toByte()) + payload.size.toUShort().writeShort() // Length (24-bit)
    handshakeMessage += payload

    return handshakeMessage
}

// Dummy utils for writing basic types - these should be robust implementations
// These would ideally be in a separate utils file.
// fun UShort.writeShort(): ByteArray = byteArrayOf((this.toInt() shr 8).toByte(), this.toByte())
// fun ByteArray.writeShortLengthPrefixed(): ByteArray = this.size.toUShort().writeShort() + this
// fun ByteArray.writeByteLengthPrefixed(): ByteArray = byteArrayOf(this.size.toUByte().toByte()) + this
// These are now expected to be in borg.trikeshed.net.quic.utils
// private fun UShort.toByte(): Byte = this.toInt().toByte() // Ensure this is a safe cast for your values. Redundant due to Kotlin's .toByte()
// private fun Int.toByte(): Byte = this.toByte() // Ensure this is a safe cast for your values. Redundant.


// --- Deserialization Helpers ---

/**
 * Parses a TLS Handshake message.
 * Returns a Pair of HandshakeType (Byte) and the message payload (ByteArray),
 * or null if the message is too short or malformed.
 */
fun parseHandshakeMessage(bytes: ByteArray): Triple<Byte, UInt, ByteArray>? {
    if (bytes.size < 4) return null // Type (1) + Length (3)
    val handshakeType = bytes[0]
    val length = ((bytes[1].toUInt() and 0xFFu) shl 16) or
                 ((bytes[2].toUInt() and 0xFFu) shl 8) or
                 (bytes[3].toUInt() and 0xFFu)

    if (bytes.size < 4 + length.toInt()) return null // Check if full payload is present
    val payload = bytes.sliceArray(4 until (4 + length.toInt()))
    return Triple(handshakeType, length, payload)
}

fun deserializeServerHello(bytes: ByteArray): ServerHelloData? {
    val parsedHandshake = parseHandshakeMessage(bytes) ?: return null
    if (parsedHandshake.first != 0x02.toByte()) { // 0x02 is ServerHello
        // Not a ServerHello message
        return null
    }
    val payload = parsedHandshake.third
    var offset = 0

    // Legacy Version (2 bytes)
    if (offset + 2 > payload.size) return null
    val legacyVersion = payload.readShort(offset)
    offset += 2

    // Server Random (32 bytes)
    if (offset + 32 > payload.size) return null
    val serverRandom = payload.sliceArray(offset until offset + 32)
    offset += 32

    // Legacy Session ID (length-prefixed, 1 byte for length)
    if (offset + 1 > payload.size) return null
    val sessionIdLength = payload[offset++].toInt() and 0xFF
    if (offset + sessionIdLength > payload.size) return null
    val sessionId = payload.sliceArray(offset until offset + sessionIdLength)
    offset += sessionIdLength

    // Cipher Suite (2 bytes)
    if (offset + 2 > payload.size) return null
    val cipherSuite = payload.readShort(offset)
    offset += 2

    // Compression Method (1 byte)
    if (offset + 1 > payload.size) return null
    val compressionMethod = payload[offset++].toUByte()
    if (compressionMethod != 0x00.toUByte()) {
        // As per RFC 8446, this MUST be 0 for TLS 1.3
        // For earlier versions, it might differ, but we focus on 1.3
        // For strict TLS 1.3, one might return null here if not 0.
    }

    // Extensions (length-prefixed, 2 bytes for length)
    if (offset + 2 > payload.size) return null // Min length for extensions length field
    val extensionsLength = payload.readShort(offset).toInt()
    offset += 2

    if (offset + extensionsLength > payload.size) return null // Check if all extensions bytes are present
    val extensionsBytes = payload.sliceArray(offset until offset + extensionsLength)
    var extOffset = 0

    var keyShareEntry: KeyShareEntry? = null
    var supportedVersion: UShort? = null

    while (extOffset < extensionsBytes.size) {
        if (extOffset + 4 > extensionsBytes.size) return null // Type (2) + Length (2)
        val extType = extensionsBytes.readShort(extOffset)
        extOffset += 2
        val extLen = extensionsBytes.readShort(extOffset).toInt()
        extOffset += 2

        if (extOffset + extLen > extensionsBytes.size) return null
        val extData = extensionsBytes.sliceArray(extOffset until extOffset + extLen)
        extOffset += extLen

        when (extType) {
            TlsExtensionType.KEY_SHARE -> {
                if (extData.size < 4) return null // Group (2) + KeyExchange Length (2)
                val group = extData.readShort(0)
                val keyExchangeLen = extData.readShort(2).toInt()
                if (4 + keyExchangeLen != extData.size) return null // Validate actual key_exchange length
                val keyExchange = extData.sliceArray(4 until 4 + keyExchangeLen)
                keyShareEntry = KeyShareEntry(group, keyExchange)
            }
            TlsExtensionType.SUPPORTED_VERSIONS -> {
                if (extData.size != 2) return null // Should contain exactly one version (2 bytes)
                supportedVersion = extData.readShort(0)
            }
            // Other extensions can be parsed here if needed
        }
    }
    // For TLS 1.3, supported_versions extension is mandatory in ServerHello.
    // KeyShare is also typically mandatory if the ClientHello offered it.
    if (supportedVersion == null || keyShareEntry == null) {
         // Depending on strictness, might return null if these are missing for TLS 1.3
    }


    return ServerHelloData(
        version = legacyVersion, // This is the legacy_version field. Actual version is in supportedVersion.
        serverRandom = serverRandom,
        sessionId = sessionId,
        cipherSuite = cipherSuite,
        compressionMethod = compressionMethod,
        keyShareEntry = keyShareEntry,
        supportedVersion = supportedVersion
    )
}

// Extension of ByteArray to read a UShort, assuming it's in BinaryUtils.kt
// If not, it needs to be defined or imported.
// Example: fun ByteArray.readShort(offset: Int = 0): UShort { ... }

/**
 * Generic TLS Extension data class.
 */
data class TlsExtension(val type: UShort, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TlsExtension
        if (type != other.type) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * EncryptedExtensions message structure (RFC 8446, Section 4.3.1).
 * It's simply a list of extensions.
 */
data class EncryptedExtensionsData(val extensions: List<TlsExtension>) {
    // Note: The EncryptedExtensionsData in the previous subtask had a Map, changing to List<TlsExtension>
    // to better reflect the ordered nature and allow multiple extensions of the same type if permitted by spec (though rare).
    // For most common cases, types are unique.
}

fun deserializeEncryptedExtensions(bytes: ByteArray): EncryptedExtensionsData? {
    val parsedHandshake = parseHandshakeMessage(bytes) ?: return null
    if (parsedHandshake.first != 0x08.toByte()) { // 0x08 is EncryptedExtensions
        // Not an EncryptedExtensions message
        return null
    }
    val payload = parsedHandshake.third // This is the "Extension" structure: list of extensions
    var offset = 0

    if (offset + 2 > payload.size) return null // Extensions Length (2 bytes)
    val totalExtensionsLength = payload.readShort(offset).toInt()
    offset += 2

    if (offset + totalExtensionsLength > payload.size || totalExtensionsLength != payload.size - offset) {
        // Malformed: total extensions length doesn't match remaining payload
        return null
    }

    val extensions = mutableListOf<TlsExtension>()
    val extensionsBytes = payload.sliceArray(offset until offset + totalExtensionsLength)
    var extOffset = 0
    while (extOffset < extensionsBytes.size) {
        if (extOffset + 4 > extensionsBytes.size) return null // Min Type (2) + Length (2)
        val extType = extensionsBytes.readShort(extOffset)
        extOffset += 2
        val extLen = extensionsBytes.readShort(extOffset).toInt()
        extOffset += 2

        if (extOffset + extLen > extensionsBytes.size) return null // Malformed extension
        val extData = extensionsBytes.sliceArray(extOffset until extOffset + extLen)
        extOffset += extLen
        extensions.add(TlsExtension(extType, extData))
    }

    if (extOffset != extensionsBytes.size) {
        // Malformed, did not consume all extension bytes
        return null
    }

    return EncryptedExtensionsData(extensions)
}


// --- Placeholder Data Classes and Deserializers for further handshake messages ---

data class CertificateData(
    val certificateRequestContext: ByteArray, // Length-prefixed (1 byte)
    val certificateList: List<CertificateEntry>
)

data class CertificateEntry(
    val certificateData: ByteArray, // The actual certificate
    val extensions: List<TlsExtension> // Extensions for this certificate
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CertificateEntry
        if (!certificateData.contentEquals(other.certificateData)) return false
        if (extensions != other.extensions) return false // Assuming TlsExtension has good equals
        return true
    }
    override fun hashCode(): Int {
        var result = certificateData.contentHashCode()
        result = 31 * result + extensions.hashCode()
        return result
    }
}

fun deserializeCertificate(bytes: ByteArray): CertificateData? {
    val parsedHandshake = parseHandshakeMessage(bytes) ?: return null
    if (parsedHandshake.first != 0x0B.toByte()) return null // 0x0B is Certificate
    // TODO: Implement full parsing of Certificate message (RFC 8446, Section 4.4.2)
    // For now, return a placeholder or handle basic structure.
    // Placeholder:
    // val certificateRequestContextLen = payload[0].toInt() and 0xFF ...
    // val certificateListLen = payload.readUInt24(offset) ...
    // Placeholder implementation - this needs full parsing logic.
    val payload = parsedHandshake.third
    var currentOffset = 0

    // Certificate Request Context
    if (currentOffset + 1 > payload.size) return null
    val contextLength = payload[currentOffset++].toInt() and 0xFF
    if (currentOffset + contextLength > payload.size) return null
    val certificateRequestContext = payload.sliceArray(currentOffset until currentOffset + contextLength)
    currentOffset += contextLength

    // Certificate List
    if (currentOffset + 3 > payload.size) return null // Length of certificate_list
    val certificateListLength = payload.readUInt24(currentOffset)
    currentOffset += 3

    if (currentOffset + certificateListLength > payload.size) return null
    val certificateListBytes = payload.sliceArray(currentOffset until currentOffset + certificateListLength)
    currentOffset += certificateListLength // Should be payload.size now if correctly formed

    val certificateList = mutableListOf<CertificateEntry>()
    var certListOffset = 0
    while (certListOffset < certificateListBytes.size) {
        // Certificate Entry: cert_data
        if (certListOffset + 3 > certificateListBytes.size) return null // Length of cert_data
        val certDataLength = certificateListBytes.readUInt24(certListOffset)
        certListOffset += 3
        if (certListOffset + certDataLength > certificateListBytes.size) return null
        val certData = certificateListBytes.sliceArray(certListOffset until certListOffset + certDataLength)
        certListOffset += certDataLength

        // Certificate Entry: extensions
        if (certListOffset + 2 > certificateListBytes.size) return null // Length of extensions
        val extensionsLength = certificateListBytes.readShort(certListOffset).toInt()
        certListOffset += 2
        if (certListOffset + extensionsLength > certificateListBytes.size) return null
        val extensionsBytes = certificateListBytes.sliceArray(certListOffset until certListOffset + extensionsLength)
        certListOffset += extensionsLength

        val certExtensions = mutableListOf<TlsExtension>()
        var individualExtOffset = 0
        while (individualExtOffset < extensionsBytes.size) {
            if (individualExtOffset + 4 > extensionsBytes.size) return null // Type + Length
            val extType = extensionsBytes.readShort(individualExtOffset)
            individualExtOffset += 2
            val extLen = extensionsBytes.readShort(individualExtOffset).toInt()
            individualExtOffset += 2
            if (individualExtOffset + extLen > extensionsBytes.size) return null
            val extData = extensionsBytes.sliceArray(individualExtOffset until individualExtOffset + extLen)
            individualExtOffset += extLen
            certExtensions.add(TlsExtension(extType, extData))
        }
        if (individualExtOffset != extensionsBytes.size) return null // Malformed extensions

        certificateList.add(CertificateEntry(certData, certExtensions))
    }
    if (certListOffset != certificateListBytes.size) return null // Malformed certificate list

    return CertificateData(certificateRequestContext, certificateList)
}

data class CertificateVerifyData(
    val algorithm: UShort, // SignatureScheme
    val signature: ByteArray
) {
     override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CertificateVerifyData
        if (algorithm != other.algorithm) return false
        if (!signature.contentEquals(other.signature)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}

fun deserializeCertificateVerify(bytes: ByteArray): CertificateVerifyData? {
    val parsedHandshake = parseHandshakeMessage(bytes) ?: return null
    if (parsedHandshake.first != 0x0F.toByte()) return null // 0x0F is CertificateVerify
    // TODO: Implement full parsing (RFC 8446, Section 4.4.3)
    // Placeholder:
    // val algorithm = payload.readShort(0) ...
    // val signatureLen = payload.readShort(2) ...
    // Placeholder implementation - this needs full parsing logic.
    val payload = parsedHandshake.third
    var currentOffset = 0

    if (currentOffset + 2 > payload.size) return null // SignatureScheme
    val algorithm = payload.readShort(currentOffset)
    currentOffset += 2

    if (currentOffset + 2 > payload.size) return null // Signature length
    val signatureLength = payload.readShort(currentOffset).toInt()
    currentOffset += 2

    if (currentOffset + signatureLength > payload.size || signatureLength != payload.size - currentOffset) return null
    val signature = payload.sliceArray(currentOffset until currentOffset + signatureLength)

    return CertificateVerifyData(algorithm, signature)
}

// FinishedData is already defined. We can use it for ServerFinished.
// If ServerFinished and ClientFinished need different structures, then rename or add new.
// For now, FinishedData(verifyData: ByteArray) is sufficient.
typealias ServerFinishedData = FinishedData // RFC 8446, Section 4.4.4

fun deserializeServerFinished(bytes: ByteArray): ServerFinishedData? {
    val parsedHandshake = parseHandshakeMessage(bytes) ?: return null
    if (parsedHandshake.first != 0x14.toByte()) return null // 0x14 is Finished
    val payload = parsedHandshake.third
    // The payload *is* the verify_data for Finished message.
    // Its length is determined by the cipher suite's hash algorithm (e.g., 32 for SHA-256).
    // We might want to validate payload.size against expected hash size.
    return ServerFinishedData(payload)
}

typealias ClientFinishedData = FinishedData

fun deserializeClientFinished(bytes: ByteArray): ClientFinishedData? {
    // ClientFinished has the same structure as ServerFinished
    val parsedHandshake = parseHandshakeMessage(bytes) ?: return null
    if (parsedHandshake.first != 0x14.toByte()) { // 0x14 is Finished
        // Not a Finished message
        return null
    }
    val payload = parsedHandshake.third
    // Validate payload size against expected hash size if necessary
    return ClientFinishedData(payload)
}


fun serializeServerHello(shlo: ServerHelloData): ByteArray {
    var payload = byteArrayOf()
    // For TLS 1.3, the main version field is typically TLS 1.2 (0x0303),
    // and the actual negotiated version is in the "supported_versions" extension.
    payload += shlo.version.writeShort() // This is legacy_version
    payload += shlo.serverRandom // 32 bytes

    payload += shlo.sessionId.writeByteLengthPrefixed()
    payload += shlo.cipherSuite.writeShort()
    payload += shlo.compressionMethod.toByte() // Single byte

    var extensionsBytes = byteArrayOf()
    // Supported Version extension (mandatory for TLS 1.3 ServerHello)
    shlo.supportedVersion?.let {
        extensionsBytes += serializeSupportedVersionsExtension(listOf(it)) // List contains one version
    }

    // Key Share extension (mandatory if ClientHello had it and negotiation is happening)
    shlo.keyShareEntry?.let {
        // For ServerHello, KeyShare extension contains just one entry, and no outer length for that single entry.
        // The serializeKeyShareExtension handles the forClientHello=false case correctly.
        extensionsBytes += serializeKeyShareExtension(listOf(it), forClientHello = false)
    }
    // Other extensions could be added here (e.g., PreSharedKey if resuming)

    payload += extensionsBytes.size.toUShort().writeShort() // Overall extensions length
    payload += extensionsBytes

    // Handshake message wrapper
    var handshakeMessage = byteArrayOf()
    handshakeMessage += 0x02.toByte() // HandshakeType: ServerHello
    val lengthBytes = byteArrayOf(
        ((payload.size shr 16) and 0xFF).toByte(),
        ((payload.size shr 8) and 0xFF).toByte(),
        (payload.size and 0xFF).toByte()
    )
    handshakeMessage += lengthBytes
    handshakeMessage += payload
    return handshakeMessage
}

fun serializeEncryptedExtensions(ee: EncryptedExtensionsData): ByteArray {
    var extensionsPayloadBytes = byteArrayOf()
    for (extension in ee.extensions) {
        extensionsPayloadBytes += extension.type.writeShort()
        extensionsPayloadBytes += extension.data.size.toUShort().writeShort()
        extensionsPayloadBytes += extension.data
    }

    // The EncryptedExtensions message payload *is* the extensions block (length + data)
    val payload = extensionsPayloadBytes.size.toUShort().writeShort() + extensionsPayloadBytes

    var handshakeMessage = byteArrayOf()
    handshakeMessage += 0x08.toByte() // HandshakeType: EncryptedExtensions
    val lengthBytes = byteArrayOf(
        ((payload.size shr 16) and 0xFF).toByte(),
        ((payload.size shr 8) and 0xFF).toByte(),
        (payload.size and 0xFF).toByte()
    )
    handshakeMessage += lengthBytes
    handshakeMessage += payload
    return handshakeMessage
}


fun serializeFinished(finishedData: FinishedData): ByteArray {
    // HandshakeType (1 byte, 0x14 for Finished)
    // Length (3 bytes, length of the verify_data)
    // Verify Data (variable, typically 32 bytes for SHA-256 HMAC)
    var handshakeMessage = byteArrayOf()
    handshakeMessage += 0x14.toByte() // HandshakeType: Finished

    val payload = finishedData.verifyData
    // Length is a 24-bit uint
    val lengthBytes = byteArrayOf(
        ((payload.size shr 16) and 0xFF).toByte(),
        ((payload.size shr 8) and 0xFF).toByte(),
        (payload.size and 0xFF).toByte()
    )
    handshakeMessage += lengthBytes
    handshakeMessage += payload

    return handshakeMessage
}
