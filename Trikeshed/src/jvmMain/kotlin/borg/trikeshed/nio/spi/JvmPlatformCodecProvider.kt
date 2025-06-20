package borg.trikeshed.nio.spi

import borg.trikeshed.nio.PlatformByteBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class JvmPlatformCodecProvider : PlatformCodecProvider {
    private val attentionDelegate = JvmPlatformCodecAttentionDelegate()
    
    override fun getAttentionDelegate(): AttentionDelegate = attentionDelegate
    
    // Default to Big Endian as it's common for network protocols and Java DataStreams
    private val defaultByteOrder = ByteOrder.BIG_ENDIAN
    
    // === READ OPERATIONS ===
    override fun readLong(bytes: ByteArray): Long {
        val startTime = System.nanoTime()
        val buffer = ByteBuffer.wrap(bytes).order(defaultByteOrder)
        val result = buffer.getLong()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onReadOperation("Long", bytes.size, duration)
        return result
    }
    
    override fun readInt(bytes: ByteArray): Int {
        val startTime = System.nanoTime()
        val buffer = ByteBuffer.wrap(bytes).order(defaultByteOrder)
        val result = buffer.getInt()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onReadOperation("Int", bytes.size, duration)
        return result
    }
    
    override fun readShort(bytes: ByteArray): Short {
        val startTime = System.nanoTime()
        val buffer = ByteBuffer.wrap(bytes).order(defaultByteOrder)
        val result = buffer.getShort()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onReadOperation("Short", bytes.size, duration)
        return result
    }
    
    override fun readDouble(bytes: ByteArray): Double {
        val startTime = System.nanoTime()
        val result = Double.fromBits(readLong(bytes))
        val duration = System.nanoTime() - startTime
        attentionDelegate.onReadOperation("Double", bytes.size, duration)
        return result
    }
    
    override fun readFloat(bytes: ByteArray): Float {
        val startTime = System.nanoTime()
        val result = Float.fromBits(readInt(bytes))
        val duration = System.nanoTime() - startTime
        attentionDelegate.onReadOperation("Float", bytes.size, duration)
        return result
    }
    
    override fun readUShort(bytes: ByteArray): Int {
        val startTime = System.nanoTime()
        val buffer = ByteBuffer.wrap(bytes).order(defaultByteOrder)
        val result = buffer.getShort().toInt() and 0xFFFF
        val duration = System.nanoTime() - startTime
        attentionDelegate.onReadOperation("UShort", bytes.size, duration)
        return result
    }
    
    override fun readUInt(bytes: ByteArray): Long {
        val startTime = System.nanoTime()
        val buffer = ByteBuffer.wrap(bytes).order(defaultByteOrder)
        val result = buffer.getInt().toLong() and 0xFFFFFFFFL
        val duration = System.nanoTime() - startTime
        attentionDelegate.onReadOperation("UInt", bytes.size, duration)
        return result
    }
    
    override fun readULong(bytes: ByteArray): Long {
        val startTime = System.nanoTime()
        // This is a simplification and may not correctly handle all ULong values
        val buffer = ByteBuffer.wrap(bytes).order(defaultByteOrder)
        val result = buffer.getLong()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onReadOperation("ULong", bytes.size, duration)
        return result
    }
    
    // === WRITE OPERATIONS ===
    override fun writeLong(value: Long): ByteArray {
        val startTime = System.nanoTime()
        val buffer = ByteBuffer.allocate(Long.SIZE_BYTES).order(defaultByteOrder)
        buffer.putLong(value)
        val result = buffer.array()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onWriteOperation("Long", result.size, duration)
        return result
    }
    
    override fun writeInt(value: Int): ByteArray {
        val startTime = System.nanoTime()
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES).order(defaultByteOrder)
        buffer.putInt(value)
        val result = buffer.array()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onWriteOperation("Int", result.size, duration)
        return result
    }
    
    override fun writeShort(value: Short): ByteArray {
        val startTime = System.nanoTime()
        val buffer = ByteBuffer.allocate(Short.SIZE_BYTES).order(defaultByteOrder)
        buffer.putShort(value)
        val result = buffer.array()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onWriteOperation("Short", result.size, duration)
        return result
    }
    
    override fun writeDouble(value: Double): ByteArray {
        val startTime = System.nanoTime()
        val result = writeLong(value.toBits())
        val duration = System.nanoTime() - startTime
        attentionDelegate.onWriteOperation("Double", result.size, duration)
        return result
    }
    
    override fun writeFloat(value: Float): ByteArray {
        val startTime = System.nanoTime()
        val result = writeInt(value.toBits())
        val duration = System.nanoTime() - startTime
        attentionDelegate.onWriteOperation("Float", result.size, duration)
        return result
    }
    
    override fun writeUShort(value: Int): ByteArray {
        val startTime = System.nanoTime()
        val buffer = ByteBuffer.allocate(Short.SIZE_BYTES).order(defaultByteOrder)
        buffer.putShort(value.toShort())
        val result = buffer.array()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onWriteOperation("UShort", result.size, duration)
        return result
    }
    
    override fun writeUInt(value: Long): ByteArray {
        val startTime = System.nanoTime()
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES).order(defaultByteOrder)
        buffer.putInt(value.toInt())
        val result = buffer.array()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onWriteOperation("UInt", result.size, duration)
        return result
    }
    
    override fun writeULong(value: Long): ByteArray {
        val startTime = System.nanoTime()
        // This is a simplification and may not correctly handle all ULong values
        val buffer = ByteBuffer.allocate(Long.SIZE_BYTES).order(defaultByteOrder)
        buffer.putLong(value)
        val result = buffer.array()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onWriteOperation("ULong", result.size, duration)
        return result
    }
    
    // === BUFFER OPERATIONS ===
    override fun readLongFromBuffer(buffer: PlatformByteBuffer): Long {
        val startTime = System.nanoTime()
        val result = buffer.getLong()
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("Long", "read", duration)
        return result
    }
    
    override fun readIntFromBuffer(buffer: PlatformByteBuffer): Int {
        val startTime = System.nanoTime()
        // Read 4 bytes and construct int
        var result = 0
        for (i in 0 until 4) {
            result = (result shl 8) or (buffer.get().toInt() and 0xFF)
        }
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("Int", "read", duration)
        return result
    }
    
    override fun readShortFromBuffer(buffer: PlatformByteBuffer): Short {
        val startTime = System.nanoTime()
        // Read 2 bytes and construct short
        var result = 0
        for (i in 0 until 2) {
            result = (result shl 8) or (buffer.get().toInt() and 0xFF)
        }
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("Short", "read", duration)
        return result.toShort()
    }
    
    override fun readDoubleFromBuffer(buffer: PlatformByteBuffer): Double {
        val startTime = System.nanoTime()
        val result = Double.fromBits(readLongFromBuffer(buffer))
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("Double", "read", duration)
        return result
    }
    
    override fun readFloatFromBuffer(buffer: PlatformByteBuffer): Float {
        val startTime = System.nanoTime()
        val result = Float.fromBits(readIntFromBuffer(buffer))
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("Float", "read", duration)
        return result
    }
    
    override fun readUShortFromBuffer(buffer: PlatformByteBuffer): Int {
        val startTime = System.nanoTime()
        val result = readShortFromBuffer(buffer).toInt() and 0xFFFF
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("UShort", "read", duration)
        return result
    }
    
    override fun readUIntFromBuffer(buffer: PlatformByteBuffer): Long {
        val startTime = System.nanoTime()
        val result = readIntFromBuffer(buffer).toLong() and 0xFFFFFFFFL
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("UInt", "read", duration)
        return result
    }
    
    override fun readULongFromBuffer(buffer: PlatformByteBuffer): Long {
        val startTime = System.nanoTime()
        val result = readLongFromBuffer(buffer)
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("ULong", "read", duration)
        return result
    }
    
    // === BUFFER WRITE OPERATIONS ===
    override fun writeLongToBuffer(buffer: PlatformByteBuffer, value: Long): PlatformByteBuffer {
        val startTime = System.nanoTime()
        // Write 8 bytes (big-endian)
        for (i in 7 downTo 0) {
            buffer.put(((value shr (i * 8)) and 0xFF).toByte())
        }
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("Long", "write", duration)
        return buffer
    }
    
    override fun writeIntToBuffer(buffer: PlatformByteBuffer, value: Int): PlatformByteBuffer {
        val startTime = System.nanoTime()
        // Write 4 bytes (big-endian)
        for (i in 3 downTo 0) {
            buffer.put(((value shr (i * 8)) and 0xFF).toByte())
        }
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("Int", "write", duration)
        return buffer
    }
    
    override fun writeShortToBuffer(buffer: PlatformByteBuffer, value: Short): PlatformByteBuffer {
        val startTime = System.nanoTime()
        // Write 2 bytes (big-endian)
        for (i in 1 downTo 0) {
            buffer.put(((value.toInt() shr (i * 8)) and 0xFF).toByte())
        }
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("Short", "write", duration)
        return buffer
    }
    
    override fun writeDoubleToBuffer(buffer: PlatformByteBuffer, value: Double): PlatformByteBuffer {
        val startTime = System.nanoTime()
        writeLongToBuffer(buffer, value.toBits())
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("Double", "write", duration)
        return buffer
    }
    
    override fun writeFloatToBuffer(buffer: PlatformByteBuffer, value: Float): PlatformByteBuffer {
        val startTime = System.nanoTime()
        writeIntToBuffer(buffer, value.toBits())
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("Float", "write", duration)
        return buffer
    }
    
    override fun writeUShortToBuffer(buffer: PlatformByteBuffer, value: Int): PlatformByteBuffer {
        val startTime = System.nanoTime()
        writeShortToBuffer(buffer, value.toShort())
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("UShort", "write", duration)
        return buffer
    }
    
    override fun writeUIntToBuffer(buffer: PlatformByteBuffer, value: Long): PlatformByteBuffer {
        val startTime = System.nanoTime()
        writeIntToBuffer(buffer, value.toInt())
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("UInt", "write", duration)
        return buffer
    }
    
    override fun writeULongToBuffer(buffer: PlatformByteBuffer, value: Long): PlatformByteBuffer {
        val startTime = System.nanoTime()
        writeLongToBuffer(buffer, value)
        val duration = System.nanoTime() - startTime
        attentionDelegate.onBufferOperation("ULong", "write", duration)
        return buffer
    }
}

