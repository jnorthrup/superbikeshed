@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor.ccek

import kotlinx.coroutines.*
import kotlinx.datetime.*
import borg.trikeshed.reactor.*
import borg.trikeshed.lib.*

/**
 * CCEK Series 1: Protocol Stack Composition
 * 
 * Building on Series 0's basic adapters, we now compose them into protocol stacks.
 * This implements the subsumption hierarchy's second layer.
 */

// Protocol stack implementations
class HttpOverTcpStack(
    internal val tcpAdapter: TcpAdapter,
    internal val httpVersion: String = "1.1"
) : ProtocolStack {
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        return when (operation) {
            is HttpRequest -> handleHttpRequest(operation as HttpRequest) as T
            is HttpResponse -> handleHttpResponse(operation as HttpResponse) as T
            else -> tcpAdapter.dispatch(operation)
        }
    }
    
    internal suspend fun handleHttpRequest(request: HttpRequest): HttpResult {
        val timestamp = Clock.System.now()
        
        // Build HTTP request
        val httpData = buildString {
            append("${request.method} ${request.path} HTTP/$httpVersion\r\n")
            request.headers.forEach { (key, value) ->
                append("$key: $value\r\n")
            }
            append("\r\n")
            if (request.body != null) {
                append(request.body)
            }
        }.encodeToByteArray()
        
        // Send via TCP
        val tcpResult = tcpAdapter.dispatch(
            TcpSend(request.host, request.port, httpData)
        )
        
        return HttpResult(
            statusCode = 200, // Parse from response
            headers = emptyMap(),
            body = tcpResult.data,
            timestamp = timestamp
        )
    }
    
    internal suspend fun handleHttpResponse(response: HttpResponse): HttpResult {
        // Transform HTTP response to result
        return HttpResult(
            statusCode = response.statusCode,
            headers = response.headers,
            body = response.body,
            timestamp = Clock.System.now()
        )
    }
    
    companion object : CoroutineContext.Key<HttpOverTcpStack>
}

class WebSocketOverHttpStack(
    internal val httpStack: HttpOverTcpStack,
    internal val subprotocol: String? = null
) : ProtocolStack {
    
    internal val activeConnections = mutableMapOf<String, WebSocketConnection>()
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        return when (operation) {
            is WebSocketConnect -> handleConnect(operation) as T
            is WebSocketSend -> handleSend(operation) as T
            is WebSocketClose -> handleClose(operation) as T
            else -> httpStack.dispatch(operation)
        }
    }
    
    internal suspend fun handleConnect(connect: WebSocketConnect): WebSocketResult {
        val key = generateWebSocketKey()
        val timestamp = Clock.System.now()
        
        // Upgrade request via HTTP
        val upgradeRequest = HttpRequest(
            method = "GET",
            path = connect.path,
            host = connect.host,
            port = connect.port,
            headers = mapOf(
                "Upgrade" to "websocket",
                "Connection" to "Upgrade",
                "Sec-WebSocket-Key" to key,
                "Sec-WebSocket-Version" to "13"
            ) + if (subprotocol != null) {
                mapOf("Sec-WebSocket-Protocol" to subprotocol)
            } else emptyMap(),
            body = null
        )
        
        val httpResult = httpStack.dispatch(upgradeRequest)
        
        if (httpResult.statusCode == 101) {
            val connectionId = "${connect.host}:${connect.port}"
            activeConnections[connectionId] = WebSocketConnection(
                id = connectionId,
                establishedAt = timestamp
            )
        }
        
        return WebSocketResult(
            connected = httpResult.statusCode == 101,
            connectionId = "${connect.host}:${connect.port}",
            timestamp = timestamp
        )
    }
    
    internal suspend fun handleSend(send: WebSocketSend): WebSocketResult {
        val connection = activeConnections[send.connectionId]
            ?: return WebSocketResult(
                connected = false,
                connectionId = send.connectionId,
                timestamp = Clock.System.now()
            )
            
        // Frame the WebSocket message
        val frame = createWebSocketFrame(send.data, send.opcode)
        
        // Send via HTTP's TCP connection
        val tcpResult = httpStack.dispatch(
            TcpSend(
                connection.host,
                connection.port,
                frame
            )
        )
        
        return WebSocketResult(
            connected = true,
            connectionId = send.connectionId,
            timestamp = Clock.System.now(),
            data = tcpResult.data
        )
    }
    
    internal suspend fun handleClose(close: WebSocketClose): WebSocketResult {
        val timestamp = Clock.System.now()
        activeConnections.remove(close.connectionId)
        
        return WebSocketResult(
            connected = false,
            connectionId = close.connectionId,
            timestamp = timestamp
        )
    }
    
    internal fun generateWebSocketKey(): String {
        // Generate 16 random bytes and base64 encode
        return ByteArray(16).apply {
            for (i in indices) {
                this[i] = (0..255).random().toByte()
            }
        }.encodeBase64()
    }
    
    internal fun createWebSocketFrame(data: ByteArray, opcode: Int): ByteArray {
        // Simplified WebSocket framing
        val frame = mutableListOf<Byte>()
        frame.add((0x80 or opcode).toByte()) // FIN + opcode
        
        when {
            data.size < 126 -> frame.add(data.size.toByte())
            data.size < 65536 -> {
                frame.add(126.toByte())
                frame.add((data.size shr 8).toByte())
                frame.add(data.size.toByte())
            }
            else -> {
                frame.add(127.toByte())
                // Add 8 bytes for length
                for (i in 7 downTo 0) {
                    frame.add((data.size shr (i * 8)).toByte())
                }
            }
        }
        
        frame.addAll(data.toList())
        return frame.toByteArray()
    }
    
    companion object : CoroutineContext.Key<WebSocketOverHttpStack>
}

