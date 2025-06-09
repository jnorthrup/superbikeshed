package evolution

import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
<<<<<<< HEAD
import borg.trikeshed.lib.CowSeriesHandle
import borg.trikeshed.lib.emptySeries
=======
// Assuming Series is from borg.trikeshed.lib.Series for QuicPacketSeries usage
import borg.trikeshed.lib.Series
// Import new types from QuicSpecTypes.kt & QuicTypes.kt
import evolution.* // Wildcard import for simplicity
import kotlin.math.max // For maxOf
import kotlin.math.min // For minOf
>>>>>>> origin/jules_wip_8844705664950451013

// BBR congestion controller using TrikeShed patterns
class BBRCongestionController {
<<<<<<< HEAD
    private var state = BBRState.STARTUP
    private var cwnd: ULong = 10240uL
    private var bottleneckBandwidth: BandwidthEstimate = 0uL
    private var minRTT: RTTMicros = ULong.MAX_VALUE
    private var rtprop: RTTMicros = ULong.MAX_VALUE
    private var pacingGain = 2.77
=======
    private var state = BBRState.STARTUP // BBRState is already an enum from QuicTypes.kt
    private var cwnd: CongestionWindow = CongestionWindow(10240uL) // Initialized with new type
    private var bottleneckBandwidth: Bandwidth = Bandwidth(0uL)    // Initialized with new type
    private var minRTT: RTT = RTT(ULong.MAX_VALUE)                  // Initialized with new type
    private var rtprop: RTT = RTT(ULong.MAX_VALUE)                  // Initialized with new type
    private var pacingGain = 2.77 // High gain for startup
>>>>>>> origin/jules_wip_8844705664950451013
    private var cwndGain = 2.0
    private var cycleIndex = 0
    private val pacingGainCycle = doubleArrayOf(1.25, 0.75, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)

<<<<<<< HEAD
    // Use Series-based storage with copy-on-write semantics
    private val inflightPackets: CowSeriesHandle<Join<QuicPacketNumber, ULong>> = emptySeries<Join<QuicPacketNumber, ULong>>().cow
    private var bytesInFlight: CongestionWindow = 0uL
=======
    // Packet tracking for congestion control
    // Key changed from QuicPacketNumber (typealias to ULong) to PacketNumber (value class)
    private val inflightPackets = mutableMapOf<PacketNumber, ULong>() // Value ULong is packetSize
    private var bytesInFlight: CongestionWindow = CongestionWindow(0uL) // Initialized with new type
>>>>>>> origin/jules_wip_8844705664950451013

    private fun removePacket(packetNumber: QuicPacketNumber): ULong? {
        val index = inflightPackets.letter.backing.▶.indexOfFirst { it.a == packetNumber }
        if (index != -1) {
            val packetSize = inflightPackets.letter.backing[index].b
            inflightPackets.removeAt(index)
            return packetSize
        }
        return null
    }

