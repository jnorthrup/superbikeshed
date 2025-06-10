package borg.trikeshed.core

import kotlin.math.abs // For potential delta comparisons, though not used yet.
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
// import kotlin.test.assertNotEquals // Not used yet
// import kotlin.test.assertNull // Not used yet
// import kotlin.test.assertNotNull // Not used yet

// Imports from borg.trikeshed.core
import borg.trikeshed.core.Series
import borg.trikeshed.core.Tensor
import borg.trikeshed.core.emptySeries
import borg.trikeshed.core.j // For Series construction: size j { accessor }
import borg.trikeshed.core.m
import borg.trikeshed.core.d
import borg.trikeshed.core._l
import borg.trikeshed.core._v
import borg.trikeshed.core.s_
import borg.trikeshed.core.z
import borg.trikeshed.core.nz
import borg.trikeshed.core.select
import borg.trikeshed.core.readableUnitsToNumber
import borg.trikeshed.core.humanReadableByteCountIEC
import borg.trikeshed.core.humanReadableByteCountSI

// Network order utilities
import borg.trikeshed.core.networkOrderSetIntAt
import borg.trikeshed.core.networkOrderGetIntAt
import borg.trikeshed.core.networkOrderSetLongAt
import borg.trikeshed.core.networkOrderGetLongAt
import borg.trikeshed.core.networkOrderSetFloatAt
import borg.trikeshed.core.networkOrderGetFloatAt
import borg.trikeshed.core.networkOrderSetDoubleAt
import borg.trikeshed.core.networkOrderGetDoubleAt
import borg.trikeshed.core.networkOrderSetShortAt
import borg.trikeshed.core.networkOrderGetShortAt
import borg.trikeshed.core.networkOrderSetCharAt
import borg.trikeshed.core.networkOrderGetCharAt

// Base64 encoding
import borg.trikeshed.core.base64Encode

// Tensor constructors from core if not directly using Join interface
// These are internal in TrikeShedCore.kt, so for testing we either:
// 1. Make them public (not ideal if they are meant to be internal)
// 2. Test Tensor through other public APIs that construct Tensors
// 3. Replicate minimal versions of them here if they are simple enough.
// The helper functions createTestTensor1D and createTestTensor2D already use
// internal TensorSeries and TensorCursor from TrikeShedCore.kt.
// This requires them to be accessible. If they are internal, tests from a different module might not access them.
// However, commonTest is in the same module as commonMain for Kotlin multiplatform projects.
// So internal members of borg.trikeshed.core should be accessible.
import borg.trikeshed.core.TensorSeries
import borg.trikeshed.core.TensorCursor


class TrikeShedCoreTests {

    // --- Helper Functions for Test Data ---

    private fun <T> createTestSeries(vararg elements: T): Series<T> {
        if (elements.isEmpty()) return emptySeries()
        val list = elements.toList()
        return list.size j { i -> list[i] }
    }

    // Tensor helper functions (createTestTensor1D, createEmptyTensor1D, createTestTensor2D) removed
    // as their tests are moved to core.TrikeShedTensorOperationsTests.kt


    // --- Test Cases ---

    // == Tensor Shorthands ==
    // Tests for Tensor shorthands (testTensorM, testTensorD, testTensorLast, testTensorValues, testTensorSum)
    // were moved to core.TrikeShedTensorOperationsTests.kt

    // == Series Shorthands ==

    @Test
    fun testSeriesM() {
        val series = createTestSeries("a", "b", "c")
        val mappedSeries = series.m { it + "!" }
        assertEquals(3, mappedSeries.size)
        assertEquals("a!", mappedSeries[0])
        assertEquals("b!", mappedSeries[1])
        assertEquals("c!", mappedSeries[2])

        val emptyS = createTestSeries<String>()
        val mappedEmptyS = emptyS.m { it + "!" }
        assertEquals(0, mappedEmptyS.size)
    }

    @Test
    fun testSeriesD() {
        val series = createTestSeries(10, 20, 30, 40)

        val dropped1 = series.d(1)
        assertEquals(3, dropped1.size)
        assertEquals(20, dropped1[0])
        assertEquals(30, dropped1[1])
        assertEquals(40, dropped1[2])

        val dropped0 = series.d(0)
        assertEquals(4, dropped0.size)
        assertEquals(10, dropped0[0])

        val droppedAll = series.d(4)
        assertEquals(0, droppedAll.size)

        val droppedMore = series.d(5)
        assertEquals(0, droppedMore.size)

        val emptyS = createTestSeries<Int>()
        val droppedEmptyS = emptyS.d(1)
        assertEquals(0, droppedEmptyS.size)
    }

    @Test
    fun testSeriesLast() {
        val series = createTestSeries("x", "y", "z")
        assertEquals("z", series._l)

        val singleElementSeries = createTestSeries(100)
        assertEquals(100, singleElementSeries._l)

        val emptySeries = createTestSeries<Int>()
        assertFailsWith<NoSuchElementException>("L_EMPTY_SERIES") { emptySeries._l }
    }

