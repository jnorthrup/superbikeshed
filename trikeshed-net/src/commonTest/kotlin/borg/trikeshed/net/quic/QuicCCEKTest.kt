@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.GlobalScope
import borg.trikeshed.lib.*

class QuicCCEKTest {

    @Test
    fun `CCEK should orchestrate QUIC operations with proper separation of concerns`() = runTest {
        // Given: A complete CCEK orchestrator setup
        val orchestrator = createTestOrchestrator()
        
        // When: We execute a stream creation operation
        val connectionId = ConnectionId.random()
        val operation = QuicOperation.CreateStream(connectionId, 0L)
        val result = orchestrator.execute(operation)
        
        // Then: Operation should succeed with proper CCEK flow
        assertTrue(result is QuicResult.Success)
    }
    
    @Test
    fun `CCEK Control layer should manage execution phases properly`() = runTest {
        // Given: Control layer components
        val flowManager = QuicFlowManager(maxConcurrentStreams = 2)
        val executionContext = QuicExecutionContext(
            ConnectionId.random(),
            "127.0.0.1:8443",
            "127.0.0.1:9443"
        )
        val control = QuicControl(
            QuicControl.QuicPhase.ESTABLISHED,
            flowManager,
            executionContext
        )
        
        // When: Planning execution for multiple operations
        val op1 = QuicOperation.CreateStream(ConnectionId.random(), 0L)
        val op2 = QuicOperation.CreateStream(ConnectionId.random(), 2L)
        val op3 = QuicOperation.CreateStream(ConnectionId.random(), 4L) // Should exceed limit
        
        val decision1 = control.flowManager.planExecution(op1, control.phase)
        val decision2 = control.flowManager.planExecution(op2, control.phase)
        val decision3 = control.flowManager.planExecution(op3, control.phase)
        
        // Then: Flow manager should apply backpressure appropriately
        assertTrue(decision1 is QuicFlowDecision.Proceed)
        assertTrue(decision2 is QuicFlowDecision.Proceed)
        assertTrue(decision3 is QuicFlowDecision.Backpressure)
    }
    
    @Test
    fun `CCEK Context layer should manage coroutine scopes and sessions`() = runTest {
        // Given: Context layer with session management
        val sessionCache = DefaultQuicSessionCache()
        val connectionPool = QuicConnectionPool()
        val streamRegistry = QuicStreamRegistry()
        
        val context = QuicContext(
            scope = GlobalScope,
            sessionCache = sessionCache,
            connectionPool = connectionPool,
            streamRegistry = streamRegistry
        )
        
        // When: Creating stream contexts
        val streamContext1 = context.createStreamContext(0L)
        val streamContext2 = context.createStreamContext(2L)
        
        // And: Registering streams
        streamRegistry.register(0L, "mock_stream_1")
        streamRegistry.register(2L, "mock_stream_2")
        
        // Then: Context should manage separate stream scopes
        assertEquals(0L, streamContext1.streamId)
        assertEquals(2L, streamContext2.streamId)
        assertNotNull(streamRegistry.getContext(0L))
        assertNotNull(streamRegistry.getContext(2L))
        assertNull(streamRegistry.getContext(4L))
    }
    
    @Test
    fun `CCEK Environment layer should abstract transport mechanisms`() = runTest {
        // Given: Mock transport implementation
        val transport = MockQuicTransport()
        val crypto = QuicCrypto()
        val congestionControl = QuicCongestionControl()
        val packetScheduler = QuicPacketScheduler()
        
        val environment = QuicEnvironment(
            transport = MockTransportAdapter(),
            crypto = crypto,
            congestionControl = congestionControl,
            packetScheduler = packetScheduler
        )
        
        // When: Delivering payloads through environment
        val connectionId = ConnectionId.random()
        val payload = 10 j { i: Int -> i.toByte() }
        val result = environment.deliverPayload(
            connectionId,
            0L,
            payload,
            QuicAction.Send
        )
        
        // Then: Environment should handle payload delivery
        assertTrue(result.success)
        assertEquals(10L, result.bytesTransferred)
    }
    
    @Test
    fun `CCEK Knowledge layer should validate protocol compliance`() = runTest {
        // Given: Knowledge layer with rules and constraints
        val protocolRules = QuicProtocolRules()
        val constraints = QuicConstraints(maxStreamsPerConnection = 2)
        val validator = DefaultQuicValidator()
        val attentionModel = DefaultQuicAttentionModel()
        
        val knowledge = QuicKnowledge(
            protocolRules = protocolRules,
            constraints = constraints,
            validator = validator,
            attentionModel = attentionModel
        )
        
        // When: Validating operations against constraints
        val state = QuicConnectionState(
            localConnectionId = ConnectionId.random(),
            remoteConnectionId = ConnectionId.random(),
            streams = 3 j { i -> QuicStreamState(i.toLong()) } // 3 streams = exceeds limit
        )
        
        val operation = QuicOperation.CreateStream(ConnectionId.random(), 6L)
        val validation = knowledge.validateOperation(operation, state)
        
        // Then: Knowledge layer should enforce constraints
        assertFalse(validation.isValid)
        assertEquals("Too many streams", validation.error)
    }
    
