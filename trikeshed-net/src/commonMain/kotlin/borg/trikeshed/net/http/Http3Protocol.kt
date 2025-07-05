package borg.trikeshed.net.http


import borg.trikeshed.lib.*
import borg.trikeshed.net.quic.*
import kotlinx.coroutines.*
import kotlin.jvm.JvmInline

// === HTTP/3 TAXONOMICAL TYPEALIASES ===

typealias Http3StreamId = Long
typealias Http3FrameType = Long
typealias Http3FramePayload = Indexed<Byte>
typealias Http3ErrorCode = Long
typealias Http3SettingId = Long
typealias Http3SettingValue = Long
typealias Http3PushId = Long
typealias Http3Priority = Byte

// Value classes for type safety

/**
 * HTTP/3 Protocol Implementation (RFC 9114)
 * Built on top of QUIC transport with QPACK header compression
 */
object Http3Protocol {
    
    // === HTTP/3 STREAM TYPES ===
    object StreamTypes {
        val CONTROL = Http3StreamType(0x00)
        val PUSH = Http3StreamType(0x01)
        val QPACK_ENCODER = Http3StreamType(0x02)
        val QPACK_DECODER = Http3StreamType(0x03)
    }
    
    // === HTTP/3 FRAME TYPES ===
    object FrameTypes {
        const val DATA: Http3FrameType = 0x00
        const val HEADERS: Http3FrameType = 0x01
        const val CANCEL_PUSH: Http3FrameType = 0x03
        const val SETTINGS: Http3FrameType = 0x04
        const val PUSH_PROMISE: Http3FrameType = 0x05
        const val GOAWAY: Http3FrameType = 0x07
        const val MAX_PUSH_ID: Http3FrameType = 0x0d
    }
    
    // === HTTP/3 SETTINGS ===
    object Settings {
        const val QPACK_MAX_TABLE_CAPACITY: Http3SettingId = 0x01
        const val MAX_FIELD_SECTION_SIZE: Http3SettingId = 0x06
        const val QPACK_BLOCKED_STREAMS: Http3SettingId = 0x07
    }
    
    // === HTTP/3 ERROR CODES ===
    object ErrorCodes {
        const val NO_ERROR: Http3ErrorCode = 0x100
        const val GENERAL_PROTOCOL_ERROR: Http3ErrorCode = 0x101
        const val INTERNAL_ERROR: Http3ErrorCode = 0x102
        const val STREAM_CREATION_ERROR: Http3ErrorCode = 0x103
        const val CLOSED_CRITICAL_STREAM: Http3ErrorCode = 0x104
        const val FRAME_UNEXPECTED: Http3ErrorCode = 0x105
        const val FRAME_ERROR: Http3ErrorCode = 0x106
        const val EXCESSIVE_LOAD: Http3ErrorCode = 0x107
        const val ID_ERROR: Http3ErrorCode = 0x108
        const val SETTINGS_ERROR: Http3ErrorCode = 0x109
        const val MISSING_SETTINGS: Http3ErrorCode = 0x10a
        const val REQUEST_REJECTED: Http3ErrorCode = 0x10b
        const val REQUEST_CANCELLED: Http3ErrorCode = 0x10c
        const val REQUEST_INCOMPLETE: Http3ErrorCode = 0x10d
        const val MESSAGE_ERROR: Http3ErrorCode = 0x10e
        const val CONNECT_ERROR: Http3ErrorCode = 0x10f
        const val VERSION_FALLBACK: Http3ErrorCode = 0x110
        const val QPACK_DECOMPRESSION_FAILED: Http3ErrorCode = 0x200
        const val QPACK_ENCODER_STREAM_ERROR: Http3ErrorCode = 0x201
        const val QPACK_DECODER_STREAM_ERROR: Http3ErrorCode = 0x202
    }
}

/**
 * HTTP/3 Frame
 */