    suspend fun onPacketSent(
        packetNumber: PacketNumber, // Changed from QuicPacketNumber (ULong)
        packetSize: ULong           // packetSize in bytes, ULong is fine
    ) = withContext(coroutineContext + QuicOperationContext("bbrPacketSent")) {
<<<<<<< HEAD
        inflightPackets.add(packetNumber j packetSize)
        bytesInFlight += packetSize

=======
        inflightPackets[packetNumber] = packetSize
        bytesInFlight = CongestionWindow(bytesInFlight.bytes + packetSize)

        // Update congestion window based on BBR state
        // Note: BBR's BDP calculation (BW * RTT) needs consistent units.
        // If bottleneckBandwidth is bits/sec and minRTT is usec, conversion is needed.
        // Assuming original formulas were dimensionally intended for their outcome,
        // just applying .value access and wrapping.
>>>>>>> origin/jules_wip_8844705664950451013
        when (state) {
            BBRState.STARTUP -> {
                if (bottleneckBandwidth.bitsPerSecond > 0uL) {
                    // BDP = BW (bytes/sec) * RTT (sec)
                    // BW (bits/sec) / 8 * RTT (usec) / 1_000_000
                    val bdp = (bottleneckBandwidth.bitsPerSecond / 8uL) * minRTT.microseconds / 1_000_000uL
                    cwnd = CongestionWindow((bdp.toDouble() * cwndGain).toULong())
                }
            }
            BBRState.PROBE_BW -> {
                // pacingRate is in bytes/sec if bottleneckBandwidth is in bytes/sec
                // Assuming getPacingRate now returns Bandwidth (bits/sec)
                val pacingRateBitsPerSec = getPacingRate().bitsPerSecond
                // Convert pacingRate to bytes/sec for BDP calculation
                val pacingRateBytesPerSec = pacingRateBitsPerSec / 8uL
                val bdpLikeValue = pacingRateBytesPerSec * minRTT.microseconds / 1_000_000uL
                cwnd = CongestionWindow(bdpLikeValue)
            }
            BBRState.PROBE_RTT -> {
<<<<<<< HEAD
                cwnd = minOf(cwnd, 4uL * 1460uL)
=======
                // Target a small CWND to measure RTT, typically a few MTUs
                cwnd = CongestionWindow(minOf(cwnd.bytes, 4uL * 1460uL)) // Example: 4 * MSS
>>>>>>> origin/jules_wip_8844705664950451013
            }
            BBRState.DRAIN -> {
                // BDP = BW (bytes/sec) * RTT (sec)
                val bdp = (bottleneckBandwidth.bitsPerSecond / 8uL) * minRTT.microseconds / 1_000_000uL
                cwnd = CongestionWindow((bdp.toDouble() / pacingGain).toULong())
            }
        }
    }

