package borg.trikeshed.net.quic

// Assuming QuicStreamTypes.kt, QuicFrames.kt, QuicTypes.kt,
// QuicStreamManager.kt (for QuicTransportParameters), and QuicConnectionFlowControl.kt
// are in this package or imported correctly.

/**
 * Conceptual class representing a QUIC Connection, demonstrating integration of
 * QuicStreamManager and QuicConnectionFlowController.
 */
class QuicConnection(
    private val localRole: StreamInitiatorRole,
    // Other necessary params like CIDs, crypto context would go here.
    // For this subtask, we focus on manager/controller integration.
    private var localTransportParams: QuicTransportParameters,
    private var peerTransportParams: QuicTransportParameters, // Assumed to be known/updated after handshake
    private val connectionManager: QuicConnectionManager // Added ConnectionManager
) {
    private val controlFramesToSend: MutableList<QuicFrame> = mutableListOf()

    private val connectionFlowController: QuicConnectionFlowController = QuicConnectionFlowController(
        initialLocalMaxData = localTransportParams.initialMaxDataLocal,
        initialPeerMaxData = peerTransportParams.initialMaxDataRemote, // Peer's remote is their limit on us
        localTransportParams = localTransportParams, // Our TPs for our increment logic
        queueControlFrame = { frame -> controlFramesToSend.add(frame) },
        signalConnectionWantsToSend = { scheduleSend() }
    )

    private val streamManager: QuicStreamManager = QuicStreamManager(
        localRole = localRole,
        localTransportParamsProvider = { localTransportParams },
        peerTransportParamsProvider = { peerTransportParams }, // Provide peer's TPs
        queueControlFrameCallback = { frame -> controlFramesToSend.add(frame) },
        connectionErrorCallback = { errorCode, reason -> closeConnectionLocal(errorCode, reason, 0L /* Placeholder for frame type */) },
        // onConnectionWantsToSend is set by QuicConnection itself after streamManager is initialized.
    ).also { it.onConnectionWantsToSend = { scheduleSend() } }


    init {
        // After handshake, peer TPs are known and should be used to update limits.
        // This might be called from a specific post-handshake setup method.
        // streamManager.updatePeerStreamLimits(peerTransportParams) // Already handled by peerTransportParamsProvider for init
        // connectionFlowController.updateTransportParameters(localTransportParams, peerTransportParams) // Also handled by constructor with peer TPs
    }

    /**
     * Called when peer's transport parameters are finalized (e.g., after handshake).
     */
    fun onPeerTransportParametersAvailable(newPeerTransportParams: QuicTransportParameters) {
        this.peerTransportParams = newPeerTransportParams
        // Update components that depend on peer's TPs
        streamManager.updatePeerStreamLimits(newPeerTransportParams)
        connectionFlowController.updateTransportParameters(this.localTransportParams, newPeerTransportParams)
        scheduleSend() // Peer might have increased our limits
    }

    /**
     * Called when our local transport parameters change (e.g. new address validation token received)
     */
    fun onLocalTransportParametersUpdated(newLocalTransportParams: QuicTransportParameters) {
        this.localTransportParams = newLocalTransportParams
        // Update components that depend on local TPs
        connectionFlowController.updateTransportParameters(newLocalTransportParams, this.peerTransportParams)
        // Stream manager's limits for peer are also derived from local TPs
        streamManager.checkAndSendMaxStreamsUpdateIfNeeded() // May need to update peer limits
        scheduleSend()
    }


    fun scheduleSend() {
        println("QuicConnection: scheduleSend() called. Will attempt to prepare and send packets.")
        // In a real system, this would trigger the packet sending loop, possibly with pacing.
        // prepareAndSendPackets() // Example call
    }

    private fun closeConnectionLocal(errorCode: Long, reason: String, frameType: Long) {
        println("QuicConnection: Closing connection. Error: $errorCode, Reason: '$reason', Offending FrameType: $frameType")
        // Full connection closure logic: set state, inform streams, send CONNECTION_CLOSE etc.
    }

    /**
     * Conceptual method where outgoing packets are built.
     */
    fun prepareAndSendPackets() {
        val MAX_PACKET_PAYLOAD_SIZE = 1200 // Example

        // Connection Level Flow Control Check for data-carrying frames
        val availableConnectionSendWindow = connectionFlowController.getAvailableSendWindow()
        var canSendDataFrames = availableConnectionSendWindow > 0L

        if (!canSendDataFrames) {
            // If connection is blocked, queue DATA_BLOCKED if not sent recently for this limit
            if (connectionFlowController.checkAndQueueDataBlocked()) {
                 println("QuicConnection: Connection data blocked. Limit: ${connectionFlowController.peerMaxData}")
            }
        }

        val streamGeneratedFrames = streamManager.collectOutgoingFrames(
            maxPacketPayloadSize = MAX_PACKET_PAYLOAD_SIZE - estimateNonStreamFrameOverhead(controlFramesToSend),
            currentConnectionMaxData = connectionFlowController.peerMaxData,
            currentConnectionDataSent = connectionFlowController.dataSentCountingTowardsMaxData
        )

        val dataBytesSentThisPacket = streamGeneratedFrames.filterIsInstance<StreamFrame>().sumOf { it.data.size }
        if (dataBytesSentThisPacket > 0) {
            if (!canSendDataFrames) {
                // This should ideally not happen if collectOutgoingFrames respects the zero connection window.
                // It means we tried to send data when connection FC said no. This is an internal logic error.
                println("ERROR: Sent data frames despite connection flow control window being <= 0")
                // Potentially drop these frames or handle error
            }
            connectionFlowController.recordDataFramesSent(dataBytesSentThisPacket.toLong())
        }

        val allFramesForPacket = mutableListOf<QuicFrame>()
        allFramesForPacket.addAll(controlFramesToSend.toList()) // Process a copy
        controlFramesToSend.clear()
        allFramesForPacket.addAll(streamGeneratedFrames)

        if (allFramesForPacket.isNotEmpty()) {
            // Determine encryption level for sending
            val encryptionLevel = connectionManager.getSendEncryptionLevel() // Assuming getSendEncryptionLevel is public
            val packetNumber = connectionManager.getNextPacketNumberForEncryptionLevel(encryptionLevel).toULong() // Assuming toULong conversion is needed if PacketNumber is ULong

            // Placeholder for actual packet construction and size calculation
            // val packetHeader = buildPacketHeader(packetNumber, encryptionLevel, ...)
            // val encryptedPayload = encryptFrames(allFramesForPacket, encryptionLevel, ...)
            // val packetBytes = packetHeader + encryptedPayload
            val estimatedPacketSize = allFramesForPacket.sumOf { estimateFrameSize(it) } + 50 // Rough estimate: sum of frame sizes + header/crypto overhead

            println("QuicConnection: Prepared packet PN $packetNumber with frames: ${allFramesForPacket.map { it::class.simpleName }}")
            // buildAndSendActualPacket(packetNumber, encryptionLevel, allFramesForPacket)

            // Record packet sent AFTER it's (conceptually) handed off for sending
            connectionManager.recordPacketSent(packetNumber, estimatedPacketSize)

        } else {
            println("QuicConnection: No frames to send.")
        }
    }

    private fun estimateNonStreamFrameOverhead(frames: List<QuicFrame>): Int {
        // Very rough estimate: type byte + some payload for simple control frames
        return frames.sumOf {
            when (it) {
                is MaxDataFrame -> 1 + 8 // Type + max varint
                is DataBlockedFrame -> 1 + 8
                is MaxStreamsFrame -> 1 + 8 + 1
                is MaxStreamDataFrame -> 1 + 8 + 8
                is StreamDataBlockedFrame -> 1 + 8 + 8
                // Add other control frames
                else -> 5 // Generic small control frame
            }
        }
    }

    // Helper to estimate individual frame size (very rough)
    private fun estimateFrameSize(frame: QuicFrame): Int {
        return when (frame) {
            is StreamFrame -> 1 + 2 + 2 + frame.data.size // Type + StreamID(varint) + Offset(varint) + Data
            is AckFrame -> 1 + 8 + 8 + 8 + 8 + (frame.ackRanges.size * 16) // Type + LargestAck + Delay + Count + FirstRange + Ranges
            is MaxDataFrame -> 1 + 8
            is DataBlockedFrame -> 1 + 8
            is MaxStreamsFrame -> 1 + 8 + 1
            is MaxStreamDataFrame -> 1 + 8 + 8
            is StreamDataBlockedFrame -> 1 + 8 + 8
            is PingFrame -> 1
            else -> 10 // Default for other control frames
        }
    }

    /**
     * Conceptual method where incoming frames from a decrypted packet are processed.
     */
    fun processIncomingFrames(frames: List<QuicFrame>) {
        var streamDataReceivedBytes = 0L
        for (frame in frames) {
            when (frame) {
                is StreamFrame -> {
                    streamManager.handleStreamFrame(frame)
                    streamDataReceivedBytes += frame.data.size // Sum up actual payload bytes
                }
                is MaxStreamDataFrame -> streamManager.handleMaxStreamDataFrame(frame)
                is ResetStreamFrame -> streamManager.handleResetStreamFrame(frame)
                is StopSendingFrame -> streamManager.handleStopSendingFrame(frame)
                is MaxStreamsFrame -> streamManager.handleMaxStreamsFrame(frame)
                is StreamDataBlockedFrame -> { println("Peer stream ${frame.streamId} is data blocked by us at limit ${frame.streamDataLimit}") }

                is MaxDataFrame -> connectionFlowController.handleMaxDataFrame(frame)
                is DataBlockedFrame -> { println("Peer connection is data blocked by us at limit ${frame.dataLimit}") }

                is AckFrame -> {
                    // ackProcessor.handleAckFrame(frame) // Original comment
                    // Assuming frame is QuicFrames.AckFrame which has ackRanges, largestAcknowledged, ackDelay
                    frame.largestAcknowledged?.let { largestAckedPn -> // Ensure largestAcknowledged is not null
                        connectionManager.recordPacketAckedByPeer(largestAckedPn)
                        // Potentially iterate through ackRanges to mark multiple packets.
                        // For now, just using largestAcknowledged.
                        // TODO: Process ackRanges for more precise ACK tracking.
                    }
                    println("Processed ACK frame for PN up to ${frame.largestAcknowledged}")
                }
                // is CryptoFrame -> cryptoStream.handleCryptoFrame(frame)
                // ... other control frames like PING, CONNECTION_CLOSE, etc.
                else -> { println("Received unhandled frame type: ${frame::class.simpleName}") }
            }
        }

        if (streamDataReceivedBytes > 0L) {
            connectionFlowController.onPayloadDataReceived(streamDataReceivedBytes)
        }

        // After processing frames that might open up peer's stream limits or our send window:
        streamManager.checkAndSendMaxStreamsUpdateIfNeeded() // Manager may want to send MAX_STREAMS
        // connectionFlowController might have queued MAX_DATA in onPayloadDataReceived.

        // Periodically check for lost packets. This might not be the best place,
        // ideally a timer would call connectionManager.checkForLostPackets().
        // For now, calling it after processing incoming frames as a placeholder.
        connectionManager.checkForLostPackets()


        // If any control frames were queued by the handlers, or if retransmissions are needed, try to send.
        val packetsToRetransmit = connectionManager.getPacketsForRetransmission()
        if (packetsToRetransmit.isNotEmpty()) {
            println("QuicConnection: Needs to retransmit packets: $packetsToRetransmit")
            // TODO: Add logic to re-queue frames from these lost packets for sending.
            // This is complex as it involves fetching original frames or data.
            // For now, just noting they need retransmission.
            // Example: packetsToRetransmit.forEach { connectionManager.onPacketRetransmitted(it) } // If we simply remove them
        }


        if (controlFramesToSend.isNotEmpty() || packetsToRetransmit.isNotEmpty()) {
            scheduleSend()
        }
    }
}
