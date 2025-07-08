@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * JVM implementation of CCekEngine
 */
actual class CCekEngine {
    
    actual fun initialize(compressionLevel: Int, bufferSize: Int) {
        // JVM initialization
    }
    
    actual fun cleanup() {
        // JVM cleanup
    }
    
    actual suspend fun send(target: String, data: ByteArray): Int {
        // JVM send implementation
        return data.size
    }
    
    actual suspend fun <T> sendObject(target: String, obj: T): Int {
        // JVM sendObject implementation
        return 0
    }
    
    actual suspend fun receive(source: String, buffer: ByteArray): Int {
        // JVM receive implementation
        return 0
    }
    
    actual suspend fun <T> receiveObject(source: String): T? {
        // JVM receiveObject implementation
        return null
    }
    
    actual suspend fun broadcast(targets: List<String>, data: ByteArray): List<Int> {
        // JVM broadcast implementation
        return targets.map { data.size }
    }
    
    actual fun incomingMessages(): Flow<CCekMessage> {
        // JVM incoming messages flow
        return emptyFlow()
    }
    
    actual fun outgoingMessages(): Flow<CCekMessage> {
        // JVM outgoing messages flow
        return emptyFlow()
    }
    
    actual fun getCompressionStats(): CompressionStats {
        return CompressionStats()
    }
    
    actual fun setCompressionAlgorithm(algorithm: CompressionAlgorithm) {
        // JVM compression algorithm setting
    }
    
    actual companion object {
        actual fun create(): CCekEngine {
            return CCekEngine()
        }
    }
}