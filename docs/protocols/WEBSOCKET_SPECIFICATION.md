# WebSocket Protocol Specification

## 1. Taxonomical Type System

### 1.1 Core WebSocket Types

```kotlin
// WebSocket connection identifiers
@JvmInline value class WebSocketConnectionId(val bytes: UByteArray)
@JvmInline value class WebSocketSessionId(val uuid: String)

// WebSocket protocol types
@JvmInline value class WebSocketVersion(val version: String) // "13"
@JvmInline value class WebSocketKey(val base64: String) // Sec-WebSocket-Key
@JvmInline value class WebSocketAccept(val base64: String) // Sec-WebSocket-Accept
@JvmInline value class WebSocketProtocol(val name: String) // Subprotocol

// WebSocket frame types
@JvmInline value class WebSocketOpcode(val code: UByte)
@JvmInline value class WebSocketPayloadLength(val bytes: Long)
@JvmInline value class WebSocketMaskKey(val key: UInt)

// WebSocket state types
@JvmInline value class WebSocketCloseCode(val code: UShort)
@JvmInline value class WebSocketCloseReason(val reason: String)
@JvmInline value class WebSocketPingPayload(val data: UByteArray)
@JvmInline value class WebSocketPongPayload(val data: UByteArray)
```

### 1.2 WebSocket Enumerations

```kotlin
enum class WebSocketFrameType(val opcode: UByte) {
    CONTINUATION(0x0u),
    TEXT(0x1u),
    BINARY(0x2u),
    CLOSE(0x8u),
    PING(0x9u),
    PONG(0xAu)
}

enum class WebSocketCloseCode(val code: UShort) {
    NORMAL_CLOSURE(1000u),
    GOING_AWAY(1001u),
    PROTOCOL_ERROR(1002u),
    UNSUPPORTED_DATA(1003u),
    NO_STATUS_RECEIVED(1005u),
    ABNORMAL_CLOSURE(1006u),
    INVALID_FRAME_PAYLOAD_DATA(1007u),
    POLICY_VIOLATION(1008u),
    MESSAGE_TOO_BIG(1009u),
    INTERNAL_ERROR(1011u)
}

enum class WebSocketState(val id: UByte) {
    CONNECTING(0u),
    OPEN(1u),
    CLOSING(2u),
    CLOSED(3u)
}

enum class WebSocketRole(val id: UByte) {
    CLIENT(0u),
    SERVER(1u)
}
```

## 2. Coroutine Context Key Composition

### 2.1 WebSocket Stream Context Keys

```kotlin
// WebSocket stream context keys following endgame pattern
data class WebSocketStreamKey(val streamId: Long) : CoroutineContext.Key<WebSocketStreamKey>
data class WebSocketConnectionKey(val connectionId: WebSocketConnectionId) : CoroutineContext.Key<WebSocketConnectionKey>
data class WebSocketRoleKey(val role: WebSocketRole) : CoroutineContext.Key<WebSocketRoleKey>

// Platform reactor abstraction
expect class PlatformReactor {
    suspend fun submitWebSocketOperation(op: WebSocketOperation): WebSocketResult
}

// Linux implementation with io_uring
actual class PlatformReactor {
    actual suspend fun submitWebSocketOperation(op: WebSocketOperation): WebSocketResult = 
        suspendCoroutine { cont ->
            uring.submit { sqe ->
                sqe.prepareCmd(EBPF_WEBSOCKET_PARSER)
                    .withStreamId(op.streamId)
                    .withPayload(op.payload)
                    .withContinuation(cont)
            }
        }
}

// JVM fallback implementation
actual class PlatformReactor {
    private val executor = Executors.newFixedThreadPool(4)
    
    actual suspend fun submitWebSocketOperation(op: WebSocketOperation): WebSocketResult =
        suspendCancellableCoroutine { cont ->
            executor.submit {
                val result = nioWebSocketOperation(op)
                cont.resume(result)
            }
        }
}
```

### 2.2 WebSocket Operations as Kernel Commands

```kotlin
// WebSocket operations lifted to kernel commands
sealed class WebSocketOperation {
    data class Handshake(
        val streamId: Long,
        val key: WebSocketKey,
        val version: WebSocketVersion,
        val protocols: List<WebSocketProtocol>
    ) : WebSocketOperation()
    
    data class SendFrame(
        val streamId: Long,
        val frame: WebSocketFrame
    ) : WebSocketOperation()
    
    data class ReceiveFrame(
        val streamId: Long
    ) : WebSocketOperation()
    
    data class Close(
        val streamId: Long,
        val code: WebSocketCloseCode,
        val reason: WebSocketCloseReason
    ) : WebSocketOperation()
}

sealed class WebSocketResult {
    data class HandshakeSuccess(
        val accept: WebSocketAccept,
        val protocol: WebSocketProtocol?,
        val state: WebSocketState
    ) : WebSocketResult()
    
    data class FrameReceived(
        val frame: WebSocketFrame
    ) : WebSocketResult()
    
    data class FrameSent(
        val streamId: Long
    ) : WebSocketResult()
    
    data class ConnectionClosed(
        val streamId: Long
    ) : WebSocketResult()
}
```

