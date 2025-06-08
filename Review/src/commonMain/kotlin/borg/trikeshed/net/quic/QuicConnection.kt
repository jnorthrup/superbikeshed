package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.ConnectionId // From QuicTypes.kt
import borg.trikeshed.net.quic.PacketNumber // From QuicTypes.kt
import borg.trikeshed.net.quic.tls.ClientHelloData
import borg.trikeshed.net.quic.crypto.QuicCryptoUtils
import borg.trikeshed.net.quic.tls.CertificateData
import borg.trikeshed.net.quic.tls.CertificateEntry
import borg.trikeshed.net.quic.tls.CertificateVerifyData
import borg.trikeshed.net.quic.tls.ServerHelloData
import borg.trikeshed.net.quic.tls.ServerFinishedData
import borg.trikeshed.net.quic.tls.TlsSignatureScheme
import borg.trikeshed.net.quic.tls.deserializeCertificate
import borg.trikeshed.net.quic.tls.deserializeCertificateVerify
import borg.trikeshed.net.quic.tls.deserializeEncryptedExtensions
import borg.trikeshed.net.quic.tls.deserializeServerFinished
import borg.trikeshed.net.quic.tls.deserializeServerHello
import borg.trikeshed.net.quic.tls.serializeFinished
import borg.trikeshed.net.quic.crypto.QuicSecrets
// import borg.trikeshed.net.quic.crypto.generateEcdhKeyPair // Managed by TlsService
// import borg.trikeshed.net.quic.crypto.hkdfExpandLabel // Now using the new wrapper
// import borg.trikeshed.net.quic.crypto.hkdfExtract // Now using HkdfService directly
// import borg.trikeshed.net.quic.crypto.hkdfExpandLabel // The new wrapper from QuicCryptoUtils.kt
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
import kotlin.coroutines.CoroutineContext // For passing to TlsHandler

enum class QuicConnectionStateEnum {
  INITIAL,
  HANDSHAKE_STARTED, // ClientHello sent, ServerHello received, Handshake keys established (via TlsHandler)
  // HANDSHAKE_KEYS_DERIVED is implicit in HANDSHAKE_STARTED or HANDSHAKE_COMPLETED
  // CLIENT_FINISHED_SENT might be internal to TlsHandler
  HANDSHAKE_COMPLETED, // ServerFinished received and validated, Application keys established (via TlsHandler callback)
  CONNECTED, // Connection established and ready for 1-RTT data
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

  // TLS related states are now managed by QuicTlsHandler or TlsService
  // var tlsHandshakeState: TlsHandshakeState? = null,
  // var clientEphemeralPrivateKey: ByteArray? = null, // Managed by TlsService
  // var clientEphemeralPublicKey: ByteArray? = null, // Managed by TlsService
  // var handshakeTranscript: ByteArray = byteArrayOf(), // Managed by TlsService
  // private var derivedHandshakeSecret: ByteArray? = null, // Managed by TlsService logic

  // QUIC-level secrets, derived from TLS secrets by QuicTlsHandler
  val cryptoSecrets: MutableMap<EncryptionLevel, QuicSecrets> = mutableMapOf(),
  var serverCertificates: List<CertificateEntry>? = null, // Can be populated by TlsHandler callback

  // Base TLS secrets, populated by QuicTlsHandler from TlsService callbacks
  var clientHandshakeTrafficSecretInternal: ByteArray? = null,
  var serverHandshakeTrafficSecretInternal: ByteArray? = null,
  var clientAppTrafficSecretInternal: ByteArray? = null, // New field for base app traffic secret
  var serverAppTrafficSecretInternal: ByteArray? = null, // Was already here, population changes

