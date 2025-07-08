package tests.tdd

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import borg.trikeshed.lib.*
import borg.trikeshed.net.quic.*
import borg.trikeshed.net.http.*
import borg.trikeshed.io.IOContext

/**
 * Comprehensive TDD tests for QUIC and HTTP servers and clients
 * These tests drive the development of fully functional implementations
 */
class QuicHttpServerClientTest {

    @Test
    fun `QUIC server should start and accept connections`() = runTest {
        // Given: A QUIC server configuration
        val serverConfig = QuicServerConfig(
            port = 9443,
            host = "127.0.0.1",
            maxConnections = 1000,
            idleTimeoutMs = 30000
        )
        
        // When: Server is started
        val server = QuicServer(serverConfig)
        val serverJob = launch { server.start() }
        
        // Then: Server should be running
        assertTrue(server.isRunning())
        
        // Cleanup
        server.stop()
        serverJob.cancel()
    }

    @Test
    fun `QUIC client should connect to server`() = runTest {
        // Given: A running QUIC server
        val server = QuicServer(QuicServerConfig(port = 9444))
        val serverJob = launch { server.start() }
        
        // And: A QUIC client
        val client = QuicClient(QuicClientConfig())
        
        // When: Client connects to server
        val connection = client.connect("127.0.0.1", 9444)
        
        // Then: Connection should be established
        assertNotNull(connection)
        assertTrue(connection.isConnected())
        
        // Cleanup
        connection.close()
        client.close()
        server.stop()
        serverJob.cancel()
    }

    @Test
    fun `QUIC should support bidirectional streams`() = runTest {
        // Given: Connected client and server
        val server = QuicServer(QuicServerConfig(port = 9445))
        val serverJob = launch { server.start() }
        
        val client = QuicClient(QuicClientConfig())
        val connection = client.connect("127.0.0.1", 9445)
        
        // When: Client creates a bidirectional stream
        val stream = connection.createBidirectionalStream()
        
        // Then: Stream should be created
        assertNotNull(stream)
        assertTrue(stream.isOpen())
        
        // And: Server should accept the stream
        val serverStream = server.acceptStream()
        assertNotNull(serverStream)
        
        // Cleanup
        stream.close()
        connection.close()
        client.close()
        server.stop()
        serverJob.cancel()
    }

    @Test
    fun `QUIC should support data transfer over streams`() = runTest {
        // Given: Connected client and server with stream
        val server = QuicServer(QuicServerConfig(port = 9446))
        val serverJob = launch { server.start() }
        
        val client = QuicClient(QuicClientConfig())
        val connection = client.connect("127.0.0.1", 9446)
        val clientStream = connection.createBidirectionalStream()
        val serverStream = server.acceptStream()
        
        // When: Client sends data
        val testData = "Hello QUIC!".toByteArray()
        clientStream.send(testData)
        
        // Then: Server should receive the data
        val receivedData = serverStream.receive()
        assertContentEquals(testData, receivedData)
        
        // Cleanup
        clientStream.close()
        connection.close()
        client.close()
        server.stop()
        serverJob.cancel()
    }

    @Test
    fun `HTTP server should handle HTTP/1.1 requests`() = runTest {
        // Given: An HTTP server
        val httpServer = HttpServer(HttpServerConfig(port = 8080))
        val serverJob = launch { httpServer.start() }
        
        // And: A route handler
        httpServer.route("/test") { request ->
            HttpResponse(
                status = HttpStatus.OK,
                headers = mapOf("Content-Type" to "text/plain"),
                body = "Hello HTTP!".toByteArray()
            )
        }
        
        // When: Client makes HTTP request
        val httpClient = HttpClient()
        val response = httpClient.get("http://127.0.0.1:8080/test")
        
        // Then: Response should be correct
        assertEquals(200, response.status)
        assertEquals("Hello HTTP!", String(response.body))
        
        // Cleanup
        httpClient.close()
        httpServer.stop()
        serverJob.cancel()
    }

