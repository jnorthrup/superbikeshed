package evolution

import borg.trikeshed.lib.Series
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline
// Import PacketNumber from QuicSpecTypes.kt; assuming it's in the same 'evolution' package
// If QuicSpecTypes.kt is in a sub-package, the import path would need adjustment.
// For this task, assuming 'evolution.PacketNumber' is resolvable.
import evolution.PacketNumber // Or could use 'import evolution.*' if many types were needed
// Value classes Bandwidth, RTT, CongestionWindow moved to QuicSpecTypes.kt

// Old type aliases comments remain relevant or can be removed if fully superseded.
// typealias BandwidthEstimate = ULong // Now in QuicSpecTypes as Bandwidth
// typealias RTTMicros = ULong         // Now in QuicSpecTypes as RTT
// typealias QuicPacketNumber = ULong  // Removed, PacketNumber from QuicSpecTypes.kt is used
// typealias CongestionWindow = ULong  // Now in QuicSpecTypes as CongestionWindow

enum class BBRState {
    STARTUP, DRAIN, PROBE_BW, PROBE_RTT
}

data class QuicOperationContext(val name: String) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<QuicOperationContext>
}

// Updated to use PacketNumber from QuicSpecTypes.kt
typealias QuicPacketSeries = Series<PacketNumber>