package borg.trikeshed.wireproto

import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.*
import kotlin.test.*

class TrikeShedWireProtoTest {
    
    @Test
    fun testIoMementoSerialization() {
        // Create test IoMemento
        val original = IOMemento.create(
            name = "test_column",
            type = "String", 
            width = 255,
            nullable = true
        ).apply {
            encoding = "utf-8"
            format = "varchar"
        }
        
        // Serialize to wire format
        val wireBytes = original.toWireBytes()
        assertTrue(wireBytes.isNotEmpty())
        
        // Deserialize back
        val deserialized = wireBytes.toIoMemento()
        
        // Verify all fields match
        assertEquals(original.name, deserialized.name)
        assertEquals(original.type, deserialized.type)
        assertEquals(original.width, deserialized.width)
        assertEquals(original.nullable, deserialized.nullable)
        assertEquals(original.encoding, deserialized.encoding)
        assertEquals(original.format, deserialized.format)
    }
    
    @Test
    fun testIoMementoWithNulls() {
        // Create IoMemento with some null fields
        val original = IOMemento.create(
            name = "nullable_test",
            type = null,
            width = null,
            nullable = null
        )
        
        val wireBytes = original.toWireBytes()
        val deserialized = wireBytes.toIoMemento()
        
        assertEquals(original.name, deserialized.name)
        assertNull(deserialized.type)
        assertNull(deserialized.width)
        assertNull(deserialized.nullable)
    }
    
    @Test
    fun testStringSeriesSerialization() {
        // Create test Indexed<String>
        val strings = listOf("hello", "world", "test", "data")
        val series = strings.size j { i -> strings[i] }
        
        // Serialize to wire format
        val wireBytes = series.toWireBytes()
        assertTrue(wireBytes.isNotEmpty())
        
        // Deserialize back
        val deserialized = wireBytes.toSeries<String>()
        
        // Verify series content
        assertEquals(series.a, deserialized.a)
        for (i in 0 until series.a) {
            assertEquals(series.b(i), deserialized.b(i))
        }
    }
    
    @Test
    fun testIntSeriesSerialization() {
        // Create test Indexed<Int>
        val numbers = listOf(1, 2, 3, 42, 100, -5)
        val series = numbers.size j { i -> numbers[i] }
        
        val wireBytes = series.toWireBytes()
        val deserialized = wireBytes.toSeries<Int>()
        
        assertEquals(series.a, deserialized.a)
        for (i in 0 until series.a) {
            assertEquals(series.b(i), deserialized.b(i))
        }
    }
    
    @Test
    fun testDoubleSeriesSerialization() {
        // Create test Indexed<Double>
        val doubles = listOf(3.14159, 2.71828, 1.41421, 0.0, -1.5)
        val series = doubles.size j { i -> doubles[i] }
        
        val wireBytes = series.toWireBytes()
        val deserialized = wireBytes.toSeries<Double>()
        
        assertEquals(series.size, deserialized.size)
        for (i in 0 until series.size) {
            assertEquals(series[i], deserialized[i], 0.00001)
        }
    }
    
    @Test
    fun testBooleanSeriesSerialization() {
        // Create test Indexed<Boolean>
        val booleans = listOf(true, false, true, true, false)
        val series = booleans.size j { i -> booleans[i] }
        
        val wireBytes = series.toWireBytes()
        val deserialized = wireBytes.toSeries<Boolean>()
        
        assertEquals(series.a, deserialized.a)
        for (i in 0 until series.a) {
            assertEquals(series.b(i), deserialized.b(i))
        }
    }
    
    @Test
    fun testEmptySeries() {
        // Test empty series
        val series = 0 j { _: Int -> "" }
        
        val wireBytes = series.toWireBytes()
        val deserialized = wireBytes.toSeries<String>()
        
        assertEquals(0, deserialized.size)
    }
    
    @Test
    fun testLargeSeries() {
        // Test larger series for performance
        val size = 1000
        val series = size j { i -> i * 2 }
        
        val wireBytes = series.toWireBytes()
        val deserialized = wireBytes.toSeries<Int>()
        
        assertEquals(series.size, deserialized.size)
        
        // Spot check some values
        assertEquals(0, deserialized[0])
        assertEquals(500, deserialized[250])
        assertEquals(1998, deserialized[999])
    }
    
