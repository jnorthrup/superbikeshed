package borg.trikeshed.nio.spi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlatformCodecSpiTest {
    
    @Test
    fun testPlatformCodecProvider() {
        val provider = JvmPlatformCodecProvider()
        
        // Test basic read/write operations
        val testLong = 1234567890123456789L
        val longBytes = provider.writeLong(testLong)
        val readLong = provider.readLong(longBytes)
        assertEquals(testLong, readLong)
        
        val testInt = 1234567890
        val intBytes = provider.writeInt(testInt)
        val readInt = provider.readInt(intBytes)
        assertEquals(testInt, readInt)
        
        val testShort = 12345.toShort()
        val shortBytes = provider.writeShort(testShort)
        val readShort = provider.readShort(shortBytes)
        assertEquals(testShort, readShort)
        
        val testDouble = 123.456
        val doubleBytes = provider.writeDouble(testDouble)
        val readDouble = provider.readDouble(doubleBytes)
        assertEquals(testDouble, readDouble, 0.001)
        
        val testFloat = 123.456f
        val floatBytes = provider.writeFloat(testFloat)
        val readFloat = provider.readFloat(floatBytes)
        assertEquals(testFloat, readFloat, 0.001f)
    }
    
    @Test
    fun testBufferOperations() {
        val provider = JvmPlatformCodecProvider()
        val buffer = PlatformByteBuffer.allocate(1024)
        
        // Test buffer write/read operations
        val testLong = 9876543210987654321L
        provider.writeLongToBuffer(buffer, testLong)
        buffer.flip()
        val readLong = provider.readLongFromBuffer(buffer)
        assertEquals(testLong, readLong)
        
        // Test unsigned operations
        val testUShort = 65535 // Max UShort
        val buffer2 = PlatformByteBuffer.allocate(1024)
        provider.writeUShortToBuffer(buffer2, testUShort)
        buffer2.flip()
        val readUShort = provider.readUShortFromBuffer(buffer2)
        assertEquals(testUShort, readUShort)
    }
    
    @Test
    fun testAttentionDelegate() {
        val provider = JvmPlatformCodecProvider()
        val delegate = provider.getAttentionDelegate()
        
        assertNotNull(delegate)
        // The delegate should be monitoring operations without throwing exceptions
        val testBytes = provider.writeInt(42)
        val result = provider.readInt(testBytes)
        assertEquals(42, result)
    }
} 