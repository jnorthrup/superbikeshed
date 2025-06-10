package evolution

import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.Series
import evolution.*
import kotlin.math.max
import kotlin.math.min

// BBR congestion controller using TrikeShed patterns
class BBRCongestionController {
    private var state = BBRState.STARTUP
    private var cwnd: CongestionWindow = CongestionWindow(10240uL)
    private var bottleneckBandwidth: Bandwidth = Bandwidth(0uL)
    private var minRTT: RTT = RTT(ULong.MAX_VALUE)
    private var rtprop: RTT = RTT(ULong.MAX_VALUE)
    private var pacingGain = 2.77
    private var cwndGain = 2.0
    private var cycleIndex = 0
    private val pacingGainCycle = doubleArrayOf(1.25, 0.75, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)

    // Packet tracking for congestion control
    private val inflightPackets = mutableMapOf<PacketNumber, ULong>()
    private var bytesInFlight: CongestionWindow = CongestionWindow(0uL)


    suspend fun onPacketSent(
        packetNumber: PacketNumber, // Changed from QuicPacketNumber (ULong)
        packetSize: ULong           // packetSize in bytes, ULong is fine
    ) = withContext(coroutineContext + QuicOperationContext("bbrPacketSent")) {
        inflightPackets[packetNumber] = packetSize
        bytesInFlight = CongestionWindow(bytesInFlight.bytes + packetSize)
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
                cwnd = CongestionWindow(minOf(cwnd.bytes, 4uL * 1460uL))
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
        if (rttSample.microseconds < minRTT.microseconds) {
            minRTT = rttSample
            // rtprop might have more complex logic in full BBR, for now mirrors minRTT
            rtprop = rttSample
        }

        if (rttSample.microseconds > 0uL) {
            val deliveryRateBps = (ackedBytes * 8uL * 1_000_000uL) / rttSample.microseconds
            if (deliveryRateBps > bottleneckBandwidth.bitsPerSecond) {
                bottleneckBandwidth = Bandwidth(deliveryRateBps)
            }
        }

        ackedPackets.▶.forEach { packetNum ->
            inflightPackets[packetNum]?.let { packetSize ->
                inflightPackets.remove(packetNum)
                bytesInFlight = CongestionWindow(maxOf(0uL, bytesInFlight.bytes - packetSize))
            }
        }

        when (state) {
            BBRState.STARTUP -> {
                if (bottleneckBandwidth.bitsPerSecond > 0uL) {
                    state = BBRState.DRAIN
                    pacingGain = 1.0 / 2.77
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
        lostPackets.▶.forEach { packetNum ->
            inflightPackets[packetNum]?.let { packetSize ->
                inflightPackets.remove(packetNum)
                bytesInFlight = CongestionWindow(maxOf(0uL, bytesInFlight.bytes - packetSize))
            }
        }

        // BBR's loss response - simplified for now
        if (state != BBRState.STARTUP) {
            // BBR typically doesn't react aggressively to loss like traditional CC algorithms
        }
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