@file:OptIn(ExperimentalUnsignedTypes::class)
@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.net.tls


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import borg.trikeshed.crypto.*
import borg.trikeshed.net.quic.*
import kotlinx.coroutines.*
import kotlin.jvm.JvmInline

// === TLS 1.3 TAXONOMICAL TYPEALIASES ===

// Protocol types
typealias TLSVersion = UShort
typealias HandshakeType = Byte
typealias ContentType = Byte
typealias AlertLevel = Byte
typealias AlertDescription = Byte
typealias ExtensionType = UShort
typealias NamedGroup = UShort
typealias SignatureScheme = UShort
typealias PskKeyExchangeMode = Byte
typealias CertificateType = Byte

// Data types
typealias Random = Indexed<Byte>
typealias SessionID = Indexed<Byte>
typealias Cookie = Indexed<Byte>
typealias PSKIdentity = Indexed<Byte>
typealias Certificate = Indexed<Byte>
typealias CertificateVerify = Indexed<Byte>
typealias Finished = Indexed<Byte>

// Value classes for type safety
@JvmInline value class ProtocolVersion(val value: UShort)
@JvmInline value class HandshakeMessageType(val value: Byte)
@JvmInline value class RecordType(val value: Byte)
@JvmInline value class MaxFragmentLength(val value: Int)
@JvmInline value class TicketAge(val millis: Long)
@JvmInline value class TicketNonce(val value: Indexed<Byte>)

/**
 * TLS 1.3 Protocol Implementation (RFC 8446)
 */
object TLS13Protocol {
    
    // === PROTOCOL VERSION ===
    val VERSION = ProtocolVersion(0x0304u) // TLS 1.3
    
    // === CONTENT TYPES ===
    object ContentTypes {
        const val INVALID: ContentType = 0
        const val CHANGE_CIPHER_SPEC: ContentType = 20
        const val ALERT: ContentType = 21
        const val HANDSHAKE: ContentType = 22
        const val APPLICATION_DATA: ContentType = 23
        const val HEARTBEAT: ContentType = 24
    }
    
    // === HANDSHAKE TYPES ===
    object HandshakeTypes {
        const val CLIENT_HELLO: HandshakeType = 1
        const val SERVER_HELLO: HandshakeType = 2
        const val NEW_SESSION_TICKET: HandshakeType = 4
        const val END_OF_EARLY_DATA: HandshakeType = 5
        const val ENCRYPTED_EXTENSIONS: HandshakeType = 8
        const val CERTIFICATE: HandshakeType = 11
        const val CERTIFICATE_REQUEST: HandshakeType = 13
        const val CERTIFICATE_VERIFY: HandshakeType = 15
        const val FINISHED: HandshakeType = 20
        const val KEY_UPDATE: HandshakeType = 24
        const val MESSAGE_HASH: HandshakeType = 254.toByte()
    }
    
    // === EXTENSION TYPES ===
    object ExtensionTypes {
        const val SERVER_NAME: ExtensionType = 0u
        const val MAX_FRAGMENT_LENGTH: ExtensionType = 1u
        const val STATUS_REQUEST: ExtensionType = 5u
        const val SUPPORTED_GROUPS: ExtensionType = 10u
        const val SIGNATURE_ALGORITHMS: ExtensionType = 13u
        const val USE_SRTP: ExtensionType = 14u
        const val HEARTBEAT: ExtensionType = 15u
        const val APPLICATION_LAYER_PROTOCOL_NEGOTIATION: ExtensionType = 16u
        const val SIGNED_CERTIFICATE_TIMESTAMP: ExtensionType = 18u
        const val CLIENT_CERTIFICATE_TYPE: ExtensionType = 19u
        const val SERVER_CERTIFICATE_TYPE: ExtensionType = 20u
        const val PADDING: ExtensionType = 21u
        const val PRE_SHARED_KEY: ExtensionType = 41u
        const val EARLY_DATA: ExtensionType = 42u
        const val SUPPORTED_VERSIONS: ExtensionType = 43u
        const val COOKIE: ExtensionType = 44u
        const val PSK_KEY_EXCHANGE_MODES: ExtensionType = 45u
        const val CERTIFICATE_AUTHORITIES: ExtensionType = 47u
        const val OID_FILTERS: ExtensionType = 48u
        const val POST_HANDSHAKE_AUTH: ExtensionType = 49u
        const val SIGNATURE_ALGORITHMS_CERT: ExtensionType = 50u
        const val KEY_SHARE: ExtensionType = 51u
    }
    
