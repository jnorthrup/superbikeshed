@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import borg.trikeshed.lib.*
import kotlinx.coroutines.CoroutineScope

/**
 * Supporting types for QUIC CCEK implementation
 */

// Control layer types
data class QuicFlowManager(
    internal val maxConcurrentStreams: Int = 100,
    internal val windowSize: Long = 65536
) {
    internal var nextStreamId = 0L
    internal val activeStreams = mutableSetOf<Long>()
    
    fun planExecution(operation: QuicOperation, phase: QuicControl.QuicPhase): QuicFlowDecision {
        return when (operation) {
            is QuicOperation.CreateStream -> {
                if (activeStreams.size >= maxConcurrentStreams) {
                    QuicFlowDecision.Backpressure("Max streams exceeded")
                } else {
                    QuicFlowDecision.Proceed
                }
            }
            is QuicOperation.SendData -> {
                if (phase == QuicControl.QuicPhase.ESTABLISHED) {
                    QuicFlowDecision.Proceed
                } else {
                    QuicFlowDecision.Defer("Connection not established")
                }
            }
            else -> QuicFlowDecision.Proceed
        }
    }
    
    fun allocateStream(): Long {
        val streamId = nextStreamId
        nextStreamId += 2 // Maintain client/server distinction
        activeStreams.add(streamId)
        return streamId
    }
    
    fun releaseStream(streamId: Long) {
        activeStreams.remove(streamId)
    }
}

data class QuicExecutionContext(
    val connectionId: ConnectionId,
    val localAddress: String,
    val remoteAddress: String
)

sealed class QuicFlowDecision {
    object Proceed : QuicFlowDecision()
    data class Defer(val reason: String) : QuicFlowDecision()
    data class Backpressure(val reason: String) : QuicFlowDecision()
}

// Context layer types
data class QuicConnectionPool(
    internal val connections: MutableMap<ConnectionId, QuicConnection> = mutableMapOf()
) {
    fun addConnection(connectionId: ConnectionId, connection: QuicConnection) {
        connections[connectionId] = connection
    }
    
    fun getConnection(connectionId: ConnectionId): QuicConnection? {
        return connections[connectionId]
    }
    
    fun getCurrentState(): QuicConnectionState {
        // Return aggregate state of all connections
        return QuicConnectionState(
            localConnectionId = ConnectionId.random(),
            remoteConnectionId = ConnectionId.random(),
            streams = \1 j { \2: Int ->
                val conn = connections.values.elementAt(i)
                QuicStreamState(streamId = i.toLong())
            }
        )
    }
}

data class QuicStreamRegistry(
    internal val streams: MutableMap<Long, QuicStreamContext> = mutableMapOf()
) {
    fun register(streamId: Long, stream: Any) {
        streams[streamId] = QuicStreamContext(
            streamId = streamId,
            parentScope = kotlinx.coroutines.GlobalScope,
            flowControl = QuicFlowControl(),
            priority = 10
        )
    }
    
    fun getContext(streamId: Long): QuicStreamContext? {
        return streams[streamId]
    }
}

data class QuicStreamContext(
    val streamId: Long,
    val parentScope: CoroutineScope,
    val flowControl: QuicFlowControl,
    val priority: Int
)

data class QuicFlowControl(
    var window: Long = 32768,
    var sent: Long = 0,
    var received: Long = 0
) {
    fun canSend(bytes: Long): Boolean = sent + bytes <= window
    fun updateWindow(newWindow: Long) { window = newWindow }
}

// Environment layer types
interface QuicTransport {
    suspend fun send(connectionId: ConnectionId, streamId: Long, data: Indexed<Byte>): QuicDeliveryResult
    suspend fun receive(connectionId: ConnectionId, streamId: Long): QuicDeliveryResult  
    suspend fun createStream(connectionId: ConnectionId, streamId: Long): Any
    suspend fun closeStream(connectionId: ConnectionId, streamId: Long): QuicDeliveryResult
    suspend fun resetStream(connectionId: ConnectionId, streamId: Long, errorCode: Long): QuicDeliveryResult
    suspend fun requestRange(connectionId: ConnectionId, start: Long, end: Long): Indexed<Byte>
}

data class QuicCrypto(
    val tlsVersion: String = "1.3",
    val cipherSuite: String = "TLS_AES_256_GCM_SHA384"
)

data class QuicCongestionControl(
    val algorithm: String = "CUBIC",
    val window: Long = 14720, // Initial congestion window
    val ssthresh: Long = Long.MAX_VALUE
)

data class QuicPacketScheduler(
    val maxPacketSize: Int = 1350,
    val batchSize: Int = 16
)

data class QuicDeliveryResult(
    val success: Boolean,
    val bytesTransferred: Long = 0,
    val error: String? = null
)

// Knowledge layer types
data class QuicProtocolRules(
    val maxStreamId: Long = 1L shl 60,
    val maxDataPerStream: Long = 1L shl 32,
    val idleTimeout: Long = 30000,
    val maxAckDelay: Long = 25
)

data class QuicConstraints(
    val maxConnections: Int = 1000,
    val maxStreamsPerConnection: Int = 100,
    val maxFrameSize: Int = 16384
)

interface QuicValidator {
    fun validate(
        operation: QuicOperation,
        state: QuicConnectionState,
        rules: QuicProtocolRules,
        constraints: QuicConstraints
    ): QuicValidationResult
}

data class QuicValidationResult(
    val isValid: Boolean,
    val error: String? = null
)

interface QuicAttentionModel {
    fun prioritizeRanges(ranges: Indexed<Twin<Long>>, priority: Int): Indexed<QuicAttentionRange>
}

data class QuicAttentionRange(
    val connectionId: ConnectionId,
    val start: Long,
    val end: Long,
    val priority: Int,
    val streamId: Long
)

data class QuicRangeResult(
    val streamId: Long,
    val start: Long,
    val end: Long,
    val data: Indexed<Byte>
)

// Default implementations
class DefaultQuicValidator : QuicValidator {
    override fun validate(
        operation: QuicOperation,
        state: QuicConnectionState,
        rules: QuicProtocolRules,
        constraints: QuicConstraints
    ): QuicValidationResult {
        return when (operation) {
            is QuicOperation.CreateStream -> {
                if (state.streams.component1() >= constraints.maxStreamsPerConnection) {
                    QuicValidationResult(false, "Too many streams")
                } else {
                    QuicValidationResult(true)
                }
            }
            is QuicOperation.SendData -> {
                if (operation.data.component1() > constraints.maxFrameSize) {
                    QuicValidationResult(false, "Frame too large")
                } else {
                    QuicValidationResult(true)
                }
            }
            else -> QuicValidationResult(true)
        }
    }
}

class DefaultQuicAttentionModel : QuicAttentionModel {
    override fun prioritizeRanges(ranges: Indexed<Twin<Long>>, priority: Int): Indexed<QuicAttentionRange> {
        return ranges.component1() j { i: Int ->
            val range = ranges.component2()(i)
            QuicAttentionRange(
                connectionId = ConnectionId.random(),
                start = range.component1(),
                end = range.component2(),
                priority = priority,
                streamId = i.toLong()
            )
        }
    }
}