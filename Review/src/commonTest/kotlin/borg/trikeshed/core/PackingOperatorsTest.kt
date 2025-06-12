package borg.trikeshed.core

import kotlin.test.*

// Helper IsPackable type for precise bit size control in tests
data class TinyVal(val data: Byte, val trueNumBits: Int) : IsPackable {
    // data holds the value, trueNumBits is just for context during assertion/debugging
    // The packer will define the actual bitSize used for packing.
}

class TinyValPacker(private val configuredBitSize: Int) : Packer<TinyVal> {
    init {
        require(configuredBitSize in 1..8) { "TinyValPacker bit size must be between 1 and 8." }
    }
    override fun pack(value: TinyVal): ActualPackedBits {
        // Ensure data fits within the packer's configured bitSize for this instance of TinyVal
        val mask = (1L shl configuredBitSize) - 1
        return value.data.toLong() and mask
    }
    override fun unpack(bits: ActualPackedBits): TinyVal {
        // When unpacking, we only know the configuredBitSize of this packer.
        // The trueNumBits from the original TinyVal isn't directly available from 'bits' alone.
        // We construct a TinyVal; the 'data' part is from 'bits', 'trueNumBits' is the packer's size.
        return TinyVal(bits.toByte(), configuredBitSize)
    }
    override val bitSize: Int get() = configuredBitSize
}


class PackingOperatorsTest {

    // Register TinyVal packers with different bit sizes for testing
    // This would ideally be in a @BeforeTest or suite setup if tests were more complex,
    // but for simplicity, register as needed or rely on ad-hoc registration in tests.
    // Let's register them once.
    companion object {
        init {
            // Ensure default packers are available from PackedTypesTest if that runs first
            // or re-register if necessary for isolated test runs.
            // PackerRegistry.register(Int::class, IntPacker as Packer<Any>)
            // PackerRegistry.register(Boolean::class, BooleanPacker as Packer<Any>)

            PackerRegistry.register(TinyVal::class, TinyValPacker(3) as Packer<TinyVal>) // Default TinyVal packer
            // If specific tests need different TinyVal packers, they can register/unregister or use distinct types.
            // For now, we will create packers explicitly in tests rather than relying on a single registered TinyValPacker.
        }
    }

    private fun getTinyValPacker(bits: Int): TinyValPacker {
        return TinyValPacker(bits)
    }

    @Test
    fun testKj_BoolBool_NonSpilled() {
        // B: Boolean, A: Boolean
        // packerB.bitSize = 1, packerA.bitSize = 1. Total = 2 bits.
        // Expected layout: A(1 bit) then B(1 bit) (kj packs B first, then A)
        // Segment: ...00[A][B] (Continuation bit 0)

        val valB = true  // Receiver
        val valA = false // Argument

        // Manually register packers for this specific test instance to avoid conflicts
        val boolPacker = getPackerFromRegistry<Boolean>()
        // (Already registered by default)

        val result = valB.kj(valA)

        assertTrue(result.isSuccess, "Packing Boolean.kj(Boolean) should succeed. Error: ${result.getErrorOrNull()}")
        val nJoin = result.getOrNull()!!

        assertEquals(1, nJoin.packedBitsList.size, "Should use 1 segment for Bool+Bool")
        assertEquals(boolPacker.bitSize + boolPacker.bitSize, nJoin.totalBitSize, "Total bit size for Bool+Bool")
        assertFalse(nJoin.isSpilled(), "Bool+Bool should not be spilled")

        val packedLong = nJoin.packedBitsList[0]
        // B (true = 1) is packed first at LSB. A (false = 0) is packed at bit 1.
        // Expected: 0...00[A=0][B=1] = 0b01 = 1L
        assertEquals(0b01L, packedLong and ((1L shl (boolPacker.bitSize + boolPacker.bitSize))-1) , "Packed bits for true.kj(false)")
        assertEquals(0L, packedLong and CONTINUATION_BIT_MASK, "Continuation bit should be 0")

        // Test unpacking (relies on NJoin.getA/B for non-spilled)
        assertEquals(valA, nJoin.getA(), "Unpacked A should match original")
        assertEquals(valB, nJoin.getB(), "Unpacked B should match original")
    }