    // === ALERT LEVELS ===
    object AlertLevels {
        const val WARNING: AlertLevel = 1.toByte()
        const val FATAL: AlertLevel = 2.toByte()
    }
    
    // === ALERT DESCRIPTIONS ===
    object AlertDescriptions {
        const val CLOSE_NOTIFY: AlertDescription = 0
        const val UNEXPECTED_MESSAGE: AlertDescription = 10
        const val BAD_RECORD_MAC: AlertDescription = 20
        const val RECORD_OVERFLOW: AlertDescription = 22
        const val HANDSHAKE_FAILURE: AlertDescription = 40
        const val BAD_CERTIFICATE: AlertDescription = 42
        const val UNSUPPORTED_CERTIFICATE: AlertDescription = 43
        const val CERTIFICATE_REVOKED: AlertDescription = 44
        const val CERTIFICATE_EXPIRED: AlertDescription = 45
        const val CERTIFICATE_UNKNOWN: AlertDescription = 46
        const val ILLEGAL_PARAMETER: AlertDescription = 47
        const val UNKNOWN_CA: AlertDescription = 48
        const val ACCESS_DENIED: AlertDescription = 49
        const val DECODE_ERROR: AlertDescription = 50
        const val DECRYPT_ERROR: AlertDescription = 51
        const val PROTOCOL_VERSION: AlertDescription = 70
        const val INSUFFICIENT_SECURITY: AlertDescription = 71
        const val INTERNAL_ERROR: AlertDescription = 80
        const val INAPPROPRIATE_FALLBACK: AlertDescription = 86
        const val USER_CANCELED: AlertDescription = 90
        const val MISSING_EXTENSION: AlertDescription = 109
        const val UNSUPPORTED_EXTENSION: AlertDescription = 110
        const val UNRECOGNIZED_NAME: AlertDescription = 112
        const val BAD_CERTIFICATE_STATUS_RESPONSE: AlertDescription = 113
        const val UNKNOWN_PSK_IDENTITY: AlertDescription = 115
        const val CERTIFICATE_REQUIRED: AlertDescription = 116
        const val NO_APPLICATION_PROTOCOL: AlertDescription = 120
    }
    
    // === NAMED GROUPS (ECDHE) ===
    object NamedGroups {
        const val SECP256R1: NamedGroup = 0x0017u
        const val SECP384R1: NamedGroup = 0x0018u
        const val SECP521R1: NamedGroup = 0x0019u
        const val X25519: NamedGroup = 0x001Du
        const val X448: NamedGroup = 0x001Eu
        const val FFDHE2048: NamedGroup = 0x0100u
        const val FFDHE3072: NamedGroup = 0x0101u
        const val FFDHE4096: NamedGroup = 0x0102u
        const val FFDHE6144: NamedGroup = 0x0103u
        const val FFDHE8192: NamedGroup = 0x0104u
    }
    
    // === SIGNATURE SCHEMES ===
    object SignatureSchemes {
        const val RSA_PKCS1_SHA256: SignatureScheme = 0x0401u
        const val RSA_PKCS1_SHA384: SignatureScheme = 0x0501u
        const val RSA_PKCS1_SHA512: SignatureScheme = 0x0601u
        const val ECDSA_SECP256R1_SHA256: SignatureScheme = 0x0403u
        const val ECDSA_SECP384R1_SHA384: SignatureScheme = 0x0503u
        const val ECDSA_SECP521R1_SHA512: SignatureScheme = 0x0603u
        const val RSA_PSS_RSAE_SHA256: SignatureScheme = 0x0804u
        const val RSA_PSS_RSAE_SHA384: SignatureScheme = 0x0805u
        const val RSA_PSS_RSAE_SHA512: SignatureScheme = 0x0806u
        const val ED25519: SignatureScheme = 0x0807u
        const val ED448: SignatureScheme = 0x0808u
        const val RSA_PSS_PSS_SHA256: SignatureScheme = 0x0809u
        const val RSA_PSS_PSS_SHA384: SignatureScheme = 0x080au
        const val RSA_PSS_PSS_SHA512: SignatureScheme = 0x080bu
    }
}

