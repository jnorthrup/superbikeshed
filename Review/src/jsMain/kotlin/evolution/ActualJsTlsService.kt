package evolution

import borg.trikeshed.lib.Series
import borg.trikeshed.net.tls.* // Imports TlsService, TlsConnection, TlsHandshakeCallbacks, etc.
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

actual class ActualJsTlsService(
    private val parentCoroutineContext: CoroutineContext
) : TlsService, CoroutineScope {

    override val coroutineContext: CoroutineContext = parentCoroutineContext + SupervisorJob() + CoroutineName("ActualJsTlsService")

    actual override suspend fun startClientHandshake(
        hostname: String,
        alpnProtocols: Series<String>,
        quicTransportParams: ByteArray,
        callbacks: TlsHandshakeCallbacks,
        clientCertificateChainDer: Series<ByteArray>?,
        clientPrivateKeyDer: ByteArray?
    ): Result<TlsConnection> = withContext(Dispatchers.Default) { // Default for JS usually means main thread or worker.

        if (clientCertificateChainDer != null || clientPrivateKeyDer != null) {
            console.warn("ActualJsTlsService: Client certificate authentication parameters were provided, but are not supported for QUIC/TLS via Web APIs in standard browser environments. These parameters will be ignored.")
            // For Node.js, one might use the 'tls' module which supports client certs:
            // const tls = require('tls');
            // const options = {
            //   host: hostname,
            //   port: 443, // QUIC port, though Node's tls is for TCP TLS
            //   key: Buffer.from(clientPrivateKeyDer),
            //   cert: Buffer.from(clientCertificateChainDer.first()), // and chain
            //   ALPNProtocols: alpnProtocols.toList().toTypedArray(),
            //   servername: hostname
            // };
            // const socket = tls.connect(options, () => { /* connected */ });
            // Interfacing this with memory BIOs for QUIC's CRYPTO stream is non-trivial.
        }

        // Browser Web APIs (like WebSocket, WebTransport when it becomes more available for QUIC,
        // or direct UDP via WebRTC DataChannels) do not typically allow programmatic setting of
        // client certificates for the underlying TLS handshake in the same way native sockets do.
        // The browser manages its own certificate stores and UI for client cert selection,
        // usually for HTTPS connections initiated by the browser itself.

        // This implementation will therefore proceed without client certificate authentication
        // and act as if a standard, anonymous client TLS handshake is being performed.
        // The actual TLS handshake would need to be provided by a hypothetical underlying
        // JavaScript QUIC implementation that this TlsService would wrap.
        // Since we don't have such an underlying JS QUIC stack here that does TLS,
        // this service cannot fully implement the TLS handshake for JS in a way that
        // integrates with QUIC CRYPTO frames.

        // For now, return a failure or a dummy connection indicating lack of full TLS support here.
        // This highlights that a full KMP TlsService for JS QUIC requires a JS QUIC stack.

        // To allow QuicTlsHandler to exist and common code to compile,
        // we need to return a TlsConnection. Let's return a dummy one that
        // immediately calls onHandshakeComplete or an error.
        // A more robust solution would be to have the JS target not compile/link this if not supported.

        // Simulate an immediate handshake failure or an unsupported operation.
        // Assuming TlsAlert.description is Byte for alert codes. 80 is internal_error.
        callbacks.onTlsAlertToSend(TlsAlert(2.toByte(), 80.toByte(), "TLS handshake not supported in this JS environment without a backing QUIC/TLS stack."))
        return@withContext Result.failure(UnsupportedOperationException("TLS handshake with client certs (or any TLS handshake via this service) is not fully supported in JS for QUIC without a backing JS QUIC/TLS library."))
    }

    fun close() { // Optional: if TlsService common interface adds close()
        cancel("ActualJsTlsService closed")
    }
}

// Helper to convert Series to List<String> for JS array needs if any
// This should ideally be part of Series definition or a common utility if Series is not directly usable as List.
// For the purpose of this file, if alpnProtocols needs to be List for some JS API.
private fun Series<String>.toList(): List<String> {
    val list = mutableListOf<String>()
    // Assuming Series has a forEach or can be converted to something iterable
    // If Series is a typealias for List, this is direct.
    // If Series is its own class, it needs an iteration mechanism.
    // For now, let's assume it's directly iterable or has a .map {} .toList() chain available
    // This is a placeholder, actual conversion depends on Series<T> API.
    // If Series is `List<T>` itself (not recommended by CLAUDE.md but for placeholder):
    // return this
    // If it's custom, we need a way to iterate. Let's assume forEach for now.
    // this.forEach { list.add(it) } // This line would require Series to have forEach.
    // As a robust placeholder, if Series has a .toList() method itself:
    // return this.toList()
    // Given the context of previous tasks, Series is a distinct type.
    // Let's assume a conceptual iteration or conversion:
    try {
        (this as? Collection<String>)?.let { return it.toList() } // If it happens to be a collection
        // Fallback or specific Series iteration if available.
        // For this example, let's assume it's not directly iterable without more info on Series API.
        // The ALPN protocols are used in comments above for Node.js, not directly by the current dummy logic.
    } catch (e: Exception) {
        // Ignore if conversion is not straightforward for this dummy implementation.
    }
    return list // Returns empty list if no simple conversion path for this dummy.
}
