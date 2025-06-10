package core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.NoSuchElementException // Required for _l tests

// Imports from core.* for Tensor and its operations
import borg.trikeshed.lib.j // core.Tensor uses lib.j
// Note: core.Tensor is typealias Join<IntArray, (IntArray)->T>
// We will use core.TensorSeries, core.TensorCursor for construction which are defined in TrikeShedTensorOperations.kt
// and NuclearCore.kt. These are effectively core.Tensor constructors.

// Explicit imports for the functions being tested, assuming they are top-level extensions in core package
// (as they were just added to TrikeShedTensorOperations.kt)
// No, they are members of Tensor typealias, so direct call like tensor.m()

class TrikeShedTensorOperationsTests {

    // --- Helper Functions for Test Data (adapted for core.Tensor) ---

    // These helpers will use core.TensorSeries and core.TensorCursor which are assumed
    // to be available and correctly typed for core.Tensor (i.e. using borg.trikeshed.lib.Join)

    private fun <T> createTestTensor1D(vararg elements: T): Tensor<T> {
        if (elements.isEmpty()) return TensorSeries(0) { throw IndexOutOfBoundsException("Accessing empty 1D tensor") }
        val list = elements.toList()
        return TensorSeries(list.size) { i -> list[i] }
    }

    private fun <T> createEmptyTensor1D(): Tensor<T> = TensorSeries(0) { throw IndexOutOfBoundsException("Accessing empty tensor") }

    private fun <T> createTestTensor2D(rows: Int, cols: Int, vararg elements: T): Tensor<T> {
        val expectedSize = rows * cols
        if (elements.size != expectedSize) {
            if (elements.isEmpty() && expectedSize == 0) {
                 return TensorCursor(rows, cols) { _, _ -> throw IndexOutOfBoundsException("Accessing empty 2D tensor") }
            }
            throw IllegalArgumentException("Number of elements (${elements.size}) does not match rows*cols ($expectedSize) for 2D tensor")
        }
        if (expectedSize == 0) {
            return TensorCursor(rows, cols) { _, _ -> throw IndexOutOfBoundsException("Accessing empty 2D tensor with zero dimensions") }
        }
        val list = elements.toList()
        return TensorCursor(rows, cols) { r, c -> list[r * cols + c] }
    }

    // --- Test Cases (copied and adapted from TrikeShedCoreTests.kt) ---

    @Test
    fun testTensorM() {
        val tensor = createTestTensor1D(1, 2, 3)
        val mappedTensor = tensor.m { it * 2 } // core.Tensor.m
        assertEquals(3, mappedTensor.totalSize)
        assertEquals(2, mappedTensor(0))
        assertEquals(4, mappedTensor(1))
        assertEquals(6, mappedTensor(2))

        val emptyTensor = createEmptyTensor1D<Int>()
        val mappedEmpty = emptyTensor.m { it * 2 }
        assertEquals(0, mappedEmpty.totalSize)
    }

    @Test
    fun testTensorD() {
        val tensor = createTestTensor1D(1, 2, 3, 4, 5)

        val dropped2 = tensor.d(2) // core.Tensor.d
        assertEquals(3, dropped2.totalSize, "Tensor D: Dropped 2 size incorrect")
        assertEquals(3, dropped2(0), "Tensor D: Dropped 2 element 0 incorrect")
        assertEquals(4, dropped2(1), "Tensor D: Dropped 2 element 1 incorrect")
        assertEquals(5, dropped2(2), "Tensor D: Dropped 2 element 2 incorrect")
        assertEquals(1, dropped2.rank, "Tensor D: Dropped tensor should be rank 1")

        val dropped0 = tensor.d(0)
        assertEquals(5, dropped0.totalSize)
        assertEquals(1, dropped0(0))

        val droppedAll = tensor.d(5)
        assertEquals(0, droppedAll.totalSize)

        val droppedMore = tensor.d(10)
        assertEquals(0, droppedMore.totalSize)

        val emptyTensor = createEmptyTensor1D<Int>()
        val droppedFromEmpty = emptyTensor.d(2)
        assertEquals(0, droppedFromEmpty.totalSize)

        val tensor2D = createTestTensor2D(2, 2, 10, 20, 30, 40) // Linear: 10, 20, 30, 40
        val dropped2DResult = tensor2D.d(1)
        assertEquals(3, dropped2DResult.totalSize, "Tensor D: 2D dropped 1 size incorrect")
        assertEquals(20, dropped2DResult(0), "Tensor D: 2D dropped 1 element 0 incorrect")
        assertEquals(30, dropped2DResult(1), "Tensor D: 2D dropped 1 element 1 incorrect")
        assertEquals(40, dropped2DResult(2), "Tensor D: 2D dropped 1 element 2 incorrect")
        assertEquals(1, dropped2DResult.rank, "Tensor D: 2D dropped tensor should be rank 1")
    }

    @Test
    fun testTensorLast() {
        val tensor = createTestTensor1D(1, 2, 10)
        assertEquals(10, tensor._l) // core.Tensor._l

        val tensor2D = createTestTensor2D(2, 2, 1, 2, 3, 44)
        assertEquals(44, tensor2D._l)

        val emptyTensor = createEmptyTensor1D<Int>()
        assertFailsWith<NoSuchElementException>("L_EMPTY_TENSOR") { emptyTensor._l }
    }

    @Test
    fun testTensorValues() {
        val tensor = createTestTensor1D(5, 6, 7)
        val values = tensor._v.toList() // core.Tensor._v
        assertEquals(listOf(5, 6, 7), values)

        val tensor2D = createTestTensor2D(2, 2, 10, 20, 30, 40)
        val values2D = tensor2D._v.toList()
        assertEquals(listOf(10, 20, 30, 40), values2D)

        val emptyTensor = createEmptyTensor1D<Int>()
        assertTrue(emptyTensor._v.toList().isEmpty())
    }

    @Test
    fun testTensorSum() {
        val intTensor = createTestTensor1D(1, 2, 3, 4)
        assertEquals(10.0, intTensor.s_()) // core.Tensor.s_

        val doubleTensor = createTestTensor1D(1.5, 2.5, 3.0)
        assertEquals(7.0, doubleTensor.s_())

        val emptyTensor = createEmptyTensor1D<Int>()
        assertEquals(0.0, emptyTensor.s_())

        val tensor2D = createTestTensor2D(2,2, 1,2,3,10)
        assertEquals(16.0, tensor2D.s_())
    }
}