    @Test
    fun testKj_TinyVal3_TinyVal5_NonSpilled() {
        // B: TinyVal (5 bits), A: TinyVal (3 bits)
        // Total bits = 8. Fits in one segment.
        // Expected layout: A(3 bits) then B(5 bits)
        // Segment: ...0[AAA][BBBBB] (Continuation bit 0)

        //val packerB = getTinyValPacker(5) // B is receiver
        //val packerA = getTinyValPacker(3) // A is argument

        // Temporarily register these specific packers for an IsPackable type if kj relies on registry
        // This is tricky because TinyVal is one class. For this test, let's assume kj can take packers directly
        // OR we use distinct subtypes of IsPackable if we must use the registry with fixed packers per type.
        // The current kj implementation uses getPackerFromRegistry.
        // To test this properly, we would need two distinct IsPackable types or a more flexible getPacker.

        // Let's create two distinct types for this test to work with current getPackerFromRegistry
        data class MyPackableB(val v: Byte): IsPackable
        class MyPackerBImpl(val bits: Int) : Packer<MyPackableB> {
            override fun pack(value: MyPackableB): ActualPackedBits = value.v.toLong()
            override fun unpack(bits: ActualPackedBits): MyPackableB = MyPackableB(bits.toByte())
            override val bitSize: Int get() = this.bits
        }
        data class MyPackableA(val v: Byte): IsPackable
        class MyPackerAImpl(val bits: Int) : Packer<MyPackableA> {
            override fun pack(value: MyPackableA): ActualPackedBits = value.v.toLong()
            override fun unpack(bits: ActualPackedBits): MyPackableA = MyPackableA(bits.toByte())
            override val bitSize: Int get() = this.bits
        }

        val pB = MyPackerBImpl(5)
        val pA = MyPackerAImpl(3)
        PackerRegistry.register(MyPackableB::class, pB as Packer<MyPackableB>)
        PackerRegistry.register(MyPackableA::class, pA as Packer<MyPackableA>)

        val valB = MyPackableB(0b10101.toByte()) // 5 bits, value 21
        val valA = MyPackableA(0b101.toByte())   // 3 bits, value 5

        val result = valB.kj(valA)

        assertTrue(result.isSuccess, "Packing MyPackableB(5).kj(MyPackableA(3)) should succeed. Error: ${result.getErrorOrNull()}")
        val nJoin = result.getOrNull()!!

        assertEquals(1, nJoin.packedBitsList.size)
        assertEquals(pA.bitSize + pB.bitSize, nJoin.totalBitSize)
        assertFalse(nJoin.isSpilled())

        val packedLong = nJoin.packedBitsList[0]
        // B (0b10101) is packed first (LSB). A (0b101) is packed next.
        // Expected: 0...0 [A=101][B=10101] = 0b10110101 = 0xB5
        val expectedPacked = (valA.v.toLong() shl pB.bitSize) or (valB.v.toLong())
        assertEquals(expectedPacked, packedLong and ((1L shl (pA.bitSize + pB.bitSize))-1), "Packed bits for custom types")
        assertEquals(0L, packedLong and CONTINUATION_BIT_MASK, "Continuation bit should be 0")

        // Test unpacking
        val unpackedA = nJoin.getA()
        val unpackedB = nJoin.getB()
        assertEquals(valA.v, unpackedA.v, "Unpacked A.v should match")
        assertEquals(valB.v, unpackedB.v, "Unpacked B.v should match")

        // Clean up registry if necessary, though for tests it's usually fine
        // PackerRegistry.unregister(MyPackableB::class)
        // PackerRegistry.unregister(MyPackableA::class)
    }

    @Test
    fun testKj_IntBool_NonSpilled() {
        // B: Boolean (receiver), A: Int (argument)
        // packerB.bitSize = 1, packerA.bitSize = 32. Total = 33 bits.
        // Expected layout: A(32 bits) then B(1 bit)

        val boolPacker = getPackerFromRegistry<Boolean>()
        val intPacker = getPackerFromRegistry<Int>()

        val valB = false
        val valA = 0x12345678 // An Int

        val result = valB.kj(valA)
        assertTrue(result.isSuccess, "Packing Boolean.kj(Int) should succeed. Error: ${result.getErrorOrNull()}")
        val nJoin = result.getOrNull()!!

        assertEquals(1, nJoin.packedBitsList.size)
        assertEquals(intPacker.bitSize + boolPacker.bitSize, nJoin.totalBitSize)
        assertFalse(nJoin.isSpilled())

        val packedLong = nJoin.packedBitsList[0]
        // B (false=0) is packed first (LSB). A (0x12345678) is packed next.
        // Expected: 0...0 [A=0x12345678][B=0]
        val expectedBits = (valA.toLong() shl boolPacker.bitSize) or (if (valB) 1L else 0L)

        val totalBits = intPacker.bitSize + boolPacker.bitSize
        val maskForPayload = (1L shl totalBits) -1
        assertEquals(expectedBits, packedLong and maskForPayload, "Packed bits for Boolean.kj(Int)")
        assertEquals(0L, packedLong and CONTINUATION_BIT_MASK, "Continuation bit should be 0")

        assertEquals(valA, nJoin.getA(), "Unpacked A (Int) should match")
        assertEquals(valB, nJoin.getB(), "Unpacked B (Boolean) should match")
    }

