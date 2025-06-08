package borg.trikeshed.curl

import borg.trikeshed.io.network.QuicNetworkService
import borg.trikeshed.io.network.QuicNetworkServiceKey
import borg.trikeshed.lib.Series
import borg.trikeshed.net.quic.QuicConnection
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.collections.Map // Using standard Map for now
import kotlin.collections.MutableMap
import kotlin.collections.mutableMapOf
import kotlin.Result

actual class ActualNativeQuicConnectionProvider(
    private val parentCoroutineContext: CoroutineContext
) : QuicConnectionProvider, CoroutineScope {

    override val coroutineContext: CoroutineContext = parentCoroutineContext + SupervisorJob() + CoroutineName("ActualNativeQuicConnectionProvider")

    private val quicNetworkService: QuicNetworkService = coroutineContext[QuicNetworkServiceKey]
        ?: throw IllegalStateException("QuicNetworkService not found in CoroutineContext for NativeQuicConnectionProvider.")

    private val activeConnections: MutableMap<String, QuicConnection> = mutableMapOf()
    // private val connectionJobs: MutableMap<String, Job> = mutableMapOf() // For managing receive loops

    actual override suspend fun getConnection(
        host: String,
        port: Int,
        scheme: String,
        clientCertificateChainDer: Series<ByteArray>?,
        clientPrivateKeyDer: ByteArray?
    ): Result<QuicConnection> {
        val connectionKey = "$host:$port"
        // TODO: Implement connection pooling

        val newQuicConnection = QuicConnection.newClientConnection()
        newQuicConnection.expectedServerName = host
        // TODO: Configure trusted CAs, client certs for newQuicConnection

        // As with JVM, the detailed receive loop and management using
        // quicNetworkService (PosixQuicNetworkService) is a larger task.
        // This provider would use it here.
        activeConnections[connectionKey] = newQuicConnection
        return Result.success(newQuicConnection)
    }

    actual override fun releaseConnection(connection: QuicConnection) {
        // TODO: Implement release logic
    }

    actual override suspend fun closeAll() {
        // TODO: Close all connections
        cancel()
    }
}
