package borg.trikeshed.lib

import kotlin.test.*

class TensorMathTest {

    @Test
    fun testTensorAddition() {
        val tensor1 = TensorIndexed(3) { it.toDouble() }
        val tensor2 = TensorIndexed(3) { (it + 1).toDouble() }
        
        val result = tensor1 + tensor2
        assertEquals(1.0, result(0), 0.001)
        assertEquals(3.0, result(1), 0.001)
        assertEquals(5.0, result(2), 0.001)
    }

    @Test
    fun testTensorSubtraction() {
        val tensor1 = TensorIndexed(3) { (it + 2).toDouble() }
        val tensor2 = TensorIndexed(3) { it.toDouble() }
        
        val result = tensor1 - tensor2
        assertEquals(2.0, result(0), 0.001)
        assertEquals(2.0, result(1), 0.001)
        assertEquals(2.0, result(2), 0.001)
    }

    @Test
    fun testTensorMultiplication() {
        val tensor1 = TensorIndexed(3) { (it + 1).toDouble() }
        val tensor2 = TensorIndexed(3) { 2.0 }
        
        val result = tensor1 * tensor2
        assertEquals(2.0, result(0), 0.001)
        assertEquals(4.0, result(1), 0.001)
        assertEquals(6.0, result(2), 0.001)
    }

    @Test
    fun testTensorDivision() {
        val tensor1 = TensorIndexed(3) { (it + 2).toDouble() }
        val tensor2 = TensorIndexed(3) { 2.0 }
        
        val result = tensor1 / tensor2
        assertEquals(1.0, result(0), 0.001)
        assertEquals(1.5, result(1), 0.001)
        assertEquals(2.0, result(2), 0.001)
    }

    @Test
    fun testScalarAddition() {
        val tensor = TensorIndexed(3) { it.toDouble() }
        val result = tensor + 5.0
        
        assertEquals(5.0, result(0), 0.001)
        assertEquals(6.0, result(1), 0.001)
        assertEquals(7.0, result(2), 0.001)
    }

    @Test
    fun testScalarSubtraction() {
        val tensor = TensorIndexed(3) { (it + 5).toDouble() }
        val result = tensor - 2.0
        
        assertEquals(3.0, result(0), 0.001)
        assertEquals(4.0, result(1), 0.001)
        assertEquals(5.0, result(2), 0.001)
    }

    @Test
    fun testScalarMultiplication() {
        val tensor = TensorIndexed(3) { (it + 1).toDouble() }
        val result = tensor * 3.0
        
        assertEquals(3.0, result(0), 0.001)
        assertEquals(6.0, result(1), 0.001)
        assertEquals(9.0, result(2), 0.001)
    }

    @Test
    fun testScalarDivision() {
        val tensor = TensorIndexed(3) { (it + 3).toDouble() }
        val result = tensor / 2.0
        
        assertEquals(1.5, result(0), 0.001)
        assertEquals(2.0, result(1), 0.001)
        assertEquals(2.5, result(2), 0.001)
    }

    @Test
    fun testUnaryMinus() {
        val tensor = TensorIndexed(3) { (it + 1).toDouble() }
        val result = -tensor
        
        assertEquals(-1.0, result(0), 0.001)
        assertEquals(-2.0, result(1), 0.001)
        assertEquals(-3.0, result(2), 0.001)
    }

    @Test
    fun testSum() {
        val tensor = TensorIndexed(4) { (it + 1).toDouble() }
        val result = tensor.sum()
        assertEquals(10.0, result, 0.001) // 1 + 2 + 3 + 4 = 10
    }

    @Test
    fun testMean() {
        val tensor = TensorIndexed(4) { (it + 1).toDouble() }
        val result = tensor.mean()
        assertEquals(2.5, result, 0.001) // (1 + 2 + 3 + 4) / 4 = 2.5
    }

    @Test
    fun testSqrt() {
        val tensor = TensorIndexed(3) { (it + 1).toDouble() }
        val result = tensor.sqrt()
        
        assertEquals(1.0, result(0), 0.001) // sqrt(1) = 1
        assertEquals(1.414, result(1), 0.001) // sqrt(2) ≈ 1.414
        assertEquals(1.732, result(2), 0.001) // sqrt(3) ≈ 1.732
    }

    @Test
    fun testShapeMismatchThrowsException() {
        val tensor1 = TensorIndexed(3) { it.toDouble() }
        val tensor2 = TensorIndexed(4) { it.toDouble() }
        
        assertFailsWith<IllegalArgumentException> {
            tensor1 + tensor2
        }
    }

    @Test
    fun testSumRequires1DTensor() {
        val tensor = TensorCursor(2, 2) { row, col -> (row + col).toDouble() }
        
        assertFailsWith<IllegalArgumentException> {
            tensor.sum()
        }
    }

    @Test
    fun testMeanRequires1DTensor() {
        val tensor = TensorCursor(2, 2) { row, col -> (row + col).toDouble() }
        
        assertFailsWith<IllegalArgumentException> {
            tensor.mean()
        }
    }

    @Test
    fun testEmptyTensorMean() {
        val tensor = TensorIndexed(0) { it.toDouble() }
        val result = tensor.mean()
        assertEquals(0.0, result, 0.001)
    }
} 