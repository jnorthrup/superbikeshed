package borg.trikeshed.net.tls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer
import javax.net.ssl.*
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

// Constants for TLS Exporter labels (RFC 5705 context, used by QUIC for key derivation from TLS 1.3)
// QUIC RFC 9001 Appendix A specifies labels like "client hs traffic", "server hs traffic", etc.
// These are for the HKDF-Expand-Label operation done by QUIC.
// The TLS exporter (RFC 5705) itself might use different labels or contexts.
// For Java's SSLSession.exportKeyingMaterial, the label is directly what you ask for.
// "TLS 1.3 Exporter" labels are defined in RFC 8446, Section 7.5.
// QUIC specific exporter labels are defined in RFC 9001, Section 5.1.
// Let's use the QUIC specific labels directly if the JSSE provider supports them,
// otherwise, one might need to export a master secret and derive them manually.
// For this implementation, we assume direct exportability or that the provided label
// will correctly produce the base secret QUIC needs.

const val QUIC_EXPORTER_LABEL_CLIENT_HANDSHAKE_TRAFFIC_SECRET = "client handshake traffic secret"
const val QUIC_EXPORTER_LABEL_SERVER_HANDSHAKE_TRAFFIC_SECRET = "server handshake traffic secret"
const val QUIC_EXPORTER_LABEL_CLIENT_APPLICATION_TRAFFIC_SECRET = "client application traffic secret"
const val QUIC_EXPORTER_LABEL_SERVER_APPLICATION_TRAFFIC_SECRET = "server application traffic secret"


class JvmTlsService : TlsService {

    override suspend fun startClientHandshake(
        hostname: String,
        alpnProtocols: List<String>,
        quicTransportParams: ByteArray, // Bytes of the QUIC TP TLS extension payload
        callbacks: TlsHandshakeCallbacks
    ): Result<TlsConnection> {
        try {
            val sslContext = SSLContext.getInstance("TLSv1.3")
            // Using default KeyManager and TrustManager. For production, configure appropriately.
            sslContext.init(null, null, java.security.SecureRandom())

            val engine = sslContext.createSSLEngine(hostname, -1) // Port hint, not strictly used by client engine
            engine.useClientMode = true

            val sslParameters = engine.sslParameters ?: SSLParameters()

            // Set SNI
            if (hostname.isNotEmpty()) {
                val sniHostName = SNIHostName(hostname)
                sslParameters.serverNames = listOf(sniHostName)
            }

            // Set ALPN
            if (alpnProtocols.isNotEmpty()) {
                sslParameters.applicationProtocols = alpnProtocols.toTypedArray()
            }
            engine.sslParameters = sslParameters

            // QUIC Transport Parameters Extension (RFC 9001, Section 8.2)
            // Standard SSLEngine makes adding custom extensions like QUIC's transport_parameters difficult.
            // This functionality is typically provider-specific (e.g., BouncyCastle) or requires reflection.
            // For this implementation, we acknowledge this limitation and log a warning.
            if (quicTransportParams.isNotEmpty()) {
                println("JvmTlsService: WARNING - Sending QUIC Transport Parameters TLS extension is not supported by standard SSLEngine. Parameter will be ignored.")
            }

            // Create a CoroutineScope for this TlsConnection. It should be a child of a connection-level scope.
            // For now, creating a new SupervisorJob. This should be refined with proper parent scope.
            val connectionJob = SupervisorJob(callbacks_getContext_Job_or_Default()) // conceptual parent job
            val connectionScope = CoroutineScope(Dispatchers.Default + connectionJob) // Use Default or IO for tasks

            val jvmTlsConnection = JvmTlsConnection(engine, callbacks, connectionScope)

            engine.beginHandshake()
            jvmTlsConnection.driveHandshake() // Initial drive

            return Result.success(jvmTlsConnection)
        } catch (e: Exception) {
            return Result.failure(RuntimeException("JVM TlsService: Failed to start client handshake: ${e.message}", e))
        }
    }
}
// Helper to get a Job from callbacks if it's a CoroutineScope or provide a default
private fun callbacks_getContext_Job_or_Default(): Job? {
    // This is conceptual. In a real app, the callbacks' context might be accessible.
    return null
}


