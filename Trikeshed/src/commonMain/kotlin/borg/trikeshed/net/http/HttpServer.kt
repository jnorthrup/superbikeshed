@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.net.http

import borg.trikeshed.ccek.CcekContext
import borg.trikeshed.lib.*
import borg.trikeshed.reactor.*
// import borg.trikeshed.services.DealService
// import borg.trikeshed.services.*
import borg.trikeshed.io.*
import borg.trikeshed.net.http.HttpParser
import kotlin.jvm.JvmInline
import kotlinx.coroutines.*

// RFC 7230 Compliant HTTP/1.1 Server Implementation

@JvmInline value class HttpServerPort(val value: Int)
@JvmInline value class HttpServerHost(val value: String)

typealias HttpHandler = suspend (HttpRequest) -> HttpResponse

/** A CCEK-aware handler. It takes the raw request and the specific CCEK. */
typealias CcekHttpHandler = suspend (HttpRequest, CcekContext) -> HttpResponse

data class HttpServerConfig(
    val host: HttpServerHost = HttpServerHost("0.0.0.0"),
    val port: HttpServerPort = HttpServerPort(8080),
    val maxHeaderSize: Int = 8192,
    val maxBodySize: Long = 1024 * 1024,  // 1MB
    val keepAliveTimeout: Long = 5000,     // 5 seconds
    val maxConnections: Int = 1000,
    val enableCompression: Boolean = true
)

/**
 * Creates a router that combines multiple handlers with priority ordering.
 * This allows us to handle both API endpoints and static files.
 */
/*
fun createRouter(
    staticRoot: String,
    dealService: DealService,
    requestFactoryService: RequestFactoryService
): HttpHandler {
    val staticHandler = createStaticFileHandler(staticRoot)
    val batchHandler = createBatchHandler(dealService)
    val requestFactoryHandler = createRequestFactoryHandler(requestFactoryService)

    return { request ->
        // Log incoming request
        println("Routing request: ${request.method} ${request.path.value}")

        when {
            // RequestFactory endpoint takes highest precedence
            request.path.value == "/gwtRequest" -> requestFactoryHandler(request)
            
            // API endpoints take next precedence
            request.path.value == "/api/batch" -> batchHandler(request)
            
            // Static files as fallback
            else -> staticHandler(request)
        }
    }
}
*/

class HttpServer(
    private val config: HttpServerConfig,
    private val reactor: Reactor,
    private val handler: CcekHttpHandler
) {
    private lateinit var serverChannel: ServerChannel

    /**
     * The server's main loop. It receives a request and then WAITS for the
     * orchestrator (`main`) to provide the CCEK for that request.
     */
    suspend fun processRequest(request: HttpRequest, ccek: CcekContext) {
        println("--- Server received request for path: ${request.path.value} ---")
        val response = handler(request, ccek)
        println("--- Server sending response: ${response.status.value} ---")
        // (Network write logic would go here, e.g., channel.write(response.toByteArray()))
    }

    suspend fun start() {
        serverChannel = PlatformIO.create().createServerChannel()
        serverChannel.configureBlocking(false)
        serverChannel.bind(config.port.value)
        println("TrikeShed HTTP Server started on ${config.host.value}:${config.port.value}")

        reactor.register(serverChannel) {
            val clientChannel = serverChannel.accept()
            if (clientChannel != null) {
                clientChannel.configureBlocking(false)
                // In a real implementation, this would hand off to the MainOrchestrator
                // to build the CCEK and call processRequest.
                reactor.reactorScope.launch {
                    println("Accepted connection from ${clientChannel.remoteAddress}")
                    clientChannel.close() // Simplified for this example
                }
            }
            null // No further reaction needed
        }
        
        println("TrikeShed HTTP Server running on port ${config.port.value}")
        reactor.run()
    }

    suspend fun stop() {
        if (::serverChannel.isInitialized) {
            serverChannel.close()
            reactor.stop()
            println("TrikeShed HTTP Server stopped.")
        }
    }
}

