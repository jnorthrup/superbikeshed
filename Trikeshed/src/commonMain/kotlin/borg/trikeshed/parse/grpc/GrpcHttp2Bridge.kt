package borg.trikeshed.parse.grpc

import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import borg.trikeshed.net.quic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * gRPC to HTTP/2 Bridge
 * Adapts gRPC protocol to existing HTTP/2 implementation
 */
class GrpcHttp2Bridge(
    private val http2Connection: Http2Protocol.Http2Connection
) {
    
    /**
     * gRPC Message Framing
     * 5-byte prefix: 1 byte compression flag + 4 bytes message length
     */
    private fun frameGrpcMessage(message: Indexed<Byte>, compressed: Boolean = false): Indexed<Byte> {
        val frame = ByteArray(5 + message.a)
        
        // Compression flag
        frame[0] = if (compressed) 1 else 0
        
        // Message length (big-endian)
        val length = message.a
        frame[1] = (length shr 24).toByte()
        frame[2] = (length shr 16).toByte()
        frame[3] = (length shr 8).toByte()
        frame[4] = length.toByte()
        
        // Copy message
        for (i in 0 until message.a) {
            frame[5 + i] = message.b(i)
        }
        
        return frame.size j { i -> frame[i] }
    }
    
    /**
     * Extract gRPC message from framed data
     */
    private fun unframeGrpcMessage(data: Indexed<Byte>): GrpcMessage? {
        if (data.a < 5) return null
        
        val compressed = data.b(0) != 0.toByte()
        val length = ((data.b(1).toInt() and 0xFF) shl 24) or
                    ((data.b(2).toInt() and 0xFF) shl 16) or
                    ((data.b(3).toInt() and 0xFF) shl 8) or
                    (data.b(4).toInt() and 0xFF)
        
        if (data.a < 5 + length) return null
        
        val message = length j { i: Int -> data.b(5 + i) }
        return GrpcMessage(message, compressed)
    }
    
    /**
     * Send gRPC unary request
     */
    suspend fun sendUnaryRequest(
        path: String,
        message: ProtoMessage,
        metadata: Map<String, String> = emptyMap()
    ): GrpcResponse {
        // Create HTTP/2 stream
        val stream = http2Connection.createStream()
        
        // Build headers
        val headers = buildGrpcHeaders(path, metadata)
        
        // Send HEADERS frame
        http2Connection.sendHeaders(stream.id, headers, endStream = false)
        
        // Encode and frame the message
        val wireBytes = message.encode()
        val framedMessage = frameGrpcMessage(wireBytes)
        
        // Send DATA frame
        http2Connection.sendData(stream.id, framedMessage, endStream = true)
        
        // Wait for response (simplified - real implementation would use channels)
        return GrpcResponse(
            status = GrpcStatus.OK,
            message = null,
            trailers = emptyMap()
        )
    }
    
    /**
     * Send gRPC server streaming request
     */
    suspend fun sendServerStreamingRequest(
        path: String,
        message: ProtoMessage,
        metadata: Map<String, String> = emptyMap()
    ): Flow<GrpcMessage> = channelFlow {
        val stream = http2Connection.createStream()
        val headers = buildGrpcHeaders(path, metadata)
        
        http2Connection.sendHeaders(stream.id, headers, endStream = false)
        
        val wireBytes = message.encode()
        val framedMessage = frameGrpcMessage(wireBytes)
        
        http2Connection.sendData(stream.id, framedMessage, endStream = true)
        
        // Emit messages as they arrive (simplified)
        // Real implementation would listen to stream events
    }
    
    /**
     * Send gRPC client streaming request
     */
    fun sendClientStreamingRequest(
        path: String,
        metadata: Map<String, String> = emptyMap()
    ): GrpcClientStream {
        val stream = http2Connection.createStream()
        val headers = buildGrpcHeaders(path, metadata)
        
        runBlocking {
            http2Connection.sendHeaders(stream.id, headers, endStream = false)
        }
        
        return GrpcClientStream(stream.id, http2Connection)
    }
    
    /**
     * Build gRPC headers for HTTP/2
     */
    private fun buildGrpcHeaders(
        path: String,
        metadata: Map<String, String>
    ): Indexed<Join<HttpHeaderName, HttpHeaderValue>> {
        val headers = mutableListOf<Join<HttpHeaderName, HttpHeaderValue>>()
        
        // Required gRPC headers
        headers.add(HttpHeaderName(":method") j HttpHeaderValue("POST"))
        headers.add(HttpHeaderName(":scheme") j HttpHeaderValue("https"))
        headers.add(HttpHeaderName(":path") j HttpHeaderValue(path))
        headers.add(HttpHeaderName("content-type") j HttpHeaderValue("application/grpc+proto"))
        headers.add(HttpHeaderName("te") j HttpHeaderValue("trailers"))
        
        // Add custom metadata
        metadata.forEach { (key, value) ->
            headers.add(HttpHeaderName(key.lowercase()) j HttpHeaderValue(value))
        }
        
        val headerArray = headers.toTypedArray()
        return headerArray.size j headerArray::get
    }
    
    /**
     * Parse gRPC status from trailers
     */
    private fun parseGrpcStatus(
        trailers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>
    ): GrpcStatus {
        for (i in 0 until trailers.a) {
            val trailer = trailers.b(i)
            if (trailer.a.value == "grpc-status") {
                val code = trailer.b.value.toIntOrNull() ?: 2
                return GrpcStatus.fromCode(code)
            }
        }
        return GrpcStatus.UNKNOWN
    }
}