internal class JvmTlsConnection(
    private val engine: SSLEngine,
    private val callbacks: TlsHandshakeCallbacks,
    override val coroutineContext: CoroutineContext // Provided by JvmTlsService, includes a Job
) : TlsConnection, CoroutineScope {

    // SSLEngine recommends using session.getPacketBufferSize() and session.getApplicationBufferSize().
    // Allocate them once and reuse.
    private val netOutBuffer: ByteBuffer = ByteBuffer.allocate(engine.session.packetBufferSize)
    private val appOutBuffer: ByteBuffer = ByteBuffer.allocate(engine.session.applicationBufferSize) // Typically empty for sending handshake data

    private val netInBuffer: ByteBuffer = ByteBuffer.allocate(engine.session.packetBufferSize) // For received data
    private val appInBuffer: ByteBuffer = ByteBuffer.allocate(engine.session.applicationBufferSize) // For unwrapped application data (usually empty during handshake)

    private val keyExtractionHelper = JvmQuicKeyExtractionHelper(engine, callbacks)
    private var closed = false

    // Initial non-blocking drive after beginHandshake
    fun driveHandshake() {
        if (!isActive || closed) return // isActive from CoroutineScope

        while (isActive && !closed) {
            when (val status = engine.handshakeStatus) {
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    appOutBuffer.clear() // Handshake data is generated by engine, not from appOutBuffer
                    netOutBuffer.clear()
                    try {
                        val result = engine.wrap(appOutBuffer, netOutBuffer)
                        netOutBuffer.flip()

                        if (result.status == SSLEngineResult.Status.CLOSED && engine.isOutboundDone) {
                            close() // Or handle graceful closure signal
                            return
                        }
                        if (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                             // Should not happen if netOutBuffer is sized to session.packetBufferSize
                             callbacks.onTlsAlertToSend(TlsAlert(2, SSLHandshakeException("SSLEngine wrap buffer overflow").toAlertDescription(), "SSLEngine wrap buffer overflow"))
                             closeAndSignalError("SSLEngine wrap buffer overflow")
                             return
                        }

                        if (netOutBuffer.hasRemaining()) {
                            val dataToSend = ByteArray(netOutBuffer.remaining())
                            netOutBuffer.get(dataToSend)
                            callbacks.onHandshakeDataToSend(dataToSend, keyExtractionHelper.currentTlsLevel())
                        }
                        keyExtractionHelper.extractAndSendKeysIfNeeded(keyExtractionHelper.currentTlsLevel())

                        if (result.handshakeStatus != SSLEngineResult.HandshakeStatus.NEED_WRAP) continue // Re-evaluate new status
                        else return // If it still needs wrap but produced data, let caller send it then call drive again or process more input.
                                   // Or if it produced no data and still NEED_WRAP, it's a strange state.

                    } catch (e: SSLException) {
                        callbacks.onTlsAlertToSend(TlsAlert(2, e.toAlertDescription(), "SSLException during wrap: ${e.message}"))
                        closeAndSignalError("SSLException during wrap: ${e.message}", e)
                        return
                    }
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    // Waiting for data from peer. processHandshakeData() will feed it and call driveHandshake().
                    return
                }
                SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                    var task: Runnable?
                    while (engine.delegatedTask.also { task = it } != null) {
                        try {
                            task!!.run()
                        } catch (e: Exception) {
                             callbacks.onTlsAlertToSend(TlsAlert(2, 80, "Delegated task error: ${e.message}")) // internal_error
                             closeAndSignalError("Delegated task error: ${e.message}", e)
                             return
                        }
                    }
                    // After tasks, status might change, loop again.
                }
                SSLEngineResult.HandshakeStatus.FINISHED -> {
                    keyExtractionHelper.extractAndSendKeysIfNeeded(TlsEncryptionLevel.APPLICATION_DATA) // Ensure final app keys
                    val negotiatedAlpn = try { engine.applicationProtocol } catch (e: UnsupportedOperationException) { null } // ALPN support Java 9+
                    callbacks.onHandshakeComplete(negotiatedAlpn)
                    // Handshake complete, further calls to driveHandshake are for app data or post-handshake messages.
                    return // Handshake is done.
                }
                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> {
                    // If handshake was in progress and now it's NOT_HANDSHAKING, it means it either
                    // finished successfully (and FINISHED state was hit) or an error occurred that
                    // moved it out of handshaking state without explicit FINISHED.
                    // If keys are extracted and onHandshakeComplete was called, we are good.
                    // Otherwise, this might be an implicit error or unexpected state.
                    if (!keyExtractionHelper.applicationSecretsReported) { // If app keys not yet out, something is wrong
                        // This could be an error path not properly handled by SSLException during wrap/unwrap
                         println("JvmTlsConnection: Entered NOT_HANDSHAKING unexpectedly before app keys reported.")
                        // callbacks.onTlsAlertToSend(TlsAlert(2, 80, "Handshake ended prematurely"))
                        // closeAndSignalError("Handshake ended prematurely or failed implicitly")
                    }
                    return
                }
                null -> { // Should not happen
                    closeAndSignalError("SSLEngine handshake status is null")
                    return
                }
            }
        }
    }

    override fun processHandshakeData(data: ByteArray, level: TlsEncryptionLevel): Result<Unit> {
        if (closed) return Result.failure(IllegalStateException("TLS connection is closed."))
        if (!isActive) return Result.failure(IllegalStateException("TLS CoroutineScope is not active."))

        // Append data to netInBuffer (assuming it's cleared or compacted appropriately)
        // For simplicity, assume netInBuffer is large enough or managed correctly.
        // A more robust impl would handle cases where 'data' > netInBuffer.remaining()
        if (netInBuffer.remaining() < data.size) {
            // This indicates either a very large handshake message or insufficient buffer sizing.
            // Or, previous data wasn't consumed fully. This part needs robust multi-packet handshake message handling.
            // For now, assume data fits or this is an error.
            val newBuffer = ByteBuffer.allocate(netInBuffer.position() + data.size + engine.session.packetBufferSize)
            netInBuffer.flip()
            newBuffer.put(netInBuffer)
            netInBuffer = newBuffer // This is not how you'd normally do it, but for placeholder...
            // A real solution uses a larger buffer or a loop to feed SSLEngine.
        }
        netInBuffer.put(data)
        netInBuffer.flip()

        try {
            while (netInBuffer.hasRemaining() && engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP && isActive && !closed) {
                appInBuffer.clear()
                val result = engine.unwrap(netInBuffer, appInBuffer)
                // appInBuffer.flip() // Data unwrapped into appInBuffer (usually handshake messages, not app data here)

                when (result.status) {
                    SSLEngineResult.Status.OK -> {
                        keyExtractionHelper.extractAndSendKeysIfNeeded(keyExtractionHelper.currentTlsLevel())
                        // Continue to drive handshake as status might have changed
                    }
                    SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                        // Need more data from peer.
                        netInBuffer.compact() // Preserve remaining partial data
                        driveHandshake() // Call drive to see if it transitions to NEED_WRAP or TASK
                        return Result.success(Unit)
                    }
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                        // appInBuffer is too small. This is unexpected if sized by session.applicationBufferSize.
                        // This might indicate an issue with appInBuffer management or an extremely large handshake message.
                        callbacks.onTlsAlertToSend(TlsAlert(2, SSLHandshakeException("SSLEngine unwrap app buffer overflow").toAlertDescription(), "SSLEngine unwrap app buffer overflow"))
                        closeAndSignalError("SSLEngine unwrap app buffer overflow")
                        return Result.failure(IllegalStateException("SSLEngine unwrap application buffer overflow"))
                    }
                    SSLEngineResult.Status.CLOSED -> {
                        if (engine.isInboundDone) {
                            close() // Peer closed cleanly
                        } else {
                            callbacks.onTlsAlertToSend(TlsAlert(2, SSLHandshakeException("SSLEngine unwrap returned CLOSED unexpectedly").toAlertDescription(), "SSLEngine unwrap returned CLOSED unexpectedly"))
                            closeAndSignalError("SSLEngine unwrap returned CLOSED unexpectedly")
                        }
                        return Result.success(Unit) // Or failure if unexpected
                    }
                    null -> {
                         closeAndSignalError("SSLEngine unwrap result status is null")
                         return Result.failure(IllegalStateException("SSLEngine unwrap result status is null."))
                    }
                }
                if (result.handshakeStatus != SSLEngineResult.HandshakeStatus.NEED_UNWRAP) break // Exit unwrap loop if status changed
            }
            netInBuffer.compact() // Preserve remaining partial data if any
            driveHandshake() // Continue handshake based on new status
            return Result.success(Unit)
        } catch (e: SSLException) {
            callbacks.onTlsAlertToSend(TlsAlert(2, e.toAlertDescription(), "SSLException during unwrap: ${e.message}"))
            closeAndSignalError("SSLException during unwrap: ${e.message}", e)
            return Result.failure(e)
        } catch (e: Exception) {
            callbacks.onTlsAlertToSend(TlsAlert(2, 80, "Generic exception during unwrap: ${e.message}")) // internal_error
            closeAndSignalError("Generic exception during unwrap: ${e.message}", e)
            return Result.failure(e)
        }
    }

    private fun closeAndSignalError(message: String, cause: Throwable? = null) {
        if(!closed) {
            // callbacks.onTlsAlertToSend if not already sent by caller
            // callbacks.onHandshakeFailed(QuicCurlException(message, cause)) // Or some other specific error callback
            close() // Ensure engine and scope are cleaned up
        }
    }


    override fun providePskTicket(ticket: ByteArray) {
        println("JvmTlsConnection: WARNING - providePskTicket not supported by this basic SSLEngine setup.")
        // Standard SSLEngine doesn't offer a simple API to inject PSK tickets for client-side resumption.
        // This usually involves custom SSLSessionCache or specific JSSE provider features.
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            if (isActive) { // Check if coroutine scope is still active
                if (!engine.isOutboundDone) {
                    engine.closeOutbound()
                    // After calling closeOutbound, we might need to drive the handshake
                    // to send the close_notify alert.
                    if (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                        driveHandshakeLoopForClose()
                    }
                }
                // For inbound, SSLEngine typically handles receiving close_notify.
                // If not already closed by peer, we might just close it.
                if (!engine.isInboundDone) {
                     try { engine.closeInbound() } catch (e: SSLException) { /* ignore if already closed or other side didn't send close_notify */ }
                }
            }
        } catch (e: SSLException) {
            println("JvmTlsConnection: SSLException during close: ${e.message}")
        } finally {
            // Cancel the CoroutineScope job to clean up any child coroutines/flows
            (coroutineContext[Job] as? CompletableJob)?.complete()
                ?: coroutineContext[Job]?.cancel()
            println("JvmTlsConnection: Closed for $engine")
        }
    }

    private fun driveHandshakeLoopForClose() {
        // Simplified loop to send pending close_notify
        while (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP && !engine.isOutboundDone && isActive) {
            appOutBuffer.clear()
            netOutBuffer.clear()
            try {
                val result = engine.wrap(appOutBuffer, netOutBuffer)
                netOutBuffer.flip()
                if (netOutBuffer.hasRemaining()) {
                    val dataToSend = ByteArray(netOutBuffer.remaining())
                    netOutBuffer.get(dataToSend)
                    // This data (close_notify) should be sent at ApplicationData level if handshake completed
                    callbacks.onHandshakeDataToSend(dataToSend, TlsEncryptionLevel.APPLICATION_DATA)
                }
                if (result.status == SSLEngineResult.Status.CLOSED || engine.isOutboundDone) break
                if (result.handshakeStatus != SSLEngineResult.HandshakeStatus.NEED_WRAP) break

            } catch (e: SSLException) {
                println("JvmTlsConnection: SSLException during close wrap: ${e.message}")
                break
            }
        }
    }
}