    @Test
    fun testSeriesValues() {
        val series = createTestSeries(1, 2, 3)
        val values = series._v.toList()
        assertEquals(listOf(1, 2, 3), values)

        val emptySeries = createTestSeries<String>()
        assertTrue(emptySeries._v.toList().isEmpty())
    }

    @Test
    fun testSeriesSum() {
        val intSeries = createTestSeries(5, 10, 15) // Sum = 30
        assertEquals(30.0, intSeries.s_())

        val floatSeries = createTestSeries(0.5f, 0.25f, 1.25f) // Sum = 2.0
        assertEquals(2.0, floatSeries.s_().toDouble(), 0.0001) // Added delta for float comparison

        val emptyS = createTestSeries<Double>()
        assertEquals(0.0, emptyS.s_())
    }

    // == Numeric Boolean Checks ==
    @Test
    fun testNumericZNZ() {
        // Int
        assertTrue(0.z)
        assertFalse(0.nz)
        assertFalse(1.z)
        assertTrue(1.nz)
        assertFalse((-1).z)
        assertTrue((-1).nz)

        // Long
        assertTrue(0L.z)
        assertFalse(0L.nz)
        assertFalse(100L.z)
        assertTrue(100L.nz)

        // Double
        assertTrue(0.0.z)
        assertFalse(0.0.nz)
        assertFalse(0.0001.z)
        assertTrue(0.0001.nz)
        assertFalse((-0.5).z)
        assertTrue((-0.5).nz)
        // Note: Double precision can be tricky, but for exact 0.0 it should be fine.
        // assertTrue((0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001).nz)

        // Float
        assertTrue(0.0f.z)
        assertFalse(0.0f.nz)
        assertFalse(1.0f.z)
        assertTrue(1.0f.nz)

        // Byte
        assertTrue(0.toByte().z)
        assertFalse(0.toByte().nz)
        assertFalse(1.toByte().z)
        assertTrue(1.toByte().nz)

        // Short
        assertTrue(0.toShort().z)
        assertFalse(0.toShort().nz)
        assertFalse(1.toShort().z)
        assertTrue(1.toShort().nz)
    }

    // == Ternary Select ==
    @Test
    fun testBooleanSelect() {
        assertEquals("Yes", true.select("Yes" to "No"))
        assertEquals("No", false.select("Yes" to "No"))

        assertEquals(100, true.select(100 to 200))
        assertEquals(200, false.select(100 to 200))

        val list1 = listOf(1,2)
        val list2 = listOf(3,4)
        assertEquals(list1, true.select(list1 to list2)) // Uses referential equality for lists if not overridden
        assertEquals(list2, false.select(list1 to list2))
    }

    // == Human Readable Numbers ==
    @Test
    fun testReadableUnitsToNumber() {
        assertEquals(1024.0, "1k".readableUnitsToNumber())
        assertEquals(1000.0, "1k".readableUnitsToNumber(decimal = true))
        assertEquals(1024.0 * 1024.0, "1M".readableUnitsToNumber()) // Case-insensitivity
        assertEquals(1000.0 * 1000.0, "1m".readableUnitsToNumber(decimal = true))
        assertEquals(2.5 * 1024.0 * 1024.0 * 1024.0, "2.5g".readableUnitsToNumber())
        assertEquals(2.5 * 1000.0 * 1000.0 * 1000.0, "2.5G".readableUnitsToNumber(decimal = true))
        assertEquals(1024.0 * 1024.0 * 1024.0 * 1024.0, "1T".readableUnitsToNumber())
        assertEquals(1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0, "1P".readableUnitsToNumber())
        assertEquals(1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0, "1E".readableUnitsToNumber())

        assertEquals(500.0, "500".readableUnitsToNumber()) // No suffix
        assertEquals(500.0, "500b".readableUnitsToNumber()) // 'b' suffix
        assertEquals(1024.0, "1kb".readableUnitsToNumber()) // kb suffix

        assertEquals(0.0, "0k".readableUnitsToNumber())
        assertEquals(0.0, "0".readableUnitsToNumber())

        assertFailsWith<NumberFormatException> { "gb".readableUnitsToNumber() } // Invalid number part
        assertFailsWith<NumberFormatException> { "1.k".readableUnitsToNumber() } // Invalid format
        assertFailsWith<NumberFormatException> { "1.2.3k".readableUnitsToNumber() }
    }

