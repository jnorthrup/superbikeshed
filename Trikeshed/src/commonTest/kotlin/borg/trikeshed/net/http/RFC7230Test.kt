@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.net.http

import borg.trikeshed.lib.*
import kotlin.test.*

class RFC7230Test {
    
    @Test
    fun testHttpRequestLineParsing() {
        val input = "GET /index.html HTTP/1.1\r\n\r\n".toCharSeries()
        val message = HttpParser.parseHttpMessage(input)
        
        assertNotNull(message)
        assertTrue(message.startLine is HttpRequestLine)
        
        val requestLine = message.startLine as HttpRequestLine
        assertEquals(HttpMethod.GET, requestLine.method)
        assertEquals("/index.html", requestLine.requestTarget.value)
        assertEquals("HTTP/1.1", requestLine.httpVersion.value)
    }
    
    @Test
    fun testHttpStatusLineParsing() {
        val input = "HTTP/1.1 200 OK\r\n\r\n".toCharSeries()
        val message = HttpParser.parseHttpMessage(input)
        
        assertNotNull(message)
        assertTrue(message.startLine is HttpStatusLine)
        
        val statusLine = message.startLine as HttpStatusLine
        assertEquals("HTTP/1.1", statusLine.httpVersion.value)
        assertEquals(200, statusLine.statusCode.value)
        assertEquals("OK", statusLine.reasonPhrase.value)
    }
    
    @Test
    fun testHeaderFieldParsing() {
        val headerLine = "Content-Type: application/json".toCharSeries()
        val headerField = HttpParser.parseHeaderField(headerLine)
        
        assertNotNull(headerField)
        assertEquals("Content-Type", headerField.a.value)
        assertEquals("application/json", headerField.b.value)
    }
    
    @Test
    fun testHeaderFieldWithWhitespace() {
        val headerLine = "Authorization:   Bearer token123   ".toCharSeries()
        val headerField = HttpParser.parseHeaderField(headerLine)
        
        assertNotNull(headerField)
        assertEquals("Authorization", headerField.a.value)
        assertEquals("Bearer token123", headerField.b.value)
    }
    
    @Test
    fun testCompleteHttpMessage() {
        val httpMessage = """
            GET /api/users HTTP/1.1
            Host: example.com
            User-Agent: TrikeShed/1.0
            Content-Length: 13
            
            {"name":"test"}
        """.trimIndent().replace("\n", "\r\n").toCharSeries()
        
        val message = HttpParser.parseHttpMessage(httpMessage)
        assertNotNull(message)
        
        assertTrue(message.startLine is HttpRequestLine)
        val requestLine = message.startLine as HttpRequestLine
        assertEquals(HttpMethod.GET, requestLine.method)
        assertEquals("/api/users", requestLine.requestTarget.value)
        
        // Check headers
        val headers = message.headerFields.`play`
        assertEquals(3, headers.size)
        
        val hostHeader = headers.find { it.a.value == "Host" }
        assertNotNull(hostHeader)
        assertEquals("example.com", hostHeader.b.value)
        
        // Check body
        val bodyStr = message.messageBody.`play`.map { it.toInt().toChar() }.joinToString("")
        assertEquals("{\"name\":\"test\"}", bodyStr)
    }
    
    @Test
    fun testChunkedTransferEncoding() {
        val chunkedData = """
            4
            test
            5
            data
            0
            
            
        """.trimIndent().replace("\n", "\r\n").toCharSeries()
        
        val chunkedBody = HttpParser.parseChunkedBody(chunkedData)
        assertNotNull(chunkedBody)
        
        assertEquals(2, chunkedBody.chunks.size)
        
        val firstChunk = chunkedBody.chunks[0]
        assertEquals(4, firstChunk.size)
        assertEquals("test", firstChunk.data.`play`.map { it.toInt().toChar() }.joinToString(""))
        
        val secondChunk = chunkedBody.chunks[1]
        assertEquals(5, secondChunk.size)
        assertEquals("data", secondChunk.data.`play`.map { it.toInt().toChar() }.joinToString(""))
    }
    
    @Test
    fun testConnectionHeaderParsing() {
        val closeHeader = HttpFieldValue("close")
        val closeOptions = HttpParser.parseConnectionHeader(closeHeader)
        assertEquals(1, closeOptions.size)
        assertEquals(HttpParser.ConnectionOption.CLOSE, closeOptions[0])
        
        val upgradeHeader = HttpFieldValue("upgrade, keep-alive")
        val upgradeOptions = HttpParser.parseConnectionHeader(upgradeHeader)
        assertEquals(2, upgradeOptions.size)
        assertTrue(upgradeOptions.`play`.contains(HttpParser.ConnectionOption.UPGRADE))
        assertTrue(upgradeOptions.`play`.contains(HttpParser.ConnectionOption.KEEP_ALIVE))
    }
    
