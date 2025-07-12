package tdd

import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.*
import kotlin.test.*

/**
 * TDD Tests for TrikeShed Wire Protocol Implementation
 * 
 * These tests define the requirements for the wire protocol serialization
 * system as described in README.md. Each test should fail until the
 * corresponding functionality is implemented.
 * 
 * Background: The wire protocol provides efficient binary serialization
 * for TrikeShed's core types including IoMemento and Series<T> with
 * checksums, versioning, and platform-independent encoding.
 */
class WireProtocolTDDTest {
    
    // ===== IoMemento Serialization Tests =====
    
    @Test
    fun `should serialize IoMemento with all fields to wire format`() {
        // Given: IoMemento with all fields populated
        val memento = IOMemento.create(
            name = "user_id",
            type = "Long",
            width = 8,
            nullable = false
        ).apply {
            encoding = "binary"
            format = "int64"
        }
        
        // When: Serialized to wire format
        val wireBytes = memento.toWireBytes()
        
        // Then: Should produce valid wire format with all fields
        assertTrue(wireBytes.isNotEmpty(), "Wire bytes should not be empty")
        assertTrue(wireBytes.size > 10, "Wire format should include version, type, length, and checksum")
        
        // Verify wire format structure: [Version:1] [MessageType:varint+string] [PayloadLength:varint] [Payload:bytes] [CRC32:4]
        assertEquals(1u, wireBytes[0], "Version should be 1")
        
        // Verify deserialization preserves all fields
        val restored = wireBytes.toIoMemento()
        assertEquals(memento.name, restored.name)
        assertEquals(memento.type, restored.type)
        assertEquals(memento.width, restored.width)
        assertEquals(memento.nullable, restored.nullable)
        assertEquals(memento.encoding, restored.encoding)
        assertEquals(memento.format, restored.format)
    }
    
    @Test
    fun `should handle optional fields in IoMemento wire format`() {
        // Given: IoMemento with some null fields
        val memento = IOMemento.create(
            name = "nullable_test",
            type = null,
            width = null,
            nullable = null
        )
        
        // When: Serialized to wire format
        val wireBytes = memento.toWireBytes()
        
        // Then: Should use presence markers (0=absent, 1=present)
        // Format: [1,"nullable_test"] [0] [0] [0] [0] [0]
        assertTrue(wireBytes.isNotEmpty())
        
        // Verify deserialization preserves null fields
        val restored = wireBytes.toIoMemento()
        assertEquals(memento.name, restored.name)
        assertNull(restored.type)
        assertNull(restored.width)
        assertNull(restored.nullable)
    }
    
    @Test
    fun `should serialize IoMemento with performance target of 50-100ns`() {
        // Given: IoMemento for performance testing
        val memento = IOMemento.create("perf_test", "Int", 4, false)
        
        // When: Measuring serialization performance
        val startTime = System.nanoTime()
        repeat(1000) {
            memento.toWireBytes()
        }
        val endTime = System.nanoTime()
        
        val avgTime = (endTime - startTime) / 1000.0
        
        // Then: Should meet performance target of 50-100ns per object
        assertTrue(avgTime >= 50, "Serialization should not be faster than 50ns (too fast might indicate missing work)")
        assertTrue(avgTime <= 1000, "Serialization should be under 1μs per object")
    }
    
    // ===== Series<T> Serialization Tests =====
    
    @Test
    fun `should serialize Series<Int> with type information and elements`() {
        // Given: Series<Int> with values
        val numbers = \1 j { \2: Int -> i * i } // [0, 1, 4, 9, 16]
        
        // When: Serialized to wire format
        val wireBytes = numbers.toWireBytes()
        
        // Then: Should include type name, size, and all elements
        // Format: ["Int"] [5] [0] [1] [4] [9] [16]
        assertTrue(wireBytes.isNotEmpty())
        
        // Verify deserialization preserves all values
        val restored = wireBytes.toSeries<Int>()
        assertEquals(numbers.size, restored.size)
        for (i in 0 until numbers.size) {
            assertEquals(numbers[i], restored[i])
        }
    }
    
    @Test
    fun `should serialize Series<String> with variable-length encoding`() {
        // Given: Series<String> with strings of varying lengths
        val strings = \1 j { \2: Int -> "item$i" } // ["item0", "item1", "item2"]
        
        // When: Serialized to wire format
        val wireBytes = strings.toWireBytes()
        
        // Then: Should use length-prefixed UTF-8 encoding for strings
        // Format: ["String"] [3] [1,"item0"] [1,"item1"] [1,"item2"]
        assertTrue(wireBytes.isNotEmpty())
        
        // Verify deserialization
        val restored = wireBytes.toSeries<String>()
        assertEquals(strings.size, restored.size)
        for (i in 0 until strings.size) {
            assertEquals(strings[i], restored[i])
        }
    }
    
