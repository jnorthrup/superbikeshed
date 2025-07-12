@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import borg.trikeshed.lib.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Mock transport layer for QUIC testing using channels instead of actual network
 */
class MockQuicTransport {
    internal val serverToClientChannel = Channel<QuicPacket>(Channel.UNLIMITED)
    internal val clientToServerChannel = Channel<QuicPacket>(Channel.UNLIMITED)
    
    /**
     * Create a connected client-server pair for testing
     */
    fun createConnectedPair(): Pair<MockQuicConnection, MockQuicConnection> {
        val clientConnection = MockQuicConnection(
            isClient = true,
            sendChannel = clientToServerChannel,
            receiveChannel = serverToClientChannel
        )
        
        val serverConnection = MockQuicConnection(
            isClient = false,
            sendChannel = serverToClientChannel,
            receiveChannel = clientToServerChannel
        )
        
        return clientConnection to serverConnection
    }
}

/**
 * Mock QUIC connection that uses channels for transport
 */
class MockQuicConnection(
    internal val isClient: Boolean,
    internal val sendChannel: SendChannel<QuicPacket>,
    internal val receiveChannel: ReceiveChannel<QuicPacket>
) {
    internal val streams = mutableMapOf<Long, MockQuicStream>()
    internal var nextStreamId = if (isClient) 0L else 1L // Client uses even, server uses odd
    internal var connected = false
    
    suspend fun connect(): Boolean {
        connected = true
        return true
    }
    
    fun isAlive(): Boolean = connected && !sendChannel.isClosedForSend
    
    suspend fun createStream(): MockQuicStream {
        val streamId = nextStreamId
        nextStreamId += 2 // Increment by 2 to maintain client/server distinction
        
        val stream = MockQuicStream(streamId, sendChannel, receiveChannel)
        streams[streamId] = stream
        return stream
    }
    
    suspend fun acceptStream(): MockQuicStream? {
        // For testing: just create a new stream
        return createStream()
    }
    
    fun getActiveStreamCount(): Int = streams.size
    
    suspend fun close() {
        connected = false
        streams.values.forEach { it.close() }
        streams.clear()
    }
}

/**
 * Mock QUIC stream using channels
 */
class MockQuicStream(
    val id: Long,
    internal val sendChannel: SendChannel<QuicPacket>,
    internal val receiveChannel: ReceiveChannel<QuicPacket>
) {
    internal var closed = false
    internal val receiveBuffer = mutableListOf<ByteArray>()
    
    suspend fun send(data: ByteArray, fin: Boolean = false) {
        if (closed) return
        
        // Create a mock QUIC packet with stream frame
        val streamFrame = StreamFrame(
            streamId = id,
            offset = 0L,
            data = data.size j { data[it] },
            fin = fin
        )
        
        val packet = QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.SHORT_HEADER,
                version = 1L,
                destinationConnectionId = ConnectionId.random(),
                sourceConnectionId = ConnectionId.random(),
                packetNumber = 1L
            ),
            frames = 1 j { streamFrame },
            payload = data.size j { data[it] }
        )
        
        sendChannel.send(packet)
        
        if (fin) {
            close()
        }
    }
    
    suspend fun receive(): ByteArray {
        if (closed) return ByteArray(0)
        
        // Try to receive a packet
        val packet = receiveChannel.tryReceive().getOrNull() ?: return ByteArray(0)
        
        // Extract stream data from packet
        for (i in 0 until packet.frames.component1()) {
            val frame = packet.frames.component2()(i)
            if (frame is StreamFrame && frame.streamId == id) {
                val data = ByteArray(frame.data.component1()) { j -> frame.data.component2()(j) }
                receiveBuffer.add(data)
                
                if (frame.fin) {
                    closed = true
                }
                
                return data
            }
        }
        
        return ByteArray(0)
    }
    
    fun isFinished(): Boolean = closed
    
    fun close() {
        closed = true
    }
}