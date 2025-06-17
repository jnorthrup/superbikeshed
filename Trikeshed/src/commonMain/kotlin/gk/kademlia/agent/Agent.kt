package gk.kademlia.agent

import gk.kademlia.id.NUID
import gk.kademlia.net.NetMask
import gk.kademlia.routing.RoutingTable
import gk.kademlia.security.CryptoService
import gk.kademlia.security.DummyCryptoService
import gk.kademlia.security.KeyPair
import gk.kademlia.bitops.impl.BigIntOps
import gk.kademlia.codec.SecureKademliaCodec
import gk.kademlia.bitswap.BitswapEngine
import gk.kademlia.bitswap.BitswapNetworkSender
import gk.kademlia.bitswap.BitswapNetworkServiceImpl
import gk.kademlia.bitswap.BlockStore
import gk.kademlia.bitswap.InMemoryBlockStore
import gk.kademlia.bitswap.WantManager
import borg.trikeshed.num.BigInt as BigInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
// Removed Random import as it's used in DummyNetworkService, not directly here.
// import kotlin.random.Random
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import gk.kademlia.messages.KademliaPayload // For processIncomingKademliaPayload
import gk.kademlia.messages.BitswapEnvelope
import gk.kademlia.messages.PingRequest
import gk.kademlia.messages.PongResponse
// Potentially FindNodeRequest, NodesResponse if handling them here too
import gk.kademlia.bitswap.BitswapMessage // For decoding
import gk.kademlia.include.SubnetRoute // For processIncomingKademliaPayload
import gk.kademlia.KademliaConfig // For NODE_DISCOVERY_COUNT
import kotlinx.serialization.json.Json // For decoding BitswapMessage
import kotlinx.serialization.decodeFromString // For decoding

interface Agent<TNum : Comparable<TNum>, Sz : NetMask<TNum>> {
    /**
     * Network Unique Id
     */
    val NUID: NUID<TNum>
    val routingTable: RoutingTable<TNum, Sz>
    val keyPair: KeyPair // Added keyPair to the interface
    fun start()
    fun stop()
}

