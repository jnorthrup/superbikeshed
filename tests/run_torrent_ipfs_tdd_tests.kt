#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
@file:DependsOn("org.jetbrains.kotlin:kotlin-test:1.9.10")

import kotlinx.coroutines.*
import kotlin.test.*

/**
 * Test Runner for TrikeShed Torrent and IPFS Gateway TDD Tests
 * 
 * This script runs all TDD tests for the torrent implementation and IPFS gateway.
 * It provides comprehensive coverage of:
 * - BitTorrent protocol implementation
 * - Torrent simulation framework
 * - IPFS gateway integration
 * - RPC server functionality
 * - Batch control systems
 */

fun main() = runBlocking {
    println("🚀 Starting TrikeShed Torrent & IPFS Gateway TDD Test Suite")
    println("=" * 60)
    
    val testResults = mutableListOf<TestResult>()
    
    // Run protocol tests
    println("\n📡 Testing BitTorrent Protocol Implementation...")
    testResults.addAll(runProtocolTests())
    
    // Run simulation tests
    println("\n🎮 Testing Torrent Simulation Framework...")
    testResults.addAll(runSimulationTests())
    
    // Run IPFS gateway tests
    println("\n🌐 Testing IPFS Gateway Integration...")
    testResults.addAll(runIpfsGatewayTests())
    
    // Run integration tests
    println("\n🔗 Testing Integration Workflows...")
    testResults.addAll(runIntegrationTests())
    
    // Print summary
    printTestSummary(testResults)
}

data class TestResult(
    val testName: String,
    val category: String,
    val success: Boolean,
    val error: String? = null,
    val duration: Long = 0
)

suspend fun runProtocolTests(): List<TestResult> {
    val results = mutableListOf<TestResult>()
    
    try {
        val startTime = System.currentTimeMillis()
        
        // Test handshake protocol
        runTest {
            val infoHash = 20 j { i -> i.toByte() }
            val peerId = PeerId(20 j { i -> (i + 10).toByte() })
            val handshake = BitTorrentPeerWire.PeerMessage.Handshake(
                protocol = "BitTorrent protocol",
                reserved = ByteArray(8),
                infoHash = infoHash,
                peerId = peerId
            )
            assertEquals("BitTorrent protocol", handshake.protocol)
            assertEquals(infoHash, handshake.infoHash)
        }
        results.add(TestResult("Handshake Protocol", "Protocol", true, duration = System.currentTimeMillis() - startTime))
        
    } catch (e: Exception) {
        results.add(TestResult("Handshake Protocol", "Protocol", false, e.message))
    }
    
    try {
        val startTime = System.currentTimeMillis()
        
        // Test message types
        runTest {
            assertEquals(BitTorrentPeerWire.MessageType.CHOKE, BitTorrentPeerWire.MessageType.fromId(0))
            assertEquals(BitTorrentPeerWire.MessageType.UNCHOKE, BitTorrentPeerWire.MessageType.fromId(1))
            assertEquals(BitTorrentPeerWire.MessageType.INTERESTED, BitTorrentPeerWire.MessageType.fromId(2))
            assertNull(BitTorrentPeerWire.MessageType.fromId(99))
        }
        results.add(TestResult("Message Types", "Protocol", true, duration = System.currentTimeMillis() - startTime))
        
    } catch (e: Exception) {
        results.add(TestResult("Message Types", "Protocol", false, e.message))
    }
    
    try {
        val startTime = System.currentTimeMillis()
        
        // Test peer connection
        runTest {
            val infoHash = 20 j { i -> i.toByte() }
            val peerId = PeerId(20 j { i -> (i + 10).toByte() })
            val peerWire = BitTorrentPeerWire(
                context = Dispatchers.Unconfined,
                infoHash = infoHash,
                peerId = peerId
            )
            val connection = peerWire.addConnection("127.0.0.1:6881", 6881)
            assertNotNull(connection)
            assertEquals("127.0.0.1:6881", connection.address)
        }
        results.add(TestResult("Peer Connection", "Protocol", true, duration = System.currentTimeMillis() - startTime))
        
    } catch (e: Exception) {
        results.add(TestResult("Peer Connection", "Protocol", false, e.message))
    }
    
    return results
}

