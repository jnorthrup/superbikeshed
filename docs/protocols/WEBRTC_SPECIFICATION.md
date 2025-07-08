# WebRTC Protocol Specification

## 1. Taxonomical Type System

### 1.1 Core WebRTC Types

```kotlin
// WebRTC connection identifiers
@JvmInline value class WebRtcConnectionId(val bytes: UByteArray)
@JvmInline value class WebRtcSessionId(val uuid: String)
@JvmInline value class WebRtcPeerId(val bytes: UByteArray)

// WebRTC signaling types
@JvmInline value class WebRtcOffer(val sdp: String)
@JvmInline value class WebRtcAnswer(val sdp: String)
@JvmInline value class WebRtcIceCandidate(val candidate: String)
@JvmInline value class WebRtcIceUfrag(val ufrag: String)
@JvmInline value class WebRtcIcePwd(val pwd: String)

// WebRTC media types
@JvmInline value class WebRtcMediaStreamId(val id: String)
@JvmInline value class WebRtcTrackId(val id: String)
@JvmInline value class WebRtcSsrc(val ssrc: UInt)
@JvmInline value class WebRtcPayloadType(val type: UByte)

// WebRTC transport types
@JvmInline value class WebRtcDtlsFingerprint(val fingerprint: String)
@JvmInline value class WebRtcDtlsRole(val role: String) // "auto", "client", "server"
@JvmInline value class WebRtcSrtpProfile(val profile: String) // "SRTP_AES128_CM_SHA1_80"
```

### 1.2 WebRTC Enumerations

```kotlin
enum class WebRtcSignalingState(val id: UByte) {
    STABLE(0u),
    HAVE_LOCAL_OFFER(1u),
    HAVE_REMOTE_OFFER(2u),
    HAVE_LOCAL_PRANSWER(3u),
    HAVE_REMOTE_PRANSWER(4u),
    CLOSED(5u)
}

enum class WebRtcIceConnectionState(val id: UByte) {
    NEW(0u),
    CHECKING(1u),
    CONNECTED(2u),
    COMPLETED(3u),
    FAILED(4u),
    DISCONNECTED(5u),
    CLOSED(6u)
}

enum class WebRtcIceGatheringState(val id: UByte) {
    NEW(0u),
    GATHERING(1u),
    COMPLETE(2u)
}

enum class WebRtcConnectionState(val id: UByte) {
    NEW(0u),
    CONNECTING(1u),
    CONNECTED(2u),
    DISCONNECTED(3u),
    FAILED(4u),
    CLOSED(5u)
}

enum class WebRtcRole(val id: UByte) {
    OFFERER(0u),
    ANSWERER(1u)
}

enum class WebRtcMediaType(val id: UByte) {
    AUDIO(0u),
    VIDEO(1u),
    DATA(2u)
}

enum class WebRtcDirection(val id: UByte) {
    SENDONLY(0u),
    RECVONLY(1u),
    SENDRECV(2u),
    INACTIVE(3u)
}
```

## 2. Coroutine Context Key Composition

### 2.1 WebRTC Stream Context Keys

```kotlin
// WebRTC stream context keys following endgame pattern
data class WebRtcSignalingStreamKey(val streamId: Long) : CoroutineContext.Key<WebRtcSignalingStreamKey>
data class WebRtcMediaStreamKey(val streamId: Long, val mediaType: WebRtcMediaType) : CoroutineContext.Key<WebRtcMediaStreamKey>
data class WebRtcConnectionKey(val connectionId: WebRtcConnectionId) : CoroutineContext.Key<WebRtcConnectionKey>
data class WebRtcRoleKey(val role: WebRtcRole) : CoroutineContext.Key<WebRtcRoleKey>
data class WebRtcPeerKey(val peerId: WebRtcPeerId) : CoroutineContext.Key<WebRtcPeerKey>

// Platform reactor abstraction for WebRTC
expect class PlatformReactor {
    suspend fun submitWebRtcOperation(op: WebRtcOperation): WebRtcResult
}

// Linux implementation with io_uring
actual class PlatformReactor {
    actual suspend fun submitWebRtcOperation(op: WebRtcOperation): WebRtcResult = 
        suspendCoroutine { cont ->
            uring.submit { sqe ->
                sqe.prepareCmd(EBPF_WEBRTC_PARSER)
                    .withStreamId(op.streamId)
                    .withMediaType(op.mediaType)
                    .withPayload(op.payload)
                    .withContinuation(cont)
            }
        }
}

// JVM fallback implementation
actual class PlatformReactor {
    private val executor = Executors.newFixedThreadPool(4)
    
    actual suspend fun submitWebRtcOperation(op: WebRtcOperation): WebRtcResult =
        suspendCancellableCoroutine { cont ->
            executor.submit {
                val result = nioWebRtcOperation(op)
                cont.resume(result)
            }
        }
}
```