data class Http3Frame(
    val type: Http3FrameType,
    val payload: Http3FramePayload
) {
    fun encode(): Indexed<Byte> {
        val encoded = mutableListOf<Byte>()
        
        // Encode frame type as variable-length integer
        encoded.addAll(encodeVarInt(type))
        
        // Encode payload length as variable-length integer
        encoded.addAll(encodeVarInt(payload.a.toLong()))
        
        // Add payload
        for (i in 0 until payload.a) {
            encoded.add(payload[i])
        }
        
        return encoded.size j { i: Int -> encoded[i] }
    }
    
    companion object {
        fun decode(data: Indexed<Byte>, offset: Int = 0): Pair<Http3Frame?, Int> {
            var pos = offset
            
            // Decode frame type
            val (frameType, typeLen) = decodeVarInt(data, pos)
            if (frameType == null) return null to pos
            pos += typeLen
            
            // Decode payload length
            val (payloadLength, lengthLen) = decodeVarInt(data, pos)
            if (payloadLength == null) return null to pos
            pos += lengthLen
            
            // Extract payload
            if (pos + payloadLength > data.a) return null to pos
            
            val payload = payloadLength.toInt() j { i: Int -> data[pos + i] }
            pos += payloadLength.toInt()
            
            return Http3Frame(frameType, payload) to pos
        }
    }
}

/**
 * HTTP/3 Connection
 */