class WorldAgent(
    // cryptoService is now the primary constructor parameter needed to derive NUID and KeyPair.
    private val cryptoServiceInstance: CryptoService = DummyCryptoService()
    // RoutingTable is no longer taken as a constructor parameter, as it depends on the generated NUID.
) : Agent<BigInteger, WorldNetwork> {

    override val keyPair: KeyPair = cryptoServiceInstance.generateKeyPair()

    override val NUID: NUID<BigInteger> = NUID.createNUIDFromPublicKey(
        keyPair.publicKey,
        cryptoServiceInstance,
        WorldNetwork, // WorldNetwork is defined below as the NetMask for this agent
        BigIntOps     // Explicitly provide BigIntOps for BigInteger NUIDs
    )

    // WorldAgent now creates its own RoutingTable (WorldRouter) using the generated NUID.
    override val routingTable: RoutingTable<BigInteger, WorldNetwork> = WorldRouter(NUID)

    private val agentScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Instantiate SecureKademliaCodec using the agent's cryptoService and keyPair
    private val codec = SecureKademliaCodec(cryptoServiceInstance, keyPair)

    // Instantiate DummyNetworkService with the codec
    private val networkService: NetworkService<BigInteger, WorldNetwork> = DummyNetworkService(codec) // This is Kademlia's network service

    private val routingManager: RoutingManager<BigInteger, WorldNetwork>

    // --- Bitswap Components ---
    private val localPeerIdString: String = NUID.id?.toString() ?: "unknown_agent_${kotlin.random.Random.nextInt()}"

    val bitswapBlockStore: BlockStore = InMemoryBlockStore()
    val bitswapWantManager: WantManager = WantManager()

    // BitswapNetworkSender is now generic, so specify type arguments
    private val bitswapNetworkSender: BitswapNetworkSender<BigInteger, WorldNetwork> = BitswapNetworkServiceImpl(
        localPeerIdString = localPeerIdString,
        kademliaNetworkService = networkService
    )

    // BitswapEngine is now generic, so specify type arguments
    val bitswapEngine: BitswapEngine<BigInteger, WorldNetwork> = BitswapEngine(
        scope = agentScope,
        blockStore = bitswapBlockStore,
        wantManager = bitswapWantManager,
        networkSender = bitswapNetworkSender,
        localPeerId = localPeerIdString
    )
    // --- End Bitswap Components ---

    init {
        routingManager = RoutingManager(NUID, routingTable, networkService, agentScope)
        println("WorldAgent ${NUID.id ?: "ID_INIT_ERROR"} initialized with PublicKey: ${keyPair.publicKey.getEncoded().contentToString()}")
        println("WorldAgent PeerID for Bitswap: $localPeerIdString")

        // Conceptual: Setup listener for incoming BitswapEnvelopes
        // This would typically be part of a larger message dispatching system.
        // For now, we're just showing the structure.
        // listenForBitswapEnvelopes()
    }

    // private fun listenForBitswapEnvelopes() {
    //    agentScope.launch {
    //        // Assuming `networkService` could somehow provide a flow of received KademliaPayloads
    //        // or the agent has a central message bus. This is highly conceptual.
    //        // Example: someChannelOrFlowOfReceivedMessages.collect { (sourceRoute, payload) ->
    //        //    if (payload is BitswapEnvelope) {
    //        //        try {
    //        //            val bitswapMessage = Json.decodeFromString<BitswapMessage>(payload.bitswapMessageBytes.decodeToString())
    //        //            bitswapEngine.handleIncomingMessage(sourceRoute, bitswapMessage)
    //        //        } catch (e: Exception) {
    //        //            println("Error decoding BitswapMessage from envelope: ${"$"}{e.message}")
    //        //        }
    //        //    }
    //        // }
    //    }
    // }

    override fun start() {
        println("WorldAgent ${NUID.id ?: "ID_START_ERROR"} starting...")
        routingManager.start()
        bitswapEngine.start() // Start the Bitswap engine
        println("WorldAgent ${NUID.id ?: "ID_START_ERROR"} started successfully.")
    }

    override fun stop() {
        println("WorldAgent ${NUID.id ?: "ID_STOP_ERROR"} stopping...")
        bitswapEngine.stop() // Stop the Bitswap engine
        routingManager.stop()
        agentScope.cancel() // Cancel the scope when agent stops
        println("WorldAgent ${NUID.id ?: "ID_STOP_ERROR"} stopped successfully.")
    }

    // Central dispatch for incoming Kademlia payloads
    suspend fun processIncomingKademliaPayload(sourceRoute: SubnetRoute<BigInteger>, payload: KademliaPayload) {
        val agentIdStr = NUID.id?.toString() ?: "UNKNOWN_AGENT"
        val sourceNuidStr = sourceRoute.nuid.id?.toString() ?: "UNKNOWN_SOURCE_NUID"
        println("WorldAgent $agentIdStr: Received KademliaPayload of type ${payload::class.simpleName} from $sourceNuidStr")

        when (payload) {
            is BitswapEnvelope -> {
                // Deserialize the inner BitswapMessage
                // Using Json for now, consistent with BitswapNetworkServiceImpl
                try {
                    val bitswapMessageJson = payload.bitswapMessageBytes.decodeToString()
                    val bitswapMessage = Json { ignoreUnknownKeys = true }.decodeFromString<BitswapMessage>(bitswapMessageJson)

                    println("WorldAgent $agentIdStr: Decoded BitswapMessage from envelope. Original sender in envelope: ${payload.sourcePeerIdString}, Kademlia sender: $sourceNuidStr")

                    bitswapEngine.handleIncomingMessage(sourceRoute, bitswapMessage)
                } catch (e: Exception) {
                    println("WorldAgent $agentIdStr: Error decoding BitswapMessage from envelope: ${e.message}")
                    e.printStackTrace()
                }
            }
            is PingRequest -> {
                println("WorldAgent $agentIdStr: Received PingRequest ${payload.uniqueId} from $sourceNuidStr. Conceptual handling: sending Pong.")
                // val pongResponse = PongResponse(payload.uniqueId)
                // agentScope.launch { networkService.sendMessage(sourceRoute, pongResponse) }
            }
            is PongResponse -> {
                 println("WorldAgent $agentIdStr: Received PongResponse ${payload.uniqueId} from $sourceNuidStr. (Conceptual handling)")
            }
            else -> {
                println("WorldAgent $agentIdStr: Received unhandled KademliaPayload type: ${payload::class.simpleName} from $sourceNuidStr")
            }
        }
    }

    suspend fun discoverPeersAndInitiateBitswap(targetNUID: NUID<BigInteger>? = null) {
        val discoveryTarget = targetNUID ?: NUID // If no specific target, discover around self
        val agentIdStr = this.NUID.id?.toString() ?: "UNKNOWN_AGENT"
        val discoveryTargetStr = discoveryTarget.id?.toString() ?: "UNKNOWN_TARGET"

        println("WorldAgent $agentIdStr: Discovering peers for Bitswap around $discoveryTargetStr...")

        // Use Kademlia NetworkService to find peers.
        // networkService is currently DummyNetworkService. Its findNode returns emptyList by default.
        // For actual testing, DummyNetworkService.findNode would need to be enhanced or this service mocked.
        val discoveredRoutes = networkService.findNode(discoveryTarget, KademliaConfig.NODE_DISCOVERY_COUNT)

        if (discoveredRoutes.isNotEmpty()) {
            println("WorldAgent $agentIdStr: Discovered ${discoveredRoutes.size} peers. Informing BitswapEngine.")
            // Ensure we are passing List<SubnetRoute<BigInteger>> which matches BitswapEngine's expectation
            bitswapEngine.addPotentialPeers(discoveredRoutes)
        } else {
            println("WorldAgent $agentIdStr: No peers discovered for Bitswap around $discoveryTargetStr.")
        }
    }
}

class WorldRouter(agentNUID: NUID<BigInteger>) : RoutingTable<BigInteger, WorldNetwork>(agentNUID)


object WorldNetwork : NetMask<BigInteger> {
    override val bits: Int
        get() = 128
}