/**
 * gRPC Message wrapper
 */
data class GrpcMessage(
    val data: Indexed<Byte>,
    val compressed: Boolean
)

/**
 * gRPC Response
 */
data class GrpcResponse(
    val status: GrpcStatus,
    val message: ProtoMessage?,
    val trailers: Map<String, String>
)

/**
 * gRPC Status codes
 */
enum class GrpcStatus(val code: Int) {
    OK(0),
    CANCELLED(1),
    UNKNOWN(2),
    INVALID_ARGUMENT(3),
    DEADLINE_EXCEEDED(4),
    NOT_FOUND(5),
    ALREADY_EXISTS(6),
    PERMISSION_DENIED(7),
    RESOURCE_EXHAUSTED(8),
    FAILED_PRECONDITION(9),
    ABORTED(10),
    OUT_OF_RANGE(11),
    UNIMPLEMENTED(12),
    INTERNAL(13),
    UNAVAILABLE(14),
    DATA_LOSS(15),
    UNAUTHENTICATED(16);
    
    companion object {
        fun fromCode(code: Int): GrpcStatus = 
            values().find { it.code == code } ?: UNKNOWN
    }
}

/**
 * gRPC Client Stream for client streaming and bidirectional streaming
 */
class GrpcClientStream(
    private val streamId: Int,
    private val http2Connection: Http2Protocol.Http2Connection
) {
    suspend fun send(message: ProtoMessage) {
        val wireBytes = message.encode()
        val framedMessage = frameGrpcMessage(wireBytes)
        http2Connection.sendData(streamId, framedMessage, endStream = false)
    }
    
    suspend fun complete() {
        // Send empty DATA frame with END_STREAM flag
        http2Connection.sendData(streamId, emptyIndexed(), endStream = true)
    }
    
    private fun frameGrpcMessage(message: Indexed<Byte>, compressed: Boolean = false): Indexed<Byte> {
        val frame = ByteArray(5 + message.a)
        frame[0] = if (compressed) 1 else 0
        val length = message.a
        frame[1] = (length shr 24).toByte()
        frame[2] = (length shr 16).toByte()
        frame[3] = (length shr 8).toByte()
        frame[4] = length.toByte()
        for (i in 0 until message.a) {
            frame[5 + i] = message.b(i)
        }
        return frame.size j { i -> frame[i] }
    }
}

/**
 * Extension functions
 */
suspend fun Http2Protocol.Http2Connection.grpcUnaryCall(
    path: String,
    request: ProtoMessage,
    metadata: Map<String, String> = emptyMap()
): GrpcResponse {
    val bridge = GrpcHttp2Bridge(this)
    return bridge.sendUnaryRequest(path, request, metadata)
}

/**
 * Example usage
 */
object GrpcHttp2Example {
    suspend fun demonstrateGrpcOverHttp2(quicConnection: QuicConnection) {
        // Create HTTP/2 connection
        val http2 = Http2Protocol.Http2Connection(quicConnection, isServer = false)
        http2.sendPreface()
        
        // Create gRPC message
        val request = ProtoMessageBuilder()
            .addString(1, "Hello")
            .addInt32(2, 42)
            .build()
        
        // Make gRPC call
        val response = http2.grpcUnaryCall(
            path = "/myservice.MyService/MyMethod",
            request = request,
            metadata = mapOf(
                "authorization" to "Bearer token123",
                "x-request-id" to "req-456"
            )
        )
        
        println("gRPC Status: ${response.status}")
    }
}