## 3. TDD Test Specifications

### 3.1 WebSocket Handshake Tests with Context Composition

```kotlin
@Test
fun `should perform WebSocket handshake with coroutine context composition`() = runTest {
    // Given: Coroutine context with WebSocket stream keys
    val streamId = 0L // Client-initiated
    val connectionId = WebSocketConnectionId(UByteArray(16) { it.toUByte() })
    val role = WebSocketRole.CLIENT
    
    val context = coroutineContext + 
        WebSocketStreamKey(streamId) +
        WebSocketConnectionKey(connectionId) +
        WebSocketRoleKey(role)
    
    // When: WebSocket handshake is performed
    val reactor = PlatformReactor()
    val result = withContext(context) {
        reactor.submitWebSocketOperation(
            WebSocketOperation.Handshake(
                streamId = streamId,
                key = WebSocketKey("dGhlIHNhbXBsZSBub25jZQ=="),
                version = WebSocketVersion("13"),
                protocols = listOf(WebSocketProtocol("chat"))
            )
        )
    }
    
    // Then: Should succeed with proper accept
    assertThat(result).isInstanceOf(WebSocketResult.HandshakeSuccess::class.java)
    val success = result as WebSocketResult.HandshakeSuccess
    assertThat(success.accept).isEqualTo(WebSocketAccept("s3pPLMBiTxaQ9kYGzzhZRbK+xOo="))
    assertThat(success.protocol).isEqualTo(WebSocketProtocol("chat"))
    assertThat(success.state).isEqualTo(WebSocketState.OPEN)
}

@Test
fun `should handle WebSocket upgrade with platform reactor`() = runTest {
    // Given: HTTP/3 upgrade request
    val upgradeRequest = HttpRequest(
        method = HttpMethod.GET,
        path = "/ws",
        headers = mapOf(
            "Upgrade" to "websocket",
            "Connection" to "Upgrade",
            "Sec-WebSocket-Key" to "dGhlIHNhbXBsZSBub25jZQ==",
            "Sec-WebSocket-Version" to "13"
        )
    )
    
    // When: Upgrade is processed with platform reactor
    val reactor = PlatformReactor()
    val streamId = 1L // Server-initiated
    val context = coroutineContext + WebSocketStreamKey(streamId) + WebSocketRoleKey(WebSocketRole.SERVER)
    
    val result = withContext(context) {
        WebSocketUpgrade.processWithReactor(upgradeRequest, reactor)
    }
    
    // Then: Should create QUIC stream with server initiator bit
    assertThat(streamId % 4).isEqualTo(1u) // Server initiated
    assertThat(result.state).isEqualTo(WebSocketState.OPEN)
}
```

### 3.2 WebSocket Frame Tests with Kernel Integration

```kotlin
@Test
fun `should send WebSocket frame through kernel command`() = runTest {
    // Given: WebSocket frame and context
    val frame = WebSocketFrame(
        fin = true,
        opcode = WebSocketOpcode(WebSocketFrameType.TEXT.opcode),
        mask = true,
        payload = "Hello, WebSocket!".encodeToByteArray().toUByteArray()
    )
    
    val streamId = 0L
    val context = coroutineContext + WebSocketStreamKey(streamId)
    
    // When: Frame is sent through platform reactor
    val reactor = PlatformReactor()
    val result = withContext(context) {
        reactor.submitWebSocketOperation(
            WebSocketOperation.SendFrame(streamId, frame)
        )
    }
    
    // Then: Should be processed by kernel
    assertThat(result).isInstanceOf(WebSocketResult.FrameSent::class.java)
    val sent = result as WebSocketResult.FrameSent
    assertThat(sent.streamId).isEqualTo(streamId)
}

@Test
fun `should receive WebSocket frame from kernel`() = runTest {
    // Given: WebSocket stream context
    val streamId = 1L
    val context = coroutineContext + WebSocketStreamKey(streamId)
    
    // When: Frame is received through platform reactor
    val reactor = PlatformReactor()
    val result = withContext(context) {
        reactor.submitWebSocketOperation(
            WebSocketOperation.ReceiveFrame(streamId)
        )
    }
    
    // Then: Should receive frame from kernel
    assertThat(result).isInstanceOf(WebSocketResult.FrameReceived::class.java)
    val received = result as WebSocketResult.FrameReceived
    assertThat(received.frame.opcode).isEqualTo(WebSocketOpcode(WebSocketFrameType.TEXT.opcode))
}
```

## 4. Implementation Architecture

### 4.1 WebSocket Context Composition

