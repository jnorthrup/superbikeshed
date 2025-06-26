package borg.trikeshed.nio.spi

import borg.trikeshed.nio.PlatformByteBuffer

/**
 * Service Provider Interface for Platform Codec implementations
 * This replaces the boilerplate PlatformCodec class with a modern SPI-based approach
 */
interface PlatformCodecProvider {
    fun getAttentionDelegate(): AttentionDelegate
    
    // === READ OPERATIONS ===
    fun readLong(bytes: ByteArray): Long
    fun readInt(bytes: ByteArray): Int
    fun readShort(bytes: ByteArray): Short
    fun readDouble(bytes: ByteArray): Double
    fun readFloat(bytes: ByteArray): Float
    fun readUShort(bytes: ByteArray): Int
    fun readUInt(bytes: ByteArray): Long
    fun readULong(bytes: ByteArray): Long
    
    // === WRITE OPERATIONS ===
    fun writeLong(value: Long): ByteArray
    fun writeInt(value: Int): ByteArray
    fun writeShort(value: Short): ByteArray
    fun writeDouble(value: Double): ByteArray
    fun writeFloat(value: Float): ByteArray
    fun writeUShort(value: Int): ByteArray
    fun writeUInt(value: Long): ByteArray
    fun writeULong(value: Long): ByteArray
    
    // === BUFFER OPERATIONS ===
    fun readLongFromBuffer(buffer: PlatformByteBuffer): Long
    fun readIntFromBuffer(buffer: PlatformByteBuffer): Int
    fun readShortFromBuffer(buffer: PlatformByteBuffer): Short
    fun readDoubleFromBuffer(buffer: PlatformByteBuffer): Double
    fun readFloatFromBuffer(buffer: PlatformByteBuffer): Float
    fun readUShortFromBuffer(buffer: PlatformByteBuffer): Int
    fun readUIntFromBuffer(buffer: PlatformByteBuffer): Long
    fun readULongFromBuffer(buffer: PlatformByteBuffer): Long
    
    fun writeLongToBuffer(buffer: PlatformByteBuffer, value: Long): PlatformByteBuffer
    fun writeIntToBuffer(buffer: PlatformByteBuffer, value: Int): PlatformByteBuffer
    fun writeShortToBuffer(buffer: PlatformByteBuffer, value: Short): PlatformByteBuffer
    fun writeDoubleToBuffer(buffer: PlatformByteBuffer, value: Double): PlatformByteBuffer
    fun writeFloatToBuffer(buffer: PlatformByteBuffer, value: Float): PlatformByteBuffer
    fun writeUShortToBuffer(buffer: PlatformByteBuffer, value: Int): PlatformByteBuffer
    fun writeUIntToBuffer(buffer: PlatformByteBuffer, value: Long): PlatformByteBuffer
    fun writeULongToBuffer(buffer: PlatformByteBuffer, value: Long): PlatformByteBuffer
}

/**
 * Attention delegate for monitoring PlatformCodec operations
 */
interface PlatformCodecAttentionDelegate : AttentionDelegate {
    fun onReadOperation(type: String, bytesRead: Int, duration: Long)
    fun onWriteOperation(type: String, bytesWritten: Int, duration: Long)
    fun onBufferOperation(type: String, operation: String, duration: Long)
    fun onByteOrderConversion(from: String, to: String, duration: Long)
} 