package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.crypto.QuicSecrets
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
// ConnectionId and PacketNumber are already in borg.trikeshed.net.quic via QuicTypes.kt
// QuicConnection and QuicConnectionStateEnum are in borg.trikeshed.net.quic via QuicConnection.kt

/**
 * Data class to store information about sent packets.
 */
data class SentPacketInfo(
    val packetNumber: PacketNumber,
    val sentTime: Instant,
    val packetSize: Int,
    val frames: List<QuicFrame>,
    val encryptionLevel: EncryptionLevel,
    val elicitsAck: Boolean // Added to mark if the packet requires an ACK
)

/**
 * Defines the encryption levels used during a QUIC connection.
 * This is primarily from a client's perspective for sending data.
 */
enum class EncryptionLevel {
    /** Initial secrets, used for client and server initial packets. */
    INITIAL,
    /** Handshake secrets, used after initial key exchange. */
    HANDSHAKE,
    /** Zero RTT secrets, used for 0-RTT data (not fully implemented here). */
    ZERORTT, // Placeholder, 0-RTT is complex
    /** One RTT secrets, used for application data after handshake completion. */
    ONERTT
}

/**
 * Manages the state and logic of a QUIC connection.
 * This includes packet number generation, state transitions, and cryptographic key management per encryption level.
 *
 * @property connection The underlying [QuicConnection] data object holding the state.
 * @property role The role of this endpoint in the connection (Client or Server).
 */
