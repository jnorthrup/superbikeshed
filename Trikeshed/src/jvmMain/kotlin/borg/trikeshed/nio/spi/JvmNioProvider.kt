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
            attentionDelegate.afterOperation("createBuffer", duration, true, "capacity=$capacity")
            buffer
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.onOperationError("createBuffer", e, "capacity=$capacity")
            attentionDelegate.afterOperation("createBuffer", duration, false, "capacity=$capacity")
            throw e
        }
    }
    
    override fun createChannel(): PlatformChannel {
        attentionDelegate.beforeOperation("createChannel")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val channel = PlatformChannel()
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createChannel", duration, true)
            channel
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.onOperationError("createChannel", e)
            attentionDelegate.afterOperation("createChannel", duration, false)
            throw e
        }
    }
    
    override fun createDatagramSocket(): PlatformDatagramSocket {
        attentionDelegate.beforeOperation("createDatagramSocket")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val socket = PlatformDatagramSocket.create()
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createDatagramSocket", duration, true)
            socket
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.onOperationError("createDatagramSocket", e)
            attentionDelegate.afterOperation("createDatagramSocket", duration, false)
            throw e
        }
    }
    
    override fun createDatagramSocket(address: PlatformInetSocketAddress): PlatformDatagramSocket {
        attentionDelegate.beforeOperation("createDatagramSocket", "address=${address.hostName}:${address.port}")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val socket = PlatformDatagramSocket.create(address)
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createDatagramSocket", duration, true, "address=${address.hostName}:${address.port}")
            socket
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.onOperationError("createDatagramSocket", e, "address=${address.hostName}:${address.port}")
            attentionDelegate.afterOperation("createDatagramSocket", duration, false, "address=${address.hostName}:${address.port}")
            throw e
        }
    }
    
    override fun createAddress(host: String, port: Int): PlatformInetSocketAddress {
        attentionDelegate.beforeOperation("createAddress", "host=$host, port=$port")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val address = PlatformInetSocketAddress(host, port)
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createAddress", duration, true, "host=$host, port=$port")
            address
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.onOperationError("createAddress", e, "host=$host, port=$port")
            attentionDelegate.afterOperation("createAddress", duration, false, "host=$host, port=$port")
            throw e
        }
    }
    
    override fun createPacket(data: ByteArray, length: Int, address: PlatformInetSocketAddress): PlatformDatagramPacket {
        attentionDelegate.beforeOperation("createPacket", "length=$length, address=${address.hostName}:${address.port}")
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        return try {
            val packet = PlatformDatagramPacket(data, length, address)
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.afterOperation("createPacket", duration, true, "length=$length, address=${address.hostName}:${address.port}")
            packet
        } catch (e: Exception) {
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            attentionDelegate.onOperationError("createPacket", e, "length=$length, address=${address.hostName}:${address.port}")
            attentionDelegate.afterOperation("createPacket", duration, false, "length=$length, address=${address.hostName}:${address.port}")
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
    
    override fun afterOperation(operation: String, durationMs: Long, success: Boolean, details: String) {
        val threshold = getThresholdForOperation(operation)
        val status = if (success) "SUCCESS" else "FAILED"
        
        if (details.isNotEmpty()) {
            println("[JVM-NIO] $operation completed in ${durationMs}ms - $status ($details)")
        } else {
            println("[JVM-NIO] $operation completed in ${durationMs}ms - $status")
        }
        
        if (durationMs > threshold) {
            onSlowOperation(operation, durationMs, threshold, details)
        }
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