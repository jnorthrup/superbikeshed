package borg.trikeshed.lib

import kotlin.test.*

class TensorConstructionTest {

    @Test
    fun testTensorSeries() {
        val tensor = TensorSeries(3) { it * 2 }
        assertEquals(3, tensor.shape[0])
        assertEquals(0, tensor(0))
        assertEquals(2, tensor(1))
        assertEquals(4, tensor(2))
    }

    @Test
    fun testTensorCursor() {
        val tensor = TensorCursor(2, 3) { row, col -> row * 10 + col }
        assertEquals(2, tensor.shape[0])
        assertEquals(3, tensor.shape[1])
        assertEquals(0, tensor(0, 0))
        assertEquals(1, tensor(0, 1))
        assertEquals(2, tensor(0, 2))
        assertEquals(10, tensor(1, 0))
        assertEquals(11, tensor(1, 1))
        assertEquals(12, tensor(1, 2))
    }

    @Test
    fun testListToTensor() {
        val list = listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6)
        )
        val tensor = list.toTensor()
        assertEquals(2, tensor.shape[0])
        assertEquals(3, tensor.shape[1])
        assertEquals(1, tensor(0, 0))
        assertEquals(2, tensor(0, 1))
        assertEquals(3, tensor(0, 2))
        assertEquals(4, tensor(1, 0))
        assertEquals(5, tensor(1, 1))
        assertEquals(6, tensor(1, 2))
    }

    @Test
    fun testListToTensor1D() {
        val list = listOf(1, 2, 3, 4)
        val tensor = list.toTensor1D()
        assertEquals(4, tensor.shape[0])
        assertEquals(1, tensor(0))
        assertEquals(2, tensor(1))
        assertEquals(3, tensor(2))
        assertEquals(4, tensor(3))
    }

    @Test
    fun testFillTensor() {
        val tensor = fillTensor(intArrayOf(2, 3), 42)
        assertEquals(2, tensor.shape[0])
        assertEquals(3, tensor.shape[1])
        assertEquals(42, tensor(0, 0))
        assertEquals(42, tensor(0, 1))
        assertEquals(42, tensor(0, 2))
        assertEquals(42, tensor(1, 0))
        assertEquals(42, tensor(1, 1))
        assertEquals(42, tensor(1, 2))
    }

    @Test
    fun testGenerateTensor() {
        val tensor = generateTensor(intArrayOf(2, 2)) { coords -> coords[0] * 10 + coords[1] }
        assertEquals(2, tensor.shape[0])
        assertEquals(2, tensor.shape[1])
        assertEquals(0, tensor(0, 0))
        assertEquals(1, tensor(0, 1))
        assertEquals(10, tensor(1, 0))
        assertEquals(11, tensor(1, 1))
    }

    @Test
    fun testEmptyListToTensor() {
        val emptyList = emptyList<List<Int>>()
        val tensor = emptyList.toTensor()
        assertEquals(0, tensor.shape[0])
        assertEquals(0, tensor.shape[1])
    }

    @Test
    fun testEmptyListToTensor1D() {
        val emptyList = emptyList<Int>()
        val tensor = emptyList.toTensor1D()
        assertEquals(0, tensor.shape[0])
    }

    @Test
    fun testTensorSeriesWithNegativeSize() {
        assertFailsWith<IllegalArgumentException> {
            TensorSeries(-1) { it }
        }
    }

    @Test
    fun testTensorCursorWithNegativeDimensions() {
        assertFailsWith<IllegalArgumentException> {
            TensorCursor(-1, 2) { _, _ -> 0 }
        }
        assertFailsWith<IllegalArgumentException> {
            TensorCursor(2, -1) { _, _ -> 0 }
        }
    }

    @Test
    fun testListToTensorWithInconsistentRows() {
        val inconsistentList = listOf(
            listOf(1, 2),
            listOf(3, 4, 5) // Different size
        )
        assertFailsWith<IllegalArgumentException> {
            inconsistentList.toTensor()
        }
    }

    @Test
    fun testTensorRank() {
        val series = TensorSeries(3) { it }
        assertEquals(1, series.rank)
        
        val cursor = TensorCursor(2, 3) { _, _ -> 0 }
        assertEquals(2, cursor.rank)
    }

    @Test
    fun testTensorTotalSize() {
        val series = TensorSeries(3) { it }
        assertEquals(3, series.totalSize)
        
        val cursor = TensorCursor(2, 3) { _, _ -> 0 }
        assertEquals(6, cursor.totalSize)
    }

    @Test
    fun testTensorAccessor() {
        val tensor = TensorSeries(3) { it * 2 }
        val accessor = tensor.accessor
        assertEquals(0, accessor(intArrayOf(0)))
        assertEquals(2, accessor(intArrayOf(1)))
        assertEquals(4, accessor(intArrayOf(2)))
    }
} 