    @Test
    fun `HTTP/3 server should handle requests over QUIC`() = runTest {
        // Given: An HTTP/3 server over QUIC
        val quicServer = QuicServer(QuicServerConfig(port = 9447))
        val http3Server = Http3Server(quicServer)
        val serverJob = launch { http3Server.start() }
        
        // And: A route handler
        http3Server.route("/api/data") { request ->
            HttpResponse(
                status = HttpStatus.OK,
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"message": "HTTP/3 over QUIC"}""".toByteArray()
            )
        }
        
        // When: Client makes HTTP/3 request
        val quicClient = QuicClient(QuicClientConfig())
        val connection = quicClient.connect("127.0.0.1", 9447)
        val http3Client = Http3Client(connection)
        
        val response = http3Client.get("/api/data")
        
        // Then: Response should be correct
        assertEquals(200, response.status)
        assertTrue(String(response.body).contains("HTTP/3 over QUIC"))
        
        // Cleanup
        http3Client.close()
        connection.close()
        quicClient.close()
        http3Server.stop()
        serverJob.cancel()
    }

    @Test
    fun `HTTP server should support multiple concurrent requests`() = runTest {
        // Given: An HTTP server with slow handler
        val httpServer = HttpServer(HttpServerConfig(port = 8081))
        val serverJob = launch { httpServer.start() }
        
        httpServer.route("/slow") { request ->
            delay(100) // Simulate slow processing
            HttpResponse(
                status = HttpStatus.OK,
                headers = mapOf("Content-Type" to "text/plain"),
                body = "Slow response".toByteArray()
            )
        }
        
        // When: Multiple clients make concurrent requests
        val httpClient = HttpClient()
        val requests = (1..10).map { 
            async { httpClient.get("http://127.0.0.1:8081/slow") }
        }
        
        val responses = requests.awaitAll()
        
        // Then: All requests should succeed
        responses.forEach { response ->
            assertEquals(200, response.status)
            assertEquals("Slow response", String(response.body))
        }
        
        // Cleanup
        httpClient.close()
        httpServer.stop()
        serverJob.cancel()
    }

    @Test
    fun `QUIC should support connection migration`() = runTest {
        // Given: A QUIC connection
        val server = QuicServer(QuicServerConfig(port = 9448))
        val serverJob = launch { server.start() }
        
        val client = QuicClient(QuicClientConfig())
        val connection = client.connect("127.0.0.1", 9448)
        
        // When: Connection is migrated to new address
        val newAddress = "127.0.0.2"
        connection.migrate(newAddress, 9448)
        
        // Then: Connection should remain active
        assertTrue(connection.isConnected())
        
        // And: Data transfer should continue working
        val stream = connection.createBidirectionalStream()
        val testData = "Migration test".toByteArray()
        stream.send(testData)
        
        val serverStream = server.acceptStream()
        val receivedData = serverStream.receive()
        assertContentEquals(testData, receivedData)
        
        // Cleanup
        stream.close()
        connection.close()
        client.close()
        server.stop()
        serverJob.cancel()
    }

    @Test
    fun `HTTP server should handle large file uploads`() = runTest {
        // Given: An HTTP server with file upload handler
        val httpServer = HttpServer(HttpServerConfig(port = 8082))
        val serverJob = launch { httpServer.start() }
        
        httpServer.route("/upload", method = "POST") { request ->
            val contentLength = request.headers["Content-Length"]?.toIntOrNull() ?: 0
            HttpResponse(
                status = HttpStatus.OK,
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"uploaded": $contentLength}""".toByteArray()
            )
        }
        
        // When: Client uploads large file
        val httpClient = HttpClient()
        val largeData = ByteArray(1024 * 1024) { it.toByte() } // 1MB
        val response = httpClient.post(
            "http://127.0.0.1:8082/upload",
            headers = mapOf("Content-Type" to "application/octet-stream"),
            body = largeData
        )
        
