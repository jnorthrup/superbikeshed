@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

class TrikeDownloaderTest {
    
    @Test
    fun `should start and stop correctly`() = runTest {
        val downloader = TrikeDownloader(
            maxConcurrentDownloads = 3,
            downloadDir = "./test-downloads",
            tempDir = "./test-temp"
        )
        
        // Initially not running
        val initialStats = downloader.getStats()
        assertEquals(0, initialStats.totalDownloads)
        assertEquals(0, initialStats.activeDownloads)
        
        // Start the downloader
        downloader.start()
        
        // Should be running
        val runningStats = downloader.getStats()
        assertEquals(0, runningStats.totalDownloads) // No downloads yet
        
        // Stop the downloader
        downloader.stop()
        
        // Should be stopped
        val stoppedStats = downloader.getStats()
        assertEquals(0, stoppedStats.totalDownloads)
    }
    
    @Test
    fun `should add HTTP download correctly`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        assertTrue(id.startsWith("download_"))
        assertTrue(id.length > 10)
        
        downloader.stop()
    }
    
    @Test
    fun `should add torrent download correctly`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addTorrentDownload(
            url = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678",
            outputPath = "./test-downloads/torrent"
        )
        
        assertTrue(id.startsWith("download_"))
        assertTrue(id.length > 10)
        
        downloader.stop()
    }
    
    @Test
    fun `should add HTTP download with custom parameters`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val headers = mapOf("User-Agent" to "TrikeTest/1.0")
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip",
            headers = headers,
            method = "POST",
            resume = false
        )
        
        assertTrue(id.startsWith("download_"))
        
        downloader.stop()
    }
    
    @Test
    fun `should add torrent download with custom parameters`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val trackers = listOf("udp://tracker.opentrackr.org:1337")
        val id = downloader.addTorrentDownload(
            url = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678",
            outputPath = "./test-downloads/torrent",
            trackers = trackers,
            maxPeers = 100
        )
        
        assertTrue(id.startsWith("download_"))
        
        downloader.stop()
    }
    
    @Test
    fun `should get download progress`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        // Wait a bit for download to start
        delay(100)
        
        val progress = downloader.getProgress(id)
        assertNotNull(progress)
        assertEquals(id, progress.id)
        assertTrue(progress.status in listOf(
            TrikeDownloader.DownloadStatus.Pending,
            TrikeDownloader.DownloadStatus.Downloading
        ))
        
        downloader.stop()
    }
    
    @Test
    fun `should list all downloads`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id1 = downloader.addHttpDownload(
            url = "https://example.com/file1.zip",
            outputPath = "./test-downloads/file1.zip"
        )
        
        val id2 = downloader.addTorrentDownload(
            url = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678",
            outputPath = "./test-downloads/torrent"
        )
        
        // Wait a bit for downloads to start
        delay(100)
        
        val downloads = downloader.listDownloads()
        assertTrue(downloads.size >= 2)
        
        val downloadIds = downloads.map { it.id }
        assertTrue(downloadIds.contains(id1))
        assertTrue(downloadIds.contains(id2))
        
        downloader.stop()
    }
    
    @Test
    fun `should get global statistics`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id1 = downloader.addHttpDownload(
            url = "https://example.com/file1.zip",
            outputPath = "./test-downloads/file1.zip"
        )
        
        val id2 = downloader.addTorrentDownload(
            url = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678",
            outputPath = "./test-downloads/torrent"
        )
        
        // Wait a bit for downloads to start
        delay(100)
        
        val stats = downloader.getStats()
        assertTrue(stats.totalDownloads >= 2)
        assertTrue(stats.activeDownloads >= 0)
        assertTrue(stats.totalDownloadedBytes >= 0)
        
        downloader.stop()
    }
    
    @Test
    fun `should pause and resume downloads`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        // Wait a bit for download to start
        delay(100)
        
        // Pause download
        downloader.pauseDownload(id)
        
        val pausedProgress = downloader.getProgress(id)
        assertNotNull(pausedProgress)
        assertEquals(TrikeDownloader.DownloadStatus.Paused, pausedProgress.status)
        
        // Resume download
        downloader.resumeDownload(id)
        
        val resumedProgress = downloader.getProgress(id)
        assertNotNull(resumedProgress)
        assertTrue(resumedProgress.status in listOf(
            TrikeDownloader.DownloadStatus.Downloading,
            TrikeDownloader.DownloadStatus.Completed
        ))
        
        downloader.stop()
    }
    
    @Test
    fun `should cancel downloads`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        // Wait a bit for download to start
        delay(100)
        
        // Cancel download
        downloader.cancelDownload(id)
        
        val cancelledProgress = downloader.getProgress(id)
        assertNull(cancelledProgress) // Should be removed from active downloads
        
        downloader.stop()
    }
    
    @Test
    fun `should respect max concurrent downloads limit`() = runTest {
        val downloader = TrikeDownloader(maxConcurrentDownloads = 2)
        downloader.start()
        
        val id1 = downloader.addHttpDownload(
            url = "https://example.com/file1.zip",
            outputPath = "./test-downloads/file1.zip"
        )
        
        val id2 = downloader.addHttpDownload(
            url = "https://example.com/file2.zip",
            outputPath = "./test-downloads/file2.zip"
        )
        
        val id3 = downloader.addHttpDownload(
            url = "https://example.com/file3.zip",
            outputPath = "./test-downloads/file3.zip"
        )
        
        // Wait a bit for downloads to process
        delay(200)
        
        val stats = downloader.getStats()
        assertTrue(stats.activeDownloads <= 2)
        
        downloader.stop()
    }
    
    @Test
    fun `should handle multiple torrent downloads`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val magnet1 = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678"
        val magnet2 = "magnet:?xt=urn:btih:abcdef1234567890abcdef1234567890abcdef12"
        
        val id1 = downloader.addTorrentDownload(
            url = magnet1,
            outputPath = "./test-downloads/torrent1"
        )
        
        val id2 = downloader.addTorrentDownload(
            url = magnet2,
            outputPath = "./test-downloads/torrent2",
            maxPeers = 50
        )
        
        // Wait a bit for downloads to start
        delay(100)
        
        val downloads = downloader.listDownloads()
        assertTrue(downloads.size >= 2)
        
        val downloadIds = downloads.map { it.id }
        assertTrue(downloadIds.contains(id1))
        assertTrue(downloadIds.contains(id2))
        
        downloader.stop()
    }
    
    @Test
    fun `should handle download progress updates`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        // Wait for download to start
        delay(100)
        
        val progress1 = downloader.getProgress(id)
        assertNotNull(progress1)
        
        // Wait a bit more for progress to update
        delay(200)
        
        val progress2 = downloader.getProgress(id)
        assertNotNull(progress2)
        
        // Progress should have increased or completed
        assertTrue(progress2.downloadedBytes >= progress1.downloadedBytes)
        
        downloader.stop()
    }
    
    @Test
    fun `should handle download completion`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        // Wait for download to complete (simulated)
        delay(2000)
        
        val progress = downloader.getProgress(id)
        assertNotNull(progress)
        assertTrue(progress.status in listOf(
            TrikeDownloader.DownloadStatus.Completed,
            TrikeDownloader.DownloadStatus.Downloading
        ))
        
        downloader.stop()
    }
    
    @Test
    fun `should handle download failure gracefully`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        // Add a download that might fail (invalid URL)
        val id = downloader.addHttpDownload(
            url = "https://invalid-url-that-does-not-exist.com/file.zip",
            outputPath = "./test-downloads/invalid.zip"
        )
        
        // Wait a bit for download to process
        delay(100)
        
        val progress = downloader.getProgress(id)
        // Should handle gracefully even if download fails
        assertNotNull(progress)
        
        downloader.stop()
    }
    
    @Test
    fun `should handle pause all downloads`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id1 = downloader.addHttpDownload(
            url = "https://example.com/file1.zip",
            outputPath = "./test-downloads/file1.zip"
        )
        
        val id2 = downloader.addTorrentDownload(
            url = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678",
            outputPath = "./test-downloads/torrent"
        )
        
        // Wait for downloads to start
        delay(100)
        
        // Pause all downloads
        downloader.listDownloads().forEach { progress ->
            if (progress.status == TrikeDownloader.DownloadStatus.Downloading) {
                downloader.pauseDownload(progress.id)
            }
        }
        
        // Check that downloads are paused
        val downloads = downloader.listDownloads()
        downloads.forEach { progress ->
            if (progress.status != TrikeDownloader.DownloadStatus.Completed) {
                assertTrue(progress.status in listOf(
                    TrikeDownloader.DownloadStatus.Paused,
                    TrikeDownloader.DownloadStatus.Pending
                ))
            }
        }
        
        downloader.stop()
    }
    
    @Test
    fun `should handle resume all downloads`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id1 = downloader.addHttpDownload(
            url = "https://example.com/file1.zip",
            outputPath = "./test-downloads/file1.zip"
        )
        
        val id2 = downloader.addTorrentDownload(
            url = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678",
            outputPath = "./test-downloads/torrent"
        )
        
        // Wait for downloads to start
        delay(100)
        
        // Pause all downloads first
        downloader.listDownloads().forEach { progress ->
            if (progress.status == TrikeDownloader.DownloadStatus.Downloading) {
                downloader.pauseDownload(progress.id)
            }
        }
        
        // Resume all downloads
        downloader.listDownloads().forEach { progress ->
            if (progress.status == TrikeDownloader.DownloadStatus.Paused) {
                downloader.resumeDownload(progress.id)
            }
        }
        
        // Check that downloads are resumed
        val downloads = downloader.listDownloads()
        downloads.forEach { progress ->
            if (progress.status != TrikeDownloader.DownloadStatus.Completed) {
                assertTrue(progress.status in listOf(
                    TrikeDownloader.DownloadStatus.Downloading,
                    TrikeDownloader.DownloadStatus.Pending
                ))
            }
        }
        
        downloader.stop()
    }
    
    @Test
    fun `should handle concurrent operations`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        // Launch multiple concurrent operations
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        
        repeat(5) { i ->
            val job = launch {
                val id = downloader.addHttpDownload(
                    url = "https://example.com/file$i.zip",
                    outputPath = "./test-downloads/file$i.zip"
                )
                
                delay(50)
                
                val progress = downloader.getProgress(id)
                assertNotNull(progress)
                
                downloader.pauseDownload(id)
                downloader.resumeDownload(id)
            }
            jobs.add(job)
        }
        
        // Wait for all operations to complete
        jobs.forEach { it.join() }
        
        val stats = downloader.getStats()
        assertTrue(stats.totalDownloads >= 5)
        
        downloader.stop()
    }
    
    @Test
    fun `should handle download status transitions`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        // Check initial status
        val initialProgress = downloader.getProgress(id)
        assertNotNull(initialProgress)
        assertTrue(initialProgress.status in listOf(
            TrikeDownloader.DownloadStatus.Pending,
            TrikeDownloader.DownloadStatus.Downloading
        ))
        
        // Wait for download to start
        delay(100)
        
        val downloadingProgress = downloader.getProgress(id)
        assertNotNull(downloadingProgress)
        assertTrue(downloadingProgress.status in listOf(
            TrikeDownloader.DownloadStatus.Downloading,
            TrikeDownloader.DownloadStatus.Completed
        ))
        
        // Pause download
        downloader.pauseDownload(id)
        
        val pausedProgress = downloader.getProgress(id)
        assertNotNull(pausedProgress)
        assertEquals(TrikeDownloader.DownloadStatus.Paused, pausedProgress.status)
        
        // Resume download
        downloader.resumeDownload(id)
        
        val resumedProgress = downloader.getProgress(id)
        assertNotNull(resumedProgress)
        assertTrue(resumedProgress.status in listOf(
            TrikeDownloader.DownloadStatus.Downloading,
            TrikeDownloader.DownloadStatus.Completed
        ))
        
        downloader.stop()
    }
    
    @Test
    fun `should handle downloader restart`() = runTest {
        val downloader = TrikeDownloader()
        
        // Start, add download, stop
        downloader.start()
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        downloader.stop()
        
        // Restart and check
        downloader.start()
        val progress = downloader.getProgress(id)
        // Should be null since downloads are cleared on stop
        assertNull(progress)
        
        downloader.stop()
    }
    
    @Test
    fun `should handle download progress with speed and ETA`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        // Wait for download to start
        delay(100)
        
        val progress = downloader.getProgress(id)
        assertNotNull(progress)
        
        // Check that speed and ETA are reasonable values
        assertTrue(progress.speed >= 0)
        assertTrue(progress.eta >= 0)
        assertTrue(progress.downloadedBytes >= 0)
        assertTrue(progress.totalBytes >= 0)
        assertTrue(progress.connections >= 0)
        
        downloader.stop()
    }
    
    @Test
    fun `should handle downloader with custom configuration`() = runTest {
        val downloader = TrikeDownloader(
            maxConcurrentDownloads = 1,
            downloadDir = "/custom/downloads",
            tempDir = "/custom/temp"
        )
        
        downloader.start()
        
        val id1 = downloader.addHttpDownload(
            url = "https://example.com/file1.zip",
            outputPath = "/custom/downloads/file1.zip"
        )
        
        val id2 = downloader.addHttpDownload(
            url = "https://example.com/file2.zip",
            outputPath = "/custom/downloads/file2.zip"
        )
        
        // Wait for downloads to process
        delay(200)
        
        val stats = downloader.getStats()
        assertTrue(stats.totalDownloads >= 2)
        assertTrue(stats.activeDownloads <= 1) // Due to maxConcurrentDownloads = 1
        
        downloader.stop()
    }
    
    @Test
    fun `should handle download cancellation during execution`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        // Wait for download to start
        delay(100)
        
        // Cancel download
        downloader.cancelDownload(id)
        
        // Wait a bit
        delay(100)
        
        // Should be removed from active downloads
        val progress = downloader.getProgress(id)
        assertNull(progress)
        
        downloader.stop()
    }
    
    @Test
    fun `should handle downloader stop during active downloads`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        val id = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        // Wait for download to start
        delay(100)
        
        // Stop downloader while downloads are active
        downloader.stop()
        
        // Downloads should be cleared
        val progress = downloader.getProgress(id)
        assertNull(progress)
    }
    
    @Test
    fun `should handle multiple download types simultaneously`() = runTest {
        val downloader = TrikeDownloader()
        downloader.start()
        
        // Add HTTP download
        val httpId = downloader.addHttpDownload(
            url = "https://example.com/file.zip",
            outputPath = "./test-downloads/file.zip"
        )
        
        // Add torrent download
        val torrentId = downloader.addTorrentDownload(
            url = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678",
            outputPath = "./test-downloads/torrent"
        )
        
        // Wait for downloads to start
        delay(100)
        
        val downloads = downloader.listDownloads()
        assertTrue(downloads.size >= 2)
        
        val downloadIds = downloads.map { it.id }
        assertTrue(downloadIds.contains(httpId))
        assertTrue(downloadIds.contains(torrentId))
        
        downloader.stop()
    }
} 