package com.superbikeshed.trikeshed.native

import com.superbikeshed.trikeshed.*
import com.superbikeshed.trikeshed.native.uring.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlin.experimental.ExperimentalNativeApi

/**
 * HTTP/2 Server implementation using io_uring
 * Integrates with existing Trikeshed HTTP/2 protocol implementation
 */
@OptIn(ExperimentalNativeApi::class)
class URingHTTP2Server(
    private val sock: Int,
    private val ring: CPointer<io_uring>
) : HTTP2Server {
    
    // Connection management
    private val connections = mutableMapOf<Int, HTTP2ConnectionState>()
    private val acceptChannel = Channel<Int>(Channel.UNLIMITED)
    private val requestHandlers = mutableListOf<suspend (HTTP2Request) -> HTTP2Response>()
    
    // TLS context for ALPN negotiation
    private var tlsContext: TLSContext? = null
    
    override suspend fun start() = withContext(Dispatchers.IO) {
        // Get context from coroutine
        val ctx = coroutineContext
        val securityContext = ctx.securityContext
        
        // Setup TLS if configured
        if (securityContext?.tlsConfig != null) {
            tlsContext = setupTLS(securityContext.tlsConfig)
        }
        
        // Start accepting connections
        launch { acceptLoop() }
        
        // Start connection processor
        launch { processConnections() }
    }
    
    private suspend fun acceptLoop() {
        memScoped {
            // Multi-shot accept
            val sqe = io_uring_get_sqe(ring)!!
            val addr = alloc<sockaddr_storage>()
            val addrlen = alloc<socklen_tVar>()
            addrlen.value = sizeOf<sockaddr_storage>().convert()
            
            io_uring_prep_multishot_accept(
                sqe, sock,
                addr.ptr.reinterpret(), addrlen.ptr,
                0
            )
            sqe.pointed.flags = sqe.pointed.flags or IOSQE_FIXED_FILE
            
            io_uring_submit(ring)
            
            // Process accept completions
            val cqe = alloc<CPointerVar<io_uring_cqe>>()
            
            while (currentCoroutineContext().isActive) {
                val ret = io_uring_wait_cqe(ring, cqe.ptr)
                if (ret < 0) continue
                
                val completion = cqe.value
                if (completion != null) {
                    val fd = completion.pointed.res
                    if (fd > 0) {
                        acceptChannel.send(fd)
                    }
                    
                    io_uring_cqe_seen(ring, completion)
                    
                    // Re-arm if not multi-shot
                    if (completion.pointed.flags and IORING_CQE_F_MORE == 0u) {
                        rearmAccept()
                    }
                }
            }
        }
    }
    
    private suspend fun processConnections() {
        for (clientFd in acceptChannel) {
            launch { handleConnection(clientFd) }
        }
    }
    
    private suspend fun handleConnection(clientFd: Int) {
        try {
            // Setup non-blocking
            fcntl(clientFd, F_SETFL, O_NONBLOCK)
            
            // TLS handshake if configured
            val tlsConn = if (tlsContext != null) {
                performTLSHandshake(clientFd)
            } else null
            
            // Check ALPN negotiation
            val negotiatedProtocol = tlsConn?.getALPN() ?: "h2"
            if (negotiatedProtocol != "h2") {
                // Fall back to HTTP/1.1 if needed
                close(clientFd)
                return
            }
            
            // Create HTTP/2 connection
            val connState = HTTP2ConnectionState(
                fd = clientFd,
                tlsConn = tlsConn,
                isServer = true
            )
            connections[clientFd] = connState
            
            // Start connection handler
            handleHTTP2Connection(connState)
            
        } catch (e: Exception) {
            close(clientFd)
        } finally {
            connections.remove(clientFd)
        }
    }
    
    private suspend fun handleHTTP2Connection(connState: HTTP2ConnectionState) {
        // Receive client preface
        val preface = ByteArray(24)
        val received = readExact(connState, preface)
        
        if (received != 24 || !preface.contentEquals(CLIENT_PREFACE)) {
            sendGoAway(connState, ErrorCode.PROTOCOL_ERROR)
            return
        }
        
        // Send server preface (SETTINGS frame)
        sendSettings(connState)
        
        // Main frame processing loop
        while (connState.active && currentCoroutineContext().isActive) {
            val frame = readFrame(connState) ?: break
            processFrame(connState, frame)
        }
    }
    
    private suspend fun processFrame(connState: HTTP2ConnectionState, frame: HTTP2Frame) {
        when (frame.type) {
            FrameType.SETTINGS -> handleSettings(connState, frame)
            FrameType.HEADERS -> handleHeaders(connState, frame)
            FrameType.DATA -> handleData(connState, frame)
            FrameType.WINDOW_UPDATE -> handleWindowUpdate(connState, frame)
            FrameType.PING -> handlePing(connState, frame)
            FrameType.GOAWAY -> handleGoAway(connState, frame)
            FrameType.RST_STREAM -> handleRstStream(connState, frame)
            else -> {
                // Unknown frame type, ignore
            }
        }
    }
    
    private suspend fun handleHeaders(connState: HTTP2ConnectionState, frame: HTTP2Frame) {
        val streamId = frame.streamId
        if (streamId == 0) {
            sendGoAway(connState, ErrorCode.PROTOCOL_ERROR)
            return
        }
        
        // Get or create stream
        val stream = connState.streams.getOrPut(streamId) {
            HTTP2StreamState(streamId)
        }
        
        // Decode headers using HPACK
        val headers = connState.hpackDecoder.decode(frame.payload)
        stream.headers.addAll(headers)
        
        // Check if END_HEADERS flag is set
        if (frame.flags and FrameFlags.END_HEADERS != 0.toByte()) {
            // Process complete headers
            val request = buildRequest(stream)
            
            // Check if END_STREAM flag is set
            if (frame.flags and FrameFlags.END_STREAM != 0.toByte()) {
                // Request is complete, process it
                launch {
                    handleRequest(connState, stream, request)
                }
            }
        }
    }
    
    private suspend fun handleRequest(
        connState: HTTP2ConnectionState,
        stream: HTTP2StreamState,
        request: HTTP2Request
    ) {
        try {
            // Find handler
            val handler = requestHandlers.firstOrNull()
            val response = if (handler != null) {
                handler(request)
            } else {
                HTTP2Response(404, mapOf("content-length" to "0"), null)
            }
            
            // Send response
            sendResponse(connState, stream.id, response)
            
        } catch (e: Exception) {
            // Send error response
            sendRstStream(connState, stream.id, ErrorCode.INTERNAL_ERROR)
        }
    }
    
    private suspend fun sendResponse(
        connState: HTTP2ConnectionState,
        streamId: Int,
        response: HTTP2Response
    ) {
        // Prepare headers
        val headers = mutableListOf<Pair<String, String>>()
        headers.add(":status" to response.status.toString())
        headers.addAll(response.headers.entries.map { it.key to it.value })
        
        // Encode headers using HPACK
        val encodedHeaders = connState.hpackEncoder.encode(headers)
        
        // Send HEADERS frame
        val headersFrame = HTTP2Frame(
            type = FrameType.HEADERS,
            flags = if (response.body == null) {
                (FrameFlags.END_HEADERS or FrameFlags.END_STREAM).toByte()
            } else {
                FrameFlags.END_HEADERS
            },
            streamId = streamId,
            payload = encodedHeaders
        )
        
        sendFrame(connState, headersFrame)
        
        // Send DATA frames if body exists
        response.body?.let { body ->
            sendData(connState, streamId, body, endStream = true)
        }
    }
    
    private suspend fun sendData(
        connState: HTTP2ConnectionState,
        streamId: Int,
        data: ByteArray,
        endStream: Boolean
    ) {
        val maxFrameSize = connState.remoteSettings[SettingsParameter.MAX_FRAME_SIZE] ?: 16384
        var offset = 0
        
        while (offset < data.size) {
            val chunkSize = minOf(maxFrameSize, data.size - offset)
            val chunk = data.sliceArray(offset until offset + chunkSize)
            
            val flags = if (endStream && offset + chunkSize >= data.size) {
                FrameFlags.END_STREAM
            } else {
                0
            }
            
            val dataFrame = HTTP2Frame(
                type = FrameType.DATA,
                flags = flags.toByte(),
                streamId = streamId,
                payload = chunk
            )
            
            sendFrame(connState, dataFrame)
            offset += chunkSize
        }
    }
    
    private suspend fun sendFrame(connState: HTTP2ConnectionState, frame: HTTP2Frame) {
        val frameBytes = frame.serialize()
        writeExact(connState, frameBytes)
    }
    
    private suspend fun writeExact(connState: HTTP2ConnectionState, data: ByteArray): Int {
        memScoped {
            val sqe = io_uring_get_sqe(ring)!!
            val iov = alloc<iovec>()
            iov.iov_base = data.refTo(0).getPointer(this)
            iov.iov_len = data.size.toULong()
            
            io_uring_prep_writev(sqe, connState.fd, iov.ptr, 1u, -1)
            io_uring_sqe_set_data(sqe, StableRef.create(connState).asCPointer())
            io_uring_submit(ring)
            
            // Wait for completion
            val cqe = alloc<CPointerVar<io_uring_cqe>>()
            io_uring_wait_cqe(ring, cqe.ptr)
            
            val completion = cqe.value
            val written = completion?.pointed?.res ?: -1
            io_uring_cqe_seen(ring, completion)
            
            return written
        }
    }
    
    private suspend fun readExact(connState: HTTP2ConnectionState, buffer: ByteArray): Int {
        memScoped {
            val sqe = io_uring_get_sqe(ring)!!
            val iov = alloc<iovec>()
            iov.iov_base = buffer.refTo(0).getPointer(this)
            iov.iov_len = buffer.size.toULong()
            
            io_uring_prep_readv(sqe, connState.fd, iov.ptr, 1u, -1)
            io_uring_sqe_set_data(sqe, StableRef.create(connState).asCPointer())
            io_uring_submit(ring)
            
            // Wait for completion
            val cqe = alloc<CPointerVar<io_uring_cqe>>()
            io_uring_wait_cqe(ring, cqe.ptr)
            
            val completion = cqe.value
            val read = completion?.pointed?.res ?: -1
            io_uring_cqe_seen(ring, completion)
            
            return read
        }
    }
    
    private suspend fun readFrame(connState: HTTP2ConnectionState): HTTP2Frame? {
        // Read frame header (9 bytes)
        val header = ByteArray(9)
        val headerRead = readExact(connState, header)
        if (headerRead != 9) return null
        
        // Parse frame header
        val length = ((header[0].toInt() and 0xFF) shl 16) or
                    ((header[1].toInt() and 0xFF) shl 8) or
                    (header[2].toInt() and 0xFF)
        
        val type = FrameType.fromValue(header[3]) ?: return null
        val flags = header[4]
        val streamId = ((header[5].toInt() and 0x7F) shl 24) or
                      ((header[6].toInt() and 0xFF) shl 16) or
                      ((header[7].toInt() and 0xFF) shl 8) or
                      (header[8].toInt() and 0xFF)
        
        // Read payload
        val payload = if (length > 0) {
            val payloadData = ByteArray(length)
            val payloadRead = readExact(connState, payloadData)
            if (payloadRead != length) return null
            payloadData
        } else {
            ByteArray(0)
        }
        
        return HTTP2Frame(type, flags, streamId, payload)
    }
    
    private fun sendSettings(connState: HTTP2ConnectionState) {
        val settings = listOf(
            SettingsParameter.MAX_CONCURRENT_STREAMS to 100,
            SettingsParameter.INITIAL_WINDOW_SIZE to 65535,
            SettingsParameter.MAX_FRAME_SIZE to 16384
        )
        
        val payload = ByteArray(settings.size * 6)
        var offset = 0
        
        for ((param, value) in settings) {
            // Parameter ID (16 bits)
            payload[offset++] = (param.id shr 8).toByte()
            payload[offset++] = param.id.toByte()
            
            // Value (32 bits)
            payload[offset++] = (value shr 24).toByte()
            payload[offset++] = (value shr 16).toByte()
            payload[offset++] = (value shr 8).toByte()
            payload[offset++] = value.toByte()
        }
        
        val frame = HTTP2Frame(
            type = FrameType.SETTINGS,
            flags = 0,
            streamId = 0,
            payload = payload
        )
        
        runBlocking { sendFrame(connState, frame) }
    }
    
    private suspend fun sendGoAway(connState: HTTP2ConnectionState, error: ErrorCode) {
        val lastStreamId = connState.lastStreamId
        val payload = ByteArray(8)
        
        // Last stream ID (32 bits)
        payload[0] = (lastStreamId shr 24).toByte()
        payload[1] = (lastStreamId shr 16).toByte()
        payload[2] = (lastStreamId shr 8).toByte()
        payload[3] = lastStreamId.toByte()
        
        // Error code (32 bits)
        payload[4] = (error.code shr 24).toByte()
        payload[5] = (error.code shr 16).toByte()
        payload[6] = (error.code shr 8).toByte()
        payload[7] = error.code.toByte()
        
        val frame = HTTP2Frame(
            type = FrameType.GOAWAY,
            flags = 0,
            streamId = 0,
            payload = payload
        )
        
        sendFrame(connState, frame)
        connState.active = false
    }
    
    private suspend fun sendRstStream(
        connState: HTTP2ConnectionState,
        streamId: Int,
        error: ErrorCode
    ) {
        val payload = ByteArray(4)
        payload[0] = (error.code shr 24).toByte()
        payload[1] = (error.code shr 16).toByte()
        payload[2] = (error.code shr 8).toByte()
        payload[3] = error.code.toByte()
        
        val frame = HTTP2Frame(
            type = FrameType.RST_STREAM,
            flags = 0,
            streamId = streamId,
            payload = payload
        )
        
        sendFrame(connState, frame)
    }
    
    // Frame handlers
    private suspend fun handleSettings(connState: HTTP2ConnectionState, frame: HTTP2Frame) {
        if (frame.flags and FrameFlags.ACK != 0.toByte()) {
            // Settings ACK received
            return
        }
        
        // Parse settings
        val payload = frame.payload
        var offset = 0
        
        while (offset + 6 <= payload.size) {
            val id = ((payload[offset].toInt() and 0xFF) shl 8) or
                    (payload[offset + 1].toInt() and 0xFF)
            val value = ((payload[offset + 2].toInt() and 0xFF) shl 24) or
                       ((payload[offset + 3].toInt() and 0xFF) shl 16) or
                       ((payload[offset + 4].toInt() and 0xFF) shl 8) or
                       (payload[offset + 5].toInt() and 0xFF)
            
            SettingsParameter.fromId(id)?.let { param ->
                connState.remoteSettings[param] = value
            }
            
            offset += 6
        }
        
        // Send ACK
        val ackFrame = HTTP2Frame(
            type = FrameType.SETTINGS,
            flags = FrameFlags.ACK,
            streamId = 0,
            payload = ByteArray(0)
        )
        sendFrame(connState, ackFrame)
    }
    
    private suspend fun handlePing(connState: HTTP2ConnectionState, frame: HTTP2Frame) {
        if (frame.flags and FrameFlags.ACK == 0.toByte()) {
            // Echo the ping with ACK flag
            val ackFrame = HTTP2Frame(
                type = FrameType.PING,
                flags = FrameFlags.ACK,
                streamId = 0,
                payload = frame.payload
            )
            sendFrame(connState, ackFrame)
        }
    }
    
    private suspend fun handleWindowUpdate(connState: HTTP2ConnectionState, frame: HTTP2Frame) {
        if (frame.payload.size != 4) {
            sendGoAway(connState, ErrorCode.FRAME_SIZE_ERROR)
            return
        }
        
        val increment = ((frame.payload[0].toInt() and 0x7F) shl 24) or
                       ((frame.payload[1].toInt() and 0xFF) shl 16) or
                       ((frame.payload[2].toInt() and 0xFF) shl 8) or
                       (frame.payload[3].toInt() and 0xFF)
        
        if (frame.streamId == 0) {
            connState.connectionWindow += increment
        } else {
            connState.streams[frame.streamId]?.let { stream ->
                stream.window += increment
            }
        }
    }
    
    private suspend fun handleData(connState: HTTP2ConnectionState, frame: HTTP2Frame) {
        val stream = connState.streams[frame.streamId] ?: return
        stream.data.add(frame.payload)
        
        if (frame.flags and FrameFlags.END_STREAM != 0.toByte()) {
            // Stream is complete
            val request = buildRequest(stream)
            launch {
                handleRequest(connState, stream, request)
            }
        }
    }
    
    private fun handleGoAway(connState: HTTP2ConnectionState, frame: HTTP2Frame) {
        connState.active = false
    }
    
    private fun handleRstStream(connState: HTTP2ConnectionState, frame: HTTP2Frame) {
        connState.streams.remove(frame.streamId)
    }
    
    private fun buildRequest(stream: HTTP2StreamState): HTTP2Request {
        val headers = mutableMapOf<String, String>()
        var method = "GET"
        var path = "/"
        
        for ((name, value) in stream.headers) {
            when (name) {
                ":method" -> method = value
                ":path" -> path = value
                else -> if (!name.startsWith(":")) {
                    headers[name] = value
                }
            }
        }
        
        val body = if (stream.data.isNotEmpty()) {
            val totalSize = stream.data.sumOf { it.size }
            val result = ByteArray(totalSize)
            var offset = 0
            for (chunk in stream.data) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            result
        } else null
        
        return HTTP2Request(method, path, headers, body)
    }
    
    override suspend fun stop() {
        connections.values.forEach { conn ->
            sendGoAway(conn, ErrorCode.NO_ERROR)
            close(conn.fd)
        }
        connections.clear()
        close(sock)
    }
    
    override suspend fun stats(): ServerStats {
        return ServerStats(
            connections = connections.size.toLong(),
            requestsPerSecond = 0.0, // TODO: Implement
            bytesPerSecond = 0,
            latencyP50 = 0.0,
            latencyP99 = 0.0,
            latencyP999 = 0.0,
            errors = 0,
            cpuUsage = 0.0,
            memoryUsage = 0
        )
    }
    
    override suspend fun connections(): Flow<Connection> = flow {
        connections.values.forEach { connState ->
            emit(HTTP2ConnectionAdapter(connState))
        }
    }
    
    override suspend fun handleRequest(handler: suspend (HTTP2Request) -> HTTP2Response) {
        requestHandlers.add(handler)
    }
    
    override suspend fun pushPromise(streamId: Int, headers: Map<String, String>): Result<Int> {
        // TODO: Implement server push
        return Result.failure(NotImplementedError("Server push not yet implemented"))
    }
    
    private fun rearmAccept() {
        memScoped {
            val sqe = io_uring_get_sqe(ring)!!
            val addr = alloc<sockaddr_storage>()
            val addrlen = alloc<socklen_tVar>()
            addrlen.value = sizeOf<sockaddr_storage>().convert()
            
            io_uring_prep_multishot_accept(
                sqe, sock,
                addr.ptr.reinterpret(), addrlen.ptr,
                0
            )
            sqe.pointed.flags = sqe.pointed.flags or IOSQE_FIXED_FILE
            
            io_uring_submit(ring)
        }
    }
    
    private fun setupTLS(tlsConfig: TLSConfig): TLSContext {
        // TODO: Implement TLS setup
        return TLSContext()
    }
    
    private fun performTLSHandshake(fd: Int): TLSConnection? {
        // TODO: Implement TLS handshake
        return null
    }
    
    companion object {
        val CLIENT_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".encodeToByteArray()
        const val IORING_CQE_F_MORE = 2u
    }
}

