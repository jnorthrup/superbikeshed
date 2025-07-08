@file:Suppress("FunctionName")

package borg.trikeshed.demos

import borg.trikeshed.lib.*
import borg.trikeshed.ccek.*
import borg.trikeshed.parse.bbcursive.ann.*
import kotlinx.coroutines.*

/**
 * CCEK + BBCursive for binary protocols
 * Shows how to handle length-prefixed messages, checksums, and binary framing
 */

// === SIMPLE BINARY PROTOCOL (like Protocol Buffers wire format) ===

object SimpleBinaryProtocol {
    
    // Wire types (similar to protobuf)
    enum class WireType(val value: Int) {
        VARINT(0),
        FIXED64(1),
        LENGTH_DELIMITED(2),
        FIXED32(5)
    }
    
    // Message field using Join
    typealias Field = Join<Int, Join<WireType, ByteArray>> // fieldNum j (wireType j data)
    typealias Message = Indexed<Field>
    
    // BBCursive ops for binary parsing
    fun interface Op { fun apply(buf: ByteIndexedBuffer): ByteIndexedBuffer? }
    
    @Backtracking
    fun varint(): Op = Op { buf ->
        var value = 0L
        var shift = 0
        
        while (buf.hasRemaining) {
            val b = buf.get
            value = value or ((b.toLong() and 0x7F) shl shift)
            if (b >= 0) return@Op buf // MSB not set, we're done
            shift += 7
            if (shift >= 64) return@Op null // Too many bytes
        }
        null // Ran out of bytes
    }
    
    @Backtracking
    fun fixed32(): Op = Op { buf ->
        if (buf.rem >= 4) {
            repeat(4) { buf.get }
            buf
        } else null
    }
    
    @Backtracking
    fun fixed64(): Op = Op { buf ->
        if (buf.rem >= 8) {
            repeat(8) { buf.get }
            buf
        } else null
    }
    
    // CCEK choreographer for binary protocol
    class BinaryProtocolChoreographer {
        
        suspend fun processMessage(
            data: ByteArray,
            ccek: CcekContext
        ): Any? = withContext(ccek) {
            val buf = ByteIndexedBuffer(data)
            
            when (ccek.environment.action) {
                "DECODE" -> decodeMessage(buf)
                "ENCODE" -> encodeMessage(ccek.environment.payload as Message)
                "VALIDATE" -> validateMessage(decodeMessage(buf), ccek.knowledge)
                else -> null
            }
        }
        
        internal fun decodeMessage(buf: ByteIndexedBuffer): Message? {
            val fields = mutableListOf<Field>()
            
            while (buf.hasRemaining) {
                // Read field header (field number + wire type)
                val headerStart = buf.pos
                varint().apply(buf) ?: return null
                val headerBytes = buf.extractBytes(headerStart, buf.pos)
                val header = decodeVarint(headerBytes)
                
                val fieldNum = (header shr 3).toInt()
                val wireType = WireType.values().find { it.value == (header and 0x7).toInt() }
                    ?: return null
                
                // Read field data based on wire type
                val dataStart = buf.pos
                when (wireType) {
                    WireType.VARINT -> {
                        varint().apply(buf) ?: return null
                    }
                    WireType.FIXED32 -> {
                        fixed32().apply(buf) ?: return null
                    }
                    WireType.FIXED64 -> {
                        fixed64().apply(buf) ?: return null
                    }
                    WireType.LENGTH_DELIMITED -> {
                        // Read length
                        val lenStart = buf.pos
                        varint().apply(buf) ?: return null
                        val lenBytes = buf.extractBytes(lenStart, buf.pos)
                        val len = decodeVarint(lenBytes).toInt()
                        
                        // Read data
                        if (buf.rem < len) return null
                        dataStart = buf.pos
                        repeat(len) { buf.get }
                    }
                }
                
                val data = buf.extractBytes(dataStart, buf.pos)
                fields.add(fieldNum j (wireType j data))
            }
            
            return fields.size j fields::get
        }
        
