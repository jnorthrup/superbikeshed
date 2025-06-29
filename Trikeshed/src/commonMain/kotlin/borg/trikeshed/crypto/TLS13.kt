package borg.trikeshed.crypto

import borg.trikeshed.lib.*

/**
 * TLS 1.3 Implementation (RFC 8446)
 */
class TLS13Connection(
    private val isServer: Boolean,
    private val supportedCipherSuites: List<CipherSuite> = listOf(
        TLS13CipherSuites.TLS_AES_128_GCM_SHA256,
        TLS13CipherSuites.TLS_CHACHA20_POLY1305_SHA256,
        TLS13CipherSuites.TLS_AES_256_GCM_SHA384
    ),
    private val supportedKeyExchangeGroups: List<KeyExchangeAlgorithm> = listOf(
        KeyExchangeAlgorithms.X25519,
        KeyExchangeAlgorithms.SECP256R1
    ),
    private val supportedSignatureAlgorithms: List<SignatureAlgorithm> = listOf(
        SignatureAlgorithms.ED25519,
        SignatureAlgorithms.ECDSA_SECP256R1_SHA256
    )
    // TODO: Add PSK support, certificate handling, etc.
) {
    private var state: TLSState = TLSState.START
    private val transcriptHash = TranscriptHash(CryptoFactory.createHasher(HashAlgorithms.SHA256)) // Adjust hash based on cipher suite
    private var cryptoTLS: TLS13Crypto? = null // Will be initialized after cipher suite negotiation

    // Handshake parameters
    private var clientRandom: Indexed<Byte>? = null
    private var serverRandom: Indexed<Byte>? = null
    private var chosenCipherSuite: CipherSuite? = null
    private var clientKeyShare: KeyShareEntry? = null
    private var serverKeyShare: KeyShareEntry? = null
    private var sharedKey: SessionKey? = null

    // Traffic keys
    private var clientHandshakeTrafficSecret: SessionKey? = null
    private var serverHandshakeTrafficSecret: SessionKey? = null
    private var clientApplicationTrafficSecret: SessionKey? = null
    private var serverApplicationTrafficSecret: SessionKey? = null

    // TODO: Add sequence numbers for record protocol encryption/decryption

    private enum class TLSState {
        START,
        WAIT_CLIENT_HELLO, // Server
        WAIT_SERVER_HELLO, // Client
        WAIT_SERVER_ENCRYPTED_EXTENSIONS, // Client
        WAIT_SERVER_CERTIFICATE, // Client (if server sends one)
        WAIT_SERVER_CERTIFICATE_VERIFY, // Client (if server sends one)
        WAIT_SERVER_FINISHED, // Client
        WAIT_CLIENT_CERTIFICATE, // Server (if client sends one)
        WAIT_CLIENT_CERTIFICATE_VERIFY, // Server (if client sends one)
        WAIT_CLIENT_FINISHED, // Server
        CONNECTED,
        FAILED
    }

    // --- Public API ---

    /**
     * Initiates the TLS handshake.
     * For clients, this generates and returns a ClientHello message.
     * For servers, this prepares to receive a ClientHello.
     */
    fun startHandshake(): Indexed<Byte>? {
        if (!isServer) {
            state = TLSState.WAIT_SERVER_HELLO
            return createClientHello()
        } else {
            state = TLSState.WAIT_CLIENT_HELLO
            return null // Server waits for ClientHello
        }
    }

    /**
     * Processes an incoming TLS record (or a sequence of handshake messages).
     * Returns a list of TLS records to be sent in response.
     */
    fun processIncomingRecord(record: TLSRecord): List<TLSRecord> {
        // TODO: Decrypt record if keys are established
        // TODO: Handle multiple messages within one record, or fragmented messages
        val responses = mutableListOf<TLSRecord>()

        when (record.type) {
            ContentType.HANDSHAKE -> {
                // Assuming record.fragment contains one complete handshake message for now
                val handshakeMessage = HandshakeMessage.fromBytes(record.fragment)
                transcriptHash.update(record.fragment) // Add raw handshake message to transcript

                when (handshakeMessage.type) {
                    HandshakeType.CLIENT_HELLO -> if (isServer && state == TLSState.WAIT_CLIENT_HELLO) {
                        handleClientHello(handshakeMessage.body)
                        responses.add(createHandshakeRecord(createServerHello()))
                        responses.add(createHandshakeRecord(createEncryptedExtensions()))
                        // TODO: Add Certificate, CertificateVerify if needed
                        responses.add(createHandshakeRecord(createServerFinished()))
                        state = TLSState.WAIT_CLIENT_FINISHED
                    }
                    HandshakeType.SERVER_HELLO -> if (!isServer && state == TLSState.WAIT_SERVER_HELLO) {
                        handleServerHello(handshakeMessage.body)
                        state = TLSState.WAIT_SERVER_ENCRYPTED_EXTENSIONS
                    }
                    HandshakeType.ENCRYPTED_EXTENSIONS -> if (!isServer && state == TLSState.WAIT_SERVER_ENCRYPTED_EXTENSIONS) {
                        handleEncryptedExtensions(handshakeMessage.body)
                        // TODO: Next state depends on whether server sent Certificate
                        state = TLSState.WAIT_SERVER_FINISHED // Simplified
                    }
                    HandshakeType.FINISHED -> {
                        if (!isServer && state == TLSState.WAIT_SERVER_FINISHED) {
                            handleServerFinished(handshakeMessage.body)
                            responses.add(createHandshakeRecord(createClientFinished()))
                            state = TLSState.CONNECTED
                            println("TLS Handshake successful (Client)")
                        } else if (isServer && state == TLSState.WAIT_CLIENT_FINISHED) {
                            handleClientFinished(handshakeMessage.body)
                            state = TLSState.CONNECTED
                            println("TLS Handshake successful (Server)")
                        }
                    }
                    // TODO: Handle Certificate, CertificateVerify, NewSessionTicket etc.
                    else -> {
                        println("Unhandled handshake message type: ${handshakeMessage.type}")
                        failHandshake("Unhandled handshake type")
                    }
                }
            }
            ContentType.APPLICATION_DATA -> {
                if (state == TLSState.CONNECTED) {
                    // TODO: Decrypt application data
                    // val plaintext = decryptApplicationData(record.fragment)
                    // Pass plaintext to application layer
                } else {
                     failHandshake("Application data received before handshake completion")
                }
            }
            ContentType.ALERT -> {
                // TODO: Process alert message
                val alert = AlertMessage.fromBytes(record.fragment)
                println("Alert received: Level=${alert.level}, Description=${alert.description}")
                if (alert.level == AlertLevel.FATAL) state = TLSState.FAILED
            }
            else -> {
                 failHandshake("Unhandled record content type: ${record.type}")
            }
        }
        return responses
    }

    fun encryptApplicationData(plaintext: Indexed<Byte>): TLSRecord? {
        if (state != TLSState.CONNECTED || clientApplicationTrafficSecret == null || serverApplicationTrafficSecret == null) {
            return null // Or throw error
        }
        // TODO: Implement actual encryption using derived application traffic keys
        // val key = if (isServer) serverApplicationTrafficSecret else clientApplicationTrafficSecret
        // val ciphertext = ... encrypt with key, nonce, aad ...
        return TLSRecord(ContentType.APPLICATION_DATA, ProtocolVersion.TLS13, plaintext /* placeholder */)
    }


    // --- Handshake Message Creation ---
    private fun createClientHello(): Indexed<Byte> {
        clientRandom = CryptoFactory.getSecureRandom().randomBytes(32)

        // Choose a key share (e.g., X25519)
        val kex = CryptoFactory.createKeyExchange(supportedKeyExchangeGroups.first())
        val clientKeyPair = kex.generateKeyPair()
        clientKeyShare = KeyShareEntry(supportedKeyExchangeGroups.first(), clientKeyPair.first)

        val extensions = mutableListOf<Extension>()
        extensions.add(KeyShareExtension(listOf(clientKeyShare!!))) // ClientHello key share
        extensions.add(SupportedVersionsExtension(listOf(ProtocolVersion.TLS13)))
        extensions.add(SignatureAlgorithmsExtension(supportedSignatureAlgorithms))
        // TODO: Add other extensions (supported_groups, etc.)

        val clientHello = ClientHelloBody(
            legacyVersion = ProtocolVersion.TLS12, // Standard practice
            random = clientRandom!!,
            legacySessionId = 0 j {}, // Empty
            cipherSuites = supportedCipherSuites,
            legacyCompressionMethods = listOf(0.toByte()), // Null compression
            extensions = extensions
        )
        val bodyBytes = clientHello.toBytes()
        return HandshakeMessage(HandshakeType.CLIENT_HELLO, bodyBytes).toBytes()
    }

    private fun createServerHello(): Indexed<Byte> {
        serverRandom = CryptoFactory.getSecureRandom().randomBytes(32)
        // TODO: Choose cipher suite based on ClientHello
        chosenCipherSuite = supportedCipherSuites.first() // Simplified
        cryptoTLS = TLS13Crypto(chosenCipherSuite!!)

        // TODO: Select key share based on ClientHello
        // For now, assume client sent a supported one (e.g. X25519)
        val kex = CryptoFactory.createKeyExchange(clientKeyShare!!.group)
        val serverKeyPair = kex.generateKeyPair()
        serverKeyShare = KeyShareEntry(clientKeyShare!!.group, serverKeyPair.first)
        sharedKey = kex.computeSharedSecret(serverKeyPair.second, clientKeyShare!!.keyExchange)

        // Derive handshake secrets
        val handshakeSecrets = cryptoTLS!!.deriveHandshakeSecrets(sharedKey!!, clientHelloFullBytes!!, serverHelloFullBytes_placeholder!!)
        clientHandshakeTrafficSecret = handshakeSecrets.clientSecret
        serverHandshakeTrafficSecret = handshakeSecrets.serverSecret

        val extensions = mutableListOf<Extension>()
        extensions.add(KeyShareExtension(listOf(serverKeyShare!!))) // ServerHello key share
        extensions.add(SupportedVersionsExtension(listOf(ProtocolVersion.TLS13)))

        val serverHello = ServerHelloBody(
            legacyVersion = ProtocolVersion.TLS12,
            random = serverRandom!!,
            legacySessionIdEcho = 0 j {}, // Echo if resuming, else new
            cipherSuite = chosenCipherSuite!!,
            legacyCompressionMethod = 0.toByte(),
            extensions = extensions
        )
        val bodyBytes = serverHello.toBytes()
        // serverHelloFullBytes_placeholder should be set to the full ServerHello message bytes here for transcript
        return HandshakeMessage(HandshakeType.SERVER_HELLO, bodyBytes).toBytes()
    }

    // Placeholders for full message bytes needed for transcript hash before secrets are available
    private var clientHelloFullBytes: Indexed<Byte>? = null
    private var serverHelloFullBytes_placeholder: Indexed<Byte>? = null


    private fun createEncryptedExtensions(): Indexed<Byte> {
        // TODO: Implement actual encrypted extensions
        val extensions = emptyList<Extension>() // Example
        val eeBody = EncryptedExtensionsBody(extensions).toBytes()
        // TODO: Encrypt eeBody using server_handshake_traffic_secret
        return HandshakeMessage(HandshakeType.ENCRYPTED_EXTENSIONS, eeBody /* encrypted */).toBytes()
    }

    private fun createServerFinished(): Indexed<Byte> {
        val verifyData = calculateVerifyData(serverHandshakeTrafficSecret!!, transcriptHash.getCurrentHash())
        // TODO: Encrypt Finished message
        return HandshakeMessage(HandshakeType.FINISHED, verifyData /* encrypted */).toBytes()
    }

    private fun createClientFinished(): Indexed<Byte> {
        val verifyData = calculateVerifyData(clientHandshakeTrafficSecret!!, transcriptHash.getCurrentHash())
        // TODO: Encrypt Finished message
        // After this, derive application traffic secrets
        val appSecrets = cryptoTLS!!.deriveApplicationSecrets(serverHandshakeTrafficSecret!! /* This should be master secret derived from handshake */, transcriptHash.getCurrentHash())
        clientApplicationTrafficSecret = appSecrets.clientSecret
        serverApplicationTrafficSecret = appSecrets.serverSecret
        return HandshakeMessage(HandshakeType.FINISHED, verifyData /* encrypted */).toBytes()
    }

    private fun calculateVerifyData(baseKey: SessionKey, handshakeContextHash: Hash): Indexed<Byte> {
        val finishedKey = cryptoTLS!!.kdf.expandLabel(baseKey, "finished", 0 j {}, KeyLength(handshakeContextHash.a * 8))
        return CryptoFactory.createHasher(cryptoTLS!!.hasher.algorithm).hmac(finishedKey, handshakeContextHash)
    }


    // --- Handshake Message Handling ---
    private fun handleClientHello(body: Indexed<Byte>) {
        val ch = ClientHelloBody.fromBytes(body)
        clientRandom = ch.random
        // TODO: Process extensions, choose cipher suite, key share, etc.
        // For now, assume first supported ones are chosen.
        chosenCipherSuite = ch.cipherSuites.firstOrNull { it in supportedCipherSuites }
        if (chosenCipherSuite == null) {
            failHandshake("No common cipher suite")
            return
        }
        cryptoTLS = TLS13Crypto(chosenCipherSuite!!)

        val clientKS = ch.extensions.filterIsInstance<KeyShareExtension>().firstOrNull()?.shares?.firstOrNull()
        clientKeyShare = clientKS?.takeIf { it.group in supportedKeyExchangeGroups }
        if (clientKeyShare == null) {
            failHandshake("No common key share group or client didn't send one")
            return
        }
        clientHelloFullBytes = HandshakeMessage(HandshakeType.CLIENT_HELLO, body).toBytes() // Store for transcript
    }

    private fun handleServerHello(body: Indexed<Byte>) {
        val sh = ServerHelloBody.fromBytes(body)
        serverRandom = sh.random
        chosenCipherSuite = sh.cipherSuite
        if (chosenCipherSuite !in supportedCipherSuites) {
            failHandshake("Server chose unsupported cipher suite")
            return
        }
        cryptoTLS = TLS13Crypto(chosenCipherSuite!!)

        val serverKS = sh.extensions.filterIsInstance<KeyShareExtension>().firstOrNull()?.shares?.firstOrNull()
        serverKeyShare = serverKS
        if (serverKeyShare == null || serverKeyShare!!.group != clientKeyShare!!.group) {
            failHandshake("Server key share issue")
            return
        }

        // Compute shared secret
        val kex = CryptoFactory.createKeyExchange(clientKeyShare!!.group)
        sharedKey = kex.computeSharedSecret(clientKeyShare!!.keyExchange /*This should be client's private key component*/, serverKeyShare!!.keyExchange)
        // ^ This is wrong. Client uses its private key with server's public key.
        // sharedKey = kex.computeSharedSecret(clientKeyPair.second, serverKeyShare.keyExchange) - clientKeyPair needs to be stored from createClientHello

        // Derive handshake secrets
        // val handshakeSecrets = cryptoTLS!!.deriveHandshakeSecrets(sharedKey!!, clientHelloFullBytes!!, HandshakeMessage(HandshakeType.SERVER_HELLO, body).toBytes())
        // clientHandshakeTrafficSecret = handshakeSecrets.clientSecret
        // serverHandshakeTrafficSecret = handshakeSecrets.serverSecret
        println("Processed ServerHello. Shared key derived (simplified).")
    }

    private fun handleEncryptedExtensions(body: Indexed<Byte>) {
        // TODO: Decrypt body using server_handshake_traffic_secret
        // val decryptedBody = ...
        // val ee = EncryptedExtensionsBody.fromBytes(decryptedBody)
        println("Processed EncryptedExtensions (decryption placeholder).")
    }

    private fun handleServerFinished(body: Indexed<Byte>) {
        // TODO: Decrypt body using server_handshake_traffic_secret
        // val expectedVerifyData = calculateVerifyData(serverHandshakeTrafficSecret!!, transcriptHash.getCurrentHash())
        // if (!body.contentEquals(expectedVerifyData)) failHandshake("Server Finished verification failed")

        // After server Finished, client derives application traffic secrets
        // val appSecrets = cryptoTLS!!.deriveApplicationSecrets(...)
        // clientApplicationTrafficSecret = appSecrets.clientSecret
        // serverApplicationTrafficSecret = appSecrets.serverSecret
        println("Processed Server Finished (verification placeholder).")
    }

    private fun handleClientFinished(body: Indexed<Byte>) {
        // TODO: Decrypt body using client_handshake_traffic_secret
        // val expectedVerifyData = calculateVerifyData(clientHandshakeTrafficSecret!!, transcriptHash.getCurrentHash())
        // if (!body.contentEquals(expectedVerifyData)) failHandshake("Client Finished verification failed")
        println("Processed Client Finished (verification placeholder).")
    }


    private fun failHandshake(reason: String) {
        println("TLS Handshake Failed: $reason")
        state = TLSState.FAILED
        // TODO: Send appropriate alert message
    }

    private fun createHandshakeRecord(handshakeMessageBytes: Indexed<Byte>): TLSRecord {
        // TODO: Encrypt handshakeMessageBytes if handshake keys are established
        // (e.g. for Finished messages, or EE if server encrypts it)
        return TLSRecord(ContentType.HANDSHAKE, ProtocolVersion.TLS13, handshakeMessageBytes)
    }

    // --- Data structures for TLS messages (simplified) ---
    // These should be more robustly defined in separate files ideally.

    enum class ContentType(val value: Byte) { HANDSHAKE(22), ALERT(21), APPLICATION_DATA(23), CHANGE_CIPHER_SPEC(20) }
    enum class ProtocolVersion(val value: UShort) { TLS10(0x0301u), TLS11(0x0302u), TLS12(0x0303u), TLS13(0x0304u) }

    data class TLSRecord(val type: ContentType, val version: ProtocolVersion, val fragment: Indexed<Byte>) {
        fun toBytes(): Indexed<Byte> { /* TODO: Implement serialization */ return fragment }
        companion object {
            fun fromBytes(bytes: Indexed<Byte>): TLSRecord { /* TODO: Implement parsing */
                 return TLSRecord(ContentType.HANDSHAKE, ProtocolVersion.TLS13, bytes) // Placeholder
            }
        }
    }

    enum class HandshakeType(val value: Byte) {
        CLIENT_HELLO(1), SERVER_HELLO(2), NEW_SESSION_TICKET(4), ENCRYPTED_EXTENSIONS(8),
        CERTIFICATE(11), CERTIFICATE_REQUEST(13), CERTIFICATE_VERIFY(15), FINISHED(20), KEY_UPDATE(24)
    }
    data class HandshakeMessage(val type: HandshakeType, val body: Indexed<Byte>) {
        fun toBytes(): Indexed<Byte> { /* type (1) + length (3) + body */ return body } // Simplified
        companion object {
            fun fromBytes(bytes: Indexed<Byte>): HandshakeMessage { /* parse */ return HandshakeMessage(HandshakeType.CLIENT_HELLO, bytes) } // Placeholder
        }
    }

    // Extensions (simplified)
    interface Extension { fun toBytes(): Indexed<Byte> }
    data class KeyShareEntry(val group: KeyExchangeAlgorithm, val keyExchange: Indexed<Byte>)
    data class KeyShareExtension(val shares: List<KeyShareEntry>) : Extension { override fun toBytes(): Indexed<Byte> = 0 j {} }
    data class SupportedVersionsExtension(val versions: List<ProtocolVersion>) : Extension { override fun toBytes(): Indexed<Byte> = 0 j {} }
    data class SignatureAlgorithmsExtension(val algorithms: List<SignatureAlgorithm>) : Extension { override fun toBytes(): Indexed<Byte> = 0 j {} }

    data class ClientHelloBody(
        val legacyVersion: ProtocolVersion, val random: Indexed<Byte>, val legacySessionId: Indexed<Byte>,
        val cipherSuites: List<CipherSuite>, val legacyCompressionMethods: List<Byte>, val extensions: List<Extension>
    ) {
        fun toBytes(): Indexed<Byte> = 0 j {} // Placeholder
        companion object { fun fromBytes(bytes: Indexed<Byte>): ClientHelloBody = ClientHelloBody(ProtocolVersion.TLS12, 0 j {}, 0 j {}, emptyList(), emptyList(), emptyList()) } // Placeholder
    }
    data class ServerHelloBody(
        val legacyVersion: ProtocolVersion, val random: Indexed<Byte>, val legacySessionIdEcho: Indexed<Byte>,
        val cipherSuite: CipherSuite, val legacyCompressionMethod: Byte, val extensions: List<Extension>
    ) {
        fun toBytes(): Indexed<Byte> = 0 j {} // Placeholder
        companion object { fun fromBytes(bytes: Indexed<Byte>): ServerHelloBody = ServerHelloBody(ProtocolVersion.TLS12, 0 j {}, 0 j {}, TLS13CipherSuites.TLS_AES_128_GCM_SHA256, 0, emptyList()) } // Placeholder
    }
    data class EncryptedExtensionsBody(val extensions: List<Extension>) { fun toBytes(): Indexed<Byte> = 0 j {} }

    enum class AlertLevel(val value: Byte) { WARNING(1), FATAL(2) }
    enum class AlertDescription(val value: Byte) { /* ... many ... */ CLOSE_NOTIFY(0), UNEXPECTED_MESSAGE(10), BAD_RECORD_MAC(20), HANDSHAKE_FAILURE(40) }
    data class AlertMessage(val level: AlertLevel, val description: AlertDescription) {
        fun toBytes(): Indexed<Byte> = byteArrayOf(level.value, description.value).toIndexed()
        companion object { fun fromBytes(bytes: Indexed<Byte>): AlertMessage = AlertMessage(AlertLevel.WARNING, AlertDescription.CLOSE_NOTIFY) } // Placeholder
    }
     private fun ByteArray.toIndexed(): Indexed<Byte> = this.size j { i -> this[i] }
}
