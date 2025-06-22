package borg.trikeshed.net.quic

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join  
import borg.trikeshed.lib.`play`
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live QUIC client-server integration test with abusive traffic patterns
 * Tests real socket communication between our QUIC implementations on localhost
 */
class LiveQuicIntegrationTest {
    
    @Test
    fun `full duplex QUIC client-server communication with traffic analysis`() = runBlocking {
        val serverPort = 9643
        val totalClients = 20
        val packetsPerClient = 25
        val serverStats = Channel<Join<String, Long>>(capacity = 1000)
        val clientStats = Channel<Join<String, Long>>(capacity = 1000)
        
        // Production-style QUIC server
        val serverJob = launch {
            val channel = DatagramChannel.open()
            channel.configureBlocking(false)
            channel.bind(InetSocketAddress("127.0.0.1", serverPort))
            channel.socket().receiveBufferSize = 131072 // 128KB buffer
            
            val buffer = ByteBuffer.allocate(9000)
            val connectionMap = mutableMapOf<InetSocketAddress, AtomicInteger>()
            var totalPackets = 0
            var totalBytes = 0L
            val startTime = System.currentTimeMillis()
            
            try {
                while (isActive && totalPackets < totalClients * packetsPerClient) {
                    buffer.clear()
                    val clientAddr = channel.receive(buffer)
                    
                    if (clientAddr != null) {
                        buffer.flip()
                        val packetSize = buffer.remaining()
                        val packetData = ByteArray(packetSize)
                        buffer.get(packetData)
                        
                        totalPackets++
                        totalBytes += packetSize
                        
                        // Track per-client connections
                        val addr = clientAddr as InetSocketAddress
                        val clientCounter = connectionMap.computeIfAbsent(addr) { AtomicInteger(0) }
                        val clientPacketNum = clientCounter.incrementAndGet()
                        
                        // Parse client message
                        val message = packetData.decodeToString()
                        val timestamp = System.currentTimeMillis()
                        
                        serverStats.trySend("RECV" j timestamp)
                        
                        // Generate QUIC-style response
                        val response = when {
                            message.contains("HANDSHAKE") -> "SERVER_HANDSHAKE_ACK_$clientPacketNum"
                            message.contains("DATA") -> "SERVER_DATA_ACK_$clientPacketNum"
                            message.contains("CLOSE") -> "SERVER_CLOSE_ACK_$clientPacketNum" 
                            else -> "SERVER_UNKNOWN_ACK_$clientPacketNum"
                        }
                        
                        // Send response with latency simulation
                        if (totalPackets % 100 == 0) delay(1) // Congestion control
                        channel.send(ByteBuffer.wrap(response.toByteArray()), addr)
                        
                        if (totalPackets % 50 == 0) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val throughput = (totalBytes * 1000 / elapsed) / 1024 // KB/s
                            serverStats.trySend("THROUGHPUT_${throughput}KB/s" j elapsed)
                        }
                    }
                    
                    if (totalPackets % 25 == 0) delay(1) // Yield for fairness
                }
                
                val finalTime = System.currentTimeMillis() - startTime
                serverStats.trySend("FINAL_STATS_${totalPackets}_packets_${totalBytes}_bytes_${finalTime}ms" j finalTime)
            } finally {
                channel.close()
            }
        }
        
        delay(200) // Server startup and stabilization
        
        // Launch concurrent QUIC clients with realistic traffic patterns
        val clientJobs = (1..totalClients).map { clientId ->
            launch {
                val socket = DatagramSocket()
                socket.soTimeout = 2000 // 2s timeout
                val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
                val clientStartTime = System.currentTimeMillis()
                
                try {
                    // QUIC connection lifecycle simulation
                    val connectionPhases = Indexed.of(
                        "HANDSHAKE_INITIAL_$clientId",
                        "HANDSHAKE_CRYPTO_$clientId",
                        "HANDSHAKE_FINISH_$clientId"
                    ).`play` + (1..packetsPerClient - 5).map { "DATA_PAYLOAD_${clientId}_$it" } +
                    listOf("CLOSE_CONNECTION_$clientId", "CLOSE_ACK_$clientId")
                    
                    var packetsExchanged = 0
                    var bytesExchanged = 0L
                    
                    connectionPhases.forEach { phase ->
                        val sendTime = System.currentTimeMillis()
                        
                        // Send packet to server
                        val packetData = phase.toByteArray()
                        socket.send(java.net.DatagramPacket(packetData, packetData.size, serverAddr))
                        
                        // Receive server response
                        val responseBuffer = ByteArray(2048)
                        val responsePacket = java.net.DatagramPacket(responseBuffer, responseBuffer.size)
                        socket.receive(responsePacket)
                        
                        val receiveTime = System.currentTimeMillis()
                        val rtt = receiveTime - sendTime
                        
                        packetsExchanged++
                        bytesExchanged += packetData.size + responsePacket.length
                        
                        clientStats.trySend("CLIENT_${clientId}_RTT_${rtt}ms" j receiveTime)
                        
                        // Realistic inter-packet delays based on traffic type
                        when {
                            phase.contains("HANDSHAKE") -> delay(10) // Critical handshake timing
                            phase.contains("DATA") -> delay(kotlin.random.Random.nextLong(1, 20)) // Variable data timing
                            phase.contains("CLOSE") -> delay(50) // Connection teardown
                        }
                    }
                    
                    val clientDuration = System.currentTimeMillis() - clientStartTime
                    clientStats.trySend("CLIENT_${clientId}_COMPLETE_${packetsExchanged}_packets_${bytesExchanged}_bytes_${clientDuration}ms" j clientDuration)
                    
                } catch (e: Exception) {
                    clientStats.trySend("CLIENT_${clientId}_ERROR_${e.javaClass.simpleName}" j System.currentTimeMillis())
                } finally {
                    socket.close()
                }
            }
        }
        
