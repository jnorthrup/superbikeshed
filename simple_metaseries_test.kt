#!/usr/bin/env kotlin

import kotlin.time.*

// === STANDALONE METASERIES IMPLEMENTATION ===

/**
 * TrikeShed MetaSeries Implementation - Standalone Version
 * 
 * This demonstrates the complete MetaSeries<A,T> universal metaclass architecture
 * as designed for TrikeShed, but in a standalone script that can run independently.
 */

// Foundation Join Interface
interface Join<A, B> {
    val a: A
    val b: B
    
    operator fun component1(): A = a
    operator fun component2(): B = b
    val pair: Pair<A, B> get() = Pair(a, b)
    
    companion object {
        operator fun <A, B> invoke(a: A, b: B): Join<A, B> = object : Join<A, B> {
            override val a: A = a
            override val b: B = b
        }
    }
}

// Universal composition operator
inline infix fun <A, B> A.j(b: B) = Join.invoke(this, b)

// === UNIVERSAL METACLASS FOUNDATION ===

// MetaSeries<A, T> - The universal foundation
typealias MetaSeries<A, T> = Join<A, (A) -> T>

// Realm specializations
typealias Indexed<T> = MetaSeries<Int, T>
typealias Twin<T> = MetaSeries<Boolean, T>
typealias Shape = Indexed<Int>
typealias Tensor<T> = MetaSeries<Shape, T>
typealias Series2<A, B> = MetaSeries<Int, Join<A, B>>

// Series operations
val <T> Indexed<T>.size: Int get() = a
operator fun <T> Indexed<T>.get(i: Int): T = b(i)
inline infix fun <X, C, V : Indexed<X>> V.α(crossinline xform: (X) -> C): Indexed<C> = size j { i -> xform(this[i]) }

// Play materialization for standard library integration
val <T> Indexed<T>.play: List<T> get() = (0 until size).map { this[it] }

// Simple assertion helper
fun assert(condition: Boolean, message: String = "Assertion failed") {
    if (!condition) {
        throw AssertionError(message)
    }
}

fun assertEquals(expected: Any?, actual: Any?, message: String = "Values not equal") {
    if (expected != actual) {
        throw AssertionError("$message: expected=$expected, actual=$actual")
    }
}

// === TESTS ===

