package borg.trikeshed.gossip

import borg.ipfs.IpfsPubSubService
import borg.trikeshed.io.network.DatagramPacket
import borg.trikeshed.io.network.NetworkAddress
import borg.trikeshed.io.network.QuicNetworkService
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.orchestration.BaseOrchestrationAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

@Serializable
data class SubnetToken(
    val subnetId: String,
    val networkAddress: NetworkAddress,
    val publicKey: String,
    val confidence: Double = 1.0
)

@Serializable
data class GossipEvent(
    val timestamp: Long,
    val source: String,
    val topic: String,
    val content: String,
    val confidence: Double
)

class ConcentricSubnetAgent(
    parentCoroutineContext: CoroutineContext,
    override val ipfsPubSubService: IpfsPubSubService,
    private val quicNetworkService: QuicNetworkService,
    givenAgentId: String? = null
) : BaseOrchestrationAgent(parentCoroutineContext, ipfsPubSubService, givenAgentId) {

    private val subnetState = MutableStateFlow<Map<String, SubnetToken>>(emptyMap())
    val subnetStateFlow = subnetState.asStateFlow()

    private val gossipEvents = MutableStateFlow<Series<GossipEvent>>(Series.empty())
    val gossipEventsFlow = gossipEvents.asStateFlow()

    override fun start() {
        println("ConcentricSubnetAgent $agentId starting")
        
        // Start QUIC network service
        launch {
            try {
                quicNetworkService.bind(null) // Bind to any available port
                println("Agent $agentId bound to ${quicNetworkService.localAddress}")
            } catch (e: Exception) {
                println("Agent $agentId failed to bind QUIC service: ${e.message}")
            }
        }

        // Subscribe to IPFS pubsub for subnet gossip
        launch {
            try {
                ipfsPubSubService.subscribe("subnet/$agentId").collect { message ->
                    try {
                        val event = Json.decodeFromString<GossipEvent>(message)
                        processGossipEvent(event)
                    } catch (e: Exception) {
                        println("Agent $agentId failed to decode gossip event: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("Agent $agentId error subscribing to subnet gossip: ${e.message}")
            }
        }

        // Start QUIC packet receiver
        launch {
            while (isActive) {
                try {
                    val packet = quicNetworkService.receive(1024)
                    processQuicPacket(packet)
                } catch (e: Exception) {
                    if (isActive) {
                        println("Agent $agentId error receiving QUIC packet: ${e.message}")
                    }
                }
            }
        }
    }

    private fun processGossipEvent(event: GossipEvent) {
        gossipEvents.value = gossipEvents.value + event
        // Process high confidence events
        if (event.confidence > 0.8) {
            launch {
                analyzeHighConfidenceEvent(event)
            }
        }
    }

    private suspend fun analyzeHighConfidenceEvent(event: GossipEvent) {
        // TODO: Implement LLM analysis of high confidence events
        val analysis = "Analysis of ${event.content} from ${event.source}"
        gossipAbout("subnet/analysis", Series.of("analysis" j analysis))
    }

    private fun processQuicPacket(packet: DatagramPacket) {
        // Process incoming QUIC packets
        // TODO: Implement QUIC packet processing logic
    }

    suspend fun joinSubnet(token: SubnetToken) {
        subnetState.value = subnetState.value + (token.subnetId to token)
        gossipAbout("subnet/join", Series.of(
            "subnetId" j token.subnetId,
            "address" j "${token.networkAddress.first}:${token.networkAddress.second}",
            "publicKey" j token.publicKey
        ))
    }

    suspend fun leaveSubnet(subnetId: String) {
        subnetState.value = subnetState.value - subnetId
        gossipAbout("subnet/leave", Series.of("subnetId" j subnetId))
    }

    override fun stop() {
        super.stop()
        quicNetworkService.close()
    }
} 