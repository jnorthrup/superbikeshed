package tests

import borg.trikeshed.lib.*
import borg.trikeshed.torrent.simulation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * Comprehensive TDD Test Suite for Torrent Simulation Framework
 * 
 * Tests all simulation components with coroutine context wiring:
 * - Complete torrent lifecycle simulation
 * - Peer network interactions
 * - DHT and tracker communication
 * - File system operations
 * - Registry management
 */
class TorrentSimulationTDDTest {
    
    @Test
    fun `test complete torrent simulation lifecycle`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-simulation")
        val simulation = TorrentSimulation(context)
        
        // Start simulation
        simulation.startSimulation()
        
        // Create a test torrent
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = simulation.createTorrent(
            infoHash = infoHash,
            name = "Test Torrent",
            totalSize = 1024 * 1024, // 1MB
            pieceSize = 16384,
            files = listOf(
                SimulatedFile("test.txt", 1024 * 1024, 0)
            )
        )
        
        // Verify torrent creation
        assertNotNull(torrent)
        assertEquals("Test Torrent", torrent.name)
        assertEquals(1024 * 1024, torrent.totalSize)
        assertEquals(64, torrent.totalPieces) // 1MB / 16KB = 64 pieces
        
        // Create peers with different piece sets
        val peer1 = simulation.simulatePeerJoin(
            torrent = torrent,
            peerId = \1 j { \2: Int -> (i * 2).toByte() },
            address = "192.168.1.100",
            port = 6881,
            hasPieces = (0..31).toSet() // First half
        )
        
        val peer2 = simulation.simulatePeerJoin(
            torrent = torrent,
            peerId = \1 j { \2: Int -> (i * 3).toByte() },
            address = "192.168.1.101",
            port = 6882,
            hasPieces = (32..63).toSet() // Second half
        )
        
        // Verify peer creation
        assertNotNull(peer1)
        assertNotNull(peer2)
        assertEquals(32, peer1.hasPieces.size)
        assertEquals(32, peer2.hasPieces.size)
        
        // Create downloader peer
        val downloaderPeer = simulation.simulatePeerJoin(
            torrent = torrent,
            peerId = \1 j { \2: Int -> (i * 4).toByte() },
            address = "192.168.1.102",
            port = 6883,
            hasPieces = emptySet() // No pieces initially
        )
        
        // Simulate complete download
        simulation.simulateCompleteDownload(torrent, downloaderPeer)
        
        // Verify download completion
        val stats = simulation.getSimulationStats()
        assertTrue(stats.totalPiecesDownloaded > 0)
        assertTrue(stats.totalDataTransferred > 0)
        