        internal fun encodeMessage(msg: Message): ByteArray {
            val result = mutableListOf<Byte>()
            
            for (i in 0 until msg.a) {
                val field = msg.b(i)
                val fieldNum = field.a
                val wireType = field.b.a
                val data = field.b.b
                
                // Encode field header
                val header = (fieldNum shl 3) or wireType.value
                result.addAll(encodeVarint(header.toLong()))
                
                // Encode field data
                when (wireType) {
                    WireType.LENGTH_DELIMITED -> {
                        result.addAll(encodeVarint(data.size.toLong()))
                    }
                    else -> { /* No length prefix needed */ }
                }
                result.addAll(data.toList())
            }
            
            return result.toByteArray()
        }
        
        internal fun validateMessage(msg: Message?, knowledge: Knowledge): Message? {
            msg ?: return null
            
            // Apply validation rules from knowledge
            for (i in 0 until knowledge.rules.a) {
                val rule = knowledge.rules.b(i)
                // Check field constraints, required fields, etc.
            }
            
            return if (knowledge.validator(msg)) msg else null
        }
        
        internal fun decodeVarint(bytes: ByteArray): Long {
            var value = 0L
            var shift = 0
            
            for (b in bytes) {
                value = value or ((b.toLong() and 0x7F) shl shift)
                if (b >= 0) break
                shift += 7
            }
            return value
        }
        
        internal fun encodeVarint(value: Long): List<Byte> {
            val result = mutableListOf<Byte>()
            var v = value
            
            while (v >= 0x80) {
                result.add(((v and 0x7F) or 0x80).toByte())
                v = v ushr 7
            }
            result.add(v.toByte())
            
            return result
        }
    }
}

// === FRAME-BASED PROTOCOL (like WebSocket frames) ===

object FrameProtocol {
    
    // Frame structure using Join
    typealias Frame = Join<FrameHeader, ByteArray> // header j payload
    typealias FrameHeader = Join<Int, Join<Boolean, Long>> // opcode j (masked j length)
    
    // Opcodes
    object OpCode {
        const val CONTINUATION = 0x0
        const val TEXT = 0x1
        const val BINARY = 0x2
        const val CLOSE = 0x8
        const val PING = 0x9
        const val PONG = 0xA
    }
    
    // CCEK frame processor
    class FrameProtocolProcessor {
        
        suspend fun processFrame(
            data: ByteArray,
            ccek: CcekContext
        ): Frame? = withContext(ccek) {
            val buf = ByteIndexedBuffer(data)
            
            when (ccek.control.phase) {
                ExecutionPhase.INIT -> parseFrame(buf)
                ExecutionPhase.TRANSFORM -> transformFrame(parseFrame(buf), ccek.knowledge)
                else -> null
            }
        }
        
        internal fun parseFrame(buf: ByteIndexedBuffer): Frame? {
            if (buf.rem < 2) return null
            
            // First byte: FIN (1 bit), RSV (3 bits), Opcode (4 bits)
            val b1 = buf.get.toInt() and 0xFF
            val fin = (b1 and 0x80) != 0
            val opcode = b1 and 0x0F
            
            // Second byte: MASK (1 bit), Payload length (7 bits)
            val b2 = buf.get.toInt() and 0xFF
            val masked = (b2 and 0x80) != 0
            var payloadLen = (b2 and 0x7F).toLong()
            
            // Extended payload length
            when (payloadLen) {
                126L -> {
                    if (buf.rem < 2) return null
                    payloadLen = ((buf.get.toInt() and 0xFF) shl 8) or
                                 (buf.get.toInt() and 0xFF).toLong()
                }
                127L -> {
                    if (buf.rem < 8) return null
                    payloadLen = 0L
                    repeat(8) { i ->
                        payloadLen = (payloadLen shl 8) or (buf.get.toLong() and 0xFF)
                    }
                }
            }
            
            // Masking key
            val maskKey = if (masked) {
                if (buf.rem < 4) return null
                ByteArray(4) { buf.get }
            } else null
            
            // Payload
            if (buf.rem < payloadLen) return null
            val payload = ByteArray(payloadLen.toInt()) { i ->
                val b = buf.get
                if (maskKey != null) {
                    (b.toInt() xor maskKey[i % 4].toInt()).toByte()
                } else b
            }
            
            val header = opcode j (masked j payloadLen)
            return header j payload
        }
        