class HttpConnectionHandler(
    private val config: HttpServerConfig,
    private val reactor: Reactor,
    private val channel: ClientChannel,
    private val handler: HttpHandler
) {
    suspend fun handle() {
        try {
            // Read request data directly into ByteArray
            val buffer = ByteArray(8192) // 8KB buffer
            val bytesRead = channel.read(ByteBuffer.wrap(buffer))
            if (bytesRead <= 0) {
                channel.close()
                return
            }

            val requestBytes = buffer.copyOf(bytesRead)
            val requestString = requestBytes.decodeToString()

            // Use RFC7230 parser
            val requestMessage = HttpParser.parseHttpMessage(requestString.toSeries())
            if (requestMessage == null) {
                sendErrorResponse(400, "Bad Request")
                return
            }

            val request = convertToHttpRequest(requestMessage)
            if (request == null) {
                sendErrorResponse(400, "Bad Request")
                return
            }

            val response = try {
                handler(request)
            } catch (e: Exception) {
                HttpResponse(HttpStatusCode(500), HttpReasonPhrase("Internal Server Error"))
            }

            // Write response directly using ByteArray
            val responseBytes = response.toByteArray()
            channel.write(ByteBuffer.wrap(responseBytes))
        } catch (e: Exception) {
            println("Connection error: ${e.message}")
        } finally {
            channel.close()
        }
    }

    private fun convertToHttpRequest(message: HttpMessage): HttpRequest? {
        val startLine = message.startLine as? HttpRequestLine ?: return null
        val headers = message.headerFields.α { HttpHeaderName(it.a.value) j HttpHeaderValue(it.b.value) }
        return HttpRequest(startLine.method, HttpRequestPath(startLine.requestTarget.value), headers, message.messageBody, startLine.httpVersion)
    }

    private suspend fun sendErrorResponse(code: Int, phrase: String) {
        val response = HttpResponse(HttpStatusCode(code), HttpReasonPhrase(phrase))
        val responseBytes = response.toByteArray()
        channel.write(ByteBuffer.wrap(responseBytes))
        channel.close()
    }
}

fun createStaticFileHandler(rootDir: String): HttpHandler {
    return { request ->
        val path = request.path.value.substringBefore('?')
        val sanitizedPath = path.removePrefix("/").replace("../", "")
        
        var file = PlatformFile("$rootDir/$sanitizedPath")
        if (!file.exists() || file.isDirectory()) { // Simplified directory check
            file = PlatformFile("$rootDir/$sanitizedPath/index.html")
        }

        if (!file.exists()) {
            HttpResponse(HttpStatusCode(404), HttpReasonPhrase("Not Found"))
        } else {
            // Opportunistic Gzip
            val acceptEncoding = request.headers.play.find { it.a.value.equals("Accept-Encoding", ignoreCase = true) }?.b?.value ?: ""
            val gzFile = PlatformFile("${file.path}.gz")
            
            val (fileToSend, contentEncoding) = if ("gzip" in acceptEncoding && gzFile.exists()) {
                gzFile to "gzip"
            } else {
                file to null
            }

            val bodyBytes = fileToSend.readAllBytes()

            val headers = mutableListOf<Join<HttpHeaderName, HttpHeaderValue>>()
            headers.add(HttpHeaderName("Content-Type") j HttpHeaderValue(getMimeType(file.path)))
            headers.add(HttpHeaderName("Content-Length") j HttpHeaderValue(bodyBytes.size.toString()))
            contentEncoding?.let {
                headers.add(HttpHeaderName("Content-Encoding") j HttpHeaderValue(it))
            }

            HttpResponse(
                status = HttpStatusCode(200),
                headers = headers.size j { headers[it] },
                body = bodyBytes
            )
        }
    }
}

private fun getMimeType(path: String): String {
    return when (path.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js" -> "application/javascript"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        else -> "application/octet-stream"
    }
}

private fun HttpResponse(
    status: HttpStatusCode,
    reasonPhrase: HttpReasonPhrase = HttpReasonPhrase("OK")
): HttpResponse {
    return HttpResponse(
        status = status,
        reasonPhrase = reasonPhrase,
        headers = 0 j { HttpHeaderName("") j HttpHeaderValue("") }, // Empty headers
        body = byteArrayOf() // Empty body
    )
}

private fun String.toSeries(): Indexed<Char> = this.length j { this[it] }


// ===== CONNECTION MANAGEMENT (RFC 7230 Section 6) =====

class HttpConnectionManager(private val config: HttpServerConfig) {
    
    private val connections = mutableMapOf<String, HttpConnection>()
    
