package gk.kademlia.bitswap

import gk.kademlia.KademliaConfig // For NODE_DISCOVERY_COUNT, if needed, or use a const
import gk.kademlia.agent.WorldNetwork // Assuming a concrete NetMask for tests
import gk.kademlia.id.BigIntegerNUID
import gk.kademlia.id.NUID
import gk.kademlia.include.Address
import gk.kademlia.include.SubnetRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull
import borg.trikeshed.num.BigInt as BigInteger


// --- Fake Implementations ---

class FakeBlockStore : BlockStore {
    private val store = mutableMapOf<CID, ByteArray>()
    val putCalls = mutableListOf<Pair<CID, ByteArray>>()
    val getCalls = mutableListOf<CID>()
    val hasCalls = mutableListOf<CID>()

    override suspend fun get(cid: CID): ByteArray? {
        getCalls.add(cid)
        return store[cid]
    }

    override suspend fun put(cid: CID, data: ByteArray): Boolean {
        putCalls.add(Pair(cid, data))
        store[cid] = data
        return true
    }

    override suspend fun has(cid: CID): Boolean {
        hasCalls.add(cid)
        return store.containsKey(cid)
    }

    fun prePopulate(cid: CID, data: ByteArray) {
        store[cid] = data
    }
    fun clear() {
        store.clear()
        putCalls.clear()
        getCalls.clear()
        hasCalls.clear()
    }
}

class FakeWantManager : WantManager() { // Inherit to use actual WantManager's flows if simple
    val wantBlockCalls = mutableListOf<Pair<CID, Int>>()
    val blockReceivedCalls = mutableListOf<CID>()
    val cancelWantCalls = mutableListOf<CID>()

    private var activeWantsList: List<Wantlist.Entry> = emptyList()
    private var wantEntriesMap = mutableMapOf<CID, WantEntry>()

    // Expose flows for test control and verification if needed
    val testWantlistChangesFlow = MutableSharedFlow<Wantlist.Entry>()
    override val wantlistChangesFlow = testWantlistChangesFlow.asSharedFlow() // Allow emitting

    val testBlockReceivedFlow = MutableSharedFlow<CID>()
    override val blockReceivedFlow = testBlockReceivedFlow.asSharedFlow()


    override suspend fun wantBlock(cid: CID, priority: Int) {
        wantBlockCalls.add(Pair(cid, priority))
        // Simulate actual WantManager behavior for getActiveWants if needed for some tests
        val existing = wantEntriesMap[cid]
        if (existing == null || existing.priority != priority) {
            wantEntriesMap[cid] = WantEntry(cid, priority)
            testWantlistChangesFlow.tryEmit(Wantlist.Entry(cid, priority, cancel = false, wantType = Wantlist.WantType.Block, sendDontHave = true))
        }
        activeWantsList = wantEntriesMap.values.map { Wantlist.Entry(it.cid, it.priority) }
    }

    override suspend fun blockReceived(cid: CID) {
        blockReceivedCalls.add(cid)
        wantEntriesMap.remove(cid)
        activeWantsList = wantEntriesMap.values.map { Wantlist.Entry(it.cid, it.priority) }
        testBlockReceivedFlow.tryEmit(cid)
    }

    override suspend fun cancelWant(cid: CID) {
        cancelWantCalls.add(cid)
        if (wantEntriesMap.containsKey(cid)) {
            wantEntriesMap.remove(cid)
            testWantlistChangesFlow.tryEmit(Wantlist.Entry(cid, 0, cancel = true))
        }
        activeWantsList = wantEntriesMap.values.map { Wantlist.Entry(it.cid, it.priority) }
    }

    override suspend fun getActiveWants(): List<Wantlist.Entry> = activeWantsList

    fun setActiveWants(wants: List<Wantlist.Entry>) {
        activeWantsList = wants
        wantEntriesMap.clear()
        wants.forEach { wantEntriesMap[it.block] = WantEntry(it.block, it.priority) }
    }

    override suspend fun getWantEntry(cid: CID): WantEntry? = wantEntriesMap[cid]

    fun clear() {
        wantBlockCalls.clear()
        blockReceivedCalls.clear()
        cancelWantCalls.clear()
        activeWantsList = emptyList()
        wantEntriesMap.clear()
    }
}


