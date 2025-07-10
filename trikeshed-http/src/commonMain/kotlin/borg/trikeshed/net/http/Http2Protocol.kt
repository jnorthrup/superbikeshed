@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.http


import borg.trikeshed.lib.*
import borg.trikeshed.net.quic.*
import kotlinx.coroutines.*

/**
 * HTTP/2 Protocol Implementation
 * RFC 7540 compliant with HPACK header compression (RFC 7541)
 */
class Http2Protocol {
    
    /**
     * HTTP/2 Frame Types (RFC 7540 Section 6)
     */
    enum class FrameType(val value: Byte) {
        DATA(0x0),
        HEADERS(0x1),
        PRIORITY(0x2),
        RST_STREAM(0x3),
        SETTINGS(0x4),
        PUSH_PROMISE(0x5),
        PING(0x6),
        GOAWAY(0x7),
        WINDOW_UPDATE(0x8),
        CONTINUATION(0x9)
    }
    
    /**
     * HTTP/2 Frame Flags
     */
    object FrameFlags {
        const val END_STREAM: Byte = 0x1
        const val END_HEADERS: Byte = 0x4
        const val PADDED: Byte = 0x8
        const val PRIORITY: Byte = 0x20
    }
    
    /**
     * HTTP/2 Settings Parameters (RFC 7540 Section 6.5.2)
     */
    enum class SettingsParameter(val id: Int) {
        HEADER_TABLE_SIZE(0x1),
        ENABLE_PUSH(0x2),
        MAX_CONCURRENT_STREAMS(0x3),
        INITIAL_WINDOW_SIZE(0x4),
        MAX_FRAME_SIZE(0x5),
        MAX_HEADER_LIST_SIZE(0x6)
    }
    
    /**
     * HTTP/2 Error Codes (RFC 7540 Section 7)
     */
    enum class ErrorCode(val code: Long) {
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
    
    /**
     * HTTP/2 Frame
     */
    data class Http2Frame(
        val type: FrameType,
        val flags: Byte,
        val streamId: Int,
        val payload: Indexed<Byte>
    ) {
        val length: Int get() = payload.a
        
        fun toByteArray(): ByteArray {
            val result = ByteArray(9 + length)
            
            // Length (24 bits)
            result[0] = (length shr 16).toByte()
            result[1] = (length shr 8).toByte()
            result[2] = length.toByte()
            
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
            for (i in 0 until length) {
                result[9 + i] = payload[i]
            }
            
            return result
        }
        
        companion object {
            fun parse(bytes: ByteArray): Http2Frame? {
                if (bytes.size < 9) return null
                
                val length = ((bytes[0].toInt() and 0xFF) shl 16) or
                           ((bytes[1].toInt() and 0xFF) shl 8) or
                           (bytes[2].toInt() and 0xFF)
                
                val type = FrameType.values().find { it.value == bytes[3] } ?: return null
                val flags = bytes[4]
                
                val streamId = ((bytes[5].toInt() and 0x7F) shl 24) or
                             ((bytes[6].toInt() and 0xFF) shl 16) or
                             ((bytes[7].toInt() and 0xFF) shl 8) or
                             (bytes[8].toInt() and 0xFF)
                
                if (bytes.size < 9 + length) return null
                
                val payload = length j { i: Int -> bytes[9 + i] }
                
                return Http2Frame(type, flags, streamId, payload)
            }
        }
    }
    
    /**
     * HTTP/2 Stream State
     */
    enum class StreamState {
        IDLE,
        RESERVED_LOCAL,
        RESERVED_REMOTE,
        OPEN,
        HALF_CLOSED_LOCAL,
        HALF_CLOSED_REMOTE,
        CLOSED
    }
    