    data class HttpConnection(
        val id: String,
        val keepAlive: Boolean,
        val lastActivity: Long,
        val requestCount: Int = 0
    )
    
    fun shouldKeepAlive(headers: Indexed2<HttpFieldName, HttpFieldValue>, version: HttpVersion): Boolean {
        val connectionHeader = headers.`play`.find {
            it.a.value.lowercase() == "connection" 
        }?.b?.value?.lowercase()
        
        return when {
            connectionHeader == "close" -> false
            connectionHeader == "keep-alive" -> true
            version.value == "HTTP/1.1" -> true  // Default for HTTP/1.1
            else -> false  // Default for HTTP/1.0
        }
    }
    
    fun handleConnectionUpgrade(
        headers: Indexed2<HttpFieldName, HttpFieldValue>
    ): UpgradeProtocol? {
        if (!HttpUpgrade.canUpgrade(headers.α { join -> 
                HttpFieldName(join.a.value) j HttpFieldValue(join.b.value) 
            }, ProtocolName("websocket"))) {
            return null
        }
        
        return UpgradeProtocol(ProtocolName("websocket"), ProtocolVersion("13"))
    }
}

// ===== CHUNKED TRANSFER ENCODING SUPPORT =====

object ChunkedTransferEncoder {
    
    fun encodeChunked(data: ByteArray): ByteArray {
        val chunkSize = minOf(8192, data.size)  // 8KB chunks
        val chunks = mutableListOf<Byte>()
        
        var offset = 0
        while (offset < data.size) {
            val currentChunkSize = minOf(chunkSize, data.size - offset)
            
            // Chunk size in hex + CRLF
            val sizeHex = currentChunkSize.toString(16)
            chunks.addAll("$sizeHex\r\n".encodeToByteArray().toList())
            
            // Chunk data
            for (i in 0 until currentChunkSize) {
                chunks.add(data[offset + i])
            }
            
            // Trailing CRLF
            chunks.addAll("\r\n".encodeToByteArray().toList())
            
            offset += currentChunkSize
        }
        
        // Final chunk (size 0) + CRLF
        chunks.addAll("0\r\n\r\n".encodeToByteArray().toList())
        
        return chunks.toByteArray()
    }
    
    fun decodeChunked(input: ByteArray): ByteArray? {
        val inputChars = input.map { it.toInt().toChar() }
        val chunkedBody = HttpParser.parseChunkedBody(inputChars.toSeries()) ?: return null
        
        val allData = mutableListOf<Byte>()
        chunkedBody.chunks.`play`.forEach { chunk ->
            allData.addAll(chunk.data.`play`)
        }
        
        return allData.toByteArray()
    }
}

// ===== UTILITY EXTENSIONS =====

private fun ByteArray.toSeries(): Indexed<Byte> = size j { this[it] }
private fun List<Char>.toSeries(): Indexed<Char> = size j { this[it] }


// ===== CCEK SERVICE HANDLERS =====

fun createBatchHandler(dealService: DealService): HttpHandler = { request ->
    // Delegate to CCEK DealService
    HttpResponse(
        status = HttpStatusCode(200),
        reasonPhrase = HttpReasonPhrase("OK"),
        headers = 1 j { i:Int ->
            when (i) {
                0 -> HttpHeaderName("Content-Type") j HttpHeaderValue("application/json")
                else -> throw IndexOutOfBoundsException()
            }
        },
        body = "{}".encodeToByteArray()
    )
}

fun createRequestFactoryHandler(requestFactoryService: RequestFactoryService): HttpHandler = { request ->
    // Delegate to CCEK RequestFactoryService
    val requestBodySeries = request.body.size j { request.body[it] }
    val responsePayloadSeries = requestFactoryService.process(requestBodySeries)
    val responsePayload = responsePayloadSeries.play.toList().toByteArray()
    HttpResponse(
        status = HttpStatusCode(200),
        reasonPhrase = HttpReasonPhrase("OK"),
        headers = 2 j { i:Int ->
            when (i) {
                0 -> HttpHeaderName("Content-Type") j HttpHeaderValue("application/json; charset=utf-8")
                1 -> HttpHeaderName("Content-Length") j HttpHeaderValue(responsePayload.size.toString())
                else -> throw IndexOutOfBoundsException()
            }
        },
        body = responsePayload
    )
}