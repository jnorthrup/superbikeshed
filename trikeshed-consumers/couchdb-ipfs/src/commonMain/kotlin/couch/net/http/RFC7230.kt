@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.consumers.couchdbipfs.net.http

import borg.trikeshed.lib.*

// RFC 7230: HTTP/1.1 Message Syntax and Routing Implementation

// ===== MESSAGE SYNTAX (Section 3) =====

@JvmInline value class HttpToken(val value: String)
@JvmInline value class HttpQuotedString(val value: String) 
@JvmInline value class HttpComment(val value: String)
@JvmInline value class HttpFieldName(val value: String)
@JvmInline value class HttpFieldValue(val value: String)

// HTTP-message = start-line *( header-field CRLF ) CRLF [ message-body ]
data class HttpMessage(
    val startLine: HttpStartLine,
    val headerFields: Series2<HttpFieldName, HttpFieldValue>,
    val messageBody: Series<Byte>
)

// start-line = request-line / status-line
sealed class HttpStartLine

// ===== REQUEST LINE (Section 3.1.1) =====
// request-line = method SP request-target SP HTTP-version CRLF

@JvmInline value class HttpRequestTarget(val value: String)

data class HttpRequestLine(
    val method: HttpMethod,
    val requestTarget: HttpRequestTarget, 
    val httpVersion: HttpVersion
) : HttpStartLine()

// ===== STATUS LINE (Section 3.1.2) =====
// status-line = HTTP-version SP status-code SP reason-phrase CRLF

data class HttpStatusLine(
    val httpVersion: HttpVersion,
    val statusCode: HttpStatusCode,
    val reasonPhrase: HttpReasonPhrase
) : HttpStartLine()

// ===== HEADER FIELDS (Section 3.2) =====
// header-field = field-name ":" OWS field-value OWS

object HttpParser {
    
    // ===== ABNF CHARACTER SETS (Appendix B) =====
    
    private val VCHAR = (0x21..0x7E).map { it.toChar() }.toSet()  // visible characters
    private val WSP = setOf(' ', '\t')  // whitespace
    private val OWS_CHARS = WSP  // optional whitespace
    private val TCHAR = "!#\$&'*+-.^_`|~".toSet() + ('a'..'z') + ('A'..'Z') + ('0'..'9')
    
    fun isToken(char: Char): Boolean = char in TCHAR
    fun isOWS(char: Char): Boolean = char in OWS_CHARS
    fun isVCHAR(char: Char): Boolean = char in VCHAR
    
    // ===== MESSAGE PARSING =====
    
    fun parseHttpMessage(input: Series<Char>): HttpMessage? {
        var pos = 0
        
        // Parse start-line
        val startLineEnd = findCRLF(input, pos) ?: return null
        val startLineChars = input.α { input[it] }.take(startLineEnd - pos)
        val startLine = parseStartLine(startLineChars) ?: return null
        pos = startLineEnd + 2  // Skip CRLF
        
        // Parse header fields
        val headerFields = mutableListOf<Join<HttpFieldName, HttpFieldValue>>()
        while (pos < input.size) {
            val lineEnd = findCRLF(input, pos)
            if (lineEnd == null || lineEnd == pos) {
                // Empty line indicates end of headers
                pos += 2  // Skip CRLF
                break
            }
            
            val headerLine = (lineEnd - pos) j { i -> input[pos + i] }
            val headerField = parseHeaderField(headerLine)
            if (headerField != null) {
                headerFields.add(headerField)
            }
            pos = lineEnd + 2  // Skip CRLF
        }
        
        // Parse message body (remainder)
        val messageBody = (input.size - pos) j { i -> input[pos + i].code.toByte() }
        
        return HttpMessage(
            startLine = startLine,
            headerFields = headerFields.size j { headerFields[it] },
            messageBody = messageBody
        )
    }
    
    private fun parseStartLine(line: Series<Char>): HttpStartLine? {
        val lineStr = line.`▶`.joinToString("")
        val parts = lineStr.split(' ')
        
        return when {
            // Request line: METHOD SP request-target SP HTTP-version
            parts.size == 3 && parts[2].startsWith("HTTP/") -> {
                val method = HttpMethod.values().find { it.name == parts[0] } ?: return null
                HttpRequestLine(
                    method = method,
                    requestTarget = HttpRequestTarget(parts[1]),
                    httpVersion = HttpVersion(parts[2])
                )
            }
            // Status line: HTTP-version SP status-code SP reason-phrase
            parts.size >= 3 && parts[0].startsWith("HTTP/") -> {
                val statusCode = parts[1].toIntOrNull() ?: return null
                val reasonPhrase = parts.drop(2).joinToString(" ")
                HttpStatusLine(
                    httpVersion = HttpVersion(parts[0]),
                    statusCode = HttpStatusCode(statusCode),
                    reasonPhrase = HttpReasonPhrase(reasonPhrase)
                )
            }
            else -> null
        }
    }
    
