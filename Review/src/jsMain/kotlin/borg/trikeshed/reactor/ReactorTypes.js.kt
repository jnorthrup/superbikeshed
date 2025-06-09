package borg.trikeshed.reactor

actual abstract class IOOperation {
    actual abstract val value: Int

    actual companion object {
        actual val ACCEPT: IOOperation
            get() = TODO("Not yet implemented")
        actual val READ: IOOperation
            get() = TODO("Not yet implemented")
        actual val WRITE: IOOperation
            get() = TODO("Not yet implemented")
    }
}

actual class PlatformIO {
    actual suspend fun createSelector(): SelectorInterface {
        TODO("Not yet implemented")
    }

    actual suspend fun createServerChannel(): ServerChannel {
        TODO("Not yet implemented")
    }

    actual suspend fun createClientChannel(): ClientChannel {
        TODO("Not yet implemented")
    }

    actual suspend fun createBufferPool(bufferSize: Int): BufferPool {
        TODO("Not yet implemented")
    }

    actual companion object {
        actual suspend fun create(): PlatformIO {
            TODO("Not yet implemented")
        }
    }
}

actual open class BufferPool {
    actual open fun acquire(): ByteArray {
        TODO("Not yet implemented")
    }

    actual open fun release(buffer: ByteArray) {
    }
}

actual abstract class SelectionKey {
    actual abstract val isValid: Boolean
    actual abstract val readyOps: Int
    actual abstract var interestOps: Int
    actual abstract var attachment: Any?
    actual abstract fun cancel()
    actual abstract fun channel(): SelectableChannel
}