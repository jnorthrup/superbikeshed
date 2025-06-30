package borg.trikeshed.io

import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class MappedFile private constructor(
    val size: Long,
    private val segment: Any?,
    private val buffer: MappedByteBuffer?,
    private val byteArray: ByteArray?
) {
    companion object {
        fun map(path: Path, size: Long, readOnly: Boolean = false): MappedFile {
            return try {
                // Try MemorySegment (Java 19+)
                val memorySegmentClass = Class.forName("java.lang.foreign.MemorySegment")
                val fileChannel = FileChannel.open(path, if (readOnly) setOf(StandardOpenOption.READ) else setOf(StandardOpenOption.READ, StandardOpenOption.WRITE))
                val mapMode = if (readOnly) FileChannel.MapMode.READ_ONLY else FileChannel.MapMode.READ_WRITE
                val buffer = fileChannel.map(mapMode, 0, size)
                // Use MemorySegment.ofBuffer if available
                val ofBuffer = memorySegmentClass.getMethod("ofBuffer", ByteBuffer::class.java)
                val segment = ofBuffer.invoke(null, buffer)
                MappedFile(size, segment, buffer, null)
            } catch (e: Throwable) {
                // Fallback to MappedByteBuffer
                val raf = RandomAccessFile(path.toFile(), if (readOnly) "r" else "rw")
                val channel = raf.channel
                val mapMode = if (readOnly) FileChannel.MapMode.READ_ONLY else FileChannel.MapMode.READ_WRITE
                val buffer = channel.map(mapMode, 0, size)
                MappedFile(size, null, buffer, null)
            }
        }

        fun allocate(size: Long): MappedFile {
            return try {
                // Try MemorySegment (Java 19+)
                val memorySegmentClass = Class.forName("java.lang.foreign.MemorySegment")
                val allocateNative = memorySegmentClass.getMethod("allocateNative", Long::class.javaPrimitiveType)
                val segment = allocateNative.invoke(null, size)
                MappedFile(size, segment, null, null)
            } catch (e: Throwable) {
                // Fallback to ByteArray
                MappedFile(size, null, null, ByteArray(size.toInt()))
            }
        }
    }

    fun getByte(offset: Long): Byte {
        require(offset in 0 until size) { "Offset out of bounds" }
        return when {
            segment != null -> {
                // Use MemorySegment getAtIndex
                val msClass = segment.javaClass
                val getAtIndex = msClass.getMethod("get", Long::class.javaPrimitiveType)
                getAtIndex.invoke(segment, offset) as Byte
            }
            buffer != null -> buffer.get(offset.toInt())
            byteArray != null -> byteArray[offset.toInt()]
            else -> throw IllegalStateException("No backing store")
        }
    }

    fun setByte(offset: Long, value: Byte) {
        require(offset in 0 until size) { "Offset out of bounds" }
        when {
            segment != null -> {
                // Use MemorySegment setAtIndex
                val msClass = segment.javaClass
                val setAtIndex = msClass.getMethod("set", Long::class.javaPrimitiveType, Byte::class.javaPrimitiveType)
                setAtIndex.invoke(segment, offset, value)
            }
            buffer != null -> buffer.put(offset.toInt(), value)
            byteArray != null -> byteArray[offset.toInt()] = value
            else -> throw IllegalStateException("No backing store")
        }
    }

    fun asByteArray(): ByteArray {
        return when {
            segment != null -> {
                // Use MemorySegment to copy to ByteArray
                val arr = ByteArray(size.toInt())
                for (i in arr.indices) arr[i] = getByte(i.toLong())
                arr
            }
            buffer != null -> {
                val arr = ByteArray(size.toInt())
                buffer.position(0)
                buffer.get(arr)
                arr
            }
            byteArray != null -> byteArray.copyOf()
            else -> throw IllegalStateException("No backing store")
        }
    }

    fun close() {
        // Unmap logic (optional, handled by GC in JVM)
    }

    val backingStore: Any?
        get() = segment ?: buffer ?: byteArray
} 