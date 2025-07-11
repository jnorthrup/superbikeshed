package fiduciary.concentric

import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages inter-group communication using a gossip protocol.
 * This class is responsible for propagating state updates and discoveries
 * between different concentric groups.
 *
 * @param agentId The NUID of the current agent.
 * @param quicProtocol The QUIC concentric protocol instance for communication.
 * @param groupManager The group manager for understanding network topology and group memberships.
 * @param sharedState The shared state manager to propagate CRDT updates.
 */
class InterGroupComm(
    private val agentId: NUID,
    private val quicProtocol: QuicConcentricProtocol,
    private val groupManager: GroupTemplates,
    private val sharedState: SharedState
) {

    private val config = InterGroupCommConfig()

    /**
     * Starts the gossip protocol for inter-group communication.
     * This coroutine will periodically select a peer and exchange state information.
     */
    fun startGossip(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(config.gossipInterval)
                gossipWithRandomPeer()
            }
        }
    }

    /**
     * Selects a random peer from a different group and initiates a gossip exchange.
     */
    private suspend fun gossipWithRandomPeer() {
        val allAgents = groupManager.getAllAgentIds()
        val otherAgents = allAgents.filter { it != agentId }

        if (otherAgents.isNotEmpty()) {
            val randomPeer = otherAgents.random(Random.Default)
            val peerGroup = groupManager.getAgentGroup(randomPeer)
            val myGroup = groupManager.getAgentGroup(agentId)

            // Only gossip if the peer is in a different group or if it's a special case for intra-group gossip
            if (peerGroup != myGroup || config.allowIntraGroupGossip) {
                println("Agent $agentId gossiping with $randomPeer (Group: $peerGroup)")
                exchangeGossip(randomPeer)
            }
        } else {
            println("No other agents to gossip with.")
        }
    }

    /**
     * Exchanges gossip messages with a target peer.
     * This involves sending local state summaries and receiving peer's state summaries.
     *
     * @param peerId The NUID of the peer to gossip with.
     */
    private suspend fun exchangeGossip(peerId: NUID) {
        try {
            // 1. Send local state summary (e.g., CRDT versions, discovery hashes)
            val localStateSummary = createLocalStateSummary()
            quicProtocol.sendGossipMessage(agentId, peerId, localStateSummary)

            // 2. Request peer's state summary
            val peerStateSummary = quicProtocol.requestGossipSummary(agentId, peerId)

            // 3. Compare summaries and request missing/newer data
            val missingCrdts = compareSummaries(localStateSummary, peerStateSummary)
            missingCrdts.forEach { crdtId ->
                val fullCrdt = quicProtocol.requestFullCrdt(agentId, peerId, crdtId)
                fullCrdt?.let { sharedState.handleSharedStateUpdate(it) }
            }

            println("Gossip exchange with $peerId completed.")
        } catch (e: Exception) {
            println("Gossip exchange with $peerId failed: ${e.message}")
        }
    }

    /**
     * Creates a summary of the local state to be sent during gossip.
     * This could include CRDT versions, hashes of discoveries, etc.
     */
    private fun createLocalStateSummary(): GossipStateSummary {
        // For simplicity, just sending a list of CRDT IDs and their timestamps/versions
        val crdtSummaries = sharedState.crdts.map { (id, crdt) ->
            CRDTSummary(id, Clock.System.now()) // In a real CRDT, this would be a version vector or similar
        }
        return GossipStateSummary(crdtSummaries)
    }

    /**
     * Compares local and peer state summaries to identify missing or outdated CRDTs.
     * @return A list of CRDT IDs that are missing or newer on the peer.
     */
    private fun compareSummaries(
        local: GossipStateSummary,
        peer: GossipStateSummary
    ): List<String> {
        val missing = mutableListOf<String>()
        val localMap = local.crdtSummaries.associateBy { it.id }

        peer.crdtSummaries.forEach { peerCrdt ->
            val localCrdt = localMap[peerCrdt.id]
            if (localCrdt == null || peerCrdt.timestamp > localCrdt.timestamp) {
                // If local doesn't have it, or peer's version is newer
                missing.add(peerCrdt.id)
            }
        }
        return missing
    }

    /**
     * Configuration for the inter-group communication (gossip protocol).
     */
    data class InterGroupCommConfig(
        val gossipInterval: Duration = 5000.milliseconds, // How often to initiate gossip
        val allowIntraGroupGossip: Boolean = false // Whether to gossip within the same group
    )

    /**
     * Represents a summary of the state for gossip exchange.
     */
    @Serializable
    data class GossipStateSummary(
        val crdtSummaries: List<CRDTSummary>
    )

    /**
     * Represents a summary of a single CRDT for gossip exchange.
     */
    @Serializable
    data class CRDTSummary(
        val id: String,
        val timestamp: Instant // Placeholder for version information
    )
}
