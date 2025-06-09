package borg.trikeshed.curl

import borg.trikeshed.io.network.QuicNetworkService
import borg.trikeshed.io.network.QuicNetworkServiceKey
import borg.trikeshed.lib.Series
import borg.trikeshed.net.quic.QuicConnection
// Added imports
import borg.trikeshed.io.network.NetworkAddress
import borg.trikeshed.io.network.DatagramPacket // For typealias Join<ByteArray, Join<NetworkAddress, Int>>
import borg.trikeshed.net.quic.QuicConnectionManager
import borg.trikeshed.net.quic.QuicPacketProcessor
import borg.trikeshed.net.quic.QuicFrameParser
import borg.trikeshed.net.quic.QuicFrame
import borg.trikeshed.net.quic.crypto.QuicCryptoUtils
import borg.trikeshed.net.quic.EncryptionLevel
import borg.trikeshed.net.quic.TransportErrorCode
import borg.trikeshed.net.quic.QuicPacketType // For conceptual packet type determination
import borg.trikeshed.io.network.data // DatagramPacket extensions
import borg.trikeshed.io.network.address // DatagramPacket extensions
import borg.trikeshed.io.network.length // DatagramPacket extensions
import borg.trikeshed.lib.first // For NetworkAddress.first
import borg.trikeshed.lib.second // For NetworkAddress.second
import borg.trikeshed.lib.isEmpty // For Series.isEmpty
import borg.trikeshed.net.quic.QuicPacketUtils // For creating packets
import borg.trikeshed.net.quic.QuicFrameType // For specifying frame type in sendData
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import borg.trikeshed.lib.Join // For constructing DatagramPacket
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
    private val connectionJobs: MutableMap<String, Job> = mutableMapOf()
    private val connectionRemoteAddresses: MutableMap<QuicConnection, NetworkAddress> = mutableMapOf()


    actual override suspend fun getConnection(
        host: String,
        port: Int,
        scheme: String,
        clientCertificateChainDer: Series<ByteArray>?,
        clientPrivateKeyDer: ByteArray?
    ): Result<QuicConnection> {
        val connectionKey = "$host:$port"

        activeConnections[connectionKey]?.let { existingConn ->
            connectionJobs[connectionKey]?.let { job ->
                if (job.isActive && existingConn.state != borg.trikeshed.net.quic.QuicConnectionStateEnum.CLOSED && existingConn.state != borg.trikeshed.net.quic.QuicConnectionStateEnum.CLOSING) { // Basic active check
                    println("Reusing existing Native connection for $connectionKey")
                    return Result.success(existingConn)
                } else {
                    activeConnections.remove(connectionKey)
                    connectionJobs.remove(connectionKey)
                }
            } ?: run {
                 activeConnections.remove(connectionKey) // No job for this connection
            }
        }

        println("Creating new Native connection for $connectionKey")
        val newQuicConnection = QuicConnection.newClientConnection()
        newQuicConnection.expectedServerName = host
        // TODO: Store Client Cert/Key in QuicConnection if fields are added
        // if (clientCertificateChainDer != null && clientPrivateKeyDer != null) {
        //    newQuicConnection.clientCertChainDer = clientCertificateChainDer
        //    newQuicConnection.clientPrivateKeyDer = clientPrivateKeyDer
        // }

        return try {
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
            Result.failure(borg.trikeshed.net.http.QuicCurlException("Failed to establish Native connection for $connectionKey", cause = e))
        }
    }

    actual override suspend fun releaseConnection(connection: QuicConnection) {
        val connectionKey = activeConnections.entries.find { it.value == connection }?.key
        if (connectionKey != null) {
            val job = connectionJobs.remove(connectionKey)
            try {
                job?.cancelAndJoin()
            } catch (e: Exception) {
                println("Exception during job cancellation for $connectionKey (Native): ${e.message}")
            }
            activeConnections.remove(connectionKey)
            connectionRemoteAddresses.remove(connection)
            println("Released Native connection for key: $connectionKey")
            // TODO: connection.closeInternalState()
        } else {
            // println("Attempted to release a Native connection not actively managed: ${connection.clientId.toHexString()}")
        }
    }

    actual override suspend fun closeAll() {
        println("Closing all Native connections in ActualNativeQuicConnectionProvider.")
        val jobsToCancel = connectionJobs.values.toList()
        jobsToCancel.forEach { job ->
            try {
                job.cancelAndJoin()
            } catch (e: Exception) {
                println("Exception while cancelling a Native connection job: ${e.message}")
            }
        }
        connectionJobs.clear()
        activeConnections.clear()
        connectionRemoteAddresses.clear()

        try {
            coroutineContext[Job]?.cancelAndJoin()
        } catch (e: Exception) {
            println("Exception while cancelling Native provider scope: ${e.message}")
        }
        println("ActualNativeQuicConnectionProvider closed.")
    }

    private fun startConnectionReceiveLoop(
        connection: QuicConnection,
        targetHost: String,
        targetPort: Int,
        clientCertificateChainDer: Series<ByteArray>?,
        clientPrivateKeyDer: ByteArray?
    ): Job {
        return launch {
            val connectionKey = "${targetHost}:${targetPort}"
            println("Native receive loop started for $connectionKey. ClientID: ${connection.clientId.toHexString()}")
            var successfullyBound = false // Renamed for clarity

            try {
                // Phase 1: Bind Socket
                val localAddress = quicNetworkService.bind(null)
                successfullyBound = true
                // TODO: Store localAddress in QuicConnection if it has a field
                // connection.localSocketAddress = localAddress
                println("Native loop for $connectionKey bound to ${localAddress.first}:${localAddress.second}")

                // Phase 2: Resolve Remote Address
                val resolvedRemoteAddresses = quicNetworkService.resolve(targetHost, targetPort)
                if (resolvedRemoteAddresses.isEmpty()) { // Assuming Series.isEmpty()
                    println("Error resolving $targetHost:$targetPort for native connection.")
                    // No need to remove from maps as it wasn't added yet if resolve fails before loop
                    if (successfullyBound) quicNetworkService.close()
                    return@launch
                }
                val remoteAddress = resolvedRemoteAddresses.first()!! // Assuming Series.first() is non-null if not empty
                connectionRemoteAddresses[connection] = remoteAddress
                println("Native loop for $connectionKey resolved remote to ${remoteAddress.first}:${remoteAddress.second}")

                // Phase 3: Setup Connection Manager, CryptoUtils, Initial Secrets
                val connectionManager = QuicConnectionManager(connection, QuicConnectionManager.ConnectionRole.CLIENT)
                val cryptoUtils = QuicCryptoUtils(this.coroutineContext) // Job's context
                connection.deriveInitialSecrets(cryptoUtils, connectionManager)

                // Phase 4: Main Receive Loop
                while (isActive) {
                    val datagram = quicNetworkService.receive(2048) // Buffer size

                    // Placeholder for TlsHandler access
                    val tempTlsHandlerForTest: borg.trikeshed.net.quic.QuicTlsHandler? = null

                    val expectedDestCid = connection.clientId
                    val currentPacketTypeForParsing = if (connectionManager.isHandshakeConfirmed()) QuicPacketType.INITIAL /* TODO: Should be 1-RTT type */ else QuicPacketType.INITIAL


                    val unprotectedPacket = QuicPacketProcessor.deserializeAndUnprotectPacket(
                        this.coroutineContext,
                        datagram.data.copyOfRange(0, datagram.length),
                        connectionManager,
                        currentPacketTypeForParsing,
                        expectedDestCid
                    )

                    if (unprotectedPacket != null) {
                        val frames = QuicFrameParser.parseFrames(unprotectedPacket.unprotectedPayload, unprotectedPacket.header.type)
                        frames.forEach { frame ->
                            when (frame) {
                                is QuicFrame.CryptoFrame -> {
                                    println("Native Provider Loop: Received Crypto Frame, offset ${frame.offset}, len ${frame.data.size}")
                                    // tempTlsHandlerForTest?.processIncomingCryptoData(frame.data, connectionManager.getReceiveEncryptionLevel())
                                }
                                is QuicFrame.AckFrame -> {
                                    println("Native Provider Loop: Received Ack Frame for PN ${frame.largestAcked}")
                                    connectionManager.recordPacketAckedByPeer(frame)
                                }
                                is QuicFrame.ConnectionCloseFrame -> {
                                    println("Native Provider Loop: Received ConnectionClose: ${frame.reasonPhrase}")
                                    // connection.closeWithError(frame.errorCode, frame.reasonPhrase) // Conceptual
                                    this.coroutineContext.cancel() // Stop this connection's loop
                                }
                                else -> println("Native Provider Loop: Received other frame: ${frame::class.simpleName}")
                            }
                        }
                    } else {
                        println("Native Provider Loop: Failed to unprotect packet from ${datagram.address.first}:${datagram.address.second}")
                    }
                }
            } catch (e: CancellationException) {
                println("Native receive loop for $connectionKey cancelled.")
            } catch (e: Exception) {
                println("Error in native receive loop for $connectionKey: ${e.message}")
                // TODO: connection.closeWithError(TransportErrorCode.INTERNAL_ERROR.value, "Receive loop error")
            } finally {
                println("Native receive loop for $connectionKey cleaning up.")
                if (successfullyBound) {
                    quicNetworkService.close() // Close this connection's "session"
                }
                activeConnections.remove(connectionKey)
                connectionJobs.remove(connectionKey)
                connectionRemoteAddresses.remove(connection)
            }
        }
    }

    // Helper for CID to HexString if not already available globally
    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    // Called by TlsHandler (for CRYPTO data) or StreamManager (for STREAM data)
    suspend fun sendDataForConnection(
        connection: QuicConnection,
        payloadBytes: ByteArray,
        encryptionLevel: EncryptionLevel,
        frameType: QuicFrameType,
        streamId: Long? = null,
        offset: Long? = null,
        isFin: Boolean = false
    ) {
        val connectionManager = QuicConnectionManager(connection, QuicConnectionManager.ConnectionRole.CLIENT)
        val remoteAddress = connectionRemoteAddresses[connection] ?: run {
            println("Error: Remote address not found for Native connection ${connection.clientId.toHexString()}. Cannot send.")
            return
        }

        val quicFrames: List<QuicFrame> = when (frameType) {
            QuicFrameType.CRYPTO -> listOf(QuicFrame.CryptoFrame(offset ?: 0L, payloadBytes))
            QuicFrameType.STREAM_BASE -> {
                if (streamId == null) {
                    println("Error: Stream ID is null for STREAM frame in Native sender. Cannot send.")
                    return
                }
                listOf(QuicFrame.StreamFrame(streamId, offset ?: 0L, payloadBytes, isFin, true))
            }
            else -> {
                println("Error: Unsupported frame type for direct sending in Native: $frameType")
                return
            }
        }

        if (quicFrames.isEmpty()) {
            println("No frames to send for Native connection ${connection.clientId.toHexString()}.")
            return
        }

        val packetNumber = connectionManager.getNextPacketNumberForEncryptionLevel(encryptionLevel).toULong()
        val packetType = QuicPacketUtils.determinePacketType(encryptionLevel, connectionManager.isHandshakeConfirmed())
        val destinationCid = connection.serverId ?: connection.initialClientChosenDcId ?: connection.clientId
        val sourceCid = connection.clientId

        val quicPacket = QuicPacketUtils.createQuicPacket(
            packetType,
            destinationCid,
            sourceCid,
            packetNumber,
            quicFrames,
            connection
        )

        val protectedPacketBytes = QuicPacketProcessor.serializeAndProtectPacket(
            this.coroutineContext,
            quicPacket,
            connectionManager,
            encryptionLevel
        )

        if (protectedPacketBytes != null) {
            val datagram = Join( // Constructing DatagramPacket: Join<ByteArray, Join<NetworkAddress, Int>>
                protectedPacketBytes,
                Join(remoteAddress, protectedPacketBytes.size)
            )
            try {
                quicNetworkService.send(datagram)
                connectionManager.recordPacketSent(packetNumber, protectedPacketBytes.size, quicFrames, encryptionLevel, quicFrames.any { it !is QuicFrame.AckFrame && it !is QuicFrame.PaddingFrame })
                println("Native Sent packet PN $packetNumber, Level $encryptionLevel, ${protectedPacketBytes.size} bytes to ${remoteAddress.first}:${remoteAddress.second}")
            } catch (e: Exception) {
                println("Error sending packet for Native connection ${connection.clientId.toHexString()}: ${e.message}")
                // Handle send error
            }
        } else {
            println("Error: Failed to serialize/protect packet for PN $packetNumber, Level $encryptionLevel (Native)")
        }
    }
}