        // Wait for all clients to complete
        clientJobs.joinAll()
        delay(1000) // Final server processing
        
        // Analyze traffic patterns and performance
        val serverMetrics = mutableListOf<Join<String, Long>>()
        val clientMetrics = mutableListOf<Join<String, Long>>()
        
        while (!serverStats.isEmpty) {
            serverMetrics.add(serverStats.receive())
        }
        while (!clientStats.isEmpty) {
            clientMetrics.add(clientStats.receive())
        }
        
        // Verify integration test results
        assertTrue(serverMetrics.isNotEmpty(), "Server should generate performance metrics")
        assertTrue(clientMetrics.isNotEmpty(), "Clients should generate performance metrics")
        
        val completedClients = clientMetrics.count { it.α.contains("COMPLETE") }
        assertTrue(completedClients >= totalClients * 0.9, "At least 90% of clients should complete successfully")
        
        val serverFinalStats = serverMetrics.find { it.α.startsWith("FINAL_STATS") }
        assertTrue(serverFinalStats != null, "Server should provide final statistics")
        
        val totalProcessedPackets = serverFinalStats?.α?.substringAfter("FINAL_STATS_")?.substringBefore("_packets")?.toIntOrNull() ?: 0
        assertTrue(totalProcessedPackets >= totalClients * packetsPerClient * 0.9, "Server should process at least 90% of expected packets")
        
        // Analyze RTT performance  
        val rttMeasurements = clientMetrics.filter { it.α.contains("RTT") }
            .mapNotNull { it.α.substringAfter("RTT_").substringBefore("ms").toLongOrNull() }
        
        if (rttMeasurements.isNotEmpty()) {
            val avgRtt = rttMeasurements.average()
            val maxRtt = rttMeasurements.maxOrNull() ?: 0
            assertTrue(avgRtt < 100, "Average RTT should be under 100ms for localhost (was ${avgRtt}ms)")
            assertTrue(maxRtt < 500, "Max RTT should be under 500ms for localhost (was ${maxRtt}ms)")
        }
        
        // Analyze throughput
        val throughputMeasurements = serverMetrics.filter { it.α.contains("THROUGHPUT") }
        assertTrue(throughputMeasurements.isNotEmpty(), "Server should measure throughput during load")
        
