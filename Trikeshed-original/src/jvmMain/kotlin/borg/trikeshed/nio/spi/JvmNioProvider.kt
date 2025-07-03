package borg.trikeshed.nio.spi

import borg.trikeshed.lib.*
import borg.trikeshed.nio.*
import kotlinx.datetime.Clock

/**
 * JVM-specific implementation of the NIO service provider
 */
class JvmNioProvider(
    private val attentionDelegate: AttentionDelegate = JvmAttentionDelegate()
) : NioServiceProvider {
    
    override val platformId: String = "jvm"
    
    override fun createBuffer(capacity: Int): PlatformByteBuffer {
        attentionDelegate.beforeOperation("createBuffer", "capacity=$capacity")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val buffer = PlatformByteBuffer.allocate(capacity)
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createBuffer", "success", null)
            buffer
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createBuffer", "failed", e)
            throw e
        }
    }
    
    override fun createChannel(): PlatformChannel {
        attentionDelegate.beforeOperation("createChannel")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val channel = PlatformChannel()
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createChannel", "success", null)
            channel
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createChannel", "failed", e)
            throw e
        }
    }
    
    override fun createDatagramSocket(): PlatformDatagramSocket {
        attentionDelegate.beforeOperation("createDatagramSocket")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val socket = PlatformDatagramSocket.create()
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createDatagramSocket", "success", null)
            socket
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createDatagramSocket", "failed", e)
            throw e
        }
    }
    
    override fun createDatagramSocket(address: PlatformInetSocketAddress): PlatformDatagramSocket {
        attentionDelegate.beforeOperation("createDatagramSocket", "address=${address.hostName}:${address.port}")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val socket = PlatformDatagramSocket.create(address)
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createDatagramSocket", "success", null)
            socket
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createDatagramSocket", "failed", e)
            throw e
        }
    }
    
    override fun createAddress(host: String, port: Int): PlatformInetSocketAddress {
        attentionDelegate.beforeOperation("createAddress", "host=$host, port=$port")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val address = PlatformInetSocketAddress(host, port)
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createAddress", "success", null)
            address
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createAddress", "failed", e)
            throw e
        }
    }
    
    override fun createPacket(data: ByteArray, length: Int, address: PlatformInetSocketAddress): PlatformDatagramPacket {
        attentionDelegate.beforeOperation("createPacket", "length=$length, address=${address.hostName}:${address.port}")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val packet = PlatformDatagramPacket(data, length, address)
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createPacket", "success", null)
            packet
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createPacket", "failed", e)
            throw e
        }
    }
    
    override fun getAttentionDelegate(): AttentionDelegate = attentionDelegate
}

/**
 * JVM-specific attention monitoring with performance thresholds
 */
class JvmAttentionDelegate(
    private val bufferThresholdMs: Long = 10,
    private val socketThresholdMs: Long = 100,
    private val channelThresholdMs: Long = 50,
    private val addressThresholdMs: Long = 5,
    private val packetThresholdMs: Long = 5
) : AttentionDelegate {
    
    override fun beforeOperation(operation: String, details: String) {
        if (details.isNotEmpty()) {
            println("[JVM-NIO] Starting $operation ($details)")
        } else {
            println("[JVM-NIO] Starting $operation")
        }
    }
    
    override fun afterOperation(operation: String, result: String, error: Throwable?) {
        val status = if (error == null) "SUCCESS" else "FAILED"
        println("[JVM-NIO] $operation completed - $status")
        
        if (error != null) {
            onOperationError(operation, error, "")
        }
    }
    
    override fun attentionTransferred(from: String, to: String, reason: String) {
        println("[JVM-NIO] Attention transferred from $from to $to: $reason")
    }
    
    override fun onSlowOperation(operation: String, durationMs: Long, threshold: Long, details: String) {
        val warning = if (details.isNotEmpty()) {
            "[JVM-NIO] SLOW OPERATION: $operation took ${durationMs}ms (threshold: ${threshold}ms) ($details)"
        } else {
            "[JVM-NIO] SLOW OPERATION: $operation took ${durationMs}ms (threshold: ${threshold}ms)"
        }
        println(warning)
    }
    
    override fun onOperationError(operation: String, error: Throwable, details: String) {
        val errorMsg = if (details.isNotEmpty()) {
            "[JVM-NIO] ERROR in $operation ($details): ${error.message}"
        } else {
            "[JVM-NIO] ERROR in $operation: ${error.message}"
        }
        println(errorMsg)
    }
    
    private fun getThresholdForOperation(operation: String): Long {
        return when {
            operation.contains("Buffer") -> bufferThresholdMs
            operation.contains("Socket") -> socketThresholdMs
            operation.contains("Channel") -> channelThresholdMs
            operation.contains("Address") -> addressThresholdMs
            operation.contains("Packet") -> packetThresholdMs
            else -> 50 // default threshold
        }
    }
}
