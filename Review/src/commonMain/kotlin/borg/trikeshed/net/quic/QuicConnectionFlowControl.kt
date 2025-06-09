package borg.trikeshed.net.quic

// Assuming QuicFrames.kt (for MaxDataFrame, DataBlockedFrame) is in this package or imported.
// Assuming QuicTransportParameters is accessible (e.g., defined in QuicStreamManager.kt or commonly available)

/**
 * Manages connection-level flow control state for a QUIC connection.
 *
 * @param initialLocalMaxData Initial maximum data this endpoint is prepared to receive.
 * @param initialPeerMaxData Initial maximum data the peer is prepared to receive (our send limit).
 * @param localTransportParams Our local transport parameters, used for window increment logic.
 * @param queueControlFrame A callback to send control frames like MAX_DATA or DATA_BLOCKED.
 * @param signalConnectionWantsToSend A callback to signal the connection that it might be unblocked and can try sending again.
 */
class QuicConnectionFlowController(
    private var initialLocalMaxData: Long, // From our transport parameters (initial_max_data)
    private var initialPeerMaxData: Long,  // From peer's transport parameters (initial_max_data)
    private val localTransportParams: QuicTransportParameters, // Our TPs, for knowing how much to increment our window by
    private val queueControlFrame: (frame: QuicFrame) -> Unit,
    private val signalConnectionWantsToSend: (() -> Unit)? = null
) {
    // Receive-side: Manages how much data we've received and when to tell the peer they can send more.
    // This is our receive window that we advertise to the peer.
    var localMaxData: Long = initialLocalMaxData
        private set
    var dataReceivedCountingTowardsMaxData: Long = 0L
        private set

    // Send-side: Manages how much data we can send based on what the peer told us.
    // This is the peer's receive window for data we send.
    var peerMaxData: Long = initialPeerMaxData
        private set
    var dataSentCountingTowardsMaxData: Long = 0L
        private set

    private var isConnectionDataBlockedSent: Boolean = false

    /**
     * Initializes/updates flow control parameters, typically after handshake completes
     * and transport parameters are exchanged.
     *
     * @param localTp Our local transport parameters.
     * @param peerTp Peer's transport parameters.
     */
    fun updateTransportParameters(localTp: QuicTransportParameters, peerTp: QuicTransportParameters) {
        this.initialLocalMaxData = localTp.initialMaxDataLocal
        this.localMaxData = kotlin.math.max(this.localMaxData, localTp.initialMaxDataLocal)

        this.initialPeerMaxData = peerTp.initialMaxDataLocal // Peer's initial_max_data is their local, our remote
        val oldPeerMaxData = this.peerMaxData
        this.peerMaxData = kotlin.math.max(this.peerMaxData, peerTp.initialMaxDataLocal)

        if (this.peerMaxData > oldPeerMaxData) {
            isConnectionDataBlockedSent = false
            signalConnectionWantsToSend?.invoke()
        }
    }

    /**
     * Called when payload data has been received and processed from stream frames.
     * Updates receive counters and potentially triggers sending a MAX_DATA frame.
     *
     * @param bytesReceived The total number of stream payload bytes received in a packet/set of frames.
     */
    fun onPayloadDataReceived(bytesReceived: Long) {
        if (bytesReceived == 0L) return
        dataReceivedCountingTowardsMaxData += bytesReceived

        // Check if MAX_DATA needs to be sent to extend window for peer
        // Send MAX_DATA if less than half of the potential window increment is left.
        // The window increment is typically based on our initial_max_data.
        val windowIncrement = localTransportParams.initialMaxDataLocal
        val currentWindowSpaceLeftForPeer = localMaxData - dataReceivedCountingTowardsMaxData
        val threshold = windowIncrement / 2

        if (currentWindowSpaceLeftForPeer < threshold) {
            val newAbsoluteMaxData = dataReceivedCountingTowardsMaxData + windowIncrement
            if (newAbsoluteMaxData > localMaxData) { // Only send if it actually increases the window
                queueControlFrame(MaxDataFrame(newAbsoluteMaxData))
                localMaxData = newAbsoluteMaxData
            }
        }
    }

    /**
     * Handles an incoming MAX_DATA frame from the peer.
     * Updates the limit on how much data we can send.
     *
     * @param frame The received [MaxDataFrame].
     */
    fun handleMaxDataFrame(frame: MaxDataFrame) {
        val oldPeerMaxData = peerMaxData
        peerMaxData = kotlin.math.max(peerMaxData, frame.maximumData)
        if (peerMaxData > oldPeerMaxData) {
            val unblocked = isConnectionDataBlockedSent // Store if we were blocked
            isConnectionDataBlockedSent = false // Unblocked, can try sending again
            if (unblocked) {
                 signalConnectionWantsToSend?.invoke() // Signal send loop if previously blocked
            }
        }
    }

    /**
     * Records that data frames (specifically STREAM frames) have been prepared for sending.
     * Updates the count of data sent towards the peer's flow control limit.
     *
     * @param totalDataBytesSent The total number of payload bytes from STREAM frames sent.
     */
    fun recordDataFramesSent(totalDataBytesSent: Long) {
        if (totalDataBytesSent > 0L) {
            dataSentCountingTowardsMaxData += totalDataBytesSent
            // If we sent data, we are presumably not blocked at this exact moment,
            // but a DATA_BLOCKED frame might still be needed if we immediately hit the limit again.
            // The checkAndQueueDataBlocked handles sending it if we are now at the limit.
            isConnectionDataBlockedSent = false
        }
    }

    /**
     * Gets the current available window for sending data based on peer's MAX_DATA.
     * @return Number of bytes that can be sent before hitting the limit.
     */
    fun getAvailableSendWindow(): Long {
        return peerMaxData - dataSentCountingTowardsMaxData
    }

    /**
     * Checks if the connection is flow control blocked for sending data.
     * If blocked and a DATA_BLOCKED frame hasn't been sent recently for this limit,
     * it queues one.
     *
     * @return True if blocked, false otherwise.
     */
    fun checkAndQueueDataBlocked(): Boolean {
        if (getAvailableSendWindow() <= 0L) {
            if (!isConnectionDataBlockedSent) {
                queueControlFrame(DataBlockedFrame(peerMaxData)) // dataLimit is the current limit causing blockage
                isConnectionDataBlockedSent = true
            }
            return true
        }
        isConnectionDataBlockedSent = false // If window > 0, we are not blocked.
        return false
    }
}
