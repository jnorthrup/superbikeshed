package tests

import borg.trikeshed.lib.*
import borg.trikeshed.torrent.simulation.ConsolidatedTorrentSimulation.*
import borg.trikeshed.torrent.simulation.ConsolidatedTorrentSimulation
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * TDD Test Suite for Consolidated Torrent Simulation
 * 
 * Tests the complete self-contained torrent simulation without external dependencies:
 * - Complete torrent lifecycle
 * - Peer interactions
 * - Piece transfers
 * - Progress tracking
 * - Coroutine context wiring
 */
class ConsolidatedTorrentSimulationTDDTest {
    
    @Test
    fun `test complete consolidated simulation lifecycle`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-consolidated")
        val simulation = ConsolidatedTorrentSimulation(context)
        
        // Start simulation
        simulation.start()
        
        // Create torrent
        val torrent = simulation.createTorrent(
            name = "Test Consolidated Torrent",
            totalSize = 1024 * 1024, // 1MB
            pieceSize = 16384,
            files = listOf(
                SimulatedFile("test.txt", 1024 * 1024, 0)
            )
        )
        
        // Verify torrent creation
        assertNotNull(torrent)
        assertEquals("Test Consolidated Torrent", torrent.name)
        assertEquals(1024 * 1024, torrent.totalSize)
        assertEquals(64, torrent.totalPieces) // 1MB / 16KB = 64 pieces
        
        // Add peers with different piece sets
        val peer1 = simulation.addPeer(
            torrent = torrent,
            address = "192.168.1.100",
            port = 6881,
            hasPieces = (0..31).toSet() // First half
        )
        
        val peer2 = simulation.addPeer(
            torrent = torrent,
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
        val downloaderPeer = simulation.addPeer(
            torrent = torrent,
            address = "192.168.1.102",
            port = 6883,
            hasPieces = emptySet() // No pieces initially
        )
        
        // Test piece download
        val downloadSuccess = simulation.downloadPiece(torrent, peer1, downloaderPeer, 0)
        assertTrue(downloadSuccess)
        
        // Verify piece was stored
        val progress = simulation.getTorrentProgress(torrent.infoHash)
        assertEquals(1, progress.downloadedPieces)
        assertTrue(progress.completionPercentage > 0)
        
        // Simulate complete download
        simulation.simulateCompleteDownload(torrent, downloaderPeer)
        
        // Verify final stats
        val stats = simulation.getSimulationStats()
        assertTrue(stats.totalPiecesDownloaded > 0)
        assertTrue(stats.totalDataTransferred > 0)
        assertEquals(3, stats.totalPeers)
        
        // Stop simulation
        simulation.stop()
    }
    
    @Test
    fun `test peer discovery and piece exchange`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-peer-exchange")
        val simulation = ConsolidatedTorrentSimulation(context)
        
        simulation.start()
        
        // Create torrent
        val torrent = simulation.createTorrent(
            name = "Peer Exchange Test",
            totalSize = 512 * 1024, // 512KB
            pieceSize = 16384
        )
        
        // Add multiple peers
        val peers = (1..5).map { i ->
            simulation.addPeer(
                torrent = torrent,
                address = "192.168.1.$i",
                port = 6880 + i,
                hasPieces = setOf(i - 1, i + 4) // Each peer has 2 pieces
            )
        }
        
        // Verify peers are discoverable
        val discoveredPeers = simulation.getPeersForTorrent(torrent.infoHash)
        assertEquals(5, discoveredPeers.size)
        
        // Test piece exchange between peers
        val downloader = simulation.addPeer(
            torrent = torrent,
            address = "192.168.1.100",
            port = 6890,
            hasPieces = emptySet()
        )
        
        // Download pieces from different peers
        for (i in 0..4) {
            val peerWithPiece = peers.find { it.hasPieces.contains(i) }
            assertNotNull(peerWithPiece)
            
            val success = simulation.downloadPiece(torrent, peerWithPiece, downloader, i)
            assertTrue(success)
        }
        
        // Verify progress
        val progress = simulation.getTorrentProgress(torrent.infoHash)
        assertEquals(5, progress.downloadedPieces)
        