    /**
     * HTTP/2 Stream
     */
    class Http2Stream(
        val id: Int,
        var state: StreamState = StreamState.IDLE,
        var localWindowSize: Int = 65535,
        var remoteWindowSize: Int = 65535
    ) {
        internal val pendingHeaders = mutableListOf<Indexed<Byte>>()
        internal val pendingData = mutableListOf<Indexed<Byte>>()
        
        fun transitionTo(newState: StreamState): Boolean {
            // Validate state transitions according to RFC 7540
            return when (state) {
                StreamState.IDLE -> newState in listOf(
                    StreamState.RESERVED_LOCAL,
                    StreamState.RESERVED_REMOTE,
                    StreamState.OPEN,
                    StreamState.CLOSED
                )
                StreamState.RESERVED_LOCAL -> newState in listOf(
                    StreamState.HALF_CLOSED_REMOTE,
                    StreamState.CLOSED
                )
                StreamState.RESERVED_REMOTE -> newState in listOf(
                    StreamState.HALF_CLOSED_LOCAL,
                    StreamState.CLOSED
                )
                StreamState.OPEN -> newState in listOf(
                    StreamState.HALF_CLOSED_LOCAL,
                    StreamState.HALF_CLOSED_REMOTE,
                    StreamState.CLOSED
                )
                StreamState.HALF_CLOSED_LOCAL -> newState == StreamState.CLOSED
                StreamState.HALF_CLOSED_REMOTE -> newState == StreamState.CLOSED
                StreamState.CLOSED -> false
            }.also { if (it) state = newState }
        }
    }
    
