@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import kotlinx.serialization.json.*
import kotlin.random.Random

/**
 * QUIC protocol engine - handles packet processing and state management
 * Uses mutable structures where needed for performance
 */
class QuicEngine(
    private val role: Role,
    private val initialState: QuicConnectionState
) {
    enum class Role { CLIENT, SERVER }
    
    // Mutable state for performance
    private var state = initialState
    private val streamStates = mutableMapOf<Long, QuicStreamState>()
    private val packetBuffer = mutableListOf<QuicPacket>()
    private val ackPending = mutableListOf<Long>()
    
    /**
     * Process incoming packet
     */
    fun processPacket(packet: QuicPacket): Indexed<QuicPacket> {
        val responses = mutableListOf<QuicPacket>()
        
        // Process each frame
        for (i in 0 until packet.frames.a) {
            val frame = packet.frames.b(i)
            when (frame) {
                is StreamFrame -> processStreamFrame(frame, responses)
                is AckFrame -> processAckFrame(frame)
                is CryptoFrame -> processCryptoFrame(frame, responses)
            }
        }
        
        // Update state with received packet
        state = state.copy(
            receivedPackets = appendToIndexed(state.receivedPackets, packet),
            nextPacketNumber = state.nextPacketNumber + 1
        )
        
        // Generate ACK if needed
        if (ackPending.size.nz) {
            responses.add(createAckPacket())
            ackPending.clear()
        }
        
        return responses.size j { responses[it] }
    }
    
    /**
     * Send data on a stream
     */
    fun sendStreamData(streamId: Long, data: Indexed<Byte>): QuicPacket {
        val stream = streamStates.getOrPut(streamId) {
            QuicStreamState(
                streamId = streamId,
                maxData = state.transportParams.maxStreamData
            )
        }
        
        // Create stream frame
        val frame = StreamFrame(
            streamId = streamId,
            offset = stream.sendOffset,
            data = data,
            fin = false
        )
        
        // Update stream state
        streamStates[streamId] = stream.copy(
            sendBuffer = appendToIndexed(stream.sendBuffer, data),
            sendOffset = stream.sendOffset + data.a
        )
        
        // Create packet
        val packet = QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.SHORT_HEADER,
                version = state.version,
                destinationConnectionId = state.remoteConnectionId,
                sourceConnectionId = state.localConnectionId,
                packetNumber = state.nextPacketNumber
            ),
            frames = 1 j { frame },
            payload = data
        )
        
        // Update connection state
        state = state.copy(
            sentPackets = appendToIndexed(state.sentPackets, packet),
            nextPacketNumber = state.nextPacketNumber + 1,
            bytesInFlight = state.bytesInFlight + data.a
        )
        
        return packet
    }
    
    /**
     * Create new stream
     */
    fun createStream(bidirectional: Boolean = true): Long {
        val streamId = state.nextStreamId
        val streamType = if (bidirectional) 0 else 2
        val initiator = if (role == Role.CLIENT) 0 else 1
        val actualStreamId = (streamId * 4) + streamType + initiator
        
        streamStates[actualStreamId] = QuicStreamState(
            streamId = actualStreamId,
            maxData = state.transportParams.maxStreamData
        )
        
        state = state.copy(nextStreamId = streamId + 1)
        return actualStreamId
    }
    
    // Private helper methods
    
    private fun processStreamFrame(frame: StreamFrame, responses: MutableList<QuicPacket>) {
        val stream = streamStates.getOrPut(frame.streamId) {
            QuicStreamState(
                streamId = frame.streamId,
                maxData = state.transportParams.maxStreamData
            )
        }
        
        // Update stream receive buffer
        streamStates[frame.streamId] = stream.copy(
            receiveBuffer = appendToIndexed(stream.receiveBuffer, frame.data),
            receiveOffset = frame.offset + frame.data.a
        )
        
        // Mark packet for ACK
        ackPending.add(state.nextPacketNumber - 1)
    }
    
    private fun processAckFrame(frame: AckFrame) {
        // Remove acknowledged packets from bytes in flight
        var ackedBytes = 0L
        for (i in 0 until frame.ackRanges.a) {
            val range = frame.ackRanges.b(i)
            val start = range.a
            val end = range.b
            
            // Calculate acked bytes (simplified)
            ackedBytes += (end - start + 1) * 1350 // Assume max packet size
        }
        
        state = state.copy(
            bytesInFlight = maxOf(0, state.bytesInFlight - ackedBytes)
        )
    }
    
    private fun processCryptoFrame(frame: CryptoFrame, responses: MutableList<QuicPacket>) {
        // Process crypto data (simplified - would involve TLS in real implementation)
        // For now, just ACK it
        ackPending.add(state.nextPacketNumber - 1)
    }
    
    private fun createAckPacket(): QuicPacket {
        // Create ACK ranges from pending ACKs
        val sortedAcks = ackPending.sorted()
        val ranges = mutableListOf<Join<Long, Long>>()
        
        if (sortedAcks.isNotEmpty()) {
            var start = sortedAcks[0]
            var end = sortedAcks[0]
            
            for (i in 1 until sortedAcks.size) {
                if (sortedAcks[i] == end + 1) {
                    end = sortedAcks[i]
                } else {
                    ranges.add(start j end)
                    start = sortedAcks[i]
                    end = sortedAcks[i]
                }
            }
            ranges.add(start j end)
        }
        
        val ackFrame = AckFrame(
            largestAcknowledged = sortedAcks.maxOrNull() ?: 0,
            ackDelay = 0,
            ackRanges = ranges.size j { ranges[it] }
        )
        
        return QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.SHORT_HEADER,
                version = state.version,
                destinationConnectionId = state.remoteConnectionId,
                sourceConnectionId = state.localConnectionId,
                packetNumber = state.nextPacketNumber
            ),
            frames = 1 j { ackFrame },
            payload = emptyIndex()
        )
    }
    
    private fun <T> appendToIndexed(indexed: Indexed<T>, item: T): Indexed<T> {
        val newSize = indexed.a + 1
        return newSize j { i: Int ->
            if (i < indexed.a) indexed.b(i) else item
        }
    }
    
    private fun <T> appendToIndexed(indexed: Indexed<T>, items: Indexed<T>): Indexed<T> {
        val newSize = indexed.a + items.a
        return newSize j { i: Int ->
            if (i < indexed.a) indexed.b(i) else items.b(i - indexed.a)
        }
    }
    
    private fun maxOf(a: Long, b: Long): Long = if (a > b) a else b
    
    // Public accessors
    fun getState(): QuicConnectionState = state
    fun getStream(streamId: Long): QuicStreamState? = streamStates[streamId]
    fun getActiveStreams(): Indexed<Long> {
        val ids = streamStates.keys.toList()
        return ids.size j { ids[it] }
    }
}