package borg.trikeshed.http11server

import borg.trikeshed.net.http.server.HttpConnectionHandler
import borg.trikeshed.nio.services.jvm.ActualNioService // Assuming JVM actual for NioService
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import kotlin.coroutines.CoroutineContext

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // To allow @BeforeAll and @AfterAll on non-static methods if needed
class Http11ServerTest {

    private lateinit var connectionHandler: HttpConnectionHandler
    private val testServerScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("Http11ServerTestScope"))
    private var serverJob: Job? = null
    private val testPort = 8088 // Use a different port for testing

    @BeforeAll
    fun startServer() {
        println("Starting test HTTP server on port \$testPort")
        val nioService = ActualNioService()
        val serverRootContext: CoroutineContext = testServerScope.coroutineContext + nioService

        connectionHandler = HttpConnectionHandler(
            nioService = nioService,
            CCEKContext = serverRootContext
        )

        serverJob = testServerScope.launch {
            try {
                connectionHandler.start("0.0.0.0", testPort)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    println("Test server start failed: \${e.message}")
                    e.printStackTrace()
                    // JUnit 5 assertions should be used in test context, not here.
                    // For setup/teardown, manual fail or rethrow might be better.
                    // Assertions.fail("Server failed to start: " + e.message)
                    throw e // Rethrow to fail the setup
                } else {
                    println("Test server start cancelled.")
                }
            }
        }
        // Give the server a moment to start - in a real scenario, might need a more robust check
        runBlocking { delay(500) }
        println("Test HTTP server setup complete.")
    }

    @AfterAll
    fun stopServer() {
        println("Stopping test HTTP server...")
        connectionHandler.stop()
        serverJob?.cancel()
        testServerScope.cancel()
        println("Test HTTP server stopped.")
        // Allow time for port to be released
        runBlocking { delay(500) }
    }

    private fun sendRawHttpRequest(host: String, port: Int, request: String): String {
        SocketChannel.open(InetSocketAddress(host, port)).use { clientSocket ->
            clientSocket.configureBlocking(true)

            val requestBuffer = ByteBuffer.wrap(request.toByteArray(StandardCharsets.UTF_8))
            while (requestBuffer.hasRemaining()) {
                clientSocket.write(requestBuffer)
            }
            clientSocket.shutdownOutput()

            val responseBuffer = ByteBuffer.allocate(8192)
            val responseBuilder = StringBuilder()

            while (clientSocket.read(responseBuffer) != -1) {
                responseBuffer.flip()
                responseBuilder.append(StandardCharsets.UTF_8.decode(responseBuffer))
                responseBuffer.compact()
            }
            responseBuffer.flip()
            if (responseBuffer.hasRemaining()) {
                responseBuilder.append(StandardCharsets.UTF_8.decode(responseBuffer))
            }

            return responseBuilder.toString()
        }
    }

    @Test
    fun `GET request to testpath should return dynamic content`() {
        val path = "/testpath"
        // Using String.format to avoid issues with dollar signs in multiline strings for the subtask
        val httpRequest = String.format(
            "GET %s HTTP/1.1\r\n" +
            "Host: localhost:%d\r\n" +
            "Connection: close\r\n" +
            "\r\n",
            path, testPort
        )

        val responseString = sendRawHttpRequest("localhost", testPort, httpRequest)

        assertTrue(responseString.startsWith("HTTP/1.1 200 OK"), "Response should start with HTTP/1.1 200 OK. Got: '\$responseString'")

        val expectedBody = "Hello from TrikeShed HTTP/1.1 Server! You requested: \$path"
        assertTrue(responseString.contains("Content-Type: text/plain; charset=utf-8"), "Content-Type header missing or incorrect.")
        assertTrue(responseString.contains("Content-Length: \${expectedBody.toByteArray(StandardCharsets.UTF_8).size}"), "Content-Length header missing or incorrect. Body size: \${expectedBody.toByteArray(StandardCharsets.UTF_8).size}")
        assertTrue(responseString.endsWith(expectedBody), "Response body did not match. Expected to end with '\$expectedBody'. Got: '\$responseString'")
    }

    @Test
    fun `GET request to anotherpath should return dynamic content`() {
        val path = "/another/sample/path?query=123"
        val httpRequest = String.format(
            "GET %s HTTP/1.1\r\n" +
            "Host: localhost:%d\r\n" +
            "Connection: close\r\n" +
            "\r\n",
            path, testPort
        )

        val responseString = sendRawHttpRequest("localhost", testPort, httpRequest)

        assertTrue(responseString.startsWith("HTTP/1.1 200 OK"), "Response should start with HTTP/1.1 200 OK. Got: '\$responseString'")

        val expectedBody = "Hello from TrikeShed HTTP/1.1 Server! You requested: \$path"
        assertTrue(responseString.contains("Content-Type: text/plain; charset=utf-8"), "Content-Type header missing or incorrect.")
        assertTrue(responseString.contains("Content-Length: \${expectedBody.toByteArray(StandardCharsets.UTF_8).size}"), "Content-Length header missing or incorrect. Body size: \${expectedBody.toByteArray(StandardCharsets.UTF_8).size}")
        assertTrue(responseString.endsWith(expectedBody), "Response body did not match. Expected to end with '\$expectedBody'. Got: '\$responseString'")
    }

    @Test
    fun `GET request to root path should return dynamic content`() {
        val path = "/"
        val httpRequest = String.format(
            "GET %s HTTP/1.1\r\n" +
            "Host: localhost:%d\r\n" +
            "Connection: close\r\n" +
            "\r\n",
            path, testPort
        )

        val responseString = sendRawHttpRequest("localhost", testPort, httpRequest)

        assertTrue(responseString.startsWith("HTTP/1.1 200 OK"), "Response should start with HTTP/1.1 200 OK. Got: '\$responseString'")

        val expectedBody = "Hello from TrikeShed HTTP/1.1 Server! You requested: \$path"
        assertTrue(responseString.contains("Content-Type: text/plain; charset=utf-8"), "Content-Type header missing or incorrect.")
        assertTrue(responseString.contains("Content-Length: \${expectedBody.toByteArray(StandardCharsets.UTF_8).size}"), "Content-Length header missing or incorrect. Body size: \${expectedBody.toByteArray(StandardCharsets.UTF_8).size}")
        assertTrue(responseString.endsWith(expectedBody), "Response body did not match. Expected to end with '\$expectedBody'. Got: '\$responseString'")
    }
}
