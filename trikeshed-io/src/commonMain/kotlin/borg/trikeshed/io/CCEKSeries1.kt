@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.*

/**
 * CCEK Series 1: Protocol Stack Composition
 * 
 * Provides composable protocol layers that can be stacked and combined
 * to create complex protocol implementations from simple building blocks.
 */

// === PROTOCOL LAYER INTERFACE ===

/**
 * Protocol layer interface - each layer can transform messages
 */
interface ProtocolLayer {
    val layerId: String
    val priority: Int
    
    suspend fun processIncoming(message: CCekMessage): CCekMessage?
    suspend fun processOutgoing(message: CCekMessage): CCekMessage?
    
    fun canHandle(message: CCekMessage): Boolean
}

/**
 * Protocol layer with CCEK context
 */
interface CCEKProtocolLayer : ProtocolLayer {
    suspend fun processWithContext(
        message: CCekMessage,
        context: CCEKContext
    ): CCekMessage?
}

// === PROTOCOL STACK COMPOSITION ===

/**
 * Composable protocol stack
 */
class ProtocolStack(
    internal val layers: Indexed<ProtocolLayer> = 0 j { throw IndexOutOfBoundsException() }
) {
    
    /**
     * Add a layer to the stack
     */
    fun addLayer(layer: ProtocolLayer): ProtocolStack {
        val newLayers = Array(layers.a + 1) { i ->
            if (i < layers.a) layers.b(i) else layer
        }
        return ProtocolStack(newLayers.size j newLayers::get)
    }
    
    /**
     * Remove a layer by ID
     */
    fun removeLayer(layerId: String): ProtocolStack {
        val filteredLayers = layers.play.filter { it.layerId != layerId }
        return ProtocolStack(filteredLayers.size j { i -> filteredLayers[i] })
    }
    
    /**
     * Process message through all layers
     */
    suspend fun processIncoming(message: CCekMessage): CCekMessage? {
        var currentMessage = message
        
        // Process through layers in priority order (lowest first)
        val sortedLayers = layers.play.sortedBy { it.priority }
        
        for (layer in sortedLayers) {
            if (layer.canHandle(currentMessage)) {
                val processed = layer.processIncoming(currentMessage)
                if (processed == null) return null // Layer dropped the message
                currentMessage = processed
            }
        }
        
        return currentMessage
    }
    
    /**
     * Process message through all layers in reverse order
     */
    suspend fun processOutgoing(message: CCekMessage): CCekMessage? {
        var currentMessage = message
        
        // Process through layers in reverse priority order (highest first)
        val sortedLayers = layers.play.sortedByDescending { it.priority }
        
        for (layer in sortedLayers) {
            if (layer.canHandle(currentMessage)) {
                val processed = layer.processOutgoing(currentMessage)
                if (processed == null) return null // Layer dropped the message
                currentMessage = processed
            }
        }
        
        return currentMessage
    }
    
    /**
     * Get layer by ID
     */
    fun getLayer(layerId: String): ProtocolLayer? {
        return layers.play.find { it.layerId == layerId }
    }
    
    /**
     * Check if stack contains layer
     */
    fun hasLayer(layerId: String): Boolean {
        return getLayer(layerId) != null
    }
}

// === BUILT-IN PROTOCOL LAYERS ===

/**
 * Compression layer
 */
class CompressionLayer(
    internal val algorithm: CompressionAlgorithm = CompressionAlgorithm.ZSTD
) : ProtocolLayer {
    override val layerId = "compression"
    override val priority = 100
    
    override suspend fun processIncoming(message: CCekMessage): CCekMessage? {
        return if (message.algorithm != CompressionAlgorithm.NONE) {
            // Decompress message
            val decompressedData = decompress(message.data, message.algorithm)
            message.copy(
                data = decompressedData,
                compressedSize = message.data.size,
                originalSize = decompressedData.size,
                compressionRatio = message.data.size.toDouble() / decompressedData.size,
                algorithm = CompressionAlgorithm.NONE
            )
        } else message
    }
    
    override suspend fun processOutgoing(message: CCekMessage): CCekMessage? {
        return if (message.algorithm == CompressionAlgorithm.NONE) {
            // Compress message
            val compressedData = compress(message.data, algorithm)
            message.copy(
                data = compressedData,
                compressedSize = compressedData.size,
                originalSize = message.data.size,
                compressionRatio = compressedData.size.toDouble() / message.data.size,
                algorithm = algorithm
            )
        } else message
    }
    
    override fun canHandle(message: CCekMessage): Boolean = true
    
    internal fun compress(data: ByteArray, algorithm: CompressionAlgorithm): ByteArray {
        // Placeholder compression - in production use actual compression libraries
        return when (algorithm) {
            CompressionAlgorithm.ZSTD -> data // Placeholder
            CompressionAlgorithm.LZ4 -> data // Placeholder
            CompressionAlgorithm.GZIP -> data // Placeholder
            CompressionAlgorithm.BROTLI -> data // Placeholder
            CompressionAlgorithm.NONE -> data
        }
    }
    
    internal fun decompress(data: ByteArray, algorithm: CompressionAlgorithm): ByteArray {
        // Placeholder decompression - in production use actual compression libraries
        return when (algorithm) {
            CompressionAlgorithm.ZSTD -> data // Placeholder
            CompressionAlgorithm.LZ4 -> data // Placeholder
            CompressionAlgorithm.GZIP -> data // Placeholder
            CompressionAlgorithm.BROTLI -> data // Placeholder
            CompressionAlgorithm.NONE -> data
        }
    }
}