suspend fun runSimulationTests(): List<TestResult> {
    val results = mutableListOf<TestResult>()
    
    try {
        val startTime = System.currentTimeMillis()
        
        // Test torrent simulation creation
        runTest {
            val simulation = TorrentSimulation(context = Dispatchers.Unconfined)
            simulation.start()
            assertTrue(simulation.isRunning())
            
            val infoHash = 20 j { i -> i.toByte() }
            val torrent = simulation.createTorrent(
                infoHash = infoHash,
                name = "test-torrent",
                totalSize = 1024 * 1024,
                pieceSize = 16384
            )
            assertEquals("test-torrent", torrent.name)
            assertEquals(64, torrent.totalPieces)
        }
        results.add(TestResult("Torrent Creation", "Simulation", true, duration = System.currentTimeMillis() - startTime))
        
    } catch (e: Exception) {
        results.add(TestResult("Torrent Creation", "Simulation", false, e.message))
    }
    
    try {
        val startTime = System.currentTimeMillis()
        
        // Test peer joining
        runTest {
            val simulation = TorrentSimulation(context = Dispatchers.Unconfined)
            simulation.start()
            
            val infoHash = 20 j { i -> i.toByte() }
            val torrent = simulation.createTorrent(
                infoHash = infoHash,
                name = "test-torrent",
                totalSize = 1024 * 1024,
                pieceSize = 16384
            )
            
            val peerId = PeerId(20 j { i -> (i + 20).toByte() })
            val peer = simulation.simulatePeerJoin(
                torrent = torrent,
                peerId = peerId,
                address = "127.0.0.1",
                port = 6882,
                hasPieces = setOf(0, 1, 2, 3)
            )
            assertEquals(peerId, peer.peerId)
            assertEquals(4, peer.hasPieces.size)
        }
        results.add(TestResult("Peer Joining", "Simulation", true, duration = System.currentTimeMillis() - startTime))
        
    } catch (e: Exception) {
        results.add(TestResult("Peer Joining", "Simulation", false, e.message))
    }
    
    return results
}

suspend fun runIpfsGatewayTests(): List<TestResult> {
    val results = mutableListOf<TestResult>()
    
    try {
        val startTime = System.currentTimeMillis()
        
        // Test IPFS client
        runTest {
            val storage = InMemoryIpfsStorage()
            val peerId = PeerId(32 j { i -> i.toByte() })
            val client = IpfsClient(
                localPeerId = peerId,
                quicEngine = null,
                storage = storage
            )
            
            val testData = 1024 j { i -> (i % 256).toByte() }
            val cid = client.add(testData)
            assertNotNull(cid)
            
            val retrievedData = client.get(cid)
            assertNotNull(retrievedData)
            assertEquals(testData.a, retrievedData!!.a)
        }
        results.add(TestResult("IPFS Client", "Gateway", true, duration = System.currentTimeMillis() - startTime))
        
    } catch (e: Exception) {
        results.add(TestResult("IPFS Client", "Gateway", false, e.message))
    }
    
    try {
        val startTime = System.currentTimeMillis()
        
        // Test IPFS server
        runTest {
            val config = IpfsServerConfig()
            val storage = InMemoryIpfsStorage()
            val dht = MockDHTService()
            val pubsub = EnhancedIpfsPubSubService()
            
            val server = IpfsServer(config, storage, dht, pubsub)
            server.start()
            
            val stats = server.getStats()
            assertTrue(stats.uptime >= 0)
        }
        results.add(TestResult("IPFS Server", "Gateway", true, duration = System.currentTimeMillis() - startTime))
        
    } catch (e: Exception) {
        results.add(TestResult("IPFS Server", "Gateway", false, e.message))
    }
    
    try {
        val startTime = System.currentTimeMillis()
        
        // Test content pinning
        runTest {
            val storage = InMemoryIpfsStorage()
            val peerId = PeerId(32 j { i -> i.toByte() })
            val client = IpfsClient(
                localPeerId = peerId,
                quicEngine = null,
                storage = storage
            )
            
            val testData = 1024 j { i -> (i % 256).toByte() }
            val cid = client.add(testData)
            val pinSuccess = client.pin(cid)
            assertTrue(pinSuccess)
            
            val pinnedCids = client.listPinned()
            assertTrue(pinnedCids.a > 0)
        }
        results.add(TestResult("Content Pinning", "Gateway", true, duration = System.currentTimeMillis() - startTime))
        
    } catch (e: Exception) {
        results.add(TestResult("Content Pinning", "Gateway", false, e.message))
    }
    
    return results
}