// Protocol-specific operations
data class HttpRequest(
    val method: String,
    val path: String,
    val host: String,
    val port: Int,
    val headers: Map<String, String>,
    val body: ByteArray?
) : ChannelOperation<HttpResult>

data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: ByteArray
) : ChannelOperation<HttpResult>

data class HttpResult(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
    val timestamp: Instant
)

data class WebSocketConnect(
    val host: String,
    val port: Int,
    val path: String
) : ChannelOperation<WebSocketResult>

data class WebSocketSend(
    val connectionId: String,
    val data: ByteArray,
    val opcode: Int = 0x01 // Text frame
) : ChannelOperation<WebSocketResult>

data class WebSocketClose(
    val connectionId: String
) : ChannelOperation<WebSocketResult>

data class WebSocketResult(
    val connected: Boolean,
    val connectionId: String,
    val timestamp: Instant,
    val data: ByteArray? = null
)

data class WebSocketConnection(
    val id: String,
    val establishedAt: Instant,
    val host: String = id.substringBefore(':'),
    val port: Int = id.substringAfter(':').toInt()
)

// Extension to create protocol stacks
fun CoroutineScope.httpOverTcp(
    tcpAdapter: TcpAdapter = TcpAdapter(),
    httpVersion: String = "1.1"
): HttpOverTcpStack = HttpOverTcpStack(tcpAdapter, httpVersion)

fun CoroutineScope.webSocketOverHttp(
    httpStack: HttpOverTcpStack,
    subprotocol: String? = null
): WebSocketOverHttpStack = WebSocketOverHttpStack(httpStack, subprotocol)

// Base64 encoding helper
internal fun ByteArray.encodeBase64(): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val output = StringBuilder()
    
    for (i in indices step 3) {
        val b1 = this[i].toInt() and 0xFF
        val b2 = if (i + 1 < size) this[i + 1].toInt() and 0xFF else 0
        val b3 = if (i + 2 < size) this[i + 2].toInt() and 0xFF else 0
        
        val triple = (b1 shl 16) or (b2 shl 8) or b3
        
        output.append(table[(triple shr 18) and 0x3F])
        output.append(table[(triple shr 12) and 0x3F])
        output.append(if (i + 1 < size) table[(triple shr 6) and 0x3F] else '=')
        output.append(if (i + 2 < size) table[triple and 0x3F] else '=')
    }
    
    return output.toString()
}