    // ===== HEADER FIELD PARSING (Section 3.2) =====
    
    fun parseHeaderField(line: Series<Char>): Join<HttpFieldName, HttpFieldValue>? {
        val colonPos = line.`▶`.indexOfFirst { it == ':' }
        if (colonPos == -1) return null
        
        // field-name = token
        val fieldNameChars = colonPos j { line[it] }
        val fieldName = fieldNameChars.`▶`.joinToString("")
        if (!isValidToken(fieldName)) return null
        
        // field-value = *( field-content / obs-fold )
        var valueStart = colonPos + 1
        
        // Skip OWS after colon
        while (valueStart < line.size && isOWS(line[valueStart])) {
            valueStart++
        }
        
        // Extract field value, trimming trailing OWS
        var valueEnd = line.size
        while (valueEnd > valueStart && isOWS(line[valueEnd - 1])) {
            valueEnd--
        }
        
        val fieldValueChars = (valueEnd - valueStart) j { line[valueStart + it] }
        val fieldValue = fieldValueChars.`▶`.joinToString("")
        
        return HttpFieldName(fieldName) j HttpFieldValue(fieldValue)
    }
    
    private fun isValidToken(str: String): Boolean {
        return str.isNotEmpty() && str.all { isToken(it) }
    }
    
    // ===== CHUNKED TRANSFER ENCODING (Section 4.1) =====
    
    data class ChunkedBody(
        val chunks: Series<ChunkData>,
        val trailerFields: Series2<HttpFieldName, HttpFieldValue>
    )
    
    data class ChunkData(
        val size: Int,
        val extension: String,
        val data: Series<Byte>
    )
    
    fun parseChunkedBody(input: Series<Char>): ChunkedBody? {
        val chunks = mutableListOf<ChunkData>()
        var pos = 0
        
        while (pos < input.size) {
            // Parse chunk-size
            val sizeLineEnd = findCRLF(input, pos) ?: return null
            val sizeLine = (sizeLineEnd - pos) j { input[pos + it] }
            val sizeStr = sizeLine.`▶`.joinToString("").split(';')[0].trim()
            val chunkSize = sizeStr.toIntOrNull(16) ?: return null
            
            pos = sizeLineEnd + 2  // Skip CRLF
            
            if (chunkSize == 0) {
                // Last chunk, parse trailer fields
                val trailerFields = mutableListOf<Join<HttpFieldName, HttpFieldValue>>()
                while (pos < input.size) {
                    val lineEnd = findCRLF(input, pos)
                    if (lineEnd == null || lineEnd == pos) break
                    
                    val trailerLine = (lineEnd - pos) j { input[pos + it] }
                    val trailerField = parseHeaderField(trailerLine)
                    if (trailerField != null) {
                        trailerFields.add(trailerField)
                    }
                    pos = lineEnd + 2
                }
                
                return ChunkedBody(
                    chunks = chunks.size j { chunks[it] },
                    trailerFields = trailerFields.size j { trailerFields[it] }
                )
            }
            
            // Read chunk data
            if (pos + chunkSize + 2 > input.size) return null
            val chunkData = chunkSize j { input[pos + it].code.toByte() }
            chunks.add(ChunkData(chunkSize, "", chunkData))
            pos += chunkSize + 2  // Skip data + CRLF
        }
        
        return null
    }
    
    // ===== CONNECTION MANAGEMENT (Section 6) =====
    
    enum class ConnectionOption {
        CLOSE, KEEP_ALIVE, UPGRADE
    }
    
    fun parseConnectionHeader(value: HttpFieldValue): Series<ConnectionOption> {
        val options = value.value.split(',').mapNotNull { option ->
            when (option.trim().lowercase()) {
                "close" -> ConnectionOption.CLOSE
                "keep-alive" -> ConnectionOption.KEEP_ALIVE
                "upgrade" -> ConnectionOption.UPGRADE
                else -> null
            }
        }
        return options.size j { options[it] }
    }
    
    // ===== UTILITY FUNCTIONS =====
    
    private fun findCRLF(input: Series<Char>, start: Int): Int? {
        for (i in start until input.size - 1) {
            if (input[i] == '\r' && input[i + 1] == '\n') {
                return i
            }
        }
        return null
    }
}