    @Test
    fun `CCEK should demonstrate attention-based range prioritization`() = runTest {
        // Given: CCEK orchestrator with attention model
        val orchestrator = createTestOrchestrator()
        
        // When: Requesting sparse ranges (attention mechanism)
        val ranges: Indexed<Twin<Long>> = 4 j { i: Int ->
            when (i) {
                0 -> 0L j 1023L        // First KB
                1 -> 4096L j 5119L     // Skip to 5th KB (attention gap)
                2 -> 8192L j 9215L     // Skip to 9th KB (larger gap)
                3 -> 16384L j 17407L   // Skip to 17th KB (even larger gap)
                else -> 0L j 0L
            }
        }
        
        val operation = QuicOperation.AttentionRequest(
            ConnectionId.random(),
            ranges,
            priority = 1 // High priority
        )
        
        val result = orchestrator.execute(operation)
        
        // Then: Attention mechanism should prioritize sparse access
        assertTrue(result is QuicResult.Success)
        
        // And: Verify attention model behavior
        val knowledge = (orchestrator as TestQuicCCEKOrchestrator).knowledge
        val attentionRanges = knowledge.applyAttention(ranges, 1)
        
        assertEquals(4, attentionRanges.a)
        assertEquals(0L, attentionRanges.b(0).start)
        assertEquals(1023L, attentionRanges.b(0).end)
        assertEquals(4096L, attentionRanges.b(1).start) // Demonstrates attention gap
        assertEquals(5119L, attentionRanges.b(1).end)
    }
    
    @Test
    fun `CCEK convergence should handle complex multi-stream scenarios`() = runTest {
        // Given: Orchestrator handling multiple concurrent operations
        val orchestrator = createTestOrchestrator()
        val connectionId = ConnectionId.random()
        
        // When: Executing multiple operations in sequence
        val operations = listOf(
            QuicOperation.Connect("example.com", 443),
            QuicOperation.CreateStream(connectionId, 0L),
            QuicOperation.CreateStream(connectionId, 2L),
            QuicOperation.SendData(connectionId, 0L, 5 j { i: Int -> i.toByte() }),
            QuicOperation.SendData(connectionId, 2L, 3 j { i: Int -> (i + 10).toByte() })
        )
        
        val results = mutableListOf<QuicResult>()
        for (operation in operations) {
            val result = orchestrator.execute(operation)
            results.add(result)
        }
        
        // Then: All operations should succeed with proper CCEK orchestration
        assertTrue(results.all { it is QuicResult.Success })
        assertEquals(5, results.size)
    }
    
    internal fun createTestOrchestrator(): TestQuicCCEKOrchestrator {
        val control = QuicControl(
            QuicControl.QuicPhase.ESTABLISHED,
            QuicFlowManager(),
            QuicExecutionContext(ConnectionId.random(), "local", "remote")
        )
        
        val context = QuicContext(
            scope = GlobalScope,
            sessionCache = DefaultQuicSessionCache(),
            connectionPool = QuicConnectionPool(),
            streamRegistry = QuicStreamRegistry()
        )
        
        val environment = QuicEnvironment(
            transport = MockTransportAdapter(),
            crypto = QuicCrypto(),
            congestionControl = QuicCongestionControl(),
            packetScheduler = QuicPacketScheduler()
        )
        
        val knowledge = QuicKnowledge(
            protocolRules = QuicProtocolRules(),
            constraints = QuicConstraints(),
            validator = DefaultQuicValidator(),
            attentionModel = DefaultQuicAttentionModel()
        )
        
        return TestQuicCCEKOrchestrator(control, context, environment, knowledge)
    }
}

// Test implementation that exposes internal components
class TestQuicCCEKOrchestrator(
    control: QuicControl,
    context: QuicContext,
    environment: QuicEnvironment,
    val knowledge: QuicKnowledge
) : QuicCCEKOrchestrator(control, context, environment, knowledge)

// Mock transport adapter for testing
class MockTransportAdapter : QuicTransport {
    override suspend fun send(connectionId: ConnectionId, streamId: Long, data: Indexed<Byte>): QuicDeliveryResult {
        return QuicDeliveryResult(true, data.a.toLong())
    }
    
    override suspend fun receive(connectionId: ConnectionId, streamId: Long): QuicDeliveryResult {
        return QuicDeliveryResult(true, 0)
    }
    
    override suspend fun createStream(connectionId: ConnectionId, streamId: Long): Any {
        return "mock_stream_$streamId"
    }
    
    override suspend fun closeStream(connectionId: ConnectionId, streamId: Long): QuicDeliveryResult {
        return QuicDeliveryResult(true)
    }
    
    override suspend fun resetStream(connectionId: ConnectionId, streamId: Long, errorCode: Long): QuicDeliveryResult {
        return QuicDeliveryResult(true)
    }
    
    override suspend fun requestRange(connectionId: ConnectionId, start: Long, end: Long): Indexed<Byte> {
        val size = (end - start + 1).toInt()
        return size j { i: Int -> (start + i).toByte() }
    }
}