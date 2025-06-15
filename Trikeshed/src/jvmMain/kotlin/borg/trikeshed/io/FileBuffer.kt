package borg.trikeshed.io

import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

actual class FileBuffer {
    private var randomAccessFile: RandomAccessFile? = null
    private var mappedByteBuffer: MappedByteBuffer? = null

    actual fun open(path: String) {
        randomAccessFile = RandomAccessFile(path, "rw")
        mappedByteBuffer = randomAccessFile?.channel?.map(FileChannel.MapMode.READ_WRITE, 0, randomAccessFile!!.length())
    }

    actual fun close() {
        mappedByteBuffer?.force()
        randomAccessFile?.close()
    }

    actual fun get(index: Long): Byte {
        return mappedByteBuffer?.get(index.toInt()) ?: throw Error("Buffer not mapped")
    }

    actual fun put(index: Long, value: Byte) {
        mappedByteBuffer?.put(index.toInt(), value)
    }

    actual val size: Long
        get() = randomAccessFile?.length() ?: 0
}