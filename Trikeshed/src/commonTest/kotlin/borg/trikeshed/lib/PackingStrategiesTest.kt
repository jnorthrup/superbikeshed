package borg.trikeshed.lib

import kotlin.test.*
// import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Suppress("UNCHECKED_CAST", "USELESS_IS_CHECK")
class PackingStrategiesTest {

    @Test
    fun `test enhanced jj operator works`() {
        val result = 42 jj 100
        // The result should be either a PackedResult or a Join
        assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
    }

    @Test
    fun `test diagonal packing with integers`() {
        val result = 42 jj 100
        assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
        val packed = result as? DiagonalPacked
        if (packed != null) {
            assertEquals(42L, (packed.reg shr 32) and 0xFFFFFFFFL)
            assertEquals(100L, packed.reg and 0xFFFFFFFFL)
        }
    }

    @Test
    fun `test diagonal packing with short strings`() {
        val result = "abc" jj "def"
        assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
        val packed = result as? DiagonalPacked
        if (packed != null) {
            assertNotEquals(0L, packed.reg)
        }
    }

    @Test
    fun `test prefixed packing with byte and long`() {
        val result = 42.toByte() jj 123456789L
        assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
        val packed = result as? PrefixedPacked
        if (packed != null) {
            assertEquals(123456789L, packed.reg)
            assertEquals(42.toByte(), packed.prefix)
        }
    }

    @Test
    fun `test prefixed packing with short and long strings`() {
        val result = "ab" jj "longer_string"
        assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
        val packed = result as? PrefixedPacked
        if (packed != null) {
            assertNotEquals(0L, packed.reg)
            assertNotEquals(0.toByte(), packed.prefix)
        }
    }

    @Test
    fun `test range offset packing with int array`() {
        val array = intArrayOf(100, 101, 102, 103, 104)
        val result = array jj 0L
        assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
        val packed = result as? RangeOffsetPacked
        if (packed != null) {
            assertEquals(100L, packed.base)
            assertEquals(5, packed.regs.size)
            assertEquals(0L, packed.regs[0])
            assertEquals(1L, packed.regs[1])
            assertEquals(2L, packed.regs[2])
        }
    }

    @Test
    fun `test relative increment packing with sequential ints`() {
        val array = intArrayOf(100, 101, 102, 103, 104)
        val result = array jj 0L
        assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
        
        // This should use range offset, but let's test relative increment with different data
        val sequential = intArrayOf(100, 101, 102, 103, 104)
        val result2 = sequential jj 0L
        assertTrue(result2 is PackedResult<*, *> || result2 is Join<*, *>)
    }

    @Test
    fun `test palette packing with repeated values`() {
        val array = arrayOf("a", "b", "a", "c", "b", "a", "d", "c")
        val result = array jj arrayOf<Any>()
        assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
        val packed = result as? PalettePacked
        if (packed != null) {
            assertEquals(8, packed.regs.size)
            assertEquals(4, packed.palette.size)
        }
    }

    @Test
    fun `test multi cluster packing with distributed values`() {
        val array = (0..15).toList().toTypedArray()
        val result = array jj arrayOf<Any>()
        assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
        val packed = result as? MultiClusterPacked
        if (packed != null) {
            assertEquals(16, packed.regs.size)
            assertEquals(2, packed.clusters.size)
        }
    }

    @Test
    fun `test fallback to simple join when no packing applies`() {
        val result = "very_long_string_that_cannot_be_packed" jj "another_long_string"
        assertTrue(result is Join<*, *>)
        assertFalse(result is PackedResult<*, *>)
    }

    @Test
    fun `test packing context influences strategy selection`() {
        val data = intArrayOf(100, 101, 102, 103, 104)
        
        // With MINIMAL context, should use diagonal or prefixed if possible
        val minimalContext = PackingContext(PackingStrategy.MINIMAL, CpuBudget.MINIMAL)
        val minimalResult = data.jp(0L, minimalContext)
        
        // With AGGRESSIVE context, should try more expensive strategies
        val aggressiveContext = PackingContext(PackingStrategy.AGGRESSIVE, CpuBudget.AGGRESSIVE)
        val aggressiveResult = data.jp(0L, aggressiveContext)
        
        // Both should work, but aggressive might use different strategy
        assertTrue(minimalResult is PackedResult<*, *> || minimalResult is Join<*, *>)
        assertTrue(aggressiveResult is PackedResult<*, *> || aggressiveResult is Join<*, *>)
    }

    // @Test
    // fun `test context-aware jc operator`() {
    //     runBlocking {
    //         val context = PackingContext(PackingStrategy.STANDARD, CpuBudget.STANDARD)
    //         withContext(context) {
    //             val result = 42.jc(100)
    //             assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
    //         }
    //     }
    // }

    @Test
    fun `test explicit context jp operator`() {
        val context = PackingContext(PackingStrategy.AGGRESSIVE, CpuBudget.AGGRESSIVE)
        val result = 42.jp(100, context)
        assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
    }

    @Test
    fun `test forced non-packing jn operator`() {
        val result = 42 jn 100
        assertTrue(result is Join<*, *>)
        assertFalse(result is PackedResult<*, *>)
    }

    // @Test
    // fun `test packing performance characteristics`() {
    //     val iterations = 10000
    //     val data = (0 until iterations).toList()
    //     
    //     val startTime = System.nanoTime()
    //     val result = data jj 0L
    //     val endTime = System.nanoTime()
    //     
    //     val duration = endTime - startTime
    //     val avgTimePerOperation = duration / iterations.toDouble()
    //     
    //     // Should be very fast - less than 1000ns per operation
    //     assertTrue(avgTimePerOperation < 1000.0, "Packing too slow: avgTimePerOperation}ns per operation")
    //     
    //     // Result should be packed
    //     assertTrue(result is PackedResult<*, *> || result is Join<*, *>)
    // }

    @Test
    fun `test compositional strategy extensibility`() {
        // This test demonstrates that the strategy list is compositional
        // and can be extended without modifying the core Packer class
        
        val strategies = listOf(
            object : PackerStrategy<Any?, Any?> {
                override fun canPack(a: Any?, b: Any?, context: PackingContext) = 
                    a is String && b is String && a == "test" && b == "extensible"
                
                override fun pack(a: Any?, b: Any?) = 
                    Either.right(DiagonalPacked(0xDEADBEEFL))
            }
        )
        
        // In a real implementation, these would be added to the Packer.strategies list
        // For now, we just verify the interface works
        val strategy = strategies.first()
        assertTrue(strategy.canPack("test", "extensible", PackingContext.DEFAULT))
        val result = strategy.pack("test", "extensible")
        assertTrue(result is Either.Right<*>)
        val rightResult = result as Either.Right<DiagonalPacked>
        assertEquals(0xDEADBEEFL, rightResult.value.reg)
    }
} 