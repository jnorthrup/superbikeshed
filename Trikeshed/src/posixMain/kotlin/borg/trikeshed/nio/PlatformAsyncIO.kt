package borg.trikeshed.nio

import borg.trikeshed.lib.ByteIndexedBuffer
import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.kqueue

actual class PlatformAsyncIO actual constructor(private val fd: Int) : PlatformAsyncIO {
    actual suspend fun read(buffer: ByteIndexedBuffer): Int {
        // TODO: Implement kqueue read
        return -1
    }

    actual suspend fun write(buffer: ByteIndexedBuffer): Int {
        // TODO: Implement kqueue write
        return -1
    }

    actual suspend fun accept(): PlatformAsyncIO {
        // TODO: Implement kqueue accept
        throw NotImplementedError("Not yet implemented")
    }

    actual suspend fun connect(address: PlatformInetSocketAddress) {
        // TODO: Implement kqueue connect
        throw NotImplementedError("Not yet implemented")
    }

    actual fun close() {
        // TODO: Implement kqueue close
    }

    actual val isOpen: Boolean
        get() = false // TODO: Implement actual check
}