    @Test
    fun testHumanReadableByteCount() {
        assertEquals("0 B", 0L.humanReadableByteCountIEC)
        assertEquals("1023 B", 1023L.humanReadableByteCountIEC)
        assertEquals("1.0 KiB", 1024L.humanReadableByteCountIEC)
        assertEquals("1.5 KiB", 1536L.humanReadableByteCountIEC)
        assertEquals("1024 KiB", (1024L * 1024L - 1).humanReadableByteCountIEC) // Check this based on unitizer logic
        assertEquals("1.0 MiB", (1024L * 1024L).humanReadableByteCountIEC)
        assertEquals("1.25 MiB", (1024L * 1024L * 1.25).toLong().humanReadableByteCountIEC)
        assertEquals("1.0 GiB", (1024L * 1024L * 1024L).humanReadableByteCountIEC)
        assertEquals("-1.0 KiB", (-1024L).humanReadableByteCountIEC)


        assertEquals("0 B", 0L.humanReadableByteCountSI)
        assertEquals("999 B", 999L.humanReadableByteCountSI)
        assertEquals("1.0 KB", 1000L.humanReadableByteCountSI)
        assertEquals("1.5 KB", 1500L.humanReadableByteCountSI)
        assertEquals("1.0 MB", (1000L * 1000L).humanReadableByteCountSI)
        assertEquals("1.25 MB", (1000L * 1000L * 1.25).toLong().humanReadableByteCountSI)
        assertEquals("1.0 GB", (1000L * 1000L * 1000L).humanReadableByteCountSI)
        assertEquals("-1.0 KB", (-1000L).humanReadableByteCountSI)

        val teraSI = 1000L * 1000 * 1000 * 1000
        assertEquals("1.0 TB", teraSI.humanReadableByteCountSI)
        val tebiIEC = 1024L * 1024 * 1024 * 1024
        assertEquals("1.0 TiB", tebiIEC.humanReadableByteCountIEC)
    }

    // == Network Order Utilities ==
    // Helper for byte array comparison in tests if needed, or use direct element check.
    // private fun assertByteArrayEquals(expected: ByteArray, actual: ByteArray, message: String? = null) {
    //     assertTrue(expected.contentEquals(actual), message)
    // }

    @Test
    fun testNetworkOrderInt() {
        val arr = ByteArray(4)
        val value = 0x1A2B3C4D
        arr.networkOrderSetIntAt(0, value)
        assertEquals(0x1A.toByte(), arr[0])
        assertEquals(0x2B.toByte(), arr[1])
        assertEquals(0x3C.toByte(), arr[2])
        assertEquals(0x4D.toByte(), arr[3])
        assertEquals(value, arr.networkOrderGetIntAt(0))
    }

    @Test
    fun testNetworkOrderLong() {
        val arr = ByteArray(8)
        val value = 0x1122334455667788L
        arr.networkOrderSetLongAt(0, value)
        assertEquals(0x11.toByte(), arr[0])
        assertEquals(0x88.toByte(), arr[7])
        assertEquals(value, arr.networkOrderGetLongAt(0))
    }

    @Test
    fun testNetworkOrderFloat() {
        val arr = ByteArray(4)
        val value = 123.456f
        arr.networkOrderSetFloatAt(0, value)
        assertEquals(value, arr.networkOrderGetFloatAt(0), 0.00001f) // Delta for float

        val specificFloat = Float.fromBits(0x42f6e979) // approx 123.456f
        arr.networkOrderSetFloatAt(0, specificFloat)
        assertEquals(specificFloat.toBits(), arr.networkOrderGetIntAt(0))
    }

    @Test
    fun testNetworkOrderDouble() {
        val arr = ByteArray(8)
        val value = 9876.54321
        arr.networkOrderSetDoubleAt(0, value)
        assertEquals(value, arr.networkOrderGetDoubleAt(0), 0.000000001) // Delta for double

        val specificDouble = Double.fromBits(0x40c388876064c000L) // approx 9876.54321
        arr.networkOrderSetDoubleAt(0, specificDouble)
        assertEquals(specificDouble.toBits(), arr.networkOrderGetLongAt(0))
    }

    @Test
    fun testNetworkOrderShort() {
        val arr = ByteArray(2)
        val value: Short = 0xABCD.toShort()
        arr.networkOrderSetShortAt(0, value)
        assertEquals(0xAB.toByte(), arr[0])
        assertEquals(0xCD.toByte(), arr[1])
        assertEquals(value, arr.networkOrderGetShortAt(0))
    }

    @Test
    fun testNetworkOrderChar() {
        val arr = ByteArray(2)
        val value: Char = 'Z' // Unicode 0x005A
        arr.networkOrderSetCharAt(0, value)
        assertEquals(0x00.toByte(), arr[0])
        assertEquals(0x5A.toByte(), arr[1])
        assertEquals(value, arr.networkOrderGetCharAt(0))
    }

    // == Base64 Encoding ==
    // These tests will rely on the actual platform implementation of base64Encode.
    @Test
    fun testBase64EncodeString() {
        assertEquals("TWFu", base64Encode("Man"))
        assertEquals("bGlnaHQgd29yay4=", base64Encode("light work."))
        assertEquals("bGlnaHQgd29yaw==", base64Encode("light work"))
        assertEquals("bGlnaHQgd29y", base64Encode("light wor"))
        assertEquals("", base64Encode("")) // Empty string
    }

    @Test
    fun testBase64EncodeByteArray() {
        assertEquals("TWFu", base64Encode("Man".encodeToByteArray()))
        assertEquals("AQID", base64Encode(byteArrayOf(1, 2, 3)))
        assertEquals("", base64Encode(byteArrayOf()))
    }
}