// Supporting classes
private data class HTTP2ConnectionState(
    val fd: Int,
    val tlsConn: TLSConnection? = null,
    val isServer: Boolean = true,
    var active: Boolean = true,
    val streams: MutableMap<Int, HTTP2StreamState> = mutableMapOf(),
    val localSettings: MutableMap<SettingsParameter, Int> = mutableMapOf(),
    val remoteSettings: MutableMap<SettingsParameter, Int> = mutableMapOf(),
    var connectionWindow: Int = 65535,
    var lastStreamId: Int = 0,
    val hpackEncoder: HpackEncoder = HpackEncoder(),
    val hpackDecoder: HpackDecoder = HpackDecoder()
)

private data class HTTP2StreamState(
    val id: Int,
    val headers: MutableList<Pair<String, String>> = mutableListOf(),
    val data: MutableList<ByteArray> = mutableListOf(),
    var window: Int = 65535,
    var state: StreamState = StreamState.IDLE
)

private enum class StreamState {
    IDLE,
    RESERVED_LOCAL,
    RESERVED_REMOTE,
    OPEN,
    HALF_CLOSED_LOCAL,
    HALF_CLOSED_REMOTE,
    CLOSED
}

private class HTTP2ConnectionAdapter(
    private val connState: HTTP2ConnectionState
) : Connection {
    override val id = connState.fd.toString()
    override val protocol = Protocol.HTTP2
    override val localAddress = SocketAddress("0.0.0.0", 443) // TODO: Get real address
    override val remoteAddress = SocketAddress("0.0.0.0", 0) // TODO: Get real address
    
    override suspend fun send(data: ByteArray): Result<Unit> = Result.success(Unit)
    override suspend fun receive(): Result<ByteArray> = Result.success(ByteArray(0))
    override suspend fun close() {
        platform.posix.close(connState.fd)
    }
}