    /**
     * HTTP/2 Connection
     */
    class Http2Connection(
        internal val transport: QuicConnection,
        internal val isServer: Boolean = true
    ) {
        internal val streams = mutableMapOf<Int, Http2Stream>()
        internal val hpackEncoder = HpackEncoder()
        internal val hpackDecoder = HpackDecoder()
        
        // Connection settings
        internal val localSettings = mutableMapOf(
            SettingsParameter.HEADER_TABLE_SIZE to 4096,
            SettingsParameter.ENABLE_PUSH to (if (isServer) 1 else 0),
            SettingsParameter.MAX_CONCURRENT_STREAMS to 100,
            SettingsParameter.INITIAL_WINDOW_SIZE to 65535,
            SettingsParameter.MAX_FRAME_SIZE to 16384,
            SettingsParameter.MAX_HEADER_LIST_SIZE to 8192
        )
        
        internal val remoteSettings = mutableMapOf<SettingsParameter, Int>()
        
        // Flow control
        internal var localWindowSize = 65535
        internal var remoteWindowSize = 65535
        
        // Stream ID generation
        internal var nextStreamId = if (isServer) 2 else 1
        
        /**
         * Send HTTP/2 preface
         */
        suspend fun sendPreface() {
            if (!isServer) {
                // Client sends connection preface
                val preface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".encodeToByteArray()
                val stream = transport.createStream() ?: return
                stream.writeBytes(preface.size j { i: Int -> preface[i] })
            }
            
            // Send SETTINGS frame
            sendSettings()
        }
        
        /**
         * Send SETTINGS frame
         */
        internal suspend fun sendSettings() {
            val payload = mutableListOf<Byte>()
            
            for ((param, value) in localSettings) {
                // Parameter ID (16 bits)
                payload.add((param.id shr 8).toByte())
                payload.add(param.id.toByte())
                
                // Value (32 bits)
                payload.add((value shr 24).toByte())
                payload.add((value shr 16).toByte())
                payload.add((value shr 8).toByte())
                payload.add(value.toByte())
            }
            
            val frame = Http2Frame(
                type = FrameType.SETTINGS,
                flags = 0,
                streamId = 0,
                payload = payload.size j { i: Int -> payload[i] }
            )
            
            sendFrame(frame)
        }
        
        /**
         * Send frame
         */
        internal suspend fun sendFrame(frame: Http2Frame) {
            val stream = transport.createStream() ?: return
            val frameBytes = frame.toByteArray()
            stream.writeBytes(frameBytes.size j { i: Int -> frameBytes[i] })
        }
        
        /**
         * Create new stream
         */
        fun createStream(): Http2Stream {
            val streamId = nextStreamId
            nextStreamId += 2
            
            val stream = Http2Stream(streamId)
            streams[streamId] = stream
            return stream
        }
        
        /**
         * Send HEADERS frame
         */
        suspend fun sendHeaders(
            streamId: Int,
            headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>,
            endStream: Boolean = false
        ) {
            val stream = streams[streamId] ?: return
            
            // Encode headers using HPACK
            val encodedHeaders = hpackEncoder.encode(headers)
            
            var flags: Byte = FrameFlags.END_HEADERS
            if (endStream) {
                flags = (flags.toInt() or FrameFlags.END_STREAM.toInt()).toByte()
            }
            
            val frame = Http2Frame(
                type = FrameType.HEADERS,
                flags = flags,
                streamId = streamId,
                payload = encodedHeaders
            )
            
            sendFrame(frame)
            
            // Update stream state
            if (stream.state == StreamState.IDLE) {
                stream.transitionTo(StreamState.OPEN)
            }
            if (endStream) {
                stream.transitionTo(
                    if (isServer) StreamState.HALF_CLOSED_REMOTE 
                    else StreamState.HALF_CLOSED_LOCAL
                )
            }
        }
        
        /**
         * Send DATA frame
         */
        suspend fun sendData(
            streamId: Int,
            data: Indexed<Byte>,
            endStream: Boolean = false
        ) {
            val stream = streams[streamId] ?: return
            
            // Check flow control window
            val maxSize = minOf(
                remoteWindowSize,
                stream.remoteWindowSize,
                remoteSettings[SettingsParameter.MAX_FRAME_SIZE] ?: 16384
            )
            
            var offset = 0
            while (offset < data.a) {
                val chunkSize = minOf(maxSize, data.a - offset)
                val chunk = chunkSize j { i: Int -> data[offset + i] }
                
                val flags = if (endStream && offset + chunkSize >= data.a) {
                    FrameFlags.END_STREAM
                } else {
                    0
                }
                
                val frame = Http2Frame(
                    type = FrameType.DATA,
                    flags = flags.toByte(),
                    streamId = streamId,
                    payload = chunk
                )
                
                sendFrame(frame)
                
                // Update window sizes
                remoteWindowSize -= chunkSize
                stream.remoteWindowSize -= chunkSize
                
                offset += chunkSize
            }
            
            if (endStream) {
                stream.transitionTo(
                    if (isServer) StreamState.HALF_CLOSED_LOCAL
                    else StreamState.HALF_CLOSED_REMOTE
                )
            }
        }
        
        /**
         * Send PUSH_PROMISE frame (server only)
         */
        suspend fun sendPushPromise(
            streamId: Int,
            promisedStreamId: Int,
            headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>
        ) {
            if (!isServer) return
            if (remoteSettings[SettingsParameter.ENABLE_PUSH] == 0) return
            
            val encodedHeaders = hpackEncoder.encode(headers)
            
            // Build payload: promised stream ID + headers
            val payload = mutableListOf<Byte>()
            payload.add((promisedStreamId shr 24).toByte())
            payload.add((promisedStreamId shr 16).toByte())
            payload.add((promisedStreamId shr 8).toByte())
            payload.add(promisedStreamId.toByte())
            
            for (i in 0 until encodedHeaders.a) {
                payload.add(encodedHeaders[i])
            }
            
            val frame = Http2Frame(
                type = FrameType.PUSH_PROMISE,
                flags = FrameFlags.END_HEADERS,
                streamId = streamId,
                payload = payload.size j { i: Int -> payload[i] }
            )
            
            sendFrame(frame)
            
            // Create promised stream
            val promisedStream = Http2Stream(promisedStreamId, StreamState.RESERVED_LOCAL)
            streams[promisedStreamId] = promisedStream
        }
        
        /**
         * Send RST_STREAM frame
         */
        suspend fun sendRstStream(streamId: Int, errorCode: ErrorCode) {
            val payload = 4 j { i: Int ->
                when (i) {
                    0 -> (errorCode.code shr 24).toByte()
                    1 -> (errorCode.code shr 16).toByte()
                    2 -> (errorCode.code shr 8).toByte()
                    3 -> errorCode.code.toByte()
                    else -> 0.toByte()
                }
            }
            
            val frame = Http2Frame(
                type = FrameType.RST_STREAM,
                flags = 0,
                streamId = streamId,
                payload = payload
            )
            
            sendFrame(frame)
            
            // Close stream
            streams[streamId]?.transitionTo(StreamState.CLOSED)
        }
    }
}

/**
 * HPACK Header Compression (RFC 7541)
 */
class HpackEncoder {
    internal val dynamicTable = mutableListOf<Join<String, String>>()
    internal var dynamicTableSize = 0
    internal var maxDynamicTableSize = 4096
    
