@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import borg.trikeshed.lib.*
import borg.trikeshed.channel.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * HTTP protocol channelization - converts HTTP operations to channel operations.
 * This is the SINGLE class that bridges HTTP to our channel architecture.
 */
class HttpChannelization(
    internal val channelProvider: ChannelProvider
) {
    
    /**
     * Execute HTTP request through channelized architecture.
     */
    suspend fun executeRequest(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null
    ): HttpResponse {
        
        // Parse URL to get host/port
        val parsedUrl = parseUrl(url)
        
        // Create connected channel to server
        val channel = channelProvider.createConnectedChannel(
            ChannelConfig(
                type = ChannelType.TCP,
                mode = ChannelMode.READ_WRITE,
                bufferSize = 8192,
                timeout = 30000
            ),
            ChannelAddress.InetAddress(parsedUrl.host, parsedUrl.port)
        )
        
        try {
            // Build HTTP request
            val httpRequest = buildHttpRequest(method, parsedUrl.path, headers, body)
            
            // Send request through channel
            val requestBuffer = ByteBuffer.wrap(httpRequest)
            channel.write(requestBuffer)
            channel.flush()
            
            // Read response through channel
            val responseBuffer = ByteBuffer.allocate(8192)
            val bytesRead = channel.read(responseBuffer)
            responseBuffer.flip()
            
            // Parse HTTP response
            return parseHttpResponse(responseBuffer.array().sliceArray(0 until bytesRead))
            
        } finally {
            channel.close()
        }
    }
    
    /**
     * Create HTTP server using channelized architecture.
     */
    suspend fun createServer(
        port: Int,
        handler: suspend (HttpRequest) -> HttpResponse
    ): HttpServer {
        return HttpServer(channelProvider, port, handler)
    }
    
    internal fun parseUrl(url: String): ParsedUrl {
        val withoutProtocol = url.removePrefix("http://").removePrefix("https://")
        val parts = withoutProtocol.split("/", limit = 2)
        val hostPort = parts[0].split(":")
        val host = hostPort[0]
        val port = if (hostPort.size > 1) hostPort[1].toInt() else 80
        val path = if (parts.size > 1) "/${parts[1]}" else "/"
        
        return ParsedUrl(host, port, path)
    }
    
    internal fun buildHttpRequest(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray?
    ): ByteArray {
        val request = StringBuilder()
        request.append("$method $path HTTP/1.1\r\n")
        
        headers.forEach { (key, value) ->
            request.append("$key: $value\r\n")
        }
        
        if (body != null) {
            request.append("Content-Length: ${body.size}\r\n")
        }
        
        request.append("\r\n")
        
        val headerBytes = request.toString().encodeToByteArray()
        return if (body != null) {
            headerBytes + body
        } else {
            headerBytes
        }
    }
    
    internal fun parseHttpResponse(responseBytes: ByteArray): HttpResponse {
        val responseString = responseBytes.decodeToString()
        val lines = responseString.split("\r\n")
        
        // Parse status line
        val statusLine = lines[0]
        val statusParts = statusLine.split(" ", limit = 3)
        val statusCode = statusParts[1].toInt()
        val statusMessage = if (statusParts.size > 2) statusParts[2] else ""
        
        // Parse headers
        val headers = mutableMapOf<String, String>()
        var bodyStartIndex = 0
        
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) {
                bodyStartIndex = responseString.indexOf("\r\n\r\n") + 4
                break
            }
            
            val colonIndex = line.indexOf(":")
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }
        
        // Extract body
        val body = if (bodyStartIndex > 0 && bodyStartIndex < responseBytes.size) {
            responseBytes.sliceArray(bodyStartIndex until responseBytes.size)
        } else {
            byteArrayOf()
        }
        
        return HttpResponse(statusCode, statusMessage, headers, body)
    }
}

/**
 * Channelized HTTP server.
 */
