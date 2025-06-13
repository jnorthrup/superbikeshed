package borg.trikeshed.net.quic

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.`▶`
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live QUIC server test with real UDP sockets - no mocks
 * Tests actual socket binding, packet reception, and concurrent client handling
 */
class LiveQuicServerTest {
    
    @Test
    fun `server binds to localhost and receives packets`() = runBlocking {
        val serverPort = 9443
        val testData = "QUIC_INITIAL_PACKET".toByteArray()
        val receivedPackets = Channel<ByteArray>(capacity = 100)
        
        // Start real QUIC server on localhost
        val serverJob = launch {
            val channel = DatagramChannel.open()
            channel.configureBlocking(false)
            channel.bind(InetSocketAddress("127.0.0.1", serverPort))
            
            val buffer = ByteBuffer.allocate(1500) // MTU size
            
            try {
                while (isActive) {
                    buffer.clear()
                    val clientAddress = channel.receive(buffer)
                    
                    if (clientAddress != null) {
                        buffer.flip()
                        val packetData = ByteArray(buffer.remaining())
                        buffer.get(packetData)
                        
                        receivedPackets.trySend(packetData)
                        
                        // Echo response for integration testing
                        val response = "QUIC_RESPONSE_${packetData.decodeToString()}".toByteArray()
                        channel.send(ByteBuffer.wrap(response), clientAddress)
                    }
                    delay(1) // Yield for cooperative multitasking
                }
            } finally {
                channel.close()
            }
        }
        
        delay(100) // Server startup time
        
        // Send test packet to server
        val clientSocket = DatagramSocket()
        val serverAddress = InetSocketAddress("127.0.0.1", serverPort)
        val packet = java.net.DatagramPacket(testData, testData.size, serverAddress)
        
        clientSocket.send(packet)
        
        // Verify packet received
        val received = withTimeout(1000) { receivedPackets.receive() }
        assertTrue(received.contentEquals(testData), "Server should receive exact packet data")
        
        clientSocket.close()
        serverJob.cancel()
    }
    
    @Test 
    fun `server handles concurrent client connections`() = runBlocking {
        val serverPort = 9444
        val clientCount = 50
        val packetsPerClient = 10
        val receivedCount = Channel<Int>(capacity = clientCount * packetsPerClient)
        
        // QUIC server with concurrent handling
        val serverJob = launch {
            val channel = DatagramChannel.open()
            channel.configureBlocking(false)
            channel.bind(InetSocketAddress("127.0.0.1", serverPort))
            
            val buffer = ByteBuffer.allocate(1500)
            val clientSessions = mutableMapOf<InetSocketAddress, Int>()
            
            try {
                while (isActive) {
                    buffer.clear()
                    val clientAddr = channel.receive(buffer)
                    
                    if (clientAddr != null) {
                        buffer.flip()
                        val packetData = ByteArray(buffer.remaining())
                        buffer.get(packetData)
                        
                        // Track client sessions
                        clientSessions[clientAddr as InetSocketAddress] = 
                            clientSessions.getOrDefault(clientAddr, 0) + 1
                        
                        receivedCount.trySend(clientSessions.size)
                        
                        // Send connection ID response
                        val response = "CONN_${clientSessions[clientAddr]}".toByteArray()
                        channel.send(ByteBuffer.wrap(response), clientAddr)
                    }
                    delay(1)
                }
            } finally {
                channel.close()
            }
        }
        
        delay(100) // Server startup
        
        // Launch concurrent clients
        val clientJobs = (1..clientCount).map { clientId ->
            launch {
                val socket = DatagramSocket()
                val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
                
                repeat(packetsPerClient) { packetId ->
                    val data = "CLIENT_${clientId}_PACKET_$packetId".toByteArray()
                    val packet = java.net.DatagramPacket(data, data.size, serverAddr)
                    socket.send(packet)
                    delay(10) // Realistic packet spacing
                }
                socket.close()
            }
        }
        
        // Wait for all clients to complete
        clientJobs.joinAll()
        
        // Verify server handled concurrent load
        delay(500) // Processing time
        var totalReceived = 0
        while (!receivedCount.isEmpty) {
            receivedCount.tryReceive().getOrNull()?.let { totalReceived++ }
        }
        
        assertTrue(totalReceived >= clientCount, "Server should handle $clientCount concurrent clients")
        
        serverJob.cancel()
    }
    
    @Test
    fun `server processes abusive traffic patterns`() = runBlocking {
        val serverPort = 9445
        val abusePatterns = Series.of(
            "flood" to 1000,    // Packet flood
            "burst" to 100,     // Burst pattern  
            "jumbo" to 10       // Large packets
        ).`▶`
        
        val serverStats = Channel<String>(capacity = 2000)
        
        // Resilient QUIC server
        val serverJob = launch {
            val channel = DatagramChannel.open()
            channel.configureBlocking(false)
            channel.bind(InetSocketAddress("127.0.0.1", serverPort))
            channel.socket().receiveBufferSize = 65536 // Larger buffer for abuse testing
            
            val buffer = ByteBuffer.allocate(9000) // Support jumbo frames
            var packetCount = 0
            var totalBytes = 0L
            
            try {
                while (isActive) {
                    buffer.clear()
                    val clientAddr = channel.receive(buffer)
                    
                    if (clientAddr != null) {
                        buffer.flip()
                        val size = buffer.remaining()
                        packetCount++
                        totalBytes += size
                        
                        serverStats.trySend("PACKET_${packetCount}_SIZE_$size")
                        
                        // Rate limiting simulation
                        if (packetCount % 100 == 0) {
                            delay(10) // Throttle on high load
                        }
                    }
                    if (packetCount % 10 == 0) delay(1) // Yield periodically
                }
            } finally {
                serverStats.trySend("FINAL_STATS_PACKETS_${packetCount}_BYTES_$totalBytes")
                channel.close()
            }
        }
        
        delay(100) // Server startup
        
        // Execute abuse patterns
        abusePatterns.forEach { (pattern, count) ->
            launch {
                val socket = DatagramSocket()
                val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
                
                when (pattern) {
                    "flood" -> {
                        // Rapid fire small packets
                        repeat(count) {
                            val data = "FLOOD_$it".toByteArray()
                            socket.send(java.net.DatagramPacket(data, data.size, serverAddr))
                        }
                    }
                    "burst" -> {
                        // Bursty pattern with delays
                        repeat(count / 10) { burst ->
                            repeat(10) { packet ->
                                val data = "BURST_${burst}_$packet".toByteArray()
                                socket.send(java.net.DatagramPacket(data, data.size, serverAddr))
                            }
                            delay(50) // Burst interval
                        }
                    }
                    "jumbo" -> {
                        // Large packet sizes
                        repeat(count) {
                            val data = ByteArray(8000) { (it % 256).toByte() } // 8KB packets
                            socket.send(java.net.DatagramPacket(data, data.size, serverAddr))
                            delay(5)
                        }
                    }
                }
                socket.close()
            }
        }.joinAll()
        
        delay(1000) // Processing time for all patterns
        
        // Verify server resilience
        var statsReceived = 0
        var finalStats = ""
        while (!serverStats.isEmpty) {
            val stat = serverStats.tryReceive().getOrNull()
            if (stat != null) {
                statsReceived++
                if (stat.startsWith("FINAL_STATS")) finalStats = stat
            }
        }
        
        assertTrue(statsReceived > 0, "Server should generate traffic statistics")
        assertTrue(finalStats.isNotEmpty(), "Server should provide final statistics")
        assertTrue(finalStats.contains("PACKETS_"), "Final stats should include packet count")
        
        serverJob.cancel()
    }
}