### 2.2 WebRTC Operations as Kernel Commands

```kotlin
// WebRTC operations lifted to kernel commands
sealed class WebRtcOperation {
    data class CreateOffer(
        val streamId: Long,
        val mediaConstraints: WebRtcMediaConstraints
    ) : WebRtcOperation()
    
    data class CreateAnswer(
        val streamId: Long,
        val offer: WebRtcOffer
    ) : WebRtcOperation()
    
    data class SendSignalingMessage(
        val streamId: Long,
        val message: WebRtcSignalingMessage
    ) : WebRtcOperation()
    
    data class SendRtpPacket(
        val streamId: Long,
        val mediaType: WebRtcMediaType,
        val packet: RtpPacket
    ) : WebRtcOperation()
    
    data class SendRtcpPacket(
        val streamId: Long,
        val packet: RtcpPacket
    ) : WebRtcOperation()
    
    data class IceGathering(
        val streamId: Long,
        val candidates: List<WebRtcIceCandidate>
    ) : WebRtcOperation()
    
    data class DtlsHandshake(
        val streamId: Long,
        val role: WebRtcDtlsRole
    ) : WebRtcOperation()
}

sealed class WebRtcResult {
    data class OfferCreated(
        val offer: WebRtcOffer,
        val signalingState: WebRtcSignalingState
    ) : WebRtcResult()
    
    data class AnswerCreated(
        val answer: WebRtcAnswer,
        val signalingState: WebRtcSignalingState
    ) : WebRtcResult()
    
    data class SignalingMessageReceived(
        val message: WebRtcSignalingMessage
    ) : WebRtcResult()
    
    data class RtpPacketReceived(
        val packet: RtpPacket,
        val mediaType: WebRtcMediaType
    ) : WebRtcResult()
    
    data class RtcpPacketReceived(
        val packet: RtcpPacket
    ) : WebRtcResult()
    
    data class IceCandidatesGathered(
        val candidates: List<WebRtcIceCandidate>
    ) : WebRtcResult()
    
    data class DtlsConnected(
        val streamId: Long
    ) : WebRtcResult()
}
```

## 3. TDD Test Specifications

### 3.1 WebRTC Signaling Tests with Context Composition

```kotlin
@Test
fun `should create WebRTC offer with coroutine context composition`() = runTest {
    // Given: Coroutine context with WebRTC stream keys
    val signalingStreamId = 0L // Client-initiated
    val connectionId = WebRtcConnectionId(UByteArray(16) { it.toUByte() })
    val role = WebRtcRole.OFFERER
    val peerId = WebRtcPeerId(UByteArray(8) { it.toUByte() })
    
    val context = coroutineContext + 
        WebRtcSignalingStreamKey(signalingStreamId) +
        WebRtcConnectionKey(connectionId) +
        WebRtcRoleKey(role) +
        WebRtcPeerKey(peerId)
    
    // When: WebRTC offer is created
    val reactor = PlatformReactor()
    val result = withContext(context) {
        reactor.submitWebRtcOperation(
            WebRtcOperation.CreateOffer(
                streamId = signalingStreamId,
                mediaConstraints = WebRtcMediaConstraints(
                    audio = true,
                    video = true
                )
            )
        )
    }
    
    // Then: Should succeed with proper offer
    assertThat(result).isInstanceOf(WebRtcResult.OfferCreated::class.java)
    val success = result as WebRtcResult.OfferCreated
    assertThat(success.offer.sdp).contains("m=audio")
    assertThat(success.offer.sdp).contains("m=video")
    assertThat(success.signalingState).isEqualTo(WebRtcSignalingState.HAVE_LOCAL_OFFER)
    assertThat(signalingStreamId % 4).isEqualTo(0u) // Client initiated
}

@Test
fun `should handle WebRTC answer with platform reactor`() = runTest {
    // Given: WebRTC offer received
    val offer = WebRtcOffer("""
        v=0
        o=- 1234567890 2 IN IP4 127.0.0.1
        s=-
        t=0 0
        m=audio 9 UDP/TLS/RTP/SAVPF 111
        c=IN IP4 0.0.0.0
        a=mid:audio
        a=sendrecv
        a=rtpmap:111 opus/48000/2
    """.trimIndent())
    
    // When: Answer is created with platform reactor
    val reactor = PlatformReactor()
    val signalingStreamId = 1L // Server-initiated
    val context = coroutineContext + 
        WebRtcSignalingStreamKey(signalingStreamId) + 
        WebRtcRoleKey(WebRtcRole.ANSWERER)
    
    val result = withContext(context) {
        reactor.submitWebRtcOperation(
            WebRtcOperation.CreateAnswer(
                streamId = signalingStreamId,
                offer = offer
            )
        )
    }
    
    // Then: Should use QUIC stream with server initiator bit
    assertThat(signalingStreamId % 4).isEqualTo(1u) // Server initiated
    assertThat(result).isInstanceOf(WebRtcResult.AnswerCreated::class.java)
    val answer = result as WebRtcResult.AnswerCreated
    assertThat(answer.answer.sdp).contains("m=audio")
    assertThat(answer.signalingState).isEqualTo(WebRtcSignalingState.STABLE)
}
```

