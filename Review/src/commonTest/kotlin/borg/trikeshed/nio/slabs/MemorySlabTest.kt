package borg.trikeshed.nio.slabs

import borg.trikeshed.logging.getTestMemorySlabManagerService // Re-use the test helper
import kotlin.test.*

class MemorySlabTest {
    private lateinit var slabManager: MemorySlabManagerService
    private val defaultTestSlabSize = 128 // Sufficient for testing various types

    @BeforeTest
    fun setup() {
        // Initialize slabManager before each test using the expect function
        slabManager = getTestMemorySlabManagerService(defaultSlabSize = defaultTestSlabSize)
    }

    @Test
    fun testPutAndGetPrimitives() {
        val slab = slabManager.acquireSlab(defaultTestSlabSize)
        assertEquals(0, slab.position, "Initial position should be 0")
        assertEquals(defaultTestSlabSize, slab.remaining, "Initial remaining should be capacity")

        slab.putByte(1.toByte())
        slab.putShort(2.toShort())
        slab.putInt(3)
        slab.putLong(4L)
        slab.putFloat(5.0f)
        slab.putDouble(6.0)

        val expectedPosition = 1 + 2 + 4 + 8 + 4 + 8 // byte + short + int + long + float + double
        assertEquals(expectedPosition, slab.position, "Position after puts should be sum of primitive sizes")

        val buffer = slab.getRawByteBuffer()
        assertEquals(expectedPosition, buffer.limit(), "Raw buffer limit should be current slab position")
        assertEquals(0, buffer.position(), "Raw buffer initial position should be 0")

        assertEquals(1.toByte(), buffer.get(), "Byte value mismatch")
        assertEquals(2.toShort(), buffer.getShort(), "Short value mismatch")
        assertEquals(3, buffer.getInt(), "Int value mismatch")
        assertEquals(4L, buffer.getLong(), "Long value mismatch")
        assertEquals(5.0f, buffer.getFloat(), "Float value mismatch")
        assertEquals(6.0, buffer.getDouble(), "Double value mismatch")
        assertFalse(buffer.hasRemaining(), "Buffer should have no remaining after reading all primitives")
    }

    @Test
    fun testPutBytesAndRead() {
        val slab = slabManager.acquireSlab(defaultTestSlabSize)
        val data = byteArrayOf(10, 20, 30, 40, 50)
        slab.putBytes(data)
        assertEquals(data.size, slab.position, "Position after putBytes should be data size")

        val buffer = slab.getRawByteBuffer()
        assertEquals(data.size, buffer.remaining(), "Raw buffer remaining should be data size")
        val readData = ByteArray(data.size)
        buffer.get(readData)
        assertTrue(data.contentEquals(readData), "Read data should match written data")
    }

    @Test
    fun testPutByteBufferAndRead() {
        val slab = slabManager.acquireSlab(defaultTestSlabSize)
        val sourceData = byteArrayOf(11, 22, 33, 44, 55)
        // Use the ByteBufferFactory from the actual nio package
        val sourceBuffer = borg.trikeshed.nio.ByteBufferFactory.wrap(sourceData)

        val initialSourceRemaining = sourceBuffer.remaining()
        slab.putBytes(sourceBuffer, sourceData.size) // Test putBytes(ByteBuffer, length)
        assertEquals(sourceData.size, slab.position, "Position after putBytes(ByteBuffer) should be data size")
        assertEquals(initialSourceRemaining - sourceData.size, sourceBuffer.remaining(), "Source buffer should have advanced by length")


        val rawSlabBuffer = slab.getRawByteBuffer()
        val readData = ByteArray(sourceData.size)
        rawSlabBuffer.get(readData)
        assertTrue(sourceData.contentEquals(readData), "Read data from ByteBuffer source should match")
    }

    @Test
    fun testSlabOverflow() {
        val smallCapacity = 10
        val slab = slabManager.acquireSlab(smallCapacity)
        slab.putBytes(ByteArray(smallCapacity)) // Fill the slab exactly
        assertTrue(slab.isFull(), "Slab should be full after writing capacity bytes")
        assertEquals(0, slab.remaining, "Remaining should be 0 when full")

        assertFailsWith<IllegalArgumentException>("Should throw when trying to write past capacity") {
            slab.putByte(1)
        }
    }

    @Test
    fun testPutBytesOverflowPartial() {
        val capacity = 10
        val slab = slabManager.acquireSlab(capacity)
        slab.putBytes(ByteArray(5)) // Write 5 bytes
        assertEquals(5, slab.remaining, "Remaining should be capacity - 5")
        assertFailsWith<IllegalArgumentException>("Should throw when trying to write more bytes than remaining") {
            slab.putBytes(ByteArray(6)) // Try to write 6 bytes when only 5 remaining
        }
    }

    @Test
    fun testClearSlab() {
        val slab = slabManager.acquireSlab(defaultTestSlabSize)
        slab.putInt(12345)
        assertTrue(slab.position > 0, "Position should advance after putInt")

        slab.clear()
        assertEquals(0, slab.position, "Position should be 0 after clear")
        assertEquals(defaultTestSlabSize, slab.remaining, "Remaining should be full capacity after clear")
        assertFalse(slab.isFull(), "Slab should not be full after clear if capacity > 0")

        // Verify it can be written to again
        slab.putInt(67890)
        val buffer = slab.getRawByteBuffer()
        assertEquals(4, buffer.limit(), "Raw buffer limit should be size of int")
        assertEquals(67890, buffer.getInt(), "Reading after clear and put should yield new value")
    }

    @Test
    fun testGetRawByteBufferProperties() {
        val slab = slabManager.acquireSlab(defaultTestSlabSize)
        slab.putInt(1)
        slab.putInt(2)
        val expectedSlabPosition = 8 // 2 ints

        assertEquals(expectedSlabPosition, slab.position, "Slab position after puts")

        val rawBuffer = slab.getRawByteBuffer()
        assertEquals(0, rawBuffer.position(), "Raw buffer position should be 0")
        assertEquals(expectedSlabPosition, rawBuffer.limit(), "Raw buffer limit should be slab's current position")
        assertEquals(expectedSlabPosition, rawBuffer.remaining(), "Raw buffer remaining should be slab's current position")

        // Read some data from rawBuffer and check original slab's properties are unaffected
        val val1 = rawBuffer.getInt()
        assertEquals(1, val1, "First value from raw buffer")
        assertEquals(expectedSlabPosition, slab.position, "Original slab position should be unchanged after reading from rawBuffer")
        assertEquals(defaultTestSlabSize - expectedSlabPosition, slab.remaining, "Original slab remaining should be unchanged")

        val val2 = rawBuffer.getInt()
        assertEquals(2, val2, "Second value from raw buffer")
        assertFalse(rawBuffer.hasRemaining(), "Raw buffer should have no data after reading both ints")
    }

    @Test
    fun testPutBytesWithOffsetAndLength() {
        val slab = slabManager.acquireSlab(defaultTestSlabSize)
        val fullData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val offset = 2
        val length = 5 // Data to write: 3, 4, 5, 6, 7
        val expectedData = fullData.copyOfRange(offset, offset + length)

        slab.putBytes(fullData, offset, length)
        assertEquals(length, slab.position, "Position should be equal to length written")

        val buffer = slab.getRawByteBuffer()
        assertEquals(length, buffer.remaining(), "Raw buffer should have 'length' bytes")
        val readData = ByteArray(length)
        buffer.get(readData)
        assertTrue(expectedData.contentEquals(readData), "Read data segment should match written segment")
    }
}