class JvmPlatformCodecAttentionDelegate : PlatformCodecAttentionDelegate {
    private val performanceThreshold = 1000L // 1 microsecond
    
    override fun onBufferAllocated(capacity: Int, duration: Long) {
        if (duration > performanceThreshold) {
            println("⚠️  Slow buffer allocation: ${duration}ns for capacity $capacity")
        }
    }
    
    override fun onBufferWrapped(arraySize: Int, offset: Int, length: Int, duration: Long) {
        if (duration > performanceThreshold) {
            println("⚠️  Slow buffer wrapping: ${duration}ns for size $arraySize")
        }
    }
    
    override fun onChannelCreated(type: String, config: Map<String, Any>) {
        println("📡 Channel created: $type with config $config")
    }
    
    override fun onIoOperation(operation: String, bytes: Int, duration: Long) {
        if (duration > performanceThreshold) {
            println("⚠️  Slow I/O operation: $operation took ${duration}ns for $bytes bytes")
        }
    }
    
    override fun onReadOperation(type: String, bytesRead: Int, duration: Long) {
        if (duration > performanceThreshold) {
            println("⚠️  Slow read operation: $type took ${duration}ns for $bytesRead bytes")
        }
    }
    
    override fun onWriteOperation(type: String, bytesWritten: Int, duration: Long) {
        if (duration > performanceThreshold) {
            println("⚠️  Slow write operation: $type took ${duration}ns for $bytesWritten bytes")
        }
    }
    
    override fun onBufferOperation(type: String, operation: String, duration: Long) {
        if (duration > performanceThreshold) {
            println("⚠️  Slow buffer operation: $operation $type took ${duration}ns")
        }
    }
    
    override fun onByteOrderConversion(from: String, to: String, duration: Long) {
        if (duration > performanceThreshold) {
            println("⚠️  Slow byte order conversion: $from to $to took ${duration}ns")
        }
    }
} 