### 3.2 WebRTC Media Tests with Kernel Integration

```kotlin
@Test
fun `should transmit RTP packets through kernel command`() = runTest {
    // Given: WebRTC media stream context
    val mediaStreamId = 2L // Client-initiated unidirectional
    val mediaType = WebRtcMediaType.AUDIO
    val context = coroutineContext + 
        WebRtcMediaStreamKey(mediaStreamId, mediaType)
    
    val rtpPacket = RtpPacket(
        ssrc = WebRtcSsrc(12345u),
        payloadType = WebRtcPayloadType(111u),
        sequenceNumber = 1u,
        timestamp = 48000u,
        payload = UByteArray(160) { it.toUByte() }
    )
    
    // When: RTP packet is sent through platform reactor
    val reactor = PlatformReactor()
    val result = withContext(context) {
        reactor.submitWebRtcOperation(
            WebRtcOperation.SendRtpPacket(
                streamId = mediaStreamId,
                mediaType = mediaType,
                packet = rtpPacket
            )
        )
    }
    
    // Then: Should be processed by kernel
    assertThat(mediaStreamId % 4).isEqualTo(0u) // Client initiated
    assertThat(mediaStreamId % 4 / 2).isEqualTo(0u) // Unidirectional
    assertThat(result).isInstanceOf(WebRtcResult.RtpPacketReceived::class.java)
}

@Test
fun `should handle RTCP feedback through kernel`() = runTest {
    // Given: WebRTC RTCP context
    val rtcpStreamId = 3L // Client-initiated unidirectional
    val context = coroutineContext + WebRtcMediaStreamKey(rtcpStreamId, WebRtcMediaType.DATA)
    
    val rtcpPacket = RtcpPacket(
        type = RtcpPacketType.RECEIVER_REPORT,
        ssrc = WebRtcSsrc(12345u),
        reports = listOf(
            RtcpReceiverReport(
                ssrc = WebRtcSsrc(67890u),
                fractionLost = 0u,
                cumulativeLost = 0u,
                highestSequence = 1000u,
                jitter = 0u,
                lastSr = 0u,
                delaySinceLastSr = 0u
            )
        )
    )
    
    // When: RTCP packet is sent through platform reactor
    val reactor = PlatformReactor()
    val result = withContext(context) {
        reactor.submitWebRtcOperation(
            WebRtcOperation.SendRtcpPacket(
                streamId = rtcpStreamId,
                packet = rtcpPacket
            )
        )
    }
    
    // Then: Should use separate QUIC stream for RTCP
    assertThat(rtcpStreamId % 4).isEqualTo(0u) // Client initiated
    assertThat(rtcpStreamId % 4 / 2).isEqualTo(0u) // Unidirectional
    assertThat(result).isInstanceOf(WebRtcResult.RtcpPacketReceived::class.java)
}
```

### 3.3 WebRTC ICE Tests with Context Composition

