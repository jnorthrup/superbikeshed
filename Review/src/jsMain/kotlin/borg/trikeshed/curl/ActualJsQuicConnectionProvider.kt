package borg.trikeshed.curl

import borg.trikeshed.lib.Series
import borg.trikeshed.net.quic.QuicConnection
import kotlinx.coroutines.*
import kotlin.Result
import kotlin.coroutines.CoroutineContext


actual class ActualJsQuicConnectionProvider(
    private val parentCoroutineContext: CoroutineContext // May not be used if all ops are unsupported
) : QuicConnectionProvider, CoroutineScope {

    override val coroutineContext: CoroutineContext = parentCoroutineContext + SupervisorJob() + CoroutineName("ActualJsQuicConnectionProvider")

    actual override suspend fun getConnection(
        host: String,
        port: Int,
        scheme: String,
        clientCertificateChainDer: Series<ByteArray>?,
        clientPrivateKeyDer: ByteArray?
    ): Result<QuicConnection> {
        // QUIC client connections over raw UDP are generally not possible in browser JS.
        // If this were for Node.js with UDP module, it could be implemented.
        // Or if WebTransport becomes the QUIC interface, this provider would change.
        return Result.failure(UnsupportedOperationException("Direct QUIC connection provider not supported in this JavaScript environment."))
    }

    actual override fun releaseConnection(connection: QuicConnection) {
        // No-op or throw UnsupportedOperationException
    }

    actual override suspend fun closeAll() {
        // No-op or throw UnsupportedOperationException
        cancel()
    }
}
