package borg.trikeshed.core

import kotlin.test.*

class PackedTypesTest {

    // --- Packer<T> Tests ---

    @Test
    fun testIntPacker() {
        val packer = IntPacker
        assertEquals(32, packer.bitSize, "IntPacker bitSize should be 32")
        assertEquals(0xFFFFFFFFL, packer.bitMask, "IntPacker bitMask should be 0xFFFFFFFFL")

        val valuesToTest = listOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE, 12345, -67890)
        valuesToTest.forEach { value ->
            val packed = packer.pack(value)
            val unpacked = packer.unpack(packed)
            assertEquals(value, unpacked, "Value $value did not match after pack/unpack. Packed: $packed")
            // Check if packed value respects bitSize (for positive numbers, it shouldn't exceed 2^31-1 if sign bit is not part of this,
            // or for negative numbers, it should be correctly represented)
            // For Int, toLong() preserves sign extension, so direct check is fine.
            // We are interested that unpack(pack(X)) == X
        }

        // Test packing specifically for bit representation if necessary
        assertEquals(0L, packer.pack(0))
        assertEquals(1L, packer.pack(1))
        assertEquals(0xFFFFFFFFL, packer.pack(-1), "Packing -1 for IntPacker") // -1 is all ones in 2's complement
        assertEquals(0x7FFFFFFFL, packer.pack(Int.MAX_VALUE))
        assertEquals(0x80000000L, packer.pack(Int.MIN_VALUE)) // This is -2147483648 as a Long
    }

    @Test
    fun testBooleanPacker() {
        val packer = BooleanPacker
        assertEquals(1, packer.bitSize, "BooleanPacker bitSize should be 1")
        assertEquals(0x1L, packer.bitMask, "BooleanPacker bitMask should be 0x1L")

        assertTrue(packer.unpack(packer.pack(true)), "Packing/unpacking true failed")
        assertFalse(packer.unpack(packer.pack(false)), "Packing/unpacking false failed")

        assertEquals(1L, packer.pack(true), "Packed true should be 1L")
        assertEquals(0L, packer.pack(false), "Packed false should be 0L")

        assertTrue(packer.unpack(1L), "Unpacking 1L should be true")
        assertFalse(packer.unpack(0L), "Unpacking 0L should be false")
        // Boolean packer should ideally only consider the LSB from bitMask
        assertTrue(packer.unpack(0xFFFFFFFFFFFFFFFFL), "Unpacking all ones should be true (checks LSB)")
        assertFalse(packer.unpack(0xFFFFFFFFFFFFFFFEL), "Unpacking all ones but LSB zero should be false")

    }

    // --- PackerRegistry Tests ---

    // Dummy IsPackable type for registry testing
    object MyTestPackable : IsPackable
    object MyTestPackablePacker : Packer<@IsPackableType MyTestPackable> {
        override fun pack(value: @IsPackableType MyTestPackable): ActualPackedBits = 123L
        override fun unpack(bits: ActualPackedBits): @IsPackableType MyTestPackable = MyTestPackable
        override val bitSize: Int = 8
    }

    @Test
    fun testPackerRegistry_DefaultPackers() {
        val intPacker = PackerRegistry.get(Int::class)
        assertNotNull(intPacker, "IntPacker should be registered by default")
        assertSame(IntPacker, intPacker, "Registered IntPacker should be the IntPacker object")

        val boolPacker = PackerRegistry.get(Boolean::class)
        assertNotNull(boolPacker, "BooleanPacker should be registered by default")
        assertSame(BooleanPacker, boolPacker, "Registered BooleanPacker should be the BooleanPacker object")
    }

    @Test
    fun testGetPackerFromRegistry_DefaultPackers() {
         val intPacker = getPackerFromRegistry<Int>()
         assertNotNull(intPacker, "IntPacker should be retrieved by getPackerFromRegistry")
         assertSame(IntPacker, intPacker)

         val boolPacker = getPackerFromRegistry<Boolean>()
         assertNotNull(boolPacker, "BooleanPacker should be retrieved by getPackerFromRegistry")
         assertSame(BooleanPacker, boolPacker)
    }


    @Test
    fun testPackerRegistry_CustomPacker() {
        // Register the custom packer
        // Note: Need to cast MyTestPackablePacker because MyTestPackable is an object,
        // and Packer<MyTestPackable> is what we need.
        // The type system requires a bit of help here.
        PackerRegistry.register(MyTestPackable::class, MyTestPackablePacker as Packer<MyTestPackable>)

        val customPacker = PackerRegistry.get(MyTestPackable::class)
        assertNotNull(customPacker, "Custom packer for MyTestPackable should be found after registration")
        assertSame(MyTestPackablePacker, customPacker, "Retrieved custom packer should be the same instance")
    }

    @Test
    fun testGetPackerFromRegistry_CustomPacker() {
        // Assumes MyTestPackablePacker is already registered from previous test or in a setup block
        // For isolated tests, register here:
        PackerRegistry.register(MyTestPackable::class, MyTestPackablePacker as Packer<MyTestPackable>)

        val customPacker = getPackerFromRegistry<MyTestPackable>()
        assertNotNull(customPacker)
        assertSame(MyTestPackablePacker, customPacker)
    }


    @Test
    fun testPackerRegistry_NotFound() {
        // Some other type that isn't registered
        class AnotherType {}
        val packer = PackerRegistry.get(AnotherType::class)
        assertNull(packer, "Packer for an unregistered type should be null")
    }

    @Test
    fun testGetPackerFromRegistry_NotFound() {
        class YetAnotherType {}
        assertFailsWith<UnsupportedOperationException>("Should throw for unregistered type via getPackerFromRegistry") {
            getPackerFromRegistry<YetAnotherType>()
        }
    }

    @Test
    fun testGetPacker_BasicTypes() {
        // getPacker is simpler and might only support a few hardcoded types
        val intP = getPacker<Int>()
        assertSame(IntPacker, intP)

        val boolP = getPacker<Boolean>()
        assertSame(BooleanPacker, boolP)

        assertFailsWith<UnsupportedOperationException> {
            getPacker<MyTestPackable>() // getPacker doesn't know about MyTestPackable by default
        }
    }
}