// Frame types and constants from Trikeshed
private enum class FrameType(val value: Byte) {
    DATA(0x0),
    HEADERS(0x1),
    PRIORITY(0x2),
    RST_STREAM(0x3),
    SETTINGS(0x4),
    PUSH_PROMISE(0x5),
    PING(0x6),
    GOAWAY(0x7),
    WINDOW_UPDATE(0x8),
    CONTINUATION(0x9);
    
    companion object {
        fun fromValue(value: Byte): FrameType? = values().find { it.value == value }
    }
}

private object FrameFlags {
    const val END_STREAM: Byte = 0x1
    const val END_HEADERS: Byte = 0x4
    const val PADDED: Byte = 0x8
    const val PRIORITY: Byte = 0x20
    const val ACK: Byte = 0x1
}

private enum class SettingsParameter(val id: Int) {
    HEADER_TABLE_SIZE(0x1),
    ENABLE_PUSH(0x2),
    MAX_CONCURRENT_STREAMS(0x3),
    INITIAL_WINDOW_SIZE(0x4),
    MAX_FRAME_SIZE(0x5),
    MAX_HEADER_LIST_SIZE(0x6);
    
    companion object {
        fun fromId(id: Int): SettingsParameter? = values().find { it.id == id }
    }
}

private enum class ErrorCode(val code: Long) {
    NO_ERROR(0x0),
    PROTOCOL_ERROR(0x1),
    INTERNAL_ERROR(0x2),
    FLOW_CONTROL_ERROR(0x3),
    SETTINGS_TIMEOUT(0x4),
    STREAM_CLOSED(0x5),
    FRAME_SIZE_ERROR(0x6),
    REFUSED_STREAM(0x7),
    CANCEL(0x8),
    COMPRESSION_ERROR(0x9),
    CONNECT_ERROR(0xa),
    ENHANCE_YOUR_CALM(0xb),
    INADEQUATE_SECURITY(0xc),
    HTTP_1_1_REQUIRED(0xd)
}