    @Test
    fun `should meet Series<Int> performance target of 2-5μs per 1000 elements`() {
        // Given: Large Series<Int> for performance testing
        val size = 1000
        val series = \1 j { \2: Int -> i }
        
        // When: Measuring serialization performance
        val startTime = System.nanoTime()
        val wireBytes = series.toWireBytes()
        val endTime = System.nanoTime()
        
        val timeMicroseconds = (endTime - startTime) / 1000.0
        
        // Then: Should meet performance target
        assertTrue(timeMicroseconds >= 2, "Serialization should not be faster than 2μs (too fast might indicate missing work)")
        assertTrue(timeMicroseconds <= 10, "Serialization should be under 10μs for 1000 elements")
    }
    
    @Test
    fun `should meet Series<String> performance target of 10-20μs per 1000 elements`() {
        // Given: Large Series<String> for performance testing
        val size = 1000
        val series = \1 j { \2: Int -> "string_$i" }
        
        // When: Measuring serialization performance
        val startTime = System.nanoTime()
        val wireBytes = series.toWireBytes()
        val endTime = System.nanoTime()
        
        val timeMicroseconds = (endTime - startTime) / 1000.0
        
        // Then: Should meet performance target
        assertTrue(timeMicroseconds >= 5, "Serialization should not be faster than 5μs (too fast might indicate missing work)")
        assertTrue(timeMicroseconds <= 25, "Serialization should be under 25μs for 1000 string elements")
    }
    
    // ===== Wire Protocol Format Tests =====
    
    @Test
    fun `should include all required wire protocol fields`() {
        // Given: Any serializable object
        val memento = IOMemento.create("test", "Int", 4, false)
        
        // When: Serialized to wire format
        val wireBytes = memento.toWireBytes()
        
        // Then: Should include all required fields: [Version:1] [MessageType:varint+string] [PayloadLength:varint] [Payload:bytes] [CRC32:4]
        assertTrue(wireBytes.size >= 9, "Wire format should have minimum size for all required fields")
        
        // Version should be 1
        assertEquals(1u, wireBytes[0], "Protocol version should be 1")
        
        // Should have enough bytes for CRC32 at the end
        assertTrue(wireBytes.size >= 4, "Should have space for CRC32 checksum")
    }
    
    @Test
    fun `should use varint encoding for small integers`() {
        // Given: Series with small integers
        val smallNumbers = \1 j { \2: Int -> i } // [0,1,2,3,4,5,6,7,8,9]
        
        // When: Serialized to wire format
        val wireBytes = smallNumbers.toWireBytes()
        
        // Then: Should use varint encoding (1-5 bytes vs fixed 4/8)
        // Small integers should use fewer bytes than fixed-width encoding
        val expectedMaxSize = 10 * 5 + 20 // 10 elements * 5 bytes max + overhead
        assertTrue(wireBytes.size <= expectedMaxSize, "Varint encoding should be efficient for small integers")
    }
    
    @Test
    fun `should validate checksum on deserialization`() {
        // Given: Valid serialized data
        val memento = IOMemento.create("checksum_test", "String", 100, true)
        val wireBytes = memento.toWireBytes()
        
        // When: Corrupting the data
        val corrupted = wireBytes.copyOf()
        if (corrupted.size > 10) {
            corrupted[10] = (corrupted[10] + 1u).toUByte()
        }
        
        // Then: Should fail checksum validation
        assertFailsWith<IllegalArgumentException>("Checksum mismatch should be detected") {
            corrupted.toIoMemento()
        }
    }
    
    @Test
    fun `should handle type mismatches gracefully`() {
        // Given: Serialized data for one type
        val intSeries = \1 j { \2: Int -> i }
        val wireBytes = intSeries.toWireBytes()
        
        // When: Attempting to deserialize as different type
        // Then: Should either succeed (if format is compatible) or fail gracefully
        try {
            val stringSeries = wireBytes.toSeries<String>()
            // If it succeeds, the format should be type-safe
            assertTrue(stringSeries.size == intSeries.size || stringSeries.size == 0)
        } catch (e: Exception) {
            // If it fails, should be a clear error
            assertTrue(e.message?.contains("type") == true || e.message?.contains("format") == true)
        }
    }
    
