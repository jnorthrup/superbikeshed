package borg.trikeshed.net.http.client

import borg.trikeshed.foundation.common.brandt.ActualCoreTensorCursor // Placeholder for actual construction
import borg.trikeshed.foundation.common.brandt.CoreTensorCursorWithMeta
import borg.trikeshed.foundation.common.series.asString
import borg.trikeshed.foundation.common.series.toByteArray
import borg.trikeshed.foundation.common.series.toSeries
import borg.trikeshed.net.http.server.HttpConnectionHandler // For running the test server
import borg.trikeshed.net.http.types.*
import borg.trikeshed.nio.services.ActualNioService // Assuming common actual or JVM for test
import borg.trikeshed.nio.services.NioService
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.coroutines.CoroutineContext

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpClientTest {

    private lateinit var serverConnectionHandler: HttpConnectionHandler
    private val testServerScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("HttpClientTestServerScope"))
    private var serverJob: Job? = null
    private val testServerPort = 8089 // Different port for client tests

    private lateinit var nioService: NioService
    private lateinit var clientConnection: HttpClientConnection
    private lateinit var testClientContext: CoroutineContext

    @BeforeAll
    fun startTestServer() {
        println("Starting test HTTP server for client tests on port \$testServerPort")
        nioService = ActualNioService() // Assuming common actual or JVM for test env
        val serverCCEKContext: CoroutineContext = testServerScope.coroutineContext + nioService

        serverConnectionHandler = HttpConnectionHandler(
            nioService = nioService,
            CCEKContext = serverCCEKContext
        )

        serverJob = testServerScope.launch {
            try {
                serverConnectionHandler.start("0.0.0.0", testServerPort)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    println("Test server (for client test) start failed: \${e.message}")
                    e.printStackTrace()
                    throw e // Fail setup
                } else {
                    println("Test server (for client test) start cancelled.")
                }
            }
        }
        runBlocking { delay(500) } // Allow server to start
        println("Test HTTP server (for client test) setup complete.")

        // Setup client resources
        testClientContext = Dispatchers.IO + SupervisorJob() + CoroutineName("HttpClientTestClientScope") + nioService
        clientConnection = HttpClientConnection(nioService, testClientContext)
    }

    @AfterAll
    fun stopTestServer() {
        println("Stopping test HTTP server (for client tests)...")
        serverConnectionHandler.stop()
        serverJob?.cancel()
        testServerScope.cancel()
        (testClientContext[Job])?.cancel() // Cancel client scope job as well
        println("Test HTTP server (for client tests) stopped.")
        runBlocking { delay(500) }
    }

    private fun createHeaders(vararg headers: Pair<String, String>): HttpHeaders {
        val headerStrings = headers.map { "\${it.first}: \${it.second}" }.toSeries()
        // Placeholder for ActualCoreTensorCursor construction or Series.asCoreTensorCursor()
        val cursor = ActualCoreTensorCursor(headerStrings)
        return CoreTensorCursorWithMeta(cursor, HttpHeadersMeta())
    }

    @Test
    fun `client GET request to testpath should receive dynamic content`() = runBlocking {
        val path = "/clientTestPath"
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath(path),
            version = HttpVersion("HTTP/1.1"),
            headers = createHeaders(
                HttpHeaderName.HOST to "localhost:\$testServerPort",
                HttpHeaderName.CONNECTION to "close",
                HttpHeaderName.ACCEPT to "text/plain"
            ),
            body = HttpBody.Empty
        )

        val response = clientConnection.sendRequest(request, "localhost", testServerPort)

        assertEquals(200, response.statusCode.value, "Status code should be 200")

        val expectedBody = "Hello from TrikeShed HTTP/1.1 Server! You requested: \$path"
        val responseBody = response.body
        assertInstanceOf(HttpBody.Bytes::class.java, responseBody, "Response body should be HttpBody.Bytes")
        val bodyBytes = (responseBody as HttpBody.Bytes).data.toByteArray()
        assertEquals(expectedBody, bodyBytes.toString(Charsets.UTF_8), "Response body content mismatch")

        // Check headers (basic check)
        var contentTypeFound = false
        var contentLengthFound = false
        val responseHeaders = response.headers.cursor // Assuming cursor gives access to Series<String>
        for (i in 0 until responseHeaders.rows) {
            val headerLine = responseHeaders[i,0] // Assuming single column of "Name: Value"
            if (headerLine.startsWith(HttpHeaderName.CONTENT_TYPE, ignoreCase = true)) {
                assertTrue(headerLine.contains("text/plain; charset=utf-8", ignoreCase = true), "Content-Type incorrect")
                contentTypeFound = true
            }
            if (headerLine.startsWith(HttpHeaderName.CONTENT_LENGTH, ignoreCase = true)) {
                // Corrected to use byte length for comparison
                assertEquals("\${HttpHeaderName.CONTENT_LENGTH}: \${expectedBody.toByteArray(Charsets.UTF_8).size}", headerLine, "Content-Length incorrect")
                contentLengthFound = true
            }
        }
        assertTrue(contentTypeFound, "Content-Type header not found")
        assertTrue(contentLengthFound, "Content-Length header not found")
    }

    @Test
    fun `client GET request with query params should receive dynamic content`() = runBlocking {
        val path = "/queryTest?name=TrikeShed&type=Client"
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath(path),
            version = HttpVersion("HTTP/1.1"),
            headers = createHeaders(
                HttpHeaderName.HOST to "localhost:\$testServerPort",
                HttpHeaderName.CONNECTION to "close"
            ),
            body = HttpBody.Empty
        )

        val response = clientConnection.sendRequest(request, "localhost", testServerPort)

        assertEquals(200, response.statusCode.value, "Status code should be 200")
        val expectedBody = "Hello from TrikeShed HTTP/1.1 Server! You requested: \$path"
        val responseBody = response.body
        assertInstanceOf(HttpBody.Bytes::class.java, responseBody, "Response body should be HttpBody.Bytes")
        val bodyBytes = (responseBody as HttpBody.Bytes).data.toByteArray()
        assertEquals(expectedBody, bodyBytes.toString(Charsets.UTF_8), "Response body content mismatch")
    }
}
