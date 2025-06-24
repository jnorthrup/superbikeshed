package borg.trikeshed.lib

import kotlin.test.*

class CoreTypesTest {
    
    @Test
    fun testJoinBasicComposition() {
        val join = 42 j "hello"
        assertEquals(42, join.a)
        assertEquals("hello", join.b)
    }
    
    @Test
    fun testJoinDestructuring() {
        val join = 1.5 j true
        val (first, second) = join
        assertEquals(1.5, first)
        assertEquals(true, second)
    }
    
    @Test
    fun testJoinPairInterop() {
        val join = "key" j "value"
        val pair = join.pair
        assertEquals(Pair("key", "value"), pair)
    }
    
    @Test
    fun testIndexedConstruction() {
        val indexed = 5 j { i -> i * 2 }
        assertEquals(5, indexed.size)
        assertEquals(0, indexed[0])
        assertEquals(2, indexed[1])
        assertEquals(4, indexed[2])
        assertEquals(6, indexed[3])
        assertEquals(8, indexed[4])
    }
    
    @Test
    fun testIndexedTransformation() {
        val numbers = 3 j { i -> i + 1 }  // [1, 2, 3]
        val doubled = numbers α { it * 2 }   // [2, 4, 6]
        
        assertEquals(3, doubled.size)
        assertEquals(2, doubled[0])
        assertEquals(4, doubled[1])
        assertEquals(6, doubled[2])
    }
    
    @Test
    fun testIndexedPlay() {
        val indexed = 4 j { i -> "item$i" }
        val list = indexed.play.toList()
        
        assertEquals(listOf("item0", "item1", "item2", "item3"), list)
    }
    
    @Test
    fun testIndexedIterator() {
        val indexed = 3 j { i -> i * i }
        val results = mutableListOf<Int>()
        
        for (value in indexed) {
            results.add(value)
        }
        
        assertEquals(listOf(0, 1, 4), results)
    }
    
    @Test
    fun testTwinAlias() {
        val twin: Twin<String> = "left" j "right"
        assertEquals("left", twin.a)
        assertEquals("right", twin.b)
    }
    
    @Test
    fun testIndexed2Projections() {
        val pairs = 3 j { i -> (i * 2) j (i * 3) }  // [(0,0), (2,3), (4,6)]
        
        val leftProjection = pairs.left
        val rightProjection = pairs.right
        
        assertEquals(3, leftProjection.size)
        assertEquals(3, rightProjection.size)
        
        assertEquals(0, leftProjection[0])
        assertEquals(2, leftProjection[1])
        assertEquals(4, leftProjection[2])
        
        assertEquals(0, rightProjection[0])
        assertEquals(3, rightProjection[1])
        assertEquals(6, rightProjection[2])
    }
    
    @Test
    fun testEmptyIndexed() {
        val empty = emptyIndex<String>()
        assertEquals(0, empty.size)
        
        assertFailsWith<IndexOutOfBoundsException> {
            empty[0]
        }
    }
    
    @Test
    fun testCollectionConversions() {
        val list = listOf(1, 2, 3)
        val indexed = list.toIdx()
        
        assertEquals(3, indexed.size)
        assertEquals(1, indexed[0])
        assertEquals(2, indexed[1])
        assertEquals(3, indexed[2])
        
        val backToList = indexed.toList()
        assertEquals(list, backToList)
    }
    
    @Test
    fun testArrayConversions() {
        val intArray = intArrayOf(10, 20, 30)
        val indexed = intArray.toIdx()
        
        assertEquals(3, indexed.size)
        assertEquals(10, indexed[0])
        assertEquals(20, indexed[1])
        assertEquals(30, indexed[2])
        
        val backToArray = indexed.toArray()
        assertContentEquals(intArray, backToArray)
    }
    
    @Test
    fun testShapeConstruction() {
        val shape: Shape = 3 j { i -> i + 1 }  // [1, 2, 3]
        assertEquals(3, shape.size)
        assertEquals(1, shape[0])
        assertEquals(2, shape[1])
        assertEquals(3, shape[2])
    }
    
    @Test
    fun testTensorConstruction() {
        val matrixShape: Shape = 2 j { i -> if (i == 0) 2 else 3 }  // [2, 3]
        val tensor: Tensor<Double> = matrixShape j { coords -> 
            coords[0] * 3.0 + coords[1] 
        }
        
        assertEquals(matrixShape, tensor.a)
        // Access would require coordinate construction
    }
    
    @Test
    fun testEitherType() {
        val left: Either<String, Int> = Either.left("error")
        val right: Either<String, Int> = Either.right(42)
        
        assertTrue(left is Either.Left)
        assertTrue(right is Either.Right)
        
        assertEquals("error", (left as Either.Left).value)
        assertEquals(42, (right as Either.Right).value)
    }
    
    @Test
    fun testColumnMeta() {
        val meta = ColumnMeta.create("name", "String")
        assertEquals("name", meta.a)
        assertEquals(String::class, meta.b)
        
        val intMeta = ColumnMeta.create("age", "Int")
        assertEquals("age", intMeta.a)
        assertEquals(Int::class, intMeta.b)
    }
    
    @Test
    fun testMetaSeriesComposition() {
        // Test that Indexed is properly a MetaSeries specialization
        val indexed: Indexed<String> = 2 j { i -> "value$i" }
        val metaSeries: MetaSeries<Int, String> = indexed
        
        assertEquals(2, metaSeries.a)
        assertEquals("value0", metaSeries.b(0))
        assertEquals("value1", metaSeries.b(1))
    }
}