/**
 * TLS 1.3 Record Layer
 */
data class TLSRecord(
    val type: RecordType,
    val legacyVersion: ProtocolVersion = ProtocolVersion(0x0303u), // Always TLS 1.2 for compatibility
    val fragment: Indexed<Byte>
) {
    fun encode(): Indexed<Byte> {
        val encoded = mutableListOf<Byte>()
        
        // Content type
        encoded.add(type.value)
        
        // Legacy version (always 0x0303 for TLS 1.3)
        encoded.add((legacyVersion.value.toInt() shr 8).toByte())
        encoded.add(legacyVersion.value.toByte())
        
        // Length
        encoded.add((fragment.a shr 8).toByte())
        encoded.add(fragment.a.toByte())
        
        // Fragment
        for (i in 0 until fragment.a) {
            encoded.add(fragment[i])
        }
        
        return encoded.size j { i: Int -> encoded[i] }
    }
    
    companion object {
        fun decode(data: Indexed<Byte>, offset: Int = 0): Pair<TLSRecord?, Int> {
            if (offset + 5 > data.a) return null to offset
            
            val type = RecordType(data[offset])
            val version = ProtocolVersion(
                ((data[offset + 1].toInt() and 0xFF) shl 8 or 
                 (data[offset + 2].toInt() and 0xFF)).toUShort()
            )
            val length = (data[offset + 3].toInt() and 0xFF) shl 8 or 
                        (data[offset + 4].toInt() and 0xFF)
            
            if (offset + 5 + length > data.a) return null to offset
            
            val fragment = length j { i: Int -> data[offset + 5 + i] }
            
            return TLSRecord(type, version, fragment) to (offset + 5 + length)
        }
    }
}

/**
 * TLS 1.3 Handshake Message
 */
data class HandshakeMessage(
    val type: HandshakeMessageType,
    val body: Indexed<Byte>
) {
    fun encode(): Indexed<Byte> {
        val encoded = mutableListOf<Byte>()
        
        // Message type
        encoded.add(type.value)
        
        // Length (24 bits)
        encoded.add((body.a shr 16).toByte())
        encoded.add((body.a shr 8).toByte())
        encoded.add(body.a.toByte())
        
        // Body
        for (i in 0 until body.a) {
            encoded.add(body[i])
        }
        
        return encoded.size j { i: Int -> encoded[i] }
    }
    
    companion object {
        fun decode(data: Indexed<Byte>, offset: Int = 0): Pair<HandshakeMessage?, Int> {
            if (offset + 4 > data.a) return null to offset
            
            val type = HandshakeMessageType(data[offset])
            val length = ((data[offset + 1].toInt() and 0xFF) shl 16) or
                        ((data[offset + 2].toInt() and 0xFF) shl 8) or
                        (data[offset + 3].toInt() and 0xFF)
            
            if (offset + 4 + length > data.a) return null to offset
            
            val body = length j { i: Int -> data[offset + 4 + i] }
            
            return HandshakeMessage(type, body) to (offset + 4 + length)
        }
    }
}

/**
 * TLS 1.3 Extension
 */
data class Extension(
    val type: ExtensionType,
    val data: Indexed<Byte>
) {
    fun encode(): Indexed<Byte> {
        val encoded = mutableListOf<Byte>()
        
        // Extension type
        encoded.add((type.toInt() shr 8).toByte())
        encoded.add(type.toByte())
        
        // Length
        encoded.add((data.a shr 8).toByte())
        encoded.add(data.a.toByte())
        
        // Data
        for (i in 0 until data.a) {
            encoded.add(data[i])
        }
        
        return encoded.size j { i: Int -> encoded[i] }
    }
}

/**
 * ClientHello message
 */
