package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.crypto.QuicCryptoUtils // For hkdfExpandLabel for QUIC keys
import borg.trikeshed.net.quic.tls.TlsAlert
import borg.trikeshed.net.quic.tls.TlsConnection
import borg.trikeshed.net.quic.tls.TlsEncryptionLevel
import borg.trikeshed.net.quic.tls.TlsHandshakeCallbacks
import borg.trikeshed.net.tls.TlsService // The main expect interface
import borg.trikeshed.net.tls.TlsServiceKey
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

// Data class to hold QUIC specific secrets derived from TLS stage secrets
// This aligns with borg.trikeshed.net.quic.crypto.QuicSecrets
// but might be slightly different if TLS provides combined secrets vs. separate read/write.
// For now, assuming QuicSecrets is suitable.
typealias QuicDerivedSecrets = borg.trikeshed.net.quic.crypto.QuicSecrets


class QuicTlsHandler(
    private val parentCoroutineContext: CoroutineContext,
    private val connection: QuicConnection, // To update its state and secrets
    private val connectionManager: QuicConnectionManager, // To update QUIC secrets
    private val cryptoUtils: QuicCryptoUtils, // For QUIC specific key derivation from TLS secrets
    private val localQuicTransportParams: ByteArray,
    private val onHandshakeCompleteCallback: (negotiatedAlpn: String?) -> Unit,
    private val onHandshakeDataToSendCallback: (data: ByteArray, level: EncryptionLevel) -> Unit, // QUIC level
    private val onTlsAlertCallback: (alert: TlsAlert, quicError: Long) -> Unit // Maps TLS alert to QUIC error
) : TlsHandshakeCallbacks, CoroutineScope {

    override val coroutineContext: CoroutineContext = parentCoroutineContext + SupervisorJob() + CoroutineName("QuicTlsHandler-${connection.clientId.toHexString()}")

    private val tlsService: TlsService = coroutineContext[TlsServiceKey]
        ?: throw IllegalStateException("TlsService not found in CoroutineContext. Ensure platform implementation is provided.")

    private var tlsConnection: TlsConnection? = null
    // This queue is for illustrative purposes if TlsHandler were to manage a queue.
    // The current onHandshakeDataToSendCallback directly passes data out.
    // If it were to queue, it would need to be a var and re-assigned with an immutable Series.
    private var cryptoDataToSendQueue: Series<Join<ByteArray, EncryptionLevel>> = Series.empty()


    suspend fun startClientHandshake(hostname: String, alpnProtocols: Series<String>) {
        val result = tlsService.startClientHandshake(
            hostname = hostname,
            alpnProtocols = alpnProtocols, // Pass Series<String> directly
            quicTransportParams = localQuicTransportParams,
            callbacks = this
        )
        result.fold(
            onSuccess = { activeTlsConnection ->
                tlsConnection = activeTlsConnection
                // Initial handshake messages might be triggered now via onHandshakeDataToSend
            },
            onFailure = { exception ->
                // Handle handshake initiation failure, e.g., by closing QUIC connection
                onTlsAlertCallback(
                    TlsAlert(2, 80), // level fatal, description internal_error
                    TransportErrorCode.INTERNAL_ERROR.code // Generic internal error
                )
                cancel("TLS handshake initiation failed", exception)
            }
        )
    }

    fun processIncomingCryptoData(data: ByteArray, quicLevel: EncryptionLevel) {
        val currentTlsConnection = tlsConnection ?: run {
            // Should not happen if startClientHandshake was called and succeeded
            // Or if handshake already completed/failed.
            return
        }

        // Map QUIC EncryptionLevel to TLS TlsEncryptionLevel
        val tlsLevel = when (quicLevel) {
            EncryptionLevel.INITIAL -> TlsEncryptionLevel.HANDSHAKE // TLS client/server hello are part of "handshake" level for TLS
            EncryptionLevel.HANDSHAKE -> TlsEncryptionLevel.HANDSHAKE
            EncryptionLevel.ONERTT -> TlsEncryptionLevel.APPLICATION_DATA // This is unlikely for CRYPTO frames post-handshake
            else -> {
                 // Log error: unexpected QUIC level for CRYPTO data
                onTlsAlertCallback(
                    TlsAlert(2, 10), // level fatal, description unexpected_message
                    TransportErrorCode.PROTOCOL_VIOLATION.code
                )
                return
            }
        }

        launch { // Process in a new coroutine to not block caller
            val result = currentTlsConnection.processHandshakeData(data, tlsLevel)
            result.onFailure { exception ->
                // TLS stack indicated an error processing the data
                // onTlsAlertReceived might have already been called by the TlsService impl.
                // If not, or if we need to map this specific exception:
                // For now, assume TlsService impl calls onTlsAlertReceived for specific TLS errors.
                // If processHandshakeData throws directly, it's a critical local failure.
                onTlsAlertCallback(
                    TlsAlert(2, 80), // internal_error
                    TransportErrorCode.INTERNAL_ERROR.code
                )
                cancel("TLS data processing failed", exception)
            }
        }
    }

    // --- TlsHandshakeCallbacks Implementation ---

    override fun onTlsAlertReceived(alert: TlsAlert) {
        // Map TLS alert to QUIC transport error code
        val quicErrorCode = mapTlsAlertToQuicError(alert)
        onTlsAlertCallback(alert, quicErrorCode)
        if (alert.level == 2.toByte()) { // Fatal
            cancel("Fatal TLS Alert received: desc=${alert.description}")
        }
    }

    override fun onTlsAlertToSend(alert: TlsAlert) {
        // This callback might be used by TlsService to indicate it wants to send an alert.
        // The QUIC layer might decide to send a CONNECTION_CLOSE instead.
        val quicErrorCode = mapTlsAlertToQuicError(alert)
        onTlsAlertCallback(alert, quicErrorCode) // Inform QUIC layer
    }

    override fun onHandshakeDataToSend(data: ByteArray, level: TlsEncryptionLevel) {
        // Map TLS level back to QUIC level for sending in CRYPTO frames
        val quicLevel = when (level) {
            TlsEncryptionLevel.HANDSHAKE -> EncryptionLevel.HANDSHAKE // Could also be INITIAL if it's ClientHello
            TlsEncryptionLevel.APPLICATION_DATA -> EncryptionLevel.ONERTT // For post-handshake messages like NewSessionTicket
        }
        // TODO: Determine if it's ClientHello to use INITIAL level.
        // This needs more sophisticated state tracking or info from TlsService.
        // For now, assume HANDSHAKE level for handshake data.
        // A robust solution would involve the TlsService indicating the *exact* QUIC packet type/level.
        // Or, the first call to onHandshakeDataToSend (ClientHello) is special-cased to INITIAL.

        // Hacky way to guess ClientHello for INITIAL level:
        // This is not robust. TlsService should ideally specify the QUIC level.
        val finalQuicLevel = if (connectionManager.getSendEncryptionLevel() == EncryptionLevel.INITIAL && level == TlsEncryptionLevel.HANDSHAKE) {
            EncryptionLevel.INITIAL
        } else if (level == TlsEncryptionLevel.HANDSHAKE) {
            EncryptionLevel.HANDSHAKE
        } else {
            EncryptionLevel.ONERTT
        }
        onHandshakeDataToSendCallback(data, finalQuicLevel)
    }

    override fun onNewEncryptionSecretsReady(
        level: TlsEncryptionLevel,
        readSecret: ByteArray, // From peer's perspective, this is their write secret
        writeSecret: ByteArray, // From peer's perspective, this is their read secret
        cipherSuite: Int // e.g., 0x1301 for TLS_AES_128_GCM_SHA256
    ) {
        // These are TLS-level secrets (e.g., client_handshake_traffic_secret, server_handshake_traffic_secret)
        // QUIC needs to derive its own packet protection keys from these.
        val quicWriteLevel: EncryptionLevel
        val quicReadLevel: EncryptionLevel // For updating what connection expects to read from peer

        when (level) {
            TlsEncryptionLevel.HANDSHAKE -> {
                quicWriteLevel = EncryptionLevel.HANDSHAKE
                quicReadLevel = EncryptionLevel.HANDSHAKE // Peer also sends handshake with handshake keys

                // Client writes with client_handshake_traffic_secret (writeSecret)
                // Client reads with server_handshake_traffic_secret (readSecret)
                val clientSendSecrets = cryptoUtils.deriveQuicSecrets(writeSecret, cipherSuite, "client")
                connectionManager.updateSecrets(quicWriteLevel, clientSendSecrets)
                connection.clientHandshakeTrafficSecretInternal = writeSecret // Store base TLS secret

                val serverSendSecrets = cryptoUtils.deriveQuicSecrets(readSecret, cipherSuite, "server")
                connection.serverHandshakeSecretsForReception = serverSendSecrets // For client to decrypt server handshake packets
                connection.serverHandshakeTrafficSecretInternal = readSecret // Store base TLS secret
            }
            TlsEncryptionLevel.APPLICATION_DATA -> {
                quicWriteLevel = EncryptionLevel.ONERTT
                quicReadLevel = EncryptionLevel.ONERTT

                // Client writes with client_application_traffic_secret_0 (writeSecret)
                // Client reads with server_application_traffic_secret_0 (readSecret)
                val clientAppSecrets = cryptoUtils.deriveQuicSecrets(writeSecret, cipherSuite, "client")
                connectionManager.updateSecrets(quicWriteLevel, clientAppSecrets)
                // connection.clientAppTrafficSecret = writeSecret // Store base TLS secret if needed

                val serverAppSecrets = cryptoUtils.deriveQuicSecrets(readSecret, cipherSuite, "server")
                // connection.serverAppSecretsForReception = serverAppSecrets // Store if needed for decryption directly by QuicConnection
                connection.serverAppTrafficSecretInternal = readSecret // Store base TLS secret

                // Also update the connection manager's ONERTT secrets if it's used for receiving by some shared component
                // For now, ConnectionManager stores client's send perspective. QuicConnection stores server's read perspective.
            }
        }
        // Update connection state if appropriate, e.g. after handshake keys are ready
        if (level == TlsEncryptionLevel.HANDSHAKE) {
             connectionManager.setState(QuicConnectionStateEnum.HANDSHAKE_STARTED) // Or a more specific state
        }
    }

    override fun onHandshakeComplete(negotiatedAlpn: String?) {
        connectionManager.setState(QuicConnectionStateEnum.HANDSHAKE_COMPLETED) // Or CONNECTED if 1-RTT keys are also ready
        onHandshakeCompleteCallback(negotiatedAlpn)
        // cancel() // Scope can be cancelled as TLS handshake is done. Or keep for NewSessionTicket?
    }

    override fun onPeerCertificateReceived(derEncodedCertificate: ByteArray?) {
        // This is informational. The TlsService actual implementation should have already validated it.
        // QuicConnection can store it if needed for application-level checks.
        // For now, just log or ignore.
        if (derEncodedCertificate != null) {
            // connection.setPeerRawCertificate(derEncodedCertificate)
        }
    }

    fun close() {
        tlsConnection?.close() // Inform TLS stack we are closing
        cancel("QuicTlsHandler closed") // Cancel the coroutine scope
    }

    private fun mapTlsAlertToQuicError(alert: TlsAlert): Long {
        // RFC 9001, Section 11: TLS errors map to QUIC error codes in the range 0x0100 to 0x01FF.
        // The value is 0x0100 + alert description.
        return (TransportErrorCode.CRYPTO_ERROR_TLS_ALERT_BASE.value + alert.description.toLong())
    }
}