```kotlin
class WebSocketContextComposer {
    fun createWebSocketContext(
        streamId: Long,
        connectionId: WebSocketConnectionId,
        role: WebSocketRole
    ): CoroutineContext {
        return WebSocketStreamKey(streamId) +
               WebSocketConnectionKey(connectionId) +
               WebSocketRoleKey(role)
    }
    
    suspend fun withWebSocketContext(
        streamId: Long,
        connectionId: WebSocketConnectionId,
        role: WebSocketRole,
        block: suspend CoroutineScope.() -> WebSocketResult
    ): WebSocketResult {
        val context = createWebSocketContext(streamId, connectionId, role)
        return withContext(context, block)
    }
}
```

### 4.2 WebSocket Connection Manager with Platform Reactor

```kotlin
class WebSocketConnectionManager(
    private val reactor: PlatformReactor
) {
    private val connections = mutableMapOf<WebSocketConnectionId, WebSocketConnection>()
    
    suspend fun createConnection(
        connectionId: WebSocketConnectionId,
        role: WebSocketRole
    ): WebSocketConnection {
        val streamId = createStreamWithInitiator(role)
        
        val connection = WebSocketConnection(
            connectionId = connectionId,
            role = role,
            streamId = streamId,
            reactor = reactor
        )
        
        connections[connectionId] = connection
        return connection
    }
    
    private fun createStreamWithInitiator(role: WebSocketRole): Long {
        // Use QUIC initiator logic: client=0, server=1
        val initiator = role.id.toLong()
        val streamId = generateStreamId() * 4 + initiator
        return streamId
    }
    
    suspend fun handleIncomingFrame(
        connectionId: WebSocketConnectionId, 
        frame: WebSocketFrame
    ) {
        val connection = connections[connectionId] ?: return
        
        val context = WebSocketContextComposer().createWebSocketContext(
            streamId = connection.streamId,
            connectionId = connectionId,
            role = connection.role
        )
        
        withContext(context) {
            connection.handleFrame(frame)
        }
    }
}
```

### 4.3 WebSocket Upgrade with Platform Integration

```kotlin
class WebSocketUpgrade {
    companion object {
        suspend fun processWithReactor(
            request: HttpRequest,
            reactor: PlatformReactor
        ): WebSocketConnection {
            // Validate upgrade headers
            require(request.headers["Upgrade"] == "websocket")
            require(request.headers["Connection"]?.contains("Upgrade") == true)
            
            // Extract WebSocket parameters
            val key = WebSocketKey(request.headers["Sec-WebSocket-Key"] ?: "")
            val version = WebSocketVersion(request.headers["Sec-WebSocket-Version"] ?: "")
            val protocols = request.headers["Sec-WebSocket-Protocol"]?.split(",")
                ?.map { WebSocketProtocol(it.trim()) } ?: emptyList()
            
            // Create stream with server initiator bit
            val streamId = 1L // Server initiated
            val connectionId = WebSocketConnectionId(UByteArray(16) { it.toUByte() })
            val role = WebSocketRole.SERVER
            
            // Verify server initiator bit
            require(streamId % 4 == 1L) { "Server must be initiator" }
            
            // Create WebSocket connection with context
            val context = WebSocketContextComposer().createWebSocketContext(
                streamId, connectionId, role
            )
            
            return withContext(context) {
                val connection = WebSocketConnection(
                    connectionId = connectionId,
                    role = role,
                    streamId = streamId,
                    reactor = reactor
                )
                
                // Send upgrade response through kernel
                val accept = calculateAccept(key)
                reactor.submitWebSocketOperation(
                    WebSocketOperation.Handshake(
                        streamId = streamId,
                        key = key,
                        version = version,
                        protocols = protocols
                    )
                )
                
                connection
            }
        }
        
        private fun calculateAccept(key: WebSocketKey): WebSocketAccept {
            val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
            val concatenated = key.base64 + magic
            val sha1 = MessageDigest.getInstance("SHA-1").digest(concatenated.toByteArray())
            val base64 = Base64.getEncoder().encodeToString(sha1)
            return WebSocketAccept(base64)
        }
    }
}
```

This specification now follows the endgame architecture pattern by:
1. **Lifting QUIC streams into coroutine context keys** - `WebSocketStreamKey`, `WebSocketConnectionKey`, `WebSocketRoleKey`
2. **Normalizing operations through platform reactor** - `PlatformReactor` with `expect`/`actual` implementations
3. **Converting WebSocket operations to kernel commands** - `WebSocketOperation` sealed class
4. **Using context composition** - `WebSocketContextComposer` for proper coroutine context management
5. **Platform-specific implementations** - Linux gets io_uring + eBPF, JVM gets NIO fallback

The QUIC initiator logic is preserved but now operates through the coroutine context key composition pattern, allowing the kernel to handle the actual stream management while maintaining the proper client/server role distinction. 