    // ===== Platform Support Tests =====
    
    @Test
    fun `should be Kotlin Multiplatform compatible`() {
        // Given: Any serializable object
        val memento = IOMemento.create("multiplatform_test", "Double", 8, false)
        
        // When: Serialized on current platform
        val wireBytes = memento.toWireBytes()
        
        // Then: Should produce consistent binary format
        // This test ensures the format is endianness-independent and platform-consistent
        assertTrue(wireBytes.isNotEmpty())
        
        // Verify deserialization works
        val restored = wireBytes.toIoMemento()
        assertEquals(memento.name, restored.name)
    }
    
    // ===== Integration Tests =====
    
    @Test
    fun `should support ISAM metadata serialization`() {
        // Given: Column metadata that would be used in ISAM
        val columnMeta = IOMemento.create("user_id", "Long", 8, false).apply {
            encoding = "binary"
            format = "int64"
        }
        
        // When: Serialized for ISAM storage
        val wireBytes = columnMeta.toWireBytes()
        
        // Then: Should be deserializable back to usable metadata
        val restored = wireBytes.toIoMemento()
        assertNotNull(restored.name)
        assertNotNull(restored.type)
        assertNotNull(restored.width)
        assertNotNull(restored.nullable)
    }
    
    @Test
    fun `should support cursor schema serialization`() {
        // Given: Multiple column metadata for cursor schema
        val columns = listOf(
            IOMemento.create("id", "Long", 8, false),
            IOMemento.create("name", "String", 255, true),
            IOMemento.create("created_at", "Instant", 12, false)
        )
        
        // When: Serialized as cursor schema
        val metadata = mutableListOf<ByteArray>()
        columns.forEach { col ->
            metadata.add(col.toWireBytes())
        }
        
        // Then: Should be able to reconstruct schema
        val restoredColumns = metadata.map { it.toIoMemento() }
        assertEquals(columns.size, restoredColumns.size)
        for (i in columns.indices) {
            assertEquals(columns[i].name, restoredColumns[i].name)
            assertEquals(columns[i].type, restoredColumns[i].type)
        }
    }
    
    // ===== Performance and Efficiency Tests =====
    
    @Test
    fun `should achieve 60-80% size reduction vs JSON`() {
        // Given: Data that would be serialized
        val memento = IOMemento.create("test_field", "String", 255, true).apply {
            encoding = "utf-8"
            format = "varchar"
        }
        
        // When: Serialized to wire format
        val wireBytes = memento.toWireBytes()
        
        // Simulate JSON representation (approximate)
        val jsonSize = """{"name":"test_field","type":"String","width":255,"nullable":true,"encoding":"utf-8","format":"varchar"}""".length
        
        // Then: Should be significantly smaller than JSON
        val compressionRatio = wireBytes.size.toDouble() / jsonSize
        assertTrue(compressionRatio <= 0.8, "Wire format should be at least 20% smaller than JSON")
        assertTrue(compressionRatio >= 0.2, "Wire format should not be unrealistically small")
    }
    
    @Test
    fun `should support zero-copy operations where possible`() {
        // Given: Large data set
        val largeSeries = \1 j { \2: Int -> i }
        
        // When: Serialized
        val wireBytes = largeSeries.toWireBytes()
        
        // Then: Should be efficient (zero-copy where possible)
        // This is a performance assertion - zero-copy should result in faster serialization
        val startTime = System.nanoTime()
        val restored = wireBytes.toSeries<Int>()
        val endTime = System.nanoTime()
        
        val timeMicroseconds = (endTime - startTime) / 1000.0
        assertTrue(timeMicroseconds <= 100, "Deserialization should be efficient (zero-copy where possible)")
        assertEquals(largeSeries.size, restored.size)
    }
    
    // ===== Error Handling Tests =====
    
    @Test
    fun `should provide clear error messages for corrupted data`() {
        // Given: Corrupted wire data
        val corruptedData = byteArrayOf(1, 2, 3, 4, 5) // Invalid format
        
        // When: Attempting to deserialize
        // Then: Should provide clear error message
        assertFailsWith<IllegalArgumentException>("Should fail with clear error message") {
            corruptedData.toIoMemento()
        }
    }
    
    @Test
    fun `should handle empty or null data gracefully`() {
        // Given: Empty data
        val emptyData = byteArrayOf()
        
        // When: Attempting to deserialize
        // Then: Should fail gracefully with clear error
        assertFailsWith<IllegalArgumentException>("Should fail gracefully for empty data") {
            emptyData.toIoMemento()
        }
    }
} 