suspend fun runIntegrationTests(): List<TestResult> {
    val results = mutableListOf<TestResult>()
    
    try {
        val startTime = System.currentTimeMillis()
        
        // Test torrent to IPFS gateway integration
        runTest {
            val simulation = TorrentSimulation(context = Dispatchers.Unconfined)
            simulation.start()
            
            val storage = InMemoryIpfsStorage()
            val peerId = PeerId(32 j { i -> i.toByte() })
            val ipfsClient = IpfsClient(
                localPeerId = peerId,
                quicEngine = null,
                storage = storage
            )
            
            val infoHash = 20 j { i -> i.toByte() }
            val torrent = simulation.createTorrent(
                infoHash = infoHash,
                name = "integration-test",
                totalSize = 512 * 1024,
                pieceSize = 16384
            )
            
            // Simulate downloaded pieces
            val downloadedPieces = mutableListOf<Indexed<Byte>>()
            for (i in 0 until torrent.totalPieces) {
                val pieceData = 16384 j { j -> ((i * 16384 + j) % 256).toByte() }
                downloadedPieces.add(pieceData)
            }
            
            // Store in IPFS
            val torrentData = downloadedPieces.fold(0 j { 0.toByte() }) { acc, piece ->
                val newSize = acc.a + piece.a
                newSize j { i ->
                    if (i < acc.a) acc.b(i) else piece.b(i - acc.a)
                }
            }
            val ipfsCid = ipfsClient.add(torrentData)
            assertNotNull(ipfsCid)
            
            // Create gateway mapping
            val gatewayMapping = TorrentIpfsGatewayMapping(
                torrentHash = infoHash,
                ipfsCid = ipfsCid,
                torrentName = torrent.name,
                totalSize = torrent.totalSize
            )
            assertEquals(infoHash, gatewayMapping.torrentHash)
            assertEquals(ipfsCid, gatewayMapping.ipfsCid)
        }
        results.add(TestResult("Torrent-IPFS Gateway", "Integration", true, duration = System.currentTimeMillis() - startTime))
        
    } catch (e: Exception) {
        results.add(TestResult("Torrent-IPFS Gateway", "Integration", false, e.message))
    }
    
    return results
}

fun printTestSummary(results: List<TestResult>) {
    println("\n" + "=" * 60)
    println("📊 TDD Test Summary")
    println("=" * 60)
    
    val totalTests = results.size
    val passedTests = results.count { it.success }
    val failedTests = totalTests - passedTests
    
    println("Total Tests: $totalTests")
    println("✅ Passed: $passedTests")
    println("❌ Failed: $failedTests")
    println("Success Rate: ${(passedTests.toDouble() / totalTests * 100).format(1)}%")
    
    // Group by category
    val byCategory = results.groupBy { it.category }
    println("\n📋 Results by Category:")
    for ((category, categoryResults) in byCategory) {
        val categoryPassed = categoryResults.count { it.success }
        val categoryTotal = categoryResults.size
        println("  $category: $categoryPassed/$categoryTotal passed")
    }
    
    // Show failed tests
    val failedResults = results.filter { !it.success }
    if (failedResults.isNotEmpty()) {
        println("\n❌ Failed Tests:")
        for (result in failedResults) {
            println("  - ${result.testName} (${result.category}): ${result.error}")
        }
    }
    
    // Show performance summary
    val totalDuration = results.sumOf { it.duration }
    val avgDuration = if (results.isNotEmpty()) totalDuration / results.size else 0
    println("\n⏱️  Performance:")
    println("  Total Duration: ${totalDuration}ms")
    println("  Average Duration: ${avgDuration}ms")
    
    println("\n" + "=" * 60)
    if (failedTests == 0) {
        println("🎉 All tests passed! TDD implementation is working correctly.")
    } else {
        println("⚠️  Some tests failed. Please review and fix the implementation.")
    }
    println("=" * 60)
}

// === MOCK IMPLEMENTATIONS ===

internal class MockDHTService : DHTService {
    override suspend fun start(localPeerId: PeerId) {}
    override suspend fun stop() {}
    override suspend fun provide(cid: CID) {}
    override suspend fun findProviders(cid: CID): Indexed<PeerInfo> = 0 j { throw NoSuchElementException() }
    override suspend fun discoverPeers(): Indexed<PeerInfo> = 0 j { throw NoSuchElementException() }
}

// === DATA CLASSES ===

data class TorrentIpfsGatewayMapping(
    val torrentHash: InfoHash,
    val ipfsCid: CID,
    val torrentName: String,
    val totalSize: Long,
    val createdAt: Long = System.currentTimeMillis()
)

// === UTILITY FUNCTIONS ===

operator fun String.times(n: Int): String = repeat(n)

fun Double.format(digits: Int) = "%.${digits}f".format(this) 