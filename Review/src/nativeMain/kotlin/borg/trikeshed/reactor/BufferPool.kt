package borg.trikeshed.reactor

actual open class BufferPool {
    actual open fun acquire(): ByteArray {
        println("BufferPool.acquire() called (Native placeholder)")
        return ByteArray(1024) // Default buffer size
    }

    actual open fun release(buffer: ByteArray) {
        println("BufferPool.release() called (Native placeholder)")
        // No-op for a simple array pool
    }
}

actual abstract class IOOperation(actual override val value: Int) {
    actual companion object {
        actual val ACCEPT: IOOperation = object : IOOperation(16) {}
        actual val READ: IOOperation = object : IOOperation(1) {}
        actual val WRITE: IOOperation = object : IOOperation(4) {}
        actual val CONNECT: IOOperation = object : IOOperation(8) {}
    }
}
