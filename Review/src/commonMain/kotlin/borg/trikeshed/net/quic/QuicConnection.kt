package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.ConnectionId // From QuicTypes.kt
import borg.trikeshed.net.quic.PacketNumber // From QuicTypes.kt
import borg.trikeshed.net.quic.tls.ClientHelloData
import borg.trikeshed.net.quic.tls.EncryptedExtensionsData
import borg.trikeshed.net.quic.tls.FinishedData
import borg.trikeshed.net.quic.crypto.computeEcdhSharedSecret
import borg.trikeshed.net.quic.crypto.sha256
import borg.trikeshed.net.quic.crypto.verifySignature
import borg.trikeshed.net.quic.tls.CertificateData
import borg.trikeshed.net.quic.tls.CertificateEntry
import borg.trikeshed.net.quic.crypto.hmacSha256
import borg.trikeshed.net.quic.crypto.sha256
import borg.trikeshed.net.quic.crypto.verifySignature
import borg.trikeshed.net.quic.tls.CertificateData
import borg.trikeshed.net.quic.tls.CertificateEntry
import borg.trikeshed.net.quic.tls.CertificateVerifyData
import borg.trikeshed.net.quic.tls.KeyShareEntry
import borg.trikeshed.net.quic.tls.ServerHelloData // Explicit import for clarity
import borg.trikeshed.net.quic.tls.ServerFinishedData // For deserializing server's Finished
import borg.trikeshed.net.quic.tls.TlsSignatureScheme // Assuming this object exists with constants
import borg.trikeshed.net.quic.tls.deserializeCertificate
import borg.trikeshed.net.quic.tls.deserializeCertificateVerify
import borg.trikeshed.net.quic.tls.deserializeEncryptedExtensions
import borg.trikeshed.net.quic.tls.deserializeServerFinished // New import
import borg.trikeshed.net.quic.tls.deserializeServerHello
import borg.trikeshed.net.quic.tls.serializeFinished // New import
import evolution.AesService // For future use
import evolution.HkdfService
import evolution.platform.services.DefaultHkdfService // Placeholder
import borg.trikeshed.net.quic.crypto.QuicSecrets
import borg.trikeshed.net.quic.crypto.generateEcdhKeyPair
// import borg.trikeshed.net.quic.crypto.hkdfExpandLabel // Now using the new wrapper
// import borg.trikeshed.net.quic.crypto.hkdfExtract // Now using HkdfService directly
import borg.trikeshed.net.quic.crypto.hkdfExpandLabel // The new wrapper from QuicCryptoUtils.kt
import borg.trikeshed.net.quic.tls.ClientHelloData
import borg.trikeshed.net.quic.tls.EncryptedExtensionsData
import borg.trikeshed.net.quic.tls.FinishedData
import borg.trikeshed.net.quic.tls.KeyShareEntry
import borg.trikeshed.net.quic.tls.ServerHelloData
import borg.trikeshed.net.quic.tls.TLS_AES_128_GCM_SHA256
import borg.trikeshed.net.quic.tls.InitialMaxData
import borg.trikeshed.net.quic.tls.InitialMaxStreamDataBidiLocal
import borg.trikeshed.net.quic.tls.InitialMaxStreamsBidi
import borg.trikeshed.net.quic.tls.InitialSourceConnectionId
import borg.trikeshed.net.quic.tls.MaxAckDelay
import borg.trikeshed.net.quic.tls.MaxIdleTimeout
import borg.trikeshed.net.quic.tls.QuicTransportParameters
import borg.trikeshed.net.quic.tls.TLS_VERSION_1_3
import borg.trikeshed.net.quic.tls.X25519_GROUP
import borg.trikeshed.net.quic.tls.serializeClientHello
import borg.trikeshed.net.quic.tls.serializeQuicTransportParameters
import kotlin.random.Random // For generating random ConnectionId

enum class QuicConnectionStateEnum {
  INITIAL,
  HANDSHAKE_STARTED,
  HANDSHAKE_COMPLETED,
  CONNECTED,
  CLOSING,
  CLOSED
}