// Note: The @IsPackableType annotation is conceptual.
// For MyTestPackable, it directly implements IsPackable.
// For Int/Boolean, Packer<@IsPackableType Int> is used to satisfy Packer<T: IsPackable>
// while IntPacker/BooleanPacker deal with raw Int/Boolean.
// The casts `as Packer<MyTestPackable>` for registration might need adjustment
// if MyTestPackable itself is not an object or if @IsPackableType has runtime effects (it doesn't).
// The main thing is that the KClass matches.

// Re-evaluating the MyTestPackable for registry:
// The type for Packer<T> is `T : IsPackable`.
// `MyTestPackable` is `IsPackable`. `MyTestPackablePacker` is `Packer<MyTestPackable>`.
// The cast `as Packer<MyTestPackable>` in `testPackerRegistry_CustomPacker` is actually fine.
// It was the `Packer<Any>` cast in `PackerRegistry.init` that was for variance with the map.

    // --- ActualPackedBits Extension Function Tests ---

    @Test
    fun testGetBits() {
        val value = 0x0123456789ABCDEF_L // Example: FEDCBA9876543210 in hex if reversed by nibble

        // Test basic extractions
        assertEquals(0xEF_L, value.getBits(0, 8), "GetBits: LSB byte")
        assertEquals(0xCD_L, value.getBits(8, 8), "GetBits: Second byte")
        assertEquals(0x01_L, value.getBits(56, 8), "GetBits: MSB byte")

        // Test single bit extraction
        assertEquals(1L, value.getBits(0, 1), "GetBits: LSB itself") // LSB of 0xEF is 1
        assertEquals(0L, value.getBits(1, 1), "GetBits: Second bit of LSB byte") // Second bit of 0xEF is 1 (1110_1111) -> this is 1
        // Correcting: EF = 1110_1111. Bit 0 is 1. Bit 1 is 1. Bit 2 is 1. Bit 3 is 0. Bit 4 is 1...
        assertEquals(1L, value.getBits(0,1))
        assertEquals(1L, value.getBits(1,1))
        assertEquals(1L, value.getBits(2,1))
        assertEquals(1L, value.getBits(3,1)) // Corrected: EF = 1110_1111. Bit 3 is 1
        assertEquals(0L, value.getBits(4,1)) // Corrected: EF = 1110_1111. Bit 4 is 0

        // Test extraction across byte boundaries (within a Long)
        assertEquals(0x5678_L, value.getBits(16, 16), "GetBits: Middle 16 bits") // Bytes for 0x4567

        // Test full extraction (or close to it, respecting 64-start limit for length)
        assertEquals(value and ((1L shl 63) -1) , value.getBits(0, 63), "GetBits: Lower 63 bits") //
        assertEquals(value, value.getBits(0, 64-0), "GetBits: Full 64 bits")


        // Test with all ones and all zeros
        val allOnes = -1L // 0xFFFFFFFFFFFFFFFF_L
        assertEquals(0xFF_L, allOnes.getBits(0, 8), "GetBits: All ones, LSB byte")
        assertEquals(0x1_L, allOnes.getBits(63, 1), "GetBits: All ones, MSB")
        assertEquals(allOnes, allOnes.getBits(0,64))


        val allZeros = 0L
        assertEquals(0L, allZeros.getBits(0, 8), "GetBits: All zeros, LSB byte")
        assertEquals(0L, allZeros.getBits(32, 32), "GetBits: All zeros, upper 32 bits")
        assertEquals(0L, allZeros.getBits(0,64))


        // Test edge lengths and positions
        assertEquals(allOnes.getBits(0,1), 1L, "GetBits: Length 1 at start from all ones")
        assertEquals(allOnes.getBits(63,1), 1L, "GetBits: Length 1 at end from all ones")

        // Test require conditions
        assertFailsWith<IllegalArgumentException> { value.getBits(-1, 5) }
        assertFailsWith<IllegalArgumentException> { value.getBits(0, 0) }
        assertFailsWith<IllegalArgumentException> { value.getBits(0, 65) }
        assertFailsWith<IllegalArgumentException> { value.getBits(64, 1) }
        assertFailsWith<IllegalArgumentException> { value.getBits(1, 64) }
    }

    @Test
    fun testSetBits() {
        var baseValue = 0L

        // Set LSB byte
        baseValue = baseValue.setBits(0, 8, 0xFF_L)
        assertEquals(0xFF_L, baseValue, "SetBits: LSB byte to FF")

        // Set second byte, ensure LSB byte is untouched
        baseValue = baseValue.setBits(8, 8, 0xAA_L)
        assertEquals(0xAAFF_L, baseValue, "SetBits: Second byte to AA")

        // Set bits in the middle, ensure surrounding bits are untouched
        baseValue = 0xFEDCBA9876543210_L
        val originalBase = baseValue
        baseValue = baseValue.setBits(16, 16, 0xEEEE_L) // bits from 16 to 31
        // Original: ...BA98 7654... -> Target: ...BA98 EEEE...
        // Mask for original part: 0xFFFF0000FFFF_L
        val expectedAfterMiddleSet = (originalBase and (0xFFFF_FFFF_0000_FFFF_L)) or (0xEEEE_L shl 16)
        assertEquals(expectedAfterMiddleSet, baseValue, "SetBits: Middle 16 bits to EEEE")


        // Test setting single bit
        var singleBitTest = 0L
        singleBitTest = singleBitTest.setBits(0, 1, 1L)
        assertEquals(1L, singleBitTest, "SetBits: Set LSB to 1")
        singleBitTest = singleBitTest.setBits(0, 1, 0L)
        assertEquals(0L, singleBitTest, "SetBits: Set LSB to 0")
        singleBitTest = singleBitTest.setBits(63, 1, 1L)
        assertEquals(1L shl 63, singleBitTest, "SetBits: Set MSB to 1")
        singleBitTest = singleBitTest.setBits(63, 1, 0L)
        assertEquals(0L, singleBitTest, "SetBits: Set MSB to 0")

        // Test setting bits where bitsToSet is larger than length (should be masked)
        var maskingTest = 0L
        maskingTest = maskingTest.setBits(0, 4, 0x1F_L) // Set 4 bits with 0b11111 (F should be used)
        assertEquals(0xF_L, maskingTest, "SetBits: bitsToSet should be masked by length")

        // Test setting bits to zero out a section
        var zeroOutTest = -1L // All ones
        zeroOutTest = zeroOutTest.setBits(8, 8, 0L) // Zero out second byte
        val expectedZeroOut = -1L xor (0xFF_L shl 8) // All ones except second byte is zero
        assertEquals(expectedZeroOut, zeroOutTest, "SetBits: Zero out second byte")

        // Test full set (or close to it)
        var fullSetTest = 0L
        fullSetTest = fullSetTest.setBits(0, 63, -1L) // -1L masked to 63 bits is (1L shl 63) -1
        assertEquals((1L shl 63) -1, fullSetTest, "SetBits: Lower 63 bits to ones")

        fullSetTest = 0L
        fullSetTest = fullSetTest.setBits(0,64, -1L)
        assertEquals(-1L, fullSetTest, "SetBits: Full 64 bits to ones")

        fullSetTest = (-1L).setBits(0,64,0L)
        assertEquals(0L, fullSetTest, "SetBits: Full 64 bits to zeros")


        // Test require conditions for setBits
        val v = 0L
        assertFailsWith<IllegalArgumentException> { v.setBits(-1, 5, 0L) }
        assertFailsWith<IllegalArgumentException> { v.setBits(0, 0, 0L) }
        assertFailsWith<IllegalArgumentException> { v.setBits(0, 65, 0L) }
        assertFailsWith<IllegalArgumentException> { v.setBits(64, 1, 0L) }
        assertFailsWith<IllegalArgumentException> { v.setBits(1, 64, 0L) }
    }

    // --- NSeries Basic Tests (without kjSeries) ---

    @Test
    fun testNSeries_Properties_NonSpilled() {
        val boolPacker = getPackerFromRegistry<Boolean>()
        val seriesLength = 3
        val totalElementBits = seriesLength * boolPacker.bitSize // 3 * 1 = 3

        // Manually craft a packed Long:
        // Metadata: seriesLength (3) in NSeries.SIZE_METADATA_BITS (8 bits) = 0x03
        // Data: true, false, true (1, 0, 1)
        // Layout: [A2][A1][A0][SizeMetadata]
        // A0 (true) at offset 8, A1 (false) at offset 9, A2 (true) at offset 10
        // So, (1 << 10) | (0 << 9) | (1 << 8) | 3 = 0b10100000011
        var packedDataContent = 0L
        packedDataContent = packedDataContent.setBits(0, NSeries.SIZE_METADATA_BITS, seriesLength.toLong()) // Size
        packedDataContent = packedDataContent.setBits(NSeries.SIZE_METADATA_BITS + 0*boolPacker.bitSize, boolPacker.bitSize, boolPacker.pack(true))  // Element 0
        packedDataContent = packedDataContent.setBits(NSeries.SIZE_METADATA_BITS + 1*boolPacker.bitSize, boolPacker.bitSize, boolPacker.pack(false)) // Element 1
        packedDataContent = packedDataContent.setBits(NSeries.SIZE_METADATA_BITS + 2*boolPacker.bitSize, boolPacker.bitSize, boolPacker.pack(true))  // Element 2

        val packedBitsList = listOf(packedDataContent)

        val nSeries = NSeries(
            packedBitsList = packedBitsList,
            packerT = boolPacker,
            seriesSize = seriesLength,
            totalElementBitSize = totalElementBits
        )

        assertEquals(seriesLength, nSeries.seriesSize, "NSeries seriesSize property")
        assertFalse(nSeries.isSpilled(), "NSeries with 1 segment should not be spilled")
        assertEquals(totalElementBits, nSeries.totalElementBitSize, "NSeries totalElementBitSize property")

        // Test the simplified get() method for this non-spilled case
        assertTrue(nSeries.get(0), "NSeries.get(0) expected true")
        assertFalse(nSeries.get(1), "NSeries.get(1) expected false")
        assertTrue(nSeries.get(2), "NSeries.get(2) expected true")
    }

    @Test
    fun testNSeries_Get_OutOfBounds() {
        val boolPacker = getPackerFromRegistry<Boolean>()
        // Minimal NSeries for testing bounds
        val packedBitsList = listOf(0L.setBits(0, NSeries.SIZE_METADATA_BITS, 1L)) // Series of size 1
        val nSeries = NSeries(
            packedBitsList = packedBitsList,
            packerT = boolPacker,
            seriesSize = 1,
            totalElementBitSize = boolPacker.bitSize
        )

        assertFailsWith<IndexOutOfBoundsException>("NSeries.get() should fail for negative index") {
            nSeries.get(-1)
        }
        assertFailsWith<IndexOutOfBoundsException>("NSeries.get() should fail for index == size") {
            nSeries.get(1)
        }
        assertFailsWith<IndexOutOfBoundsException>("NSeries.get() should fail for index > size") {
            nSeries.get(2)
        }
    }

    @Test
    fun testNSeries_EmptySeries() {
        val boolPacker = getPackerFromRegistry<Boolean>()
        val seriesLength = 0
        val totalElementBits = 0

        // Manually craft packed Long for empty series (only size metadata)
        var packedDataContent = 0L
        packedDataContent = packedDataContent.setBits(0, NSeries.SIZE_METADATA_BITS, seriesLength.toLong())
        val packedBitsList = listOf(packedDataContent)

        val nSeries = NSeries(
            packedBitsList = packedBitsList,
            packerT = boolPacker,
            seriesSize = seriesLength,
            totalElementBitSize = totalElementBits
        )
        assertEquals(0, nSeries.seriesSize)
        assertFalse(nSeries.isSpilled())
        assertEquals(0, nSeries.totalElementBitSize)

        assertFailsWith<IndexOutOfBoundsException>("NSeries.get() on empty series should fail") {
            nSeries.get(0)
        }
    }
