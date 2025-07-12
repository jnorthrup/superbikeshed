package tests

import borg.trikeshed.ccek.*
import borg.trikeshed.channel.api.*
import borg.trikeshed.ipfs.*
import borg.trikeshed.lib.*
import com.superbikeshed.trikeshed.*
import moneyfan.trikeshed.context.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.coroutines.*

/**
 * Comprehensive TDD test suite for all CCEK keys in the codebase.
 * Tests coroutine context key composition and integration patterns.
 */
class CCEKKeysTDDTest {

    // === CORE CCEK TESTS ===

    @Test
    fun `should create and access CcekContext with all four components`() = runTest {
        // Given: CCEK context with all components
        val control = Control(executionId = "test-123", phase = ExecutionPhase.INIT)
        val context = Context(sessionId = "session-456")
        val environment = Environment(action = "test-action", payload = "test-payload")
        val knowledge = Knowledge(
            rules = 1 j { TransformationRule("test-rule", RuleCondition.FieldEquals("field", "value"), TransformationAction.SetField("field", "new-value")) },
            constraints = 1 j { Constraint("test-constraint", "Test constraint", ConstraintValidation.FieldRequired("field")) },
            validator = { true }
        )
        
        val ccekContext = CcekContext(control, context, environment, knowledge)
        
        // When: Context is added to coroutine context
        val result = withContext(ceckContext) {
            val retrieved = coroutineContext[CcekContext.CcekContextKey]
            retrieved
        }
        
        // Then: Should retrieve the same context
        assertNotNull(result)
        assertEquals("test-123", result.control.executionId)
        assertEquals("session-456", result.context.sessionId)
        assertEquals("test-action", result.environment.action)
        assertEquals(1, result.knowledge.rules.component1())
    }

    @Test
    fun `should compose multiple CCEK contexts together`() = runTest {
        // Given: Multiple CCEK contexts
        val ccekContext = CcekContext(
            control = Control("exec-1"),
            context = Context("session-1"),
            environment = Environment("action-1", "payload-1"),
            knowledge = Knowledge(0 j { }, 0 j { }, { true })
        )
        
        val uringContext = UringBatchContext(
            batchSize = 32,
            ringFd = 42,
            sqeDepth = 4096,
            cqeDepth = 8192
        )
        
        val channelContext = ChannelChainContext(0 j { })
        
        // When: All contexts are composed
        val result = withContext(ceckContext + uringContext + channelContext) {
            val ccek = coroutineContext[CcekContext.CcekContextKey]
            val uring = coroutineContext[UringBatchContext.UringBatchKey]
            val channel = coroutineContext[ChannelChainContext.ChannelChainKey]
            
            Triple(ceck, uring, channel)
        }
        
        // Then: All contexts should be accessible
        assertNotNull(result.first)
        assertNotNull(result.second)
        assertNotNull(result.third)
        assertEquals(32, result.second?.batchSize)
        assertEquals(42, result.second?.ringFd)
    }

    // === CHANNEL API CCEK TESTS ===

    @Test
    fun `should create and access ChannelService context`() = runTest {
        // Given: Channel service context
        val channelService = ChannelService(
            name = "test-channel",
            type = ChannelType.TCP_SERVER,
            config = ChannelConfig(port = 8080)
        )
        
        // When: Service is added to context
        val result = withContext(channelService) {
            val service = coroutineContext[ChannelService.Key]
            service
        }
        
        // Then: Should retrieve the service
        assertNotNull(result)
        assertEquals("test-channel", result.name)
        assertEquals(ChannelType.TCP_SERVER, result.type)
        assertEquals(8080, result.config.port)
    }

    @Test
    fun `should create and access RecordingService context`() = runTest {
        // Given: Recording service context
        val recordingService = RecordingService(
            name = "test-recorder",
            enabled = true,
            config = RecordingConfig(maxDuration = 300)
        )
        
        // When: Service is added to context
        val result = withContext(recordingService) {
            val service = coroutineContext[RecordingService.Key]
            service
        }
        
        // Then: Should retrieve the service
        assertNotNull(result)
        assertEquals("test-recorder", result.name)
        assertTrue(result.enabled)
        assertEquals(300, result.config.maxDuration)
    }

