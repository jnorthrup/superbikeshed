package borg.trikeshed.integration

import borg.trikeshed.lib.*
import kotlin.test.*
import kotlin.time.*

/**
 * Basic MetaSeries functionality test that works with minimal dependencies.
 * Tests core MetaSeries<A,T> patterns and type safety.
 */
class MetaSeriesBasicTest {
    
    @Test
    fun `MetaSeries universal foundation works`() {
        // MetaSeries<A, T> = Join<A, (A) -> T>
        val timeSeries: MetaSeries<Int, String> = 10 j { hour -> "Event at hour $hour" }
        
        assertEquals(10, timeSeries.a) // index space size
        assertEquals("Event at hour 5", timeSeries.b(5)) // access function
    }
    
    @Test
    fun `Series realm specialization`() {
        // Series<T> = MetaSeries<Int, T>
        val numbers: Series<Double> = 5 j { i -> i * 3.14 }
        
        assertEquals(5, numbers.size)
        assertEquals(0.0, numbers[0])
        assertEquals(3.14, numbers[1], 0.001)
        assertEquals(6.28, numbers[2], 0.001)
    }
    
    @Test
    fun `Twin realm specialization`() {
        // Twin<T> = MetaSeries<Boolean, T>
        val coordinate: Twin<Float> = true j { if (it) 42.0f else 37.0f }
        
        assertEquals(42.0f, coordinate.b(true))  // x
        assertEquals(37.0f, coordinate.b(false)) // y
    }
    
    @Test
    fun `Shape as Series of Int`() {
        // Shape = Series<Int>
        val matrixShape: Shape = 3 j { dim ->
            when (dim) {
                0 -> 4  // rows
                1 -> 5  // columns
                2 -> 2  // depth
                else -> 1
            }
        }
        
        assertEquals(3, matrixShape.size) // 3D
        assertEquals(4, matrixShape[0])   // rows
        assertEquals(5, matrixShape[1])   // columns
        assertEquals(2, matrixShape[2])   // depth
    }
    
    @Test
    fun `Tensor realm with Shape index`() {
        // Tensor<T> = MetaSeries<Shape, T>
        val shape: Shape = 2 j { if (it == 0) 3 else 4 } // 3x4 matrix
        val matrix: Tensor<Int> = shape j { coords ->
            coords[0] * 10 + coords[1] // row*10 + col
        }
        
        assertEquals(shape, matrix.a) // Shape preserved
        
        // Access tensor elements
        val coord1: Shape = 2 j { if (it == 0) 1 else 2 } // [1, 2]
        assertEquals(12, matrix.b(coord1)) // 1*10 + 2 = 12
        
        val coord2: Shape = 2 j { if (it == 0) 2 else 3 } // [2, 3]
        assertEquals(23, matrix.b(coord2)) // 2*10 + 3 = 23
    }
    
    @Test
    fun `Series2 with Join elements`() {
        // Series2<A,B> = MetaSeries<Int, Join<A,B>>
        val keyValues: Series2<String, Double> = 4 j { i ->
            "key$i" j (i * 2.5)
        }
        
        assertEquals(4, keyValues.size)
        
        val first = keyValues[0]
        assertEquals("key0", first.a)
        assertEquals(0.0, first.b)
        
        val second = keyValues[1] 
        assertEquals("key1", second.a)
        assertEquals(2.5, second.b)
    }
    
    @Test
    fun `Series α transformation operator`() {
        val base: Series<Int> = 5 j { it + 1 } // [1, 2, 3, 4, 5]
        val doubled: Series<Int> = base α { it * 2 } // [2, 4, 6, 8, 10]
        
        assertEquals(5, doubled.size)
        assertEquals(2, doubled[0])
        assertEquals(4, doubled[1])
        assertEquals(6, doubled[2])
        assertEquals(8, doubled[3])
        assertEquals(10, doubled[4])
    }
    
    @Test
    fun `Play materialization for standard library`() {
        val series: Series<String> = 6 j { i -> "item-$i" }
        
        // Use play to materialize for std lib operations
        val filtered = series.play.filter { it.contains("2") || it.contains("4") }
        val mapped = filtered.map { it.uppercase() }
        
        assertEquals(listOf("ITEM-2", "ITEM-4"), mapped)
    }
    
