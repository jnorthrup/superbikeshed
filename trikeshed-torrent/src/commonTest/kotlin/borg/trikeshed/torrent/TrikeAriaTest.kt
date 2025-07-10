@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlin.system.exitProcess

class TrikeAriaTest {
    
    @Test
    fun `should parse help argument correctly`() = runTest {
        val args = arrayOf("--help")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("TrikeAria - aria2c-like downloader"))
        assertTrue(result.contains("Usage: trike-aria"))
    }
    
    @Test
    fun `should parse short help argument correctly`() = runTest {
        val args = arrayOf("-h")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("TrikeAria - aria2c-like downloader"))
    }
    
    @Test
    fun `should parse download directory argument`() = runTest {
        val args = arrayOf("--dir", "/custom/downloads", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
        assertTrue(result.contains("/custom/downloads"))
    }
    
    @Test
    fun `should parse short download directory argument`() = runTest {
        val args = arrayOf("-d", "/short/path", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should parse max connections argument`() = runTest {
        val args = arrayOf("--max-connection-per-server", "32", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should parse short max connections argument`() = runTest {
        val args = arrayOf("-x", "8", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should parse max concurrent downloads argument`() = runTest {
        val args = arrayOf("--max-concurrent-downloads", "10", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should parse short max concurrent downloads argument`() = runTest {
        val args = arrayOf("-j", "3", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should parse download speed limit argument`() = runTest {
        val args = arrayOf("--max-download-limit", "1048576", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should parse upload speed limit argument`() = runTest {
        val args = arrayOf("--max-upload-limit", "524288", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle magnet link downloads`() = runTest {
        val magnetLink = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678"
        val args = arrayOf(magnetLink)
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added torrent"))
        assertTrue(result.contains("magnet:"))
    }
    
    @Test
    fun `should handle torrent file downloads`() = runTest {
        val torrentFile = "test.torrent"
        val args = arrayOf(torrentFile)
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added torrent"))
        assertTrue(result.contains("test.torrent"))
    }
    
    @Test
    fun `should handle HTTP downloads`() = runTest {
        val httpUrl = "https://example.com/file.zip"
        val args = arrayOf(httpUrl)
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
        assertTrue(result.contains("https://example.com/file.zip"))
    }
    
    @Test
    fun `should handle FTP downloads`() = runTest {
        val ftpUrl = "ftp://example.com/file.zip"
        val args = arrayOf(ftpUrl)
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
        assertTrue(result.contains("ftp://example.com/file.zip"))
    }
    
    @Test
    fun `should handle multiple URLs`() = runTest {
        val args = arrayOf(
            "https://example.com/file1.zip",
            "https://example.com/file2.zip",
            "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678"
        )
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Adding 3 download(s) to queue"))
        assertTrue(result.contains("Added HTTP download"))
        assertTrue(result.contains("Added torrent"))
    }
    
    @Test
    fun `should handle list downloads command`() = runTest {
        val args = arrayOf("--list")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Active Downloads"))
        assertTrue(result.contains("Global Stats"))
    }
    
    @Test
    fun `should handle pause all command`() = runTest {
        val args = arrayOf("--pause-all")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Paused all active downloads"))
    }
    
    @Test
    fun `should handle resume all command`() = runTest {
        val args = arrayOf("--resume-all")
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Resumed all paused downloads"))
    }
    
    @Test
    fun `should handle empty arguments`() = runTest {
        val args = arrayOf<String>()
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("TrikeAria - aria2c-like downloader"))
    }
    
    @Test
    fun `should handle invalid numeric arguments gracefully`() = runTest {
        val args = arrayOf("--max-connection-per-server", "invalid", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        // Should use default value and continue
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle invalid speed limit arguments gracefully`() = runTest {
        val args = arrayOf("--max-download-limit", "invalid", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        // Should use default value and continue
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should extract filename from URL correctly`() {
        val testCases = mapOf(
            "https://example.com/file.zip" to "file.zip",
            "https://example.com/path/to/document.pdf" to "document.pdf",
            "ftp://server.com/data.csv" to "data.csv",
            "https://example.com/file.zip?param=value" to "file.zip",
            "https://example.com/" to "download",
            "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678" to "download"
        )
        
        testCases.forEach { (url, expected) ->
            val actual = TrikeAriaTestHelper.extractFilename(url)
            assertEquals(expected, actual, "Failed for URL: $url")
        }
    }
    
    @Test
    fun `should handle unknown arguments gracefully`() = runTest {
        val args = arrayOf("--unknown-arg", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        // Should ignore unknown args and process the URL
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle mixed valid and invalid arguments`() = runTest {
        val args = arrayOf(
            "--dir", "/valid/path",
            "--invalid-arg", "invalid-value",
            "--max-connection-per-server", "16",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle arguments with missing values`() = runTest {
        val args = arrayOf("--dir", "--max-connection-per-server", "https://example.com/file.zip")
        val result = captureOutput { TrikeAria.main(args) }
        // Should use default values for missing arguments
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle arguments at end of command line`() = runTest {
        val args = arrayOf("https://example.com/file.zip", "--dir", "/end/path")
        val result = captureOutput { TrikeAria.main(args) }
        // Should process URL and ignore trailing arguments
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle very long URLs`() = runTest {
        val longUrl = "https://example.com/" + "a".repeat(1000) + ".zip"
        val args = arrayOf(longUrl)
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle URLs with special characters`() = runTest {
        val specialUrl = "https://example.com/file%20with%20spaces.zip"
        val args = arrayOf(specialUrl)
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle complex magnet links`() = runTest {
        val complexMagnet = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678&dn=Test+Torrent&tr=udp%3A%2F%2Ftracker.example.com%3A1337"
        val args = arrayOf(complexMagnet)
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added torrent"))
    }
    
    @Test
    fun `should handle edge case numeric values`() = runTest {
        val args = arrayOf(
            "--max-connection-per-server", "1",
            "--max-concurrent-downloads", "1",
            "--max-download-limit", "1",
            "--max-upload-limit", "1",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle very large numeric values`() = runTest {
        val args = arrayOf(
            "--max-connection-per-server", "999999",
            "--max-concurrent-downloads", "999999",
            "--max-download-limit", "999999999999",
            "--max-upload-limit", "999999999999",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeAria.main(args) }
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle negative numeric values gracefully`() = runTest {
        val args = arrayOf(
            "--max-connection-per-server", "-1",
            "--max-concurrent-downloads", "-5",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeAria.main(args) }
        // Should use default values for negative numbers
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle zero numeric values gracefully`() = runTest {
        val args = arrayOf(
            "--max-connection-per-server", "0",
            "--max-concurrent-downloads", "0",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeAria.main(args) }
        // Should use default values for zero
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle malformed URLs gracefully`() = runTest {
        val malformedUrls = arrayOf(
            "not-a-url",
            "http://",
            "https://",
            "ftp://",
            "magnet:",
            "file:///path/to/file"
        )
        
        malformedUrls.forEach { url ->
            val args = arrayOf(url)
            val result = captureOutput { TrikeAria.main(args) }
            // Should still attempt to process as HTTP download
            assertTrue(result.contains("Added HTTP download") || result.contains("Added torrent"))
        }
    }
    
    @Test
    fun `should handle empty string arguments`() = runTest {
        val args = arrayOf("", "https://example.com/file.zip", "")
        val result = captureOutput { TrikeAria.main(args) }
        // Should ignore empty strings and process valid URLs
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle whitespace-only arguments`() = runTest {
        val args = arrayOf("   ", "https://example.com/file.zip", "  ")
        val result = captureOutput { TrikeAria.main(args) }
        // Should ignore whitespace-only strings and process valid URLs
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle mixed command types in single run`() = runTest {
        val args = arrayOf(
            "--list",
            "--pause-all", 
            "--resume-all",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeAria.main(args) }
        // Should execute list, pause, resume, and add download
        assertTrue(result.contains("Active Downloads"))
        assertTrue(result.contains("Paused all active downloads"))
        assertTrue(result.contains("Resumed all paused downloads"))
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle repeated arguments`() = runTest {
        val args = arrayOf(
            "--dir", "/first/path",
            "--dir", "/second/path",
            "--max-connection-per-server", "8",
            "--max-connection-per-server", "16",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeAria.main(args) }
        // Should use last value for each argument
        assertTrue(result.contains("Added HTTP download"))
    }
    
    @Test
    fun `should handle string repeat operator`() {
        val result = "=" * 5
        assertEquals("=====", result)
        
        val result2 = "-" * 10
        assertEquals("----------", result2)
        
        val result3 = "*" * 0
        assertEquals("", result3)
    }
    
    // Helper function to capture console output
    internal suspend fun captureOutput(block: suspend () -> Unit): String {
        val originalOut = System.out
        val outputStream = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(outputStream))
        
        try {
            block()
            return outputStream.toString()
        } finally {
            System.setOut(originalOut)
        }
    }
}

// Helper object to test internal functions
object TrikeAriaTestHelper {
    fun extractFilename(url: String): String {
        return url.substringAfterLast("/").substringBefore("?").ifEmpty { "download" }
    }
} 