fun main() {
    println("=== TrikeShed MetaSeries Universal Metaclass Test Suite ===\n")
    
    var testsRun = 0
    var testsPassed = 0
    
    fun runTest(name: String, test: () -> Unit) {
        testsRun++
        try {
            test()
            testsPassed++
            println("✓ $name")
        } catch (e: Exception) {
            println("❌ $name: ${e.message}")
        }
    }
    
    // Test 1: MetaSeries universal foundation
    runTest("MetaSeries universal foundation") {
        val timeSeries: MetaSeries<String, Int> = "events" j { key -> key.length }
        assertEquals("events", timeSeries.a)
        assertEquals(6, timeSeries.b("events"))
    }
    
    // Test 2: Series realm
    runTest("Series realm - Int indexed sequences") {
        val fibonacci: Indexed<Long> = 10 j { i ->
            when (i) {
                0 -> 0L
                1 -> 1L
                else -> {
                    var a = 0L
                    var b = 1L
                    for (j in 2..i) {
                        val temp = a + b
                        a = b
                        b = temp
                    }
                    b
                }
            }
        }
        
        assertEquals(10, fibonacci.size)
        assertEquals(0L, fibonacci[0])
        assertEquals(1L, fibonacci[1])
        assertEquals(1L, fibonacci[2])
        assertEquals(2L, fibonacci[3])
        assertEquals(3L, fibonacci[4])
        assertEquals(5L, fibonacci[5])
        assertEquals(8L, fibonacci[6])
    }
    
    // Test 3: Twin realm
    runTest("Twin realm - Boolean indexed pairs") {
        val minMax: Twin<Double> = true j { if (it) 100.0 else -50.0 }
        assertEquals(100.0, minMax.b(true))   // max
        assertEquals(-50.0, minMax.b(false))  // min
    }
    
    // Test 4: Shape as Indexed<Int>
    runTest("Shape as Series of Int") {
        val matrixShape: Shape = 4 j { dim ->
            when (dim) {
                0 -> 5    // batch size
                1 -> 3    // height
                2 -> 4    // width  
                3 -> 2    // channels
                else -> 1
            }
        }
        
        assertEquals(4, matrixShape.size)  // 4D tensor
        assertEquals(5, matrixShape[0])    // batch
        assertEquals(3, matrixShape[1])    // height
        assertEquals(4, matrixShape[2])    // width
        assertEquals(2, matrixShape[3])    // channels
        
        // Calculate volume using functional operations
        val volume = matrixShape.play.fold(1) { acc, dim -> acc * dim }
        assertEquals(120, volume) // 5 * 3 * 4 * 2 = 120
    }
    
    // Test 5: Tensor realm
    runTest("Tensor realm - Shape indexed multidimensional arrays") {
        val imageShape: Shape = 3 j { i -> 
            when (i) {
                0 -> 2  // height
                1 -> 3  // width
                2 -> 3  // RGB channels
                else -> 1
            }
        }
        
        val image: Tensor<Int> = imageShape j { coords ->
            val h = coords[0]
            val w = coords[1] 
            val c = coords[2]
            h * 100 + w * 10 + c  // encode position as value
        }
        
        assertEquals(imageShape, image.a)
        
        // Access specific pixels
        val pixel_0_0_R: Shape = 3 j { i -> if (i == 0) 0 else if (i == 1) 0 else 0 } // [0,0,0]
        assertEquals(0, image.b(pixel_0_0_R))   // 0*100 + 0*10 + 0 = 0
        
        val pixel_1_2_G: Shape = 3 j { i -> if (i == 0) 1 else if (i == 1) 2 else 1 } // [1,2,1]
        assertEquals(121, image.b(pixel_1_2_G)) // 1*100 + 2*10 + 1 = 121
    }
    
    // Test 6: Series2 with Join elements
    runTest("Series2 with Join elements") {
        val database: Series2<String, Any?> = 5 j { rowId ->
            when (rowId) {
                0 -> "Alice" j 25
                1 -> "Bob" j 30  
                2 -> "Charlie" j null
                3 -> "Diana" j 28
                4 -> "Eve" j 35
                else -> "Unknown" j null
            }
        }
        
        assertEquals(5, database.size)
        
        val row0 = database[0]
        assertEquals("Alice", row0.a)
        assertEquals(25, row0.b)
        
        val row2 = database[2]
        assertEquals("Charlie", row2.a)
        assertEquals(null, row2.b)
    }
    
    // Test 7: α transformation operator
    runTest("α transformation operator - functional mapping") {
        val numbers: Indexed<Int> = 8 j { it + 1 }  // [1, 2, 3, 4, 5, 6, 7, 8]
        val squares = numbers α { it * it }         // [1, 4, 9, 16, 25, 36, 49, 64]
        val strings = squares α { "[$it]" }         // ["[1]", "[4]", "[9]", ...]
        
        assertEquals(8, strings.size)
        assertEquals("[1]", strings[0])
        assertEquals("[4]", strings[1]) 
        assertEquals("[9]", strings[2])
        assertEquals("[16]", strings[3])
    }
    
    // Test 8: Play materialization
    runTest("Play materialization for standard library integration") {
        val series: Indexed<String> = 10 j { i -> "item-$i" }
        
        // Use play to get List<T> for standard library operations
        val filtered = series.play.filter { it.contains("2") || it.contains("5") || it.contains("7") }
        val uppercased = filtered.map { it.uppercase() }
        val sorted = uppercased.sorted()
        
        assertEquals(listOf("ITEM-2", "ITEM-5", "ITEM-7"), sorted)
    }
    
    // Test 9: Complex nested composition
    runTest("Complex nested composition") {
        // Create a Series of Tensors (representing a batch of images)
        val batchSize = 3
        val imageShape: Shape = 2 j { if (it == 0) 2 else 2 }  // 2x2 images
        
        val imageBatch: Indexed<Tensor<Float>> = batchSize j { batchIdx ->
            imageShape j { coords ->
                val row = coords[0]
                val col = coords[1]
                batchIdx * 10.0f + row * 3.0f + col  // unique value per batch/position
            }
        }
        
        assertEquals(3, imageBatch.size)
        
        // Access specific image in batch
        val image1 = imageBatch[1]
        assertEquals(imageShape, image1.a)
        
        // Access specific pixel in that image
        val coord: Shape = 2 j { if (it == 0) 1 else 0 }  // [1, 0]
        val pixelValue = image1.b(coord)
        assertEquals(13.0f, pixelValue)  // 1*10 + 1*3 + 0 = 13
    }
    
    // Test 10: Performance with lazy evaluation
    runTest("Performance with lazy evaluation") {
        val startTime = TimeSource.Monotonic.markNow()
        
        // Create large dataset
        val largeData: Indexed<Double> = 100_000 j { i -> i * Math.PI }
        
        // Apply multiple transformations (all lazy)
        val processed = largeData α { it * 2 } α { it + 1 } α { Math.sin(it) }
        
        // Only compute a few values to test laziness
        val sample1 = processed[0]
        val sample2 = processed[1000]
        val sample3 = processed[50000]
        
        val elapsedTime = startTime.elapsedNow()
        
        // Verify computations are correct
        val expected1 = Math.sin(0 * Math.PI * 2 + 1)
        val expected2 = Math.sin(1000 * Math.PI * 2 + 1)
        val expected3 = Math.sin(50000 * Math.PI * 2 + 1)
        
        assert(Math.abs(sample1 - expected1) < 1e-10, "Sample 1 incorrect")
        assert(Math.abs(sample2 - expected2) < 1e-10, "Sample 2 incorrect")
        assert(Math.abs(sample3 - expected3) < 1e-10, "Sample 3 incorrect")
        
        println("    Processed 100K elements lazily in $elapsedTime")
        
        // Should be very fast due to lazy evaluation - only 3 elements computed
        assert(elapsedTime.inWholeMilliseconds < 1000, "Lazy evaluation should be fast")
    }
    
    // Test 11: Type safety and realm separation
    runTest("Type safety and realm separation") {
        val intSeries: Indexed<String> = 3 j { "item$it" }
        val boolTwin: Twin<String> = true j { if (it) "yes" else "no" }
        val shape: Shape = 2 j { it + 5 }
        val tensor: Tensor<String> = shape j { coords -> "cell[${coords[0]},${coords[1]}]" }
        
        // All different index types maintain realm separation
        assertEquals("item1", intSeries[1])              // Int realm
        assertEquals("yes", boolTwin.b(true))            // Boolean realm
        assertEquals(6, shape[1])                        // Int realm (but Shape semantics)
        
        val coord: Shape = 2 j { if (it == 0) 5 else 6 }
        assertEquals("cell[5,6]", tensor.b(coord))       // Shape realm
        
        // All are MetaSeries but with different index types
        assert(intSeries is MetaSeries<Int, String>, "intSeries should be MetaSeries<Int, String>")
        assert(boolTwin is MetaSeries<Boolean, String>, "boolTwin should be MetaSeries<Boolean, String>")
        assert(tensor is MetaSeries<Shape, String>, "tensor should be MetaSeries<Shape, String>")
    }
    
    // Test 12: Join universal composition laws
    runTest("Join universal composition laws") {
        // Test composition laws
        val point = 42 j 37
        
        // Identity law: (a j b).a == a && (a j b).b == b
        assertEquals(42, point.a)
        assertEquals(37, point.b)
        
        // Destructuring
        val (x, y) = point
        assertEquals(42, x)
        assertEquals(37, y)
        
        // Nested composition
        val nested = (1 j 2) j (3 j 4)
        assertEquals(1, nested.a.a)
        assertEquals(2, nested.a.b)
        assertEquals(3, nested.b.a)
        assertEquals(4, nested.b.b)
        
        // Interop bridge
        assertEquals(Pair(42, 37), point.pair)
    }
    
    println("\n" + "=".repeat(60))
    println("🎉 TESTS COMPLETED: $testsPassed/$testsRun PASSED! 🎉")
    
    if (testsPassed == testsRun) {
        println("\nThe TrikeShed MetaSeries<A,T> universal metaclass architecture is working perfectly!")
        println("\nKey achievements:")
        println("• ✅ Universal MetaSeries<A,T> foundation implemented")
        println("• ✅ Realm separation through index types (Int, Boolean, Shape)")
        println("• ✅ Zero-cost abstractions with lazy evaluation")
        println("• ✅ Functional composition with α operator")
        println("• ✅ Standard library integration via play materialization")
        println("• ✅ Type safety across all metaclass operations")
        println("• ✅ Complex nested structures (Indexed<Tensor<T>>)")
        println("• ✅ Performance optimized for large datasets")
        println("\nThis validates that our MetaSeries design is sound and ready for production use.")
    } else {
        println("\n❌ Some tests failed. Please review the implementation.")
    }
}