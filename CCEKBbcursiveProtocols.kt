@file:Suppress("FunctionName")

package borg.trikeshed.demos

import borg.trikeshed.lib.*
import borg.trikeshed.ccek.*
import borg.trikeshed.parse.bbcursive.ann.*
import kotlinx.coroutines.*

/**
 * Demonstration of CCEK + BBCursive for simple protocol implementations
 * Shows how minimal code can handle real protocols with these two patterns
 */

// === SIMPLE LINE PROTOCOL (like Redis RESP) ===

object SimpleLineProtocol {
    
    // BBCursive parser ops
    fun interface Op { fun apply(buf: ByteIndexedBuffer): ByteIndexedBuffer? }
    
    @Backtracking
    fun crlf(): Op = Op { buf ->
        if (buf.rem >= 2 && buf.get == '\r'.code.toByte() && buf.get == '\n'.code.toByte()) 
            buf else null
    }
    
    @Backtracking
    fun simpleLine(): Op = Op { buf ->
        val start = buf.pos
        while (buf.hasRemaining && buf.peek != '\r'.code.toByte()) buf.inc()
        if (buf.pos > start) buf else null
    }
    
    // Protocol message types
    sealed class Message {
        data class SimpleString(val value: String) : Message()
        data class Error(val message: String) : Message()
        data class Integer(val value: Long) : Message()
        data class BulkString(val data: ByteArray?) : Message()
        data class Array(val elements: Indexed<Message>) : Message()
    }
    
    // CCEK choreographer for the protocol
    class LineProtocolChoreographer {
        
        suspend fun processMessage(
            data: ByteArray,
            ccek: CcekContext
        ): Message? = withContext(ccek) {
            val buf = ByteIndexedBuffer(data)
            
            when (ccek.environment.action) {
                "PARSE" -> parseMessage(buf)
                "VALIDATE" -> validateAndParse(buf, ccek.knowledge)
                else -> null
            }
        }
        
        internal fun parseMessage(buf: ByteIndexedBuffer): Message? {
            if (!buf.hasRemaining) return null
            
            return when (buf.get) {
                '+'.code.toByte() -> {
                    val line = captureUntilCrlf(buf) ?: return null
                    Message.SimpleString(line)
                }
                '-'.code.toByte() -> {
                    val line = captureUntilCrlf(buf) ?: return null
                    Message.Error(line)
                }
                ':'.code.toByte() -> {
                    val line = captureUntilCrlf(buf) ?: return null
                    Message.Integer(line.toLongOrNull() ?: return null)
                }
                '$'.code.toByte() -> {
                    val lenStr = captureUntilCrlf(buf) ?: return null
                    val len = lenStr.toIntOrNull() ?: return null
                    if (len < 0) return Message.BulkString(null)
                    
                    if (buf.rem < len + 2) return null
                    val data = ByteArray(len)
                    for (i in 0 until len) data[i] = buf.get
                    crlf().apply(buf) ?: return null
                    Message.BulkString(data)
                }
                '*'.code.toByte() -> {
                    val countStr = captureUntilCrlf(buf) ?: return null
                    val count = countStr.toIntOrNull() ?: return null
                    if (count < 0) return Message.Array(EmptyIndexed)
                    
                    val elements = Array(count) { parseMessage(buf) ?: return null }
                    Message.Array(count j elements::get)
                }
                else -> null
            }
        }
        
        internal fun validateAndParse(
            buf: ByteIndexedBuffer,
            knowledge: Knowledge
        ): Message? {
            val msg = parseMessage(buf) ?: return null
            
            // Apply knowledge constraints
            for (i in 0 until knowledge.constraints.component1()) {
                val constraint = knowledge.constraints.component2()(i)
                if (!validateConstraint(msg, constraint)) return null
            }
            
            return msg
        }
        
        internal fun validateConstraint(msg: Message, constraint: Constraint): Boolean {
            // Simple validation logic
            return when (constraint.validation) {
                is ConstraintValidation.Custom -> {
                    // Apply custom validation
                    true
                }
                else -> true
            }
        }
        
