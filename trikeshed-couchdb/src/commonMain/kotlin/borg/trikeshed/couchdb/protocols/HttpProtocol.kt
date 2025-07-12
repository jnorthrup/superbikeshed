package borg.trikeshed.couchdb.protocols

import borg.trikeshed.lib.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

/**
 * HTTP Protocol Implementation
 * 
 * TDD Implementation of HTTP channelized client with FSM states and contexts
 * for CouchDB integration via TrikeShed channels.
 */

// ===== HTTP FSM STATES =====

enum class HttpFSMState {
    Initial,
    Connecting,
    Connected,
    Sending,
    Receiving,
    Completed,
    Error
}

// ===== HTTP CHANNELS =====

interface HttpChannel {
    val channelId: String
    val isActive: Boolean
    suspend fun send(data: ByteArray): Boolean
    suspend fun receive(): ByteArray?
    fun close()
}

class HttpRequestChannel(
    override val channelId: String,
    private val requestChannel: Channel<HttpRequest>
) : HttpChannel {
    override var isActive: Boolean = true
        private set

    override suspend fun send(data: ByteArray): Boolean {
        if (!isActive) return false
        val request = HttpRequest(data.decodeToString())
        return requestChannel.trySend(request).isSuccess
    }

    override suspend fun receive(): ByteArray? {
        if (!isActive) return null
        return try {
            val response = requestChannel.receive()
            response.data.encodeToByteArray()
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        isActive = false
        requestChannel.close()
    }
}

class HttpResponseChannel(
    override val channelId: String,
    private val responseChannel: Channel<HttpResponse>
) : HttpChannel {
    override var isActive: Boolean = true
        private set

    override suspend fun send(data: ByteArray): Boolean {
        if (!isActive) return false
        val response = HttpResponse(data.decodeToString())
        return responseChannel.trySend(response).isSuccess
    }

    override suspend fun receive(): ByteArray? {
        if (!isActive) return null
        return try {
            val response = responseChannel.receive()
            response.data.encodeToByteArray()
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        isActive = false
        responseChannel.close()
    }
}

// ===== HTTP DATA STRUCTURES =====

@Serializable
data class HttpRequest(
    val method: String = "GET",
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val data: String = "" // For backward compatibility
)

@Serializable
data class HttpResponse(
    val statusCode: Int = 200,
    val statusText: String = "OK",
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val data: String = "" // For backward compatibility
)

// ===== HTTP CCek CONTEXT =====

data class HttpCCekContext(
    val method: String,
    val url: String,
    val headers: Indexed<Map.Entry<String, String>>,
    val channels: Indexed<HttpChannel>,
    val fsmState: HttpFSMState = HttpFSMState.Initial,
    val requestId: String = generateRequestId(),
    val timeout: Long = 30000L
) {
    companion object {
        private fun generateRequestId(): String {
            return "http-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}-${kotlin.random.Random.nextInt(1000, 9999)}"
        }
    }
}

// ===== HTTP CHANNELIZED CLIENT =====

class HttpChannelizedClient {
    private val requestChannels = mutableMapOf<String, Channel<HttpRequest>>()
    private val responseChannels = mutableMapOf<String, Channel<HttpResponse>>()
    private val activeConnections = mutableMapOf<String, HttpCCekContext>()

    suspend fun sendRequest(context: HttpCCekContext): HttpResponse {
        // Update FSM state
        val updatedContext = context.copy(fsmState = HttpFSMState.Connecting)
        activeConnections[context.requestId] = updatedContext

        try {
            // Create channels if they don't exist
            val requestChannel = requestChannels.getOrPut(context.requestId) { Channel() }
            val responseChannel = responseChannels.getOrPut(context.requestId) { Channel() }

            // Create HTTP request
            val request = HttpRequest(
                method = context.method,
                url = context.url,
                headers = context.headers.toMap()
            )

            // Send request
            val sendingContext = updatedContext.copy(fsmState = HttpFSMState.Sending)
            activeConnections[context.requestId] = sendingContext

            requestChannel.send(request)

            // Wait for response
            val receivingContext = sendingContext.copy(fsmState = HttpFSMState.Receiving)
            activeConnections[context.requestId] = receivingContext

            val response = responseChannel.receive()

            // Update to completed state
            val completedContext = receivingContext.copy(fsmState = HttpFSMState.Completed)
            activeConnections[context.requestId] = completedContext

            return response

        } catch (e: Exception) {
            // Update to error state
            val errorContext = updatedContext.copy(fsmState = HttpFSMState.Error)
            activeConnections[context.requestId] = errorContext

            throw HttpProtocolException("HTTP request failed: ${e.message}", e)
        }
    }

    suspend fun createConnection(url: String): HttpCCekContext {
        val context = HttpCCekContext(
            method = "GET",
            url = url,
            headers = emptyMap<Map.Entry<String, String>>().size j { i: Int -> emptyMap<Map.Entry<String, String>>().entries.toList()[i] },
            channels = emptyList<HttpChannel>().size j { i: Int -> emptyList<HttpChannel>()[i] },
            fsmState = HttpFSMState.Connecting
        )

        activeConnections[context.requestId] = context
        return context
    }

    fun getConnectionState(requestId: String): HttpFSMState? {
        return activeConnections[requestId]?.fsmState
    }

    fun closeConnection(requestId: String) {
        activeConnections.remove(requestId)
        requestChannels.remove(requestId)?.close()
        responseChannels.remove(requestId)?.close()
    }

    fun closeAllConnections() {
        activeConnections.clear()
        requestChannels.values.forEach { it.close() }
        responseChannels.values.forEach { it.close() }
        requestChannels.clear()
        responseChannels.clear()
    }
}

// ===== HTTP EXCEPTIONS =====

class HttpProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause) 