/**
 * Encryption layer
 */
class EncryptionLayer(
    internal val key: String? = null
) : ProtocolLayer {
    override val layerId = "encryption"
    override val priority = 200
    
    override suspend fun processIncoming(message: CCekMessage): CCekMessage? {
        // Decrypt message if encrypted
        return if (isEncrypted(message)) {
            val decryptedData = decrypt(message.data)
            message.copy(data = decryptedData)
        } else message
    }
    
    override suspend fun processOutgoing(message: CCekMessage): CCekMessage? {
        // Encrypt message if key is available
        return if (key != null) {
            val encryptedData = encrypt(message.data)
            message.copy(data = encryptedData)
        } else message
    }
    
    override fun canHandle(message: CCekMessage): Boolean = key != null
    
    internal fun isEncrypted(message: CCekMessage): Boolean {
        // Simple check - in production use proper encryption detection
        return message.data.size > 0 && message.data[0] == 0x01.toByte()
    }
    
    internal fun encrypt(data: ByteArray): ByteArray {
        // Placeholder encryption - in production use actual encryption
        return byteArrayOf(0x01) + data
    }
    
    internal fun decrypt(data: ByteArray): ByteArray {
        // Placeholder decryption - in production use actual decryption
        return if (data.isNotEmpty() && data[0] == 0x01.toByte()) {
            data.copyOfRange(1, data.size)
        } else data
    }
}

/**
 * Routing layer
 */
class RoutingLayer(
    internal val routingTable: Map<String, String> = emptyMap()
) : ProtocolLayer {
    override val layerId = "routing"
    override val priority = 50
    
    override suspend fun processIncoming(message: CCekMessage): CCekMessage? {
        // Route incoming message based on source
        val newTarget = routingTable[message.source] ?: message.target
        return message.copy(target = newTarget)
    }
    
    override suspend fun processOutgoing(message: CCekMessage): CCekMessage? {
        // Route outgoing message based on target
        val newTarget = routingTable[message.target] ?: message.target
        return message.copy(target = newTarget)
    }
    
    override fun canHandle(message: CCekMessage): Boolean = true
}

/**
 * Validation layer
 */
class ValidationLayer(
    internal val validator: (CCekMessage) -> Boolean = { true }
) : ProtocolLayer {
    override val layerId = "validation"
    override val priority = 25
    
    override suspend fun processIncoming(message: CCekMessage): CCekMessage? {
        return if (validator(message)) message else null
    }
    
    override suspend fun processOutgoing(message: CCekMessage): CCekMessage? {
        return if (validator(message)) message else null
    }
    
    override fun canHandle(message: CCekMessage): Boolean = true
}

/**
 * Logging layer
 */
class LoggingLayer(
    internal val logger: (String, CCekMessage) -> Unit = { level, msg -> 
        println("[$level] ${msg.id}: ${msg.source} -> ${msg.target}")
    }
) : ProtocolLayer {
    override val layerId = "logging"
    override val priority = 10
    
    override suspend fun processIncoming(message: CCekMessage): CCekMessage? {
        logger("INCOMING", message)
        return message
    }
    
    override suspend fun processOutgoing(message: CCekMessage): CCekMessage? {
        logger("OUTGOING", message)
        return message
    }
    
    override fun canHandle(message: CCekMessage): Boolean = true
}

// === PROTOCOL STACK FACTORY ===

/**
 * Protocol stack factory for common configurations
 */
object ProtocolStackFactory {
    
    /**
     * Create a basic stack with compression and logging
     */
    fun createBasicStack(): ProtocolStack {
        return ProtocolStack()
            .addLayer(LoggingLayer())
            .addLayer(CompressionLayer())
    }
    
    /**
     * Create a secure stack with encryption
     */
    fun createSecureStack(key: String): ProtocolStack {
        return ProtocolStack()
            .addLayer(LoggingLayer())
            .addLayer(CompressionLayer())
            .addLayer(EncryptionLayer(key))
    }
    
    /**
     * Create a routed stack
     */
    fun createRoutedStack(routingTable: Map<String, String>): ProtocolStack {
        return ProtocolStack()
            .addLayer(LoggingLayer())
            .addLayer(RoutingLayer(routingTable))
            .addLayer(CompressionLayer())
    }
    
    /**
     * Create a validated stack
     */
    fun createValidatedStack(validator: (CCekMessage) -> Boolean): ProtocolStack {
        return ProtocolStack()
            .addLayer(LoggingLayer())
            .addLayer(ValidationLayer(validator))
            .addLayer(CompressionLayer())
    }
}

// === CCEK CONTEXT INTEGRATION ===

/**
 * CCEK context for protocol stack operations
 */
data class CCEKContext(
    val stackId: String,
    val configuration: Map<String, Any> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Protocol stack with CCEK context support
 */
class CCEKProtocolStack(
    internal val stack: ProtocolStack,
    internal val context: CCEKContext
) {
    
    suspend fun processIncoming(message: CCekMessage): CCekMessage? {
        return stack.processIncoming(message)
    }
    
    suspend fun processOutgoing(message: CCekMessage): CCekMessage? {
        return stack.processOutgoing(message)
    }
    
    fun getContext(): CCEKContext = context
    
    fun updateContext(newContext: CCEKContext): CCEKProtocolStack {
        return CCEKProtocolStack(stack, newContext)
    }
} 