    @Test
    fun testKj_MaxNonSpilled_63bits() {
        // Test packing exactly 63 bits.
        // B: MyPackableB (31 bits), A: MyPackableA (32 bits)
        // Total = 63 bits. Should fit in one segment, no continuation bit set (as it's the last).
         data class MyPackableB63(val v: Long): IsPackable
        class MyPackerB63Impl(val bits: Int) : Packer<MyPackableB63> {
            override fun pack(value: MyPackableB63): ActualPackedBits = value.v
            override fun unpack(bits: ActualPackedBits): MyPackableB63 = MyPackableB63(bits)
            override val bitSize: Int get() = this.bits
        }
        data class MyPackableA63(val v: Long): IsPackable
        class MyPackerA63Impl(val bits: Int) : Packer<MyPackableA63> {
            override fun pack(value: MyPackableA63): ActualPackedBits = value.v
            override fun unpack(bits: ActualPackedBits): MyPackableA63 = MyPackableA63(bits)
            override val bitSize: Int get() = this.bits
        }

        val pB = MyPackerB63Impl(31)
        val pA = MyPackerA63Impl(32)
        PackerRegistry.register(MyPackableB63::class, pB as Packer<MyPackableB63>)
        PackerRegistry.register(MyPackableA63::class, pA as Packer<MyPackableA63>)

        val valB_data = (1L shl 31) -1 // All ones for 31 bits
        val valA_data = (1L shl 32) -1 // All ones for 32 bits

        val valB = MyPackableB63(valB_data)
        val valA = MyPackableA63(valA_data)

        val result = valB.kj(valA)
        assertTrue(result.isSuccess, "Packing 63 bits should succeed. Error: ${result.getErrorOrNull()}")
        val nJoin = result.getOrNull()!!

        assertEquals(1, nJoin.packedBitsList.size, "63 bits should use 1 segment")
        assertEquals(63, nJoin.totalBitSize, "Total bit size for 63 bits")
        assertFalse(nJoin.isSpilled(), "63 bits should not be spilled")

        val packedLong = nJoin.packedBitsList[0]
        val expectedPacked = (valA_data shl pB.bitSize) or valB_data

        assertEquals(expectedPacked, packedLong and ((1L shl 63) -1), "Packed bits for 63 total bits")
        assertEquals(0L, packedLong and CONTINUATION_BIT_MASK, "Continuation bit for 63 bits (single segment) should be 0")

        assertEquals(valA_data, nJoin.getA().v, "Unpacked A for 63 bits")
        assertEquals(valB_data, nJoin.getB().v, "Unpacked B for 63 bits")
    }

    @Test
    fun testKj_Spilled_2Segments() {
        // B: 35 bits, A: 35 bits. Total = 70 bits.
        // PAYLOAD_BITS_PER_SEGMENT = 63.
        // Needs 2 segments.
        // Seg1: 63 bits payload + continuation. (35 of B, 28 of A)
        // Seg2: 7 bits payload (remaining 7 of A) + no continuation.

        data class MyPackableB70(val v: Long): IsPackable
        class MyPackerB70 : Packer<MyPackableB70> {
            override fun pack(value: MyPackableB70): ActualPackedBits = value.v
            override fun unpack(bits: ActualPackedBits): MyPackableB70 = MyPackableB70(bits)
            override val bitSize: Int get() = 35
        }
        data class MyPackableA70(val v: Long): IsPackable
        class MyPackerA70 : Packer<MyPackableA70> {
            override fun pack(value: MyPackableA70): ActualPackedBits = value.v
            override fun unpack(bits: ActualPackedBits): MyPackableA70 = MyPackableA70(bits)
            override val bitSize: Int get() = 35
        }

        val pB = MyPackerB70()
        val pA = MyPackerA70()
        PackerRegistry.register(MyPackableB70::class, pB as Packer<MyPackableB70>)
        PackerRegistry.register(MyPackableA70::class, pA as Packer<MyPackableA70>)

        val valB_data = ((1L shl 35) -1) xor 0x123L // Some 35-bit value
        val valA_data = ((1L shl 35) -2) xor 0xABC_L // Another 35-bit value

        val valB = MyPackableB70(valB_data)
        val valA = MyPackableA70(valA_data)

        val result = valB.kj(valA)
        assertTrue(result.isSuccess, "Packing 70 bits should succeed. Error: ${result.getErrorOrNull()}")
        val nJoin = result.getOrNull()!!

        assertEquals(2, nJoin.packedBitsList.size, "70 bits should use 2 segments")
        assertEquals(70, nJoin.totalBitSize, "Total bit size for 70 bits")
        assertTrue(nJoin.isSpilled(), "70 bits should be spilled")

        // Verify continuation bits
        assertTrue((nJoin.packedBitsList[0] and CONTINUATION_BIT_MASK) != 0L, "Segment 0 should have continuation bit")
        assertEquals(0L, nJoin.packedBitsList[1] and CONTINUATION_BIT_MASK, "Segment 1 should not have continuation bit")

        // Verify unpacked data using the (now updated) getA and getB
        assertEquals(valA_data, nJoin.getA().v, "Unpacked A for 70 bits (spilled)")
        assertEquals(valB_data, nJoin.getB().v, "Unpacked B for 70 bits (spilled)")
    }