    @Test
    fun testWireMessageStructure() {
        val memento = IOMemento.create("test", "Int", 4, false)
        val wireBytes = memento.toWireBytes()
        
        // The wire format should include:
        // - Version byte
        // - Message type
        // - Payload length
        // - Payload data
        // - CRC32 checksum
        
        assertTrue(wireBytes.size > 10) // Should have meaningful size
        assertEquals(1u, wireBytes[0]) // Version should be 1
    }
    
    @Test
    fun testChecksumValidation() {
        val memento = IOMemento.create("checksum_test", "String", 100, true)
        val wireBytes = memento.toWireBytes()
        
        // Corrupt a byte in the payload
        val corrupted = wireBytes.copyOf()
        if (corrupted.size > 10) {
            corrupted[10] = (corrupted[10] + 1u).toUByte()
        }
        
        // Should fail checksum validation
        assertFailsWith<IllegalArgumentException> {
            corrupted.toIoMemento()
        }
    }
    
    @Test
    fun testRoundTripConsistency() {
        // Test multiple round trips to ensure consistency
        val original = IOMemento.create("roundtrip", "Double", 8, false).apply {
            encoding = "ieee754"
            format = "binary"
        }
        
        var current = original
        repeat(5) {
            val wireBytes = current.toWireBytes()
            current = wireBytes.toIoMemento()
        }
        
        // Should still match original after multiple round trips
        assertEquals(original.name, current.name)
        assertEquals(original.type, current.type)
        assertEquals(original.width, current.width)
        assertEquals(original.nullable, current.nullable)
        assertEquals(original.encoding, current.encoding)
        assertEquals(original.format, current.format)
    }
}

class WirePayloadBuilderTest {
    
    @Test
    fun testVarIntEncoding() {
        val builder = WirePayloadBuilder()
        
        // Test various varint values
        builder.writeVarInt(0)
        builder.writeVarInt(127)
        builder.writeVarInt(128)
        builder.writeVarInt(16383)
        builder.writeVarInt(16384)
        
        val data = builder.build()
        val reader = WireReader(data)
        
        assertEquals(0, reader.readVarInt())
        assertEquals(127, reader.readVarInt())
        assertEquals(128, reader.readVarInt())
        assertEquals(16383, reader.readVarInt())
        assertEquals(16384, reader.readVarInt())
    }
    
    @Test
    fun testStringEncoding() {
        val builder = WirePayloadBuilder()
        
        val testStrings = listOf("", "hello", "🌟", "test with spaces", "unicode: αβγ")
        testStrings.forEach { builder.writeString(it) }
        
        val data = builder.build()
        val reader = WireReader(data)
        
        testStrings.forEach { expected ->
            assertEquals(expected, reader.readString())
        }
    }
    
    @Test
    fun testOptionalValues() {
        val builder = WirePayloadBuilder()
        
        builder.writeOptionalString("present")
        builder.writeOptionalString(null)
        builder.writeOptionalInt(42)
        builder.writeOptionalInt(null)
        builder.writeOptionalBoolean(true)
        builder.writeOptionalBoolean(null)
        
        val data = builder.build()
        val reader = WireReader(data)
        
        assertEquals("present", reader.readOptionalString())
        assertNull(reader.readOptionalString())
        assertEquals(42, reader.readOptionalInt())
        assertNull(reader.readOptionalInt())
        assertEquals(true, reader.readOptionalBoolean())
        assertNull(reader.readOptionalBoolean())
    }
}

class WireMessageTest {
    
    @Test
    fun testCrc32Calculation() {
        // Test CRC32 with known values
        val testData = "hello world".encodeToByteArray().toUByteArray()
        val message = TrikeShedWireMessage.create("test", testData)
        
        // CRC32 should be deterministic
        val message2 = TrikeShedWireMessage.create("test", testData)
        assertEquals(message.checksum.crc32, message2.checksum.crc32)
        
        // Different data should have different checksums
        val differentData = "different data".encodeToByteArray().toUByteArray()
        val message3 = TrikeShedWireMessage.create("test", differentData)
        assertNotEquals(message.checksum.crc32, message3.checksum.crc32)
    }
    
    @Test
    fun testMessageVersioning() {
        val testData = "version test".encodeToByteArray().toUByteArray()
        val message = TrikeShedWireMessage.create("test", testData)
        
        assertEquals(TrikeShedWireMessage.CURRENT_VERSION, message.version)
        assertEquals(1u, message.version.version)
    }
}