    fun encode(headers: Indexed<Join<HttpHeaderName, HttpHeaderValue>>): Indexed<Byte> {
        val output = mutableListOf<Byte>()
        
        for (i in 0 until headers.a) {
            val header = headers[i]
            val name = header.a.value.lowercase()
            val value = header.b.value
            
            // Check static table
            val staticIndex = HpackStaticTable.getIndex(name, value)
            
            if (staticIndex != null) {
                // Indexed header field
                encodeInteger(output, staticIndex, 7, 0x80.toByte())
            } else {
                // Literal header field with incremental indexing
                output.add(0x40.toByte())
                
                // Encode name
                val nameBytes = name.encodeToByteArray()
                encodeInteger(output, nameBytes.size, 7, 0.toByte())
                output.addAll(nameBytes.toList())
                
                // Encode value
                val valueBytes = value.encodeToByteArray()
                encodeInteger(output, valueBytes.size, 7, 0.toByte())
                output.addAll(valueBytes.toList())
                
                // Add to dynamic table
                addToDynamicTable(name, value)
            }
        }
        
        return output.size j { i: Int -> output[i] }
    }
    
    internal fun encodeInteger(output: MutableList<Byte>, value: Int, prefixBits: Int, flags: Byte) {
        val maxPrefix = (1 shl prefixBits) - 1
        
        if (value < maxPrefix) {
            output.add((flags.toInt() or value).toByte())
        } else {
            output.add((flags.toInt() or maxPrefix).toByte())
            var remainder = value - maxPrefix
            
            while (remainder >= 128) {
                output.add((remainder and 0x7F or 0x80).toByte())
                remainder = remainder shr 7
            }
            
            output.add(remainder.toByte())
        }
    }
    
    internal fun addToDynamicTable(name: String, value: String) {
        val entry = Join(name, value)
        val entrySize = 32 + name.length + value.length
        
        // Evict entries if necessary
        while (dynamicTableSize + entrySize > maxDynamicTableSize && dynamicTable.isNotEmpty()) {
            val removed = dynamicTable.removeAt(dynamicTable.size - 1)
            dynamicTableSize -= 32 + removed.a.length + removed.b.length
        }
        
        if (entrySize <= maxDynamicTableSize) {
            dynamicTable.add(0, entry)
            dynamicTableSize += entrySize
        }
    }
}

/**
 * HPACK Header Decompression
 */
class HpackDecoder {
    internal val dynamicTable = mutableListOf<Join<String, String>>()
    
    fun decode(data: Indexed<Byte>): Indexed<Join<HttpHeaderName, HttpHeaderValue>> {
        val headers = mutableListOf<Join<HttpHeaderName, HttpHeaderValue>>()
        var index = 0
        
        while (index < data.a) {
            val byte = data[index]
            
            when {
                // Indexed header field
                (byte.toInt() and 0x80) != 0 -> {
                    val (headerIndex, consumed) = decodeInteger(data, index, 7)
                    index += consumed
                    
                    val header = getHeader(headerIndex)
                    if (header != null) {
                        headers.add(Join(
                            HttpHeaderName(header.a),
                            HttpHeaderValue(header.b)
                        ))
                    }
                }
                
                // Literal header field with incremental indexing
                (byte.toInt() and 0x40) != 0 -> {
                    index++
                    
                    // Decode name
                    val (nameLength, nameConsumed) = decodeInteger(data, index, 7)
                    index += nameConsumed
                    
                    val nameBytes = ByteArray(nameLength)
                    for (i in 0 until nameLength) {
                        nameBytes[i] = data[index + i]
                    }
                    index += nameLength
                    
                    val name = nameBytes.decodeToString()
                    
                    // Decode value
                    val (valueLength, valueConsumed) = decodeInteger(data, index, 7)
                    index += valueConsumed
                    
                    val valueBytes = ByteArray(valueLength)
                    for (i in 0 until valueLength) {
                        valueBytes[i] = data[index + i]
                    }
                    index += valueLength
                    
                    val value = valueBytes.decodeToString()
                    
                    headers.add(Join(HttpHeaderName(name), HttpHeaderValue(value)))
                    dynamicTable.add(0, Join(name, value))
                }
                
                else -> index++ // Skip unknown patterns
            }
        }
        
        return headers.size j { i: Int -> headers[i] }
    }
    
