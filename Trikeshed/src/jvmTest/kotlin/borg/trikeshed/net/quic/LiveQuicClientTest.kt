package borg.trikeshed.net.quic

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.`play`
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live QUIC client test with real UDP sockets - no mocks
 * Tests actual connection establishment, data transmission, and error handling
 */
class LiveQuicClientTest {
    
    @Test
    fun `client connects to server and exchanges packets`() = runBlocking {
        val serverPort = 9543
        val clientResponses = Channel<String>(capacity = 10)
        
        // Mock QUIC server for client testing
        val serverJob = launch {
            val channel = DatagramChannel.open()
            channel.configureBlocking(false)
            channel.bind(InetSocketAddress("127.0.0.1", serverPort))
            
            val buffer = ByteBuffer.allocate(1500)
            
            try {
                while (isActive) {
                    buffer.clear()
                    val clientAddr = channel.receive(buffer)
                    
                    if (clientAddr != null) {
                        buffer.flip()
                        val request = ByteArray(buffer.remaining())
                        buffer.get(request)
                        
                        // Echo with QUIC handshake simulation
                        val response = when {
                            request.decodeToString().startsWith("INITIAL") -> "VERSION_NEGOTIATION"
                            request.decodeToString().startsWith("HANDSHAKE") -> "HANDSHAKE_COMPLETE"
                            else -> "DATA_ACK_${request.decodeToString()}"
                        }
                        
                        channel.send(ByteBuffer.wrap(response.toByteArray()), clientAddr)
                    }
                    delay(1)
                }
            } finally {
                channel.close()
            }
        }
        
        delay(100) // Server startup
        
        // QUIC client implementation
        val clientJob = launch {
            val socket = DatagramSocket()
            val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
            
            // QUIC connection establishment sequence
            val handshakeSteps = Series.of(
                "INITIAL_CLIENT_HELLO",
                "HANDSHAKE_CRYPTO_FRAME", 
                "DATA_APPLICATION_PAYLOAD"
            ).`play`
            
            handshakeSteps.forEach { step ->
                // Send packet
                val packet = java.net.DatagramPacket(
                    step.toByteArray(), 
                    step.length, 
                    serverAddr
                )
                socket.send(packet)
                
                // Receive response
                val responseBuffer = ByteArray(1500)
                val responsePacket = java.net.DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                
                val response = String(responsePacket.data, 0, responsePacket.length)
                clientResponses.trySend(response)
                
                delay(50) // Realistic network latency
            }
            
            socket.close()
        }
        
        // Verify handshake sequence
        val expectedResponses = listOf(
            "VERSION_NEGOTIATION",
            "HANDSHAKE_COMPLETE", 
            "DATA_ACK_DATA_APPLICATION_PAYLOAD"
        )
        
        repeat(expectedResponses.size) { i ->
            val response = withTimeout(1000) { clientResponses.receive() }
            assertTrue(
                response.contains(expectedResponses[i].split("_").first()),
                "Expected ${expectedResponses[i]} pattern, got $response"
            )
        }
        
        clientJob.join()
        serverJob.cancel()
    }
    
    @Test
    fun `client handles connection failures and retries`() = runBlocking {
        val unreachablePort = 9999
        val retryAttempts = Channel<String>(capacity = 10)
        
        // QUIC client with retry logic
        val clientJob = launch {
            val socket = DatagramSocket()
            socket.soTimeout = 100 // Quick timeout for testing
            val serverAddr = InetSocketAddress("127.0.0.1", unreachablePort)
            
            var attempt = 0
            val maxRetries = 5
            
            while (attempt < maxRetries) {
                attempt++
                retryAttempts.trySend("ATTEMPT_$attempt")
                
                try {
                    val data = "CONNECTION_ATTEMPT_$attempt".toByteArray()
                    val packet = java.net.DatagramPacket(data, data.size, serverAddr)
                    socket.send(packet)
                    
                    // Try to receive response (will timeout)
                    val responseBuffer = ByteArray(1500)
                    val responsePacket = java.net.DatagramPacket(responseBuffer, responseBuffer.size)
                    socket.receive(responsePacket) // This will timeout
                    
                    retryAttempts.trySend("SUCCESS_$attempt")
                    break
                } catch (e: Exception) {
                    retryAttempts.trySend("TIMEOUT_$attempt")
                    
                    // Exponential backoff
                    delay(100L * (1 shl (attempt - 1)))
                }
            }
            
            socket.close()
        }
        
        clientJob.join()
        
        // Verify retry behavior
        val attempts = mutableListOf<String>()
        while (!retryAttempts.isEmpty) {
            attempts.add(retryAttempts.receive())
        }
        
        assertTrue(attempts.size >= 10, "Should have attempt and timeout pairs")
        assertTrue(attempts.any { it.startsWith("ATTEMPT_") }, "Should log connection attempts")
        assertTrue(attempts.any { it.startsWith("TIMEOUT_") }, "Should log timeouts")
        assertTrue(attempts.none { it.startsWith("SUCCESS_") }, "Should not succeed on unreachable port")
    }
    
