package borg.trikeshed.net.quic

import kotlin.collections.ArrayDeque // For sendQueue
import java.util.SortedMap // For receiveChunks, ensure common availability or use alternative

// Assuming QuicStreamTypes.kt is in the same package or imported appropriately
// Assuming QuicFrames.kt (for StreamFrame) is in the same package or imported

/**
 * Represents a QUIC stream, managing its state, send/receive buffers, and flow control.
 *
 * @param streamId The unique identifier for this stream.
 * @param localRole The role of this endpoint (Client or Server) in initiating this stream type.
 * @param initialLocalMaxStreamData The initial flow control limit for data this endpoint can send.
 * @param initialRemoteMaxStreamData The initial flow control limit for data the peer can send.
 */
class QuicStream(
    val streamId: Long,
    val localRole: StreamInitiatorRole, // From QuicStreamTypes
    initialLocalMaxStreamData: Long,
    initialRemoteMaxStreamData: Long
) {
    /**
     * The resolved type of this stream (directionality, initiator) based on its ID.
     */
    val resolvedType: ResolvedStreamType = resolveStreamType(streamId) // From QuicStreamTypes

    /**
     * Current state of the stream.
     */
    var state: QuicStreamState = QuicStreamState.IDLE // From QuicStreamTypes
        private set // Use changeState method for transitions

    // --- Send-Side Properties ---
    private val sendQueue: ArrayDeque<Pair<ByteArray, Boolean>> = ArrayDeque() // Data chunk, isFin flag
    var currentSendOffset: Long = 0L
        private set
    var isFinSent: Boolean = false
        private set
    var localMaxStreamDataAllowance: Long = initialLocalMaxStreamData
        private set
    var dataSentCountingTowardsAllowance: Long = 0L
        private set
    private var isFinQueued: Boolean = false // Tracks if FIN has been added to sendQueue but not yet sent
    private var currentChunkBytesSent: Int = 0 // Tracks progress on the current head of sendQueue

    // --- Receive-Side Properties ---
    private val receiveChunks: SortedMap<Long, ByteArray> = sortedMapOf()
    var contiguousReceiveOffset: Long = 0L
        private set
    var isFinReceived: Boolean = false
        private set
    var finalSizeReceived: Long? = null
        private set
    var remoteMaxStreamDataWindow: Long = initialRemoteMaxStreamData // Max data peer can send us
        private set
    var dataReceivedCountingTowardsWindow: Long = 0L
        private set

    // --- Listeners ---
    var onDataForApplicationListener: ((stream: QuicStream, data: ByteArray, isFin: Boolean) -> Unit)? = null
    var onHasFramesToSendListener: ((stream: QuicStream) -> Unit)? = null
    var onStateChangedListener: ((stream: QuicStream, newState: QuicStreamState) -> Unit)? = null
    var onWindowUpdateNeededListener: ((stream: QuicStream, newMaxStreamData: Long) -> Unit)? = null
    var onStreamDataBlockedListener: ((stream: QuicStream, limit: Long) -> Unit)? = null

    companion object {
        /** Rough estimate for minimal STREAM frame size (Type byte + Stream ID + minimal fields). */
        const val MIN_STREAM_FRAME_SIZE_ESTIMATE = 1 + 1 + 1 + 1 // Type + StreamID + Offset + Length (all 1-byte varint) + 0 data
    }

    // Note: APPROX_STREAM_FRAME_OVERHEAD removed, use estimateFrameOverhead instead.

    /**
     * Enqueues application data to be sent on this stream.
     * @param data The data to send.
     * @param isFin True if this is the final data for this stream (sends a FIN flag).
     * @throws IllegalStateException if the stream is not in a state that can send data, if FIN already sent/queued,
     *                               or if attempting to send data on a reset stream.
     */
    fun enqueueApplicationData(data: ByteArray, isFin: Boolean) {
        if (isFinSent) {
            throw IllegalStateException("Stream $streamId: FIN already sent, cannot send more data.")
        }
        if (isFinQueued && isFin) { // If FIN is already in queue, and this call also wants to set FIN
             if (data.isNotEmpty()) throw IllegalStateException("Stream $streamId: FIN already queued, cannot send more data with FIN again.")
            // If data is empty and isFin is true, and FIN already queued, it's redundant but harmless.
        }
         if (isFinQueued && data.isNotEmpty()) { // If FIN is in queue, no more data should be added
            throw IllegalStateException("Stream $streamId: FIN already queued, cannot send more data.")
        }

        if (state == QuicStreamState.RESET_SENT || state == QuicStreamState.RESET_RECEIVED || state == QuicStreamState.CLOSED) {
            throw IllegalStateException("Stream $streamId: Cannot send data in state $state.")
        }
        // Also check LOCAL_HALF_CLOSED, but allow sending FIN if not yet sent.
        if (state == QuicStreamState.LOCAL_HALF_CLOSED && (data.isNotEmpty() || (isFin && !isFinSent && !isFinQueued))) {
            // Allow sending FIN if it wasn't the cause of LOCAL_HALF_CLOSED, but not more data.
            if (data.isNotEmpty()) throw IllegalStateException("Stream $streamId: Cannot send data in state $state.")
        }


        if (data.isEmpty() && !isFin) {
            return // Nothing to queue if no data and no FIN
        }

        sendQueue.addLast(data.copyOf() to isFin)
        if (isFin) {
            isFinQueued = true
        }
        onHasFramesToSendListener?.invoke(this)
    }

    /**
     * Handles an incoming STREAM frame.
     * Validates the frame, stores data, processes contiguous chunks, and updates flow control.
     * @param frame The received [StreamFrame].
     */
    fun handleStreamFrame(frame: StreamFrame) {
        require(frame.streamId == this.streamId) {
            "StreamFrame ID ${frame.streamId} does not match this stream ${this.streamId}"
        }

        // State Checks
        if (state == QuicStreamState.RESET_RECEIVED || state == QuicStreamState.RESET_SENT || state == QuicStreamState.CLOSED) {
            // Log late arrival or ignore
            println("Stream $streamId: Received STREAM frame in state $state, ignoring.")
            return
        }

        // Final Size Error Checks (Part 1: Data after FIN)
        if (isFinReceived && finalSizeReceived != null) {
            if (frame.offset + frame.data.size > finalSizeReceived!!) {
                // TODO: Implement connection error signaling for Final Size Error (FINAL_SIZE_ERROR)
                println("Stream $streamId: ERROR - Received data beyond final size. Frame offset+len: ${frame.offset + frame.data.size}, finalSize: $finalSizeReceived")
                // For now, just ignore the frame to prevent further processing. A real implementation would trigger a connection error.
                return
            }
            if (frame.isFin && (frame.offset + frame.data.size != finalSizeReceived!!)) {
                // TODO: Implement connection error signaling for Final Size Error (FINAL_SIZE_ERROR)
                println("Stream $streamId: ERROR - Frame with FIN has inconsistent final size. Frame offset+len: ${frame.offset + frame.data.size}, finalSize: $finalSizeReceived")
                return
            }
        }

        // TODO: Add check for frame.offset + frame.data.size > remoteMaxStreamDataWindow (Flow Control Error)
        // This check needs to be careful if receiveChunks can temporarily store data slightly beyond the window before reordering.
        // For now, assume data within window is handled by buffering, and MAX_STREAM_DATA updates are timely.

        // Data Buffering & FIN Handling
        if (frame.data.isNotEmpty()) {
            // If already FIN received, and this frame's data starts at/after finalSize, it's an error (unless it's an empty FIN frame at finalSize)
            if (isFinReceived && finalSizeReceived != null && frame.offset >= finalSizeReceived!!) {
                 // TODO: Connection error for data after FIN / final size error
                println("Stream $streamId: ERROR - Received data at/after final offset when FIN already processed. Frame offset: ${frame.offset}, finalSize: $finalSizeReceived")
                return
            }
            receiveChunks[frame.offset] = frame.data // Overwrites if duplicate offset, which is fine.
        }

        if (frame.isFin) {
            val calculatedFinalSize = frame.offset + frame.data.size
            if (!isFinReceived) {
                isFinReceived = true
                finalSizeReceived = calculatedFinalSize
            } else if (finalSizeReceived != calculatedFinalSize) {
                // TODO: Implement connection error signaling for Final Size Error (FINAL_SIZE_ERROR)
                println("Stream $streamId: ERROR - Subsequent FIN implies different final size. Original: $finalSizeReceived, New: $calculatedFinalSize")
                // This is a connection error.
                return
            }
        }

        // Process Contiguous Data Loop
        var processedAnyDataInLoop = false
        var deliveredFinInLoop = false
        while (receiveChunks.containsKey(contiguousReceiveOffset)) {
            val chunk = receiveChunks.remove(contiguousReceiveOffset)!!

            if (finalSizeReceived != null && contiguousReceiveOffset + chunk.size > finalSizeReceived!!) {
                // TODO: Implement connection error signaling for Final Size Error (FINAL_SIZE_ERROR)
                println("Stream $streamId: ERROR - Chunk would exceed final size. Offset: $contiguousReceiveOffset, ChunkSize: ${chunk.size}, FinalSize: $finalSizeReceived")
                receiveChunks[contiguousReceiveOffset] = chunk // Put chunk back
                break // Stop processing
            }

            val isFinForThisChunk = isFinReceived && (finalSizeReceived == contiguousReceiveOffset + chunk.size)
            onDataForApplicationListener?.invoke(this, chunk, isFinForThisChunk && chunk.isNotEmpty()) // Only signal FIN with data if data is present

            contiguousReceiveOffset += chunk.size
            dataReceivedCountingTowardsWindow += chunk.size // This should track total received bytes for flow control against remoteMaxStreamDataWindow
            processedAnyDataInLoop = true

            if (isFinForThisChunk) {
                deliveredFinInLoop = true // FIN flag is conceptually delivered with the last piece of data
                break // All data up to FIN consumed
            }
        }

        // Post-Loop FIN Delivery (for empty FIN or if FIN offset was met without data in last chunk)
        if (!deliveredFinInLoop && isFinReceived && finalSizeReceived != null && contiguousReceiveOffset == finalSizeReceived) {
            onDataForApplicationListener?.invoke(this, ByteArray(0), true) // Signal FIN with empty data
            deliveredFinInLoop = true
        }

        if (deliveredFinInLoop) {
            if (state == QuicStreamState.OPEN) changeState(QuicStreamState.REMOTE_HALF_CLOSED)
            else if (state == QuicStreamState.LOCAL_HALF_CLOSED) changeState(QuicStreamState.CLOSED)
        }

        // Flow Control Update Check (Simplified)
        if (processedAnyDataInLoop) { // Or if window was consumed by just receiving a FIN
            // The window is remoteMaxStreamDataWindow. dataReceivedCountingTowardsWindow tracks total bytes received.
            // We need to send MAX_STREAM_DATA if the *available window* for the peer to send into is getting small.
            // Available window = remoteMaxStreamDataWindow - contiguousReceiveOffset (or a similar measure of consumed data for flow control)
            // Let's use `contiguousReceiveOffset` as the basis for how much data the application has processed / buffer space freed.
            val currentWindowSizeAvailableToPeer = remoteMaxStreamDataWindow - contiguousReceiveOffset

            // Example: initial window was `initialRemoteMaxStreamData`. We want to maintain roughly that much window.
            // If less than half of the initial window size is available for the peer to send, send an update.
            val threshold = initialRemoteMaxStreamData / 2 // This is one strategy

            if (currentWindowSizeAvailableToPeer < threshold) {
                // We want to open the window up again, e.g., by another `initialRemoteMaxStreamData` amount.
                // The new absolute limit will be `contiguousReceiveOffset + initialRemoteMaxStreamData`.
                val newMaxStreamData = contiguousReceiveOffset + initialRemoteMaxStreamData // New absolute offset limit for peer
                onWindowUpdateNeededListener?.invoke(this, newMaxStreamData)
                // The connection manager, upon receiving this, would typically update remoteMaxStreamDataWindow
                // via updateRemoteMaxStreamDataAfterSendingFrame() AFTER successfully sending the MAX_STREAM_DATA frame.
            }
        }
    }

    /**
     * Prepares STREAM frames to be sent, based on queued data and flow control limits.
     * @param maxPayloadBytesPerFrame Maximum payload size allowed per generated STREAM frame.
     * @param maxBytesForThisStreamInCurrentBatch Max bytes this stream can contribute to the current packet,
     *                                           including estimated frame overhead.
     * @param connectionWindowShareForStream Max data payload bytes this stream can send due to connection-level flow control.
     * @return A list of [StreamFrame]s ready for transmission. Empty if no data/FIN to send or blocked.
     */
    fun prepareStreamFrames(maxBytesForThisStreamInCurrentBatch: Int, connectionWindowShareForStream: Long): List<StreamFrame> {
        val frames = mutableListOf<StreamFrame>()
        var availableSpaceInBatch = maxBytesForThisStreamInCurrentBatch

        while (availableSpaceInBatch > MIN_STREAM_FRAME_SIZE_ESTIMATE &&
               (sendQueue.isNotEmpty() || (isFinQueued && !isFinSent))) {

            val streamWindowRemaining = localMaxStreamDataAllowance - dataSentCountingTowardsAllowance
            val connWindowShareRemaining = connectionWindowShareForStream - frames.sumOf { it.data.size } // Track conn window used by this stream in this call

            if (streamWindowRemaining <= 0 && !(sendQueue.isEmpty() && isFinQueued && !isFinSent)) {
                if (sendQueue.isNotEmpty() && sendQueue.first().first.isNotEmpty()) {
                    onStreamDataBlockedListener?.invoke(this, localMaxStreamDataAllowance)
                    break // Blocked by stream-level flow control for data frames
                }
            }
             if (connWindowShareRemaining <= 0 && !(sendQueue.isEmpty() && isFinQueued && !isFinSent)) {
                if (sendQueue.isNotEmpty() && sendQueue.first().first.isNotEmpty()) {
                    // Connection level flow control would be signaled by QuicStreamManager / QuicConnection
                    break // Blocked by connection-level flow control for data frames
                }
            }


            val currentDataOffsetForFrame = currentSendOffset
            var frameData = ByteArray(0)
            var frameIsFin = false
            var sendLen = 0

            // Tentative frame for overhead estimation
            val isPotentiallyFinOnly = sendQueue.isEmpty() && isFinQueued && !isFinSent
            val tempFrameForOverhead = StreamFrame(streamId, currentDataOffsetForFrame, ByteArray(0), isFinQueued, true)
            val estimatedOverhead = estimateFrameOverhead(tempFrameForOverhead)

            if (availableSpaceInBatch <= estimatedOverhead && !isPotentiallyFinOnly) break


            if (sendQueue.isNotEmpty()) {
                val (chunkData, chunkIsFin) = sendQueue.first()
                val bytesLeftInChunk = chunkData.size - currentChunkBytesSent

                val maxDataPayloadForStreamFC = streamWindowRemaining.toInt().coerceAtLeast(0)
                val maxDataPayloadForConnectionFC = connWindowShareRemaining.toInt().coerceAtLeast(0)
                val maxDataPayloadForPacketSpace = (availableSpaceInBatch - estimatedOverhead).coerceAtLeast(0)

                sendLen = minOf(
                    bytesLeftInChunk,
                    maxDataPayloadForStreamFC,
                    maxDataPayloadForConnectionFC,
                    maxDataPayloadForPacketSpace
                )

                if (sendLen > 0) {
                    frameData = chunkData.copyOfRange(currentChunkBytesSent, currentChunkBytesSent + sendLen)
                }
                frameIsFin = chunkIsFin && (currentChunkBytesSent + sendLen == chunkData.size)

                currentChunkBytesSent += sendLen
                if (currentChunkBytesSent == chunkData.size) {
                    sendQueue.removeFirst()
                    currentChunkBytesSent = 0
                }
            } else if (isFinQueued && !isFinSent) { // FIN-only frame
                frameIsFin = true
                sendLen = 0
            } else {
                break
            }

            if (sendLen > 0 || frameIsFin) {
                // Re-estimate overhead with actual data size if sendLen > 0 for more accuracy, though less critical now
                // val actualFrame = StreamFrame(streamId, currentDataOffsetForFrame, frameData, frameIsFin, true)
                // val actualFrameSize = frameData.size + estimateFrameOverhead(actualFrame)
                // if (actualFrameSize > availableSpaceInBatch && !isPotentiallyFinOnly) { continue or break }


                val streamFrame = StreamFrame(
                    streamId = this.streamId,
                    offset = currentDataOffsetForFrame,
                    data = frameData,
                    isFin = frameIsFin,
                    sendLengthExplicitly = true // Simplification: always send length
                )
                frames.add(streamFrame)

                currentSendOffset += sendLen
                dataSentCountingTowardsAllowance += sendLen
                availableSpaceInBatch -= (frameData.size + estimateFrameOverhead(streamFrame)) // Use more accurate estimate

                if (frameIsFin) {
                    isFinSent = true
                    isFinQueued = false
                    if (state == QuicStreamState.OPEN) changeState(QuicStreamState.LOCAL_HALF_CLOSED)
                    else if (state == QuicStreamState.REMOTE_HALF_CLOSED) changeState(QuicStreamState.CLOSED)
                }
            } else if (sendQueue.isEmpty() && !isFinQueued && !isFinSent) {
                 break
            }

            if (isFinSent) break
        }

        if (sendQueue.isNotEmpty() || (isFinQueued && !isFinSent)) {
            if (localMaxStreamDataAllowance - dataSentCountingTowardsAllowance > 0 || (isFinQueued && !isFinSent && sendQueue.isEmpty())) {
                 onHasFramesToSendListener?.invoke(this)
            }
        }
        return frames
    }

    /** Estimates the size of a varint encoding for a given value. */
    private fun estimateVarintSize(value: Long): Int {
        return when {
            value < 0 -> 8 // Should not happen for positive values like ID, offset, length
            value < (1L shl 6) -> 1
            value < (1L shl 14) -> 2
            value < (1L shl 30) -> 4
            value < (1L shl 62) -> 8
            else -> throw IllegalArgumentException("Value too large for varint: $value")
        }
    }

    /** Estimates the overhead of a STREAM frame (type byte + fields, excluding data payload). */
    fun estimateFrameOverhead(frame: StreamFrame): Int {
        var overhead = 1 // Type byte
        overhead += estimateVarintSize(frame.streamId)
        // Offset is only present if OFF bit is set. OFF bit is set if frame.offset > 0 OR if explicit length is used.
        // For estimation, assume if offset is >0 or if length is explicit, offset field is present.
        if (frame.offset > 0L || frame.sendLengthExplicitly) { // A common heuristic
             overhead += estimateVarintSize(frame.offset)
        }
        // Length is only present if LEN bit is set.
        if (frame.sendLengthExplicitly) {
            overhead += estimateVarintSize(frame.data.size.toLong())
        }
        return overhead
    }

    /** Checks if the send queue is empty. */
    fun sendQueueIsEmpty(): Boolean = sendQueue.isEmpty()

    /** Checks if a FIN has been queued but not yet sent in a frame. */
    fun isFinPendingInQueue(): Boolean = isFinQueued && !isFinSent


    /**
     * Updates the maximum amount of data this stream is allowed to send.
     * Called when a MAX_STREAM_DATA frame is received from the peer.
     * @param newAllowance The new maximum stream data allowance.
     */
    fun updateLocalStreamDataAllowance(newAllowance: Long) {
        if (newAllowance > this.localMaxStreamDataAllowance) {
            this.localMaxStreamDataAllowance = newAllowance
            // Potentially re-evaluate send queue if blocked by flow control
            if (sendQueue.isNotEmpty()) {
                 onHasFramesToSendListener?.invoke(this)
            }
        }
    }

    /**
     * Updates the record of how much data the peer is allowed to send us *after* we have successfully
     * sent a MAX_STREAM_DATA frame to the peer.
     * @param newMaxStreamDataValue The new absolute maximum data offset we have allowed the peer to send.
     */
    fun recordSentMaxStreamDataFrame(newMaxStreamDataValue: Long) {
        // This function is called by the connection when a MAX_STREAM_DATA frame has been
        // scheduled to be sent (or ideally, acknowledged by the peer, but that's more complex).
        // It updates our view of the peer's send limit (the `remoteMaxStreamDataWindow` property).
        this.remoteMaxStreamDataWindow = newMaxStreamDataValue
    }


    /**
     * Called by the application after it has processed received data.
     * This is not directly used in this simplified model to trigger MAX_STREAM_DATA,
     * as `handleStreamFrame`'s flow control logic is based on `contiguousReceiveOffset`
     * and `dataReceivedCountingTowardsWindow`. However, an application might call this
     * to indicate consumption, which could be used in more advanced flow control strategies.
     * For now, this method is a placeholder or for app-level tracking if needed.
     *
     * The primary trigger for `onWindowUpdateNeededListener` is within `handleStreamFrame`.
     */
    fun applicationProcessedReceivedData(bytesCount: Long) {
        // This might adjust some internal buffer tracking if we had separate "buffered but not processed by app" vs "processed by app" states.
        // In the current model, `contiguousReceiveOffset` already represents data delivered to (and implicitly processed by) the app.
        // So, this function might not be strictly necessary for the current flow control logic in handleStreamFrame.
        // However, if an app wants to signal "I'm done with these X bytes", it could be used.
        // For now, it does not directly influence `onWindowUpdateNeededListener`.
    }

    /**
     * Handles an incoming RESET_STREAM frame.
     * @param frame The received [ResetStreamFrame].
     */
    fun handleResetStreamFrame(frame: ResetStreamFrame) {
        require(frame.streamId == this.streamId) {
            "ResetStreamFrame ID ${frame.streamId} does not match this stream ${this.streamId}"
        }

        if (state == QuicStreamState.RESET_RECEIVED || state == QuicStreamState.RESET_SENT || state == QuicStreamState.CLOSED) {
            // Ignore duplicate reset or reset on closed stream
            return
        }

        if (isFinReceived && finalSizeReceived != null && frame.finalSize != finalSizeReceived) {
            // TODO: Connection error (Final Size Error due to RESET, RFC 9000 Section 4.5)
            // This means the peer's RESET_STREAM final_size contradicts a previously sent FIN's final_size.
            println("Stream $streamId: ERROR - RESET_STREAM final size ${frame.finalSize} contradicts earlier final size $finalSizeReceived")
            // Connection should be closed with FINAL_SIZE_ERROR.
            // For now, we'll proceed to reset state, but this is a connection error.
        }

        isFinReceived = true // RESET implies no more data from peer.
        finalSizeReceived = frame.finalSize // RESET sets the final size.

        // TODO: Signal application about the reset with frame.applicationErrorCode.
        // The application should stop sending on this stream and discard any received data not yet processed.
        // For example, by invoking a specific listener like onStreamResetListener?.(this, frame.applicationErrorCode)

        // Clear receive buffer as no more data will be processed past the reset.
        // Any data in receiveChunks that is at or after finalSizeReceived should be discarded.
        // Data before finalSizeReceived that wasn't delivered might be lost from app's perspective.
        receiveChunks.clear() // Simplistic: clear all buffered data on reset.

        changeState(QuicStreamState.RESET_RECEIVED)
    }

    /**
     * Changes the stream's state and notifies listener.
     * @param newState The new [QuicStreamState].
     */
    fun changeState(newState: QuicStreamState) {
        if (this.state != newState) {
            val oldState = this.state
            this.state = newState
            onStateChangedListener?.invoke(this, newState)

            if (newState == QuicStreamState.CLOSED || newState == QuicStreamState.RESET_SENT || newState == QuicStreamState.RESET_RECEIVED) {
                sendQueue.clear()
                receiveChunks.clear()
                // TODO: Potentially release other resources associated with the stream.
                // TODO: Inform application that the stream is now fully closed or reset if not already done.
            }
        }
    }
}
