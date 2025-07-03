@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.ipc

import borg.trikeshed.lib.*
import kotlinx.coroutines.*

/**
 * WASM IPC implementation - receives commands from JVM host
 */
class WasmIPCHost(
    private val wasmHostId: ProcessId
) : IPCHost(wasmHostId, TransportTypes.WASM) {
    
    override suspend fun createChannel(
        channelId: ChannelId,
        mode: IPCMode,
        config: IPCChannelConfig
    ): IPCChannel {
        // WASM channels are created by the JVM host
        return WasmIPCChannel(channelId)
    }
    
    override suspend fun connectChannel(
        address: IPCAddress,
        port: IPCPort,
        channelId: ChannelId
    ): IPCChannel {
        // WASM doesn't connect directly, uses JVM bridge
        return WasmIPCChannel(channelId)
    }
}

class WasmIPCChannel(
    override val id: ChannelId
) : IPCChannel {
    override val mode = IPCModes.SHARED_MEMORY
    
    override suspend fun send(message: IPCMessage): Boolean {
        // Wasm JS interop limitation: can't pass ByteArray directly
        // The host environment would need to handle data serialization
        return ipcSend(
            message.destination,
            message.payload.a
        ) == 0
    }
    
    override suspend fun receive(): IPCMessage? {
        // Call external IPC receive function
        // Returns base64 encoded data that would need to be decoded
        val result = ipcReceive(1024)
        
        return if (result.isNotEmpty()) {
            // In a real implementation, decode base64 to bytes
            // For now, create a stub message
            IPCMessage(
                id = 0,
                type = MessageTypes.DATA,
                source = 0,
                destination = 0,  // Default destination for stub
                channelId = id,
                payload = ByteArray(0).toIdx()  // Stub implementation
            )
        } else null
    }
    
    override suspend fun close() {
        // Notify host of channel closure
        ipcClose(id)
    }
    
    override fun isOpen() = true
    override fun messageCount() = 0
}

// External functions provided by host
// Note: Wasm JS interop only supports primitives, strings, and functions
// These would need to be wrapped by the host environment
external fun ipcSend(destination: Int, size: Int): Int
external fun ipcReceive(size: Int): String  // Returns base64 encoded data
external fun ipcClose(channelId: String): Int

/**
 * Create WASM IPC host
 */
actual suspend fun createWASMIPCHost(hostId: ProcessId): IPCHost = WasmIPCHost(hostId)

/**
 * Create hybrid IPC host (not applicable for WASM)
 */
actual suspend fun createHybridIPCHost(hostId: ProcessId): IPCHost = WasmIPCHost(hostId)