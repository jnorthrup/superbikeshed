package borg.trikeshed.curl

import borg.trikeshed.io.network.NetworkAddress // Now Join<String, Int>
import borg.trikeshed.io.network.QuicNetworkService
import borg.trikeshed.io.network.QuicNetworkServiceKey
import borg.trikeshed.lib.Series
import borg.trikeshed.net.quic.QuicConnection
import borg.trikeshed.net.quic.QuicPacketProcessor // For packet processing
// import borg.trikeshed.net.quic.QuicTlsHandler // For TLS - TlsHandler is created by QuicConnection.startTlsHandshake
import borg.trikeshed.net.quic.crypto.QuicCryptoUtils
import borg.trikeshed.net.quic.QuicConnectionManager
import borg.trikeshed.net.quic.QuicFrame
import borg.trikeshed.net.quic.QuicFrameParser
import borg.trikeshed.net.quic.QuicPacketType
import borg.trikeshed.net.quic.QuicPacketUtils // For creating packets
import borg.trikeshed.net.quic.QuicFrameType // For specifying frame type in sendData
import borg.trikeshed.net.quic.TransportErrorCode
import borg.trikeshed.net.quic.EncryptionLevel // For determining protection level for received packets
import borg.trikeshed.io.network.DatagramPacket // This is Join<ByteArray, Join<NetworkAddress, Int>>
import borg.trikeshed.io.network.data // DatagramPacket extensions
import borg.trikeshed.io.network.address // DatagramPacket extensions
import borg.trikeshed.io.network.length // DatagramPacket extensions
import borg.trikeshed.lib.first // For NetworkAddress.first
import borg.trikeshed.lib.second // For NetworkAddress.second
import borg.trikeshed.lib.isEmpty // For Series.isEmpty
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.collections.Map // Standard Map for connection pooling for now
import kotlin.collections.MutableMap
import kotlin.collections.mutableMapOf
import kotlin.Result // Standard Result