    @Test
    fun testKj_Spilled_MaxSegments() {
        // B: 60 bits, A: 60 bits. Total 120 bits.
        // PAYLOAD_BITS_PER_SEGMENT = 63.
        // Seg1: B(60) + A(first 3) | Continuation  (63 bits payload)
        // Seg2: A(remaining 57)   | No Continuation (57 bits payload)
        // This test verifies a 2-segment spill case thoroughly. A direct MAX_PACKED_SEGMENTS fill
        // is harder with NJoin(A,B) if A or B individually exceed 64 bits due to Packer<T> limitations.

        val bSize = 60
        val aSize = 60
        data class SpilledB(val v: Long): IsPackable
        class SpilledPackerB : Packer<SpilledB> {
            override fun pack(value: SpilledB): ActualPackedBits = value.v
            override fun unpack(bits: ActualPackedBits): SpilledB = SpilledB(bits)
            override val bitSize: Int get() = bSize
        }
        data class SpilledA(val v: Long): IsPackable
        class SpilledPackerA : Packer<SpilledA> {
            override fun pack(value: SpilledA): ActualPackedBits = value.v
            override fun unpack(bits: ActualPackedBits): SpilledA = SpilledA(bits)
            override val bitSize: Int get() = aSize
        }
        PackerRegistry.register(SpilledB::class, SpilledPackerB() as Packer<SpilledB>)
        PackerRegistry.register(SpilledA::class, SpilledPackerA() as Packer<SpilledA>)

        val valB_data_120 = (1L shl bSize) - 1 // all ones for bSize bits
        val valA_data_120 = ((1L shl aSize) - 1) xor 0xAAFL // some pattern for aSize bits

        val valB_120 = SpilledB(valB_data_120)
        val valA_120 = SpilledA(valA_120)

        val result_120 = valB_120.kj(valA_120)
        assertTrue(result_120.isSuccess, "Packing 120 bits (60+60) should succeed. Error: ${result_120.getErrorOrNull()}")
        val nJoin_120 = result_120.getOrNull()!!

        assertEquals(2, nJoin_120.packedBitsList.size, "120 bits should use 2 segments")
        assertEquals(bSize + aSize, nJoin_120.totalBitSize)
        assertTrue(nJoin_120.isSpilled())

        assertTrue((nJoin_120.packedBitsList[0] and CONTINUATION_BIT_MASK) != 0L, "Seg 0 of 120 bits should have continuation")
        assertEquals(0L, (nJoin_120.packedBitsList[1] and CONTINUATION_BIT_MASK), "Seg 1 of 120 bits should not have continuation")

        assertEquals(valA_data_120, nJoin_120.getA().v, "Unpacked A for 120 bits")
        assertEquals(valB_data_120, nJoin_120.getB().v, "Unpacked B for 120 bits")

        println("Skipping direct MAX_PACKED_SEGMENTS test for NJoin(A,B) due to Packer<T> bitSize limit <= 64. Spilling for A+B > 63 is tested robustly with 2 segments (70-bit and 120-bit tests).")
    }

