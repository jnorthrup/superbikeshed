package borg.trikeshed.nio // As per CLAUDE.md for nio overrides

import borg.trikeshed.lib.Series
import borg.trikeshed.net.tls.* // Imports TlsService, TlsConnection, TlsHandshakeCallbacks, etc.
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.*
import kotlin.coroutines.CoroutineContext

actual class ActualJvmTlsService(
    private val parentCoroutineContext: CoroutineContext
) : TlsService, CoroutineScope {
    // Inherit CoroutineScope for managing SSLEngine tasks if needed, or use Dispatchers.IO directly.
    override val coroutineContext: CoroutineContext = parentCoroutineContext + SupervisorJob() + CoroutineName("ActualJvmTlsService")

    actual override suspend fun startClientHandshake(
        hostname: String,
        alpnProtocols: Series<String>,
        quicTransportParams: ByteArray, // QUIC TP for TLS extension
        callbacks: TlsHandshakeCallbacks,
        clientCertificateChainDer: Series<ByteArray>?,
        clientPrivateKeyDer: ByteArray?
    ): Result<TlsConnection> = withContext(Dispatchers.IO) {
        try {
            val sslContext = SSLContext.getInstance("TLSv1.3")

            val keyManagers: Array<KeyManager>? = if (clientCertificateChainDer != null && clientPrivateKeyDer != null && clientCertificateChainDer.isNotEmpty()) {
                val certFactory = CertificateFactory.getInstance("X.509")
                val certificates = clientCertificateChainDer.map { derBytes ->
                    certFactory.generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate
                }.toList().toTypedArray() // toList() is explicit here before toTypedArray for Series

                val pkcs8KeySpec = PKCS8EncodedKeySpec(clientPrivateKeyDer)
                // Try common key algorithms; may need to be more specific or configurable
                val keyFactory = try {
                    KeyFactory.getInstance("RSA")
                } catch (e: Exception) {
                    try { KeyFactory.getInstance("EC") }
                    catch (e2: Exception) { throw IllegalStateException("Failed to get KeyFactory for RSA or EC", e2)}
                }
                val privateKey = keyFactory.generatePrivate(pkcs8KeySpec) as PrivateKey

                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()) // Or "JKS", "PKCS12"
                keyStore.load(null, null) // Initialize empty keystore
                // Use a unique alias; "client" is common for a single client cert
                val keyStorePassword = "temp_password".toCharArray() // Password for keystore entry, can be arbitrary for in-memory
                keyStore.setKeyEntry("client_key", privateKey, keyStorePassword, certificates)

                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(keyStore, keyStorePassword)
                kmf.keyManagers
            } else {
                null // Use default key managers (none for client auth)
            }

            // Using default trust managers for now. For production, a custom TrustManager
            // might be needed to validate server certs against specific CAs.
            sslContext.init(keyManagers, null /* default TrustManagers */, null /* default SecureRandom */)

            val engine = sslContext.createSSLEngine(hostname, -1) // Port -1 as SSLEngine doesn't connect
            engine.useClientMode = true

            // ALPN Setup
            val sslParameters = engine.sslParameters ?: SSLParameters()
            if (alpnProtocols.isNotEmpty()) {
                 // SSLEngine.setHandshakeApplicationProtocolSelector for server mode
                 // For client mode, setApplicationProtocols
                sslParameters.applicationProtocols = alpnProtocols.map { it }.toList().toTypedArray() // Convert Series to Array
            }
            // SNI Setup
            if (hostname.isNotEmpty()) {
                val sniHostName = SNIHostName(hostname)
                sslParameters.serverNames = listOf(sniHostName)
            }
            // QUIC Transport Parameters Extension (custom handling needed)
            // Standard SSLEngine does not have a direct way to set arbitrary TLS extensions like QUIC TP.
            // This usually requires lower-level TLS libraries or custom JSSE providers.
            // For now, this parameter is captured but not directly applied to SSLEngine here.
            // The TlsHandshakeCallbacks would be responsible for embedding it in ClientHello if TlsService can't.
            // Or, if the TlsService is expected to handle it, this actual impl might need extension.
            // For this step, we acknowledge quicTransportParams but don't wire it into SSLEngine.
            // A more advanced JSSE setup (e.g. with BouncyCastle as a provider) might allow this.

            engine.sslParameters = sslParameters
            engine.beginHandshake()

            Result.success(JvmTlsConnection(engine, callbacks, coroutineContext))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class JvmTlsConnection(
    private val engine: SSLEngine,
    private val callbacks: TlsHandshakeCallbacks,
    private val scopeContext: CoroutineContext // Scope for running engine tasks
) : TlsConnection, CoroutineScope {

    override val coroutineContext: CoroutineContext = scopeContext + SupervisorJob() + CoroutineName("JvmTlsConnection-${engine.hashCode()}")

    private val appSendBuffer: ByteBuffer = ByteBuffer.allocate(engine.session.applicationBufferSize)
    private val netSendBuffer: ByteBuffer = ByteBuffer.allocate(engine.session.packetBufferSize)
    private val appRecvBuffer: ByteBuffer = ByteBuffer.allocate(engine.session.applicationBufferSize)
    private val netRecvBuffer: ByteBuffer = ByteBuffer.allocate(engine.session.packetBufferSize)

    init {
        // Start a task to run SSLEngine operations if it's not driven by external read/write calls.
        // This depends on how processHandshakeData is used.
        // If processHandshakeData directly drives engine.unwrap, then this task might just handle engine.wrap.
        // For now, let processHandshakeData drive the reading side.
        // Sending data (onHandshakeDataToSend) will be driven by engine.wrap calls.

        // Initial run of tasks to see if SSLEngine produces initial ClientHello data
        runEngineTasks()
    }

    private fun runEngineTasks() {
        // This function should be called after every unwrap() and wrap() to handle SSLEngine's state.
        // It needs to handle different SSLEngineResult.HandshakeStatus values.
        // Simplified loop for now.
        var tasksToRun = true
        while (tasksToRun && isActive) { // Check coroutine isActive
            tasksToRun = false
            when (engine.handshakeStatus) {
                SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                    engine.delegatedTask?.let {
                        it.run()
                        tasksToRun = true
                    }
                }
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    netSendBuffer.clear()
                    val result = engine.wrap(appSendBuffer.flip(), netSendBuffer) // appSendBuffer is empty for handshake start
                    appSendBuffer.compact()
                    netSendBuffer.flip()
                    if (result.bytesProduced() > 0) {
                        val dataToSend = ByteArray(netSendBuffer.remaining())
                        netSendBuffer.get(dataToSend)
                        callbacks.onHandshakeDataToSend(dataToSend, mapTlsEncLevel(engine.session.protocol)) // Protocol might not be set yet
                    }
                    if (result.handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED) {
                        callbacks.onHandshakeComplete(engine.applicationProtocol)
                        tasksToRun = false // Handshake finished
                    } else if (result.status == SSLEngineResult.Status.CLOSED) {
                         closeConnection(true) // Engine closed during wrap
                         tasksToRun = false
                    } else {
                        tasksToRun = true // May need more tasks or unwrap
                    }
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP,
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN -> {
                    // Waiting for data from peer, driven by processHandshakeData.
                    // If processHandshakeData was called and there's still NEED_UNWRAP, it might mean more data is needed.
                    tasksToRun = false // Stop task loop, wait for external data via processHandshakeData
                }
                SSLEngineResult.HandshakeStatus.FINISHED -> {
                    callbacks.onHandshakeComplete(engine.applicationProtocol)
                    // Potentially call onNewEncryptionSecretsReady if SSLEngine signals this way (often implicit)
                    tasksToRun = false
                }
                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> {
                    // Handshake is done or not started. If it was finished, onHandshakeComplete should have been called.
                    tasksToRun = false
                }
                null -> { // Should not happen
                    tasksToRun = false
                }
            }
        }
    }


    override fun processHandshakeData(data: ByteArray, level: TlsEncryptionLevel): Result<Unit> {
        if (engine.isInboundDone) return Result.failure(IllegalStateException("SSLEngine inbound is done."))
        netRecvBuffer.put(data) // Assume data is a single TLS record or fragment

        var processResult: Result<Unit> = Result.success(Unit)

        // Loop to consume all data in netRecvBuffer and handle SSLEngine tasks
        while (netRecvBuffer.position() > 0 && engine.handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED && !engine.isInboundDone && isActive) {
            netRecvBuffer.flip()
            val result = try {
                 engine.unwrap(netRecvBuffer, appRecvBuffer)
            } catch (e: SSLException) {
                callbacks.onTlsAlertToSend(TlsAlert(2, TlsAlertType.ILLEGAL_PARAMETER.code, e.message)) // Example mapping
                closeConnection(false)
                return Result.failure(e)
            }
            netRecvBuffer.compact()

            when (result.status) {
                SSLEngineResult.Status.OK -> {
                    if (appRecvBuffer.position() > 0) {
                        // Decrypted application data during handshake (e.g. NewSessionTicket, not typical for QUIC CRYPTO)
                        // Or handshake data that was buffered.
                        // For QUIC, CRYPTO frames only carry handshake data.
                        // This data is typically handled by SSLEngine internally for handshake.
                        // If it's actual app data, it's an error here.
                        appRecvBuffer.clear() // Discard for now
                    }
                }
                SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                    // Need more data from peer
                    break // Exit loop, wait for more data
                }
                SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                     // appRecvBuffer is too small. This is a setup error.
                    processResult = Result.failure(IllegalStateException("SSLEngine appRecvBuffer overflow during unwrap."))
                    closeConnection(false)
                    break
                }
                SSLEngineResult.Status.CLOSED -> {
                    closeConnection(true) // Peer closed gracefully or error during unwrap
                    break
                }
                null -> { // Should not happen
                    processResult = Result.failure(IllegalStateException("SSLEngine unwrap result status is null."))
                    closeConnection(false)
                    break
                }
            }
            // After unwrap, run delegated tasks or other operations based on handshake status
            runEngineTasks()
            if (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED || engine.isInboundDone) break
        }

        // Check if secrets are ready (this is often implicit with SSLEngine, keys are used internally)
        // TlsHandshakeCallbacks.onNewEncryptionSecretsReady needs to be called by QUIC specific logic
        // that extracts secrets from the TLS stack if not using direct SSLEngine keying material.
        // For SSLEngine, keying material for QUIC would typically be derived using HKDF from
        // TLS exporter (RFC 5705, RFC 8446 Section 7.5) if SSLEngine supports exporters.
        // This part is complex and often requires custom JSSE provider or BouncyCastle.
        // For now, we assume the SSLEngine handles encryption internally for TLS records,
        // and QUIC layer would need to be informed of handshake completion to switch its own keys.
        // The `onNewEncryptionSecretsReady` is hard to trigger directly from SSLEngine without exporters.
        // This TlsService model might need adjustment for how QUIC keys are derived post-SSLEngine handshake.
        // For now, onHandshakeComplete is the main signal.

        return processResult
    }

    override fun providePskTicket(ticket: ByteArray) {
        // SSLEngine session resumption is typically configured on SSLContext/SSLEngine setup,
        // not by feeding a ticket mid-handshake this way. This is more for custom TLS stacks.
        // No-op for standard SSLEngine here.
    }

    override fun close() {
        closeConnection(true)
        cancel("JvmTlsConnection closed") // Cancel its own scope
    }

    private fun closeConnection(graceful: Boolean) {
        if (!engine.isOutboundDone) {
            engine.closeOutbound()
        }
        if (graceful && !engine.isInboundDone) {
            try {
                // Attempt to process any close_notify from peer
                // This might require more data if not already received.
                // If engine.unwrap throws during this, it's okay.
                if (netRecvBuffer.position() > 0) { // If there's pending data
                     netRecvBuffer.flip()
                     engine.unwrap(netRecvBuffer, appRecvBuffer) // Drain and process
                     netRecvBuffer.compact()
                }
                engine.closeInbound() // May throw if peer didn't send close_notify
            } catch (e: SSLException) {
                // Ignore, likely peer closed without proper notify.
            }
        }
        // Release SSLEngine resources if any (not standard in SSLEngine, happens on GC)
    }

    // Helper to map SSLEngine's protocol (once negotiated) to TlsEncryptionLevel for onHandshakeDataToSend
    // This is a simplification. SSLEngine uses handshake keys then application keys.
    private fun mapTlsEncLevel(protocol: String?): TlsEncryptionLevel {
        // During handshake, SSLEngine wraps data that should be sent at Handshake level.
        // After handshake, it's Application level (but QUIC uses CRYPTO frames only for handshake).
        return if (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING &&
                   engine.session.isValid && (protocol?.startsWith("TLS") == true)) {
            TlsEncryptionLevel.APPLICATION_DATA // Should not happen for QUIC CRYPTO frames
        } else {
            TlsEncryptionLevel.HANDSHAKE
        }
    }
}

// Placeholder for TlsAlertType mapping if needed (subset)
object TlsAlertType {
    const val CLOSE_NOTIFY: Byte = 0
    const val UNEXPECTED_MESSAGE: Byte = 10
    const val BAD_RECORD_MAC: Byte = 20
    const val HANDSHAKE_FAILURE: Byte = 40
    const val ILLEGAL_PARAMETER: Byte = 47
    // ... other alert types
}