class Http3Connection(
    private val quicConnection: QuicConnection,
    private val role: Role = Role.CLIENT
) {
    enum class Role { CLIENT, SERVER }
    
    // Control streams
    private var controlStream: QuicStream? = null
    private var qpackEncoderStream: QuicStream? = null
    private var qpackDecoderStream: QuicStream? = null
    
    // QPACK codec
    private val qpackEncoder = QpackEncoder()
    private val qpackDecoder = QpackDecoder()
    
    // Settings
    private val localSettings = mutableMapOf(
        Http3Protocol.Settings.QPACK_MAX_TABLE_CAPACITY to 4096L,
        Http3Protocol.Settings.MAX_FIELD_SECTION_SIZE to 8192L,
        Http3Protocol.Settings.QPACK_BLOCKED_STREAMS to 100L
    )
    
    private val remoteSettings = mutableMapOf<Http3SettingId, Http3SettingValue>()
    
    // Request/Response handling
    private val activeRequests = mutableMapOf<Http3StreamId, Http3Request>()
    
    /**
     * Initialize HTTP/3 connection
     */
    suspend fun initialize() {
        // Create control stream
        controlStream = quicConnection.createStream()?.also { stream ->
            // Send stream type
            sendStreamType(stream, Http3Protocol.StreamTypes.CONTROL)
            
            // Send SETTINGS frame
            sendSettings(stream)
        }
        
        // Create QPACK encoder stream
        qpackEncoderStream = quicConnection.createStream()?.also { stream ->
            sendStreamType(stream, Http3Protocol.StreamTypes.QPACK_ENCODER)
        }
        
        // Create QPACK decoder stream
        qpackDecoderStream = quicConnection.createStream()?.also { stream ->
            sendStreamType(stream, Http3Protocol.StreamTypes.QPACK_DECODER)
        }
        
        // Start processing incoming streams
        processIncomingStreams()
    }
    
    /**
     * Send HTTP/3 request
     */
    suspend fun sendRequest(request: HttpRequest): Http3StreamId {
        val stream = quicConnection.createStream() ?: throw Exception("Failed to create stream")
        val streamId = stream.id
        
        // Convert headers to QPACK
        val headers = mutableListOf<Join<HttpHeaderName, HttpHeaderValue>>()
        headers.add(Join(HttpHeaderName(":method"), HttpHeaderValue(request.method.name)))
        headers.add(Join(HttpHeaderName(":path"), HttpHeaderValue(request.path.value)))
        headers.add(Join(HttpHeaderName(":scheme"), HttpHeaderValue("https")))
        headers.add(Join(HttpHeaderName(":authority"), HttpHeaderValue("example.com")))
        
        for (i in 0 until request.headers.a) {
            headers.add(request.headers[i])
        }
        
        val encodedHeaders = qpackEncoder.encode(
            headers.size j { i: Int -> headers[i] },
            streamId
        )
        
        // Send HEADERS frame
        val headersFrame = Http3Frame(
            type = Http3Protocol.FrameTypes.HEADERS,
            payload = encodedHeaders
        )
        
        val encodedFrame = headersFrame.encode()
        stream.writeBytes(encodedFrame)
        
        // Send DATA frame if body present
        if (request.body.isNotEmpty()) {
            val dataFrame = Http3Frame(
                type = Http3Protocol.FrameTypes.DATA,
                payload = request.body.size j { i: Int -> request.body[i] }
            )
            
            val encodedData = dataFrame.encode()
            stream.writeBytes(encodedData)
        }
        
        // Mark stream as finished
        stream.close()
        
        activeRequests[streamId] = Http3Request(request, stream)
        
        return streamId
    }
    
    /**
     * Process incoming streams
     */
    private suspend fun processIncomingStreams() {
        GlobalScope.launch {
            while (quicConnection.isActive()) {
                val stream = quicConnection.acceptStream() ?: continue
                
                launch {
                    handleIncomingStream(stream)
                }
            }
        }
    }
    
    /**
     * Handle incoming stream
     */
    private suspend fun handleIncomingStream(stream: QuicStream) {
        try {
            // Read stream type for unidirectional streams
            if (isUnidirectionalStream(stream.id)) {
                val streamType = readVarInt(stream)
                
                when (streamType?.toInt()) {
                    Http3Protocol.StreamTypes.CONTROL.value -> handleControlStream(stream)
                    Http3Protocol.StreamTypes.PUSH.value -> handlePushStream(stream)
                    Http3Protocol.StreamTypes.QPACK_ENCODER.value -> handleQpackEncoderStream(stream)
                    Http3Protocol.StreamTypes.QPACK_DECODER.value -> handleQpackDecoderStream(stream)
                    else -> stream.close() // Unknown stream type
                }
            } else {
                // Bidirectional stream - handle request/response
                handleRequestStream(stream)
            }
        } catch (e: Exception) {
            println("Error handling incoming stream: ${e.message}")
            stream.close()
        }
    }
    
    /**
     * Handle control stream
     */
    private suspend fun handleControlStream(stream: QuicStream) {
        while (stream.hasData()) {
            val (frame, _) = Http3Frame.decode(readAllBytes(stream))
            
            when (frame?.type) {
                Http3Protocol.FrameTypes.SETTINGS -> handleSettings(frame.payload)
                Http3Protocol.FrameTypes.GOAWAY -> handleGoaway(frame.payload)
                Http3Protocol.FrameTypes.MAX_PUSH_ID -> handleMaxPushId(frame.payload)
                else -> {} // Ignore unknown frames
            }
        }
    }
    
    /**
     * Handle request stream
     */
    private suspend fun handleRequestStream(stream: QuicStream) {
        val frames = mutableListOf<Http3Frame>()
        
        // Read all frames
        while (stream.hasData()) {
            val (frame, _) = Http3Frame.decode(readAllBytes(stream))
            if (frame != null) {
                frames.add(frame)
            }
        }
        
        // Process frames
        var headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>? = null
        val bodyParts = mutableListOf<Byte>()
        
        for (frame in frames) {
            when (frame.type) {
                Http3Protocol.FrameTypes.HEADERS -> {
                    headers = qpackDecoder.decode(frame.payload, stream.id)
                }
                Http3Protocol.FrameTypes.DATA -> {
                    for (i in 0 until frame.payload.a) {
                        bodyParts.add(frame.payload[i])
                    }
                }
            }
        }
        
        // Create HTTP request from frames
        if (headers != null && role == Role.SERVER) {
            handleIncomingRequest(stream, headers, bodyParts.toByteArray())
        }
    }
    
    /**
     * Handle incoming request (server side)
     */
    private suspend fun handleIncomingRequest(
        stream: QuicStream,
        headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>,
        body: ByteArray
    ) {
        // Extract pseudo-headers
        var method: HttpMethod? = null
        var path: HttpRequestPath? = null
        
        for (i in 0 until headers.a) {
            val header = headers[i]
            when (header.a.value) {
                ":method" -> method = HttpMethod.valueOf(header.b.value)
                ":path" -> path = HttpRequestPath(header.b.value)
            }
        }
        
        if (method != null && path != null) {
            val request = HttpRequest(
                method = method,
                path = path,
                headers = headers,
                body = body,
                version = HttpVersion("HTTP/3")
            )
            
            // Process request and send response
            // This would be handled by the application layer
        }
    }
    
    /**
     * Send settings
     */
    private suspend fun sendSettings(stream: QuicStream) {
        val payload = mutableListOf<Byte>()
        
        for ((id, value) in localSettings) {
            payload.addAll(encodeVarInt(id))
            payload.addAll(encodeVarInt(value))
        }
        
        val frame = Http3Frame(
            type = Http3Protocol.FrameTypes.SETTINGS,
            payload = payload.size j { i: Int -> payload[i] }
        )
        
        val encoded = frame.encode()
        stream.writeBytes(encoded)
    }
    
    /**
     * Handle settings frame
     */
    private fun handleSettings(payload: Http3FramePayload) {
        var offset = 0
        
        while (offset < payload.a) {
            val (id, idLen) = decodeVarInt(payload, offset)
            if (id == null) break
            offset += idLen
            
            val (value, valueLen) = decodeVarInt(payload, offset)
            if (value == null) break
            offset += valueLen
            
            remoteSettings[id] = value
        }
        
        // Apply settings
        qpackEncoder.setMaxTableCapacity(
            remoteSettings[Http3Protocol.Settings.QPACK_MAX_TABLE_CAPACITY]?.toInt() ?: 0
        )
    }
    
    /**
     * Handle GOAWAY frame
     */
    private fun handleGoaway(payload: Http3FramePayload) {
        val (streamId, _) = decodeVarInt(payload, 0)
        println("Received GOAWAY with stream ID: $streamId")
    }
    
    /**
     * Handle MAX_PUSH_ID frame
     */
    private fun handleMaxPushId(payload: Http3FramePayload) {
        val (pushId, _) = decodeVarInt(payload, 0)
        println("Received MAX_PUSH_ID: $pushId")
    }
    
    /**
     * Send stream type
     */
    private suspend fun sendStreamType(stream: QuicStream, type: Http3StreamType) {
        val encoded = encodeVarInt(type.value.toLong())
        stream.writeBytes(encoded.size j { i: Int -> encoded[i] })
    }
    
    /**
     * Check if stream is unidirectional
     */
    private fun isUnidirectionalStream(streamId: Http3StreamId): Boolean {
        return (streamId and 0x2) != 0L
    }
    
    /**
     * Read variable-length integer from stream
     */
    private suspend fun readVarInt(stream: QuicStream): Long? {
        if (!stream.hasData()) return null
        
        val firstByte = stream.readByte()
        val prefix = firstByte.toInt() and 0xC0
        
        return when (prefix) {
            0x00 -> firstByte.toLong() and 0x3F
            0x40 -> {
                val b2 = stream.readByte()
                ((firstByte.toLong() and 0x3F) shl 8) or (b2.toLong() and 0xFF)
            }
            0x80 -> {
                val b2 = stream.readByte()
                val b3 = stream.readByte()
                val b4 = stream.readByte()
                ((firstByte.toLong() and 0x3F) shl 24) or
                ((b2.toLong() and 0xFF) shl 16) or
                ((b3.toLong() and 0xFF) shl 8) or
                (b4.toLong() and 0xFF)
            }
            0xC0 -> {
                // 8-byte encoding
                var value = (firstByte.toLong() and 0x3F) shl 56
                for (i in 0 until 7) {
                    value = value or ((stream.readByte().toLong() and 0xFF) shl (48 - i * 8))
                }
                value
            }
            else -> null
        }
    }
    
    /**
     * Read all available bytes from stream
     */
    private suspend fun readAllBytes(stream: QuicStream): Indexed<Byte> {
        val bytes = mutableListOf<Byte>()
        while (stream.hasData()) {
            bytes.add(stream.readByte())
        }
        return bytes.size j { i: Int -> bytes[i] }
    }
    
    /**
     * Handle push stream
     */
    private suspend fun handlePushStream(stream: QuicStream) {
        // Read push ID
        val pushId = readVarInt(stream)
        if (pushId == null) {
            stream.close()
            return
        }
        
        // Handle push stream data
        while (stream.hasData()) {
            val data = readAllBytes(stream)
            // Process push data (simplified)
            println("Received push data: ${data.size} bytes")
        }
    }
    
    /**
     * Handle QPACK encoder stream
     */
    private suspend fun handleQpackEncoderStream(stream: QuicStream) {
        while (stream.hasData()) {
            val data = readAllBytes(stream)
            // Process QPACK encoder stream data (simplified)
            println("Received QPACK encoder data: ${data.size} bytes")
        }
    }
    
    /**
     * Handle QPACK decoder stream
     */
    private suspend fun handleQpackDecoderStream(stream: QuicStream) {
        while (stream.hasData()) {
            val data = readAllBytes(stream)
            // Process QPACK decoder stream data (simplified)
            println("Received QPACK decoder data: ${data.size} bytes")
        }
    }
}