        // Then: Upload should succeed
        assertEquals(200, response.status)
        assertTrue(String(response.body).contains("1048576"))
        
        // Cleanup
        httpClient.close()
        httpServer.stop()
        serverJob.cancel()
    }

    @Test
    fun `HTTP server should handle streaming responses`() = runTest {
        // Given: An HTTP server with streaming handler
        val httpServer = HttpServer(HttpServerConfig(port = 8083))
        val serverJob = launch { httpServer.start() }
        
        httpServer.route("/stream") { request ->
            val stream = flow {
                repeat(10) { i ->
                    emit("Chunk $i\n".toByteArray())
                    delay(50)
                }
            }
            
            HttpResponse(
                status = HttpStatus.OK,
                headers = mapOf(
                    "Content-Type" to "text/plain",
                    "Transfer-Encoding" to "chunked"
                ),
                bodyStream = stream
            )
        }
        
        // When: Client receives streaming response
        val httpClient = HttpClient()
        val response = httpClient.get("http://127.0.0.1:8083/stream")
        
        // Then: Response should be streamed
        assertEquals(200, response.status)
        val body = String(response.body)
        assertTrue(body.contains("Chunk 0"))
        assertTrue(body.contains("Chunk 9"))
        
        // Cleanup
        httpClient.close()
        httpServer.stop()
        serverJob.cancel()
    }

    @Test
    fun `QUIC should handle connection loss and recovery`() = runTest {
        // Given: A QUIC connection
        val server = QuicServer(QuicServerConfig(port = 9449))
        val serverJob = launch { server.start() }
        
        val client = QuicClient(QuicClientConfig())
        val connection = client.connect("127.0.0.1", 9449)
        
        // When: Connection is temporarily lost
        server.simulateNetworkLoss(1000) // 1 second loss
        
        // Then: Connection should recover automatically
        delay(2000)
        assertTrue(connection.isConnected())
        
        // And: Data transfer should resume
        val stream = connection.createBidirectionalStream()
        val testData = "Recovery test".toByteArray()
        stream.send(testData)
        
        val serverStream = server.acceptStream()
        val receivedData = serverStream.receive()
        assertContentEquals(testData, receivedData)
        
        // Cleanup
        stream.close()
        connection.close()
        client.close()
        server.stop()
        serverJob.cancel()
    }

    @Test
    fun `HTTP server should handle graceful shutdown`() = runTest {
        // Given: A running HTTP server with active connections
        val httpServer = HttpServer(HttpServerConfig(port = 8084))
        val serverJob = launch { httpServer.start() }
        
        // And: Multiple active connections
        val httpClient = HttpClient()
        val connections = (1..5).map { 
            async { httpClient.get("http://127.0.0.1:8084/") }
        }
        
        // When: Server is stopped gracefully
        httpServer.stop()
        
        // Then: All connections should be closed properly
        assertFalse(httpServer.isRunning())
        
        // Cleanup
        httpClient.close()
        serverJob.cancel()
    }
}

// Configuration classes for the tests
data class QuicServerConfig(
    val port: Int,
    val host: String = "0.0.0.0",
    val maxConnections: Int = 1000,
    val idleTimeoutMs: Long = 30000,
    val maxStreamsPerConnection: Int = 100,
    val initialMaxData: Long = 10_000_000,
    val initialMaxStreamData: Long = 1_000_000
)

data class QuicClientConfig(
    val connectTimeoutMs: Long = 5000,
    val idleTimeoutMs: Long = 30000,
    val maxRetries: Int = 3,
    val enable0RTT: Boolean = true
)

data class HttpServerConfig(
    val port: Int,
    val host: String = "0.0.0.0",
    val maxConnections: Int = 1000,
    val maxRequestSize: Int = 10 * 1024 * 1024, // 10MB
    val enableCompression: Boolean = true,
    val enableKeepAlive: Boolean = true
) 