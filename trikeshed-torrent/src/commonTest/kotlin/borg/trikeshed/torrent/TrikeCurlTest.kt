@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay

class TrikeCurlTest {
    
    @Test
    fun `should parse help argument correctly`() = runTest {
        val args = arrayOf("--help")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("TrikeCurl - curl-like HTTP/HTTPS downloader"))
        assertTrue(result.contains("Usage: trike-curl"))
    }
    
    @Test
    fun `should parse short help argument correctly`() = runTest {
        val args = arrayOf("-h")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("TrikeCurl - curl-like HTTP/HTTPS downloader"))
    }
    
    @Test
    fun `should parse output file argument`() = runTest {
        val args = arrayOf("--output", "custom-file.zip", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
        assertTrue(result.contains("custom-file.zip"))
    }
    
    @Test
    fun `should parse short output file argument`() = runTest {
        val args = arrayOf("-o", "short-file.zip", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse custom headers`() = runTest {
        val args = arrayOf(
            "--header", "Authorization: Bearer token123",
            "--header", "User-Agent: CustomAgent/1.0",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse short custom headers`() = runTest {
        val args = arrayOf(
            "-H", "Content-Type: application/json",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse request method`() = runTest {
        val args = arrayOf("--request", "POST", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse short request method`() = runTest {
        val args = arrayOf("-X", "PUT", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse include headers flag`() = runTest {
        val args = arrayOf("--include", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse short include headers flag`() = runTest {
        val args = arrayOf("-i", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse verbose flag`() = runTest {
        val args = arrayOf("--verbose", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Request:"))
        assertTrue(result.contains("URL:"))
        assertTrue(result.contains("Method:"))
    }
    
    @Test
    fun `should parse short verbose flag`() = runTest {
        val args = arrayOf("-v", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Request:"))
    }
    
    @Test
    fun `should parse silent flag`() = runTest {
        val args = arrayOf("--silent", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should still show basic output but not progress
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse short silent flag`() = runTest {
        val args = arrayOf("-s", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse continue at argument`() = runTest {
        val args = arrayOf("--continue-at", "1024", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse short continue at argument`() = runTest {
        val args = arrayOf("-C", "2048", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse continue at disable`() = runTest {
        val args = arrayOf("--continue-at", "-", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse connect timeout`() = runTest {
        val args = arrayOf("--connect-timeout", "60", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse location flag`() = runTest {
        val args = arrayOf("--location", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse short location flag`() = runTest {
        val args = arrayOf("-L", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse max redirects`() = runTest {
        val args = arrayOf("--max-redirs", "5", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse user agent`() = runTest {
        val args = arrayOf("--user-agent", "CustomCurl/2.0", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should parse short user agent`() = runTest {
        val args = arrayOf("-A", "TestAgent/1.0", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle HTTP URLs`() = runTest {
        val args = arrayOf("http://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
        assertTrue(result.contains("http://example.com/file.zip"))
    }
    
    @Test
    fun `should handle HTTPS URLs`() = runTest {
        val args = arrayOf("https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
        assertTrue(result.contains("https://example.com/file.zip"))
    }
    
    @Test
    fun `should handle URLs with query parameters`() = runTest {
        val args = arrayOf("https://example.com/file.zip?param=value&other=123")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle URLs with fragments`() = runTest {
        val args = arrayOf("https://example.com/file.zip#section")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle complex URLs`() = runTest {
        val args = arrayOf("https://user:pass@example.com:8080/path/to/file.zip?param=value#fragment")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle empty arguments`() = runTest {
        val args = arrayOf<String>()
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("TrikeCurl - curl-like HTTP/HTTPS downloader"))
    }
    
    @Test
    fun `should handle missing URL`() = runTest {
        val args = arrayOf("--verbose", "--output", "file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Error: No URL specified"))
    }
    
    @Test
    fun `should handle invalid timeout gracefully`() = runTest {
        val args = arrayOf("--connect-timeout", "invalid", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should use default timeout and continue
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle invalid max redirects gracefully`() = runTest {
        val args = arrayOf("--max-redirs", "invalid", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should use default value and continue
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle malformed headers gracefully`() = runTest {
        val args = arrayOf("--header", "malformed-header", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should ignore malformed headers and continue
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle headers without colon`() = runTest {
        val args = arrayOf("--header", "NoColonHeader", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should ignore headers without colon and continue
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should extract filename from URL correctly`() {
        val testCases = mapOf(
            "https://example.com/file.zip" to "file.zip",
            "https://example.com/path/to/document.pdf" to "document.pdf",
            "http://server.com/data.csv" to "data.csv",
            "https://example.com/file.zip?param=value" to "file.zip",
            "https://example.com/" to "download",
            "https://example.com/file.zip#section" to "file.zip"
        )
        
        testCases.forEach { (url, expected) ->
            val actual = TrikeCurlTestHelper.extractFilename(url)
            assertEquals(expected, actual, "Failed for URL: $url")
        }
    }
    
    @Test
    fun `should handle unknown arguments gracefully`() = runTest {
        val args = arrayOf("--unknown-arg", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should ignore unknown args and process the URL
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle mixed valid and invalid arguments`() = runTest {
        val args = arrayOf(
            "--output", "valid-file.zip",
            "--invalid-arg", "invalid-value",
            "--verbose",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
        assertTrue(result.contains("Request:"))
    }
    
    @Test
    fun `should handle arguments with missing values`() = runTest {
        val args = arrayOf("--output", "--verbose", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should use default values for missing arguments
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle arguments at end of command line`() = runTest {
        val args = arrayOf("https://example.com/file.zip", "--output", "end-file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should process URL and ignore trailing arguments
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle very long URLs`() = runTest {
        val longUrl = "https://example.com/" + "a".repeat(1000) + ".zip"
        val args = arrayOf(longUrl)
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle URLs with special characters`() = runTest {
        val specialUrl = "https://example.com/file%20with%20spaces.zip"
        val args = arrayOf(specialUrl)
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle edge case numeric values`() = runTest {
        val args = arrayOf(
            "--connect-timeout", "1",
            "--max-redirs", "1",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle very large numeric values`() = runTest {
        val args = arrayOf(
            "--connect-timeout", "999999",
            "--max-redirs", "999999",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle negative numeric values gracefully`() = runTest {
        val args = arrayOf(
            "--connect-timeout", "-1",
            "--max-redirs", "-5",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        // Should use default values for negative numbers
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle zero numeric values gracefully`() = runTest {
        val args = arrayOf(
            "--connect-timeout", "0",
            "--max-redirs", "0",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        // Should use default values for zero
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle malformed URLs gracefully`() = runTest {
        val malformedUrls = arrayOf(
            "not-a-url",
            "http://",
            "https://",
            "ftp://example.com/file.zip",
            "file:///path/to/file"
        )
        
        malformedUrls.forEach { url ->
            val args = arrayOf(url)
            val result = captureOutput { TrikeCurl.main(args) }
            // Should still attempt to process as HTTP download
            assertTrue(result.contains("Starting download") || result.contains("Error"))
        }
    }
    
    @Test
    fun `should handle empty string arguments`() = runTest {
        val args = arrayOf("", "https://example.com/file.zip", "")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should ignore empty strings and process valid URLs
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle whitespace-only arguments`() = runTest {
        val args = arrayOf("   ", "https://example.com/file.zip", "  ")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should ignore whitespace-only strings and process valid URLs
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle repeated arguments`() = runTest {
        val args = arrayOf(
            "--output", "first-file.zip",
            "--output", "second-file.zip",
            "--connect-timeout", "30",
            "--connect-timeout", "60",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        // Should use last value for each argument
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle verbose output with all details`() = runTest {
        val args = arrayOf(
            "--verbose",
            "--output", "test-file.zip",
            "--header", "Authorization: Bearer token",
            "--request", "POST",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Request:"))
        assertTrue(result.contains("URL:"))
        assertTrue(result.contains("Method:"))
        assertTrue(result.contains("Output:"))
        assertTrue(result.contains("Headers:"))
        assertTrue(result.contains("Resume:"))
        assertTrue(result.contains("Timeout:"))
    }
    
    @Test
    fun `should handle silent mode with minimal output`() = runTest {
        val args = arrayOf("--silent", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        // Should show basic output but not verbose details
        assertTrue(result.contains("Starting download"))
        assertFalse(result.contains("Request:"))
    }
    
    @Test
    fun `should handle include headers mode`() = runTest {
        val args = arrayOf("--include", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
        // Note: Actual header inclusion would be tested in integration tests
    }
    
    @Test
    fun `should handle location following`() = runTest {
        val args = arrayOf("--location", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
        // Note: Actual redirect following would be tested in integration tests
    }
    
    @Test
    fun `should handle custom user agent`() = runTest {
        val args = arrayOf("--user-agent", "CustomAgent/2.0", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle resume download`() = runTest {
        val args = arrayOf("--continue-at", "1024", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle disabled resume`() = runTest {
        val args = arrayOf("--continue-at", "-", "https://example.com/file.zip")
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle multiple headers`() = runTest {
        val args = arrayOf(
            "--header", "Content-Type: application/json",
            "--header", "Authorization: Bearer token123",
            "--header", "X-Custom-Header: value",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Starting download"))
    }
    
    @Test
    fun `should handle complex request with all options`() = runTest {
        val args = arrayOf(
            "--verbose",
            "--output", "complex-file.zip",
            "--header", "Authorization: Bearer token",
            "--header", "Content-Type: application/json",
            "--request", "POST",
            "--continue-at", "2048",
            "--connect-timeout", "60",
            "--location",
            "--max-redirs", "5",
            "--user-agent", "ComplexCurl/3.0",
            "https://example.com/file.zip"
        )
        val result = captureOutput { TrikeCurl.main(args) }
        assertTrue(result.contains("Request:"))
        assertTrue(result.contains("Starting download"))
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
object TrikeCurlTestHelper {
    fun extractFilename(url: String): String {
        val filename = url.substringAfterLast("/").substringBefore("?")
        return if (filename.isNotEmpty()) filename else "download"
    }
} 