class HttpServer(
    internal val channelProvider: ChannelProvider,
    internal val port: Int,
    internal val handler: suspend (HttpRequest) -> HttpResponse
) {
    
    internal var serverChannel: ServerChannel? = null
    internal var serverJob: Job? = null
    
    suspend fun start() {
        serverChannel = channelProvider.createServerChannel(
            ChannelConfig(
                type = ChannelType.TCP,
                mode = ChannelMode.READ_WRITE,
                bufferSize = 8192,
                timeout = 30000
            ),
            ChannelAddress.InetAddress("0.0.0.0", port)
        )
        
        serverChannel?.bind()
        
        serverJob = GlobalScope.launch {
            while (isActive) {
                try {
                    val clientChannel = serverChannel?.accept()
                    if (clientChannel != null) {
                        launch {
                            handleClient(clientChannel)
                        }
                    }
                } catch (e: Exception) {
                    // Handle server error
                    break
                }
            }
        }
    }
    
    suspend fun stop() {
        serverJob?.cancel()
        serverChannel?.close()
    }
    
    internal suspend fun handleClient(clientChannel: ConnectedChannel) {
        try {
            // Read HTTP request
            val requestBuffer = ByteBuffer.allocate(8192)
            val bytesRead = clientChannel.read(requestBuffer)
            requestBuffer.flip()
            
            val requestBytes = requestBuffer.array().sliceArray(0 until bytesRead)
            val httpRequest = parseHttpRequest(requestBytes)
            
            // Handle request
            val response = handler(httpRequest)
            
            // Send response
            val responseBytes = buildHttpResponse(response)
            val responseBuffer = ByteBuffer.wrap(responseBytes)
            clientChannel.write(responseBuffer)
            clientChannel.flush()
            
        } catch (e: Exception) {
            // Handle client error
        } finally {
            clientChannel.close()
        }
    }
    
    internal fun parseHttpRequest(requestBytes: ByteArray): HttpRequest {
        val requestString = requestBytes.decodeToString()
        val lines = requestString.split("\r\n")
        
        // Parse request line
        val requestLine = lines[0]
        val requestParts = requestLine.split(" ")
        val method = requestParts[0]
        val path = requestParts[1]
        val version = requestParts[2]
        
        // Parse headers
        val headers = mutableMapOf<String, String>()
        var bodyStartIndex = 0
        
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) {
                bodyStartIndex = requestString.indexOf("\r\n\r\n") + 4
                break
            }
            
            val colonIndex = line.indexOf(":")
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }
        
        // Extract body
        val body = if (bodyStartIndex > 0 && bodyStartIndex < requestBytes.size) {
            requestBytes.sliceArray(bodyStartIndex until requestBytes.size)
        } else {
            byteArrayOf()
        }
        
        return HttpRequest(method, path, headers, body)
    }
    
    internal fun buildHttpResponse(response: HttpResponse): ByteArray {
        val responseBuilder = StringBuilder()
        responseBuilder.append("HTTP/1.1 ${response.statusCode} ${response.statusMessage}\r\n")
        
        response.headers.forEach { (key, value) ->
            responseBuilder.append("$key: $value\r\n")
        }
        
        responseBuilder.append("Content-Length: ${response.body.size}\r\n")
        responseBuilder.append("\r\n")
        
        val headerBytes = responseBuilder.toString().encodeToByteArray()
        return headerBytes + response.body
    }
}

/**
 * Data classes for HTTP.
 */
data class ParsedUrl(val host: String, val port: Int, val path: String)

data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: ByteArray
)

data class HttpResponse(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, String>,
    val body: ByteArray
)

/**
 * Global HTTP channelization instance.
 */
fun createHttpChannelization(channelProvider: ChannelProvider) = 
    HttpChannelization(channelProvider)

/**
 * Extension functions for easy HTTP operations.
 */
suspend fun ChannelProvider.httpGet(url: String): HttpResponse =
    createHttpChannelization(this).executeRequest("GET", url)

suspend fun ChannelProvider.httpPost(url: String, body: ByteArray): HttpResponse =
    createHttpChannelization(this).executeRequest("POST", url, body = body)

suspend fun ChannelProvider.httpServer(port: Int, handler: suspend (HttpRequest) -> HttpResponse): HttpServer =
    createHttpChannelization(this).createServer(port, handler)