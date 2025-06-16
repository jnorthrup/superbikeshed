package borg.trikeshed.io

import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths

actual class PosixFile(
    actual val path: String?,
) : HasDescriptor, HasSize, Usable {
    private val randomAccessFile: RandomAccessFile = RandomAccessFile(path, "rw")
    private val channel: FileChannel = randomAccessFile.channel

    actual fun read(buf: ByteArray): UInt {
        return channel.read(java.nio.ByteBuffer.wrap(buf)).toUInt()
    }

    actual fun write(buf: ByteArray): UInt {
        return channel.write(java.nio.ByteBuffer.wrap(buf)).toUInt()
    }

    actual override fun read64(buf: ByteArray): ULong {
        val buffer = java.nio.ByteBuffer.wrap(buf)
        return channel.read(buffer).toULong()
    }

    actual fun write64(buf: ByteArray): ULong {
        val buffer = java.nio.ByteBuffer.wrap(buf)
        return channel.write(buffer).toULong()
    }

    actual override fun close(): Int {
        channel.close()
        randomAccessFile.close()
        return 0  // Simulate success
    }

    actual override fun stat(): PosixStat {
        val pathObj = Paths.get(path)
        val size = Files.size(pathObj)
        val lastModified = Files.getLastModifiedTime(pathObj).toMillis()
        return object : PosixStat {
            override val size: Long = size
            override val lastModified: Long = lastModified
        }
    }

    actual override val size: PosixOffset
        get() = channel.size()

    actual override fun seek(offset: PosixOffset, whence: Int): ULong {
        when (whence) {
            SEEK_SET_CONSTANT -> channel.position(offset)
        }
        return channel.position().toULong()
    }

    actual fun mmap(len: ULong, prot: Int, flags: Int, offset: PosixOffset): MappedPointer {
        val mappedByteBuffer: MappedByteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, offset, len.toLong())
        return object : MappedPointer {
            override fun getByte(index: Long): Byte = mappedByteBuffer.get(index.toInt())
            override fun putByte(index: Long, value: Byte) = mappedByteBuffer.put(index.toInt(), value)
            override fun unmap() {
                // No direct unmap in JVM; buffer is released on channel close
            }
        }
    }

    actual companion object {
        actual fun open(path: String?, flags: Int, mode: Int? = null): PosixFile {
            return PosixFile(path)
        }
    }
}

actual class MappedPointer() { //fix MappedPointer actual constructor to match expect
    // JVM-specific implementation as above
    fun getByte(index: Long): Byte = TODO()
    fun putByte(index: Long, value: Byte) = TODO()
    fun unmap() {}
}


actual val SEEK_SET_CONSTANT: Int = 0  // SEEK_SET equivalent
actual val O_RDONLY_FLAG: Int = 1  // Placeholder