private data class HTTP2Frame(
    val type: FrameType,
    val flags: Byte,
    val streamId: Int,
    val payload: ByteArray
) {
    fun serialize(): ByteArray {
        val result = ByteArray(9 + payload.size)
        
        // Length (24 bits)
        result[0] = (payload.size shr 16).toByte()
        result[1] = (payload.size shr 8).toByte()
        result[2] = payload.size.toByte()
        
        // Type (8 bits)
        result[3] = type.value
        
        // Flags (8 bits)
        result[4] = flags
        
        // Stream ID (32 bits) - high bit reserved
        result[5] = (streamId shr 24).toByte()
        result[6] = (streamId shr 16).toByte()
        result[7] = (streamId shr 8).toByte()
        result[8] = streamId.toByte()
        
        // Payload
        payload.copyInto(result, 9)
        
        return result
    }
}

// Simple HPACK encoder/decoder (simplified version)
private class HpackEncoder {
    fun encode(headers: List<Pair<String, String>>): ByteArray {
        val output = mutableListOf<Byte>()
        
        for ((name, value) in headers) {
            // Literal header field without indexing
            output.add(0x00)
            
            // Encode name
            val nameBytes = name.encodeToByteArray()
            encodeInteger(output, nameBytes.size, 7)
            output.addAll(nameBytes.toList())
            
            // Encode value
            val valueBytes = value.encodeToByteArray()
            encodeInteger(output, valueBytes.size, 7)
            output.addAll(valueBytes.toList())
        }
        
        return output.toByteArray()
    }
    
