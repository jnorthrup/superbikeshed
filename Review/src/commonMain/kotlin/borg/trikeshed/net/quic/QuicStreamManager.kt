package borg.trikeshed.net.quic

// Assuming QuicStreamTypes.kt, QuicFrames.kt, QuicTypes.kt are in this package or imported correctly.
// QuicTransportParameters would need to be defined, for now using placeholder access.

/**
 * Placeholder for QUIC Transport Parameters.
 * In a real implementation, this would be a data class populated during the handshake.
 */
data class QuicTransportParameters(
    // Stream limits
    val initialMaxStreamsBidi: Long = 100L,
    val initialMaxStreamsUni: Long = 100L,

    // Stream-level flow control (data peer can send on a stream initiated by us)
    val initialMaxStreamDataBidiRemote: Long = 1L shl 20, // Peer sending on our client-init bidi stream
    val initialMaxStreamDataUniRemote: Long = 1L shl 16,  // Peer sending on our client-init uni stream (e.g. QPACK encoder stream)

    // Stream-level flow control (data we can send on a stream initiated by peer)
    val initialMaxStreamDataBidiLocal: Long = 1L shl 20,  // We are sending on peer-init bidi stream
    val initialMaxStreamDataUniLocal: Long = 1L shl 16,   // We are sending on peer-init uni stream (e.g. QPACK decoder stream)

    // Connection-level flow control
    val initialMaxDataLocal: Long = 1L shl 22, // How much data we are initially prepared to receive on the connection (our limit for peer)
    val initialMaxDataRemote: Long = 1L shl 22, // How much data peer is initially prepared to receive (peer's limit for us)

    // Absolute maximums (local configuration, not directly sent in TPs but used to cap advertised increases)
    val absoluteMaxLocalStreamsBidi: Long = 200L, // Example absolute max
    val absoluteMaxLocalStreamsUni: Long = 200L,  // Example absolute max

    // Other parameters
    val maxAckDelay: Long = 25L, // Milliseconds
    val ackDelayExponent: Int = 3,
    // Add other parameters like active_connection_id_limit, etc.
) {
    // Convenience getters that match previous less specific names if needed by QuicStream constructor
    // These refer to limits *we* impose on data the *peer* can send on streams *they* initiate,
    // or data *we* can send on streams *we* initiate.
    // For QuicStream: initialLocalMaxStreamData = data we can send (depends on who initiated stream)
    //                 initialRemoteMaxStreamData = data peer can send (depends on who initiated stream)
    // The current getters are simplified and might need adjustment based on stream initiator context for more accuracy.
    val initialLocalStreamMaxData: Long get() = initialMaxStreamDataBidiLocal
    val initialRemoteStreamMaxData: Long get() = initialMaxStreamDataBidiRemote
}


/**
 * Manages multiple QUIC streams within a single connection.
 * Handles stream creation, ID allocation, dispatching frames to streams,
 * and collecting frames from streams for sending.
 *
 * @param localRole The role of this endpoint (Client or Server).
 * @param transportParamsProvider A function to retrieve the negotiated [QuicTransportParameters].
 * @param queueControlFrameCallback Callback to enqueue control frames (like MAX_STREAM_DATA, STREAM_DATA_BLOCKED, MAX_STREAMS) for sending.
 * @param connectionErrorCallback Callback to signal a connection-level error.
 */
