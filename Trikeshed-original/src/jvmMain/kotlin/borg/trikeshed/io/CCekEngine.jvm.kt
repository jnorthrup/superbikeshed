package borg.trikeshed.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Deflater
import java.util.zip.Inflater

actual class CCekEngine {
    private val asyncIOEngine = AsyncIOEngine.create()
    private val incomingFlow = MutableSharedFlow<CCekMessage>()
    private val outgoingFlow = MutableSharedFlow<CCekMessage>()
    private val messageIdCounter = AtomicLong(1)
    private var compressionAlgorithm = CompressionAlgorithm.ZSTD
    private var compressionLevel = 6
    private var bufferSize = 8192
    private val stats = CompressionStats()
    
    actual fun initialize(compressionLevel: Int, bufferSize: Int) {
        this.compressionLevel = compressionLevel
        this.bufferSize = bufferSize
        asyncIOEngine.initialize()
    }
    
    actual fun cleanup() {
        asyncIOEngine.cleanup()
    }
    
    actual suspend fun send(target: String, data: ByteArray): Int {
        return suspendCancellableCoroutine { continuation ->
            try {
                val compressedData = compress(data)
                val messageId = messageIdCounter.getAndIncrement()
                
                val message = CCekMessage(
                    id = messageId,
                    source = "local",
                    target = target,
                    data = compressedData,
                    compressedSize = compressedData.size,
                    originalSize = data.size,
                    compressionRatio = compressedData.size.toDouble() / data.size,
                    algorithm = compressionAlgorithm
                )
                
                // Simulate async send using the async I/O engine
                val bytesSent = asyncIOEngine.write(1, compressedData, 0)
                
                // Update stats
                updateStats(message)
                
                // Emit to outgoing flow
                outgoingFlow.tryEmit(message)
                
                continuation.resume(bytesSent)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
    
    actual suspend fun <T> sendObject(target: String, obj: T): Int where T : Serializable {
        val serializedData = Json.encodeToByteArray(obj)
        return send(target, serializedData)
    }
    
    actual suspend fun receive(source: String, buffer: ByteArray): Int {
        return suspendCancellableCoroutine { continuation ->
            try {
                // Simulate async receive using the async I/O engine
                val bytesRead = asyncIOEngine.read(1, buffer, 0)
                
                if (bytesRead > 0) {
                    val decompressedData = decompress(buffer.copyOf(bytesRead))
                    val messageId = messageIdCounter.getAndIncrement()
                    
                    val message = CCekMessage(
                        id = messageId,
                        source = source,
                        target = "local",
                        data = decompressedData,
                        compressedSize = bytesRead,
                        originalSize = decompressedData.size,
                        compressionRatio = bytesRead.toDouble() / decompressedData.size,
                        algorithm = compressionAlgorithm
                    )
                    
                    // Update stats
                    updateStats(message)
                    
                    // Emit to incoming flow
                    incomingFlow.tryEmit(message)
                    
                    // Copy decompressed data to buffer
                    decompressedData.copyInto(buffer, 0, 0, minOf(decompressedData.size, buffer.size))
                    continuation.resume(decompressedData.size)
                } else {
                    continuation.resume(0)
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
    
    actual suspend fun <T> receiveObject(source: String, clazz: Class<T>): T? where T : Serializable {
        val buffer = ByteArray(bufferSize)
        val bytesRead = receive(source, buffer)
        
        return if (bytesRead > 0) {
            try {
                val data = buffer.copyOf(bytesRead)
                Json.decodeFromString(clazz, String(data))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    actual suspend fun broadcast(targets: List<String>, data: ByteArray): List<Int> {
        return targets.map { target ->
            send(target, data)
        }
    }
    
    actual fun incomingMessages(): Flow<CCekMessage> = incomingFlow
    
    actual fun outgoingMessages(): Flow<CCekMessage> = outgoingFlow
    
    actual fun getCompressionStats(): CompressionStats = stats
    
    actual fun setCompressionAlgorithm(algorithm: CompressionAlgorithm) {
        this.compressionAlgorithm = algorithm
    }
    
    actual companion object {
        actual fun create(): CCekEngine = CCekEngine()
    }
    
    private fun compress(data: ByteArray): ByteArray {
        return when (compressionAlgorithm) {
            CompressionAlgorithm.GZIP -> compressGzip(data)
            CompressionAlgorithm.NONE -> data
            else -> compressGzip(data) // Fallback to GZIP for other algorithms
        }
    }
    
    private fun decompress(data: ByteArray): ByteArray {
        return when (compressionAlgorithm) {
            CompressionAlgorithm.GZIP -> decompressGzip(data)
            CompressionAlgorithm.NONE -> data
            else -> decompressGzip(data) // Fallback to GZIP for other algorithms
        }
    }
    
    private fun compressGzip(data: ByteArray): ByteArray {
        val deflater = Deflater(compressionLevel)
        deflater.setInput(data)
        deflater.finish()
        
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        
        deflater.end()
        return outputStream.toByteArray()
    }
    
    private fun decompressGzip(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        
        inflater.end()
        return outputStream.toByteArray()
    }
    
    private fun updateStats(message: CCekMessage) {
        // Update compression statistics
        stats.totalMessages++
        stats.totalCompressedBytes += message.compressedSize
        stats.totalOriginalBytes += message.originalSize
        
        val currentUsage = stats.algorithmUsage.toMutableMap()
        currentUsage[message.algorithm] = currentUsage.getOrDefault(message.algorithm, 0) + 1
        
        // Calculate average compression ratio
        val totalRatio = stats.totalCompressedBytes.toDouble() / stats.totalOriginalBytes
        stats.averageCompressionRatio = totalRatio
    }
} 