/**
 * QPACK Encoder (RFC 9204)
 */
class QpackEncoder {
    private var maxTableCapacity = DynamicTableCapacity(0)
    private val dynamicTable = mutableListOf<Join<String, String>>()
    private var currentCapacity = 0
    
    fun setMaxTableCapacity(capacity: Int) {
        maxTableCapacity = DynamicTableCapacity(capacity)
        evictEntries()
    }
    
    fun encode(
        headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>,
        streamId: Http3StreamId
    ): Indexed<Byte> {
        val encoded = mutableListOf<Byte>()
        
        // Required Insert Count (RFC 9204 Section 4.5.1)
        encoded.add(0x00) // RIC = 0
        
        // Delta Base (RFC 9204 Section 4.5.1)
        encoded.add(0x00) // Delta Base = 0
        
        // Encode header fields
        for (i in 0 until headers.a) {
            val header = headers[i]
            val name = header.a.value.lowercase()
            val value = header.b.value
            
            // Check static table
            val staticIndex = QpackStaticTable.getIndex(name, value)
            
            if (staticIndex != null) {
                // Indexed Header Field (RFC 9204 Section 4.5.2)
                encoded.add((0x80 or staticIndex).toByte())
            } else {
                // Literal Header Field With Name Reference (RFC 9204 Section 4.5.4)
                val nameIndex = QpackStaticTable.getNameIndex(name)
                
                if (nameIndex != null) {
                    encoded.add((0x40 or nameIndex).toByte())
                } else {
                    // Literal Header Field Without Name Reference (RFC 9204 Section 4.5.6)
                    encoded.add(0x20)
                    
                    // Encode name as literal
                    val nameBytes = name.encodeToByteArray()
                    encoded.addAll(encodeStringLiteral(nameBytes))
                }
                
                // Encode value as literal
                val valueBytes = value.encodeToByteArray()
                encoded.addAll(encodeStringLiteral(valueBytes))
            }
        }
        
        return encoded.size j { i: Int -> encoded[i] }
    }
    