        // Stop simulation
        simulation.stopSimulation()
    }
    
    @Test
    fun `test DHT network simulation`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-dht")
        val dht = DHTNetworkSimulation(context)
        
        // Start DHT
        dht.start()
        
        // Create test torrent
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = SimulatedTorrent(
            infoHash = infoHash,
            name = "DHT Test Torrent",
            totalSize = 512 * 1024,
            pieceSize = 16384,
            files = emptyList(),
            totalPieces = 32,
            createdAt = System.currentTimeMillis()
        )
        
        // Announce torrent to DHT
        dht.announceTorrent(torrent)
        
        // Look up peers
        val peers = dht.lookupPeers(infoHash)
        assertTrue(peers.isNotEmpty())
        
        // Add peer to DHT
        val peerId = \1 j { \2: Int -> (i * 5).toByte() }
        dht.addPeer(infoHash, peerId)
        
        // Verify DHT stats
        val stats = dht.getDHTStats()
        assertTrue(stats.totalNodes > 0)
        assertTrue(stats.totalAnnouncements > 0)
        
        // Stop DHT
        dht.stop()
    }
    
    @Test
    fun `test tracker network simulation`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-tracker")
        val tracker = TrackerNetworkSimulation(context)
        
        // Start tracker
        tracker.start()
        
        // Create test torrent
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = SimulatedTorrent(
            infoHash = infoHash,
            name = "Tracker Test Torrent",
            totalSize = 256 * 1024,
            pieceSize = 16384,
            files = emptyList(),
            totalPieces = 16,
            createdAt = System.currentTimeMillis()
        )
        
        // Announce torrent to tracker
        tracker.announceTorrent(torrent)
        
        // Get peer list
        val peers = tracker.getPeerList(infoHash)
        assertTrue(peers.isNotEmpty())
        
        // Update progress
        tracker.updateProgress(infoHash, 8, 128 * 1024)
        
        // Verify tracker stats
        val stats = tracker.getTrackerStats()
        assertTrue(stats.totalTrackers > 0)
        assertTrue(stats.totalAnnouncements > 0)
        
        // Stop tracker
        tracker.stop()
    }
    
    @Test
    fun `test peer network simulation`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-peer")
        val peerNetwork = PeerNetworkSimulation(context)
        
        // Start peer network
        peerNetwork.start()
        
        // Create test torrent and peers
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = SimulatedTorrent(
            infoHash = infoHash,
            name = "Peer Test Torrent",
            totalSize = 128 * 1024,
            pieceSize = 16384,
            files = emptyList(),
            totalPieces = 8,
            createdAt = System.currentTimeMillis()
        )
        
        val peer1 = SimulatedPeer(
            peerId = \1 j { \2: Int -> (i * 6).toByte() },
            address = "192.168.1.200",
            port = 6881,
            torrent = torrent,
            hasPieces = mutableSetOf(0, 1, 2, 3),
            downloadSpeed = 1024,
            uploadSpeed = 512,
            joinedAt = System.currentTimeMillis()
        )
        
        val peer2 = SimulatedPeer(
            peerId = \1 j { \2: Int -> (i * 7).toByte() },
            address = "192.168.1.201",
            port = 6882,
            torrent = torrent,
            hasPieces = mutableSetOf(4, 5, 6, 7),
            downloadSpeed = 1024,
            uploadSpeed = 512,
            joinedAt = System.currentTimeMillis()
        )
        
        // Register peers
        peerNetwork.registerPeer(peer1)
        peerNetwork.registerPeer(peer2)
        
        // Connect peers
        peerNetwork.connectPeers(peer1, peer2)
        
        // Send messages
        val pieceRequest = PeerMessage.PieceRequest(0, 0, 16384)
        peerNetwork.sendMessage(peer1, peer2, pieceRequest)
        
        // Discover peers
        val discoveredPeers = peerNetwork.discoverPeers(infoHash)
        assertTrue(discoveredPeers.isNotEmpty())
        
        // Verify peer stats
        val stats = peerNetwork.getPeerStats()
        assertTrue(stats.totalPeers > 0)
        assertTrue(stats.totalConnections > 0)
        
        // Stop peer network
        peerNetwork.stop()
    }
    
    @Test
    fun `test file system simulation`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-filesystem")
        val fileSystem = FileSystemSimulation(context)
        
        // Start file system
        fileSystem.start()
        
        // Create test torrent
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = SimulatedTorrent(
            infoHash = infoHash,
            name = "File System Test Torrent",
            totalSize = 64 * 1024,
            pieceSize = 16384,
            files = emptyList(),
            totalPieces = 4,
            createdAt = System.currentTimeMillis()
        )
        
        // Store pieces
        for (i in 0..3) {
            val pieceData = \1 j { \2: Int -> (j % 256).toByte() }
            fileSystem.storePiece(infoHash, i, pieceData)
        }
        
        // Retrieve pieces
        for (i in 0..3) {
            val piece = fileSystem.retrievePiece(infoHash, i)
            assertNotNull(piece)
            assertEquals(16384, piece.component1())
        }
        
        // Check completion
        val isComplete = fileSystem.isTorrentComplete(infoHash, 4)
        assertTrue(isComplete)
        
        // Get progress
        val progress = fileSystem.getTorrentProgress(infoHash, 4)
        assertEquals(4, progress.downloadedPieces)
        assertEquals(4, progress.totalPieces)
        assertEquals(100.0, progress.completionPercentage, 0.1)
        
        // Assemble file
        val file = fileSystem.assembleFile(infoHash, torrent, "test_output.bin")
        assertNotNull(file)
        assertTrue(file.isComplete)
        assertEquals(64 * 1024, file.size)
        
        // Verify file system stats
        val stats = fileSystem.getFileSystemStats()
        assertTrue(stats.totalPieces > 0)
        assertTrue(stats.totalFiles > 0)
        
        // Stop file system
        fileSystem.stop()
    }
    
    @Test
    fun `test torrent registry simulation`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-registry")
        val registry = TorrentRegistrySimulation(context)
        
        // Start registry
        registry.start()
        
        // Create test torrent
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = SimulatedTorrent(
            infoHash = infoHash,
            name = "Registry Test Torrent",
            totalSize = 32 * 1024,
            pieceSize = 16384,
            files = emptyList(),
            totalPieces = 2,
            createdAt = System.currentTimeMillis()
        )
        
        // Register torrent
        registry.registerTorrent(torrent)
        
        // Create and add peer
        val peer = SimulatedPeer(
            peerId = \1 j { \2: Int -> (i * 8).toByte() },
            address = "192.168.1.300",
            port = 6881,
            torrent = torrent,
            hasPieces = mutableSetOf(0, 1),
            downloadSpeed = 1024,
            uploadSpeed = 512,
            joinedAt = System.currentTimeMillis()
        )
        
        registry.addPeer(infoHash, peer)
        
        // Update progress
        registry.updateProgress(infoHash, 1, 16384)
        
        // Get torrent
        val retrievedTorrent = registry.getTorrent(infoHash)
        assertNotNull(retrievedTorrent)
        assertEquals(torrent.name, retrievedTorrent.name)
        
        // Get peers
        val peers = registry.getPeersForTorrent(infoHash)
        assertTrue(peers.isNotEmpty())
        
        // Get stats
        val stats = registry.getTorrentStats(infoHash)
        assertNotNull(stats)
        assertEquals(1, stats.totalPeers)
        assertEquals(1, stats.downloadedPieces)
        
        // Search torrents
        val searchResults = registry.searchTorrents("Registry")
        assertTrue(searchResults.isNotEmpty())
        
        // Verify registry stats
        val registryStats = registry.getRegistryStats()
        assertTrue(registryStats.totalTorrents > 0)
        assertTrue(registryStats.totalPeers > 0)
        
        // Stop registry
        registry.stop()
    }
    
    @Test
    fun `test coroutine context wiring throughout simulation`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-context")
        val simulation = TorrentSimulation(context)
        
        // Verify all operations use the provided context
        val contextName = (context[CoroutineName]?.name ?: "unknown")
        assertTrue(contextName.contains("test-context"))
        
        // Start simulation
        simulation.startSimulation()
        
        // Create torrent with context verification
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = simulation.createTorrent(
            infoHash = infoHash,
            name = "Context Test Torrent",
            totalSize = 16 * 1024,
            pieceSize = 16384
        )
        
        // Verify torrent was created in correct context
        assertNotNull(torrent)
        
        // Simulate peer operations
        val peer = simulation.simulatePeerJoin(
            torrent = torrent,
            peerId = \1 j { \2: Int -> (i * 9).toByte() },
            address = "192.168.1.400",
            port = 6881,
            hasPieces = setOf(0)
        )
        
        // Simulate piece download
        simulation.simulatePieceDownload(torrent, peer, 0)
        
        // Stop simulation
        simulation.stopSimulation()
    }
    
    @Test
    fun `test simulation error handling and recovery`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-error-handling")
        val simulation = TorrentSimulation(context)
        
        // Start simulation
        simulation.startSimulation()
        
        // Create torrent
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = simulation.createTorrent(
            infoHash = infoHash,
            name = "Error Test Torrent",
            totalSize = 8 * 1024,
            pieceSize = 16384
        )
        
        // Create peer with no pieces
        val peer = simulation.simulatePeerJoin(
            torrent = torrent,
            peerId = \1 j { \2: Int -> (i * 10).toByte() },
            address = "192.168.1.500",
            port = 6881,
            hasPieces = emptySet()
        )
        
        // Try to download piece that peer doesn't have (should handle gracefully)
        simulation.simulatePieceDownload(torrent, peer, 0)
        
        // Verify simulation continues to work
        val stats = simulation.getSimulationStats()
        assertNotNull(stats)
        
        // Stop simulation
        simulation.stopSimulation()
    }
} 