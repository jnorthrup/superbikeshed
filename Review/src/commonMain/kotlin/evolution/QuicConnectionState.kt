package evolution

import kotlin.random.Random
// Import new types from QuicSpecTypes.kt & QuicTypes.kt
import evolution.ConnectionID
import evolution.PacketNumber

// QUIC Connection State (simplified)
enum class QuicConnectionStateEnum {
    INITIAL,
    HANDSHAKE,
    CONNECTED,
    CLOSING,
    CLOSED
}

// QUIC Connection state holder
// Updated to use strong types from QuicSpecTypes.kt
class QuicConnection(
    val connectionId: ConnectionID = generateConnectionId(),
    var state: QuicConnectionStateEnum = QuicConnectionStateEnum.INITIAL,
    var packetNumber: PacketNumber = PacketNumber(0uL), // Initialize with PacketNumber type
    var largestReceivedPacketNumber: PacketNumber = PacketNumber(0uL) // Initialize with PacketNumber type
) {
    // Generates a random 8-byte connection ID, now returns ConnectionID
    private companion object {
        private val random = Random.Default
        fun generateConnectionId(): ConnectionID {
            val cidBytes = ByteArray(8)
            random.nextBytes(cidBytes)
            return ConnectionID(cidBytes) // Wrap in ConnectionID
        }
    }

    // No change needed for equals and hashCode if ConnectionID and PacketNumber have proper data class/value class implementations
    // However, if they are value classes wrapping arrays, direct comparison might be an issue without custom equals/hashCode
    // For ByteArray in ConnectionID, its equals/hashCode is by identity. QuicSpecTypes.kt ConnectionID has proper equals/hashCode.
    // PacketNumber (ULong) as a value class will have correct equals/hashCode.

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        // Using javaClass for compatibility in common code, could be this::class
        if (other == null || this.javaClass != other.javaClass) return false

        other as QuicConnection

        if (connectionId != other.connectionId) return false // Relies on ConnectionID.equals()
        if (state != other.state) return false
        if (packetNumber != other.packetNumber) return false // Relies on PacketNumber.equals()
        if (largestReceivedPacketNumber != other.largestReceivedPacketNumber) return false // Relies on PacketNumber.equals()

        return true
    }

    override fun hashCode(): Int {
        var result = connectionId.hashCode() // Relies on ConnectionID.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + packetNumber.hashCode() // Relies on PacketNumber.hashCode()
        result = 31 * result + largestReceivedPacketNumber.hashCode() // Relies on PacketNumber.hashCode()
        return result
    }
}