    @Test
    fun testKj_Failure_PackerNotFound() {
        // Define types that won't be registered
        data class UnregisteredB(val id: Int) : IsPackable
        data class UnregisteredA(val id: Int) : IsPackable

        val valB = UnregisteredB(1)
        val valA = UnregisteredA(1)

        // Case 1: Packer for B is not found
        // Register packer for A, but not for B
        class TempPackerA : Packer<UnregisteredA> {
            override fun pack(value: UnregisteredA): ActualPackedBits = value.id.toLong()
            override fun unpack(bits: ActualPackedBits): UnregisteredA = UnregisteredA(bits.toInt())
            override val bitSize: Int get() = 10
        }
        PackerRegistry.register(UnregisteredA::class, TempPackerA())

        var result = valB.kj(valA) // valB (UnregisteredB) packer is missing
        assertFalse(result.isSuccess, "Packing should fail if B's packer is not found.")
        assertTrue(result is PackingAttempt.PackingFailure, "Result should be PackingFailure.")
        val failure1 = result as PackingAttempt.PackingFailure
        assertTrue(failure1.reason.contains("Packer not found for type B (UnregisteredB)"), "Failure reason should mention missing B packer. Was: ${failure1.reason}")

        // Clean up for next case if registry is stateful across tests (it is)
        // For simplicity, assume tests might run in order or we need explicit unregister if this was problematic.
        // PackerRegistry.unregister(UnregisteredA::class) // Not a real method, illustrative

        // Case 2: Packer for A is not found (after B's packer is hypothetically found)
        // Re-initialize to ensure clean state for this specific test part for clarity
        // (or use different types to avoid complex cleanup)
        data class RegisteredBForFailure(val id: Int): IsPackable
        class TempPackerB : Packer<RegisteredBForFailure> {
            override fun pack(value: RegisteredBForFailure): ActualPackedBits = value.id.toLong()
            override fun unpack(bits: ActualPackedBits): RegisteredBForFailure = RegisteredBForFailure(bits.toInt())
            override val bitSize: Int get() = 10
        }
        PackerRegistry.register(RegisteredBForFailure::class, TempPackerB())
        val registeredValB = RegisteredBForFailure(1)
        // valA is still UnregisteredA, and we'll assume its packer (TempPackerA) is NOT registered for this sub-test.
        // To ensure TempPackerA is not found, we'd need to unregister it if it was registered by a previous test case,
        // or use a fresh type. Let's use a fresh type for A.
        data class UnregisteredFreshA(val id: Int): IsPackable


        result = registeredValB.kj(UnregisteredFreshA(2)) // UnregisteredFreshA packer is missing
        assertFalse(result.isSuccess, "Packing should fail if A's packer is not found.")
        assertTrue(result is PackingAttempt.PackingFailure, "Result should be PackingFailure.")
        val failure2 = result as PackingAttempt.PackingFailure
        assertTrue(failure2.reason.contains("Packer not found for type A (UnregisteredFreshA)"), "Failure reason should mention missing A packer. Was: ${failure2.reason}")

        // It's good practice to clean up temporary registrations if the registry is a shared static object.
        // However, PackerRegistry is an object, its state persists. For robust tests,
        // either use truly unique types per test or implement unregister.
        // For now, this test relies on UnregisteredFreshA being unique.
    }