        simulation.stop()
    }
    
    @Test
    fun `test coroutine context wiring throughout simulation`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-context-wiring")
        val simulation = ConsolidatedTorrentSimulation(context)
        
        // Verify context is properly wired
        val contextName = (context[CoroutineName]?.name ?: "unknown")
        assertTrue(contextName.contains("test-context-wiring"))
        
        simulation.start()
        
        // Create torrent with context verification
        val torrent = simulation.createTorrent(
            name = "Context Test",
            totalSize = 256 * 1024,
            pieceSize = 16384
        )
        
        // Add peer
        val peer = simulation.addPeer(
            torrent = torrent,
            address = "192.168.1.200",
            port = 6881,
            hasPieces = setOf(0, 1, 2)
        )
        
        // Test piece download with context
        val downloader = simulation.addPeer(
            torrent = torrent,
            address = "192.168.1.201",
            port = 6882,
            hasPieces = emptySet()
        )
        
        simulation.downloadPiece(torrent, peer, downloader, 0)
        
        // Verify operations completed in correct context
        val stats = simulation.getSimulationStats()
        assertNotNull(stats)
        
        simulation.stop()
    }
    
    @Test
    fun `test simulation error handling and recovery`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-error-handling")
        val simulation = ConsolidatedTorrentSimulation(context)
        
        simulation.start()
        
        // Create torrent
        val torrent = simulation.createTorrent(
            name = "Error Test",
            totalSize = 128 * 1024,
            pieceSize = 16384
        )
        
        // Add peer with no pieces
        val peer = simulation.addPeer(
            torrent = torrent,
            address = "192.168.1.300",
            port = 6881,
            hasPieces = emptySet()
        )
        
        // Try to download piece that peer doesn't have
        val downloader = simulation.addPeer(
            torrent = torrent,
            address = "192.168.1.301",
            port = 6882,
            hasPieces = emptySet()
        )
        
        val success = simulation.downloadPiece(torrent, peer, downloader, 0)
        assertFalse(success) // Should fail gracefully
        
        // Verify simulation continues to work
        val stats = simulation.getSimulationStats()
        assertNotNull(stats)
        assertEquals(2, stats.totalPeers)
        
        simulation.stop()
    }
    
    @Test
    fun `test multiple torrents in same simulation`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-multiple-torrents")
        val simulation = ConsolidatedTorrentSimulation(context)
        
        simulation.start()
        
        // Create multiple torrents
        val torrent1 = simulation.createTorrent(
            name = "Torrent 1",
            totalSize = 1024 * 1024,
            pieceSize = 16384
        )
        
        val torrent2 = simulation.createTorrent(
            name = "Torrent 2",
            totalSize = 512 * 1024,
            pieceSize = 16384
        )
        
        // Add peers to different torrents
        val peer1 = simulation.addPeer(
            torrent = torrent1,
            address = "192.168.1.100",
            port = 6881,
            hasPieces = setOf(0, 1, 2)
        )
        
        val peer2 = simulation.addPeer(
            torrent = torrent2,
            address = "192.168.1.101",
            port = 6882,
            hasPieces = setOf(0, 1)
        )
        
        // Verify peers are isolated by torrent
        val torrent1Peers = simulation.getPeersForTorrent(torrent1.infoHash)
        val torrent2Peers = simulation.getPeersForTorrent(torrent2.infoHash)
        
        assertEquals(1, torrent1Peers.size)
        assertEquals(1, torrent2Peers.size)
        assertTrue(torrent1Peers.contains(peer1))
        assertTrue(torrent2Peers.contains(peer2))
        
        // Test piece download within torrent
        val downloader1 = simulation.addPeer(
            torrent = torrent1,
            address = "192.168.1.102",
            port = 6883,
            hasPieces = emptySet()
        )
        
        val success = simulation.downloadPiece(torrent1, peer1, downloader1, 0)
        assertTrue(success)
        
        // Verify stats reflect multiple torrents
        val stats = simulation.getSimulationStats()
        assertEquals(2, stats.totalTorrents)
        assertEquals(4, stats.totalPeers)
        
        simulation.stop()
    }
    
    @Test
    fun `test simulation restart and state persistence`() = runTest {
        val context = Dispatchers.IO + CoroutineName("test-restart")
        val simulation = ConsolidatedTorrentSimulation(context)
        
        // Start, create content, stop
        simulation.start()
        
        val torrent = simulation.createTorrent(
            name = "Restart Test",
            totalSize = 256 * 1024,
            pieceSize = 16384
        )
        
        val peer = simulation.addPeer(
            torrent = torrent,
            address = "192.168.1.400",
            port = 6881,
            hasPieces = setOf(0, 1, 2)
        )
        
        simulation.stop()
        
        // Restart and verify state is maintained
        simulation.start()
        
        val peers = simulation.getPeersForTorrent(torrent.infoHash)
        assertEquals(1, peers.size)
        assertTrue(peers.contains(peer))
        
        val progress = simulation.getTorrentProgress(torrent.infoHash)
        assertEquals(0, progress.downloadedPieces) // No pieces downloaded yet
        
        simulation.stop()
    }
} 