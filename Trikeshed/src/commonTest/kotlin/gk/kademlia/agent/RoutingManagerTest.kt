package gk.kademlia.agent

import gk.kademlia.KademliaConfig
import gk.kademlia.id.BigIntegerNUID
import gk.kademlia.id.NUID
import gk.kademlia.include.Address
import gk.kademlia.include.SubnetID
import gk.kademlia.include.SubnetRoute
import gk.kademlia.net.NetMask
import gk.kademlia.routing.RoutingTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import borg.trikeshed.num.BigInt as BigInteger // Assuming BigInt is accessible for tests


// --- Helper: WorldNetwork definition for testing ---
object TestWorldNetwork : NetMask<BigInteger> {
    override val bits: Int get() = 160 // Standard Kademlia bits
    override fun distance(one: BigInteger, two: BigInteger): Int {
        val xorResult = one.xor(two)
        return bits - xorResult.bitLength() +1 // Simplified, ensure it provides some ordering
    }
     override fun isInNetmask(id: BigInteger, netid: BigInteger): Boolean = true // Simplified
}


// --- Fake NetworkService ---
class FakeNetworkService<TNum : Comparable<TNum>, Sz : NetMask<TNum>>(
    private val agentNUID: NUID<TNum> // Added to help generate varied NUIDs if needed
) : NetworkService<TNum, Sz> {
    data class PingCall(val route: SubnetRoute<TNum>)
    data class FindNodeCall(val targetId: NUID<TNum>, val count: Int)

    val pingCalls = mutableListOf<PingCall>()
    val findNodeCalls = mutableListOf<FindNodeCall>()

    var pingHandler: ((SubnetRoute<TNum>) -> Boolean)? = null
    var findNodeHandler: ((NUID<TNum>, Int) -> List<SubnetRoute<TNum>>)? = null

    override suspend fun sendPing(route: SubnetRoute<TNum>): Boolean {
        pingCalls.add(PingCall(route))
        return pingHandler?.invoke(route) ?: true // Default to success
    }

    override suspend fun findNode(targetId: NUID<TNum>, count: Int): List<SubnetRoute<TNum>> {
        findNodeCalls.add(FindNodeCall(targetId, count))
        return findNodeHandler?.invoke(targetId, count) ?: emptyList() // Default to empty
    }

    fun clearHandlers() {
        pingHandler = null
        findNodeHandler = null
    }
     fun clearCalls() {
        pingCalls.clear()
        findNodeCalls.clear()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoutingManagerTest {

    private lateinit var agentNUID: NUID<BigInteger>
    private lateinit var routingTable: RoutingTable<BigInteger, TestWorldNetwork>
    private lateinit var fakeNetworkService: FakeNetworkService<BigInteger, TestWorldNetwork>
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testScope: CoroutineScope
    private lateinit var routingManager: RoutingManager<BigInteger, TestWorldNetwork>

    // Helper to create NUIDs for tests
    private fun createNUID(id: Int): NUID<BigInteger> {
        return BigIntegerNUID(BigInteger.valueOf(id.toLong()), TestWorldNetwork)
    }

    // Helper to create SubnetRoutes
    private fun createSubnetRoute(id: Int, subnet: SubnetID = "default", lastSeen: Long = 0L, failedPings: Int = 0): SubnetRoute<BigInteger> {
        val nuid = createNUID(id)
        return SubnetRoute(
            nuid = nuid,
            address = Address("address_for_$id"),
            subnetId = subnet,
            lastSeen = lastSeen,
            failedPings = failedPings
        )
    }


    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        testScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

        agentNUID = createNUID(0) // Agent NUID for tests
        routingTable = RoutingTable(agentNUID, optimal = false) // Use real RoutingTable
        fakeNetworkService = FakeNetworkService(agentNUID)

        routingManager = RoutingManager(
            agentNUID,
            routingTable,
            fakeNetworkService,
            testScope
        )
        // KademliaConfig constants will be used directly
    }

    @AfterTest
    fun tearDown() {
        routingManager.stop() // Ensure manager is stopped
        testScope.cancel() // Cancel the scope to clean up coroutines
    }

    // --- Test Cases ---

    @Test
    fun `refreshBuckets pings node that requires check and updates on success`() = runTest(testScheduler) {
        val oldNode = createSubnetRoute(1, lastSeen = Clock.System.now().toEpochMilliseconds() - KademliaConfig.BUCKET_REFRESH_INTERVAL_MS - 1000)
        routingTable.addRoute(oldNode)

        fakeNetworkService.pingHandler = { true }

        // Directly call refreshBuckets for testing its isolated behavior
        val refreshMethod = routingManager.javaClass.getDeclaredMethod("refreshBuckets")
        refreshMethod.isAccessible = true
        refreshMethod.invoke(routingManager)

        assertEquals(1, fakeNetworkService.pingCalls.size)
        assertEquals(oldNode.nuid, fakeNetworkService.pingCalls.first().route.nuid)

        val updatedNode = routingTable.buckets[routingTable.bucketFor(oldNode.nuid)].getValue(oldNode.nuid.id!!)
        assertTrue(updatedNode.lastSeen > oldNode.lastSeen, "LastSeen should be updated.")
        assertEquals(0, updatedNode.failedPings, "FailedPings should be reset.")
    }

    @Test
    fun `refreshBuckets increments failedPings on ping fail and evicts after max failures`() = runTest(testScheduler) {
        val nodeToEvict = createSubnetRoute(
            id = 2,
            lastSeen = Clock.System.now().toEpochMilliseconds() - KademliaConfig.BUCKET_REFRESH_INTERVAL_MS - 1000,
            failedPings = KademliaConfig.MAX_FAILED_PINGS - 1
        )
        routingTable.addRoute(nodeToEvict)

        fakeNetworkService.pingHandler = { false } // Ping will fail

        val refreshMethod = routingManager.javaClass.getDeclaredMethod("refreshBuckets")
        refreshMethod.isAccessible = true

        // First ping failure
        refreshMethod.invoke(routingManager)

        assertEquals(1, fakeNetworkService.pingCalls.size)
        assertEquals(nodeToEvict.nuid, fakeNetworkService.pingCalls.first().route.nuid)

        var updatedNode = routingTable.buckets[routingTable.bucketFor(nodeToEvict.nuid)].getValue(nodeToEvict.nuid.id!!)
        assertEquals(KademliaConfig.MAX_FAILED_PINGS, updatedNode.failedPings)
        assertNotNull(routingTable.rmRoute(nodeToEvict.nuid), "Node should still be in table before eviction by refresh")
        routingTable.addRoute(updatedNode) // add it back for next check

        // Trigger refresh again, this time it should be evicted
        fakeNetworkService.clearCalls()
         updatedNode.lastSeen = Clock.System.now().toEpochMilliseconds() - KademliaConfig.BUCKET_REFRESH_INTERVAL_MS - 1000 // make it require ping again
         routingTable.addRoute(updatedNode)


        refreshMethod.invoke(routingManager)

        assertEquals(1, fakeNetworkService.pingCalls.size, "Ping should be attempted again")
        assertNull(routingTable.buckets[routingTable.bucketFor(nodeToEvict.nuid)][nodeToEvict.nuid.id!!], "Node should be evicted.")
    }

    @Test
    fun `refreshBuckets replenishes bucket if below size`() = runTest(testScheduler) {
        // Ensure a bucket is under capacity. Agent NUID is 0.
        // Find a bucket that would be empty.
        // For simplicity, we'll assume the first few buckets might be empty or can be cleared.
        // This test part depends heavily on NUID generation and distance logic.
        // Let's target a specific bucket by finding an NUID that falls into it.
        // The agentNUID is 0. A node with ID 1 would typically be in a close bucket.

        val discoveredNode1 = createSubnetRoute(10)
        val discoveredNode2 = createSubnetRoute(11)
        fakeNetworkService.findNodeHandler = { _, _ -> listOf(discoveredNode1, discoveredNode2) }

        val refreshMethod = routingManager.javaClass.getDeclaredMethod("refreshBuckets")
        refreshMethod.isAccessible = true
        refreshMethod.invoke(routingManager)

        assertTrue(fakeNetworkService.findNodeCalls.isNotEmpty(), "findNode should be called.")
        // Verify nodes are added (assuming their target buckets are not full)
        assertNotNull(routingTable.buckets[routingTable.bucketFor(discoveredNode1.nuid)][discoveredNode1.nuid.id!!])
        assertNotNull(routingTable.buckets[routingTable.bucketFor(discoveredNode2.nuid)][discoveredNode2.nuid.id!!])
    }

    @Test
    fun `handleFullBucket adds node if bucket not full`() = runTest(testScheduler) {
        val bucketIndex = 0 // Assume this bucket is not full
        routingTable.buckets[bucketIndex].clear() // Ensure it's empty

        val newNode = createSubnetRoute(30)
        routingManager.handleFullBucket(bucketIndex, newNode)

        assertNotNull(routingTable.buckets[bucketIndex][newNode.nuid.id!!])
    }

    @Test
    fun `handleFullBucket pings LRU and keeps LRU if it responds`() = runTest(testScheduler) {
        val bucketIndex = routingTable.bucketFor(createNUID(routingTable.bucketSize + 10))

        // Fill the bucket
        for (i in 1..routingTable.bucketSize) {
            // Create NUIDs that would fall into this bucket index or nearby.
            // This is tricky without precise NUID to bucket mapping for test.
            // For now, just add to a specific bucket, assuming it's the one.
            val node = createSubnetRoute(id = 100 + i, lastSeen = Clock.System.now().toEpochMilliseconds() - (i * 1000)) // Varied lastSeen
            routingTable.buckets[bucketIndex].put(node.nuid.id!!, node)
        }
        assertTrue(routingTable.buckets[bucketIndex].size == routingTable.bucketSize, "Bucket should be full")

        val lruNode = routingTable.buckets[bucketIndex].values.minByOrNull { it.lastSeen }!!
        val newNode = createSubnetRoute(200) // New node trying to get in

        fakeNetworkService.pingHandler = { route -> route.nuid.id == lruNode.nuid.id } // LRU ping success

        routingManager.handleFullBucket(bucketIndex, newNode)

        assertEquals(1, fakeNetworkService.pingCalls.size)
        assertEquals(lruNode.nuid, fakeNetworkService.pingCalls.first().route.nuid)
        assertTrue(routingTable.buckets[bucketIndex].containsKey(lruNode.nuid.id!!), "LRU node should still be in bucket.")
        assertFalse(routingTable.buckets[bucketIndex].containsKey(newNode.nuid.id!!), "New node should not be added.")
        assertTrue(lruNode.lastSeen > Clock.System.now().toEpochMilliseconds() - 500, "LRU lastSeen should be updated.")
    }

    @Test
    fun `handleFullBucket pings LRU and replaces LRU if it fails ping`() = runTest(testScheduler) {
        val bucketIndex = routingTable.bucketFor(createNUID(routingTable.bucketSize + 20))
         for (i in 1..routingTable.bucketSize) {
            val node = createSubnetRoute(id = 200 + i, lastSeen = Clock.System.now().toEpochMilliseconds() - (i * 1000))
            routingTable.buckets[bucketIndex].put(node.nuid.id!!, node)
        }
        assertTrue(routingTable.buckets[bucketIndex].size == routingTable.bucketSize, "Bucket should be full")

        val lruNode = routingTable.buckets[bucketIndex].values.minByOrNull { it.lastSeen }!!
        val newNode = createSubnetRoute(300)

        fakeNetworkService.pingHandler = { route -> route.nuid.id != lruNode.nuid.id } // LRU ping fails

        routingManager.handleFullBucket(bucketIndex, newNode)

        assertEquals(1, fakeNetworkService.pingCalls.size)
        assertEquals(lruNode.nuid, fakeNetworkService.pingCalls.first().route.nuid)
        assertFalse(routingTable.buckets[bucketIndex].containsKey(lruNode.nuid.id!!), "LRU node should be removed.")
        assertTrue(routingTable.buckets[bucketIndex].containsKey(newNode.nuid.id!!), "New node should be added.")
    }

    @Test
    fun `start launches refresh job and stop cancels it`() = runTest(testScheduler) {
        assertNull(routingManager.javaClass.getDeclaredField("refreshJob").apply { isAccessible = true }.get(routingManager) as? Job, "Refresh job should be null initially.")

        routingManager.start()
        val job = routingManager.javaClass.getDeclaredField("refreshJob").apply { isAccessible = true }.get(routingManager) as? Job
        assertNotNull(job, "Refresh job should be active after start.")
        assertTrue(job.isActive, "Refresh job should be active.")

        // Allow some time for the refreshBuckets to be called once if not for KademliaConfig.BUCKET_REFRESH_INTERVAL_MS
        // The test for refreshBuckets itself is separate. Here we mainly check job lifecycle.
        // advanceTimeBy(KademliaConfig.BUCKET_REFRESH_INTERVAL_MS + 100) // This would be too long for unit test.
        // We can check if refreshBuckets was called by checking network service calls if start() invoked it immediately.
        // Current RoutingManager start() has a delay *then* refresh.

        routingManager.stop()
        assertTrue(job.isCancelled, "Refresh job should be cancelled after stop.")
    }
}

// Note: Accessing private refreshBuckets and refreshJob via reflection is for focused unit testing.
// In integration tests, you'd observe behavior through public APIs or side effects over time.
// The TestWorldNetwork distance function is simplified and might not perfectly represent Kademlia XOR distance nuances
// for all bucket distribution scenarios but should be okay for these tests.
// The NUID creation for specific buckets in handleFullBucket tests is approximated.
// A more robust NUID generation for tests might involve knowing the agentNUID and deriving an ID
// that is guaranteed to fall into a certain bucket index based on bit-length and XOR distance.
// The test for `refreshBuckets replenishes bucket if below size` also makes assumptions about bucket emptiness.
// A more precise setup would involve clearing specific buckets or ensuring they are below threshold.
// The test `refreshBuckets increments failedPings on ping fail and evicts after max failures` was also adjusted to re-add the node
// to ensure the second call to refreshBuckets would then evict it.