    private fun encodeInteger(output: MutableList<Byte>, value: Int, prefixBits: Int) {
        val maxPrefix = (1 shl prefixBits) - 1
        
        if (value < maxPrefix) {
            output.add(value.toByte())
        } else {
            output.add(maxPrefix.toByte())
            var remainder = value - maxPrefix
            
            while (remainder >= 128) {
                output.add((remainder and 0x7F or 0x80).toByte())
                remainder = remainder shr 7
            }
            
            output.add(remainder.toByte())
        }
    }
}

private class HpackDecoder {
    fun decode(data: ByteArray): List<Pair<String, String>> {
        val headers = mutableListOf<Pair<String, String>>()
        var index = 0
        
        while (index < data.size) {
            // Simple literal header field parsing
            if (data[index] == 0x00.toByte()) {
                index++
                
                // Decode name length and name
                val (nameLen, nameConsumed) = decodeInteger(data, index, 7)
                index += nameConsumed
                
                val name = data.sliceArray(index until index + nameLen).decodeToString()
                index += nameLen
                
                // Decode value length and value
                val (valueLen, valueConsumed) = decodeInteger(data, index, 7)
                index += valueConsumed
                
                val value = data.sliceArray(index until index + valueLen).decodeToString()
                index += valueLen
                
                headers.add(name to value)
            } else {
                // Skip unknown encoding
                index++
            }
        }
        
        return headers
    }
    