class QuicConnectionManager(
    val connection: QuicConnection,
    initialRole: ConnectionRole
) {
    /**
     * Defines the role of this endpoint in the QUIC connection.
     */
    enum class ConnectionRole { CLIENT, SERVER }

    val role: ConnectionRole = initialRole

    private val activeSecrets: MutableMap<EncryptionLevel, QuicSecrets> = mutableMapOf()
    private val packetNumbers: MutableMap<EncryptionLevel, Long> = mutableMapOf(
        EncryptionLevel.INITIAL to 0L,
        EncryptionLevel.HANDSHAKE to 0L,
        EncryptionLevel.ONERTT to 0L,
        EncryptionLevel.ZERORTT to 0L // Though not fully used yet
    )

    // RTT and Loss Detection related fields
    private val sentPackets = mutableMapOf<PacketNumber, SentPacketInfo>()
    private val lostPacketsForRetransmission = mutableSetOf<PacketNumber>() // Using Set to avoid duplicates

    private var smoothedRtt: Long = 0L // in microseconds
    private var rttVar: Long = 0L // in microseconds
    private var firstRttSample: Boolean = true

    // Constants for RTT calculation (from RFC 6298)
    private val RTT_ALPHA = 0.125 // For SRTT
    private val RTT_BETA = 0.25  // For RTTVAR
    private val RTO_K = 4L       // For RTO calculation
    private val MIN_RTO_US = 1_000_000L // Minimum RTO in microseconds (1 second)
    private val CLOCK_GRANULARITY_US = 1_000L // Clock granularity in microseconds (1ms, example)

    // PTO related fields
    private var ptoCount: Int = 0
    // Max Ack Delay from peer's transport parameters, or default. In microseconds.
    // This should ideally be updated when peer's transport parameters are received.
    private var peerMaxAckDelayUs: Long = QuicConstants.DEFAULT_MAX_ACK_DELAY_US
    private val INITIAL_PTO_DURATION_US: Long = 200_000L // e.g., 200ms
    private val MINIMUM_PTO_DURATION_US: Long = 20_000L // e.g., 20ms, to prevent overly aggressive PTOs with small RTTs


    /**
     * Updates the cryptographic secrets for a given encryption level.
     * @param level The [EncryptionLevel] for which to store the secrets.
     * @param secrets The [QuicSecrets] to store.
     */
    fun updateSecrets(level: EncryptionLevel, secrets: QuicSecrets) {
        activeSecrets[level] = secrets
        // Potentially log or handle key update implications
    }

    /**
     * Retrieves the current secrets for sending data based on the provided or determined encryption level.
     * @param level The explicit [EncryptionLevel] to get secrets for. If null, it's determined by [getSendEncryptionLevel].
     * @return The [QuicSecrets] for the specified level, or null if not available.
     */
    fun getCurrentSecretsForSend(level: EncryptionLevel = getSendEncryptionLevel()): QuicSecrets? {
        return activeSecrets[level]
    }

    /**
     * Retrieves the current secrets for receiving (decrypting) data based on the provided or determined encryption level.
     * @param level The explicit [EncryptionLevel] to get secrets for. If null, it's determined by [getReceiveEncryptionLevel].
     * @return The [QuicSecrets] for the specified level, or null if not available.
     */
    fun getCurrentSecretsForReceive(level: EncryptionLevel = getReceiveEncryptionLevel()): QuicSecrets? {
        return activeSecrets[level]
    }

    /**
     * Determines the appropriate encryption level for sending packets based on the current connection state.
     * This logic primarily reflects a client's perspective.
     * @return The [EncryptionLevel] to use for sending.
     */
    fun getSendEncryptionLevel(): EncryptionLevel { // Made public
        return when (connection.state) {
            QuicConnectionStateEnum.INITIAL, QuicConnectionStateEnum.HANDSHAKE_STARTED -> EncryptionLevel.INITIAL
            QuicConnectionStateEnum.HANDSHAKE_COMPLETED -> EncryptionLevel.HANDSHAKE // Could also be ONERTT if sending app data immediately
            QuicConnectionStateEnum.CONNECTED -> EncryptionLevel.ONERTT
            QuicConnectionStateEnum.CLOSING, QuicConnectionStateEnum.CLOSED -> EncryptionLevel.ONERTT // Or appropriate level for CLOSE frames
        }
    }

    /**
     * Determines the appropriate encryption level for receiving packets based on the current connection state.
     * This logic primarily reflects a client's perspective.
     * @return The [EncryptionLevel] to use for receiving.
     */
    private fun getReceiveEncryptionLevel(): EncryptionLevel {
         return when (connection.state) {
            QuicConnectionStateEnum.INITIAL -> EncryptionLevel.INITIAL // Expecting Server Initial
            QuicConnectionStateEnum.HANDSHAKE_STARTED -> EncryptionLevel.HANDSHAKE // Expecting Server Handshake
            QuicConnectionStateEnum.HANDSHAKE_COMPLETED, QuicConnectionStateEnum.CONNECTED -> EncryptionLevel.ONERTT
            QuicConnectionStateEnum.CLOSING, QuicConnectionStateEnum.CLOSED -> EncryptionLevel.ONERTT // Or level of CLOSE frames
        }
    }

    /**
     * Gets the next packet number for sending at a specific encryption level.
     * Atomically increments the packet number for that level.
     * QUIC packet numbers are conceptually distinct per encryption level, though practically
     * they might share a space in some implementations if carefully managed. Here, we give them distinct spaces.
     * @param level The [EncryptionLevel] for which to get the next packet number.
     * @return The next [PacketNumber] (as Long for now, can be converted to ULong if needed by caller).
     */
    fun getNextPacketNumberForEncryptionLevel(level: EncryptionLevel): Long {
        // In a concurrent environment, this should be an atomic increment.
        val currentPn = packetNumbers.getOrPut(level) { 0L }
        packetNumbers[level] = currentPn + 1L
        return currentPn
    }

    // The old getNextPacketNumber might be an alias or specific to 1-RTT if desired,
    // or removed if getNextPacketNumberForEncryptionLevel is always used.
    // For now, let's assume the new one is the primary way.
    // fun getNextPacketNumber(): PacketNumber {
    //     return getNextPacketNumberForEncryptionLevel(getSendEncryptionLevel()).toULong()
    // }


    /**
     * Records that a packet with the given packet number has been sent.
     * (Placeholder for now - could be used for tracking packets for retransmission, RTT estimation, etc.)
     * @param pn The [PacketNumber] of the sent packet.
     * @param packetSize The size of the sent packet.
     * @param frames The list of [QuicFrame] sent in the packet.
     * @param encryptionLevel The [EncryptionLevel] at which the packet was sent.
     * @param elicitsAck True if this packet requires an acknowledgment from the peer.
     */
    fun recordPacketSent(pn: PacketNumber, packetSize: Int, frames: List<QuicFrame>, encryptionLevel: EncryptionLevel, elicitsAck: Boolean) {
        val sentTime = Clock.System.now()
        sentPackets[pn] = SentPacketInfo(pn, sentTime, packetSize, frames, encryptionLevel, elicitsAck)
        // println("Sent packet $pn at $sentTime, elicitsAck: $elicitsAck, frames: ${frames.map { it::class.simpleName }}")
    }

    /**
     * Records that a packet sent by this endpoint has been acknowledged by the peer.
     * Updates tracking based on a received ACK frame, processing all acknowledged ranges.
     * @param ackFrame The received [AckFrame].
     * @return True if any new ack-eliciting packet was acknowledged by this frame, false otherwise.
     */
    fun recordPacketAckedByPeer(ackFrame: AckFrame): Boolean {
        var overallProgressMade = false

        // Process the first ACK range (from largestAcked down by firstAckRangePacketCount)
        // Note: firstAckRangePacketCount is the number of packets in the range, including largestAcked.
        // So the smallest PN in this range is largestAcked - firstAckRangePacketCount + 1.
        if (ackFrame.firstAckRangePacketCount > 0) { // Ensure count is positive
            val largestInFirstRange = ackFrame.largestAcked
            val smallestInFirstRange = largestInFirstRange - ackFrame.firstAckRangePacketCount + 1
            for (pn in largestInFirstRange downTo smallestInFirstRange) {
                if (processSingleAckedPacket(pn)) {
                    overallProgressMade = true
                }
            }
        }


        // Process additional ACK ranges
        // currentPnConsidered is the packet number just below the previously processed range.
        var currentPnConsidered = ackFrame.largestAcked - ackFrame.firstAckRangePacketCount
                                 // (this is PN_X - 1 if first range was [PN_X - Count + 1, PN_X])

        for (ackRange in ackFrame.additionalAckRanges) {
            // The gap is relative to currentPnConsidered.
            // The largest packet in this additional range is currentPnConsidered - gap.
            val largestInAdditionalRange = currentPnConsidered - ackRange.gap
            // The smallest packet in this additional range is largestInAdditionalRange - ackRange.ackedPacketsInThisRange + 1.
            val smallestInAdditionalRange = largestInAdditionalRange - ackRange.ackedPacketsInThisRange + 1

            if (ackRange.ackedPacketsInThisRange > 0) { // Ensure count is positive
                 for (pn in largestInAdditionalRange downTo smallestInAdditionalRange) {
                    if (processSingleAckedPacket(pn)) {
                        overallProgressMade = true
                    }
                }
            }
            // Update currentPnConsidered for the next gap calculation.
            // It's the packet number just below the current additional range.
            currentPnConsidered = smallestInAdditionalRange - 1
        }

        // Update the connection's overall largest acknowledged packet number.
        // This is important for packet number reconstruction for incoming packets.
        // Note: ackFrame.largestAcked is Long, connection.largestAckedPacketNumberByPeer is ULong.
        // For now, assuming they are compatible or QuicConnection's field will be updated to Long too.
        // Let's assume connection.largestAckedPacketNumberByPeer should be Long for consistency.
        // For now, this specific update is commented out until type consistency is ensured.
        // if (ackFrame.largestAcked.toULong() > connection.largestAckedPacketNumberByPeer) {
        //     connection.largestAckedPacketNumberByPeer = ackFrame.largestAcked.toULong()
        // }
        // Instead, let's assume a direct Long field is preferred or managed elsewhere if ULong is strict.
        // The main impact of this field is PN reconstruction.
        // For RTT/loss, individual packet processing is key.

        if (overallProgressMade) {
            resetPtoBackoff() // Reset PTO if any progress was made
        }
        return overallProgressMade
    }

    /**
     * Processes a single acknowledged packet number.
     * Updates RTT, removes from sentPackets, and lostPacketsForRetransmission.
     * @param pn The acknowledged packet number.
     * @return True if this packet was ack-eliciting and newly acknowledged, false otherwise.
     */
    private fun processSingleAckedPacket(pn: Long): Boolean {
        // Update connection's view of largest ACKed for PN reconstruction if this PN is larger
        // This should ideally be done once with ackFrame.largestAcked.
        // However, if gaps mean a smaller PN is processed that is still larger than current largest, update.
        // This logic is tricky. Let's assume largestAckedPacketNumberByPeer is mainly updated by the
        // overall largestAcked from the frame, not individual PNs from ranges here.
        // The primary role here is to remove from sentPackets and update RTT.

        val sentPacketInfo = sentPackets.remove(pn)
        var progressMadeThisPacket = false
        if (sentPacketInfo != null) {
            val timeAckReceived = Clock.System.now()
            val rttSample = (timeAckReceived - sentPacketInfo.sentTime).inWholeMicroseconds

            if (firstRttSample) {
                smoothedRtt = rttSample
                rttVar = rttSample / 2
                firstRttSample = false
            } else {
                val delta = kotlin.math.abs(smoothedRtt - rttSample)
                rttVar = ((1 - RTT_BETA) * rttVar + RTT_BETA * delta).toLong()
                smoothedRtt = ((1 - RTT_ALPHA) * smoothedRtt + RTT_ALPHA * rttSample).toLong()
            }
            // TODO: Notify congestion controller with RTT sample if needed

            if (sentPacketInfo.elicitsAck) {
                progressMadeThisPacket = true
                // PTO backoff reset is now handled once per ACK frame if overallProgressMade is true.
            }
            lostPacketsForRetransmission.remove(pn)
        }
        return progressMadeThisPacket
    }

    /**
     * Checks for lost packets based on RTO.
     * This method should be called periodically (e.g., by a timer).
     */
    fun checkForLostPackets() {
        if (smoothedRtt == 0L && firstRttSample) {
            // No RTT samples yet, cannot calculate RTO effectively.
            // Use fixed initial RTO or skip. For PTO, we might use initial values.
            // For RTO-based loss, if SRTT is 0, we might use a very conservative fixed RTO or wait.
            // Let's assume this is for RTO loss detection, so if SRTT is 0, perhaps we don't declare RTO losses yet.
            return
        }

        val rto = smoothedRtt + kotlin.math.max(CLOCK_GRANULARITY_US, RTO_K * rttVar)
        val currentRto = kotlin.math.max(rto, MIN_RTO_US) // Ensure RTO is not too small
        val now = Clock.System.now()

        val packetsConsideredLost = mutableListOf<PacketNumber>()
        for ((packetNumber, sentInfo) in sentPackets) {
            if (lostPacketsForRetransmission.contains(packetNumber)) {
                // Already marked as lost, skip.
                continue
            }

            val timeSinceSent = (now - sentInfo.sentTime).inWholeMicroseconds
            if (timeSinceSent > currentRto) {
                packetsConsideredLost.add(packetNumber)
            }
        }

        for (lostPacketNumber in packetsConsideredLost) {
            if (!lostPacketsForRetransmission.contains(lostPacketNumber)) {
                println("Packet $lostPacketNumber considered lost (RTO: $currentRto us). Adding to retransmission queue.")
                lostPacketsForRetransmission.add(lostPacketNumber)
                // TODO: Notify congestion controller about the loss
                // sentPackets[lostPacketNumber]?.let { congestionController.onPacketLost(listOf(it.packetNumber)) }

                // Optional: Remove from sentPackets map once declared lost to prevent re-declaring it lost.
                // However, if it's not removed, an ACK arriving later could still process it (which might be fine).
                // For now, let's keep it in sentPackets until ACKed or connection closes.
            }
        }
    }

    /**
     * Retrieves the list of packets that need to be retransmitted.
     * The caller is responsible for clearing these from the queue or handling them appropriately.
     * @return A list of [SentPacketInfo] for retransmission. This provides packet number and original frames.
     */
    fun getPacketsForRetransmission(): List<SentPacketInfo> {
        val packetsToResendInfo = mutableListOf<SentPacketInfo>()
        val iterator = lostPacketsForRetransmission.iterator()
        while (iterator.hasNext()) {
            val packetNumber = iterator.next()
            sentPackets[packetNumber]?.let {
                packetsToResendInfo.add(it)
            } ?: run {
                // Packet info not found in sentPackets, meaning it was likely ACKed while also being marked for loss.
                // Or it was already processed for retransmission and its info removed if we modify sentPackets upon retransmission.
                // In this case, just remove it from the lost set.
                iterator.remove()
            }
        }
        return packetsToResendInfo
    }

    /**
     * Call this when a lost packet has been successfully processed for retransmission
     * (i.e., its frames have been re-queued and a new packet recorded with recordPacketSent).
     * This removes the original packet number from the retransmission queue and also from sentPackets,
     * as its original instance is now considered handled.
     * @param originalLostPacketNumber The [PacketNumber] of the original packet that was lost and now handled.
     */
    fun onPacketRetransmissionHandled(originalLostPacketNumber: PacketNumber) {
        lostPacketsForRetransmission.remove(originalLostPacketNumber)
        // Remove from sentPackets as well, since its original instance is now handled (either ACKed or retransmitted under a new PN).
        // If an ACK arrives for this originalLostPacketNumber *after* this, it will be ignored by RTT calculation, which is fine.
        sentPackets.remove(originalLostPacketNumber)
    }

    // --- PTO Methods ---

    /**
     * Resets the PTO backoff counter. Called when an ACK making progress is received
     * or when sending new ack-eliciting data.
     */
    fun resetPtoBackoff() {
        ptoCount = 0
        // The actual timer arming/disarming will be handled by QuicCurl based on this state change
        // and whether there are outstanding ack-eliciting packets.
    }

    /**
     * Calculates the current PTO duration.
     * PTO = (SRTT + 4*RTTVAR + max_ack_delay) * 2^pto_count
     * Uses initial RTT if SRTT is unknown.
     */
    fun getPtoDurationUs(): Long {
        val basePtoDuration = if (smoothedRtt == 0L && firstRttSample) {
            INITIAL_PTO_DURATION_US // Or typically 2 * kInitialRtt (e.g. 2 * 100ms = 200ms)
        } else {
            // RFC 9002: PTO = SRTT + 4*RTTVAR + max_ack_delay
            // If RTTVAR is 0 (e.g. after first sample), use clock granularity instead of 4*RTTVAR.
            val rttVarComponent = if (rttVar == 0L) CLOCK_GRANULARITY_US else RTO_K * rttVar
            smoothedRtt + rttVarComponent + peerMaxAckDelayUs
        }
        // Ensure PTO is not smaller than a minimum threshold (e.g. RTO or some fixed min)
        val clampedBasePto = kotlin.math.max(basePtoDuration, MINIMUM_PTO_DURATION_US)
        return clampedBasePto * (1L shl ptoCount) // Exponential backoff: * 2^ptoCount
    }

    /**
     * Called by QuicCurl when the PTO timer expires.
     * Increments the PTO count for exponential backoff.
     */
    fun onPtoExpired() {
        ptoCount++
        println("PTO expired. ptoCount incremented to $ptoCount.")
    }

    /**
     * Checks if there are any outstanding packets that were sent expecting an ACK.
     */
    fun hasOutstandingAckElicitingPackets(): Boolean {
        return sentPackets.any { it.value.elicitsAck }
    }

    /**
     * Allows updating peer's max_ack_delay if learned from their transport parameters.
     */
    fun updatePeerMaxAckDelay(delayUs: Long) {
        peerMaxAckDelayUs = delayUs
    }


    // --- End PTO Methods ---

    /**
     * Processes a packet number received from the peer.
     * Updates the largest received packet number from the peer if this packet number is larger.
     * @param pn The [PacketNumber] received from the peer.
     */
    fun processReceivedPacketNumberFromPeer(pn: PacketNumber) {
        if (pn > connection.largestReceivedPacketNumberFromPeer) {
            connection.largestReceivedPacketNumberFromPeer = pn
        }
    }

    /**
     * Sets the connection state to a new state.
     * @param newState The [QuicConnectionStateEnum] to transition to.
     */
    fun setState(newState: QuicConnectionStateEnum) {
        if (connection.state != newState) {
            // println("QUIC Connection State changing from ${connection.state} to $newState")
            connection.state = newState
            // TODO: Add logic for handling state transitions (e.g., starting timers, sending specific frames)
        }
    }

    /**
     * Sets the server-chosen connection ID. This is typically learned during the handshake.
     * @param serverId The [ConnectionId] chosen by the server.
     */
    fun setServerConnectionId(serverId: ConnectionId) {
        connection.serverId = serverId
    }

    /**
     * Gets the peer's advertised maximum ACK delay.
     * @return The max_ack_delay in microseconds.
     */
    fun getPeerMaxAckDelay(): ULong = connection.peerMaxAckDelay

    /**
     * Sets the peer's advertised maximum ACK delay based on their transport parameters.
     * @param delay The max_ack_delay in microseconds.
     */
    fun setPeerMaxAckDelay(delay: ULong) {
        connection.peerMaxAckDelay = delay
    }

    /**
     * Gets our local maximum ACK delay that we advertise.
     * @return The local max_ack_delay in microseconds.
     */
    fun getLocalMaxAckDelay(): ULong = connection.localMaxAckDelay
    // setLocalMaxAckDelay could be added if we allow changing it dynamically.

    /**
     * Checks if the QUIC handshake has been confirmed.
     * @return True if the state is [QuicConnectionStateEnum.HANDSHAKE_COMPLETED] or [QuicConnectionStateEnum.CONNECTED].
     */
    fun isHandshakeConfirmed(): Boolean =
        connection.state == QuicConnectionStateEnum.HANDSHAKE_COMPLETED || connection.state == QuicConnectionStateEnum.CONNECTED

    /**
     * Checks if the QUIC connection is fully established and application data can be exchanged.
     * @return True if the state is [QuicConnectionStateEnum.CONNECTED].
     */
    fun isConnected(): Boolean = connection.state == QuicConnectionStateEnum.CONNECTED

    /**
     * Closes the connection. Sets the state to CLOSING or directly to CLOSED.
     * (Further actions like sending CONNECTION_CLOSE frames would be handled by the calling code.)
     * @param graceful If true, sets state to CLOSING, allowing for a graceful close. If false, sets to CLOSED.
     */
    fun closeConnection(graceful: Boolean = true) {
        if (graceful) {
            if (connection.state != QuicConnectionStateEnum.CLOSED && connection.state != QuicConnectionStateEnum.CLOSING) {
                setState(QuicConnectionStateEnum.CLOSING)
            }
        } else {
            setState(QuicConnectionStateEnum.CLOSED)
        }
        // Clear tracking state on close
        sentPackets.clear()
        lostPacketsForRetransmission.clear()
        firstRttSample = true
        smoothedRtt = 0L
        rttVar = 0L
        ptoCount = 0
        // TODO: Cancel any timers associated with checkForLostPackets or PTO if managed here.
        // Currently, timers are managed in QuicCurl.
    }
}