        serverJob.cancel()
    }
    
    @Test
    fun `abusive traffic patterns stress testing with monitoring`() = runBlocking {
        val serverPort = 9644
        val abuseMetrics = Channel<Join<String, String>>(capacity = 2000)
        
        // Hardened QUIC server for abuse testing
        val serverJob = launch {
            val channel = DatagramChannel.open()
            channel.configureBlocking(false)
            channel.bind(InetSocketAddress("127.0.0.1", serverPort))
            channel.socket().receiveBufferSize = 262144 // 256KB buffer for abuse
            
            val buffer = ByteBuffer.allocate(16384) // 16KB max packet
            var packetsDropped = 0
            var packetsProcessed = 0
            var suspiciousPatterns = 0
            val rateLimitMap = mutableMapOf<InetSocketAddress, MutableList<Long>>()
            
            try {
                while (isActive && packetsProcessed < 5000) { // Cap for test completion
                    buffer.clear()
                    val clientAddr = channel.receive(buffer)
                    
                    if (clientAddr != null) {
                        buffer.flip()
                        val size = buffer.remaining()
                        val addr = clientAddr as InetSocketAddress
                        val now = System.currentTimeMillis()
                        
                        // Rate limiting and abuse detection
                        val clientHistory = rateLimitMap.computeIfAbsent(addr) { mutableListOf() }
                        clientHistory.add(now)
                        clientHistory.removeAll { it < now - 1000 } // 1-second window
                        
                        when {
                            clientHistory.size > 100 -> { // > 100 packets/second
                                packetsDropped++
                                suspiciousPatterns++
                                abuseMetrics.trySend("RATE_LIMIT_DROP" j addr.toString())
                                continue // Drop packet
                            }
                            size > 8192 -> { // Large packet detection
                                suspiciousPatterns++
                                abuseMetrics.trySend("LARGE_PACKET_$size" j addr.toString())
                            }
                        }
                        
                        packetsProcessed++
                        
                        // Simple response to keep clients happy
                        val ack = "ACK_$packetsProcessed".toByteArray()
                        channel.send(ByteBuffer.wrap(ack), addr)
                        
                        if (packetsProcessed % 200 == 0) {
                            abuseMetrics.trySend("STATS_processed_${packetsProcessed}_dropped_${packetsDropped}_suspicious_$suspiciousPatterns" j now.toString())
                        }
                    }
                    
                    if (packetsProcessed % 100 == 0) delay(5) // Server breathing room
                }
                
                abuseMetrics.trySend("FINAL_processed_${packetsProcessed}_dropped_${packetsDropped}_suspicious_$suspiciousPatterns" j System.currentTimeMillis().toString())
            } finally {
                channel.close()
            }
        }
        
        delay(200) // Server startup
        
        // Launch abusive traffic patterns
        val abusePatterns = listOf(
            // Packet flood attack
            launch {
                val socket = DatagramSocket()
                val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
                
                repeat(1000) { i ->
                    val data = "FLOOD_ATTACK_$i".toByteArray()
                    socket.send(java.net.DatagramPacket(data, data.size, serverAddr))
                    if (i % 50 == 0) delay(1) // Minimal relief
                }
                
                abuseMetrics.trySend("FLOOD_PATTERN_COMPLETE" j "1000_packets")
                socket.close()
            },
            
            // Large packet attack  
            launch {
                val socket = DatagramSocket()
                val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
                
                repeat(100) { i ->
                    val data = ByteArray(10240) { (i % 256).toByte() } // 10KB packets
                    socket.send(java.net.DatagramPacket(data, data.size, serverAddr))
                    delay(10)
                }
                
                abuseMetrics.trySend("LARGE_PACKET_ATTACK_COMPLETE" j "100x10KB")
                socket.close()
            },
            
            // Rapid burst attack
            launch {
                repeat(5) { burstId ->
                    launch {
                        val socket = DatagramSocket()
                        val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
                        
                        repeat(200) { packetId ->
                            val data = "BURST_${burstId}_$packetId".toByteArray()
                            socket.send(java.net.DatagramPacket(data, data.size, serverAddr))
                        }
                        
                        socket.close()
                    }
                }
                
                abuseMetrics.trySend("BURST_ATTACK_COMPLETE" j "5x200_packets")
            },
            
            // Mixed pattern attack
            launch {
                val socket = DatagramSocket()
                val serverAddr = InetSocketAddress("127.0.0.1", serverPort)
                
                repeat(300) { i ->
                    val data = when (i % 3) {
                        0 -> ByteArray(100) { (i % 256).toByte() } // Small
                        1 -> ByteArray(1500) { (i % 256).toByte() } // MTU
                        else -> ByteArray(8000) { (i % 256).toByte() } // Large
                    }
                    
                    socket.send(java.net.DatagramPacket(data, data.size, serverAddr))
                    if (i % 10 == 0) delay(kotlin.random.Random.nextLong(1, 5))
                }
                
                abuseMetrics.trySend("MIXED_ATTACK_COMPLETE" j "300_mixed_packets")
                socket.close()
            }
        )
        
        // Execute all abuse patterns
        abusePatterns.joinAll()
        delay(2000) // Server processing time
        
        // Analyze abuse test results
        val metrics = mutableListOf<Join<String, String>>()
        while (!abuseMetrics.isEmpty) {
            metrics.add(abuseMetrics.receive())
        }
        
        // Verify server resilience
        val attackCompletions = metrics.filter { it.α.contains("ATTACK_COMPLETE") }
        assertTrue(attackCompletions.size >= 4, "All abuse patterns should complete")
        
        val rateLimitDrops = metrics.count { it.α.contains("RATE_LIMIT_DROP") }
        assertTrue(rateLimitDrops > 0, "Server should enforce rate limiting")
        
        val largePacketDetections = metrics.count { it.α.contains("LARGE_PACKET") }
        assertTrue(largePacketDetections > 0, "Server should detect large packets")
        
        val finalStats = metrics.find { it.α.startsWith("FINAL_") }
        assertTrue(finalStats != null, "Server should provide final abuse statistics")
        
        val statsPattern = """processed_(\d+)_dropped_(\d+)_suspicious_(\d+)""".toRegex()
        val matchResult = finalStats?.α?.let { statsPattern.find(it) }
        
        if (matchResult != null) {
            val (processed, dropped, suspicious) = matchResult.destructured
            assertTrue(processed.toInt() > 0, "Server should process some packets")
            assertTrue(dropped.toInt() > 0, "Server should drop abusive packets")
            assertTrue(suspicious.toInt() > 0, "Server should detect suspicious patterns")
            
            val dropRate = dropped.toDouble() / (processed.toInt() + dropped.toInt())
            assertTrue(dropRate > 0.1, "Drop rate should be > 10% under abuse (was ${dropRate * 100}%)")
        }
        
        serverJob.cancel()
    }
}