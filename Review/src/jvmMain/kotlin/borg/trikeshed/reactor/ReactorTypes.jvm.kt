package borg.trikeshed.reactor

actual open class BufferPool {
    actual open fun acquire(): ByteArray {
        TODO("Not yet implemented")
    }

    actual open fun release(buffer: ByteArray) {
    }
}