// Helper extension that might be in QuicCryptoUtils or similar
fun QuicCryptoUtils.deriveQuicSecrets(tlsSecret: ByteArray, cipherSuite: Int, perspective: String): QuicDerivedSecrets {
    // Based on cipherSuite, determine hash length for HKDF. Assume SHA-256 for TLS 1.3 (0x1301, 0x1302, 0x1303)
    // val hashLen = 32 // For SHA-256
    val keyLen = 16  // For AES-128
    val ivLen = 12   // For AES-GCM common IV sizes
    val hpLen = 16   // For AES-128 header protection

    // Actual labels are "quic key", "quic iv", "quic hp"
    val quicKey = this.hkdfExpandLabel(tlsSecret, "quic key", ByteArray(0), keyLen)
    val quicIv = this.hkdfExpandLabel(tlsSecret, "quic iv", ByteArray(0), ivLen)
    val quicHp = this.hkdfExpandLabel(tlsSecret, "quic hp", ByteArray(0), hpLen)
    return QuicDerivedSecrets(quicKey, quicIv, quicHp)
}

// Helper to convert ConnectionId to HexString for logging
fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

// Adding missing imports based on usage
import kotlinx.coroutines.CoroutineName // For CoroutineScope context element
import borg.trikeshed.net.quic.QuicConnectionStateEnum // For setting state in ConnectionManager
import borg.trikeshed.net.quic.TransportErrorCode // For mapping alerts
import borg.trikeshed.net.quic.EncryptionLevel // For onHandshakeDataToSendCallback and internal logic
import borg.trikeshed.net.quic.QuicConnectionManager // For updating secrets and state
// borg.trikeshed.net.quic.ConnectionId is implicitly used via QuicConnection.clientId
// borg.trikeshed.net.quic.crypto.QuicSecrets is aliased as QuicDerivedSecrets
// borg.trikeshed.net.quic.tls.TlsAlert is imported
// borg.trikeshed.net.quic.tls.TlsConnection is imported
// borg.trikeshed.net.quic.tls.TlsEncryptionLevel is imported
// borg.trikeshed.net.quic.tls.TlsHandshakeCallbacks is imported
// borg.trikeshed.net.tls.TlsService is imported
// borg.trikeshed.net.tls.TlsServiceKey is imported
// kotlin.coroutines.CoroutineContext is imported
// kotlinx.coroutines.CoroutineScope is imported
// kotlinx.coroutines.SupervisorJob is imported
// kotlinx.coroutines.cancel is imported
// kotlinx.coroutines.launch is imported
// borg.trikeshed.net.quic.crypto.QuicCryptoUtils is imported for deriveQuicSecrets and constructor