data class ClientHello(
    val legacyVersion: ProtocolVersion = ProtocolVersion(0x0303u),
    val random: Random,
    val legacySessionId: SessionID = 32 j { 0.toByte() }, // Must be 32 bytes in TLS 1.3
    val cipherSuites: Indexed<CipherSuite>,
    val legacyCompressionMethods: Indexed<Byte> = 1 j { 0.toByte() }, // Must be 0x00
    val extensions: Indexed<Extension>
) {
    fun encode(): Indexed<Byte> {
        val body = mutableListOf<Byte>()
        
        // Legacy version
        body.add((legacyVersion.value.toInt() shr 8).toByte())
        body.add(legacyVersion.value.toByte())
        
        // Random
        for (i in 0 until 32) {
            body.add(if (i < random.a) random[i] else 0.toByte())
        }
        
        // Legacy session ID
        body.add(legacySessionId.a.toByte())
        for (i in 0 until legacySessionId.a) {
            body.add(legacySessionId[i])
        }
        
        // Cipher suites
        val cipherSuitesLength = cipherSuites.a * 2
        body.add((cipherSuitesLength shr 8).toByte())
        body.add(cipherSuitesLength.toByte())
        for (i in 0 until cipherSuites.a) {
            val suite = cipherSuites[i]
            body.add((suite.value shr 8).toByte())
            body.add(suite.value.toByte())
        }
        
        // Legacy compression methods
        body.add(legacyCompressionMethods.a.toByte())
        for (i in 0 until legacyCompressionMethods.a) {
            body.add(legacyCompressionMethods[i])
        }
        
        // Extensions
        val encodedExtensions = mutableListOf<Byte>()
        for (i in 0 until extensions.a) {
            val encoded = extensions[i].encode()
            for (j in 0 until encoded.a) {
                encodedExtensions.add(encoded[j])
            }
        }
        
        body.add((encodedExtensions.size shr 8).toByte())
        body.add(encodedExtensions.size.toByte())
        body.addAll(encodedExtensions)
        
        return body.size j { i: Int -> body[i] }
    }
}

/**
 * ServerHello message
 */
data class ServerHello(
    val legacyVersion: ProtocolVersion = ProtocolVersion(0x0303u),
    val random: Random,
    val legacySessionIdEcho: SessionID,
    val cipherSuite: CipherSuite,
    val legacyCompressionMethod: Byte = 0,
    val extensions: Indexed<Extension>
) {
    fun encode(): Indexed<Byte> {
        val body = mutableListOf<Byte>()
        
        // Legacy version
        body.add((legacyVersion.value.toInt() shr 8).toByte())
        body.add(legacyVersion.value.toByte())
        
        // Random
        for (i in 0 until 32) {
            body.add(if (i < random.a) random[i] else 0.toByte())
        }
        
        // Legacy session ID echo
        body.add(legacySessionIdEcho.a.toByte())
        for (i in 0 until legacySessionIdEcho.a) {
            body.add(legacySessionIdEcho[i])
        }
        
        // Cipher suite
        body.add((cipherSuite.value shr 8).toByte())
        body.add(cipherSuite.value.toByte())
        
        // Legacy compression method
        body.add(legacyCompressionMethod)
        
        // Extensions
        val encodedExtensions = mutableListOf<Byte>()
        for (i in 0 until extensions.a) {
            val encoded = extensions[i].encode()
            for (j in 0 until encoded.a) {
                encodedExtensions.add(encoded[j])
            }
        }
        
        body.add((encodedExtensions.size shr 8).toByte())
        body.add(encodedExtensions.size.toByte())
        body.addAll(encodedExtensions)
        
        return body.size j { i: Int -> body[i] }
    }
}

/**
 * TLS 1.3 Connection State
 */