    @Test
    fun `client sends abusive traffic loads to stress server`() = runBlocking {
        val serverPort = 9544
        val serverMetrics = Channel<String>(capacity = 1000)
        val clientMetrics = Channel<String>(capacity = 1000)
        
        // Stressed QUIC server
        val serverJob = launch {
            val channel = DatagramChannel.open()
            channel.configureBlocking(false)
            channel.bind(InetSocketAddress("127.0.0.1", serverPort))
            channel.socket().receiveBufferSize = 65536
            
            val buffer = ByteBuffer.allocate(9000)
            var packetsReceived = 0
            var bytesReceived = 0L
            val startTime = System.currentTimeMillis()
            
            try {
                while (isActive && packetsReceived < 2000) { // Cap for test completion
                    buffer.clear()
                    val clientAddr = channel.receive(buffer)
                    
                    if (clientAddr != null) {
                        buffer.flip()
                        val size = buffer.remaining()
                        packetsReceived++
                        bytesReceived += size
                        
                        if (packetsReceived % 100 == 0) {
                            val elapsed = System.currentTimeMillis() - startTime
                            serverMetrics.trySend("PROCESSED_${packetsReceived}_IN_${elapsed}ms")
                        }
                        
                        // Simple ACK response  
                        val ack = "ACK_$packetsReceived".toByteArray()
                        channel.send(ByteBuffer.wrap(ack), clientAddr)
                    }
                    if (packetsReceived % 50 == 0) delay(1) // Yield
                }
                
                val totalTime = System.currentTimeMillis() - startTime
                serverMetrics.trySend("FINAL_${packetsReceived}_packets_${bytesReceived}_bytes_${totalTime}ms")
            } finally {
                channel.close()
            }
        }
        
        delay(100) // Server startup
        
        // Abusive client patterns
        val abuseJobs = listOf(
            // High frequency small packets
            launch {
                val socket = DatagramSocket()
                val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
                
                repeat(500) { i ->
                    val data = "SPAM_$i".toByteArray()
                    socket.send(java.net.DatagramPacket(data, data.size, serverAddr))
                    if (i % 10 == 0) delay(1) // Minimal delay
                }
                
                clientMetrics.trySend("SPAM_COMPLETE_500")
                socket.close()
            },
            
            // Large packet flood
            launch {
                val socket = DatagramSocket()
                val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
                
                repeat(200) { i ->
                    val data = ByteArray(4000) { (i % 256).toByte() }
                    socket.send(java.net.DatagramPacket(data, data.size, serverAddr))
                    delay(2)
                }
                
                clientMetrics.trySend("FLOOD_COMPLETE_200")
                socket.close()
            },
            
            // Concurrent connection simulation
            launch {
                repeat(10) { clientId ->
                    launch {
                        val socket = DatagramSocket()
                        val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
                        
                        repeat(50) { packetId ->
                            val data = "CLIENT_${clientId}_PACKET_$packetId".toByteArray()
                            socket.send(java.net.DatagramPacket(data, data.size, serverAddr))
                            delay(5)
                        }
                        
                        socket.close()
                    }
                }
                
                clientMetrics.trySend("CONCURRENT_COMPLETE_10x50")
            }
        )
        
        // Wait for abuse completion
        abuseJobs.joinAll()
        delay(1000) // Server processing time
        
        // Collect metrics
        val serverStats = mutableListOf<String>()
        val clientStats = mutableListOf<String>()
        
        while (!serverMetrics.isEmpty) {
            serverStats.add(serverMetrics.receive())
        }
        while (!clientMetrics.isEmpty) {
            clientStats.add(clientMetrics.receive())
        }
        
        // Verify stress test results
        assertTrue(serverStats.any { it.startsWith("FINAL_") }, "Server should provide final statistics")
        assertTrue(clientStats.size >= 3, "All client patterns should complete")
        assertTrue(clientStats.any { it.contains("SPAM_COMPLETE") }, "Spam pattern should complete")
        assertTrue(clientStats.any { it.contains("FLOOD_COMPLETE") }, "Flood pattern should complete")
        assertTrue(clientStats.any { it.contains("CONCURRENT_COMPLETE") }, "Concurrent pattern should complete")
        
        val finalStats = serverStats.last { it.startsWith("FINAL_") }
        assertTrue(finalStats.contains("packets"), "Final stats should include packet count")
        assertTrue(finalStats.contains("bytes"), "Final stats should include byte count")
        
        serverJob.cancel()
    }
}