internal class JvmQuicKeyExtractionHelper(
    private val engine: SSLEngine,
    private val callbacks: TlsHandshakeCallbacks
) {
    private var handshakeKeysExtracted = false
    private var appKeysExtracted = false

    fun currentTlsLevel(): TlsEncryptionLevel {
        // This is an approximation. True TLS level for keying depends on handshake state.
        return when {
            appKeysExtracted -> TlsEncryptionLevel.APPLICATION_DATA
            handshakeKeysExtracted -> TlsEncryptionLevel.HANDSHAKE
            // Before any keys, QUIC uses Initial secrets. TLS engine data starts at Handshake level.
            else -> TlsEncryptionLevel.HANDSHAKE
        }
    }

    fun extractAndSendKeysIfNeeded(currentLevelAttempt: TlsEncryptionLevel) {
        val session = engine.session ?: return
        if (!session.isValid) return // Session not yet valid (e.g., early in handshake)

        val cipherSuiteString = session.cipherSuite
        val cipherSuiteInt = mapCipherSuiteNameToIANA(cipherSuiteString)
        if (cipherSuiteInt == 0) {
            println("JvmQuicKeyExtractionHelper: Unknown or unsupported cipher suite: $cipherSuiteString")
            callbacks.onTlsAlertToSend(TlsAlert(2, SSLHandshakeException("Unsupported cipher suite").toAlertDescription(), "Unsupported cipher suite: $cipherSuiteString"))
            // Consider this a fatal error for the connection context
            return
        }

        // For TLS 1.3, key derivation uses HKDF with the hash algorithm associated with the cipher suite.
        // AES-128-GCM uses SHA-256 (32-byte output). AES-256-GCM uses SHA-384 (48-byte output).
        // Chacha20Poly1305 uses SHA-256 (32-byte output).
        val secretLength = when (cipherSuiteInt) {
            0x1301, 0x1303 -> 32 // TLS_AES_128_GCM_SHA256, TLS_CHACHA20_POLY1305_SHA256 -> SHA256 -> 32 bytes
            0x1302 -> 48 // TLS_AES_256_GCM_SHA384 -> SHA384 -> 48 bytes
            else -> 32 // Default or throw error
        }


        if (!handshakeKeysExtracted &&
            (currentLevelAttempt == TlsEncryptionLevel.HANDSHAKE || engine.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)) {
            // Try to extract handshake keys once some wrapping/unwrapping has occurred.
            // Exact timing is tricky; they are available after key exchange is complete (e.g., after ServerHello + EncryptedExtensions).
            try {
                val clientHsSecret = session.exportKeyingMaterial(QUIC_EXPORTER_LABEL_CLIENT_HANDSHAKE_TRAFFIC_SECRET, null, secretLength)
                val serverHsSecret = session.exportKeyingMaterial(QUIC_EXPORTER_LABEL_SERVER_HANDSHAKE_TRAFFIC_SECRET, null, secretLength)

                if (clientHsSecret != null && serverHsSecret != null) {
                    callbacks.onNewEncryptionSecretsReady(TlsEncryptionLevel.HANDSHAKE, serverHsSecret, clientHsSecret, cipherSuiteInt)
                    handshakeKeysExtracted = true
                    println("JvmQuicKeyExtractionHelper: Reported HANDSHAKE keys. Cipher: $cipherSuiteString")
                }
            } catch (e: SSLException) { // exportKeyingMaterial throws SSLException if called too early or not supported
                 println("JvmQuicKeyExtractionHelper: Could not export HANDSHAKE secrets yet (or not supported): ${e.message}")
            } catch (e: IllegalStateException) { // Can be thrown if session is invalidated
                 println("JvmQuicKeyExtractionHelper: Could not export HANDSHAKE secrets due to illegal state (session invalidated?): ${e.message}")
            }
        }

        if (!appKeysExtracted &&
            (currentLevelAttempt == TlsEncryptionLevel.APPLICATION_DATA || engine.handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED ||
             (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING && handshakeKeysExtracted && session.isValid) ) ) {
             // Try to extract application keys when handshake is finished or we are told we are at app data level.
            try {
                val clientAppSecret = session.exportKeyingMaterial(QUIC_EXPORTER_LABEL_CLIENT_APPLICATION_TRAFFIC_SECRET, null, secretLength)
                val serverAppSecret = session.exportKeyingMaterial(QUIC_EXPORTER_LABEL_SERVER_APPLICATION_TRAFFIC_SECRET, null, secretLength)

                if (clientAppSecret != null && serverAppSecret != null) {
                    callbacks.onNewEncryptionSecretsReady(TlsEncryptionLevel.APPLICATION_DATA, serverAppSecret, clientAppSecret, cipherSuiteInt)
                    appKeysExtracted = true
                    println("JvmQuicKeyExtractionHelper: Reported APPLICATION_DATA keys. Cipher: $cipherSuiteString")

                    // Attempt to get peer certificate after handshake is complete and app keys are ready
                    try {
                        val peerCerts = session.peerCertificates
                        if (peerCerts.isNotEmpty()) {
                            callbacks.onPeerCertificateReceived(peerCerts[0].encoded)
                        } else {
                            callbacks.onPeerCertificateReceived(null)
                        }
                    } catch (e: SSLPeerUnverifiedException) {
                        callbacks.onPeerCertificateReceived(null) // No certificate or not verified
                    }

                } else if (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED) {
                    // If handshake is finished but we couldn't get app secrets, this is an issue.
                    println("JvmQuicKeyExtractionHelper: Handshake FINISHED but failed to export application secrets.")
                    // callbacks.onTlsAlertToSend(TlsAlert(2, 80, "Failed to derive application secrets post-handshake"))
                }
            } catch (e: SSLException) {
                 println("JvmQuicKeyExtractionHelper: Could not export APPLICATION secrets yet (or not supported): ${e.message}")
            } catch (e: IllegalStateException) {
                 println("JvmQuicKeyExtractionHelper: Could not export APPLICATION secrets due to illegal state (session invalidated?): ${e.message}")
            }
        }
    }

    private fun mapCipherSuiteNameToIANA(name: String?): Int {
        return when (name) {
            "TLS_AES_128_GCM_SHA256" -> 0x1301
            "TLS_AES_256_GCM_SHA384" -> 0x1302
            "TLS_CHACHA20_POLY1305_SHA256" -> 0x1303
            // TODO: Add mappings for other relevant TLS 1.3 cipher suites if needed
            else -> 0 // Unknown or unsupported
        }
    }
}

// Helper to map SSLException to a default alert description code (more specific mapping may be needed)
private fun SSLException.toAlertDescription(): Byte {
    // This is a very basic mapping. Real mapping is complex.
    // See RFC 8446 Appendix B.2. for alert descriptions.
    // Common ones: handshake_failure (40), illegal_parameter (47), internal_error (80)
    return when (this) {
        is SSLHandshakeException -> 40 // handshake_failure
        is SSLKeyException -> 40 // handshake_failure (often related to key issues)
        is SSLProtocolException -> 47 // illegal_parameter or a more specific one
        else -> 80 // internal_error
    }
}