  internal var clientInitialSecretsForSending: QuicSecrets? = null, // Keys for client to send Initial packets
  internal var serverInitialSecretsForReception: QuicSecrets? = null, // Keys for client to receive server's Initial packets
  internal var serverHandshakeSecretsForReception: QuicSecrets? = null, // QUIC keys for decrypting server handshake packets
  var initialClientChosenDcId: ByteArray? = null, // DCID client picks for its first Initial packets
  var trustedCaCertBytes: ByteArray? = null, // For X.509 validation by client (used by TlsService)
  var expectedServerName: String? = null,   // For X.509 validation by client (used by TlsService)
  internal var parsedServerCertificates: List<borg.trikeshed.net.quic.crypto.PlatformX509Certificate>? = null, // Parsed server certs (populated by TlsHandler)
  // Stream data tracking
  private val streamSendOffsets: MutableMap<ULong, Long> = mutableMapOf(),
  private val streamReceiveOffsets: MutableMap<ULong, Long> = mutableMapOf()
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
    fun newClientConnection( // Renamed for clarity
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

  // This should be called before startTlsHandshake.
  // HkdfService is now part of QuicCryptoUtils
  suspend fun deriveInitialSecrets(cryptoUtils: QuicCryptoUtils, manager: QuicConnectionManager) {
    if (cryptoSecrets[EncryptionLevel.INITIAL] == null) {
        val initialSecret = cryptoUtils.hkdfExtract(INITIAL_SALT_V1, this.clientId)
        val clientInitialSecret = cryptoUtils.hkdfExpandLabel(initialSecret, "client in", byteArrayOf(), 32)
        val clientInitialKey = cryptoUtils.hkdfExpandLabel(clientInitialSecret, "quic key", byteArrayOf(), 16)
        val clientInitialIv = cryptoUtils.hkdfExpandLabel(clientInitialSecret, "quic iv", byteArrayOf(), 12)
        val clientInitialHpKey = cryptoUtils.hkdfExpandLabel(clientInitialSecret, "quic hp", byteArrayOf(), 16)

        val clientSecrets = QuicSecrets(clientInitialKey, clientInitialIv, clientInitialHpKey)
        this.clientInitialSecretsForSending = clientSecrets // Store specifically for sending
        cryptoSecrets[EncryptionLevel.INITIAL] = clientSecrets // General map for current sending level
        manager.updateSecrets(EncryptionLevel.INITIAL, clientSecrets)

        val dcidUsedInClientInitial = this.initialClientChosenDcId
            ?: throw IllegalStateException("initialClientChosenDcId must be set before deriving server initial secrets for reception.")

        val serverInitialSecretLabel = "server in"
        // Note: The salt for HKDF-Extract for initial secrets is fixed (INITIAL_SALT_V1).
        // The IKM for client initial is client Connection ID.
        // The IKM for server initial is also client Connection ID.
        // The distinction comes from the "label" in HKDF-Expand-Label ("client in" vs "server in").
        // The `initialSecret` derived using `hkdfExtract(INITIAL_SALT_V1, this.clientId)` is common for both.
        val serverInitialSecret = cryptoUtils.hkdfExpandLabel(initialSecret, serverInitialSecretLabel, byteArrayOf(), 32)
        val serverInitialKey = cryptoUtils.hkdfExpandLabel(serverInitialSecret, "quic key", byteArrayOf(), 16)
        val serverInitialIv = cryptoUtils.hkdfExpandLabel(serverInitialSecret, "quic iv", byteArrayOf(), 12)
        val serverInitialHpKey = cryptoUtils.hkdfExpandLabel(serverInitialSecret, "quic hp", byteArrayOf(), 16)
        this.serverInitialSecretsForReception = QuicSecrets(serverInitialKey, serverInitialIv, serverInitialHpKey)
    }
  }

  // Method to be called by QuicTlsHandler when TLS handshake is complete
  fun onTlsHandshakeComplete(negotiatedAlpn: String?) {
    // Update connection state, etc.
    // This is now triggered by QuicTlsHandler's callback.
    // Actual key derivation for application secrets happens in QuicTlsHandler and its callbacks.
    // Here we just update the state.
    this.state = QuicConnectionStateEnum.HANDSHAKE_COMPLETED
    // TODO: state should transition to CONNECTED once 1-RTT keys are confirmed available and usable for sending/receiving.
    // This might depend on whether onNewEncryptionSecretsReady for APPLICATION_DATA has been fully processed.
    println("QuicConnection: TLS Handshake complete. Negotiated ALPN: $negotiatedAlpn. State: $state")
  }

  // Method to be called by QuicTlsHandler to provide data to send (CRYPTO frames)
  fun onCryptoDataToSend(data: ByteArray, level: EncryptionLevel) {
    // This connection needs to pass this data to something that will packetize and send it.
    // This is where the QuicConnectionManager (which might be part of a larger entity like QuicClientImpl)
    // and eventually QuicNetworkService come in.
    // For now, this is a placeholder for where QuicConnection gets data from TlsHandler.
    // The actual sending is managed by a higher-level component that owns the QuicTlsHandler and calls this.
    // This method is more of an FYI or if QuicConnection itself was responsible for packetizing.
    // In the current design, QuicTlsHandler calls a direct `sendCryptoDataCallback` provided to it.
    println("QuicConnection: Received crypto data to send at $level. Length: ${data.size}. (Informational)")
  }

  // Initiates the handshake by creating and starting a QuicTlsHandler.
  suspend fun startTlsHandshake(
      coroutineContext: CoroutineContext, // Parent context for the TlsHandler
      hostname: String,
      alpn: List<String>,
      connectionManager: QuicConnectionManager, // Used by TlsHandler to update secrets/state
      cryptoUtils: QuicCryptoUtils, // For HKDF functions needed by TlsHandler
      // Callback for TlsHandler to send data via QUIC layer (e.g., QuicClientImpl -> NetworkService)
      sendCryptoDataCallback: (data: ByteArray, level: EncryptionLevel) -> Unit,
      // Callback for TlsHandler to signal a fatal alert (e.g., QuicClientImpl should close connection)
      handleFatalAlertCallback: (alert: borg.trikeshed.net.tls.TlsAlert, quicErrorCode: Long) -> Unit
  ): QuicTlsHandler {
    if (cryptoSecrets[EncryptionLevel.INITIAL] == null) {
        throw IllegalStateException("Initial secrets not derived. Call deriveInitialSecrets first.")
    }
    if (this.initialClientChosenDcId == null) {
        // Ensure initialClientChosenDcId is set, as it's part of the QUIC transport params extension.
        // Typically, it's the same as this.clientId for the first flight from client.
        // Or it can be different if client anticipates server changing CID.
        // For simplicity, let's ensure it's set, defaulting to clientId if it makes sense in your model.
        // However, it's usually a *new* CID chosen by the client for the server to use as DCID.
        // The QuicTransportParameters extension should contain the *initial_source_connection_id*
        // which is the SCID the client initially chose for itself (this.clientId).
        // The TlsHandler should use the *actual* client's initial SCID for the transport params.
        // For now, let's assume `this.clientId` is the initial SCID from client's perspective.
        // And `this.initialClientChosenDcId` is what client wants server to use for DCID.
        // If `this.initialClientChosenDcId` is null, it should be generated.
        this.initialClientChosenDcId = Random.nextBytes(8)
        println("Generated initialClientChosenDcId for TLS handshake: ${this.initialClientChosenDcId?.toHexString()}")
    }

    // QUIC Transport Parameters for TLS extension
    val transportParameters = QuicTransportParameters(
        parameters = listOf(
            InitialSourceConnectionId(this.clientId), // Client's initial SCID
            InitialMaxData(1_048_576uL),
            InitialMaxStreamDataBidiLocal(1_048_576uL),
            InitialMaxStreamsBidi(100uL),
            MaxIdleTimeout(30_000uL), // Our desired max idle timeout
            MaxAckDelay(this.localMaxAckDelay) // Our max_ack_delay
            // Potentially: initial_destination_connection_id if known and different from serverId
        )
    )
    val serializedTransportParameters = serializeQuicTransportParameters(transportParameters)

    val tlsHandler = QuicTlsHandler(
        parentCoroutineContext = coroutineContext,
        connection = this, // Pass this QuicConnection instance
        connectionManager = connectionManager,
        cryptoUtils = cryptoUtils,
        localQuicTransportParams = serializedTransportParameters,
        onHandshakeCompleteCallback = { negotiatedAlpnValue ->
            this.onTlsHandshakeComplete(negotiatedAlpnValue)
        },
        onHandshakeDataToSendCallback = sendCryptoDataCallback,
        onTlsAlertCallback = handleFatalAlertCallback
    )

    // Start the client handshake process within the TlsHandler
    tlsHandler.startClientHandshake(hostname, alpn)

    // The TlsHandler is now active and will use callbacks to interact.
    // The caller should store this TlsHandler instance to feed it incoming CRYPTO data.
    return tlsHandler
  }

  // Old TLS message processing methods like processServerHandshakeMessage, generateClientFinishedMessage,
  // decryptHandshakeMessage, deriveApplicationSecrets are removed.
  // Their logic is now encapsulated within the TlsService implementation and coordinated by QuicTlsHandler.

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
    // ... compare other relevant fields for equality ...
    // Avoid comparing fields that are internal to TLS handling if they are removed or changed.
    return true
  }

