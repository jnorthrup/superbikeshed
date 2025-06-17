package gk.kademlia.agent

import kotlinx.serialization.Serializable
import gk.kademlia.id.NUID // Assuming NUID is a serializable type or has a serializer
import borg.trikeshed.num.BigInt // Assuming BigInt is serializable or has a serializer

// If NUID is not directly serializable, you might need a surrogate or custom serializer.
// For now, we assume it can be handled by kotlinx.serialization, possibly via @Contextual or a registered serializer.
// Similar assumption for BigInt if it's used directly in NUID or other event fields.

@Serializable
sealed interface KademliaEvent {
    val timestamp: Long // Common field
}

@Serializable
data class PingEvent(
    val myNuid: NUID<BigInt>, // Example: mapping my_uid
    override val timestamp: Long = System.currentTimeMillis()
    // Add other fields based on EventTypes.PING's EventKeys:
    // val recentNuids: List<NUID<BigInt>> = emptyList(),
    // val laggingNuids: List<NUID<BigInt>> = emptyList(),
    // val myRouteInfo: String = "" // Placeholder for actual route info type
) : KademliaEvent

@Serializable
data class PongEvent(
    val respondingToNuid: NUID<BigInt>,
    override val timestamp: Long = System.currentTimeMillis()
    // Add other fields based on EventTypes.PONG's EventKeys:
    // val knownNuids: List<NUID<BigInt>> = emptyList(),
    // val routes: List<String> = emptyList(), // Placeholder
    // val pubKeys: List<String> = emptyList(), // Placeholder
    // val suggest: String? = null // Placeholder
) : KademliaEvent

@Serializable
data class JoinRequestEvent( // Renamed from JOIN to be more descriptive
    val proposedNuid: NUID<BigInt>,
    override val timestamp: Long = System.currentTimeMillis()
    // val formerNuid: NUID<BigInt>? = null,
    // val myPublicKey: String // Placeholder
) : KademliaEvent

@Serializable
data class JoinResponseEvent(
    val accepted: Boolean,
    val message: String,
    val knownNodes: List<NUID<BigInt>> = emptyList(), // Example field for response
    override val timestamp: Long = System.currentTimeMillis()
) : KademliaEvent


// Add other event data classes here based on EventTypes enum:
// e.g., LagdEvent, BubyEvent

// Note: The actual fields and their types (NUID<BigInt>, String, List<...>, etc.)
// need to be carefully mapped from the EventKey definitions in EventTypes.kt
// and the actual data requirements of the Kademlia implementation.
// Placeholders are used for some fields for brevity.
// Ensure that NUID and BigInt have serializers available for kotlinx.serialization.