// ===== MESSAGE SERIALIZATION =====

object HttpSerializer {
    
    fun serializeHttpMessage(message: HttpMessage): Series<Char> {
        val startLineChars = serializeStartLine(message.startLine)
        val headerChars = serializeHeaders(message.headerFields)
        val crlfChars = 2 j { if (it == 0) '\r' else '\n' }
        val bodyChars = message.messageBody.α { it.toInt().toChar() }
        
        val totalSize = startLineChars.size + headerChars.size + crlfChars.size + bodyChars.size
        return totalSize j { i ->
            when {
                i < startLineChars.size -> startLineChars[i]
                i < startLineChars.size + headerChars.size -> headerChars[i - startLineChars.size]
                i < startLineChars.size + headerChars.size + crlfChars.size -> crlfChars[i - startLineChars.size - headerChars.size]
                else -> bodyChars[i - startLineChars.size - headerChars.size - crlfChars.size]
            }
        }
    }
    
    private fun serializeStartLine(startLine: HttpStartLine): Series<Char> {
        val line = when (startLine) {
            is HttpRequestLine -> "${startLine.method.name} ${startLine.requestTarget.value} ${startLine.httpVersion.value}\r\n"
            is HttpStatusLine -> "${startLine.httpVersion.value} ${startLine.statusCode.value} ${startLine.reasonPhrase.value}\r\n"
        }
        return line.length j { line[it] }
    }
    
    private fun serializeHeaders(headers: Series2<HttpFieldName, HttpFieldValue>): Series<Char> {
        val headerLines = headers.`▶`.map { join ->
            "${join.a.value}: ${join.b.value}\r\n"
        }
        val totalLength = headerLines.sumOf { it.length }
        var pos = 0
        return totalLength j { i ->
            for (line in headerLines) {
                if (i >= pos && i < pos + line.length) {
                    return@j line[i - pos]
                }
                pos += line.length
            }
            ' '  // Should not reach here
        }
    }
    
    fun serializeChunkedBody(body: ChunkedBody): Series<Char> {
        val chunks = body.chunks.`▶`.flatMap { chunk ->
            val sizeHex = chunk.size.toString(16)
            val chunkLine = "$sizeHex\r\n"
            val dataChars = chunk.data.α { it.toInt().toChar() }
            val crlfChars = "\r\n"
            (chunkLine + dataChars.`▶`.joinToString("") + crlfChars).toList()
        }
        
        val lastChunk = "0\r\n"
        val trailerChars = body.trailerFields.`▶`.flatMap { join ->
            "${join.a.value}: ${join.b.value}\r\n".toList()
        }
        val finalCrlf = "\r\n"
        
        val allChars = chunks + lastChunk.toList() + trailerChars + finalCrlf.toList()
        return allChars.size j { allChars[it] }
    }
}

// ===== HTTP UPGRADING (Section 6.7) =====

@JvmInline value class ProtocolName(val value: String)
@JvmInline value class ProtocolVersion(val value: String)

data class UpgradeProtocol(
    val name: ProtocolName,
    val version: ProtocolVersion? = null
)

object HttpUpgrade {
    
    fun parseUpgradeHeader(value: HttpFieldValue): Series<UpgradeProtocol> {
        val protocols = value.value.split(',').mapNotNull { protocol ->
            val parts = protocol.trim().split('/')
            when (parts.size) {
                1 -> UpgradeProtocol(ProtocolName(parts[0]))
                2 -> UpgradeProtocol(ProtocolName(parts[0]), ProtocolVersion(parts[1]))
                else -> null
            }
        }
        return protocols.size j { protocols[it] }
    }
    
    fun canUpgrade(requestHeaders: Series2<HttpFieldName, HttpFieldValue>, protocol: ProtocolName): Boolean {
        val connectionOptions = requestHeaders.`▶`
            .find { it.a.value.lowercase() == "connection" }
            ?.let { HttpParser.parseConnectionHeader(it.b) }
            ?: return false
            
        val hasUpgrade = connectionOptions.`▶`.contains(HttpParser.ConnectionOption.UPGRADE)
        if (!hasUpgrade) return false
        
        val upgradeProtocols = requestHeaders.`▶`
            .find { it.a.value.lowercase() == "upgrade" }
            ?.let { parseUpgradeHeader(it.b) }
            ?: return false
            
        return upgradeProtocols.`▶`.any { it.name.value.lowercase() == protocol.value.lowercase() }
    }
}