    private fun encodeStringLiteral(bytes: ByteArray): List<Byte> {
        val result = mutableListOf<Byte>()
        
        // No Huffman encoding for simplicity
        result.addAll(encodeVarInt(bytes.size.toLong(), 7))
        result.addAll(bytes.toList())
        
        return result
    }
    
    private fun evictEntries() {
        while (currentCapacity > maxTableCapacity.value && dynamicTable.isNotEmpty()) {
            val removed = dynamicTable.removeAt(dynamicTable.size - 1)
            currentCapacity -= 32 + removed.a.length + removed.b.length
        }
    }
}

/**
 * QPACK Decoder
 */
class QpackDecoder {
    private val dynamicTable = mutableListOf<Join<String, String>>()
    
    fun decode(
        data: Http3FramePayload,
        streamId: Http3StreamId
    ): Indexed<Join<HttpHeaderName, HttpHeaderValue>> {
        val headers = mutableListOf<Join<HttpHeaderName, HttpHeaderValue>>()
        var offset = 0
        
        // Skip Required Insert Count
        offset++
        
        // Skip Delta Base
        offset++
        
        // Decode header fields
        while (offset < data.a) {
            val byte = data[offset]
            
            when {
                // Indexed Header Field
                (byte.toInt() and 0x80) != 0 -> {
                    val index = byte.toInt() and 0x3F
                    val entry = QpackStaticTable.getEntry(index)
                    
                    if (entry != null) {
                        headers.add(Join(
                            HttpHeaderName(entry.a),
                            HttpHeaderValue(entry.b)
                        ))
                    }
                    offset++
                }
                
                // Literal Header Field With Name Reference
                (byte.toInt() and 0x40) != 0 -> {
                    val nameIndex = byte.toInt() and 0x3F
                    val name = QpackStaticTable.getName(nameIndex) ?: ""
                    offset++
                    
                    // Decode value
                    val (value, valueLen) = decodeStringLiteral(data, offset)
                    offset += valueLen
                    
                    headers.add(Join(
                        HttpHeaderName(name),
                        HttpHeaderValue(value)
                    ))
                }
                
                // Literal Header Field Without Name Reference
                (byte.toInt() and 0x20) != 0 -> {
                    offset++
                    
                    // Decode name
                    val (name, nameLen) = decodeStringLiteral(data, offset)
                    offset += nameLen
                    
                    // Decode value
                    val (value, valueLen) = decodeStringLiteral(data, offset)
                    offset += valueLen
                    
                    headers.add(Join(
                        HttpHeaderName(name),
                        HttpHeaderValue(value)
                    ))
                }
                
                else -> offset++ // Skip unknown patterns
            }
        }
        
        return headers.size j { i: Int -> headers[i] }
    }
    
