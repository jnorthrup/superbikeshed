package borg.trikeshed.wireproto

import kotlin.test.*

class ManualJoinOverloadsTest {
    
    @Test
    fun testIntBooleanJoin() {
        val joined = 42.j(true)
        
        val unpackedInt = joined.unpackA(PInt)
        val unpackedBool = joined.unpackB(PInt, PBoolean)
        
        assertEquals(42, unpackedInt)
        assertEquals(true, unpackedBool)
    }
    
    @Test
    fun testBooleanIntJoin() {
        val joined = false.j(12345)
        
        val unpackedBool = joined.unpackA(PBoolean)
        val unpackedInt = joined.unpackB(PBoolean, PInt)
        
        assertEquals(false, unpackedBool)
        assertEquals(12345, unpackedInt)
    }
    
    @Test
    fun testShortShortJoin() {
        val a = 1000.toShort()
        val b = 2000.toShort()
        val joined = a.j(b)
        
        val unpackedA = joined.unpackA(PShort)
        val unpackedB = joined.unpackB(PShort, PShort)
        
        assertEquals(a, unpackedA)
        assertEquals(b, unpackedB)
    }
    
    @Test
    fun testByteByteJoin() {
        val a = 100.toByte()
        val b = 200.toByte()
        val joined = a.j(b)
        
        val unpackedA = joined.unpackA(PByte)
        val unpackedB = joined.unpackB(PByte, PByte)
        
        assertEquals(a, unpackedA)
        assertEquals(b, unpackedB)
    }
    
    @Test
    fun testBooleanBooleanJoin() {
        val joined1 = true.j(false)
        val joined2 = false.j(true)
        
        assertEquals(true, joined1.unpackA(PBoolean))
        assertEquals(false, joined1.unpackB(PBoolean, PBoolean))
        
        assertEquals(false, joined2.unpackA(PBoolean))
        assertEquals(true, joined2.unpackB(PBoolean, PBoolean))
    }
    
    @Test
    fun testFloatFloatJoin() {
        val a = 3.14159f
        val b = 2.71828f
        val joined = a.j(b)
        
        val unpackedA = joined.unpackA(PFloat)
        val unpackedB = joined.unpackB(PFloat, PFloat)
        
        assertEquals(a, unpackedA, 0.00001f)
        assertEquals(b, unpackedB, 0.00001f)
    }
    
    @Test
    fun testIntByteJoin() {
        val joined = 0x12345678.j(0xAB.toByte())
        
        val unpackedInt = joined.unpackA(PInt)
        val unpackedByte = joined.unpackB(PInt, PByte)
        
        assertEquals(0x12345678, unpackedInt)
        assertEquals(0xAB.toByte(), unpackedByte)
    }
    
    @Test
    fun testRegisterJoinSerialization() {
        val original = 42.j(true)
        
        // Serialize to wire format
        val wireBytes = original.toWireBytes()
        assertTrue(wireBytes.isNotEmpty())
        
        // Deserialize back
        val deserialized = wireBytes.toRegisterJoin()
        
        // Verify the packed word is the same
        assertEquals(original.word, deserialized.word)
    }
    
    @Test
    fun testPackableImplementations() {
        // Test PInt
        val intValue = -12345
        val packedInt = PInt.pack(intValue)
        val unpackedInt = PInt.unpack(packedInt)
        assertEquals(intValue, unpackedInt)
        
        // Test PBoolean
        val boolValue = true
        val packedBool = PBoolean.pack(boolValue)
        val unpackedBool = PBoolean.unpack(packedBool)
        assertEquals(boolValue, unpackedBool)
        
        // Test PByte
        val byteValue = (-128).toByte()
        val packedByte = PByte.pack(byteValue)
        val unpackedByte = PByte.unpack(packedByte)
        assertEquals(byteValue, unpackedByte)
        
        // Test PShort
        val shortValue = (-32000).toShort()
        val packedShort = PShort.pack(shortValue)
        val unpackedShort = PShort.unpack(packedShort)
        assertEquals(shortValue, unpackedShort)
        
        // Test PFloat
        val floatValue = -123.456f
        val packedFloat = PFloat.pack(floatValue)
        val unpackedFloat = PFloat.unpack(packedFloat)
        assertEquals(floatValue, unpackedFloat)
        
        // Test PDouble
        val doubleValue = -123456.789
        val packedDouble = PDouble.pack(doubleValue)
        val unpackedDouble = PDouble.unpack(packedDouble)
        assertEquals(doubleValue, unpackedDouble)
    }
    
    @Test
    fun testBitWidths() {
        assertEquals(32, PInt.bitWidth)
        assertEquals(64, PLong.bitWidth)
        assertEquals(1, PBoolean.bitWidth)
        assertEquals(8, PByte.bitWidth)
        assertEquals(16, PShort.bitWidth)
        assertEquals(32, PFloat.bitWidth)
        assertEquals(64, PDouble.bitWidth)
    }
    
    @Test
    fun testZeroCostAbstraction() {
        // This test verifies that the register joins are truly zero-cost
        val joined = 0x12345678.j(true)
        
        // The word should contain both values bit-packed
        val expectedWord = 0x12345678L or (1L shl 32)
        assertEquals(expectedWord, joined.word)
        
        // Verify manual bit extraction matches packable extraction
        val manualInt = (joined.word and 0xFFFFFFFF).toInt()
        val manualBool = (joined.word shr 32) != 0L
        
        assertEquals(0x12345678, manualInt)
        assertEquals(true, manualBool)
        
        assertEquals(manualInt, joined.unpackA(PInt))
        assertEquals(manualBool, joined.unpackB(PInt, PBoolean))
    }
    
    @Test
    fun testEdgeCases() {
        // Test with zeros
        val zeros = 0.j(false)
        assertEquals(0, zeros.unpackA(PInt))
        assertEquals(false, zeros.unpackB(PInt, PBoolean))
        
        // Test with max values
        val maxes = Int.MAX_VALUE.j(true)
        assertEquals(Int.MAX_VALUE, maxes.unpackA(PInt))
        assertEquals(true, maxes.unpackB(PInt, PBoolean))
        
        // Test with min values  
        val mins = Int.MIN_VALUE.j(false)
        assertEquals(Int.MIN_VALUE, mins.unpackA(PInt))
        assertEquals(false, mins.unpackB(PInt, PBoolean))
    }
}