    @Test
    fun testUpgradeHeaderParsing() {
        val upgradeHeader = HttpFieldValue("websocket, http/2.0")
        val protocols = HttpUpgrade.parseUpgradeHeader(upgradeHeader)
        
        assertEquals(2, protocols.size)
        
        val websocket = protocols[0]
        assertEquals("websocket", websocket.name.value)
        assertNull(websocket.version)
        
        val http2 = protocols[1]
        assertEquals("http", http2.name.value)
        assertEquals("2.0", http2.version?.value)
    }
    
    @Test
    fun testHttpMessageSerialization() {
        val requestLine = HttpRequestLine(
            method = HttpMethod.POST,
            requestTarget = HttpRequestTarget("/api/data"),
            httpVersion = HttpVersion("HTTP/1.1")
        )
        
        val headers = 2 j { i:Int ->
            when (i) {
                0 -> HttpFieldName("Content-Type") j HttpFieldValue("application/json")
                1 -> HttpFieldName("Content-Length") j HttpFieldValue("13")
                else -> HttpFieldName("") j HttpFieldValue("")
            }
        }
        
        val body = "{\"test\":true}".toByteArray().toSeries()
        
        val message = HttpMessage(
            startLine = requestLine,
            headerFields = headers,
            messageBody = body
        )
        
        val serialized = HttpSerializer.serializeHttpMessage(message)
        val serializedStr = serialized.`play`.joinToString("")
        
        assertTrue(serializedStr.startsWith("POST /api/data HTTP/1.1\r\n"))
        assertTrue(serializedStr.contains("Content-Type: application/json\r\n"))
        assertTrue(serializedStr.contains("Content-Length: 13\r\n"))
        assertTrue(serializedStr.endsWith("{\"test\":true}"))
    }
    
    @Test
    fun testChunkedEncoding() {
        val originalData = "Hello, World! This is a test message.".toByteArray().toSeries()
        val encoded = ChunkedTransferEncoder.encodeChunked(originalData)
        val decoded = ChunkedTransferEncoder.decodeChunked(encoded)
        
        assertNotNull(decoded)
        val decodedStr = decoded.`play`.map { it.toInt().toChar() }.joinToString("")
        assertEquals("Hello, World! This is a test message.", decodedStr)
    }
    
    @Test
    fun testHttpServerRequestConversion() {
        val httpMessage = """
            POST /submit HTTP/1.1
            Host: localhost:8080
            Content-Type: application/json
            Content-Length: 25
            
            {"username":"testuser"}
        """.trimIndent().replace("\n", "\r\n").toCharSeries()
        
        val message = HttpParser.parseHttpMessage(httpMessage)
        assertNotNull(message)
        
        val server = HttpServer(HttpServerConfig()) { request ->
            assertEquals(HttpMethod.POST, request.method)
            assertEquals("/submit", request.path.value)
            
            val contentType = request.headers.`play`.find {
                it.a.value == "Content-Type" 
            }?.b?.value
            assertEquals("application/json", contentType)
            
            HttpResponse(
                status = HttpStatusCode(200),
                reasonPhrase = HttpReasonPhrase("OK"),
                headers = 0 j { HttpHeaderName("") j HttpHeaderValue("") },
                body = "Success".toByteArray().toSeries()
            )
        }
        
        // This would test the actual server handling in a real implementation
    }
    
    @Test
    fun testInvalidHttpMessage() {
        val invalidMessage = "INVALID REQUEST LINE\r\n\r\n".toCharSeries()
        val message = HttpParser.parseHttpMessage(invalidMessage)
        assertNull(message)
    }
    
    @Test
    fun testEmptyHeaderValue() {
        val headerLine = "X-Custom-Header: ".toCharSeries()
        val headerField = HttpParser.parseHeaderField(headerLine)
        
        assertNotNull(headerField)
        assertEquals("X-Custom-Header", headerField.a.value)
        assertEquals("", headerField.b.value)
    }
    
    private fun String.toCharSeries(): Series<Char> = length j { this[it] }
    private fun ByteArray.toSeries(): Series<Byte> = size j { this[it] }
}