actual class ActualJvmQuicConnectionProvider(
    private val parentCoroutineContext: CoroutineContext
) : QuicConnectionProvider, CoroutineScope {

    override val coroutineContext: CoroutineContext = parentCoroutineContext + SupervisorJob() + CoroutineName("ActualJvmQuicConnectionProvider")

    private val quicNetworkService: QuicNetworkService = coroutineContext[QuicNetworkServiceKey]
        ?: throw IllegalStateException("QuicNetworkService not found in CoroutineContext for JvmQuicConnectionProvider.")

    private val activeConnections: MutableMap<String, QuicConnection> = mutableMapOf()
    private val connectionJobs: MutableMap<String, Job> = mutableMapOf()
    // Conceptual: Store remote address per connection when resolved in startConnectionReceiveLoop
    private val connectionRemoteAddresses: MutableMap<QuicConnection, NetworkAddress> = mutableMapOf()


    actual override suspend fun getConnection(
        host: String,
        port: Int,
        scheme: String, // Typically "https" for QUIC/H3
        clientCertificateChainDer: Series<ByteArray>?,
        clientPrivateKeyDer: ByteArray?
    ): Result<QuicConnection> {
        // Key for connection map, could be more sophisticated
        val connectionKey = "$host:$port"

        activeConnections[connectionKey]?.let { existingConn ->
            connectionJobs[connectionKey]?.let { job ->
                if (job.isActive && existingConn.state != borg.trikeshed.net.quic.QuicConnectionStateEnum.CLOSED && existingConn.state != borg.trikeshed.net.quic.QuicConnectionStateEnum.CLOSING) { // Basic active check
                    println("Reusing existing connection for $connectionKey")
                    return Result.success(existingConn)
                } else {
                    // Job not active or connection is closed/closing, remove stale entries
                    activeConnections.remove(connectionKey)
                    connectionJobs.remove(connectionKey)
                }
            } ?: run {
                 // No job for this connection, inconsistent state, remove
                 activeConnections.remove(connectionKey)
            }
        }

        println("Creating new connection for $connectionKey")
        val newQuicConnection = QuicConnection.newClientConnection()
        newQuicConnection.expectedServerName = host

        // TODO: Store Client Cert/Key in QuicConnection if fields are added
        // if (clientCertificateChainDer != null && clientPrivateKeyDer != null) {
        //    newQuicConnection.clientCertChainDer = clientCertificateChainDer
        //    newQuicConnection.clientPrivateKeyDer = clientPrivateKeyDer
        // }

        // TODO: Set trusted CA for newQuicConnection if available globally or per request

        // Each connection likely needs its own "socket" session via QuicNetworkService.
        // The QuicNetworkService itself is a singleton, but its methods like bind()
        // would establish a new underlying socket context.

        // This is a simplified flow. A real provider would manage a receive loop per bound socket (or per connection CID on a shared socket).
        // For now, assume that starting the TLS handshake effectively "starts" the connection process.
        // The actual send/receive loop would be managed by the code that *uses* this QuicConnection,
        // for example, within QuicCurlImpl after getting the connection.
        // The QuicConnectionProvider's role here is primarily to set up the QuicConnection object
        // and potentially initiate the bind on the QuicNetworkService if it manages the socket per connection.

        // If QuicNetworkService.bind() is per "connection session":
        // val localAddress = quicNetworkService.bind(null) // Bind for this connection
        // Then this localAddress and the service need to be associated with NewQuicConnection
        // so it can send/receive. This implies QuicConnection might need a QuicNetworkService instance or a "socket handle".

        // For this sketch, we'll assume that the `QuicConnection` object is prepared,
        // and the actual network operations (bind, send, receive loop) will be
        // initiated by the consumer of this QuicConnection (e.g. QuicCurlImpl's request execution logic)
        // using a QuicNetworkService instance available in its context.
        // This makes the provider simpler, it just creates/manages QuicConnection data objects.

        // However, if the provider is meant to also run the receive loop, it's more complex.
        // Let's assume a model where the provider sets up the QuicConnection, and then
        // a separate entity (or the QuicConnection itself via methods) runs its network loop.

        // For now, just create and return the QuicConnection object.
        // The actual binding and receive loop will be handled by QuicCurlImpl using this connection
        // and the QuicNetworkService from its own context.
        // This means QuicConnectionProvider doesn't directly use QuicNetworkService.bind/receive here,
        // but ensures the QuicConnection is ready. This might be too simplistic.

        // CLARIFICATION: The provider *should* likely manage the socket and its receive loop.
        // Let's try to sketch that.

        return try {
            // This is still simplified. A real provider would have a robust connection setup.
            // The bind() on networkService might be done here, and a receive loop started.
            // For now, we just return a new connection object. The refactoring will be iterative.
            // The critical part is that *if* it were to do I/O, it would use quicNetworkService.

            // For the purpose of this refactoring step, the main goal is to establish
            // that an `actual QuicConnectionProvider` exists and *would* use QuicNetworkService.
            // The detailed internal logic of managing connections and receive loops is a larger task.

            // Call to a new (conceptual) private suspend function to start its receive loop
            val receiveLoopJob = startConnectionReceiveLoop(
                newQuicConnection,
                host,
                port,
                clientCertificateChainDer,
                clientPrivateKeyDer
            )

            activeConnections[connectionKey] = newQuicConnection
            connectionJobs[connectionKey] = receiveLoopJob

            Result.success(newQuicConnection)
        } catch (e: Exception) {
            Result.failure(QuicCurlException("Failed to establish connection for $connectionKey", cause = e))
        }
    }

    actual override suspend fun releaseConnection(connection: QuicConnection) { // Made suspend
        // Find the key associated with this connection object
        val connectionKey = activeConnections.entries.find { it.value == connection }?.key

        if (connectionKey != null) {
            val job = connectionJobs.remove(connectionKey)
            try {
                job?.cancelAndJoin() // Graceful cancellation
            } catch (e: Exception) {
                println("Exception during job cancellation for $connectionKey: ${e.message}")
            }

            activeConnections.remove(connectionKey)
            connectionRemoteAddresses.remove(connection) // Remove by QuicConnection object key

            println("Released connection for key: $connectionKey")
            // TODO: connection.closeInternalState() // Conceptual: tell QuicConnection it's being closed
        } else {
            println("Attempted to release a connection not actively managed: ${connection.clientId.toHexString()}")
        }
    }

    actual override suspend fun closeAll() {
        println("Closing all connections in JvmQuicConnectionProvider.")
        val jobsToCancel = connectionJobs.values.toList() // Copy to avoid CME
        jobsToCancel.forEach { job ->
            try {
                job.cancelAndJoin()
            } catch (e: Exception) {
                println("Exception while cancelling a connection job: ${e.message}")
                // Continue to cancel other jobs
            }
        }
        connectionJobs.clear()
        activeConnections.clear()
        connectionRemoteAddresses.clear() // Clear this map too

        // Cancel the provider's own scope, which cancels any remaining children not covered by connectionJobs
        // (e.g. if some other tasks were launched in this scope)
        // and signals that the provider itself is shutting down.
        try {
            // coroutineContext.cancel() // This cancels the provider's scope itself.
            // We need to use the Job from the context.
            coroutineContext[Job]?.cancelAndJoin() // More specific: cancel the SupervisorJob part
        } catch (e: Exception) {
            println("Exception while cancelling provider scope: ${e.message}")
        }
        println("JvmQuicConnectionProvider closed.")
    }

    private fun startConnectionReceiveLoop( // Removed suspend as it launches a coroutine
        connection: QuicConnection,
        targetHost: String,
        targetPort: Int,
        clientCertificateChainDer: Series<ByteArray>?, // These are for TlsHandler setup by QuicCurlImpl
        clientPrivateKeyDer: ByteArray?
    ): Job {
        return launch { // Launch in the provider's CoroutineScope
            val localAddress = try {
                quicNetworkService.bind(null) // Bind to an ephemeral port
            } catch (e: Exception) {
                println("Error binding socket for $targetHost:$targetPort: ${e.message}")
                activeConnections.remove("$targetHost:$targetPort")
                connectionJobs.remove("$targetHost:$targetPort")
                // TODO: Signal error to the connection object itself if it has a mechanism
                // connection.closeWithError(TransportErrorCode.INTERNAL_ERROR.value, "Socket bind failed")
                return@launch
            }
            // TODO: Store localAddress in QuicConnection if it has a field, or pass to relevant methods.
            // connection.localSocketAddress = localAddress
            println("Receive loop for $targetHost:$targetPort bound to ${localAddress.first}:${localAddress.second}")

            val connectionManager = QuicConnectionManager(connection, QuicConnectionManager.ConnectionRole.CLIENT)
            val cryptoUtils = QuicCryptoUtils(this.coroutineContext) // Use Job's context for CCEK

            try {
                connection.deriveInitialSecrets(cryptoUtils, connectionManager)
            } catch (e: Exception) {
                println("Error deriving initial secrets for $targetHost:$targetPort: ${e.message}")
                activeConnections.remove("$targetHost:$targetPort"); connectionJobs.remove("$targetHost:$targetPort"); quicNetworkService.close()
                return@launch
            }

            val resolvedRemoteAddresses = quicNetworkService.resolve(targetHost, targetPort)
            if (resolvedRemoteAddresses.isEmpty()) {
                println("Error resolving $targetHost:$targetPort")
                activeConnections.remove("$targetHost:$targetPort"); connectionJobs.remove("$targetHost:$targetPort"); quicNetworkService.close()
                return@launch
            }
            val remoteAddress = resolvedRemoteAddresses.first()!! // Assuming Series.first() gives non-null if not empty
            connectionRemoteAddresses[connection] = remoteAddress // Store for sending

            // Note: The TlsHandler is set up and ClientHello is initiated by QuicCurlImpl *after* getConnection returns.
            // This loop primarily handles responses and subsequent packets.
            // QuicCurlImpl needs to obtain the TlsHandler from the QuicConnection after it calls connection.startTlsHandshake.

            try {
                while (isActive) { // Loop while the coroutine is active
                    val datagram = quicNetworkService.receive(2048) // Buffer size

                    // TODO: This is a critical part. The TlsHandler is created/managed by QuicConnection.startTlsHandshake,
                    // which is called by QuicCurlImpl. The receive loop needs access to this handler.
                    // This implies QuicConnection must store its TlsHandler.
                    // val currentTlsHandler = connection.tlsHandler // Conceptual: get it from QuicConnection
                    // For now, this part cannot be fully implemented without modifying QuicConnection.
                    // We will proceed with a placeholder for packet processing.
                    val tempTlsHandlerForTest: borg.trikeshed.net.quic.QuicTlsHandler? = null // Placeholder!

                    val expectedDestCid = connection.clientId
                    // Determining packet type and unprotection level is complex and state-dependent.
                    // This is a major simplification.
                    val currentPacketTypeForParsing = if (connectionManager.isHandshakeConfirmed()) borg.trikeshed.net.quic.QuicPacketType.INITIAL /* Should be 1-RTT type */ else borg.trikeshed.net.quic.QuicPacketType.INITIAL

                    val unprotectedPacket = QuicPacketProcessor.deserializeAndUnprotectPacket(
                        this.coroutineContext, // Job's context, should have crypto services
                        datagram.data.copyOfRange(0, datagram.length), // Use actual length from datagram
                        connectionManager,
                        currentPacketTypeForParsing,
                        expectedDestCid
                    )

                    if (unprotectedPacket != null) {
                        val frames = QuicFrameParser.parseFrames(unprotectedPacket.unprotectedPayload, unprotectedPacket.header.type)
                        frames.forEach { frame ->
                            when (frame) {
                                is QuicFrame.CryptoFrame -> {
                                    println("Provider Loop: Received Crypto Frame, offset ${frame.offset}, len ${frame.data.size}")
                                    // This is where `currentTlsHandler.processIncomingCryptoData` would be called.
                                    // tempTlsHandlerForTest?.processIncomingCryptoData(frame.data, connectionManager.getReceiveEncryptionLevel())
                                }
                                is QuicFrame.AckFrame -> {
                                    println("Provider Loop: Received Ack Frame for PN ${frame.largestAcked}")
                                    connectionManager.recordPacketAckedByPeer(frame)
                                }
                                is QuicFrame.ConnectionCloseFrame -> {
                                    println("Provider Loop: Received ConnectionClose: ${frame.reasonPhrase}")
                                    // connection.closeWithError(frame.errorCode, frame.reasonPhrase) // Conceptual
                                    this.coroutineContext.cancel() // Stop this connection's loop
                                }
                                // TODO: Handle other frame types (Stream, Ping, etc.) that affect connection state
                                // Stream frames would be routed to Http3StreamHandler by QuicCurlImpl if this loop passes them up.
                                else -> println("Provider Loop: Received other frame: ${frame::class.simpleName}")
                            }
                        }
                    } else {
                        println("Provider Loop: Failed to unprotect packet from ${datagram.address.first}:${datagram.address.second}")
                    }
                }
            } catch (e: CancellationException) {
                println("Receive loop for $targetHost:$targetPort cancelled.")
                // Propagate cancellation or handle as normal coroutine cancellation
                throw e
            } catch (e: Exception) {
                println("Error in receive loop for $targetHost:$targetPort: ${e.message}")
                // connection.closeWithError(TransportErrorCode.INTERNAL_ERROR.value, "Receive loop error") // Conceptual
            } finally {
                println("Closing network service session for $targetHost:$targetPort")
                quicNetworkService.close() // Close the "session" for this connection
                activeConnections.remove("$targetHost:$targetPort")
                connectionJobs.remove("$targetHost:$targetPort")
                // TODO: connection.close() // Ensure QuicConnection internal state is also marked closed.
            }
        }
    }

    // Called by TlsHandler (for CRYPTO data) or StreamManager (for STREAM data)
    // This is a simplified entry point for sending.
    // A more robust system might have separate methods for crypto vs stream, or a richer data type.
    suspend fun sendDataForConnection(
        connection: QuicConnection,
        payloadBytes: ByteArray, // Raw bytes for the frame's content
        encryptionLevel: EncryptionLevel,
        frameType: QuicFrameType, // To help construct the right QUIC packet and frame
        streamId: Long? = null, // For STREAM frames
        offset: Long? = null, // For CRYPTO or STREAM frames
        isFin: Boolean = false // For STREAM frames
    ) {
        val connectionManager = QuicConnectionManager(connection, QuicConnectionManager.ConnectionRole.CLIENT) // Get or create
        val remoteAddress = connectionRemoteAddresses[connection] ?: run {
            println("Error: Remote address not found for connection ${connection.clientId.toHexString()}. Cannot send.")
            return
        }

        // 1. Construct QUIC Frame(s)
        val quicFrames: List<QuicFrame> = when (frameType) {
            QuicFrameType.CRYPTO -> listOf(QuicFrame.CryptoFrame(offset ?: 0L, payloadBytes))
            QuicFrameType.STREAM_BASE -> { // Assuming STREAM_BASE implies it's a STREAM frame
                if (streamId == null) {
                    println("Error: Stream ID is null for STREAM frame. Cannot send.")
                    return
                }
                listOf(QuicFrame.StreamFrame(streamId, offset ?: 0L, payloadBytes, isFin, true /* sendLengthExplicitly for now */))
            }
            // TODO: Add cases for other frame types that might be sent directly (e.g., PING, CONNECTION_CLOSE)
            else -> {
                println("Error: Unsupported frame type for direct sending: $frameType")
                return
            }
        }

        if (quicFrames.isEmpty()) {
            println("No frames to send for connection ${connection.clientId.toHexString()}.")
            return
        }

        // 2. Determine Packet Number and Type
        val packetNumber = connectionManager.getNextPacketNumberForEncryptionLevel(encryptionLevel).toULong()
        val packetType = QuicPacketUtils.determinePacketType(encryptionLevel, connectionManager.isHandshakeConfirmed())

        // 3. Create QuicPacket (using QuicPacketUtils or direct construction)
        // This needs SCID, DCID which should be available from `connection` object.
        // DCID for client-sent packets is typically connection.serverId (if established) or initial DCID chosen by client for server.
        // SCID is connection.clientId.
        val destinationCid = connection.serverId ?: connection.initialClientChosenDcId ?: connection.clientId // Simplified DCID selection
        val sourceCid = connection.clientId

        val quicPacket = QuicPacketUtils.createQuicPacket( // Conceptual helper
            packetType,
            destinationCid,
            sourceCid,
            packetNumber,
            quicFrames,
            connection // Potentially pass connection for more context if createQuicPacket needs it
        )

        // 4. Serialize and Protect Packet
        val protectedPacketBytes = QuicPacketProcessor.serializeAndProtectPacket(
            this.coroutineContext, // Provider's scope context, should have crypto services
            quicPacket,
            connectionManager,
            encryptionLevel
        )

        if (protectedPacketBytes != null) {
            // 5. Send via QuicNetworkService
            // The DatagramPacket is typealias DatagramPacket = Join<ByteArray, Join<NetworkAddress, Int>>
            // .data .address .length are extension properties
            val datagram = Join( // Constructing DatagramPacket using Join
                protectedPacketBytes,
                Join(remoteAddress, protectedPacketBytes.size)
            )
            try {
                quicNetworkService.send(datagram)
                connectionManager.recordPacketSent(packetNumber, protectedPacketBytes.size, quicFrames, encryptionLevel, quicFrames.any { it !is QuicFrame.AckFrame && it !is QuicFrame.PaddingFrame })
                println("Sent packet PN $packetNumber, Level $encryptionLevel, ${protectedPacketBytes.size} bytes to $remoteAddress")
            } catch (e: Exception) {
                println("Error sending packet for connection ${connection.clientId.toHexString()}: ${e.message}")
                // Handle send error, potentially close connection or mark path as failed
            }
        } else {
            println("Error: Failed to serialize/protect packet for PN $packetNumber, Level $encryptionLevel")
        }
    }
}

// Helper for CID to HexString if not already available globally
private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
