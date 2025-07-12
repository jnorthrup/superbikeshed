package fiduciary

import borg.trikeshed.rest.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

fun main() = runBlocking {
    // Build request using trikeshed structures
    val archives = listOf(
        "https://archive.org/download/patrickdevine/patrickdevine.zip",
        "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
    )
    
    println("Fetching patrick0720.txt using trikeshed REST patterns...")
    
    archives.forEach { archiveUrl ->
        // Create request meta with headers using Join
        val headHeaders: HttpHeaders = \1 j { \2: Int ->
            when (i) {
                0 -> "User-Agent" j "TrikeShed/1.0"
                else -> throw IndexOutOfBoundsException()
            }
        }
        
        val headRequest = RequestMeta(
            method = "HEAD",
            url = archiveUrl,
            headers = headHeaders,
            timeout = 30.seconds
        )
        
        // Execute HEAD request
        val headResponse = executeRequest(headRequest j null)
        
        // Extract content length from response headers
        val contentLength = extractHeader(headResponse.component1().headers, "Content-Length")
            ?.component2()?.toLongOrNull() ?: 0L
        
        println("\nArchive: $archiveUrl")
        println("Size: $contentLength bytes")
        
        // Range request for last 256KB
        val rangeStart = maxOf(0, contentLength - 256 * 1024)
        val rangeHeaders: HttpHeaders = \1 j { \2: Int ->
            when (i) {
                0 -> "User-Agent" j "TrikeShed/1.0"
                1 -> "Range" j "bytes=$rangeStart-"
                else -> throw IndexOutOfBoundsException()
            }
        }
        
        val rangeRequest = RequestMeta(
            method = "GET",
            url = archiveUrl,
            headers = rangeHeaders,
            timeout = 60.seconds
        )
        
        val rangeResponse = executeRequest(rangeRequest j null)
        
        if (rangeResponse.component1().statusCode == 206) {
            println("Got ${rangeResponse.component2().size} bytes from range request")
            // TODO: Parse ZIP central directory
        }
    }
}

// Mock implementation - replace with actual HTTP client
suspend fun executeRequest(request: HttpRequest): HttpResponse {
    // This would use actual HTTP implementation
    val responseMeta = ResponseMeta(
        statusCode = 200,
        headers = 0 j { throw IndexOutOfBoundsException() },
        duration = 1.seconds
    )
    return responseMeta j ByteArray(0)
}

fun extractHeader(headers: HttpHeaders, name: String): Join<String, String>? {
    for (i in 0 until headers.component1()) {
        val header = headers.component2()(i)
        if (header.component1().equals(name, ignoreCase = true)) {
            return header
        }
    }
    return null
}