        internal fun captureUntilCrlf(buf: ByteIndexedBuffer): String? {
            val start = buf.pos
            while (buf.hasRemaining && buf.peek != '\r'.code.toByte()) buf.inc()
            if (buf.pos == start) return null
            
            val bytes = ByteArray(buf.pos - start)
            val saved = buf.pos
            buf.pos(start)
            for (i in bytes.indices) bytes[i] = buf.get
            buf.pos(saved)
            
            crlf().apply(buf) ?: return null
            return bytes.decodeToString()
        }
    }
}

// === SIMPLE HTTP-LIKE PROTOCOL ===

object SimpleHttpProtocol {
    
    // Protocol structures using Join
    typealias Headers = Indexed<Join<String, String>>
    typealias Request = Join<RequestLine, Headers j ByteArray?>
    typealias RequestLine = Join<String, String j String> // method j (path j version)
    
    // BBCursive parsers
    fun interface Op { fun apply(buf: ByteIndexedBuffer): ByteIndexedBuffer? }
    
    @Backtracking
    fun space(): Op = Op { buf ->
        if (buf.hasRemaining && buf.get == ' '.code.toByte()) buf else null
    }
    
    @Backtracking
    fun token(): Op = Op { buf ->
        val start = buf.pos
        while (buf.hasRemaining && buf.peek.toInt().toChar().isLetterOrDigit()) buf.inc()
        if (buf.pos > start) buf else null
    }
    
    @Backtracking
    fun path(): Op = Op { buf ->
        val start = buf.pos
        while (buf.hasRemaining && buf.peek != ' '.code.toByte()) buf.inc()
        if (buf.pos > start) buf else null
    }
    
    // CCEK protocol handler
    class HttpProtocolHandler {
        
        suspend fun handleRequest(
            data: ByteArray,
            ccek: CcekContext
        ): Any? = withContext(ccek) {
            val buf = ByteIndexedBuffer(data)
            
            when (ccek.control.phase) {
                ExecutionPhase.INIT -> parseRequest(buf)
                ExecutionPhase.VALIDATE -> validateRequest(parseRequest(buf), ccek.knowledge)
                ExecutionPhase.TRANSFORM -> transformRequest(parseRequest(buf), ccek.knowledge)
                else -> null
            }
        }
        
        internal fun parseRequest(buf: ByteIndexedBuffer): Request? {
            // Parse request line: METHOD /path HTTP/1.1
            val method = captureToken(buf) ?: return null
            space().apply(buf) ?: return null
            
            val pathStr = capturePath(buf) ?: return null
            space().apply(buf) ?: return null
            
            val version = captureUntilCrlf(buf) ?: return null
            
            val requestLine = method j (pathStr j version)
            
            // Parse headers
            val headersList = mutableListOf<Join<String, String>>()
            while (true) {
                val line = captureUntilCrlf(buf) ?: break
                if (line.isEmpty()) break
                
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val name = line.substring(0, colonIdx).trim()
                    val value = line.substring(colonIdx + 1).trim()
                    headersList.add(name j value)
                }
            }
            
            val headers = headersList.size j headersList::get
            
            // Remaining is body
            val body = if (buf.hasRemaining) {
                ByteArray(buf.rem).also { bytes ->
                    for (i in bytes.indices) bytes[i] = buf.get
                }
            } else null
            
            return requestLine j (headers j body)
        }
        
        internal fun validateRequest(req: Request?, knowledge: Knowledge): Request? {
            req ?: return null
            
            // Validate using knowledge rules
            val validator = knowledge.validator
            return if (validator(req)) req else null
        }
        
        internal fun transformRequest(req: Request?, knowledge: Knowledge): Any? {
            req ?: return null
            
            // Apply transformation rules
            var result: Any = req
            for (i in 0 until knowledge.rules.component1()) {
                val rule = knowledge.rules.component2()(i)
                // Apply rule transformations
            }
            
            return result
        }
        
        internal fun captureToken(buf: ByteIndexedBuffer): String? {
            val start = buf.pos
            val result = token().apply(buf) ?: return null
            val end = result.pos
            
            val bytes = ByteArray(end - start)
            val saved = buf.pos
            buf.pos(start)
            for (i in bytes.indices) bytes[i] = buf.get
            buf.pos(saved)
            
            return bytes.decodeToString()
        }
        
