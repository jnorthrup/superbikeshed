package borg.trikeshed.ipc

import borg.trikeshed.lib.*

/**
 * WasmJs implementations of IPC host factory functions
 */

actual suspend fun createNativeIPCHost(hostId: ProcessId): IPCHost {
    return WasmIPCHost(hostId)
}

actual suspend fun createJVMIPCHost(hostId: ProcessId): IPCHost {
    return WasmIPCHost(hostId)
}