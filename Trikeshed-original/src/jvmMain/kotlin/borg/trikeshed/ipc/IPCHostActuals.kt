@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.ipc

/**
 * JVM actual implementations for IPC host factories
 */

actual suspend fun createNativeIPCHost(hostId: ProcessId): IPCHost {
    // On JVM, we simulate native IPC using JVM mechanisms
    return JvmIPCHost(hostId)
}

actual suspend fun createJVMIPCHost(hostId: ProcessId): IPCHost {
    return JvmIPCHost(hostId)
}

actual suspend fun createWASMIPCHost(hostId: ProcessId): IPCHost {
    // On JVM, WASM runs inside GraalVM
    return JvmIPCHost(hostId).apply {
        // Initialize with WASM support
    }
}

actual suspend fun createHybridIPCHost(hostId: ProcessId): IPCHost {
    // Use JVM-based implementation for now - Truffle dependencies not available
    return JvmIPCHost(hostId)
}