        internal fun capturePath(buf: ByteIndexedBuffer): String? {
            val start = buf.pos
            val result = path().apply(buf) ?: return null
            val end = result.pos
            
            val bytes = ByteArray(end - start)
            val saved = buf.pos
            buf.pos(start)
            for (i in bytes.indices) bytes[i] = buf.get
            buf.pos(saved)
            
            return bytes.decodeToString()
        }
        
        internal fun captureUntilCrlf(buf: ByteIndexedBuffer): String? {
            val start = buf.pos
            while (buf.hasRemaining && buf.peek != '\r'.code.toByte()) buf.inc()
            
            val bytes = ByteArray(buf.pos - start)
            val saved = buf.pos
            buf.pos(start)
            for (i in bytes.indices) bytes[i] = buf.get
            buf.pos(saved)
            
            // Skip CRLF
            if (buf.rem >= 2 && buf.get == '\r'.code.toByte() && buf.get == '\n'.code.toByte()) {
                return bytes.decodeToString()
            }
            return null
        }
    }
}

// === DEMONSTRATION ===

suspend fun main() = coroutineScope {
    
    // Example 1: Simple line protocol (Redis-like)
    val lineProtocol = SimpleLineProtocol.LineProtocolChoreographer()
    
    val ccekParse = CcekContext(
        control = Control("demo-1"),
        context = Context("session-1"),
        environment = Environment("PARSE", ""),
        knowledge = Knowledge(
            rules = EmptyIndexed,
            constraints = EmptyIndexed,
            validator = { true }
        )
    )
    
    // Parse different message types
    val messages = listOf(
        "+OK\r\n",
        "-ERR unknown command\r\n",
        ":42\r\n",
        "$5\r\nhello\r\n",
        "*2\r\n$5\r\nhello\r\n$5\r\nworld\r\n"
    )
    
    println("=== Simple Line Protocol Demo ===")
    for (msg in messages) {
        val parsed = lineProtocol.processMessage(msg.encodeToByteArray(), ccekParse)
        println("Input: ${msg.replace("\r\n", "\\r\\n")}")
        println("Parsed: $parsed")
        println()
    }
    
    // Example 2: HTTP-like protocol
    val httpProtocol = SimpleHttpProtocol.HttpProtocolHandler()
    
    val ccekHttp = CcekContext(
        control = Control("demo-2", ExecutionPhase.INIT),
        context = Context("http-session"),
        environment = Environment("HTTP", ""),
        knowledge = Knowledge(
            rules = EmptyIndexed,
            constraints = EmptyIndexed,
            validator = { req ->
                // Validate it's a proper HTTP request
                req is SimpleHttpProtocol.Request
            }
        )
    )
    
    val httpRequest = """GET /api/users HTTP/1.1
Host: example.com
User-Agent: CCEK/1.0
Accept: application/json

""".replace("\n", "\r\n")
    
    println("=== HTTP Protocol Demo ===")
    val parsed = httpProtocol.handleRequest(httpRequest.encodeToByteArray(), ccekHttp)
    println("Parsed HTTP Request: $parsed")
}

// Helper class for byte buffer operations
class ByteIndexedBuffer(input: String) {
    constructor(bytes: ByteArray) : this(bytes.decodeToString())
    
    internal val data = input.encodeToByteArray()
    var pos = 0
    
    val rem: Int get() = data.size - pos
    val hasRemaining: Boolean get() = pos < data.size
    val peek: Byte get() = if (hasRemaining) data[pos] else -1
    
    val get: Byte get() {
        if (!hasRemaining) throw IndexOutOfBoundsException()
        return data[pos++]
    }
    
    fun inc() { if (hasRemaining) pos++ }
    fun dec() { if (pos > 0) pos-- }
    fun pos(newPos: Int) { pos = newPos }
    
    // Whitespace operations
    val skipWs: ByteIndexedBuffer get() {
        while (hasRemaining && data[pos].toInt().toChar().isWhitespace()) pos++
        return this
    }
}