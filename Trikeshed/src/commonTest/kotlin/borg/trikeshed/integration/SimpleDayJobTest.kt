package borg.trikeshed.integration

import borg.trikeshed.lib.*
import kotlin.test.*
import kotlin.time.*

/**
 * Simplified version of DayJobIntegrationTest that focuses on working MetaSeries patterns.
 * This test demonstrates the core TrikeShed concepts without complex type dependencies.
 */
class SimpleDayJobTest {
    
    @Test
    fun `MetaSeries basic functionality`() {
        // Create a simple MetaSeries using the canonical pattern
        val data: MetaSeries<Int, String> = 5 j { i -> "item-$i" }
        
        assertEquals(5, data.a)
        assertEquals("item-3", data.b(3))
    }
    
    @Test
    fun `Series realm specialization works`() {
        // Series is MetaSeries<Int, T>
        val numbers: Indexed<Int> = 10 j { i -> i * i }
        
        assertEquals(10, numbers.size)
        assertEquals(9, numbers[3]) // 3 * 3 = 9
        assertEquals(25, numbers[5]) // 5 * 5 = 25
    }
    
    @Test
    fun `Twin realm specialization works`() {
        // Twin is MetaSeries<Boolean, T>
        val coordinate: Twin<Int> = true j { if (it) 42 else 37 }
        
        assertEquals(42, coordinate.b(true))   // x coordinate
        assertEquals(37, coordinate.b(false))  // y coordinate
    }
    
    @Test
    fun `Series transformation with α operator`() {
        val numbers: Indexed<Int> = 5 j { i -> i + 1 }
        val doubled: Indexed<Int> = numbers α { it * 2 }
        
        assertEquals(5, doubled.size)
        assertEquals(2, doubled[0])  // (0 + 1) * 2 = 2
        assertEquals(4, doubled[1])  // (1 + 1) * 2 = 4
        assertEquals(6, doubled[2])  // (2 + 1) * 2 = 6
    }
    
    @Test
    fun `Series2 with Join elements`() {
        // Series2<A,B> is MetaSeries<Int, Join<A,B>>
        val pairs: Indexed2<String, Int> = 3 j { i ->
            "key-$i" j (i * 10)
        }
        
        assertEquals(3, pairs.size)
        val firstPair = pairs[0]
        assertEquals("key-0", firstPair.a)
        assertEquals(0, firstPair.b)
        
        val secondPair = pairs[1]
        assertEquals("key-1", secondPair.a)
        assertEquals(10, secondPair.b)
    }
    
    @Test
    fun `Shape as Series of Int`() {
        // Shape is Series<Int>
        val matrixShape: Shape = 3 j { i ->
            when (i) {
                0 -> 5  // rows
                1 -> 4  // columns  
                2 -> 2  // depth
                else -> 1
            }
        }
        
        assertEquals(3, matrixShape.size) // 3D shape
        assertEquals(5, matrixShape[0])   // 5 rows
        assertEquals(4, matrixShape[1])   // 4 columns
        assertEquals(2, matrixShape[2])   // 2 depth
        
        // Calculate volume using Series operations
        var volume = 1
        for (i in 0 until matrixShape.size) {
            volume *= matrixShape[i]
        }
        assertEquals(40, volume) // 5 * 4 * 2 = 40
    }
    
    @Test
    fun `Tensor as MetaSeries with Shape index`() {
        // Tensor<T> is MetaSeries<Shape, T>
        val matrixShape: Shape = 2 j { if (it == 0) 3 else 4 } // 3x4 matrix
        val matrix: Tensor<Double> = matrixShape j { coords ->
            coords[0] * 4.0 + coords[1] // row * 4 + col
        }
        
        assertEquals(matrixShape, matrix.a) // Shape is preserved
        
        // Access tensor elements
        val coord1: Shape = 2 j { if (it == 0) 1 else 2 } // [1, 2]
        val value1 = matrix.b(coord1)
        assertEquals(6.0, value1) // 1 * 4 + 2 = 6
        
        val coord2: Shape = 2 j { if (it == 0) 2 else 3 } // [2, 3]  
        val value2 = matrix.b(coord2)
        assertEquals(11.0, value2) // 2 * 4 + 3 = 11
    }
    
    @Test
    fun `Performance test with larger dataset`() {
        val startTime = TimeSource.Monotonic.markNow()
        
        // Create a larger series for performance testing
        val largeData: Indexed<Int> = 100_000 j { i -> i * 2 }
        
        // Transform the data
        val processed = largeData α { it + 1 }
        
        // Verify some values
        assertEquals(100_000, processed.size)
        assertEquals(1, processed[0])     // (0 * 2) + 1 = 1
        assertEquals(101, processed[50])  // (50 * 2) + 1 = 101
        assertEquals(1001, processed[500]) // (500 * 2) + 1 = 1001
        
        val elapsedTime = startTime.elapsedNow()
        println("Processed ${largeData.size} elements in $elapsedTime")
        
        // Should be very fast due to lazy evaluation
        assertTrue(elapsedTime < 1.seconds, "Processing should be fast due to lazy evaluation")
    }
    
    @Test
    fun `Play materialization for standard library integration`() {
        val indexed: Indexed<String> = 5 j { i -> "value-$i" }
        
        // Use play to materialize for standard library operations
        val materialized = indexed.play.map { it.uppercase() }.filter { it.contains("2") }
        
        assertEquals(listOf("VALUE-2"), materialized)
    }
    
    @Test
    fun `Complex composition example`() {
        // Demonstrate complex composition using MetaSeries patterns
        
        // Create a series of shapes (each shape is itself a Series<Int>)
        val shapes: Indexed<Shape> = 3 j { i ->
            val dimensions = i + 2 // 2D, 3D, 4D
            dimensions j { dim -> dim + 1 } // [1,2], [1,2,3], [1,2,3,4]
        }
        
        assertEquals(3, shapes.size)
        
        // First shape: 2D [1,2]
        val shape2D = shapes[0]
        assertEquals(2, shape2D.size)
        assertEquals(1, shape2D[0])
        assertEquals(2, shape2D[1])
        
        // Second shape: 3D [1,2,3] 
        val shape3D = shapes[1]
        assertEquals(3, shape3D.size)
        assertEquals(1, shape3D[0])
        assertEquals(2, shape3D[1])
        assertEquals(3, shape3D[2])
        
        // Transform shapes using functional composition
        val scaledShapes = shapes α { shape ->
            shape α { dimension -> dimension * 2 }
        }
        
        val scaledShape2D = scaledShapes[0]
        assertEquals(2, scaledShape2D[0]) // 1 * 2 = 2
        assertEquals(4, scaledShape2D[1]) // 2 * 2 = 4
    }
}