    @Test
    fun testKj_Failure_DataTooLarge() {
        // Define types and packers that will result in total bits exceeding MAX_PACKED_SEGMENTS * PAYLOAD_BITS_PER_SEGMENT
        val maxPayloadTotal = MAX_PACKED_SEGMENTS * PAYLOAD_BITS_PER_SEGMENT

        val bitSizeB = PAYLOAD_BITS_PER_SEGMENT
        // Make bitSizeA just enough to push over the max limit
        val bitSizeA = maxPayloadTotal - bitSizeB + 1

        data class LargeDataB(val data: Long) : IsPackable
        class LargeDataPackerB : Packer<LargeDataB> {
            override fun pack(value: LargeDataB): ActualPackedBits = value.data
            override fun unpack(bits: ActualPackedBits): LargeDataB = LargeDataB(bits)
            override val bitSize: Int get() = bitSizeB
        }

        data class LargeDataA(val data: Long) : IsPackable
        class LargeDataPackerA : Packer<LargeDataA> {
            override fun pack(value: LargeDataA): ActualPackedBits = value.data
            override fun unpack(bits: ActualPackedBits): LargeDataA = LargeDataA(bits)
            override val bitSize: Int get() = bitSizeA // This packer might be > 64 bits, which current Packer<T> doesn't support in pack/unpack signatures
                                                       // So, ensure bitSizeA here is <= 64 for this test to be valid with current Packer<T>
        }

        // Re-evaluate bitSizeA to be valid for Packer<T> but still cause total to exceed limit
        val testBitSizeA = minOf(PAYLOAD_BITS_PER_SEGMENT, maxPayloadTotal - bitSizeB + 1) // Ensure individual packer is <= 63/64
        // And ensure bitSizeA + bitSizeB > maxPayloadTotal
        // Let bitSizeB = MAX_PACKED_SEGMENTS * PAYLOAD_BITS_PER_SEGMENT (e.g. 252)
        // Let bitSizeA = 1
        // This makes bitSizeB > 64, which current Packer cannot model.

        // Revised strategy for DataTooLarge:
        // PackerB reports bitSize that, when added to PackerA's bitSize, exceeds the limit.
        // Both PackerA.bitSize and PackerB.bitSize must be <= 64 (or PAYLOAD_BITS_PER_SEGMENT).
        val packerBSize = PAYLOAD_BITS_PER_SEGMENT // e.g. 63
        val packerASize = (MAX_PACKED_SEGMENTS * PAYLOAD_BITS_PER_SEGMENT) - packerBSize + 1 // e.g. (4*63) - 63 + 1 = 3*63 + 1 = 189 + 1 = 190
                                                                                          // This packerASize is > 63.
        // So, we need packers that report these sizes but still pack/unpack a Long.
        // The kj function will use these reported sizes for its calculation.

        class ReportingPackerB : Packer<LargeDataB> {
            override fun pack(value: LargeDataB): ActualPackedBits = value.data // Packs only a Long
            override fun unpack(bits: ActualPackedBits): LargeDataB = LargeDataB(bits)
            override val bitSize: Int get() = packerBSize
        }
        class ReportingPackerA : Packer<LargeDataA> {
            override fun pack(value: LargeDataA): ActualPackedBits = value.data // Packs only a Long
            override fun unpack(bits: ActualPackedBits): LargeDataA = LargeDataA(bits)
            override val bitSize: Int get() = packerASize // Reports a large size
        }

        // This test relies on kj trusting the reported bitSize, even if packer itself can't pack that many bits.
        // If packerASize > 64, the current Packer<LargeDataA>.pack() would be problematic if it tried to handle > 64 bits.
        // However, kj uses packer.bitSize for calculation *before* calling pack.
        // The issue is that ReportingPackerA cannot truthfully pack 190 bits into one Long.
        // This test is more about kj's calculation based on reported sizes.
        // For a more "honest" test, all constituent packers must be valid.

        // Let's use multiple small items that sum up, but this is for kjSeries or multiple kj calls.
        // The current kj(A,B) takes two items. If A or B itself is "too large" (its packer.bitSize > limit),
        // that's a Packer design issue, not kj's spilling limit.
        // kj's spilling limit is about sum(A.bitSize, B.bitSize).

        // Valid test for "DataTooLarge" for kj(A,B):
        // A.bitSize and B.bitSize are individually valid (e.g., <= 63).
        // Their sum exceeds MAX_PACKED_SEGMENTS * PAYLOAD_BITS_PER_SEGMENT.
        val pSize1 = MAX_PACKED_SEGMENTS * PAYLOAD_BITS_PER_SEGMENT - 10 // e.g. 252 - 10 = 242. Not good if > 63
        val pSize2 = 11

        // Let pSize1 be (MAX_PACKED_SEGMENTS -1) * PAYLOAD_BITS_PER_SEGMENT + 1. (e.g. 3*63+1 = 190) - still > 63
        // Let pSize2 be PAYLOAD_BITS_PER_SEGMENT -1 . (e.g. 62)

        // This test needs two packers whose bitSizes are individually <= 63 (or 64)
        // but whose sum is > MAX_PACKED_SEGMENTS * PAYLOAD_BITS_PER_SEGMENT.

        // Example: MAX_PACKED_SEGMENTS = 4 (max payload 252)
        // PackerB.bitSize = 60
        // PackerA.bitSize = 60
        // ... up to 4 packers of 60 bits = 240. This fits.
        // Need PackerA.bitSize + PackerB.bitSize > 252.
        // If PackerA.bitSize = 63, PackerB.bitSize = 63, sum = 126 (fits in 2 segments)
        // If PackerA.bitSize = 63*2 = 126 -> this packer is not valid by itself.

        // The `kj` operator's "data too large" refers to `totalPayloadBits` vs `MAX_PACKED_SEGMENTS`.
        // `totalPayloadBits` is `packerA.bitSize + packerB.bitSize`.
        // So we need `packerA.bitSize + packerB.bitSize > MAX_PACKED_SEGMENTS * PAYLOAD_BITS_PER_SEGMENT`.
        // And `packerA.bitSize <= PAYLOAD_BITS_PER_SEGMENT`, `packerB.bitSize <= PAYLOAD_BITS_PER_SEGMENT` (or <=64).
        // This implies `MAX_PACKED_SEGMENTS` must be 1 for this specific scenario to be easily testable with two such items.
        // If MAX_PACKED_SEGMENTS = 1 (max payload 63):
        //    PackerB.bitSize = 40
        //    PackerA.bitSize = 30 (sum = 70, > 63). This should fail.
        // Let's assume MAX_PACKED_SEGMENTS can be configured for testing or we test against current value (4).

        // If MAX_PACKED_SEGMENTS = 4 (max payload 252 bits):
        // We need packerA.bitSize + packerB.bitSize > 252.
        // This is only possible if at least one of packerA.bitSize or packerB.bitSize is > 126.
        // And Packer<T>.bitSize must be <= 64. This means this specific failure for kj(A,B)
        // (where A and B are individually valid packable types <=64 bits)
        // can only occur if MAX_PACKED_SEGMENTS < (64+64)/PAYLOAD_BITS_PER_SEGMENT = 128/63 approx 2.
        // If MAX_PACKED_SEGMENTS is 1 or 2, we can hit this. If it's 4, we can't with A,B <=64 bits each.

        // Let's test against current MAX_PACKED_SEGMENTS = 4.
        // To make totalPayloadBits > 4 * 63 = 252, with A and B each <= 63:
        // Max sum for A,B (each <=63) is 63+63 = 126. This is < 252.
        // So, this specific failure ("DataTooLarge" because sum > MAX_PACKED_SEGMENTS limit)
        // cannot be triggered if individual packers are limited to <=63 bits and MAX_PACKED_SEGMENTS >= 3.
        // It *can* be triggered if MAX_PACKED_SEGMENTS is 1 or 2.
        // E.g. If MAX_PACKED_SEGMENTS = 1 (max payload 63):
        //      B(32), A(32). Sum = 64. 64 > 63. Fails. This is a valid test.
        // The current code has MAX_PACKED_SEGMENTS = 4. The check in kj is:
        // `if (segmentsRequired > MAX_PACKED_SEGMENTS)`
        // `segmentsRequired = (totalPayloadBits + PAYLOAD_BITS_PER_SEGMENT - 1) / PAYLOAD_BITS_PER_SEGMENT`
        // If A=63, B=63, total=126. segmentsRequired = (126+63-1)/63 = 188/63 = 2.  2 <= 4. OK.

        // The "DataTooLarge" check in `kj` seems to be effective only if `packer.bitSize` itself can be very large.
        // But `Packer<T>.pack()` returns a single `Long`.
        // This means `packer.bitSize` should semantically be <= 64.
        // If so, `totalPayloadBits` for `kj(A,B)` is max 128.
        // `segmentsRequired` = (128 + 63 - 1)/63 = 190/63 = 3.
        // So, if MAX_PACKED_SEGMENTS is < 3, this could fail. Current is 4.

        // Conclusion: The "DataTooLarge" failure for `kj(A,B)` given current constraints (Packer.bitSize <=64, MAX_PACKED_SEGMENTS=4)
        // is unlikely to be triggered by the *sum* of bitSizes exceeding the *total capacity over MAX_PACKED_SEGMENTS*.
        // It *would* be triggered if a packer reported an immense bitSize, but that packer would be ill-defined.
        // The current `kj` implementation already checks `segmentsRequired > MAX_PACKED_SEGMENTS`.
        // This path is tested if we provide packers whose sum of `bitSize` leads to `segmentsRequired > MAX_PACKED_SEGMENTS`.
        // E.g. PackerA.bitSize = 60, PackerB.bitSize = 60. total=120. segReq=2.
        // E.g. PackerA.bitSize = 60 * 3 = 180. This packer is not valid.

        // Let's make a packer that *reports* a large size, even if it can't pack it.
        // This tests `kj`'s calculation logic.
        class OverReportingPacker(val reportedSize: Int) : Packer<LargeDataA> {
            override fun pack(value: LargeDataA): ActualPackedBits = 0L // Dummy pack
            override fun unpack(bits: ActualPackedBits): LargeDataA = LargeDataA(0L)
            override val bitSize: Int get() = reportedSize
        }

        val normalSize = 30
        val tooLargeSize = (MAX_PACKED_SEGMENTS * PAYLOAD_BITS_PER_SEGMENT) - normalSize + 1 // e.g. 4*63 - 30 + 1 = 252 - 30 + 1 = 223

        PackerRegistry.register(LargeDataB::class, ReportingPackerB()) // Uses packerBSize = 63
        PackerRegistry.register(LargeDataA::class, OverReportingPacker(tooLargeSize))


        val dataB = LargeDataB(1L) // Packer reports 63 bits
        val dataA = LargeDataA(1L) // Packer reports 223 bits
                                  // Total reported = 63 + 223 = 286 bits.
                                  // Segments required = (286 + 63 - 1) / 63 = 348 / 63 = 5.5 -> 6 if PAYLOAD_BITS_PER_SEGMENT = 63
                                  // Segments required = ceil(286/63) = ceil(4.53) = 5
                                  // (286 + 63 - 1) / 63 = 348 / 63 = 5 if integer division, should be 6.
                                  // segmentsRequired = (totalPayloadBits + PAYLOAD_BITS_PER_SEGMENT - 1) / PAYLOAD_BITS_PER_SEGMENT
                                  // (286 + 63 - 1) / 63 = 348 / 63 = 5. (Mistake here, 348/63 = 5 with remainder 33, so 6 segments)
                                  // (286 - 1) / 63 + 1 = 285 / 63 + 1 = 4 + 1 = 5. This is also common for ceiling.
                                  // Current formula: (286 + 63 - 1) / 63 = 348 / 63 = 5. (This is incorrect ceiling for positive integers)
                                  // Correct ceiling: (value + divisor - 1) / divisor for positive integers.
                                  // So, (286 + 63 - 1) / 63 = 348 / 63 = 5. This is correct for the formula.
                                  // Let's verify `kj`'s formula:
                                  // totalPayloadBits = 286. PAYLOAD_BITS_PER_SEGMENT = 63
                                  // segmentsRequired = (286 + 63 - 1) / 63 = 348 / 63 = 5.
                                  // This is actually `floor((totalPayloadBits - 1) / PAYLOAD_BITS_PER_SEGMENT) + 1`
                                  // Or, `(totalPayloadBits -1) / divisor + 1` for integers.
                                  // (286-1)/63 + 1 = 285/63 + 1 = 4+1 = 5.
                                  // This formula is correct for ceiling division.
                                  // So segmentsRequired = 5.
                                  // 5 > MAX_PACKED_SEGMENTS (4). So it should fail.

        val resultTooLarge = dataB.kj(dataA)
        assertFalse(resultTooLarge.isSuccess, "Packing should fail if total reported bitSize exceeds max capacity. Error: ${resultTooLarge.getErrorOrNull()}")
        assertTrue(resultTooLarge is PackingAttempt.PackingFailure)
        val failure = resultTooLarge as PackingAttempt.PackingFailure
        assertTrue(failure.reason.contains("Data too large for packing"), "Failure reason should mention data too large. Was: ${failure.reason}")
        assertTrue(failure.reason.contains("requires 5 segments"), "Reason should state 5 segments. Was: ${failure.reason}")
    }