        internal fun transformFrame(frame: Frame?, knowledge: Knowledge): Frame? {
            frame ?: return null
            
            // Apply transformations based on knowledge rules
            val opcode = frame.a.a
            
            // Example: Transform PING to PONG
            return if (opcode == OpCode.PING) {
                (OpCode.PONG j frame.a.b) j frame.b
            } else frame
        }
    }
}

// === DEMONSTRATION ===

suspend fun main() = coroutineScope {
    
    println("=== Binary Protocol Demo ===")
    
    // Example 1: Simple binary protocol (protobuf-like)
    val binaryProtocol = SimpleBinaryProtocol.BinaryProtocolChoreographer()
    
    val ccekDecode = CcekContext(
        control = Control("binary-demo"),
        context = Context("decode-session"),
        environment = Environment("DECODE", ""),
        knowledge = Knowledge(
            rules = EmptyIndexed,
            constraints = EmptyIndexed,
            validator = { true }
        )
    )
    
    // Create a simple message: field 1 = varint(150), field 2 = string("hello")
    val testMessage = byteArrayOf(
        0x08, 0x96.toByte(), 0x01,  // Field 1, varint 150
        0x12, 0x05,                   // Field 2, length-delimited
        'h'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 
        'l'.code.toByte(), 'o'.code.toByte()
    )
    
    val decoded = binaryProtocol.processMessage(testMessage, ccekDecode)
    println("Decoded binary message: $decoded")
    
    // Example 2: Frame-based protocol (WebSocket-like)
    val frameProtocol = FrameProtocol.FrameProtocolProcessor()
    
    val ccekFrame = CcekContext(
        control = Control("frame-demo", ExecutionPhase.INIT),
        context = Context("websocket-session"),
        environment = Environment("FRAME", ""),
        knowledge = Knowledge(
            rules = EmptyIndexed,
            constraints = EmptyIndexed,
            validator = { true }
        )
    )
    
    // Create a simple text frame: "Hello"
    val textFrame = byteArrayOf(
        0x81.toByte(),  // FIN=1, opcode=TEXT
        0x05,           // No mask, length=5
        'H'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(),
        'l'.code.toByte(), 'o'.code.toByte()
    )
    
    val parsedFrame = frameProtocol.processFrame(textFrame, ccekFrame)
    println("\nParsed WebSocket frame: $parsedFrame")
    if (parsedFrame != null) {
        println("  Opcode: ${parsedFrame.a.a}")
        println("  Masked: ${parsedFrame.a.b.a}")
        println("  Length: ${parsedFrame.a.b.b}")
        println("  Payload: ${parsedFrame.b.decodeToString()}")
    }
    
    // Transform PING to PONG
    val ccekTransform = ccekFrame.copy(
        control = Control("frame-demo", ExecutionPhase.TRANSFORM)
    )
    
    val pingFrame = byteArrayOf(
        0x89.toByte(),  // FIN=1, opcode=PING
        0x00            // No mask, length=0
    )
    
    val pongFrame = frameProtocol.processFrame(pingFrame, ccekTransform)
    println("\nTransformed PING to PONG: $pongFrame")
}

// Extended ByteIndexedBuffer with helper methods
internal fun ByteIndexedBuffer.extractBytes(start: Int, end: Int): ByteArray {
    val saved = pos
    pos(start)
    val result = ByteArray(end - start) { get }
    pos(saved)
    return result
}