```kotlin
@Test
fun `should gather ICE candidates using coroutine context`() = runTest {
    // Given: WebRTC ICE gathering context
    val signalingStreamId = 0L
    val context = coroutineContext + WebRtcSignalingStreamKey(signalingStreamId)
    
    // When: ICE gathering starts through platform reactor
    val reactor = PlatformReactor()
    val result = withContext(context) {
        reactor.submitWebRtcOperation(
            WebRtcOperation.IceGathering(
                streamId = signalingStreamId,
                candidates = listOf(
                    WebRtcIceCandidate("candidate:1 1 UDP 2122252543 192.168.1.1 12345 typ host")
                )
            )
        )
    }
    
    // Then: Should gather candidates through kernel
    assertThat(result).isInstanceOf(WebRtcResult.IceCandidatesGathered::class.java)
    val gathered = result as WebRtcResult.IceCandidatesGathered
    assertThat(gathered.candidates).isNotEmpty()
}

@Test
fun `should establish DTLS connection through kernel`() = runTest {
    // Given: WebRTC DTLS context
    val dtlsStreamId = 4L // Bidirectional for DTLS
    val context = coroutineContext + WebRtcSignalingStreamKey(dtlsStreamId)
    
    // When: DTLS handshake is performed through platform reactor
    val reactor = PlatformReactor()
    val result = withContext(context) {
        reactor.submitWebRtcOperation(
            WebRtcOperation.DtlsHandshake(
                streamId = dtlsStreamId,
                role = WebRtcDtlsRole.AUTO
            )
        )
    }
    
    // Then: Should establish secure connection through kernel
    assertThat(result).isInstanceOf(WebRtcResult.DtlsConnected::class.java)
    val connected = result as WebRtcResult.DtlsConnected
    assertThat(connected.streamId).isEqualTo(dtlsStreamId)
}
```

## 4. Implementation Architecture

### 4.1 WebRTC Context Composition

```kotlin
class WebRtcContextComposer {
    fun createSignalingContext(
        streamId: Long,
        connectionId: WebRtcConnectionId,
        role: WebRtcRole,
        peerId: WebRtcPeerId
    ): CoroutineContext {
        return WebRtcSignalingStreamKey(streamId) +
               WebRtcConnectionKey(connectionId) +
               WebRtcRoleKey(role) +
               WebRtcPeerKey(peerId)
    }
    
    fun createMediaContext(
        streamId: Long,
        mediaType: WebRtcMediaType,
        connectionId: WebRtcConnectionId
    ): CoroutineContext {
        return WebRtcMediaStreamKey(streamId, mediaType) +
               WebRtcConnectionKey(connectionId)
    }
    
    suspend fun withSignalingContext(
        streamId: Long,
        connectionId: WebRtcConnectionId,
        role: WebRtcRole,
        peerId: WebRtcPeerId,
        block: suspend CoroutineScope.() -> WebRtcResult
    ): WebRtcResult {
        val context = createSignalingContext(streamId, connectionId, role, peerId)
        return withContext(context, block)
    }
    
    suspend fun withMediaContext(
        streamId: Long,
        mediaType: WebRtcMediaType,
        connectionId: WebRtcConnectionId,
        block: suspend CoroutineScope.() -> WebRtcResult
    ): WebRtcResult {
        val context = createMediaContext(streamId, mediaType, connectionId)
        return withContext(context, block)
    }
}
```

### 4.2 WebRTC Peer Connection Manager with Platform Reactor

```kotlin
class WebRtcPeerConnectionManager(
    private val reactor: PlatformReactor
) {
    private val connections = mutableMapOf<WebRtcConnectionId, WebRtcPeerConnection>()
    
    suspend fun createConnection(
        connectionId: WebRtcConnectionId,
        role: WebRtcRole,
        peerId: WebRtcPeerId
    ): WebRtcPeerConnection {
        val signalingStreamId = createSignalingStreamWithInitiator(role)
        
        val connection = WebRtcPeerConnection(
            connectionId = connectionId,
            role = role,
            peerId = peerId,
            signalingStreamId = signalingStreamId,
            reactor = reactor
        )
        
        connections[connectionId] = connection
        return connection
    }
    
    private fun createSignalingStreamWithInitiator(role: WebRtcRole): Long {
        // Use QUIC initiator logic: offerer=0, answerer=1
        val initiator = role.id.toLong()
        val streamId = generateStreamId() * 4 + initiator
        return streamId
    }
    
    private fun createMediaStreamWithInitiator(role: WebRtcRole, mediaType: WebRtcMediaType): Long {
        // Media streams are unidirectional: initiator bit + direction bit
        val initiator = role.id.toLong()
        val direction = 0u // Unidirectional
        val streamId = generateStreamId() * 4 + direction * 2 + initiator
        return streamId
    }
    
    suspend fun handleSignalingMessage(
        connectionId: WebRtcConnectionId, 
        message: WebRtcSignalingMessage
    ) {
        val connection = connections[connectionId] ?: return
        
        val context = WebRtcContextComposer().createSignalingContext(
            streamId = connection.signalingStreamId,
            connectionId = connectionId,
            role = connection.role,
            peerId = connection.peerId
        )
        
        withContext(context) {
            connection.handleSignalingMessage(message)
        }
    }
    
    suspend fun handleRtpPacket(
        connectionId: WebRtcConnectionId,
        streamId: Long,
        mediaType: WebRtcMediaType,
        packet: RtpPacket
    ) {
        val connection = connections[connectionId] ?: return
        
        val context = WebRtcContextComposer().createMediaContext(
            streamId = streamId,
            mediaType = mediaType,
            connectionId = connectionId
        )
        
        withContext(context) {
            connection.handleRtpPacket(streamId, mediaType, packet)
        }
    }
}
```