    private fun decodeStringLiteral(
        data: Http3FramePayload,
        startOffset: Int
    ): Pair<String, Int> {
        var offset = startOffset
        val firstByte = data[offset]
        
        // Check Huffman encoding bit
        val huffman = (firstByte.toInt() and 0x80) != 0
        
        // Decode length
        val (length, lengthSize) = decodeVarInt(data, offset, 7)
        offset += lengthSize
        
        if (length == null || offset + length > data.a) {
            return "" to 1
        }
        
        // Extract string bytes
        val stringBytes = ByteArray(length.toInt())
        for (i in 0 until length.toInt()) {
            stringBytes[i] = data[offset + i]
        }
        
        val string = if (huffman) {
            // TODO: Implement Huffman decoding
            stringBytes.decodeToString()
        } else {
            stringBytes.decodeToString()
        }
        
        return string to (lengthSize + length.toInt())
    }
}

/**
 * QPACK Static Table
 */
object QpackStaticTable {
    private val entries = listOf(
        Join(":authority", ""),
        Join(":path", "/"),
        Join("age", "0"),
        Join("content-disposition", ""),
        Join("content-length", "0"),
        Join("cookie", ""),
        Join("date", ""),
        Join("etag", ""),
        Join("if-modified-since", ""),
        Join("if-none-match", ""),
        Join("last-modified", ""),
        Join("link", ""),
        Join("location", ""),
        Join("referer", ""),
        Join("set-cookie", ""),
        Join(":method", "CONNECT"),
        Join(":method", "DELETE"),
        Join(":method", "GET"),
        Join(":method", "HEAD"),
        Join(":method", "OPTIONS"),
        Join(":method", "POST"),
        Join(":method", "PUT"),
        Join(":scheme", "http"),
        Join(":scheme", "https"),
        Join(":status", "103"),
        Join(":status", "200"),
        Join(":status", "304"),
        Join(":status", "404"),
        Join(":status", "503"),
        Join("accept", "*/*"),
        Join("accept", "application/dns-message"),
        Join("accept-encoding", "gzip, deflate, br"),
        Join("accept-ranges", "bytes"),
        Join("access-control-allow-headers", "cache-control"),
        Join("access-control-allow-headers", "content-type"),
        Join("access-control-allow-origin", "*"),
        Join("cache-control", "max-age=0"),
        Join("cache-control", "max-age=2592000"),
        Join("cache-control", "max-age=604800"),
        Join("cache-control", "no-cache"),
        Join("cache-control", "no-store"),
        Join("cache-control", "public, max-age=31536000"),
        Join("content-encoding", "br"),
        Join("content-encoding", "gzip"),
        Join("content-type", "application/dns-message"),
        Join("content-type", "application/javascript"),
        Join("content-type", "application/json"),
        Join("content-type", "application/x-www-form-urlencoded"),
        Join("content-type", "image/gif"),
        Join("content-type", "image/jpeg"),
        Join("content-type", "image/png"),
        Join("content-type", "text/css"),
        Join("content-type", "text/html; charset=utf-8"),
        Join("content-type", "text/plain"),
        Join("content-type", "text/plain;charset=utf-8"),
        Join("range", "bytes=0-"),
        Join("strict-transport-security", "max-age=31536000"),
        Join("strict-transport-security", "max-age=31536000; includesubdomains"),
        Join("strict-transport-security", "max-age=31536000; includesubdomains; preload"),
        Join("vary", "accept-encoding"),
        Join("vary", "origin"),
        Join("x-content-type-options", "nosniff"),
        Join("x-xss-protection", "1; mode=block"),
        Join(":status", "100"),
        Join(":status", "204"),
        Join(":status", "206"),
        Join(":status", "302"),
        Join(":status", "400"),
        Join(":status", "403"),
        Join(":status", "421"),
        Join(":status", "425"),
        Join(":status", "500"),
        Join("accept-language", ""),
        Join("access-control-allow-credentials", "FALSE"),
        Join("access-control-allow-credentials", "TRUE"),
        Join("access-control-allow-headers", "*"),
        Join("access-control-allow-methods", "get"),
        Join("access-control-allow-methods", "get, post, options"),
        Join("access-control-allow-methods", "options"),
        Join("access-control-expose-headers", "content-length"),
        Join("access-control-request-headers", "content-type"),
        Join("access-control-request-method", "get"),
        Join("access-control-request-method", "post"),
        Join("alt-svc", "clear"),
        Join("authorization", ""),
        Join("content-security-policy", "script-src 'none'; object-src 'none'; base-uri 'none'"),
        Join("early-data", "1"),
        Join("expect-ct", ""),
        Join("forwarded", ""),
        Join("if-range", ""),
        Join("origin", ""),
        Join("purpose", "prefetch"),
        Join("server", ""),
        Join("timing-allow-origin", "*"),
        Join("upgrade-insecure-requests", "1"),
        Join("user-agent", ""),
        Join("x-forwarded-for", ""),
        Join("x-frame-options", "deny"),
        Join("x-frame-options", "sameorigin")
    )
    
