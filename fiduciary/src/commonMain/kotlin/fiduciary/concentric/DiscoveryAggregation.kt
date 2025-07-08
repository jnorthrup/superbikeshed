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
 * Aggregates discoveries from various concentric groups and confirms anomalies
 * based on quorum mechanisms. This system provides a hierarchical view of findings.
 *
 * @param quorumMechanics The QuorumMechanics instance for consensus building.
 * @param sharedState The SharedState instance for accessing CRDTs of discoveries.
 * @param quicProtocol The QUIC concentric protocol instance for receiving discovery updates.
 */
class DiscoveryAggregation(
    private val quorumMechanics: QuorumMechanics,
    private val sharedState: SharedState,
    private val quicProtocol: QuicConcentricProtocol
) {

    // Stores aggregated discoveries, keyed by discovery ID
    private val aggregatedDiscoveries: ConcurrentHashMap<String, AggregatedDiscovery> = ConcurrentHashMap()

    /**
     * Starts listening for incoming discovery updates from other agents.
     */
    fun startListeningForDiscoveries(scope: CoroutineScope) {
        scope.launch {
            quicProtocol.incomingDiscoveryFlow.collect { discovery ->
                handleIncomingDiscovery(discovery)
            }
        }
    }

    /**
     * Handles an incoming discovery from another agent.
     * This involves aggregating the discovery and potentially initiating quorum for confirmation.
     * @param discovery The incoming Discovery object.
     */
    private suspend fun handleIncomingDiscovery(discovery: Discovery) {
        println("Received discovery: ${discovery.id} from ${discovery.sourceAgentId}")

        aggregatedDiscoveries.compute(discovery.id) { _, existing ->
            if (existing == null) {
                AggregatedDiscovery(discovery.id, mutableListOf(discovery))
            } else {
                existing.addDiscovery(discovery)
                existing
            }
        }

        // Check if enough evidence exists to initiate quorum for this discovery
        val currentAggregated = aggregatedDiscoveries[discovery.id]
        if (currentAggregated != null && currentAggregated.discoveries.size >= quorumMechanics.getMinParticipantsForQuorum(discovery.groupTemplate)) {
            initiateDiscoveryQuorum(currentAggregated)
        }
    }

    /**
     * Initiates a quorum process for a given aggregated discovery.
     * @param aggregatedDiscovery The aggregated discovery to confirm.
     */
    private suspend fun initiateDiscoveryQuorum(aggregatedDiscovery: AggregatedDiscovery) {
        println("Initiating quorum for discovery: ${aggregatedDiscovery.id}")

        val participants = aggregatedDiscovery.discoveries.map { it.sourceAgentId }.toSet()
        val groupTemplate = aggregatedDiscovery.discoveries.first().groupTemplate // Assuming all discoveries for an ID have the same group template

        val quorumResult = quorumMechanics.achieveQuorum(
            groupTemplate = groupTemplate,
            participants = participants,
            proposal = aggregatedDiscovery.id // The discovery ID is the proposal to vote on
        ) {
            // This lambda defines the voting logic for each participant
            // In a real scenario, this would involve more complex validation
            true // For simplicity, always vote true if participating
        }

        if (quorumResult.isSuccess) {
            println("Discovery ${aggregatedDiscovery.id} confirmed by quorum!")
            // Here, you would typically store the confirmed discovery in a persistent store
            // and potentially propagate it further up the hierarchy or to a reporting system.
            val confirmedDiscovery = ConfirmedDiscovery(
                id = aggregatedDiscovery.id,
                timestamp = Clock.System.now(),
                details = aggregatedDiscovery.discoveries.first().details, // Take details from one of the discoveries
                confirmingAgents = quorumResult.getOrThrow().confirmedParticipants
            )
            // Example: Add to a shared state CRDT for confirmed discoveries
            sharedState.addOrUpdateCRDT(SharedState.LWWRegister.create("confirmed_discovery_${confirmedDiscovery.id}", confirmedDiscovery, NUID("aggregator")))

        } else {
            println("Discovery ${aggregatedDiscovery.id} failed to achieve quorum: ${quorumResult.exceptionOrNull()?.message}")
        }
    }

    /**
     * Represents a single discovery made by an agent.
     */
    @Serializable
    data class Discovery(
        val id: String,
        val sourceAgentId: NUID,
        val groupTemplate: GroupTemplate,
        val details: String, // e.g., "Anomaly detected in speech pattern", "New topic identified"
        val timestamp: Instant = Clock.System.now()
    )

    /**
     * Represents an aggregated collection of similar discoveries.
     */
    data class AggregatedDiscovery(
        val id: String,
        val discoveries: MutableList<Discovery>
    ) {
        fun addDiscovery(discovery: Discovery) {
            discoveries.add(discovery)
        }
    }

    /**
     * Represents a discovery that has been confirmed by a quorum.
     */
    @Serializable
    data class ConfirmedDiscovery(
        val id: String,
        val timestamp: Instant,
        val details: String,
        val confirmingAgents: Set<NUID>
    )
}