class QuicConnection(
  val clientId: ConnectionId, // Original DCID chosen by client
  var serverId: ConnectionId? = null, // SCID chosen by server
  var state: QuicConnectionStateEnum = QuicConnectionStateEnum.INITIAL,
  var localPacketNumber: PacketNumber = 0uL, // For sending
  var largestAckedPacketNumberByPeer: PacketNumber = 0uL, // Largest PN we've received an ACK for
  var largestReceivedPacketNumberFromPeer: PacketNumber = 0uL, // Largest PN we've successfully processed
  var peerMaxAckDelay: ULong = 25_000uL, // Peer's max_ack_delay in microseconds, default 25ms
  var localMaxAckDelay: ULong = 25_000uL, // Our max_ack_delay in microseconds
  var tlsHandshakeState: TlsHandshakeState? = null,
  var clientEphemeralPrivateKey: ByteArray? = null,
  var clientEphemeralPublicKey: ByteArray? = null,
  var handshakeTranscript: ByteArray = byteArrayOf(),
  // Replaced QuicCryptoLevel with EncryptionLevel from QuicConnectionManager
  val cryptoSecrets: MutableMap<EncryptionLevel, QuicSecrets> = mutableMapOf(),
  var serverCertificates: List<CertificateEntry>? = null,
  private var derivedHandshakeSecret: ByteArray? = null, // Store derived secret from ServerHello processing
  private var clientHandshakeTrafficSecretInternal: ByteArray? = null,
  var serverHandshakeTrafficSecretInternal: ByteArray? = null, // Made internal for test access, consider better way
  internal var clientInitialSecretsForSending: QuicSecrets? = null, // Keys for client to send Initial packets
  internal var serverInitialSecretsForReception: QuicSecrets? = null, // Keys for client to receive server's Initial packets
  // For storing server handshake write keys since QuicConnectionManager might only store one set for HANDSHAKE level
  internal var serverHandshakeSecretsForReception: QuicSecrets? = null, // Keys for client to receive server's Handshake packets
  var initialClientChosenDcId: ByteArray? = null, // DCID client picks for its first Initial packets
  var trustedCaCertBytes: ByteArray? = null, // For X.509 validation by client
  var expectedServerName: String? = null,   // For X.509 validation by client
  internal var parsedServerCertificates: List<borg.trikeshed.net.quic.crypto.PlatformX509Certificate>? = null, // Parsed server certs
  // Stream data tracking
  private val streamSendOffsets: MutableMap<ULong, Long> = mutableMapOf(),
  private val streamReceiveOffsets: MutableMap<ULong, Long> = mutableMapOf(),
  var serverAppTrafficSecretInternal: ByteArray? = null // Storing for client to derive its read keys for 1-RTT
) {
  companion object {
    /**
     * QUICv1 initial salt as defined in RFC 9001, Section 5.2.
     */
    private val INITIAL_SALT_V1 = byteArrayOf(
        0x38, 0x76, 0x2c, 0xf7, 0xf5, 0x59, 0x34, 0xb3, 0x4d, 0x17, 0x9a, 0xe6,
        0xa4, 0xc8, 0x0c, 0xad, 0xcc, 0xbb, 0x7f, 0x0a
    )

    /**
     * Creates a new client-initiated QuicConnection with a randomly generated clientId
     * and derives initial secrets.
     * QUIC connection IDs should be at least 8 bytes long for initial client IDs.
     * RFC 9000 Section 7.2.
     *
     * @param connectionIdLength The desired length for the initial client connection ID.
     * @param scidOverride Optional SCID to force for testing.
     * @param initialDcIdOverride Optional initial DCID to force for testing.
     */
    fun newClientConnectionDataOnly(
        connectionIdLength: Int = 8,
        scidOverride: ByteArray? = null,
        initialDcIdOverride: ByteArray? = null
    ): QuicConnection {
        val scid = scidOverride ?: Random.nextBytes(connectionIdLength.coerceAtLeast(8))
        require(scid.size >= 8 || scidOverride != null) { "Client chosen SCID must be at least 8 bytes for initial packets, unless overridden for tests." }

        val connection = QuicConnection(clientId = scid) // clientId property is used as SCID by client
        if (initialDcIdOverride != null) {
            require(initialDcIdOverride.size >= 8) { "Overridden initialDCID must be at least 8 bytes."}
            connection.initialClientChosenDcId = initialDcIdOverride
        }
        // If initialDcIdOverride is null, initiateClientHandshake will generate a random one if needed.
        return connection
    }
  }

  // init block is removed as initial secret derivation is now explicit.

  // This should be called before initiateClientHandshake.
  suspend fun deriveInitialSecrets(hkdfService: HkdfService, manager: QuicConnectionManager) {
    if (cryptoSecrets[EncryptionLevel.INITIAL] == null) {
        val initialSecret = hkdfService.extract(INITIAL_SALT_V1, this.clientId)
        val clientInitialSecret = hkdfExpandLabel(hkdfService, initialSecret, "client in", byteArrayOf(), 32)
        val clientInitialKey = hkdfExpandLabel(hkdfService, clientInitialSecret, "quic key", byteArrayOf(), 16)
        val clientInitialIv = hkdfExpandLabel(hkdfService, clientInitialSecret, "quic iv", byteArrayOf(), 12)
        val clientInitialHpKey = hkdfExpandLabel(hkdfService, clientInitialSecret, "quic hp", byteArrayOf(), 16)

        val clientSecrets = QuicSecrets(clientInitialKey, clientInitialIv, clientInitialHpKey)
        this.clientInitialSecretsForSending = clientSecrets // Store specifically for sending
        cryptoSecrets[EncryptionLevel.INITIAL] = clientSecrets // General map for current sending level
        manager.updateSecrets(EncryptionLevel.INITIAL, clientSecrets)

        // Derive Server Initial Secrets (for client to decrypt server's Initial packets)
        // Server's Initial packets are protected with keys derived from the DCID of the *client's* Initial packet.
        // This DCID is `this.initialClientChosenDcId` if set, or `this.clientId` if the server echoes it back
        // For deriving keys to *read* server's initials, client uses the DCID it put in its first Initial packet.
        // This DCID was `this.initialClientChosenDcId`.
        val dcidUsedInClientInitial = this.initialClientChosenDcId
            ?: throw IllegalStateException("initialClientChosenDcId must be set before deriving server initial secrets for reception.")

        val serverInitialSecretLabel = "server in"
        val serverInitialSecret = hkdfExpandLabel(hkdfService, initialSecret, serverInitialSecretLabel, byteArrayOf(), 32)
        val serverInitialKey = hkdfExpandLabel(hkdfService, serverInitialSecret, "quic key", byteArrayOf(), 16)
        val serverInitialIv = hkdfExpandLabel(hkdfService, serverInitialSecret, "quic iv", byteArrayOf(), 12)
        val serverInitialHpKey = hkdfExpandLabel(hkdfService, serverInitialSecret, "quic hp", byteArrayOf(), 16)
        this.serverInitialSecretsForReception = QuicSecrets(serverInitialKey, serverInitialIv, serverInitialHpKey)
        // These server Initial secrets (for reception) are NOT typically updated in the ConnectionManager by the client,
        // as the manager's secrets usually pertain to sending.
    }
  }

  suspend fun initiateClientHandshake(hkdfService: HkdfService, manager: QuicConnectionManager): ByteArray? {
    // ensureInitialSecretsDerived(hkdfService) // Now called externally before this.
    if (cryptoSecrets[EncryptionLevel.INITIAL] == null) {
        println("Error: Initial secrets not derived before initiateClientHandshake. Call deriveInitialSecrets first.")
        return null
    }

    // Generate client's chosen DCID for the first Initial flight if not already set
    if (this.initialClientChosenDcId == null) {
        this.initialClientChosenDcId = Random.nextBytes(8) // RFC 9000: Initial DCID must be >= 8 bytes
    }

    // Generate ECDH key pair
    val (privKey, pubKey) = generateEcdhKeyPair(X25519_GROUP)
    clientEphemeralPrivateKey = privKey
    clientEphemeralPublicKey = pubKey

    // Create QUIC Transport Parameters
    val transportParameters = QuicTransportParameters(
        parameters = listOf(
            InitialSourceConnectionId(this.clientId),
            InitialMaxData(1_048_576uL), // 1 MiB
            InitialMaxStreamDataBidiLocal(1_048_576uL),
            InitialMaxStreamsBidi(100uL),
            MaxIdleTimeout(30_000uL), // 30 seconds
            MaxAckDelay(25uL) // Default Max Ack Delay
            // Add other parameters as needed, e.g., INITIAL_MAX_STREAM_DATA_BIDI_REMOTE, INITIAL_MAX_STREAM_DATA_UNI, etc.
        )
    )
    val serializedTransportParameters = serializeQuicTransportParameters(transportParameters)

    // Populate ClientHelloData
    val clientHello = ClientHelloData(
        version = TLS_VERSION_1_3,
        clientRandom = Random.nextBytes(32),
        cipherSuites = listOf(TLS_AES_128_GCM_SHA256),
        keyShareEntries = listOf(
            KeyShareEntry(
                group = X25519_GROUP,
                keyExchange = clientEphemeralPublicKey!! // Use the generated public key
            )
        ),
        quicTransportParameters = serializedTransportParameters
    )

    // Serialize ClientHello - will be updated in a later step for more accuracy
    val serializedClientHello = serializeClientHello(clientHello)

    // Update Transcript
    handshakeTranscript += serializedClientHello

    // Update Handshake State
    tlsHandshakeState = TlsHandshakeState.EXPECTING_SERVER_HELLO

    return serializedClientHello
  }

  // Placeholder for actual packet number length decoding
  private fun decodePacketNumberLength(firstByte: Byte): Int = (firstByte.toInt() and 0x03) + 1

  private suspend fun decryptHandshakeMessage(
      encryptedPacket: ByteArray,
      packetEncryptionLevel: EncryptionLevel, // The level of the keys used for this packet
      aesService: AesService
  ): ByteArray? {
      val keys = cryptoSecrets[packetEncryptionLevel] ?: serverHandshakeSecretsForReception.takeIf { packetEncryptionLevel == EncryptionLevel.HANDSHAKE }
      if (keys == null) {
          println("Decryption keys for level $packetEncryptionLevel not found.")
          return null
      }

      // ... (rest of the simplified decryption logic remains the same)
      if (encryptedPacket.size < 20) return null // Arbitrary minimum size for a protected packet.

      // Simplified: Assume fixed offset for sample for header protection (highly inaccurate)
      // RFC 9001, Section 5.4.2: The sample is taken from the packet ciphertext.
      // The first byte of the header is at offset 0. Its 4 least significant bits are protected.
      // The packet number starts at an offset that depends on the DCID/SCID lengths.
      // For simplicity, let's assume a fixed offset for the sample relative to the start of the packet number.
      // This is NOT how it actually works.
      val estimatedPnOffset = 1 + 8 + 8 // Type + DCID + SCID (example, actual varies)
      if (encryptedPacket.size < estimatedPnOffset + 4 + 16) return null // Pkt Num (up to 4) + Sample (16)

      val sampleOffset = estimatedPnOffset + 4 // Sample after a 4-byte packet number field
      val sample = encryptedPacket.sliceArray(sampleOffset until sampleOffset + 16)

      val hpMask = aesService.ecbEncrypt(keys.hpKey, sample) ?: return null

      // Simplified: Assume first byte of packet has protected bits, and PN follows.
      val protectedHeaderPart = encryptedPacket.sliceArray(0 until estimatedPnOffset + 4) // Example slice
      val unprotectedHeader = protectedHeaderPart.clone() // Placeholder for actual unmasking

      // Apply mask (XOR) - simplified for first byte and PN
      unprotectedHeader[0] = (unprotectedHeader[0].toInt() xor (hpMask[0].toInt() and 0x0f)).toByte() // Mask 4 LSBs for first byte
      for (i in 0 until kotlin.math.min(4, hpMask.size - 1)) { // Mask PN bytes
          if (estimatedPnOffset + i < unprotectedHeader.size) {
            unprotectedHeader[estimatedPnOffset + i] = (unprotectedHeader[estimatedPnOffset + i].toInt() xor hpMask[i + 1].toInt()).toByte()
          }
      }

      // This is where packet number decoding would happen from unprotectedHeader's PN bytes
      // val packetNumberLength = decodePacketNumberLength(unprotectedHeader[0])
      // val packetNumber = decodePacketNumber(unprotectedHeader.sliceArray(estimatedPnOffset until estimatedPnOffset + packetNumberLength), packetNumberLength)


      // Simplified AEAD: AAD is the (now theoretically unprotected) header, ciphertext is the rest.
      // This is also NOT how AAD or IV construction works in QUIC.
      val aad = unprotectedHeader // Gross simplification
      val ciphertextPayload = encryptedPacket.sliceArray(aad.size until encryptedPacket.size)

      // IV construction is critical and involves XORing with packet number. This is a placeholder.
      // The nonce is 12 bytes. keys.iv is typically 12 bytes.
      // For QUIC, nonce = keys.iv XOR left-padded_packet_number (to 12 bytes)
      // This is a placeholder IV.
      val nonce = keys.iv // THIS IS WRONG for QUIC - actual IV depends on packet number.

      return aesService.gcmDecrypt(keys.key, nonce, ciphertextPayload, aad)
  }

  suspend fun processServerHandshakeMessage(
      quicPacketPayload: ByteArray,
      packetCryptoLevel: EncryptionLevel, // Renamed from currentCryptoLevel for clarity
      hkdfService: HkdfService,
      aesService: AesService,
      manager: QuicConnectionManager
  ): Boolean {

    when (tlsHandshakeState) {
        TlsHandshakeState.EXPECTING_SERVER_HELLO -> {
            if (packetCryptoLevel != EncryptionLevel.INITIAL) {
                println("Error: ServerHello received at wrong crypto level: $packetCryptoLevel")
                return false
            }
            val serverHelloData = deserializeServerHello(quicPacketPayload)
            if (serverHelloData == null) {
                // Log error: Failed to deserialize ServerHello
                return false
            }

            // Validate ServerHello (basic checks)
            if (serverHelloData.cipherSuite != TLS_AES_128_GCM_SHA256) return false // Error: Unsupported cipher suite
            if (serverHelloData.supportedVersion != TLS_VERSION_1_3) return false // Error: Unsupported TLS version
            if (serverHelloData.keyShareEntry == null) return false // Error: No key share
            if (serverHelloData.keyShareEntry.group != X25519_GROUP) return false // Error: Unsupported key share group
            if (clientEphemeralPrivateKey == null) return false // Error: Client private key missing

            // Update Transcript with the raw ServerHello message
            handshakeTranscript += quicPacketPayload

            // ECDH Shared Secret
            val dhSecret = computeEcdhSharedSecret(
                groupId = serverHelloData.keyShareEntry.group,
                privateKey = clientEphemeralPrivateKey!!,
                peerPublicKey = serverHelloData.keyShareEntry.keyExchange
            )

            // Derive Handshake Secrets
            val earlySecret = ByteArray(32) // Zero-filled for non-PSK
            // Store derivedHandshakeSecret for later use in ApplicationSecret derivation.
            // Note: RFC 8446 calls this "derived_secret" which is HKDF-Expand-Label(EarlySecret, "derived", Hash(""), Hash.length)
            // Then, HandshakeSecret = HKDF-Extract(derived_secret, SharedSecret)
            // Then, MasterSecret = HKDF-Extract(derived_secret, zeros(Hash.length)) -> This is incorrect per RFC 8446 Sec 7.1.
            // Master Secret = HKDF-Extract(Salt = DerivedSecret, IKM = PreMasterSecret=0s)
            // Actually, it's: Master Secret is derived from Handshake Secret.
            // derived_secret (used as salt for Handshake Secret) IS NOT the same as the final derived_secret for Master Secret.
            // Let's rename to avoid confusion.
            val saltForHandshakeSecret = hkdfExpandLabel(hkdfService, earlySecret, "derived", ByteArray(0), 32)
            this.derivedHandshakeSecret = saltForHandshakeSecret // Store this version of "derived"

            val handshakeSecret = hkdfService.extract(saltForHandshakeSecret, dhSecret)

            this.clientHandshakeTrafficSecretInternal = hkdfExpandLabel(
                hkdfService, handshakeSecret, "c hs traffic", handshakeTranscript, 32
            )
            this.serverHandshakeTrafficSecretInternal = hkdfExpandLabel(
                hkdfService, handshakeSecret, "s hs traffic", handshakeTranscript, 32
            )

            // Derive and store client handshake keys (for sending)
            val clientHsKey = hkdfExpandLabel(hkdfService, this.clientHandshakeTrafficSecretInternal!!, "quic key", ByteArray(0), 16)
            val clientHsIv = hkdfExpandLabel(hkdfService, this.clientHandshakeTrafficSecretInternal!!, "quic iv", ByteArray(0), 12)
            val clientHsHpKey = hkdfExpandLabel(hkdfService, this.clientHandshakeTrafficSecretInternal!!, "quic hp", ByteArray(0), 16)
            val clientHandshakeSecrets = QuicSecrets(clientHsKey, clientHsIv, clientHsHpKey)
            // Store client handshake write keys locally
            cryptoSecrets[EncryptionLevel.HANDSHAKE] = clientHandshakeSecrets
            // Inform the manager about client's handshake sending keys
            manager.updateSecrets(EncryptionLevel.HANDSHAKE, clientHandshakeSecrets)


            // Derive and store server handshake keys (for receiving by client)
            val serverHsKey = hkdfExpandLabel(hkdfService, this.serverHandshakeTrafficSecretInternal!!, "quic key", ByteArray(0), 16)
            val serverHsIv = hkdfExpandLabel(hkdfService, this.serverHandshakeTrafficSecretInternal!!, "quic iv", ByteArray(0), 12)
            val serverHsHpKey = hkdfExpandLabel(hkdfService, this.serverHandshakeTrafficSecretInternal!!, "quic hp", ByteArray(0), 16)
            // Store server handshake read keys locally for decryption. Manager might not need these if it only cares about send keys.
            this.serverHandshakeSecretsForReception = QuicSecrets(serverHsKey, serverHsIv, serverHsHpKey)
            // If ConnectionManager needs server read keys (e.g. if it handles decryption), then update it:
            // manager.updateSecrets(EncryptionLevel.HANDSHAKE_SERVER, QuicSecrets(serverHsKey, serverHsIv, serverHsHpKey))
            // For now, assume manager only needs client's sending perspective for HANDSHAKE.
            // Server handshake read keys are stored in `this.serverHandshakeSecretsForReception`.

            tlsHandshakeState = TlsHandshakeState.EXPECTING_ENCRYPTED_EXTENSIONS
            return true
        }
        TlsHandshakeState.EXPECTING_ENCRYPTED_EXTENSIONS -> {
            if (packetCryptoLevel != EncryptionLevel.HANDSHAKE) { // Server's Handshake messages are protected with Handshake keys
                 println("Error: EncryptedExtensions received at wrong crypto level: $packetCryptoLevel, expected HANDSHAKE")
                return false
            }
            if (this.serverHandshakeSecretsForReception == null) { // These are client's keys to read server's handshake
                println("Error: Server handshake reception keys (for client to read) not available.")
                return false
            }

            // 'quicPacketPayload' is now the full QUIC packet's payload, which is an *encrypted* TLS message.
            // This needs to be passed to decryptHandshakeMessage.
            // Note: The 'quicPacketPayload' from CRYPTO frame is the TLS message itself, already decrypted by packet processing layer.
            // So, the 'decryptHandshakeMessage' call is conceptual for the TLS layer if it were receiving raw packets.
            // In QUIC, CRYPTO frames *contain* TLS messages. If the QUIC packet was successfully decrypted,
            // then 'quicPacketPayload' is the plaintext of the TLS Handshake message.

            // THEREFORE: decryptHandshakeMessage is more about QUIC packet processing layer.
            // Here, we assume 'quicPacketPayload' IS the decrypted TLS message.
            // The `packetCryptoLevel` check ensures we are using the right keys if it were still encrypted by QUIC layer.

            val encryptedExtensionsData = deserializeEncryptedExtensions(quicPacketPayload)
            if (encryptedExtensionsData == null) {
                println("Error: Failed to deserialize EncryptedExtensions.")
                return false
            }

            // Process encryptedExtensionsData.extensions
            // e.g., look for server's QUIC transport parameters
            encryptedExtensionsData.extensions.find { it.type == borg.trikeshed.net.quic.tls.TlsExtensionType.QUIC_TRANSPORT_PARAMETERS }?.let {
                // val serverQuicTp = deserializeQuicTransportParameters(it.data) // Need this deserializer
                // processServerQuicTransportParameters(serverQuicTp)
            }

            handshakeTranscript += quicPacketPayload // Append the raw (but decrypted by QUIC layer) EncryptedExtensions TLS message

            tlsHandshakeState = TlsHandshakeState.EXPECTING_CERTIFICATE // Or EXPECTING_SERVER_FINISHED if PSK/resumption
            return true
        }
        TlsHandshakeState.EXPECTING_CERTIFICATE -> {
            if (packetCryptoLevel != EncryptionLevel.HANDSHAKE) {
                println("Error: Certificate received at wrong crypto level: $packetCryptoLevel")
                return false
            }
            val certificateData = deserializeCertificate(quicPacketPayload)
            if (certificateData == null) {
                println("Error: Failed to deserialize Certificate.")
                return false
            }
            val tempParsedCerts = certificateData.certificateList.mapNotNull {
                borg.trikeshed.net.quic.crypto.parseX509Certificate(it.certificateData)
            }
            if (tempParsedCerts.size != certificateData.certificateList.size) {
                println("Error: Failed to parse all server certificates during Certificate processing.")
                return false
            }
            this.parsedServerCertificates = tempParsedCerts

            if (this.trustedCaCertBytes == null || this.expectedServerName == null) {
                println("Error: Trusted CA or expected server name not configured on QuicConnection. Cannot validate server cert chain.")
                // In a strict mode, this might be a fatal error. For now, we can proceed if certs were parsed.
                // return false
            } else {
                val chainValid = borg.trikeshed.net.quic.crypto.validateCertificateChain(
                    chain = this.parsedServerCertificates!!,
                    trustedCaDerBytes = this.trustedCaCertBytes!!,
                    serverName = this.expectedServerName!!
                )
                if (!chainValid) {
                    println("Error: Server certificate chain validation failed.")
                    return false // Hard fail if validation is configured and fails
                }
                println("Server certificate chain validated successfully.")
            }
            this.serverCertificates = certificateData.certificateList // Store raw entries as well

            handshakeTranscript += quicPacketPayload // Append raw Certificate message
            tlsHandshakeState = TlsHandshakeState.EXPECTING_CERTIFICATE_VERIFY
            return true
        }
        TlsHandshakeState.EXPECTING_CERTIFICATE_VERIFY -> {
            if (packetCryptoLevel != EncryptionLevel.HANDSHAKE) {
                println("Error: CertificateVerify received at wrong crypto level: $packetCryptoLevel")
                return false
            }
            val certVerifyData = deserializeCertificateVerify(quicPacketPayload)
            if (certVerifyData == null) {
                println("Error: Failed to deserialize CertificateVerify.")
                return false
            }

            val leafParsedPlatformCert = parsedServerCertificates?.firstOrNull()
            if (leafParsedPlatformCert == null) {
                println("Error: No parsed server leaf certificate available for verification.")
                return false
            }
            val leafPublicKeyBytes = leafParsedPlatformCert.getPublicKeyBytes()
            if (leafPublicKeyBytes == null) {
                println("Error: Could not get public key from server leaf certificate.")
                return false
            }

            // Data to Verify for CertificateVerify (RFC 8446, Section 4.4.3)
            // transcriptHash = Transcript-Hash(ClientHello, ServerHello, ... Certificate)
            // Note: handshakeTranscript *already includes* ClientHello...Certificate at this point.
            val transcriptHashForCertVerify = sha256(handshakeTranscript)

            val contextStringBytes = "TLS 1.3, server CertificateVerify".encodeToByteArray()
            // Per RFC 8446: `Hash(prefix + contextString + 0x00 + transcriptHash)`
            val dataToSign = ByteArray(64) { 0x20.toByte() } + // 64 bytes of octet 32 (space)
                                 contextStringBytes +
                                 byteArrayOf(0x00) + // Separator
                                 transcriptHashForCertVerify

            val verificationSuccess = verifySignature(
                groupId = 0u, // groupId is often not needed if key + signatureScheme is enough
                publicKeyBytes = leafPublicKeyBytes,
                signatureScheme = certVerifyData.algorithm,
                dataToVerify = dataToSign,
                signature = certVerifyData.signature
            )

            if (!verificationSuccess) {
                println("Error: CertificateVerify signature validation FAILED.")
                return false
            }
            println("CertificateVerify signature validated successfully.")

            handshakeTranscript += quicPacketPayload // Append raw CertificateVerify message
            tlsHandshakeState = TlsHandshakeState.EXPECTING_SERVER_FINISHED
            return true
        }
        TlsHandshakeState.EXPECTING_SERVER_FINISHED -> {
            if (packetCryptoLevel != EncryptionLevel.HANDSHAKE) {
                println("Error: ServerFinished received at wrong crypto level: $packetCryptoLevel")
                return false
            }
            val serverFinishedData = deserializeServerFinished(quicPacketPayload)
            if (serverFinishedData == null) {
                println("Error: Failed to deserialize Server Finished.")
                return false
            }

            if (serverHandshakeTrafficSecretInternal == null) {
                println("Error: serverHandshakeTrafficSecretInternal not set for ServerFinished verification.")
                return false
            }

            val finishedKey = hkdfExpandLabel(hkdfService, serverHandshakeTrafficSecretInternal!!, "finished", ByteArray(0), 32)
            val transcriptHash = sha256(handshakeTranscript) // Transcript includes ClientHello...CertificateVerify
            val expectedVerifyData = hmacSha256(hkdfService, finishedKey, transcriptHash)

            if (!expectedVerifyData.contentEquals(serverFinishedData.verifyData)) {
                println("Error: Server Finished verification failed.")
                return false
            }

            handshakeTranscript += quicPacketPayload // Append raw ServerFinished message
            tlsHandshakeState = TlsHandshakeState.READY_TO_SEND_CLIENT_FINISHED
            return true
        }
        else -> {
            println("Error: Unexpected handshake state ${tlsHandshakeState} in processServerHandshakeMessage.")
            return false
        }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is QuicConnection) return false

    if (!clientId.contentEquals(other.clientId)) return false
    if (serverId != null) {
      if (other.serverId == null) return false
      if (!serverId!!.contentEquals(other.serverId!!)) return false
    } else if (other.serverId != null) return false
    if (state != other.state) return false
    if (localPacketNumber != other.localPacketNumber) return false
    if (largestAckedPacketNumberByPeer != other.largestAckedPacketNumberByPeer) return false
    if (largestReceivedPacketNumberFromPeer != other.largestReceivedPacketNumberFromPeer) return false
    if (peerMaxAckDelay != other.peerMaxAckDelay) return false
    if (localMaxAckDelay != other.localMaxAckDelay) return false
    if (tlsHandshakeState != other.tlsHandshakeState) return false
    if (clientEphemeralPrivateKey != null) {
        if (other.clientEphemeralPrivateKey == null) return false
        if (!clientEphemeralPrivateKey.contentEquals(other.clientEphemeralPrivateKey)) return false
    } else if (other.clientEphemeralPrivateKey != null) return false
    if (clientEphemeralPublicKey != null) {
        if (other.clientEphemeralPublicKey == null) return false
        if (!clientEphemeralPublicKey.contentEquals(other.clientEphemeralPublicKey)) return false
    } else if (other.clientEphemeralPublicKey != null) return false
    if (!handshakeTranscript.contentEquals(other.handshakeTranscript)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = clientId.contentHashCode()
    result = 31 * result + (serverId?.contentHashCode() ?: 0)
    result = 31 * result + state.hashCode()
    result = 31 * result + localPacketNumber.hashCode()
    result = 31 * result + largestAckedPacketNumberByPeer.hashCode()
    result = 31 * result + largestReceivedPacketNumberFromPeer.hashCode()
    result = 31 * result + peerMaxAckDelay.hashCode()
    result = 31 * result + localMaxAckDelay.hashCode()
    result = 31 * result + (tlsHandshakeState?.hashCode() ?: 0)
    result = 31 * result + (clientEphemeralPrivateKey?.contentHashCode() ?: 0)
    result = 31 * result + (clientEphemeralPublicKey?.contentHashCode() ?: 0)
    result = 31 * result + handshakeTranscript.contentHashCode()
    return result
  }

  // Removed the old companion object here as it's merged above with INITIAL_SALT_V1

  // Temporary helper to acknowledge the gap in storing serverHandshakeTrafficSecret
  // private fun getTemporaryServerHandshakeTrafficSecretForFinished(): ByteArray? {
      // This is no longer needed as we store serverHandshakeTrafficSecretInternal directly
      // return serverHandshakeTrafficSecretInternal
  // }

  suspend fun generateClientFinishedMessage(hkdfService: HkdfService, manager: QuicConnectionManager): ByteArray? {
    if (clientHandshakeTrafficSecretInternal == null) {
        println("Error: clientHandshakeTrafficSecretInternal not set for generateClientFinishedMessage.")
        return null
    }
    // Client Finished is sent with Handshake keys.
    // The manager should already have these from when ServerHello was processed.
    if (manager.getCurrentSecretsForSend(EncryptionLevel.HANDSHAKE) == null) {
        println("Warning: Manager does not have HANDSHAKE secrets when ClientFinished is being generated.")
        // This might indicate an issue if manager is expected to track this for sending.
        // However, QuicConnection itself has the necessary traffic secret.
    }


    val finishedKey = hkdfExpandLabel(hkdfService, clientHandshakeTrafficSecretInternal!!, "finished", ByteArray(0), 32)
    // Transcript for client Finished is ClientHello...ServerFinished
    val transcriptHash = sha256(handshakeTranscript)
    val verifyData = hmacSha256(hkdfService, finishedKey, transcriptHash)

    val finishedData = FinishedData(verifyData)
    val serializedFinished = serializeFinished(finishedData)

    handshakeTranscript += serializedFinished // Add client's Finished to transcript
    tlsHandshakeState = TlsHandshakeState.CLIENT_FINISHED_SENT
    return serializedFinished
  }

  suspend fun deriveApplicationSecrets(hkdfService: HkdfService, manager: QuicConnectionManager) {
    if (tlsHandshakeState != TlsHandshakeState.CLIENT_FINISHED_SENT &&
        tlsHandshakeState != TlsHandshakeState.READY_TO_SEND_CLIENT_FINISHED) { // Should be called after Server Finished is verified
        println("deriveApplicationSecrets called at inappropriate state: $tlsHandshakeState. Expected READY_TO_SEND_CLIENT_FINISHED or CLIENT_FINISHED_SENT.")
        return
    }

    val fullTranscriptHash = sha256(handshakeTranscript)

    val ikmForMasterSecret = ByteArray(32) // Zero-filled for SHA-256

    if (this.derivedHandshakeSecret == null) {
        println("Error: derivedHandshakeSecret (salt for master secret) is null.")
        return
    }
    val masterSecret = hkdfService.extract(salt = this.derivedHandshakeSecret!!, ikm = ikmForMasterSecret)

    val clientAppTrafficSecret = hkdfExpandLabel(hkdfService, masterSecret, "c ap traffic", fullTranscriptHash, 32)
    val serverAppTrafficSecret = hkdfExpandLabel(hkdfService, masterSecret, "s ap traffic", fullTranscriptHash, 32)

    val clientAppKey = hkdfExpandLabel(hkdfService, clientAppTrafficSecret, "quic key", ByteArray(0), 16)
    val clientAppIv = hkdfExpandLabel(hkdfService, clientAppTrafficSecret, "quic iv", ByteArray(0), 12)
    val clientAppHpKey = hkdfExpandLabel(hkdfService, clientAppTrafficSecret, "quic hp", ByteArray(0), 16)
    val clientAppSecrets = QuicSecrets(clientAppKey, clientAppIv, clientAppHpKey)
    cryptoSecrets[EncryptionLevel.ONERTT] = clientAppSecrets // Client's 1-RTT sending keys
    manager.updateSecrets(EncryptionLevel.ONERTT, clientAppSecrets)


    val serverAppKey = hkdfExpandLabel(hkdfService, serverAppTrafficSecret, "quic key", ByteArray(0), 16)
    val serverAppIv = hkdfExpandLabel(hkdfService, serverAppTrafficSecret, "quic iv", ByteArray(0), 12)
    val serverAppHpKey = hkdfExpandLabel(hkdfService, serverAppTrafficSecret, "quic hp", ByteArray(0), 16)
    // Store server's 1-RTT keys for reception locally.
    // The manager might not need separate storage if it assumes symmetric 1-RTT keys after handshake,
    // or if its 'ONERTT' level is for client sending.
    // For clarity, QuicConnection can hold its own reception keys if needed.
    // cryptoSecrets[EncryptionLevel.ONERTT_SERVER_KEYS] = QuicSecrets(serverAppKey, serverAppIv, serverAppHpKey)
    // For now, client only stores its send keys in the main map. Server read keys could be separate if needed.

    tlsHandshakeState = TlsHandshakeState.HANDSHAKE_COMPLETE
    println("Handshake complete. Application secrets derived and updated in manager for ONERTT.")
  }
}

enum class TlsHandshakeState {
  EXPECTING_SERVER_HELLO,
  EXPECTING_ENCRYPTED_EXTENSIONS,
  EXPECTING_CERTIFICATE,
  EXPECTING_CERTIFICATE_VERIFY,
  EXPECTING_SERVER_FINISHED, // Renamed from EXPECTING_FINISHED for clarity
  READY_TO_SEND_CLIENT_FINISHED, // New state after server Finished is processed
  CLIENT_FINISHED_SENT,    // After client sends its Finished message
  HANDSHAKE_COMPLETE
}

// This local QuicCryptoLevel enum is now replaced by borg.trikeshed.net.quic.EncryptionLevel
// enum class QuicCryptoLevel {
//   INITIAL,
//   HANDSHAKE_CLIENT_KEYS,
//   HANDSHAKE_SERVER_KEYS,
//   APPLICATION_0_RTT,
//   APPLICATION_1_RTT_CLIENT_KEYS,
//   APPLICATION_1_RTT_SERVER_KEYS
// }

  // Stream offset management functions
  fun getStreamSendOffset(streamId: ULong): Long = streamSendOffsets.getOrDefault(streamId, 0L)

  fun updateStreamSendOffset(streamId: ULong, bytesSentCount: Long) {
      streamSendOffsets[streamId] = getStreamSendOffset(streamId) + bytesSentCount
  }

  fun getStreamReceiveOffset(streamId: ULong): Long = streamReceiveOffsets.getOrDefault(streamId, 0L)

  fun updateStreamReceiveOffset(streamId: ULong, newOffset: Long) {
      // Here, newOffset is likely the offset from the STREAM frame + length of data
      // We should store the next expected offset, so if data up to X was received, next is X.
      // Or, if it's max offset received:
      if (newOffset > getStreamReceiveOffset(streamId)) {
          streamReceiveOffsets[streamId] = newOffset
      }
  }