class FakeBitswapNetworkSender<TNum : Comparable<TNum>, Sz : NetMask<TNum>> : BitswapNetworkSender<TNum, Sz> {
    val sentMessages = mutableListOf<Pair<SubnetRoute<TNum>, BitswapMessage>>()

    override suspend fun sendMessage(targetRoute: SubnetRoute<TNum>, message: BitswapMessage) {
        sentMessages.add(Pair(targetRoute, message))
    }
    fun clear() {
        sentMessages.clear()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BitswapEngineTest {

    private lateinit var testScope: CoroutineScope // Managed by runTest
    private lateinit var blockStore: FakeBlockStore
    private lateinit var wantManager: FakeWantManager
    private lateinit var networkSender: FakeBitswapNetworkSender<BigInteger, WorldNetwork>
    private lateinit var engine: BitswapEngine<BigInteger, WorldNetwork>

    private val localPeerIdString = "localTestPeer"
    private lateinit var localNUID: NUID<BigInteger>


    // Helper to create NUIDs for tests
    private fun createNUID(id: Long): NUID<BigInteger> {
        return BigIntegerNUID(BigInteger.valueOf(id), WorldNetwork)
    }

    private fun createSubnetRoute(id: Long, addressSfx: String = id.toString()): SubnetRoute<BigInteger> {
        val nuid = createNUID(id)
        return SubnetRoute(
            nuid = nuid,
            address = Address("address_for_$addressSfx"),
            subnetId = "default_subnet",
            lastSeen = Clock.System.now().toEpochMilliseconds(),
            failedPings = 0
        )
    }
    private fun createCID(id: String): CID = CID(id.encodeToByteArray())

    @BeforeTest
    fun setup() {
        // testDispatcher is not directly used by runTest's scope, but can be for launching other dispatchers
        // testScope = TestScope(StandardTestDispatcher() + SupervisorJob()) // runTest provides its own scope
        blockStore = FakeBlockStore()
        wantManager = FakeWantManager() // Uses real flows but methods are overridden for tracking/control
        networkSender = FakeBitswapNetworkSender()
        localNUID = createNUID(0L) // Assuming local peer ID 0 for tests

        engine = BitswapEngine(
            scope = TestScope(StandardTestDispatcher() + SupervisorJob()), // Engine gets its own scope for internal jobs
            blockStore = blockStore,
            wantManager = wantManager,
            networkSender = networkSender,
            localPeerId = localNUID.id.toString() // Use NUID's string ID
        )
    }

    @AfterTest
    fun tearDown() {
        engine.stop() // Stop the engine's internal jobs
        (engine.scope as CoroutineScope).cancel() // Cancel the engine's scope
        // testScope.cancel() // runTest scope is cancelled automatically
    }

    @Test
    fun `start subscribes to wantManager wantlistChangesFlow and broadcasts`() = runTest {
        val peerRoute1 = createSubnetRoute(1)
        // Manually add a peer to the engine's ledger so it has someone to broadcast to
        engine.handleIncomingMessage(peerRoute1, BitswapMessage()) // Minimal message to register peer

        engine.start() // Subscribes to wantlistChangesFlow

        val cid1 = createCID("cid1_broadcast")
        val wantEntry = Wantlist.Entry(cid1, 10, cancel = false)

        // Emit from the FakeWantManager's flow to simulate a change
        wantManager.testWantlistChangesFlow.emit(wantEntry)
        advanceUntilIdle() // Allow the flow collector in engine to process

        assertTrue(networkSender.sentMessages.isNotEmpty(), "Network sender should have been called.")
        val (sentRoute, sentMessage) = networkSender.sentMessages.first()
        assertEquals(peerRoute1.nuid, sentRoute.nuid)
        assertNotNull(sentMessage.wantlist)
        assertEquals(1, sentMessage.wantlist!!.entries?.size)
        assertEquals(wantEntry, sentMessage.wantlist!!.entries?.first())
    }

    @Test
    fun `handleIncomingMessage with wantlist sends available blocks and DONT_HAVE presences`() = runTest {
        val sourceRoute = createSubnetRoute(100, "source")
        val cidHave = createCID("have_this")
        val cidHaveWantTypeHave = createCID("have_this_want_have")
        val cidDontHaveSend = createCID("dont_have_send_dont")
        val cidDontHaveNoSend = createCID("dont_have_no_send")

        blockStore.prePopulate(cidHave, "data_for_have_this".encodeToByteArray())
        blockStore.prePopulate(cidHaveWantTypeHave, "data_for_have_want_type_have".encodeToByteArray())

        val incomingWantlist = Wantlist(entries = listOf(
            Wantlist.Entry(cidHave, wantType = Wantlist.WantType.Block),
            Wantlist.Entry(cidHaveWantTypeHave, wantType = Wantlist.WantType.Have),
            Wantlist.Entry(cidDontHaveSend, wantType = Wantlist.WantType.Block, sendDontHave = true),
            Wantlist.Entry(cidDontHaveNoSend, wantType = Wantlist.WantType.Block, sendDontHave = false)
        ))
        val incomingMessage = BitswapMessage(wantlist = incomingWantlist)

        engine.handleIncomingMessage(sourceRoute, incomingMessage)
        advanceUntilIdle()

        assertEquals(1, networkSender.sentMessages.size)
        val (sentRoute, responseMsg) = networkSender.sentMessages.first()
        assertEquals(sourceRoute.nuid, sentRoute.nuid)

        // Check payload (blocks sent)
        assertNotNull(responseMsg.payload)
        assertEquals(1, responseMsg.payload!!.size)
        // CID derivation in engine is internalCryptoService.hash(data). We need to match that.
        val expectedSentBlockData = "data_for_have_this".encodeToByteArray()
        // val expectedSentCID = CID(engine.internalCryptoService.hash(expectedSentBlockData)) - internalCryptoService is private
        // For testing, we'd need to know how the engine derives CID or make it part of BlockContainer for verification
        // Current FakeBlockStore doesn't use prefix, engine sends empty prefix.
        assertEquals(expectedSentBlockData.toList(), responseMsg.payload!!.first().data.toList())


        // Check blockPresences
        assertNotNull(responseMsg.blockPresences)
        assertEquals(2, responseMsg.blockPresences!!.size)
        assertTrue(responseMsg.blockPresences!!.any { it.cid == cidHaveWantTypeHave && it.type == BlockPresenceType.Have })
        assertTrue(responseMsg.blockPresences!!.any { it.cid == cidDontHaveSend && it.type == BlockPresenceType.DontHave })

        // Check ledger update for sourcePeer
        val ledger = engine.peerLedgers[sourceRoute.nuid.id.toString()]?.first
        assertNotNull(ledger)
        val peerWants = ledger.getPeerWants()
        assertTrue(peerWants.contains(cidHave))
        assertTrue(peerWants.contains(cidHaveWantTypeHave))
        assertTrue(peerWants.contains(cidDontHaveSend))
        assertTrue(peerWants.contains(cidDontHaveNoSend))
    }


    @Test
    fun `handleIncomingMessage with payload stores block and notifies wantManager`() = runTest {
        val sourceRoute = createSubnetRoute(200, "payload_source")
        val cidWanted = createCID("wanted_block")
        val blockData = "this_is_the_wanted_data".encodeToByteArray()
        // val blockCID = CID(engine.internalCryptoService.hash(blockData)) // Engine will derive this
        // For this test, we need to ensure wantManager wants the CID that engine will derive.
        // So, let's pre-calculate it or use a known hash if DummyCryptoService is predictable.
        // DummyCryptoService hash is: ByteArray(32) { i -> data.getOrNull(i) ?: i.toByte() }
        val derivedCID = CID(DummyCryptoService().hash(blockData))


        wantManager.setActiveWants(listOf(Wantlist.Entry(derivedCID, priority = 1))) // Simulate wanting this block

        val blockContainer = BlockContainer(prefix = byteArrayOf(), data = blockData)
        val incomingMessage = BitswapMessage(payload = listOf(blockContainer))

        engine.handleIncomingMessage(sourceRoute, incomingMessage)
        advanceUntilIdle()

        assertEquals(1, blockStore.putCalls.size)
        assertEquals(derivedCID, blockStore.putCalls.first().first)
        assertTrue(blockData.contentEquals(blockStore.putCalls.first().second))

        assertEquals(1, wantManager.blockReceivedCalls.size)
        assertEquals(derivedCID, wantManager.blockReceivedCalls.first())

        val ledger = engine.peerLedgers[sourceRoute.nuid.id.toString()]?.first
        assertNotNull(ledger)
        assertEquals(-blockData.size.toLong(), ledger.getBytesExchangedBalance())
    }

    @Test
    fun `requestBlock calls wantManager`() = runTest {
        val cidToRequest = createCID("request_this")
        engine.requestBlock(cidToRequest, 5)
        advanceUntilIdle()

        assertEquals(1, wantManager.wantBlockCalls.size)
        assertEquals(Pair(cidToRequest, 5), wantManager.wantBlockCalls.first())
    }

    @Test
    fun `notifyLocalBlockAvailable sends to wanting peers`() = runTest {
        val peerRoute = createSubnetRoute(300, "wanting_peer")
        val peerIdStr = peerRoute.nuid.id.toString()
        val cidAvailable = createCID("now_available")
        val blockData = "available_data".encodeToByteArray()

        // Setup: peer wants cidAvailable
        engine.getOrCreateLedger(peerIdStr, peerRoute) // Ensure ledger exists
        engine.peerLedgers[peerIdStr]?.first?.addWants(listOf(cidAvailable))

        blockStore.prePopulate(cidAvailable, blockData) // Make block available in store

        engine.notifyLocalBlockAvailable(cidAvailable)
        advanceUntilIdle()

        assertEquals(1, networkSender.sentMessages.size)
        val (sentRoute, sentMsg) = networkSender.sentMessages.first()
        assertEquals(peerRoute.nuid, sentRoute.nuid)
        assertNotNull(sentMsg.payload)
        assertEquals(1, sentMsg.payload!!.size)
        // val sentBlockCID = CID(engine.internalCryptoService.hash(sentMsg.payload!!.first().data))
        // assertEquals(cidAvailable, sentBlockCID) // This check is tricky due to hashing in engine
        assertTrue(blockData.contentEquals(sentMsg.payload!!.first().data))

        val ledger = engine.peerLedgers[peerIdStr]?.first
        assertNotNull(ledger)
        assertEquals(blockData.size.toLong(), ledger.getBytesExchangedBalance())
    }

    @Test
    fun `addPotentialPeers sends full wantlist to new peers`() = runTest {
        val newPeer1Route = createSubnetRoute(401, "newPeer1")
        val newPeer2Route = createSubnetRoute(402, "newPeer2")

        val cidWanted1 = createCID("active_want_1")
        val cidWanted2 = createCID("active_want_2")
        wantManager.setActiveWants(listOf(
            Wantlist.Entry(cidWanted1, 1),
            Wantlist.Entry(cidWanted2, 2)
        ))
        engine.start() // Engine needs to be started to process wantlist changes if that's how it gets initial wants for broadcast.
                       // Or addPotentialPeers should fetch current wants directly. (It does)

        engine.addPotentialPeers(listOf(newPeer1Route, newPeer2Route))
        advanceUntilIdle()

        // Should send to 2 peers
        assertEquals(2, networkSender.sentMessages.size)

        val firstMessage = networkSender.sentMessages.find { it.first.nuid == newPeer1Route.nuid }?.second
        val secondMessage = networkSender.sentMessages.find { it.first.nuid == newPeer2Route.nuid }?.second

        assertNotNull(firstMessage)
        assertNotNull(secondMessage)

        listOf(firstMessage, secondMessage).forEach { msg ->
            assertNotNull(msg.wantlist)
            assertTrue(msg.wantlist!!.full) // Should be a full wantlist
            assertEquals(2, msg.wantlist!!.entries?.size)
            assertTrue(msg.wantlist!!.entries!!.any { it.block == cidWanted1 && it.priority == 1 })
            assertTrue(msg.wantlist!!.entries!!.any { it.block == cidWanted2 && it.priority == 2 })
        }

        assertNotNull(engine.peerLedgers[newPeer1Route.nuid.id.toString()])
        assertNotNull(engine.peerLedgers[newPeer2Route.nuid.id.toString()])
    }
}