  override fun hashCode(): Int {
    var result = clientId.contentHashCode()
    result = 31 * result + (serverId?.contentHashCode() ?: 0)
    result = 31 * result + state.hashCode()
    result = 31 * result + localPacketNumber.hashCode()
    // ... include other relevant fields ...
    return result
  }

  // Stream offset management functions
  fun getStreamSendOffset(streamId: ULong): Long = streamSendOffsets.getOrDefault(streamId, 0L)

  fun updateStreamSendOffset(streamId: ULong, bytesSentCount: Long) {
      streamSendOffsets[streamId] = getStreamSendOffset(streamId) + bytesSentCount
  }

  fun getStreamReceiveOffset(streamId: ULong): Long = streamReceiveOffsets.getOrDefault(streamId, 0L)

  fun updateStreamReceiveOffset(streamId: ULong, newOffset: Long) {
      if (newOffset > getStreamReceiveOffset(streamId)) {
          streamReceiveOffsets[streamId] = newOffset
      }
  }

// TlsHandshakeState enum is no longer needed here, as TLS state is managed by TlsService/QuicTlsHandler
// enum class TlsHandshakeState {
//   EXPECTING_SERVER_HELLO,
//   EXPECTING_ENCRYPTED_EXTENSIONS,
//   EXPECTING_CERTIFICATE,
//   EXPECTING_CERTIFICATE_VERIFY,
//   EXPECTING_SERVER_FINISHED, // Renamed from EXPECTING_FINISHED for clarity
//   READY_TO_SEND_CLIENT_FINISHED, // New state after server Finished is processed
//   CLIENT_FINISHED_SENT,    // After client sends its Finished message
//   HANDSHAKE_COMPLETE
// }