class QuicStreamManager(
    private val localRole: StreamInitiatorRole,
    private val localTransportParamsProvider: () -> QuicTransportParameters, // Renamed
    private val peerTransportParamsProvider: () -> QuicTransportParameters,  // Added
    private val queueControlFrameCallback: (frame: QuicFrame) -> Unit,
    private val connectionErrorCallback: (errorCode: Long, reason: String) -> Unit // Signature already simplified
) {
    private val streams: MutableMap<Long, QuicStream> = mutableMapOf()

    // Stream ID counters for locally-initiated streams
    private var nextLocalBidirectionalStreamId: Long = if (localRole == StreamInitiatorRole.CLIENT) 0L else 1L
    private var nextLocalUnidirectionalStreamId: Long = if (localRole == StreamInitiatorRole.CLIENT) 2L else 3L

    // Limits for peer-initiated streams (how many the peer can open on us).
    // These are *our* settings, effectively what we advertise initially and manage via MAX_STREAMS.
    private var limitOnPeerInitiatedBidiStreams: Long
    private var limitOnPeerInitiatedUniStreams: Long
    // Track what we have advertised to the peer regarding how many streams they can open.
    private var advertisedMaxPeerCanOpenBidiStreams: Long
    private var advertisedMaxPeerCanOpenUniStreams: Long


    // Limits for locally-initiated streams (how many we can open towards the peer).
    // These are based on the *peer's* advertised transport parameters.
    private var limitOnLocallyInitiatedBidiStreams: Long
    private var limitOnLocallyInitiatedUniStreams: Long

    private var openLocallyInitiatedBidirectionalStreams: Int = 0
    private var openLocallyInitiatedUnidirectionalStreams: Int = 0
    private var openRemotelyInitiatedBidirectionalStreams: Int = 0
    private var openRemotelyInitiatedUnidirectionalStreams: Int = 0

    private val activeStreams: MutableSet<Long> = mutableSetOf() // Streams that have data/FIN to send
    var onConnectionWantsToSend: (() -> Unit)? = null // To signal connection to attempt sending packets

    // Lazy init for transportParams
    private val localTransportParams by lazy { localTransportParamsProvider() } // Renamed
    private val peerTransportParams by lazy { peerTransportParamsProvider() }    // Added

    init {
        val localTp = localTransportParams // Our own TPs that we will/did send
        limitOnPeerInitiatedBidiStreams = localTp.initialMaxStreamsBidi
        limitOnPeerInitiatedUniStreams = localTp.initialMaxStreamsUni
        advertisedMaxPeerCanOpenBidiStreams = localTp.initialMaxStreamsBidi
        advertisedMaxPeerCanOpenUniStreams = localTp.initialMaxStreamsUni

        // Limits on streams we can open are based on peer's TPs.
        // Initialize with potentially default/empty peer TPs, then update via updatePeerStreamLimits.
        val initialPeerTp = peerTransportParams
        limitOnLocallyInitiatedBidiStreams = initialPeerTp.initialMaxStreamsBidi
        limitOnLocallyInitiatedUniStreams = initialPeerTp.initialMaxStreamsUni
    }

    /**
     * Updates the stream limits imposed by the peer (i.e., how many streams we can open).
     * This should be called when the peer's transport parameters are finalized or updated.
     */
    fun updatePeerStreamLimits(peerTp: QuicTransportParameters) {
        limitOnLocallyInitiatedBidiStreams = peerTp.initialMaxStreamsBidi
        limitOnLocallyInitiatedUniStreams = peerTp.initialMaxStreamsUni
        // If we were blocked on opening streams, this might unblock us.
        onConnectionWantsToSend?.invoke()
    }

    fun openBidirectionalStream(): QuicStream? {
        return openStreamInternal(isBidirectional = true)
    }

    fun openUnidirectionalStream(): QuicStream? {
        return openStreamInternal(isBidirectional = false)
    }

    private fun openStreamInternal(isBidirectional: Boolean): QuicStream? {
        val limit: Long
        val currentCount: Int
        val streamIdTypeForLimitCheck: String // For logging/error messages

        if (isBidirectional) {
            limit = limitOnLocallyInitiatedBidiStreams // Using peer's advertised limit for our new streams
            currentCount = openLocallyInitiatedBidirectionalStreams
            streamIdTypeForLimitCheck = "bidirectional"
        } else {
            limit = limitOnLocallyInitiatedUniStreams // Using peer's advertised limit for our new streams
            currentCount = openLocallyInitiatedUnidirectionalStreams
            streamIdTypeForLimitCheck = "unidirectional"
        }

        if (currentCount >= limit) {
            println("Cannot open more locally-initiated $streamIdTypeForLimitCheck streams. Peer's Limit: $limit, Current: $currentCount")
            // QUIC requires sending STREAMS_BLOCKED if we try to open a stream but are blocked by peer's limit.
            // This frame informs the peer that we are blocked by *their* advertised limit.
            // The streamLimit in StreamsBlockedFrame is the limit that is causing the block.
            queueControlFrameCallback(StreamsBlockedFrame(
                streamType = if(isBidirectional) borg.trikeshed.net.quic.StreamType.BIDIRECTIONAL else borg.trikeshed.net.quic.StreamType.UNIDIRECTIONAL,
                streamLimit = limit // The limit imposed by the peer
            ))
            return null
        }

        val newStreamId = if (isBidirectional) {
            val id = nextLocalBidirectionalStreamId
            nextLocalBidirectionalStreamId += 4
            id
        } else {
            val id = nextLocalUnidirectionalStreamId
            nextLocalUnidirectionalStreamId += 4
            id
        }

        val stream = QuicStream(
            streamId = newStreamId,
            localRole = this.localRole,
            // For locally-initiated streams:
            // initialLocalMaxStreamData: Max data WE can send (limited by peer's TP for this stream type)
            // initialRemoteMaxStreamData: Max data PEER can send (limited by our TP for this stream type)
            initialLocalMaxStreamData = if (isBidirectional) peerTransportParams.initialMaxStreamDataBidiRemote else peerTransportParams.initialMaxStreamDataUniRemote,
            initialRemoteMaxStreamData = if (isBidirectional) localTransportParams.initialMaxStreamDataBidiLocal else localTransportParams.initialMaxStreamDataUniLocal
        )

        setupStreamListeners(stream)
        streams[newStreamId] = stream

        if (isBidirectional) openLocallyInitiatedBidirectionalStreams++ else openLocallyInitiatedUnidirectionalStreams++

        stream.changeState(QuicStreamState.IDLE) // Streams start IDLE, then typically OPEN when data is first sent/received.
                                                 // Or can be moved to OPEN immediately by some actions.
        return stream
    }

    fun getOrAcceptStream(streamId: Long, fromRemote: Boolean): QuicStream? {
        streams[streamId]?.let { return it }

        if (!fromRemote) {
            // Locally initiated stream not found by its ID - should not happen if created via openStreamInternal.
            return null
        }

        // Stream initiated by peer
        val resolvedType = resolveStreamType(streamId)
        if (resolvedType.initiator == localRole) {
            // Peer is trying to open a stream that *we* should be initiating (e.g., client peer opens stream 0)
            connectionErrorCallback(TransportErrorCode.STREAM_STATE_ERROR.value, "Peer initiated stream with local ID type ($streamId)")
            return null
        }

        val limit: Long
        var currentOpenCount: Int // Use var to allow direct modification for increment
        val streamIdTypeForLimitCheck: String

        if (resolvedType.directionality == StreamDirectionality.BIDIRECTIONAL) {
            limit = limitOnPeerInitiatedBidiStreams
            currentOpenCount = openRemotelyInitiatedBidirectionalStreams
            streamIdTypeForLimitCheck = "bidirectional"
        } else {
            limit = limitOnPeerInitiatedUniStreams
            currentOpenCount = openRemotelyInitiatedUnidirectionalStreams
            streamIdTypeForLimitCheck = "unidirectional"
        }

        if (currentOpenCount >= limit) {
            connectionErrorCallback(TransportErrorCode.STREAM_LIMIT_ERROR.value, "Peer exceeded remote-initiated $streamIdTypeForLimitCheck stream limit. Limit: $limit, Current: $currentOpenCount")
            return null
        }

        val stream = QuicStream(
            streamId = streamId,
            localRole = this.localRole,
            // For remotely-initiated streams:
            // initialLocalMaxStreamData: Max data WE can send (limited by peer's TP for this stream type)
            // initialRemoteMaxStreamData: Max data PEER can send (limited by our TP for this stream type)
            initialLocalMaxStreamData = if (resolvedType.directionality == StreamDirectionality.BIDIRECTIONAL) peerTransportParams.initialMaxStreamDataBidiLocal else peerTransportParams.initialMaxStreamDataUniLocal,
            initialRemoteMaxStreamData = if (resolvedType.directionality == StreamDirectionality.BIDIRECTIONAL) localTransportParams.initialMaxStreamDataBidiRemote else localTransportParams.initialMaxStreamDataUniRemote
        )
        setupStreamListeners(stream)
        streams[streamId] = stream

        if (resolvedType.directionality == StreamDirectionality.BIDIRECTIONAL) {
            openRemotelyInitiatedBidirectionalStreams++
        } else {
            openRemotelyInitiatedUnidirectionalStreams++
        }
        stream.changeState(QuicStreamState.IDLE)
        checkAndSendMaxStreamsUpdateIfNeeded() // Check if we need to update peer's allowance
        return stream
    }

    private fun setupStreamListeners(stream: QuicStream) {
        stream.onHasFramesToSendListener = { activeStream -> this.notifyStreamIsActive(activeStream.streamId) }
        stream.onWindowUpdateNeededListener = ::handleStreamWindowUpdateNeeded
        stream.onStreamDataBlockedListener = ::handleStreamDataBlocked
        stream.onStateChangedListener = ::handleStreamStateChanged
        // TODO: Setup onDataForApplicationListener to pass data up to application layer
    }

    /**
     * Called by a QuicStream when it has data or a FIN to send.
     * Adds the stream to the active list and signals the connection to potentially send a packet.
     */
    fun notifyStreamIsActive(streamId: Long) {
        // Could check if stream actually exists in `streams` map first if desired.
        if (streams.containsKey(streamId)) {
            activeStreams.add(streamId)
            onConnectionWantsToSend?.invoke()
        }
    }

    // --- Frame Dispatch Methods ---
    fun handleStreamFrame(frame: StreamFrame) {
        getOrAcceptStream(frame.streamId, true)?.handleStreamFrame(frame)
            ?: println("Warning: Received StreamFrame for unknown or unacceptable stream ${frame.streamId}")
    }

    fun handleMaxStreamDataFrame(frame: MaxStreamDataFrame) {
        streams[frame.streamId]?.updateLocalStreamDataAllowance(frame.maximumStreamData)
            ?: println("Warning: Received MaxStreamDataFrame for unknown stream ${frame.streamId}")
    }

    fun handleResetStreamFrame(frame: ResetStreamFrame) {
        getOrAcceptStream(frame.streamId, true)?.handleResetStreamFrame(frame)
            ?: println("Warning: Received ResetStreamFrame for unknown or unacceptable stream ${frame.streamId}")
    }

    fun handleStopSendingFrame(frame: StopSendingFrame) {
        getOrAcceptStream(frame.streamId, true)?.handleStopSendingFrame(frame)
            ?: println("Warning: Received StopSendingFrame for unknown or unacceptable stream ${frame.streamId}")
    }

    fun handleMaxStreamsFrame(frame: MaxStreamsFrame) {
        if (frame.isBidirectional) {
            limitOnLocallyInitiatedBidiStreams = kotlin.math.max(limitOnLocallyInitiatedBidiStreams, frame.maximumStreams)
        } else {
            limitOnLocallyInitiatedUniStreams = kotlin.math.max(limitOnLocallyInitiatedUniStreams, frame.maximumStreams)
        }
        // If we were blocked on opening streams, this might unblock us.
        // The onConnectionWantsToSend callback can be used to re-trigger sending attempts.
        onConnectionWantsToSend?.invoke()
    }

    /**
     * Checks if the number of streams initiated by the peer is approaching the limits
     * this endpoint has advertised, and sends MAX_STREAMS if necessary.
     */
    fun checkAndSendMaxStreamsUpdateIfNeeded() {
        val currentLocalTp = localTransportParams // Use the property

        // Bidirectional Streams Update
        // Threshold: if peer has consumed e.g., 75% of the streams we allowed them.
        val bidiConsumedThreshold = (advertisedMaxPeerCanOpenBidiStreams * 0.75).toLong()
        if (openRemotelyInitiatedBidirectionalStreams >= bidiConsumedThreshold) {
            // Check if we can still increase the limit based on our absolute max capacity
            if (advertisedMaxPeerCanOpenBidiStreams < currentLocalTp.absoluteMaxLocalStreamsBidi) {
                // Increment by the initial amount, but cap at the absolute maximum
                val newMaxBidi = (advertisedMaxPeerCanOpenBidiStreams + currentLocalTp.initialMaxStreamsBidi)
                    .coerceAtMost(currentLocalTp.absoluteMaxLocalStreamsBidi)

                if (newMaxBidi > advertisedMaxPeerCanOpenBidiStreams) {
                    queueControlFrameCallback(MaxStreamsFrame(newMaxBidi, isBidirectional = true))
                    advertisedMaxPeerCanOpenBidiStreams = newMaxBidi
                }
            }
        }

        // Unidirectional Streams Update (similar logic)
        val uniConsumedThreshold = (advertisedMaxPeerCanOpenUniStreams * 0.75).toLong()
        if (openRemotelyInitiatedUnidirectionalStreams >= uniConsumedThreshold) {
            if (advertisedMaxPeerCanOpenUniStreams < currentLocalTp.absoluteMaxLocalStreamsUni) {
                val newMaxUni = (advertisedMaxPeerCanOpenUniStreams + currentLocalTp.initialMaxStreamsUni)
                    .coerceAtMost(currentLocalTp.absoluteMaxLocalStreamsUni)

                if (newMaxUni > advertisedMaxPeerCanOpenUniStreams) {
                    queueControlFrameCallback(MaxStreamsFrame(newMaxUni, isBidirectional = false))
                    advertisedMaxPeerCanOpenUniStreams = newMaxUni
                }
            }
        }
    }


    // --- Frame Collection Method ---
    /**
     * Collects outgoing frames from active streams.
     * @param maxPacketPayloadSize The maximum payload size for the current QUIC packet being built.
     * @param currentConnectionMaxData The current connection-level maximum data allowance from the peer.
     * @param currentConnectionDataSent The total data sent on the connection so far that counts against `currentConnectionMaxData`.
     * @return A list of [QuicFrame]s to be sent.
     */
    fun collectOutgoingFrames(
        maxPacketPayloadSize: Int,
        currentConnectionMaxData: Long,
        currentConnectionDataSent: Long
    ): List<QuicFrame> {
        val allFrames = mutableListOf<QuicFrame>()
        var remainingPacketSpaceTotal = maxPacketPayloadSize
        var connectionWindowRemaining = currentConnectionMaxData - currentConnectionDataSent

        if (connectionWindowRemaining <= 0L) {
            // TODO: Trigger DATA_BLOCKED frame for the connection via queueControlFrameCallback if not sent recently.
            // This check should ideally only prevent data-carrying frames. Control frames might still be allowed.
            if (activeStreams.any { streamId -> streams[streamId]?.sendQueueIsEmpty() == false }) {
                 println("QuicStreamManager: Connection flow control blocked. Window: $connectionWindowRemaining")
                 // queueControlFrameCallback(DataBlockedFrame(currentConnectionMaxData)) // Example: This would be connection-level DataBlocked
            }
            return allFrames // Connection is flow-controlled for data-carrying frames
        }

        val streamsToProcess = activeStreams.toList() // Process a snapshot to avoid concurrent modification issues
        for (streamId in streamsToProcess) {
            if (remainingPacketSpaceTotal <= QuicStream.MIN_STREAM_FRAME_SIZE_ESTIMATE) break

            val stream = streams[streamId] ?: run {
                activeStreams.remove(streamId) // Remove stale ID
                continue
            }

            // Max bytes this stream can use in the packet, considering overall packet space
            // and its share of the connection flow control window.
            val streamCanUseFromConnectionWindow = connectionWindowRemaining.coerceAtLeast(0L)
            val streamMaxBytes = minOf(
                remainingPacketSpaceTotal,
                streamCanUseFromConnectionWindow.toInt().coerceAtLeast(0)
            )

            if (streamMaxBytes < QuicStream.MIN_STREAM_FRAME_SIZE_ESTIMATE && !stream.isFinPendingInQueue()) {
                continue // Not enough space for a meaningful frame from this stream unless it's a pure FIN.
            }

            val streamFrames = stream.prepareStreamFrames(streamMaxBytes, streamCanUseFromConnectionWindow)

            if (streamFrames.isNotEmpty()) {
                var actualDataBytesSentByStreamThisCall = 0L
                var actualOverheadBytesThisCall = 0L
                streamFrames.forEach { frame ->
                    // All stream frames carry data, even if it's an empty FIN frame (data.size = 0)
                    // Only count actual data payload towards connection flow control.
                    if (frame is StreamFrame) { // Should always be true here
                        actualDataBytesSentByStreamThisCall += frame.data.size
                        actualOverheadBytesThisCall += stream.estimateFrameOverhead(frame)
                    } else {
                        // Handle other frame types if they could be returned by prepareStreamFrames (currently not)
                        // For now, this else block might not be reachable.
                    }
                }
                allFrames.addAll(streamFrames)
                remainingPacketSpaceTotal -= (actualDataBytesSentByStreamThisCall.toInt() + actualOverheadBytesThisCall.toInt())
                connectionWindowRemaining -= actualDataBytesSentByStreamThisCall
            }

            if (stream.sendQueueIsEmpty() && !stream.isFinPendingInQueue()) {
                activeStreams.remove(streamId)
            }

            if (connectionWindowRemaining <= 0L && remainingPacketSpaceTotal > QuicStream.MIN_STREAM_FRAME_SIZE_ESTIMATE) {
                // TODO: Trigger DATA_BLOCKED for connection if appropriate
                // (and if there are still active streams with data to send)
                if (activeStreams.any { sid -> streams[sid]?.sendQueueIsEmpty() == false && streams[sid]?.isFinPendingInQueue() == false }) {
                    // queueControlFrameCallback(DataBlockedFrame(currentConnectionMaxData))
                }
            }
            if (remainingPacketSpaceTotal <= QuicStream.MIN_STREAM_FRAME_SIZE_ESTIMATE) break
        }
        return allFrames
    }

    // --- Internal Listener Handlers ---
    private fun handleStreamWindowUpdateNeeded(stream: QuicStream, newMaxStreamData: Long) {
        // This is called by a QuicStream when it determines its peer needs a larger window.
        // The `newMaxStreamData` is the new absolute offset the peer is allowed to send up to.
        queueControlFrameCallback(MaxStreamDataFrame(stream.streamId, newMaxStreamData))
        // After queuing, we tell the stream that we've advertised this new limit.
        stream.recordSentMaxStreamDataFrame(newMaxStreamData)
    }

    private fun handleStreamDataBlocked(stream: QuicStream, limit: Long) {
        // This is called by a QuicStream when it wants to send data but is blocked by its current
        // localMaxStreamDataAllowance (which was set by the peer's MAX_STREAM_DATA).
        queueControlFrameCallback(StreamDataBlockedFrame(stream.streamId, limit))
    }

    private fun handleStreamStateChanged(stream: QuicStream, newState: QuicStreamState) {
        // If stream becomes terminal, decrement relevant open stream counter.
        if (newState == QuicStreamState.CLOSED || newState == QuicStreamState.RESET_SENT || newState == QuicStreamState.RESET_RECEIVED) {
            val resolvedType = stream.resolvedType
            if (resolvedType.initiator == localRole) { // Locally-initiated
                if (resolvedType.directionality == StreamDirectionality.BIDIRECTIONAL) {
                    openLocallyInitiatedBidirectionalStreams = (openLocallyInitiatedBidirectionalStreams - 1).coerceAtLeast(0)
                } else {
                    openLocallyInitiatedUnidirectionalStreams = (openLocallyInitiatedUnidirectionalStreams - 1).coerceAtLeast(0)
                }
            } else { // Remotely-initiated
                if (resolvedType.directionality == StreamDirectionality.BIDIRECTIONAL) {
                    openRemotelyInitiatedBidirectionalStreams = (openRemotelyInitiatedBidirectionalStreams - 1).coerceAtLeast(0)
                } else {
                    openRemotelyInitiatedUnidirectionalStreams = (openRemotelyInitiatedUnidirectionalStreams - 1).coerceAtLeast(0)
                }
            }
            // TODO: Schedule stream removal from `streams` map after a delay (e.g., TIME_WAIT state equivalent)
            // For now, they remain in the map but are in a terminal state.
        }
        // TODO: Potentially signal connection if all streams are closed, etc.
    }
}
