package gk.kademlia.agent

import gk.kademlia.id.NUID
import gk.kademlia.net.NetMask
import gk.kademlia.routing.RoutingTable
import gk.kademlia.security.CryptoService
import gk.kademlia.security.DummyCryptoService
import gk.kademlia.security.KeyPair
import gk.kademlia.bitops.impl.BigIntOps // Added this import
import borg.trikeshed.num.BigInt as BigInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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
    private val networkService: NetworkService<BigInteger, WorldNetwork> = DummyNetworkService()
    private val routingManager: RoutingManager<BigInteger, WorldNetwork>

    init {
        // NUID and routingTable are initialized above.
        // keyPair is also initialized above.
        routingManager = RoutingManager(NUID, routingTable, networkService, agentScope)
        // Using a placeholder in case NUID.id is unexpectedly null during init, though createNUIDFromPublicKey should assign it.
        println("WorldAgent ${NUID.id ?: "ID_INIT_ERROR"} initialized with PublicKey: ${keyPair.publicKey.getEncoded().contentToString()}")
    }

    override fun start() {
        println("WorldAgent ${NUID.id ?: "ID_START_ERROR"} starting...")
        routingManager.start()
    }

    override fun stop() {
        println("WorldAgent ${NUID.id ?: "ID_STOP_ERROR"} stopping...")
        routingManager.stop()
        agentScope.cancel() // Cancel the scope when agent stops
    }
}

class WorldRouter(agentNUID: NUID<BigInteger>) : RoutingTable<BigInteger, WorldNetwork>(agentNUID)


object WorldNetwork : NetMask<BigInteger> {
    override val bits: Int
        get() = 128
}