    @Test
    fun `Functional composition with nested transformations`() {
        // Create Series of Shapes (each shape is Series<Int>)
        val shapes: Series<Shape> = 3 j { shapeIndex ->
            val dimensions = shapeIndex + 1 // 1D, 2D, 3D
            dimensions j { dim -> dim + 2 } // [2], [2,3], [2,3,4]
        }
        
        assertEquals(3, shapes.size)
        
        // Test 1D shape
        val shape1D = shapes[0]
        assertEquals(1, shape1D.size)
        assertEquals(2, shape1D[0])
        
        // Test 2D shape
        val shape2D = shapes[1]
        assertEquals(2, shape2D.size)
        assertEquals(2, shape2D[0])
        assertEquals(3, shape2D[1])
        
        // Test 3D shape
        val shape3D = shapes[2]
        assertEquals(3, shape3D.size)
        assertEquals(2, shape3D[0])
        assertEquals(3, shape3D[1])
        assertEquals(4, shape3D[2])
        
        // Transform shapes using nested α operations
        val scaledShapes = shapes α { shape ->
            shape α { dimension -> dimension * 3 }
        }
        
        val scaledShape2D = scaledShapes[1]
        assertEquals(6, scaledShape2D[0]) // 2 * 3 = 6
        assertEquals(9, scaledShape2D[1]) // 3 * 3 = 9
    }
    
    @Test
    fun `Performance with larger datasets`() {
        val startTime = TimeSource.Monotonic.markNow()
        
        // Create large MetaSeries
        val largeData: Series<Long> = 50_000 j { i -> i.toLong() * 7 }
        
        // Transform
        val processed = largeData α { it + 1 }
        
        // Access some elements to verify lazy evaluation works
        assertEquals(1L, processed[0])     // (0 * 7) + 1 = 1
        assertEquals(36L, processed[5])    // (5 * 7) + 1 = 36
        assertEquals(71L, processed[10])   // (10 * 7) + 1 = 71
        
        val elapsedTime = startTime.elapsedNow()
        println("Processed ${largeData.size} elements in $elapsedTime")
        
        // Should be very fast due to lazy evaluation
        assertTrue(elapsedTime < 1.seconds, "Processing should be fast due to lazy evaluation")
    }
    
    @Test
    fun `Type safety prevents realm mixing`() {
        val series: Series<String> = 3 j { "item$it" }
        val twin: Twin<String> = true j { if (it) "yes" else "no" }
        
        // These have different index types (Int vs Boolean)
        // so they maintain realm separation
        assertEquals("item1", series[1])    // Int index
        assertEquals("yes", twin.b(true))   // Boolean index
        assertEquals("no", twin.b(false))   // Boolean index
        
        // Verify they're both based on MetaSeries foundation
        assertTrue(series is MetaSeries<*, *>)
        assertTrue(twin is MetaSeries<*, *>)
    }
    
    @Test
    fun `Join universal composition operator`() {
        // Basic Join construction
        val point = 42 j 37
        assertEquals(42, point.a)
        assertEquals(37, point.b)
        
        // Destructuring
        val (x, y) = point
        assertEquals(42, x)
        assertEquals(37, y)
        
        // Nested Join compositions
        val nested = (1 j 2) j (3 j 4)
        assertEquals(1, nested.a.a)
        assertEquals(2, nested.a.b) 
        assertEquals(3, nested.b.a)
        assertEquals(4, nested.b.b)
    }
    
    @Test
    fun `MetaSeries realm separation example`() {
        // Different realms use different index types
        val intRealm: Series<String> = 5 j { "pos$it" }          // Int realm
        val boolRealm: Twin<String> = true j { if (it) "T" else "F" } // Boolean realm
        val shapeRealm: Tensor<String> = (2 j { it + 1 }) j { coords -> 
            "cell[${coords[0]},${coords[1]}]" 
        } // Shape realm
        
        // Each realm has type-safe access patterns
        assertEquals("pos2", intRealm[2])                    // Int index
        assertEquals("T", boolRealm.b(true))                 // Boolean index
        assertEquals("F", boolRealm.b(false))                // Boolean index
        
        val coord: Shape = 2 j { if (it == 0) 1 else 2 }
        assertEquals("cell[1,2]", shapeRealm.b(coord))       // Shape index
        
        // All are MetaSeries but with different realms
        assertTrue(intRealm is MetaSeries<Int, String>)
        assertTrue(boolRealm is MetaSeries<Boolean, String>)
        assertTrue(shapeRealm is MetaSeries<Shape, String>)
    }
}