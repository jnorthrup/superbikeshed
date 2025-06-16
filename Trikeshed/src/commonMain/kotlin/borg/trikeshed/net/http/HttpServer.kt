@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.net.http

import borg.trikeshed.lib.*
import borg.trikeshed.reactor.*

// RFC 7230 Compliant HTTP/1.1 Server Implementation

@JvmInline value class HttpServerPort(val value: Int)
@JvmInline value class HttpServerHost(val value: String)

typealias HttpHandler = suspend (HttpRequest) -> HttpResponse

data class HttpServerConfig(
    val host: HttpServerHost = HttpServerHost("0.0.0.0"),
    val port: HttpServerPort = HttpServerPort(8080),
    val maxHeaderSize: Int = 8192,
    val maxBodySize: Long = 1024 * 1024,  // 1MB
    val keepAliveTimeout: Long = 5000,     // 5 seconds
    val maxConnections: Int = 1000,
    val enableChunkedTransfer: Boolean = true,
    val enableCompression: Boolean = true
)

class HttpServer(
    private val config: HttpServerConfig,
    private val handler: HttpHandler
) {
    
    private val connectionManager = HttpConnectionManager(config)
    
    suspend fun start() {
        // TODO: Integration with Reactor pattern from trikeshed.reactor
        // This would use the restored reactor system for async I/O
    }
    
    suspend fun handleConnection(input: Series<Byte>): Series<Byte> {
        val inputChars = input.α { it.toInt().toChar() }
        
        // Parse HTTP message using RFC 7230 parser
        val httpMessage = HttpParser.parseHttpMessage(inputChars) ?: run {
            return createBadRequestResponse()
        }
        
        // Validate message format
        if (!isValidHttpMessage(httpMessage)) {
            return createBadRequestResponse()
        }
        
        // Convert to HttpRequest format
        val request = convertToHttpRequest(httpMessage) ?: run {
            return createBadRequestResponse()
        }
        
        // Handle with user-provided handler
        val response = try {
            handler(request)
        } catch (e: Exception) {
            createInternalServerErrorResponse()
        }
        
        // Convert response to RFC 7230 format and serialize
        val responseMessage = response.toHttpMessage()
        val serialized = HttpSerializer.serializeHttpMessage(responseMessage)
        
        return serialized.α { it.code.toByte() }
    }
    
    private fun isValidHttpMessage(message: HttpMessage): Boolean {
        // RFC 7230 validation
        when (val startLine = message.startLine) {
            is HttpRequestLine -> {
                // Validate HTTP version
                if (!isValidHttpVersion(startLine.httpVersion)) return false
                
                // Validate request target (Section 5.3)
                if (!isValidRequestTarget(startLine.requestTarget)) return false
                
                // Validate method
                if (!isValidMethod(startLine.method)) return false
            }
            is HttpStatusLine -> {
                if (!isValidHttpVersion(startLine.httpVersion)) return false
                if (!isValidStatusCode(startLine.statusCode)) return false
            }
        }
        
        // Validate headers (Section 3.2)
        return validateHeaders(message.headerFields)
    }
    
    private fun isValidHttpVersion(version: HttpVersion): Boolean {
        return version.value.matches(Regex("HTTP/\\d+\\.\\d+"))
    }
    
    private fun isValidRequestTarget(target: HttpRequestTarget): Boolean {
        // Basic validation - more comprehensive validation would follow RFC 3986
        return target.value.isNotEmpty() && target.value.length <= 2048
    }
    
    private fun isValidMethod(method: HttpMethod): Boolean {
        return true  // All enum values are valid
    }
    
    private fun isValidStatusCode(code: HttpStatusCode): Boolean {
        return code.value in 100..599
    }
    
    private fun validateHeaders(headers: Series2<HttpFieldName, HttpFieldValue>): Boolean {
        // RFC 7230 Section 3.2 validation
        headers.`▶`.forEach { headerJoin ->
            val fieldName = headerJoin.a.value
            val fieldValue = headerJoin.b.value
            
            // Validate field name is token
            if (!isValidToken(fieldName)) return false
            
            // Validate field value contains only VCHAR/WSP/obs-text
            if (!isValidFieldValue(fieldValue)) return false
        }
        return true
    }
    
    private fun isValidToken(str: String): Boolean {
        return str.isNotEmpty() && str.all { char ->
            char.isLetterOrDigit() || char in "!#\$&'*+-.^_`|~"
        }
    }
    
    private fun isValidFieldValue(str: String): Boolean {
        return str.all { char ->
            char.code in 0x21..0x7E || char == ' ' || char == '\t' || char.code in 0x80..0xFF
        }
    }
    
    private fun convertToHttpRequest(message: HttpMessage): HttpRequest? {
        val startLine = message.startLine as? HttpRequestLine ?: return null
        
        val headers = message.headerFields.α { join ->
            HttpHeaderName(join.a.value) j HttpHeaderValue(join.b.value)
        }
        
        return HttpRequest(
            method = startLine.method,
            path = HttpRequestPath(startLine.requestTarget.value),
            headers = headers,
            body = message.messageBody,
            version = startLine.httpVersion
        )
    }
    
    private fun createBadRequestResponse(): Series<Byte> {
        val response = HttpResponse(
            status = HttpStatusCode(400),
            reasonPhrase = HttpReasonPhrase("Bad Request"),
            headers = createEmptyHeaders(),
            body = "Bad Request".encodeToByteArray().toSeries()
        )
        
        val message = response.toHttpMessage()
        val serialized = HttpSerializer.serializeHttpMessage(message)
        return serialized.α { it.code.toByte() }
    }
    
    private fun createInternalServerErrorResponse(): HttpResponse {
        return HttpResponse(
            status = HttpStatusCode(500),
            reasonPhrase = HttpReasonPhrase("Internal Server Error"),
            headers = createEmptyHeaders(),
            body = "Internal Server Error".encodeToByteArray().toSeries()
        )
    }
    
    private fun createEmptyHeaders(): Series2<HttpHeaderName, HttpHeaderValue> {
        return 0 j { HttpHeaderName("") j HttpHeaderValue("") }
    }
}

// ===== CONNECTION MANAGEMENT (RFC 7230 Section 6) =====

class HttpConnectionManager(private val config: HttpServerConfig) {
    
    private val connections = mutableMapOf<String, HttpConnection>()
    
    data class HttpConnection(
        val id: String,
        val keepAlive: Boolean,
        val lastActivity: Long,
        val requestCount: Int = 0
    )
    
    fun shouldKeepAlive(headers: Series2<HttpFieldName, HttpFieldValue>, version: HttpVersion): Boolean {
        val connectionHeader = headers.`▶`.find { 
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
        headers: Series2<HttpFieldName, HttpFieldValue>
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
    
    fun encodeChunked(data: Series<Byte>): Series<Byte> {
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
        
        return chunks.size j { chunks[it] }
    }
    
    fun decodeChunked(input: Series<Byte>): Series<Byte>? {
        val inputChars = input.α { it.toInt().toChar() }
        val chunkedBody = HttpParser.parseChunkedBody(inputChars) ?: return null
        
        val allData = mutableListOf<Byte>()
        chunkedBody.chunks.`▶`.forEach { chunk ->
            allData.addAll(chunk.data.`▶`)
        }
        
        return allData.size j { allData[it] }
    }
}

// ===== UTILITY EXTENSIONS =====

private fun ByteArray.toSeries(): Series<Byte> = size j { this[it] }

private fun String.encodeToByteArray(): ByteArray = this.toByteArray(Charsets.UTF_8)