    // === IPFS CCEK TESTS ===

    @Test
    fun `should create and access IPFS client context`() = runTest {
        // Given: IPFS client context
        val client = IpfsClient() // Mock client
        val ipfsClientContext = IpfsClientContext(client)
        
        // When: Context is added to coroutine context
        val result = withContext(ipfsClientContext) {
            val context = coroutineContext[IpfsClientContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(client, result.client)
    }

    @Test
    fun `should create and access IPFS server context`() = runTest {
        // Given: IPFS server context
        val server = IpfsServer() // Mock server
        val ipfsServerContext = IpfsServerContext(server)
        
        // When: Context is added to coroutine context
        val result = withContext(ipfsServerContext) {
            val context = coroutineContext[IpfsServerContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(server, result.server)
    }

    @Test
    fun `should create and access DHT service context`() = runTest {
        // Given: DHT service context
        val dht = DHTService() // Mock DHT service
        val dhtContext = DHTServiceContext(dht)
        
        // When: Context is added to coroutine context
        val result = withContext(dhtContext) {
            val context = coroutineContext[DHTServiceContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(dht, result.dht)
    }

    @Test
    fun `should create and access IPFS peer context`() = runTest {
        // Given: IPFS peer context
        val peerId = PeerId("QmTestPeer123")
        val addresses = \1 j { \2: Int -> "127.0.0.1:${4001 + i}" }
        val protocols = \1 j { \2: Int -> if (i == 0) "/ipfs/kad/1.0.0" else "/ipfs/bitswap/1.2.0" }
        
        val peerContext = IpfsPeerContext(peerId, addresses, protocols)
        
        // When: Context is added to coroutine context
        val result = withContext(peerContext) {
            val context = coroutineContext[IpfsPeerContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(peerId, result.peerId)
        assertEquals(2, result.addresses.component1())
        assertEquals(2, result.protocols.component1())
        assertEquals("/ipfs/kad/1.0.0", result.protocols.component2()(0))
    }

    @Test
    fun `should create and access IPFS content context`() = runTest {
        // Given: IPFS content context
        val cid = CID("QmContent123")
        val providers = 1 j { PeerInfo(PeerId("QmProvider1"), "127.0.0.1:4001") }
        
        val contentContext = IpfsContentContext(cid, providers, isPinned = true)
        
        // When: Context is added to coroutine context
        val result = withContext(contentContext) {
            val context = coroutineContext[IpfsContentContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(cid, result.cid)
        assertEquals(1, result.providers.component1())
        assertTrue(result.isPinned)
    }

    // === URING CCEK TESTS ===

    @Test
    fun `should create and access URing context`() = runTest {
        // Given: URing context
        val uringContext = URingContext(
            ringId = 1,
            ringSize = 1024,
            features = setOf(URingFeature.SQPOLL, URingFeature.IOPOLL),
            bufferRingId = 2
        )
        
        // When: Context is added to coroutine context
        val result = withContext(uringContext) {
            val context = coroutineContext[URingContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(1, result.ringId)
        assertEquals(1024, result.ringSize)
        assertEquals(2, result.bufferRingId)
        assertTrue(result.features.contains(URingFeature.SQPOLL))
    }

    @Test
    fun `should create and access QUIC protocol context`() = runTest {
        // Given: QUIC protocol context
        val quicContext = ProtocolContext.QUIC(
            version = "v1",
            features = setOf(ProtocolFeature.MULTIPLEXING, ProtocolFeature.ENCRYPTION),
            congestionAlgorithm = CongestionAlgorithm.BBR
        )
        
        // When: Context is added to coroutine context
        val result = withContext(quicContext) {
            val context = coroutineContext[ProtocolContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertTrue(result is ProtocolContext.QUIC)
        val quic = result as ProtocolContext.QUIC
        assertEquals("v1", quic.version)
        assertEquals(CongestionAlgorithm.BBR, quic.congestionAlgorithm)
        assertTrue(quic.features.contains(ProtocolFeature.MULTIPLEXING))
    }

    @Test
    fun `should create and access HTTP2 protocol context`() = runTest {
        // Given: HTTP2 protocol context
        val http2Context = ProtocolContext.HTTP2(
            version = "2.0",
            features = setOf(ProtocolFeature.MULTIPLEXING, ProtocolFeature.SERVER_PUSH),
            maxConcurrentStreams = 200,
            initialWindowSize = 131072
        )
        
        // When: Context is added to coroutine context
        val result = withContext(http2Context) {
            val context = coroutineContext[ProtocolContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertTrue(result is ProtocolContext.HTTP2)
        val http2 = result as ProtocolContext.HTTP2
        assertEquals("2.0", http2.version)
        assertEquals(200, http2.maxConcurrentStreams)
        assertEquals(131072, http2.initialWindowSize)
    }

    @Test
    fun `should create and access buffer context`() = runTest {
        // Given: Buffer context
        val bufferContext = BufferContext(
            strategy = BufferStrategy.RingBuffer,
            slabSize = 8192,
            ringBuffers = 2048,
            zeroGopy = true
        )
        
        // When: Context is added to coroutine context
        val result = withContext(bufferContext) {
            val context = coroutineContext[BufferContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertTrue(result.strategy is BufferStrategy.RingBuffer)
        assertEquals(8192, result.slabSize)
        assertEquals(2048, result.ringBuffers)
        assertTrue(result.zeroGopy)
    }

    @Test
    fun `should create and access performance context`() = runTest {
        // Given: Performance context
        val performanceContext = PerformanceContext(
            batchSize = 64,
            submitThreshold = 32,
            completionMode = CompletionMode.ADAPTIVE,
            cpuAffinity = listOf(0, 1, 2),
            priority = TaskPriority.HIGH
        )
        
        // When: Context is added to coroutine context
        val result = withContext(performanceContext) {
            val context = coroutineContext[PerformanceContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(64, result.batchSize)
        assertEquals(32, result.submitThreshold)
        assertEquals(CompletionMode.ADAPTIVE, result.completionMode)
        assertEquals(listOf(0, 1, 2), result.cpuAffinity)
        assertEquals(TaskPriority.HIGH, result.priority)
    }

    @Test
    fun `should create and access security context`() = runTest {
        // Given: Security context
        val tlsConfig = TLSConfig(
            version = TLSVersion.TLS_1_3,
            cipherSuites = listOf("TLS_AES_128_GCM_SHA256"),
            alpnProtocols = listOf("h2", "http/1.1"),
            sessionCache = true
        )
        
        val securityContext = SecurityContext(
            tlsConfig = tlsConfig,
            authentication = AuthenticationMode.CERTIFICATE,
            encryption = EncryptionMode.REQUIRED
        )
        
        // When: Context is added to coroutine context
        val result = withContext(securityContext) {
            val context = coroutineContext[SecurityContext.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(tlsConfig, result.tlsConfig)
        assertEquals(AuthenticationMode.CERTIFICATE, result.authentication)
        assertEquals(EncryptionMode.REQUIRED, result.encryption)
    }

    // === LIB MODULE CCEK TESTS ===

    @Test
    fun `should create and access execution ID context`() = runTest {
        // Given: Execution ID context
        val executionId = ExecutionId("exec-789")
        
        // When: Context is added to coroutine context
        val result = withContext(executionId) {
            val context = coroutineContext[ExecutionId.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals("exec-789", result.id)
    }

    @Test
    fun `should create and access execution phase context`() = runTest {
        // Given: Execution phase context
        val executionPhase = ExecutionPhase("VALIDATE")
        
        // When: Context is added to coroutine context
        val result = withContext(executionPhase) {
            val context = coroutineContext[ExecutionPhase.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals("VALIDATE", result.phase)
    }

    @Test
    fun `should create and access transformation rules context`() = runTest {
        // Given: Transformation rules context
        val rules = \1 j { \2: Int ->
            TransformationRule(
                name = "rule-$i",
                condition = RuleCondition.FieldEquals("field$i", "value$i"),
                action = TransformationAction.SetField("field$i", "new-value$i"),
                priority = i
            )
        }
        
        val transformationRules = TransformationRules(rules)
        
        // When: Context is added to coroutine context
        val result = withContext(transformationRules) {
            val context = coroutineContext[TransformationRules.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(2, result.rules.component1())
        assertEquals("rule-0", result.rules.component2()(0).name)
        assertEquals("rule-1", result.rules.component2()(1).name)
    }

    @Test
    fun `should create and access validation constraints context`() = runTest {
        // Given: Validation constraints context
        val constraints = \1 j { \2: Int ->
            Constraint(
                name = "constraint-$i",
                description = "Test constraint $i",
                validation = ConstraintValidation.FieldRequired("field$i"),
                severity = if (i == 0) ConstraintSeverity.ERROR else ConstraintSeverity.WARNING
            )
        }
        
        val validationConstraints = ValidationConstraints(constraints)
        
        // When: Context is added to coroutine context
        val result = withContext(validationConstraints) {
            val context = coroutineContext[ValidationConstraints.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(2, result.constraints.component1())
        assertEquals("constraint-0", result.constraints.component2()(0).name)
        assertEquals(ConstraintSeverity.ERROR, result.constraints.component2()(0).severity)
        assertEquals(ConstraintSeverity.WARNING, result.constraints.component2()(1).severity)
    }

    @Test
    fun `should create and access IO preference context`() = runTest {
        // Given: IO preference context
        val ioPreference = IoPreference(IoCapability.URING)
        
        // When: Context is added to coroutine context
        val result = withContext(ioPreference) {
            val context = coroutineContext[IoPreference.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(IoCapability.URING, result.capability)
    }

    @Test
    fun `should create and access protocol channels context`() = runTest {
        // Given: Protocol channels context
        val channels = \1 j { \2: Int ->
            AsyncChannelContext(
                channelId = "channel-$i",
                fd = 100 + i,
                type = AsyncChannelContext.ChannelType.TCP_CLIENT,
                localAddr = "127.0.0.1:${8000 + i}",
                remoteAddr = "127.0.0.1:${9000 + i}"
            )
        }
        
        val protocolChannels = ProtocolChannels(channels)
        
        // When: Context is added to coroutine context
        val result = withContext(protocolChannels) {
            val context = coroutineContext[ProtocolChannels.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(2, result.channels.component1())
        assertEquals("channel-0", result.channels.component2()(0).channelId)
        assertEquals(100, result.channels.component2()(0).fd)
        assertEquals(AsyncChannelContext.ChannelType.TCP_CLIENT, result.channels.component2()(0).type)
    }

    @Test
    fun `should create and access handler registry context`() = runTest {
        // Given: Handler registry context
        val handlers = mapOf(
            "handler1" to { input: String -> "processed: $input" },
            "handler2" to { input: String -> "transformed: $input" }
        )
        
        val handlerRegistry = HandlerRegistry(handlers)
        
        // When: Context is added to coroutine context
        val result = withContext(handlerRegistry) {
            val context = coroutineContext[HandlerRegistry.Key]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(2, result.handlers.size)
        assertNotNull(result.get("handler1"))
        assertNotNull(result.get("handler2"))
    }

    // === MONEYFAN CCEK TESTS ===

    @Test
    fun `should create and access latency provider context`() = runTest {
        // Given: Latency provider context
        val latencyProvider = object : LatencyProvider {
            override val key: CoroutineContext.Key<*> = LatencyProviderKey
            
            override suspend fun simulateLatency(duration: Long) {
                delay(duration)
            }
        }
        
        // When: Context is added to coroutine context
        val result = withContext(latencyProvider) {
            val context = coroutineContext[LatencyProviderKey]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(LatencyProviderKey, result.key)
    }

    // === WEBSOCKET CCEK TESTS ===

    @Test
    fun `should create and access WebSocket stream context`() = runTest {
        // Given: WebSocket stream context
        val streamId = 123L
        val webSocketStreamKey = WebSocketStreamKey(streamId)
        
        // When: Context is added to coroutine context
        val result = withContext(webSocketStreamKey) {
            val context = coroutineContext[WebSocketStreamKey]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(streamId, result.streamId)
    }

    @Test
    fun `should create and access WebSocket connection context`() = runTest {
        // Given: WebSocket connection context
        val connectionId = WebSocketConnectionId(UByteArray(16) { it.toUByte() })
        val webSocketConnectionKey = WebSocketConnectionKey(connectionId)
        
        // When: Context is added to coroutine context
        val result = withContext(webSocketConnectionKey) {
            val context = coroutineContext[WebSocketConnectionKey]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(connectionId, result.connectionId)
    }

    @Test
    fun `should create and access WebSocket role context`() = runTest {
        // Given: WebSocket role context
        val role = WebSocketRole.CLIENT
        val webSocketRoleKey = WebSocketRoleKey(role)
        
        // When: Context is added to coroutine context
        val result = withContext(webSocketRoleKey) {
            val context = coroutineContext[WebSocketRoleKey]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(role, result.role)
    }

    // === WEBRTC CCEK TESTS ===

    @Test
    fun `should create and access WebRTC signaling stream context`() = runTest {
        // Given: WebRTC signaling stream context
        val streamId = 456L
        val webRtcSignalingStreamKey = WebRtcSignalingStreamKey(streamId)
        
        // When: Context is added to coroutine context
        val result = withContext(webRtcSignalingStreamKey) {
            val context = coroutineContext[WebRtcSignalingStreamKey]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(streamId, result.streamId)
    }

    @Test
    fun `should create and access WebRTC media stream context`() = runTest {
        // Given: WebRTC media stream context
        val streamId = 789L
        val mediaType = WebRtcMediaType.AUDIO
        val webRtcMediaStreamKey = WebRtcMediaStreamKey(streamId, mediaType)
        
        // When: Context is added to coroutine context
        val result = withContext(webRtcMediaStreamKey) {
            val context = coroutineContext[WebRtcMediaStreamKey]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(streamId, result.streamId)
        assertEquals(mediaType, result.mediaType)
    }

    @Test
    fun `should create and access WebRTC connection context`() = runTest {
        // Given: WebRTC connection context
        val connectionId = WebRtcConnectionId(UByteArray(16) { it.toUByte() })
        val webRtcConnectionKey = WebRtcConnectionKey(connectionId)
        
        // When: Context is added to coroutine context
        val result = withContext(webRtcConnectionKey) {
            val context = coroutineContext[WebRtcConnectionKey]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(connectionId, result.connectionId)
    }

    @Test
    fun `should create and access WebRTC role context`() = runTest {
        // Given: WebRTC role context
        val role = WebRtcRole.OFFERER
        val webRtcRoleKey = WebRtcRoleKey(role)
        
        // When: Context is added to coroutine context
        val result = withContext(webRtcRoleKey) {
            val context = coroutineContext[WebRtcRoleKey]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(role, result.role)
    }

    @Test
    fun `should create and access WebRTC peer context`() = runTest {
        // Given: WebRTC peer context
        val peerId = WebRtcPeerId(UByteArray(8) { it.toUByte() })
        val webRtcPeerKey = WebRtcPeerKey(peerId)
        
        // When: Context is added to coroutine context
        val result = withContext(webRtcPeerKey) {
            val context = coroutineContext[WebRtcPeerKey]
            context
        }
        
        // Then: Should retrieve the context
        assertNotNull(result)
        assertEquals(peerId, result.peerId)
    }

    // === COMPLEX CONTEXT COMPOSITION TESTS ===

    @Test
    fun `should compose multiple CCEK contexts from different modules`() = runTest {
        // Given: Multiple CCEK contexts from different modules
        val ccekContext = CcekContext(
            control = Control("exec-1"),
            context = Context("session-1"),
            environment = Environment("action-1", "payload-1"),
            knowledge = Knowledge(0 j { }, 0 j { }, { true })
        )
        
        val uringContext = URingContext(1, 1024, setOf(URingFeature.SQPOLL))
        val quicContext = ProtocolContext.QUIC()
        val bufferContext = BufferContext(BufferStrategy.RingBuffer)
        val performanceContext = PerformanceContext(batchSize = 64)
        val securityContext = SecurityContext()
        
        val executionId = ExecutionId("exec-1")
        val executionPhase = ExecutionPhase("INIT")
        val ioPreference = IoPreference(IoCapability.URING)
        
        val webSocketStreamKey = WebSocketStreamKey(123L)
        val webRtcSignalingStreamKey = WebRtcSignalingStreamKey(456L)
        
        // When: All contexts are composed together
        val result = withContext(
            ccekContext + uringContext + quicContext + bufferContext + 
            performanceContext + securityContext + executionId + executionPhase + 
            ioPreference + webSocketStreamKey + webRtcSignalingStreamKey
        ) {
            val ccek = coroutineContext[CcekContext.CcekContextKey]
            val uring = coroutineContext[URingContext.Key]
            val protocol = coroutineContext[ProtocolContext.Key]
            val buffer = coroutineContext[BufferContext.Key]
            val performance = coroutineContext[PerformanceContext.Key]
            val security = coroutineContext[SecurityContext.Key]
            val execId = coroutineContext[ExecutionId.Key]
            val execPhase = coroutineContext[ExecutionPhase.Key]
            val ioPref = coroutineContext[IoPreference.Key]
            val wsStream = coroutineContext[WebSocketStreamKey]
            val webrtcStream = coroutineContext[WebRtcSignalingStreamKey]
            
            listOf(ceck, uring, protocol, buffer, performance, security, 
                   execId, execPhase, ioPref, wsStream, webrtcStream)
        }
        
        // Then: All contexts should be accessible
        assertEquals(11, result.size)
        result.forEach { assertNotNull(it) }
        
        // Verify specific values
        assertEquals("exec-1", result[0]?.control?.executionId)
        assertEquals(1, (result[1] as URingContext).ringId)
        assertTrue(result[2] is ProtocolContext.QUIC)
        assertTrue((result[3] as BufferContext).strategy is BufferStrategy.RingBuffer)
        assertEquals(64, (result[4] as PerformanceContext).batchSize)
        assertEquals("exec-1", (result[6] as ExecutionId).id)
        assertEquals("INIT", (result[7] as ExecutionPhase).phase)
        assertEquals(IoCapability.URING, (result[8] as IoPreference).capability)
        assertEquals(123L, (result[9] as WebSocketStreamKey).streamId)
        assertEquals(456L, (result[10] as WebRtcSignalingStreamKey).streamId)
    }

    @Test
    fun `should handle context composition with null values gracefully`() = runTest {
        // Given: Context with some null values
        val executionId = ExecutionId("exec-1")
        val ioPreference = IoPreference(IoCapability.URING)
        
        // When: Context is composed and accessed
        val result = withContext(executionId + ioPreference) {
            val execId = coroutineContext[ExecutionId.Key]
            val ioPref = coroutineContext[IoPreference.Key]
            val nonExistent = coroutineContext[WebSocketStreamKey] // Should be null
            
            Triple(execId, ioPref, nonExistent)
        }
        
        // Then: Should handle null values gracefully
        assertNotNull(result.first)
        assertNotNull(result.second)
        assertNull(result.third)
    }

    @Test
    fun `should support context inheritance in nested coroutines`() = runTest {
        // Given: Parent context
        val parentContext = ExecutionId("parent-exec") + IoPreference(IoCapability.URING)
        
        // When: Nested coroutines are created
        val result = withContext(parentContext) {
            val parentExecId = coroutineContext[ExecutionId.Key]
            val parentIoPref = coroutineContext[IoPreference.Key]
            
            // Nested coroutine inherits parent context
            val nestedResult = withContext(ExecutionPhase("NESTED")) {
                val nestedExecId = coroutineContext[ExecutionId.Key]
                val nestedIoPref = coroutineContext[IoPreference.Key]
                val nestedPhase = coroutineContext[ExecutionPhase.Key]
                
                Triple(nestedExecId, nestedIoPref, nestedPhase)
            }
            
            Triple(parentExecId, parentIoPref, nestedResult)
        }
        
        // Then: Context should be inherited properly
        assertNotNull(result.first)
        assertNotNull(result.second)
        
        val nested = result.third
        assertEquals("parent-exec", nested.first?.id) // Inherited from parent
        assertEquals(IoCapability.URING, nested.second?.capability) // Inherited from parent
        assertEquals("NESTED", nested.third?.phase) // New in nested context
    }

    // === PERFORMANCE TESTS ===

    @Test
    fun `should handle large number of CCEK contexts efficiently`() = runTest {
        // Given: Large number of contexts
        val contexts = (0..100).map { i ->
            ExecutionId("exec-$i")
        }.toTypedArray()
        
        // When: All contexts are composed
        val startTime = System.currentTimeMillis()
        val result = withContext(contexts.reduce { acc, ctx -> acc + ctx }) {
            val execIds = (0..100).map { i ->
                coroutineContext[ExecutionId.Key]
            }
            execIds
        }
        val endTime = System.currentTimeMillis()
        
        // Then: Should complete efficiently
        assertEquals(101, result.size)
        assertTrue(endTime - startTime < 1000) // Should complete in under 1 second
        
        // Verify all contexts are accessible
        result.forEachIndexed { index, execId ->
            assertNotNull(execId)
            assertEquals("exec-$index", execId.id)
        }
    }

    // === ERROR HANDLING TESTS ===

    @Test
    fun `should handle missing context gracefully`() = runTest {
        // Given: Empty context
        val emptyContext = EmptyCoroutineContext
        
        // When: Accessing non-existent context
        val result = withContext(emptyContext) {
            val execId = coroutineContext[ExecutionId.Key]
            val ioPref = coroutineContext[IoPreference.Key]
            
            Pair(execId, ioPref)
        }
        
        // Then: Should return null for missing contexts
        assertNull(result.first)
        assertNull(result.second)
    }

    @Test
    fun `should handle context key collisions properly`() = runTest {
        // Given: Contexts with same key type but different values
        val execId1 = ExecutionId("exec-1")
        val execId2 = ExecutionId("exec-2")
        
        // When: Contexts are composed (later one should override)
        val result = withContext(execId1 + execId2) {
            val execId = coroutineContext[ExecutionId.Key]
            execId
        }
        
        // Then: Should use the last context added
        assertNotNull(result)
        assertEquals("exec-2", result.id)
    }
}

// === MOCK IMPLEMENTATIONS FOR TESTING ===

// Mock IPFS classes for testing
class IpfsClient
class IpfsServer
class DHTService
class IpfsStorage
class IpfsPubSubService
class IpfsServerConfig
class IpfsServerStats

data class PeerId(val id: String)
data class CID(val id: String)
data class PeerInfo(val peerId: PeerId, val address: String)

// Mock WebSocket/WebRTC classes for testing
@JvmInline value class WebSocketConnectionId(val bytes: UByteArray)
@JvmInline value class WebRtcConnectionId(val bytes: UByteArray)
@JvmInline value class WebRtcPeerId(val bytes: UByteArray)

enum class WebSocketRole { CLIENT, SERVER }
enum class WebRtcRole { OFFERER, ANSWERER }
enum class WebRtcMediaType { AUDIO, VIDEO, DATA }

// Mock data classes for WebSocket/WebRTC context keys
data class WebSocketStreamKey(val streamId: Long) : CoroutineContext.Key<WebSocketStreamKey>
data class WebSocketConnectionKey(val connectionId: WebSocketConnectionId) : CoroutineContext.Key<WebSocketConnectionKey>
data class WebSocketRoleKey(val role: WebSocketRole) : CoroutineContext.Key<WebSocketRoleKey>

data class WebRtcSignalingStreamKey(val streamId: Long) : CoroutineContext.Key<WebRtcSignalingStreamKey>
data class WebRtcMediaStreamKey(val streamId: Long, val mediaType: WebRtcMediaType) : CoroutineContext.Key<WebRtcMediaStreamKey>
data class WebRtcConnectionKey(val connectionId: WebRtcConnectionId) : CoroutineContext.Key<WebRtcConnectionKey>
data class WebRtcRoleKey(val role: WebRtcRole) : CoroutineContext.Key<WebRtcRoleKey>
data class WebRtcPeerKey(val peerId: WebRtcPeerId) : CoroutineContext.Key<WebRtcPeerKey> 