    internal fun decodeInteger(data: Indexed<Byte>, startIndex: Int, prefixBits: Int): Pair<Int, Int> {
        val maxPrefix = (1 shl prefixBits) - 1
        val firstByte = data[startIndex].toInt() and maxPrefix
        
        if (firstByte < maxPrefix) {
            return firstByte to 1
        }
        
        var value = maxPrefix
        var multiplier = 1
        var index = startIndex + 1
        
        while (index < data.a) {
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
    
    internal fun getHeader(index: Int): Join<String, String>? {
        return if (index <= HpackStaticTable.entries.size) {
            HpackStaticTable.entries[index - 1]
        } else {
            val dynamicIndex = index - HpackStaticTable.entries.size - 1
            if (dynamicIndex < dynamicTable.size) {
                dynamicTable[dynamicIndex]
            } else {
                null
            }
        }
    }
}

/**
 * HPACK Static Table (RFC 7541 Appendix A)
 */
object HpackStaticTable {
    val entries = listOf(
        Join(":authority", ""),
        Join(":method", "GET"),
        Join(":method", "POST"),
        Join(":path", "/"),
        Join(":path", "/index.html"),
        Join(":scheme", "http"),
        Join(":scheme", "https"),
        Join(":status", "200"),
        Join(":status", "204"),
        Join(":status", "206"),
        Join(":status", "304"),
        Join(":status", "400"),
        Join(":status", "404"),
        Join(":status", "500"),
        Join("accept-charset", ""),
        Join("accept-encoding", "gzip, deflate"),
        Join("accept-language", ""),
        Join("accept-ranges", ""),
        Join("accept", ""),
        Join("access-control-allow-origin", ""),
        Join("age", ""),
        Join("allow", ""),
        Join("authorization", ""),
        Join("cache-control", ""),
        Join("content-disposition", ""),
        Join("content-encoding", ""),
        Join("content-language", ""),
        Join("content-length", ""),
        Join("content-location", ""),
        Join("content-range", ""),
        Join("content-type", ""),
        Join("cookie", ""),
        Join("date", ""),
        Join("etag", ""),
        Join("expect", ""),
        Join("expires", ""),
        Join("from", ""),
        Join("host", ""),
        Join("if-match", ""),
        Join("if-modified-since", ""),
        Join("if-none-match", ""),
        Join("if-range", ""),
        Join("if-unmodified-since", ""),
        Join("last-modified", ""),
        Join("link", ""),
        Join("location", ""),
        Join("max-forwards", ""),
        Join("proxy-authenticate", ""),
        Join("proxy-authorization", ""),
        Join("range", ""),
        Join("referer", ""),
        Join("refresh", ""),
        Join("retry-after", ""),
        Join("server", ""),
        Join("set-cookie", ""),
        Join("strict-transport-security", ""),
        Join("transfer-encoding", ""),
        Join("user-agent", ""),
        Join("vary", ""),
        Join("via", ""),
        Join("www-authenticate", "")
    )
    
    fun getIndex(name: String, value: String): Int? {
        val index = entries.indexOfFirst { it.a == name && it.b == value }
        return if (index >= 0) index + 1 else null
    }
}