    fun getIndex(name: String, value: String): Int? {
        val index = entries.indexOfFirst { it.a == name && it.b == value }
        return if (index >= 0) index else null
    }
    
    fun getNameIndex(name: String): Int? {
        val index = entries.indexOfFirst { it.a == name }
        return if (index >= 0) index else null
    }
    
    fun getEntry(index: Int): Join<String, String>? {
        return if (index < entries.size) entries[index] else null
    }
    
    fun getName(index: Int): String? {
        return if (index < entries.size) entries[index].a else null
    }
}

/**
 * HTTP/3 Request wrapper
 */
private data class Http3Request(
    val httpRequest: HttpRequest,
    val stream: QuicStream
)

// === UTILITY FUNCTIONS ===

/**
 * Encode variable-length integer (RFC 9000 Section 16)
 */
private fun encodeVarInt(value: Long, prefixBits: Int = 62): List<Byte> {
    return when {
        value < (1L shl 6) -> listOf((value and 0x3F).toByte())
        value < (1L shl 14) -> listOf(
            ((value shr 8) and 0x3F or 0x40).toByte(),
            (value and 0xFF).toByte()
        )
        value < (1L shl 30) -> listOf(
            ((value shr 24) and 0x3F or 0x80).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
        else -> listOf(
            ((value shr 56) and 0x3F or 0xC0).toByte(),
            ((value shr 48) and 0xFF).toByte(),
            ((value shr 40) and 0xFF).toByte(),
            ((value shr 32) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }
}

/**
 * Decode variable-length integer
 */
private fun decodeVarInt(
    data: Indexed<Byte>,
    offset: Int,
    prefixBits: Int = 62
): Pair<Long?, Int> {
    if (offset >= data.a) return null to 0
    
    val firstByte = data[offset].toInt() and 0xFF
    val prefix = firstByte shr (8 - 2)
    
    return when (prefix) {
        0 -> (firstByte.toLong() and 0x3F) to 1
        1 -> {
            if (offset + 1 >= data.a) return null to 0
            val value = ((firstByte.toLong() and 0x3F) shl 8) or
                       (data[offset + 1].toLong() and 0xFF)
            value to 2
        }
        2 -> {
            if (offset + 3 >= data.a) return null to 0
            val value = ((firstByte.toLong() and 0x3F) shl 24) or
                       ((data[offset + 1].toLong() and 0xFF) shl 16) or
                       ((data[offset + 2].toLong() and 0xFF) shl 8) or
                       (data[offset + 3].toLong() and 0xFF)
            value to 4
        }
        3 -> {
            if (offset + 7 >= data.a) return null to 0
            var value = (firstByte.toLong() and 0x3F) shl 56
            for (i in 1..7) {
                value = value or ((data[offset + i].toLong() and 0xFF) shl (56 - i * 8))
            }
            value to 8
        }
        else -> null to 0
    }
}