    private fun decodeInteger(data: ByteArray, startIndex: Int, prefixBits: Int): Pair<Int, Int> {
        val maxPrefix = (1 shl prefixBits) - 1
        val firstByte = data[startIndex].toInt() and maxPrefix
        
        if (firstByte < maxPrefix) {
            return firstByte to 1
        }
        
        var value = maxPrefix
        var multiplier = 1
        var index = startIndex + 1
        
        while (index < data.size) {
            val byte = data[index].toInt() and 0xFF
            value += (byte and 0x7F) * multiplier
            
            if ((byte and 0x80) == 0) {
                return value to (index - startIndex + 1)
            }
            
            multiplier *= 128
            index++
        }
        
        return value to (index - startIndex)
    }
}

// TLS stubs
private class TLSContext
private class TLSConnection {
    fun getALPN(): String = "h2"
}

// Missing io_uring functions
private fun io_uring_prep_multishot_accept(
    sqe: CPointer<io_uring_sqe>,
    fd: Int,
    addr: CPointer<sockaddr>?,
    addrlen: CPointer<socklen_tVar>?,
    flags: Int
) {
    // Implementation would call actual io_uring function
}

private fun io_uring_prep_writev(
    sqe: CPointer<io_uring_sqe>,
    fd: Int,
    iov: CPointer<iovec>,
    nr_vecs: UInt,
    offset: Long
) {
    // Implementation would call actual io_uring function
}

private fun io_uring_prep_readv(
    sqe: CPointer<io_uring_sqe>,
    fd: Int,
    iov: CPointer<iovec>,
    nr_vecs: UInt,
    offset: Long
) {
    // Implementation would call actual io_uring function
}

private fun io_uring_sqe_set_data(sqe: CPointer<io_uring_sqe>, data: COpaquePointer?) {
    sqe.pointed.user_data = data?.toLong()?.toULong() ?: 0u
}