@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import kotlin.test.*
import kotlinx.coroutines.test.runTest

class TorrentHostTest {
    
    @Test
    fun `TorrentHost should start and stop correctly`() = runTest {
        val host = TorrentHost(port = 6882)
        
        // Initially not running
        val initialStats = host.getStats()
        assertFalse(initialStats.isRunning)
        assertEquals(6882, initialStats.port)
        assertEquals(0, initialStats.activeTorrents)
        
        // Start the host
        host.start()
        
        // Should be running now
        val runningStats = host.getStats()
        assertTrue(runningStats.isRunning)
        assertEquals(6882, runningStats.port)
        
        // Stop the host
        host.stop()
        
        // Should be stopped
        val stoppedStats = host.getStats()
        assertFalse(stoppedStats.isRunning)
    }
    
    @Test
    fun `TorrentHost should manage torrents`() = runTest {
        val host = TorrentHost(port = 6883)
        host.start()
        
        // Create a test torrent
        val testTorrent = TorrentHost.TorrentInfo(
            infoHash = "test-hash-123".toByteArray(),
            name = "Test Torrent",
            files = listOf(
                TorrentHost.TorrentFile(
                    path = "test.txt",
                    size = 1024L,
                    offset = 0L
                )
            ),
            pieceSize = 16384,
            pieces = listOf("piece1".toByteArray()),
            totalSize = 1024L
        )
        
        // Add torrent
        host.addTorrent(testTorrent)
        
        val stats = host.getStats()
        assertEquals(1, stats.activeTorrents)
        
        // Remove torrent
        host.removeTorrent(testTorrent.infoHash)
        
        val statsAfterRemoval = host.getStats()
        assertEquals(0, statsAfterRemoval.activeTorrents)
        
        host.stop()
    }
} 