### 4.3 WebRTC Signaling Bridge with Platform Integration

```kotlin
class WebRtcSignalingBridge(
    private val websocket: WebSocketConnection,
    private val peerConnection: WebRtcPeerConnection,
    private val reactor: PlatformReactor
) {
    suspend fun sendOffer(offer: WebRtcOffer) {
        val message = WebRtcSignalingMessage.Offer(
            offer = offer,
            iceCandidates = peerConnection.getLocalIceCandidates()
        )
        
        val context = WebRtcContextComposer().createSignalingContext(
            streamId = peerConnection.signalingStreamId,
            connectionId = peerConnection.connectionId,
            role = peerConnection.role,
            peerId = peerConnection.peerId
        )
        
        withContext(context) {
            val encoded = WebRtcSignalingEncoder.encode(message)
            websocket.send(WebSocketMessage.Text(encoded))
            
            // Also send through kernel for processing
            reactor.submitWebRtcOperation(
                WebRtcOperation.SendSignalingMessage(
                    streamId = peerConnection.signalingStreamId,
                    message = message
                )
            )
        }
    }
    
    suspend fun sendAnswer(answer: WebRtcAnswer) {
        val message = WebRtcSignalingMessage.Answer(
            answer = answer,
            iceCandidates = peerConnection.getLocalIceCandidates()
        )
        
        val context = WebRtcContextComposer().createSignalingContext(
            streamId = peerConnection.signalingStreamId,
            connectionId = peerConnection.connectionId,
            role = peerConnection.role,
            peerId = peerConnection.peerId
        )
        
        withContext(context) {
            val encoded = WebRtcSignalingEncoder.encode(message)
            websocket.send(WebSocketMessage.Text(encoded))
            
            // Also send through kernel for processing
            reactor.submitWebRtcOperation(
                WebRtcOperation.SendSignalingMessage(
                    streamId = peerConnection.signalingStreamId,
                    message = message
                )
            )
        }
    }
    
    suspend fun handleSignalingMessage(message: WebSocketMessage): WebRtcSignalingMessage? {
        if (message !is WebSocketMessage.Text) return null
        
        val decoded = WebRtcSignalingDecoder.decode(message.text)
        
        // Process through kernel
        val context = WebRtcContextComposer().createSignalingContext(
            streamId = peerConnection.signalingStreamId,
            connectionId = peerConnection.connectionId,
            role = peerConnection.role,
            peerId = peerConnection.peerId
        )
        
        withContext(context) {
            reactor.submitWebRtcOperation(
                WebRtcOperation.SendSignalingMessage(
                    streamId = peerConnection.signalingStreamId,
                    message = decoded
                )
            )
        }
        
        return decoded
    }
}
```

This specification now follows the endgame architecture pattern by:
1. **Lifting QUIC streams into coroutine context keys** - `WebRtcSignalingStreamKey`, `WebRtcMediaStreamKey`, `WebRtcConnectionKey`, `WebRtcRoleKey`, `WebRtcPeerKey`
2. **Normalizing operations through platform reactor** - `PlatformReactor` with `expect`/`actual` implementations
3. **Converting WebRTC operations to kernel commands** - `WebRtcOperation` sealed class
4. **Using context composition** - `WebRtcContextComposer` for proper coroutine context management
5. **Platform-specific implementations** - Linux gets io_uring + eBPF, JVM gets NIO fallback

The QUIC initiator logic is preserved but now operates through the coroutine context key composition pattern, allowing the kernel to handle the actual stream management while maintaining the proper offerer/answerer role distinction and media stream coordination. 