    suspend fun onAckReceived(
        ackedPackets: QuicPacketSeries, // Series<PacketNumber> via typealias
        rttSample: RTT,                 // Changed from RTTMicros (ULong)
        ackedBytes: ULong               // ackedBytes in bytes, ULong is fine
    ) = withContext(coroutineContext + QuicOperationContext("bbrAckReceived")) {
<<<<<<< HEAD
        if (rttSample < minRTT) {
=======
        if (rttSample.microseconds < minRTT.microseconds) {
>>>>>>> origin/jules_wip_8844705664950451013
            minRTT = rttSample
            // rtprop might have more complex logic in full BBR, for now mirrors minRTT
            rtprop = rttSample
        }

<<<<<<< HEAD
        if (rttSample > 0uL) {
            val deliveryRate = ackedBytes * 1_000_000uL / rttSample
            if (deliveryRate > bottleneckBandwidth) {
                bottleneckBandwidth = deliveryRate
            }
        }

        ackedPackets.▶.forEach { packetNumber ->
            removePacket(packetNumber)?.let { packetSize ->
                bytesInFlight = maxOf(0uL, bytesInFlight - packetSize)
=======
        if (rttSample.microseconds > 0uL) {
            // deliveryRate in bits/sec = (ackedBytes (bytes) * 8 (bits/byte) * 1_000_000 (usec/sec)) / rttSample (usec)
            val deliveryRateBps = (ackedBytes * 8uL * 1_000_000uL) / rttSample.microseconds
            if (deliveryRateBps > bottleneckBandwidth.bitsPerSecond) {
                bottleneckBandwidth = Bandwidth(deliveryRateBps)
            }
        }

        ackedPackets.▶.forEach { packetNum -> // packetNum is PacketNumber
            inflightPackets[packetNum]?.let { packetSize ->
                inflightPackets.remove(packetNum)
                bytesInFlight = CongestionWindow(maxOf(0uL, bytesInFlight.bytes - packetSize))
>>>>>>> origin/jules_wip_8844705664950451013
            }
        }

        when (state) {
            BBRState.STARTUP -> {
                if (bottleneckBandwidth.bitsPerSecond > 0uL) {
                    state = BBRState.DRAIN
<<<<<<< HEAD
                    pacingGain = 1.0 / 2.77
=======
                    pacingGain = 1.0 / 2.77 // Drain gain (reciprocal of startup gain)
>>>>>>> origin/jules_wip_8844705664950451013
                }
            }
            BBRState.DRAIN -> {
                if (bytesInFlight.bytes <= getBDP().bytes) {
                    state = BBRState.PROBE_BW
                    pacingGain = 1.0
                    cycleIndex = 0 // Reset cycle on entering PROBE_BW
                }
            }
            BBRState.PROBE_BW -> {
                updatePacingGainCycle()
                // TODO: Add logic for PROBE_RTT transition if RTprop expires
            }
            BBRState.PROBE_RTT -> {
                // Exit PROBE_RTT if a round trip has passed or if enough time has passed
                // Simplified: transition back to PROBE_BW if bytesInFlight allows growth
                // This condition should be based on actual BBR logic (e.g. RTprop expiry and packet conservation)
                if (bytesInFlight.bytes <= CongestionWindow(4uL * 1460uL).bytes) { // Placeholder exit condition
                    state = BBRState.PROBE_BW
                    pacingGain = 1.0
                }
            }
        }
    }

    suspend fun onPacketLost(
        lostPackets: QuicPacketSeries // Series<PacketNumber> via typealias
    ) = withContext(coroutineContext + QuicOperationContext("bbrPacketLost")) {
<<<<<<< HEAD
        lostPackets.▶.forEach { packetNumber ->
            removePacket(packetNumber)?.let { packetSize ->
                bytesInFlight = maxOf(0uL, bytesInFlight - packetSize)
            }
        }

        if (state == BBRState.PROBE_RTT) {
            cwnd = maxOf(cwnd / 2uL, 2uL * 1460uL)
        }
=======
        lostPackets.▶.forEach { packetNum -> // packetNum is PacketNumber
            inflightPackets[packetNum]?.let { packetSize ->
                inflightPackets.remove(packetNum)
                bytesInFlight = CongestionWindow(maxOf(0uL, bytesInFlight.bytes - packetSize))
            }
        }

        // BBR's loss response is more nuanced. Typically, it doesn't react to every loss by halving CWND
        // unless it's persistent. It might exit STARTUP or PROBE_BW if loss indicates bottleneck.
        // For PROBE_RTT, it might re-enter STARTUP or PROBE_BW.
        // Simplified: reduce CWND only if not in STARTUP.
        if (state != BBRState.STARTUP) {
             // Example: cwnd = CongestionWindow(maxOf(cwnd.bytes / 2uL, CongestionWindow(2uL * 1460uL).bytes))
             // BBR's actual response is more complex, e.g., setting beta, and not always halving.
             // For now, let's make a less aggressive change or reflect that BBR handles loss by adjusting estimates.
        }
         // if (state == BBRState.PROBE_RTT) { // Original logic was to reduce only in PROBE_RTT
         //    cwnd = CongestionWindow(maxOf(cwnd.bytes / 2uL, 2uL * 1460uL))
         // }
>>>>>>> origin/jules_wip_8844705664950451013
    }

    fun canSend(packetSize: ULong): Boolean =
        bytesInFlight.bytes + packetSize <= cwnd.bytes

    // Returns pacing rate in bits per second
    fun getPacingRate(): Bandwidth =
        Bandwidth((bottleneckBandwidth.bitsPerSecond.toDouble() * pacingGain).toULong())

    // BDP is in bytes. BW (bits/sec) / 8 * RTT (sec)
    private fun getBDP(): CongestionWindow {
        if (minRTT.microseconds == 0uL || minRTT.microseconds == ULong.MAX_VALUE) {
            return CongestionWindow(cwnd.bytes) // Avoid division by zero or using uninitialized RTT
        }
        // Convert BW to bytes/sec, RTT to sec
        val bwBytesPerSec = bottleneckBandwidth.bitsPerSecond / 8uL
        val rttSeconds = minRTT.microseconds.toDouble() / 1_000_000.0
        return CongestionWindow((bwBytesPerSec.toDouble() * rttSeconds).toULong())
    }


    private fun updatePacingGainCycle() {
        cycleIndex = (cycleIndex + 1) % pacingGainCycle.size
        pacingGain = pacingGainCycle[cycleIndex]
    }

    // Return types updated
    fun getConnectionMetrics(): Join<CongestionWindow, Join<Bandwidth, RTT>> =
        cwnd j (bottleneckBandwidth j minRTT)
}