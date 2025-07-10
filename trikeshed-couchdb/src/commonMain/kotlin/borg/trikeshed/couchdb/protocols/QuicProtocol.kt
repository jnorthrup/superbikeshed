package borg.trikeshed.couchdb.protocols

import borg.trikeshed.lib.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable

/**
 * QUIC Protocol Implementation
 * 
 * TDD Implementation of QUIC channelized client with FSM states and contexts
 * for CouchDB integration via TrikeShed channels.
 */

// ===== QUIC FSM STATES =====

enum class QuicFSMState {
    Initial,
    Handshake,
    Connected,
    StreamOpen,
    DataTransfer,
    StreamClose,
    ConnectionClose,
    Error
}

// ===== QUIC CHANNELS =====

interface QuicChannel {
    val channelId: String
    val streamId: UInt
    val isActive: Boolean
    suspend fun send(data: ByteArray): Boolean
    suspend fun receive(): ByteArray?
    fun close()
}

class QuicStreamChannel(
    override val channelId: String,
    override val streamId: UInt,
    private val dataChannel: Channel<QuicStreamData>
) : QuicChannel {
    override var isActive: Boolean = true
        private set

    override suspend fun send(data: ByteArray): Boolean {
        if (!isActive) return false
        val streamData = QuicStreamData(streamId, data, false)
        return dataChannel.trySend(streamData).isSuccess
    }

    override suspend fun receive(): ByteArray? {
        if (!isActive) return null
        return try {
            val streamData = dataChannel.receive()
            streamData.data
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        isActive = false
        dataChannel.close()
    }
}

// ===== QUIC DATA STRUCTURES =====

@Serializable
data class QuicStreamData(
    val streamId: UInt,
    val data: ByteArray,
    val isFin: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as QuicStreamData
        
        if (streamId != other.streamId) return false
        if (!data.contentEquals(other.data)) return false
        if (isFin != other.isFin) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = streamId.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + isFin.hashCode()
        return result
    }
}

@Serializable
data class QuicConnectionInfo(
    val connectionId: String,
    val streamCount: UInt = 0u,
    val isActive: Boolean = true
)

// ===== QUIC CCek CONTEXT =====

data class QuicCCekContext(
    val streamId: UInt,
    val connectionId: String,
    val channels: Indexed<QuicChannel>,
    val fsmState: QuicFSMState = QuicFSMState.Initial,
    val maxStreams: UInt = 100u,
    val timeout: Long = 30000L
)

// ===== QUIC CHANNELIZED CLIENT =====

class QuicChannelizedClient {
    private val connections = mutableMapOf<String, QuicConnectionInfo>()
    private val streams = mutableMapOf<UInt, QuicStreamChannel>()
    private val dataChannels = mutableMapOf<UInt, Channel<QuicStreamData>>()
    private val activeContexts = mutableMapOf<String, QuicCCekContext>()

    suspend fun connect(context: QuicCCekContext): QuicConnectionInfo {
        // Update FSM state to handshake
        val handshakeContext = context.copy(fsmState = QuicFSMState.Handshake)
        activeContexts[context.connectionId] = handshakeContext

        try {
            // Simulate QUIC handshake
            val connectionInfo = QuicConnectionInfo(
                connectionId = context.connectionId,
                streamCount = 0u,
                isActive = true
            )

            connections[context.connectionId] = connectionInfo

            // Update to connected state
            val connectedContext = handshakeContext.copy(fsmState = QuicFSMState.Connected)
            activeContexts[context.connectionId] = connectedContext

            return connectionInfo

        } catch (e: Exception) {
            // Update to error state
            val errorContext = handshakeContext.copy(fsmState = QuicFSMState.Error)
            activeContexts[context.connectionId] = errorContext

            throw QuicProtocolException("QUIC connection failed: ${e.message}", e)
        }
    }

    suspend fun openStream(connectionId: String, streamId: UInt): QuicStreamChannel {
        val context = activeContexts[connectionId] ?: throw QuicProtocolException("Connection not found: $connectionId")

        // Update to stream open state
        val streamOpenContext = context.copy(fsmState = QuicFSMState.StreamOpen)
        activeContexts[connectionId] = streamOpenContext

        val dataChannel = Channel<QuicStreamData>()
        val streamChannel = QuicStreamChannel("stream-$streamId", streamId, dataChannel)

        streams[streamId] = streamChannel
        dataChannels[streamId] = dataChannel

        // Update connection info
        val connectionInfo = connections[connectionId]
        if (connectionInfo != null) {
            connections[connectionId] = connectionInfo.copy(streamCount = connectionInfo.streamCount + 1u)
        }

        return streamChannel
    }

    suspend fun sendData(streamId: UInt, data: ByteArray): Boolean {
        val stream = streams[streamId] ?: return false
        return stream.send(data)
    }

    suspend fun receiveData(streamId: UInt): ByteArray? {
        val stream = streams[streamId] ?: return null
        return stream.receive()
    }

    fun closeStream(streamId: UInt) {
        streams[streamId]?.close()
        streams.remove(streamId)
        dataChannels.remove(streamId)?.close()
    }

    fun closeConnection(connectionId: String) {
        val context = activeContexts[connectionId]
        if (context != null) {
            val closeContext = context.copy(fsmState = QuicFSMState.ConnectionClose)
            activeContexts[connectionId] = closeContext
        }

        // Close all streams for this connection
        streams.values.forEach { stream ->
            if (stream.channelId.startsWith("stream-")) {
                stream.close()
            }
        }

        connections.remove(connectionId)
        activeContexts.remove(connectionId)
    }

    fun getConnectionState(connectionId: String): QuicFSMState? {
        return activeContexts[connectionId]?.fsmState
    }

    fun getActiveStreams(connectionId: String): List<UInt> {
        return streams.keys.filter { streamId ->
            streams[streamId]?.channelId?.startsWith("stream-") == true
        }
    }

    fun closeAllConnections() {
        connections.keys.forEach { closeConnection(it) }
        streams.values.forEach { it.close() }
        dataChannels.values.forEach { it.close() }
        
        connections.clear()
        streams.clear()
        dataChannels.clear()
        activeContexts.clear()
    }
}

// ===== QUIC EXCEPTIONS =====

class QuicProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause) 