class TLS13Connection(
    private val role: Role,
    private val transport: QuicConnection? = null,
    private val crypto: CommonCrypto = CryptoFactory.getSecureRandom()
) {
    enum class Role { CLIENT, SERVER }
    enum class State {
        START,
        WAIT_SH,      // Waiting for ServerHello
        WAIT_EE,      // Waiting for EncryptedExtensions
        WAIT_CERT_CR, // Waiting for Certificate/CertificateRequest
        WAIT_CV,      // Waiting for CertificateVerify
        WAIT_FINISHED,// Waiting for Finished
        CONNECTED,    // Handshake complete
        CLOSED
    }
    
    private var state = State.START
    private val transcriptHash = mutableListOf<Byte>()
    
    // Crypto state
    private var selectedCipherSuite: CipherSuite? = null
    private var tls13Crypto: TLS13Crypto? = null
    private var handshakeSecrets: HandshakeSecrets? = null
    private var applicationSecrets: ApplicationSecrets? = null
    
    // Key exchange
    private var keyExchange: CommonCrypto.KeyExchange? = null
    private var localKeyPair: Join<PublicKey, PrivateKey>? = null
    private var sharedSecret: SessionKey? = null
    
    // Extensions state
    private val supportedGroups = listOf(
        TLS13Protocol.NamedGroups.X25519,
        TLS13Protocol.NamedGroups.SECP256R1,
        TLS13Protocol.NamedGroups.SECP384R1
    )
    
    private val supportedSignatures = listOf(
        TLS13Protocol.SignatureSchemes.ECDSA_SECP256R1_SHA256,
        TLS13Protocol.SignatureSchemes.RSA_PSS_RSAE_SHA256,
        TLS13Protocol.SignatureSchemes.ED25519
    )
    
    private val supportedCipherSuites = listOf(
        TLS13CipherSuites.TLS_AES_128_GCM_SHA256,
        TLS13CipherSuites.TLS_AES_256_GCM_SHA384,
        TLS13CipherSuites.TLS_CHACHA20_POLY1305_SHA256
    )
    
    /**
     * Start TLS handshake
     */
    suspend fun startHandshake() {
        if (role == Role.CLIENT) {
            sendClientHello()
        } else {
            // Server waits for ClientHello
            state = State.WAIT_SH
        }
    }
    
    /**
     * Send ClientHello
     */
    private suspend fun sendClientHello() {
        // Generate random
        val random = crypto.randomBytes(32)
        
        // Generate key share
        val selectedGroup = supportedGroups.first()
        keyExchange = when (selectedGroup) {
            TLS13Protocol.NamedGroups.X25519 -> CryptoFactory.createKeyExchange(KeyExchangeAlgorithms.X25519)
            TLS13Protocol.NamedGroups.SECP256R1 -> CryptoFactory.createKeyExchange(KeyExchangeAlgorithms.SECP256R1)
            TLS13Protocol.NamedGroups.SECP384R1 -> CryptoFactory.createKeyExchange(KeyExchangeAlgorithms.SECP384R1)
            else -> throw Exception("Unsupported group")
        }
        
        localKeyPair = keyExchange!!.generateKeyPair()
        
        // Build extensions
        val extensions = mutableListOf<Extension>()
        
        // Supported Versions
        val supportedVersions = mutableListOf<Byte>()
        supportedVersions.add(2) // Length
        supportedVersions.add(0x03) // TLS 1.3 high byte
        supportedVersions.add(0x04) // TLS 1.3 low byte
        extensions.add(Extension(
            TLS13Protocol.ExtensionTypes.SUPPORTED_VERSIONS,
            supportedVersions.size j { i: Int -> supportedVersions[i] }
        ))
        
        // Supported Groups
        val supportedGroupsData = mutableListOf<Byte>()
        supportedGroupsData.add((supportedGroups.size * 2 shr 8).toByte())
        supportedGroupsData.add((supportedGroups.size * 2).toByte())
        for (group in supportedGroups) {
            supportedGroupsData.add((group.toInt() shr 8).toByte())
            supportedGroupsData.add(group.toByte())
        }
        extensions.add(Extension(
            TLS13Protocol.ExtensionTypes.SUPPORTED_GROUPS,
            supportedGroupsData.size j { i: Int -> supportedGroupsData[i] }
        ))
        
        // Key Share
        val keyShareData = mutableListOf<Byte>()
        val publicKey = localKeyPair!!.a
        
        // Client key share length
        val keyShareEntryLength = 2 + 2 + publicKey.a // group + key_exchange length + key_exchange
        keyShareData.add((keyShareEntryLength shr 8).toByte())
        keyShareData.add(keyShareEntryLength.toByte())
        
        // Key share entry
        keyShareData.add((selectedGroup.toInt() shr 8).toByte())
        keyShareData.add(selectedGroup.toByte())
        keyShareData.add((publicKey.a shr 8).toByte())
        keyShareData.add(publicKey.a.toByte())
        for (i in 0 until publicKey.a) {
            keyShareData.add(publicKey[i])
        }
        
        extensions.add(Extension(
            TLS13Protocol.ExtensionTypes.KEY_SHARE,
            keyShareData.size j { i: Int -> keyShareData[i] }
        ))
        
        // Signature Algorithms
        val sigAlgsData = mutableListOf<Byte>()
        sigAlgsData.add((supportedSignatures.size * 2 shr 8).toByte())
        sigAlgsData.add((supportedSignatures.size * 2).toByte())
        for (sigAlg in supportedSignatures) {
            sigAlgsData.add((sigAlg.toInt() shr 8).toByte())
            sigAlgsData.add(sigAlg.toByte())
        }
        extensions.add(Extension(
            TLS13Protocol.ExtensionTypes.SIGNATURE_ALGORITHMS,
            sigAlgsData.size j { i: Int -> sigAlgsData[i] }
        ))
        
        // Create ClientHello
        val clientHello = ClientHello(
            random = random,
            cipherSuites = supportedCipherSuites.size j { i: Int -> supportedCipherSuites[i] },
            extensions = extensions.size j { i: Int -> extensions[i] }
        )
        
        // Encode and send
        val encoded = clientHello.encode()
        val handshakeMsg = HandshakeMessage(
            HandshakeMessageType(TLS13Protocol.HandshakeTypes.CLIENT_HELLO),
            encoded
        )
        
        sendHandshakeMessage(handshakeMsg)
        
        // Update transcript hash
        updateTranscript(handshakeMsg.encode())
        
        state = State.WAIT_SH
    }
    
    /**
     * Process received handshake message
     */
    suspend fun processHandshakeMessage(message: HandshakeMessage) {
        // Update transcript hash
        updateTranscript(message.encode())
        
        when (message.type.value) {
            TLS13Protocol.HandshakeTypes.CLIENT_HELLO -> {
                if (role == Role.SERVER) {
                    processClientHello(message.body)
                }
            }
            TLS13Protocol.HandshakeTypes.SERVER_HELLO -> {
                if (role == Role.CLIENT && state == State.WAIT_SH) {
                    processServerHello(message.body)
                }
            }
            TLS13Protocol.HandshakeTypes.ENCRYPTED_EXTENSIONS -> {
                if (role == Role.CLIENT && state == State.WAIT_EE) {
                    processEncryptedExtensions(message.body)
                }
            }
            TLS13Protocol.HandshakeTypes.CERTIFICATE -> {
                processCertificate(message.body)
            }
            TLS13Protocol.HandshakeTypes.CERTIFICATE_VERIFY -> {
                processCertificateVerify(message.body)
            }
            TLS13Protocol.HandshakeTypes.FINISHED -> {
                processFinished(message.body)
            }
        }
    }
    
    /**
     * Process ServerHello
     */
    private suspend fun processServerHello(body: Indexed<Byte>) {
        // Parse ServerHello (simplified)
        var offset = 2 // Skip version
        
        // Random (32 bytes)
        val serverRandom = 32 j { i: Int -> body[offset + i] }
        offset += 32
        
        // Check for HelloRetryRequest
        val hrrRandom = 32 j { i: Int -> 
            when (i) {
                0 -> 0xCF.toByte()
                1 -> 0x21.toByte()
                2 -> 0xAD.toByte()
                3 -> 0x74.toByte()
                4 -> 0xE5.toByte()
                5 -> 0x9A.toByte()
                6 -> 0x61.toByte()
                7 -> 0x11.toByte()
                8 -> 0xBE.toByte()
                9 -> 0x1D.toByte()
                10 -> 0x8C.toByte()
                11 -> 0x02.toByte()
                12 -> 0x1E.toByte()
                13 -> 0x65.toByte()
                14 -> 0xB8.toByte()
                15 -> 0x91.toByte()
                16 -> 0xC2.toByte()
                17 -> 0xA2.toByte()
                18 -> 0x11.toByte()
                19 -> 0x16.toByte()
                20 -> 0x7A.toByte()
                21 -> 0xBB.toByte()
                22 -> 0x8C.toByte()
                23 -> 0x5E.toByte()
                24 -> 0x07.toByte()
                25 -> 0x9E.toByte()
                26 -> 0x09.toByte()
                27 -> 0xE2.toByte()
                28 -> 0xC8.toByte()
                29 -> 0xA8.toByte()
                30 -> 0x33.toByte()
                31 -> 0x9C.toByte()
                else -> 0.toByte()
            }
        }
        
        val isHelloRetryRequest = serverRandom.play.zip(hrrRandom.play).all { (a, b) -> a == b }
        
        if (isHelloRetryRequest) {
            // Handle HelloRetryRequest
            println("Received HelloRetryRequest")
            // Would need to send updated ClientHello
            return
        }
        
        // Skip legacy session ID
        val sessionIdLength = body[offset].toInt() and 0xFF
        offset += 1 + sessionIdLength
        
        // Cipher suite
        selectedCipherSuite = CipherSuite(
            ((body[offset].toInt() and 0xFF) shl 8) or 
            (body[offset + 1].toInt() and 0xFF)
        )
        offset += 2
        
        // Skip compression method
        offset += 1
        
        // Extensions length
        val extensionsLength = ((body[offset].toInt() and 0xFF) shl 8) or 
                             (body[offset + 1].toInt() and 0xFF)
        offset += 2
        
        // Parse extensions
        val extensionsEnd = offset + extensionsLength
        while (offset < extensionsEnd) {
            val extType = ExtensionType(
                (((body[offset].toInt() and 0xFF) shl 8) or 
                (body[offset + 1].toInt() and 0xFF)).toShort()
            )
            offset += 2
            
            val extLength = ((body[offset].toInt() and 0xFF) shl 8) or 
                          (body[offset + 1].toInt() and 0xFF)
            offset += 2
            
            when (extType) {
                TLS13Protocol.ExtensionTypes.KEY_SHARE -> {
                    // Parse server's key share
                    val group = NamedGroup(
                        (((body[offset].toInt() and 0xFF) shl 8) or 
                        (body[offset + 1].toInt() and 0xFF)).toShort()
                    )
                    offset += 2
                    
                    val keyExchangeLength = ((body[offset].toInt() and 0xFF) shl 8) or 
                                          (body[offset + 1].toInt() and 0xFF)
                    offset += 2
                    
                    val serverPublicKey = keyExchangeLength j { i: Int -> body[offset + i] }
                    
                    // Compute shared secret
                    sharedSecret = keyExchange!!.computeSharedSecret(localKeyPair!!.b, serverPublicKey)
                    
                    // Initialize crypto
                    tls13Crypto = TLS13Crypto(selectedCipherSuite!!)
                    
                    // Derive handshake secrets
                    val clientHelloData = transcriptHash.size j { i: Int -> transcriptHash[i] }
                    handshakeSecrets = tls13Crypto!!.deriveHandshakeSecrets(
                        sharedSecret!!,
                        clientHelloData,
                        body
                    )
                    
                    offset += keyExchangeLength
                }
                else -> offset += extLength
            }
        }
        
        state = State.WAIT_EE
    }
    
    /**
     * Process EncryptedExtensions
     */
    private fun processEncryptedExtensions(body: Indexed<Byte>) {
        // Parse extensions (simplified)
        state = State.WAIT_CERT_CR
    }
    
    /**
     * Process Certificate
     */
    private fun processCertificate(body: Indexed<Byte>) {
        // Parse certificate chain (simplified)
        state = State.WAIT_CV
    }
    
    /**
     * Process CertificateVerify
     */
    private fun processCertificateVerify(body: Indexed<Byte>) {
        // Verify signature (simplified)
        state = State.WAIT_FINISHED
    }
    
    /**
     * Process Finished
     */
    private suspend fun processFinished(body: Indexed<Byte>) {
        // Verify finished message
        
        if (role == Role.CLIENT) {
            // Send client Finished
            sendFinished()
            
            // Derive application secrets
            val hasher = CryptoFactory.createHasher(HashAlgorithms.SHA256)
            val handshakeHash = hasher.hash(transcriptHash.size j { i: Int -> transcriptHash[i] })
            
            applicationSecrets = tls13Crypto!!.deriveApplicationSecrets(
                handshakeSecrets!!.handshakeSecret,
                handshakeHash
            )
            
            state = State.CONNECTED
        } else {
            state = State.CONNECTED
        }
    }
    
    /**
     * Send Finished message
     */
    private suspend fun sendFinished() {
        // Calculate verify data
        val finishedKey = tls13Crypto!!.deriveKeyAndIV(
            if (role == Role.CLIENT) handshakeSecrets!!.clientSecret 
            else handshakeSecrets!!.serverSecret
        ).a
        
        val hasher = CryptoFactory.createHasher(HashAlgorithms.SHA256)
        val transcriptHashValue = hasher.hash(transcriptHash.size j { i: Int -> transcriptHash[i] })
        val verifyData = hasher.hmac(finishedKey, transcriptHashValue)
        
        val finishedMsg = HandshakeMessage(
            HandshakeMessageType(TLS13Protocol.HandshakeTypes.FINISHED),
            verifyData
        )
        
        sendHandshakeMessage(finishedMsg)
        updateTranscript(finishedMsg.encode())
    }
    
    /**
     * Send handshake message
     */
    private suspend fun sendHandshakeMessage(message: HandshakeMessage) {
        val encoded = message.encode()
        
        // Wrap in TLS record
        val record = TLSRecord(
            type = RecordType(TLS13Protocol.ContentTypes.HANDSHAKE),
            fragment = encoded
        )
        
        // Send via transport
        transport?.let { conn ->
            val stream = conn.createStream() ?: return
            val recordData = record.encode()
            stream.writeBytes(recordData)
        }
    }
    
    /**
     * Update transcript hash
     */
    private fun updateTranscript(data: Indexed<Byte>) {
        for (i in 0 until data.a) {
            transcriptHash.add(data[i])
        }
    }
    
    /**
     * Process ClientHello (server side)
     */
    private suspend fun processClientHello(body: Indexed<Byte>) {
        // Parse ClientHello and send ServerHello
        // This is a simplified implementation
        sendServerHello()
    }
    
    /**
     * Send ServerHello (server side)
     */
    private suspend fun sendServerHello() {
        // Implementation for server side
        // Generate ServerHello, derive keys, etc.
    }
}

