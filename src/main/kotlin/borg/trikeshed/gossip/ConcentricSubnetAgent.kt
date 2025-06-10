package borg.trikeshed.gossip

import borg.ipfs.IpfsPubSubService
import borg.trikeshed.core.Join
import borg.trikeshed.core.Series
import borg.trikeshed.core.emptySeries
import borg.trikeshed.core.j
import borg.trikeshed.io.network.DatagramPacket
import borg.trikeshed.io.network.NetworkAddress
import borg.trikeshed.io.network.QuicNetworkService
import borg.trikeshed.orchestration.BaseOrchestrationAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

// Type aliases for common primitives
typealias SubnetId = String
typealias PublicKey = String
typealias Confidence = Double
typealias Timestamp = Long

@JvmInline
value class SubnetToken(val value: Join<SubnetId, Join<NetworkAddress, Join<PublicKey, Confidence>>>)

@JvmInline
value class GossipEvent(val value: Join<Timestamp, Join<String, Join<String, Join<String, Confidence>>>>)

class ConcentricSubnetAgent(
    parentCoroutineContext: CoroutineContext,
    override val ipfsPubSubService: IpfsPubSubService,
    private val quicNetworkService: QuicNetworkService,
    givenAgentId: String? = null
) : BaseOrchestrationAgent(parentCoroutineContext, ipfsPubSubService, givenAgentId) {

    private val subnetState = MutableStateFlow<Series<Join<SubnetId, SubnetToken>>>(emptySeries())
    val subnetStateFlow = subnetState.asStateFlow()

    private val gossipEvents = MutableStateFlow<Series<GossipEvent>>(emptySeries())
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
        if (event.value.second.second.second.second > 0.8) {
            launch {
                analyzeHighConfidenceEvent(event)
            }
        }
    }

    private suspend fun analyzeHighConfidenceEvent(event: GossipEvent) {
        // TODO: Implement LLM analysis of high confidence events
        val analysis = "Analysis of ${event.value.second.second.first} from ${event.value.second.first}"
        gossipAbout("subnet/analysis", Series.of("analysis" j analysis))
    }

    private fun processQuicPacket(packet: DatagramPacket) {
        // Process incoming QUIC packets
        // TODO: Implement QUIC packet processing logic
    }

    suspend fun joinSubnet(subnetId: SubnetId, address: NetworkAddress, publicKey: PublicKey, confidence: Confidence = 1.0) {
        val token = SubnetToken(subnetId j (address j (publicKey j confidence)))
        subnetState.value = subnetState.value + (subnetId j token)
        gossipAbout("subnet/join", Series.of(
            "subnetId" j subnetId,
            "address" j "${address.first}:${address.second}",
            "publicKey" j publicKey
        ))
    }

    suspend fun leaveSubnet(subnetId: SubnetId) {
        subnetState.value = subnetState.value.α { it.first != subnetId }
        gossipAbout("subnet/leave", Series.of("subnetId" j subnetId))
    }

    override fun stop() {
        super.stop()
        quicNetworkService.close()
    }
} 