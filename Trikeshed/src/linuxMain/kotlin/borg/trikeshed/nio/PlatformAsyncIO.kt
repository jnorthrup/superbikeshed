package borg.trikeshed.nio

import borg.trikeshed.lib.ByteIndexedBuffer
import kotlinx.cinterop.*
import platform.posix.*

actual class PlatformAsyncIO actual constructor(private val fd: Int) : PlatformAsyncIO {
    actual suspend fun read(buffer: ByteIndexedBuffer): Int {
        // TODO: Implement liburing read
        return -1
    }

    actual suspend fun write(buffer: ByteIndexedBuffer): Int {
        // TODO: Implement liburing write
        return -1
    }

    actual suspend fun accept(): PlatformAsyncIO {
        // TODO: Implement liburing accept
        throw NotImplementedError("Not yet implemented")
    }

    actual suspend fun connect(address: PlatformInetSocketAddress) {
        // TODO: Implement liburing connect
        throw NotImplementedError("Not yet implemented")
    }

    actual fun close() {
        // TODO: Implement liburing close
    }

    actual val isOpen: Boolean
        get() = false // TODO: Implement actual check
}