/**
 * TLS 1.3 0-RTT Data
 */
data class EarlyDataIndication(
    val maxEarlyDataSize: Int = 0xFFFFFFFF.toInt()
) {
    fun encode(): Indexed<Byte> {
        return if (maxEarlyDataSize > 0) {
            4 j { i: Int ->
                when (i) {
                    0 -> (maxEarlyDataSize shr 24).toByte()
                    1 -> (maxEarlyDataSize shr 16).toByte()
                    2 -> (maxEarlyDataSize shr 8).toByte()
                    3 -> maxEarlyDataSize.toByte()
                    else -> 0.toByte()
                }
            }
        } else {
            0 j { 0.toByte() }
        }
    }
}

/**
 * NewSessionTicket message for session resumption
 */
data class NewSessionTicket(
    val ticketLifetime: Int, // seconds
    val ticketAgeAdd: Int,
    val ticketNonce: TicketNonce,
    val ticket: Indexed<Byte>,
    val extensions: Indexed<Extension> = 0 j { Extension(0u, 0 j { 0.toByte() }) }
) {
    fun encode(): Indexed<Byte> {
        val body = mutableListOf<Byte>()
        
        // Ticket lifetime
        body.add((ticketLifetime shr 24).toByte())
        body.add((ticketLifetime shr 16).toByte())
        body.add((ticketLifetime shr 8).toByte())
        body.add(ticketLifetime.toByte())
        
        // Ticket age add
        body.add((ticketAgeAdd shr 24).toByte())
        body.add((ticketAgeAdd shr 16).toByte())
        body.add((ticketAgeAdd shr 8).toByte())
        body.add(ticketAgeAdd.toByte())
        
        // Ticket nonce
        body.add(ticketNonce.value.a.toByte())
        for (i in 0 until ticketNonce.value.a) {
            body.add(ticketNonce.value[i])
        }
        
        // Ticket
        body.add((ticket.a shr 8).toByte())
        body.add(ticket.a.toByte())
        for (i in 0 until ticket.a) {
            body.add(ticket[i])
        }
        
        // Extensions
        val encodedExtensions = mutableListOf<Byte>()
        for (i in 0 until extensions.a) {
            val encoded = extensions[i].encode()
            for (j in 0 until encoded.a) {
                encodedExtensions.add(encoded[j])
            }
        }
        
        body.add((encodedExtensions.size shr 8).toByte())
        body.add(encodedExtensions.size.toByte())
        body.addAll(encodedExtensions)
        
        return body.size j { i: Int -> body[i] }
    }
}