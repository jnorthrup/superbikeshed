package tests.tdd

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import borg.trikeshed.reactor.*
import borg.trikeshed.net.http.*
import java.nio.ByteBuffer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Test HTTP server for TDD testing
 */
class TestHttpServer(
    private val port: Int,
    private val responseDelay: Duration = Duration.ZERO,
    private val failureRate: Double = 0.0,
    private val malformed: Boolean = false
) {
    private var running = false
    private var serverJob: Job? = null
    private var serverChannel: ServerChannel? = null

    suspend fun start() {
        running = true
        
        // Create server socket
        serverChannel = borg.trikeshed.reactor.SocketFactory.createServerSocket(port)
        
        serverJob = GlobalScope.launch {
            while (running) {
                try {
                    val clientChannel = serverChannel?.accept()
                    if (clientChannel != null) {
                        launch {
                            handleClient(clientChannel)
                        }
                    }
                } catch (e: Exception) {
                    if (running) {
                        println("Test server error: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun handleClient(clientChannel: ClientChannel) {
        try {
            val buffer = ByteBuffer.allocate(8192)
            val bytesRead = clientChannel.read(buffer)
            
            if (bytesRead > 0) {
                val requestData = buffer.array().sliceArray(0 until bytesRead)
                val request = parseHttpRequest(requestData)
                
                // Simulate response delay
                if (responseDelay > Duration.ZERO) {
                    delay(responseDelay)
                }
                
                // Simulate failures
                if (failureRate > 0.0 && kotlin.random.Random.nextDouble() < failureRate) {
                    // Don't send response to simulate failure
                    return
                }
                
                // Generate response
                val response = generateResponse(request)
                val responseData = response.toByteArray()
                val responseBuffer = ByteBuffer.wrap(responseData)
                clientChannel.write(responseBuffer)
            }
        } finally {
            clientChannel.close()
        }
    }
    
    private fun parseHttpRequest(data: ByteArray): HttpRequest {
        val requestString = String(data)
        val lines = requestString.split("\r\n")
        
        // Parse request line
        val requestLine = lines.firstOrNull() ?: "GET / HTTP/1.1"
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0) ?: "GET"
        val path = parts.getOrNull(1) ?: "/"
        
        // Parse headers
        val headers = mutableMapOf<String, String>()
        var bodyStartIndex = -1
        
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) {
                bodyStartIndex = i + 1
                break
            }
            
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val name = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[name] = value
            }
        }
        
        // Extract body
        val body = if (bodyStartIndex > 0 && bodyStartIndex < lines.size) {
            lines.subList(bodyStartIndex, lines.size).joinToString("\r\n").toByteArray()
        } else {
            ByteArray(0)
        }
        
        return HttpRequest(method, path, headers, body)
    }
    
    private fun generateResponse(request: HttpRequest): HttpResponse {
        return when {
            malformed -> {
                // Return malformed response
                HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "text/plain"),
                    body = "Malformed response without proper headers\r\n\r\n".toByteArray()
                )
            }
            request.path.startsWith("/test") -> {
                HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "text/plain"),
                    body = "test response".toByteArray()
                )
            }
            request.path.startsWith("/api/data") -> {
                HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = """{"received": true, "size": ${request.body.size}}""".toByteArray()
                )
            }
            request.path.startsWith("/slow") -> {
                HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "text/plain"),
                    body = "slow response".toByteArray()
                )
            }
            request.path.startsWith("/flaky") -> {
                HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "text/plain"),
                    body = "flaky response".toByteArray()
                )
            }
            request.path.startsWith("/concurrent") -> {
                HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "text/plain"),
                    body = "concurrent response".toByteArray()
                )
            }
            else -> {
                HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "text/plain"),
                    body = "default response".toByteArray()
                )
            }
        }
    }

    fun stop() {
        running = false
        serverJob?.cancel()
        serverChannel?.close()
    }
}

/**
 * Test REST server for TDD testing
 */
class TestRestServer(
    private val port: Int,
    private val streaming: Boolean = false
) {
    private var running = false
    private var serverJob: Job? = null
    private var serverChannel: ServerChannel? = null

    suspend fun start() {
        running = true
        
        // Create server socket
        serverChannel = borg.trikeshed.reactor.SocketFactory.createServerSocket(port)
        
        serverJob = GlobalScope.launch {
            while (running) {
                try {
                    val clientChannel = serverChannel?.accept()
                    if (clientChannel != null) {
                        launch {
                            handleClient(clientChannel)
                        }
                    }
                } catch (e: Exception) {
                    if (running) {
                        println("Test REST server error: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun handleClient(clientChannel: ClientChannel) {
        try {
            val buffer = ByteBuffer.allocate(8192)
            val bytesRead = clientChannel.read(buffer)
            
            if (bytesRead > 0) {
                val requestData = buffer.array().sliceArray(0 until bytesRead)
                val request = parseHttpRequest(requestData)
                
                val response = generateRestResponse(request)
                val responseData = response.toByteArray()
                val responseBuffer = ByteBuffer.wrap(responseData)
                clientChannel.write(responseBuffer)
            }
        } finally {
            clientChannel.close()
        }
    }
    
    private fun parseHttpRequest(data: ByteArray): HttpRequest {
        val requestString = String(data)
        val lines = requestString.split("\r\n")
        
        // Parse request line
        val requestLine = lines.firstOrNull() ?: "GET / HTTP/1.1"
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0) ?: "GET"
        val path = parts.getOrNull(1) ?: "/"
        
        // Parse headers
        val headers = mutableMapOf<String, String>()
        var bodyStartIndex = -1
        
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) {
                bodyStartIndex = i + 1
                break
            }
            
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val name = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[name] = value
            }
        }
        
        // Extract body
        val body = if (bodyStartIndex > 0 && bodyStartIndex < lines.size) {
            lines.subList(bodyStartIndex, lines.size).joinToString("\r\n").toByteArray()
        } else {
            ByteArray(0)
        }
        
        return HttpRequest(method, path, headers, body)
    }
    
    private fun generateRestResponse(request: HttpRequest): HttpResponse {
        return when {
            request.path.startsWith("/api/users") -> {
                HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = """{"users": [{"id": 1, "name": "Test User"}]}""".toByteArray()
                )
            }
            request.path.startsWith("/api/stream") && streaming -> {
                HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "text/event-stream"),
                    body = "data: chunk1\ndata: chunk2\ndata: chunk3\n\n".toByteArray()
                )
            }
            else -> {
                HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = """{"status": "ok"}""".toByteArray()
                )
            }
        }
    }

    fun stop() {
        running = false
        serverJob?.cancel()
        serverChannel?.close()
    }
} 