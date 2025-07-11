package fiduciary.concentric

import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages shared state within a concentric group using CRDTs (Conflict-free Replicated Data Types).
 * This class ensures eventual consistency and handles conflict resolution.
 *
 * @param groupId The NUID of the concentric group this shared state belongs to.
 * @param quicProtocol The QUIC concentric protocol instance for communication.
 */
class SharedState(
    private val groupId: NUID,
    private val quicProtocol: QuicConcentricProtocol
) {

    // Using a ConcurrentHashMap to store CRDTs, keyed by their unique ID
    private val crdts: ConcurrentHashMap<String, CRDT<*>> = ConcurrentHashMap()

    /**
     * Adds or updates a CRDT in the shared state.
     * If a CRDT with the same ID already exists, it will be merged with the new one.
     * @param crdt The CRDT to add or update.
     */
    fun <T> addOrUpdateCRDT(crdt: CRDT<T>) {
        crdts.merge(crdt.id, crdt) { existing, new ->
            @Suppress("UNCHECKED_CAST")
            (existing as CRDT<T>).merge(new)
        }
        // Propagate the change to other members of the group
        quicProtocol.propagateSharedStateUpdate(groupId, crdt)
    }

    /**
     * Retrieves a CRDT by its ID.
     * @param id The ID of the CRDT to retrieve.
     * @return The CRDT, or null if not found.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getCRDT(id: String): CRDT<T>? {
        return crdts[id] as? CRDT<T>
    }

    /**
     * Handles an incoming shared state update from another agent.
     * Merges the incoming CRDT with the local version.
     * @param incomingCrdt The CRDT received from another agent.
     */
    fun <T> handleSharedStateUpdate(incomingCrdt: CRDT<T>) {
        addOrUpdateCRDT(incomingCrdt)
    }

    /**
     * Represents a generic Conflict-free Replicated Data Type (CRDT).
     * Implementations will define their specific merge logic.
     */
    interface CRDT<T> {
        val id: String
        val value: T
        fun merge(other: CRDT<T>): CRDT<T>
    }

    /**
     * Example implementation of a simple G-Counter (Grow-only Counter) CRDT.
     * This counter only allows increments.
     */
    @Serializable
    data class GCounter(
        override val id: String,
        val increments: Map<NUID, Int> // Map of agent ID to its increments
    ) : CRDT<Int> {

        override val value: Int
            get() = increments.values.sum()

        override fun merge(other: CRDT<Int>): CRDT<Int> {
            require(other is GCounter) { "Can only merge GCounter with GCounter" }
            val mergedIncrements = (increments.asSequence() + other.increments.asSequence())
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, values) -> values.maxOrNull() ?: 0 }
            return GCounter(id, mergedIncrements)
        }

        fun increment(agentId: NUID, amount: Int = 1): GCounter {
            require(amount >= 0) { "Increment amount must be non-negative" }
            val current = increments[agentId] ?: 0
            return GCounter(id, increments + (agentId to (current + amount)))
        }
    }

    /**
     * Example implementation of a simple G-Set (Grow-only Set) CRDT.
     * This set only allows additions.
     */
    @Serializable
    data class GSet<T>(
        override val id: String,
        val elements: Set<T>
    ) : CRDT<Set<T>> {

        override val value: Set<T>
            get() = elements

        override fun merge(other: CRDT<Set<T>>): CRDT<Set<T>> {
            require(other is GSet<*>) { "Can only merge GSet with GSet" }
            @Suppress("UNCHECKED_CAST")
            return GSet(id, elements + (other as GSet<T>).elements)
        }

        fun add(element: T): GSet<T> {
            return GSet(id, elements + element)
        }
    }

    /**
     * Example implementation of a simple LWW-Register (Last-Write-Wins Register) CRDT.
     * The value with the latest timestamp wins.
     */
    @Serializable
    data class LWWRegister<T>(
        override val id: String,
        val data: T,
        val timestamp: Instant,
        val agentId: NUID
    ) : CRDT<T> {

        override val value: T
            get() = data

        override fun merge(other: CRDT<T>): CRDT<T> {
            require(other is LWWRegister<*>) { "Can only merge LWWRegister with LWWRegister" }
            @Suppress("UNCHECKED_CAST")
            return if (this.timestamp >= (other as LWWRegister<T>).timestamp) this else other
        }

        companion object {
            fun <T> create(id: String, data: T, agentId: NUID): LWWRegister<T> {
                return LWWRegister(id, data, Clock.System.now(), agentId)
            }
        }
    }
}