    @Test
    fun testJkOperator() {
        // Define a simple IsPackable type for A
        data class PackableVal(val id: Int) : IsPackable
        // B can be any type, e.g., String for this test

        val valA = PackableVal(100)
        val valB = "TestString"

        // Call A.jk(B)
        val resultJoin: Join<String, PackableVal> = valA.jk(valB)

        // Verify the types and values
        // resultJoin.a should be valB (String)
        // resultJoin.b should be valA (PackableVal)

        assertEquals(valB, resultJoin.a, "The 'a' component of Join<B,A> should be the original B value.")
        assertSame(valA, resultJoin.b, "The 'b' component of Join<B,A> should be the original A value (receiver).")

        // Test with another set of types
        val intValA = 12345 // Int is not IsPackable by default, but jk takes <A : IsPackable>
                           // To test with Int as A, we'd need Int to be IsPackable or change jk's constraint.
                           // Let's use our PackableVal again for A, and Int for B.

        val packableA = PackableVal(200)
        val intB = 9876

        val resultJoin2: Join<Int, PackableVal> = packableA.jk(intB)
        assertEquals(intB, resultJoin2.a, "Join.a should be Int for Join<Int, PackableVal>")
        assertSame(packableA, resultJoin2.b, "Join.b should be PackableVal for Join<Int, PackableVal>")
    }
}
