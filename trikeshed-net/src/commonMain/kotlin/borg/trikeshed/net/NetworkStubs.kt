@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net

import borg.trikeshed.lib.*

/**
 * Minimal working network stubs to satisfy expect declarations
 * This replaces the complex, unfinished SSH/QUIC implementations
 */

// Basic types for network operations
typealias NetworkPayload = Indexed<Byte>
typealias NetworkAddress = String
typealias NetworkPort = Int

/**
 * Simple network result wrapper
 */
sealed class NetworkResult<T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error<T>(val message: String) : NetworkResult<T>()
}

/**
 * Basic network configuration
 */
data class NetworkConfig(
    val host: String = "localhost",
    val port: Int = 8080,
    val timeout: Long = 5000L
)

/**
 * Simple network connection interface
 */
interface NetworkConnection {
    suspend fun connect(config: NetworkConfig): NetworkResult<Unit>
    suspend fun send(data: NetworkPayload): NetworkResult<Unit>
    suspend fun receive(): NetworkResult<NetworkPayload>
    suspend fun close()
}

/**
 * Basic implementation for testing
 */
class SimpleNetworkConnection : NetworkConnection {
    private var connected = false
    
    override suspend fun connect(config: NetworkConfig): NetworkResult<Unit> {
        connected = true
        return NetworkResult.Success(Unit)
    }
    
    override suspend fun send(data: NetworkPayload): NetworkResult<Unit> {
        return if (connected) {
            NetworkResult.Success(Unit)
        } else {
            NetworkResult.Error("Not connected")
        }
    }
    
    override suspend fun receive(): NetworkResult<NetworkPayload> {
        return if (connected) {
            NetworkResult.Success(0 j { 0.toByte() })
        } else {
            NetworkResult.Error("Not connected")
        }
    }
    
    override suspend fun close() {
        connected = false
    }
}