package borg.trikeshed.collections

import kotlinx.coroutines.*
import kotlin.random.Random
import kotlin.test.*

class NonBlockingHashMapTest {

    // Helper to access MIN_SIZE for testing resize without reflection
    // This is a bit of a hack; ideally, the map would expose capacity or allow smaller initial for test
    private fun getMinSize(map: NonBlockingHashMap<*, *>): Int {
        // Assuming MIN_SIZE is 8 as per implementation.
        // If it were inspectable, we'd use that.
        return 8
    }

    @Test
    fun testEmptyMap() {
        val map = NonBlockingHashMap<String, String>()
        assertEquals(0, map.size)
        assertTrue(map.isEmpty())
        assertNull(map.get("test"))
        assertFalse(map.containsKey("test"))
        assertFalse(map.containsValue("testValue")) // relies on containsValue being implemented
    }

    @Test
    fun testPutAndGet_SingleThreaded() {
        val map = NonBlockingHashMap<Int, String>()
        assertNull(map.put(1, "one"))
        assertEquals("one", map.get(1))
        assertEquals(1, map.size)

        assertEquals("one", map.put(1, "uno")) // Overwrite
        assertEquals("uno", map.get(1))
        assertEquals(1, map.size)

        assertNull(map.put(2, "two"))
        assertEquals("two", map.get(2))
        assertEquals(2, map.size)
        assertTrue(map.containsKey(2))
    }

    @Test
    fun testRemove_SingleThreaded() {
        val map = NonBlockingHashMap<Int, String>()
        map.put(1, "one")
        map.put(2, "two")
        assertEquals(2, map.size)

        assertEquals("two", map.remove(2))
        assertEquals(1, map.size)
        assertNull(map.get(2))
        assertFalse(map.containsKey(2))

        assertNull(map.remove(5)) // Remove non-existent
        assertEquals(1, map.size)

        // Test removing then re-adding
        assertEquals("one", map.remove(1))
        assertNull(map.get(1))
        assertEquals(0, map.size)

        assertNull(map.put(1, "ONE_AGAIN"))
        assertEquals("ONE_AGAIN", map.get(1))
        assertEquals(1, map.size)
    }

    @Test
    fun testRemoveKeyValue_SingleThreaded() {
        val map = NonBlockingHashMap<Int, String>()
        map.put(1, "one")
        map.put(2, "two")

        assertFalse(map.remove(1, "notone")) // Value doesn't match
        assertTrue(map.containsKey(1))
        assertEquals(2, map.size)

        assertTrue(map.remove(1, "one")) // Value matches
        assertNull(map.get(1))
        assertFalse(map.containsKey(1))
        assertEquals(1, map.size)

        assertFalse(map.remove(3, "three")) // Key doesn't exist
    }


    @Test
    fun testClear_SingleThreaded() {
        val map = NonBlockingHashMap<Int, String>()
        map.put(1, "a")
        map.put(2, "b")
        map.clear()
        assertEquals(0, map.size)
        assertTrue(map.isEmpty())
        assertNull(map.get(1))
    }

    @Test
    fun testContainsValue_SingleThreaded() {
        val map = NonBlockingHashMap<Int, String>()
        map.put(1, "apple")
        map.put(2, "banana")

        assertTrue(map.containsValue("apple"))
        assertTrue(map.containsValue("banana"))
        assertFalse(map.containsValue("cherry"))

        map.put(1, "apricot")
        assertFalse(map.containsValue("apple"))
        assertTrue(map.containsValue("apricot"))

        map.remove(2)
        assertFalse(map.containsValue("banana"))
    }


    @Test
    fun testResize_SingleThreaded() {
        val map = NonBlockingHashMap<Int, String>()
        val minSize = getMinSize(map) // Typically 8
        val threshold = (minSize * 0.75).toInt() // Typically 6 if MIN_SIZE is 8

        for (i in 0 until threshold) {
            map.put(i, "val$i")
        }
        assertEquals(threshold, map.size)
        // This access pattern might not show internal CHM directly
        // We rely on the map operating correctly before and after resize trigger

        // Add one more to trigger resize (or get close for next put to trigger)
        map.put(threshold, "val$threshold")
        assertEquals(threshold + 1, map.size) // Should have triggered resize

        // Verify all old and new elements are present
        for (i in 0..threshold) {
            assertEquals("val$i", map.get(i), "Value for key $i after resize")
        }

        // Add more elements to ensure new table is working
        val totalElements = minSize * 2 // Fill up past old minSize
        for (i in (threshold + 1) until totalElements) {
            map.put(i, "val$i")
        }
        assertEquals(totalElements, map.size)
        for (i in 0 until totalElements) {
            assertEquals("val$i", map.get(i), "Value for key $i in larger map")
        }

        // Test remove after resize
        map.remove(0)
        assertNull(map.get(0))
        assertEquals(totalElements - 1, map.size)

        map.remove(threshold)
        assertNull(map.get(threshold))
        assertEquals(totalElements - 2, map.size)

        // Test put (overwrite) after resize
        map.put(1, "new_val1")
        assertEquals("new_val1", map.get(1))
    }

    @Test
    fun testIterators_SingleThreaded_StableMap() {
        val map = NonBlockingHashMap<Int, String>()
        val items = (0..4).map { it to "val$it" }.toMap()
        items.forEach { (k, v) -> map.put(k, v) }

        // Key Iterator
        val keys = mutableSetOf<Int>()
        map.keys.iterator().forEachRemaining { keys.add(it) }
        assertEquals(items.keys, keys)

        // Value Iterator
        val values = mutableListOf<String>()
        map.values.iterator().forEachRemaining { values.add(it) }
        assertEquals(items.values.sorted(), values.sorted()) // Order not guaranteed for values

        // Entry Iterator
        val entries = mutableMapOf<Int, String>()
        map.entries.iterator().forEachRemaining { entries[it.key] = it.value }
        assertEquals(items, entries)

        // Iterator remove (on keys)
        val keyIter = map.keys.iterator()
        assertTrue(keyIter.hasNext())
        val firstKey = keyIter.next()
        keyIter.remove()
        assertNull(map.get(firstKey))
        assertEquals(items.size - 1, map.size)
    }

    @Test
    fun testEntryIteratorSetValue_SingleThreaded() {
        val map = NonBlockingHashMap<Int, String>()
        map.put(1, "one")
        map.put(2, "two")

        val entryIter = map.entries.iterator()
        while(entryIter.hasNext()) {
            val entry = entryIter.next()
            if (entry.key == 1) {
                val oldEntryValue = entry.setValue("ONE_NEW")
                assertEquals("one", oldEntryValue) // Check entry's old value
                assertEquals("ONE_NEW", entry.value) // Check entry's new value
                assertEquals("ONE_NEW", map.get(1)) // Check map's new value
            }
        }
        assertEquals("ONE_NEW", map.get(1))
        assertEquals("two", map.get(2)) // Ensure other value is not affected
    }


    @Test
    fun testNullKeyOrValueThrows() {
        val map = NonBlockingHashMap<String, String>()
        assertFailsWith<IllegalArgumentException> { map.put(null as String, "value") }
        assertFailsWith<IllegalArgumentException> { map.put("key", null as String) }
        assertFailsWith<IllegalArgumentException> { map.get(null as String) }
        assertFailsWith<IllegalArgumentException> { map.remove(null as String) }
        assertFailsWith<IllegalArgumentException> { map.containsKey(null as String) }
        assertFailsWith<IllegalArgumentException> { map.containsValue(null as String) }
        assertFailsWith<IllegalArgumentException> { map.remove("key", null as String) }
        assertFailsWith<IllegalArgumentException> { map.remove(null as String, "value") }
    }

    // --- Concurrency Tests ---
    private fun runConcurrent(
        numCoroutines: Int,
        numOperationsPerCoroutine: Int,
        action: suspend (coroutineIdx: Int, opIdx: Int) -> Unit
    ) = runBlocking {
        coroutineScope {
            (0 until numCoroutines).map { coroutineIdx ->
                async(Dispatchers.Default) {
                    repeat(numOperationsPerCoroutine) { opIdx ->
                        action(coroutineIdx, opIdx)
                    }
                }
            }.awaitAll()
        }
    }

    @Test
    fun testConcurrentPut_UniqueKeys() = runBlocking {
        val map = NonBlockingHashMap<Int, String>()
        val numCoroutines = 10
        val putsPerCoroutine = 100
        val totalPuts = numCoroutines * putsPerCoroutine

        runConcurrent(numCoroutines, putsPerCoroutine) { coroutineIdx, opIdx ->
            val key = coroutineIdx * putsPerCoroutine + opIdx
            map.put(key, "val$key")
        }

        assertEquals(totalPuts, map.size)
        for (i in 0 until totalPuts) {
            assertEquals("val$i", map.get(i))
        }
    }

    @Test
    fun testConcurrentPut_OverwriteSameKeys() = runBlocking {
        val map = NonBlockingHashMap<Int, String>()
        val numCoroutines = 10
        val updatesPerCoroutine = 100 // each coroutine updates all keys
        val numKeys = 10

        // Initial values
        for (i in 0 until numKeys) map.put(i, "initial$i")

        runConcurrent(numCoroutines, updatesPerCoroutine) { coroutineIdx, opIdx ->
            val key = opIdx % numKeys // All coroutines hammer the same few keys
            map.put(key, "coroutine${coroutineIdx}_val$key")
        }

        assertEquals(numKeys, map.size)
        // Verify that each key holds a value from one of the coroutines
        for (i in 0 until numKeys) {
            val value = map.get(i)
            assertNotNull(value)
            assertTrue(value.startsWith("coroutine"), "Value for key $i is $value")
        }
    }

    @Test
    fun testConcurrentRemove_DistinctKeys() = runBlocking {
        val map = NonBlockingHashMap<Int, String>()
        val numCoroutines = 10
        val removesPerCoroutine = 100
        val totalItems = numCoroutines * removesPerCoroutine

        // Pre-populate
        for (i in 0 until totalItems) map.put(i, "val$i")
        assertEquals(totalItems, map.size)

        runConcurrent(numCoroutines, removesPerCoroutine) { coroutineIdx, opIdx ->
            val key = coroutineIdx * removesPerCoroutine + opIdx // Each removes a distinct set
            map.remove(key)
        }
        assertEquals(0, map.size, "All items should be removed")
    }


    @Test
    fun testConcurrentPutGetRemove_Mixed() = runBlocking {
        val map = NonBlockingHashMap<Int, String>()
        val numCoroutines = 10
        val opsPerCoroutine = 200
        val keyRange = 50 // Smaller key range to increase contention

        runConcurrent(numCoroutines, opsPerCoroutine) { _, _ ->
            val key = Random.nextInt(keyRange)
            val opType = Random.nextInt(3)
            when (opType) {
                0 -> map.put(key, "val$key")
                1 -> map.remove(key)
                2 -> map.get(key)
            }
        }

        // Assertions: Check for consistency, not specific size/content
        // Ensure all remaining keys have non-null, non-TOMBSTONE values from map.get()
        map.keys.forEach { key -> // Assuming keys() iterator works reasonably
            val value = map.get(key)
            assertNotNull(value, "Key $key found in keyset but get returns null")
        }
        println("NBHM Mixed ops final size: ${map.size}")
        assertTrue(map.size <= keyRange)
    }

    @Test
    fun testConcurrentResize() = runBlocking {
        val map = NonBlockingHashMap<Int, String>()
        val numCoroutines = 10
        // Enough operations to reliably trigger multiple resizes
        // Initial MIN_SIZE=8. Resize at ~6. Next at ~12. Next at ~24 etc.
        val putsPerCoroutine = 30 // e.g. 10 * 30 = 300 puts total should trigger resizes
        val totalPuts = numCoroutines * putsPerCoroutine

        runConcurrent(numCoroutines, putsPerCoroutine) { coroutineIdx, opIdx ->
            val key = coroutineIdx * putsPerCoroutine + opIdx
            map.put(key, "val$key")
            // Sporadic gets and removes to mix with puts during resize
            if (opIdx % 10 == 0) {
                map.get(Random.nextInt(key + 1))
            }
            if (opIdx % 20 == 0 && key > 0) {
                 map.remove(Random.nextInt(key))
            }
        }

        var finalSize = 0
        var missingCount = 0
        // Verify content - some items might have been removed
        for (i in 0 until totalPuts) {
            val v = map.get(i)
            if (v != null) {
                assertEquals("val$i", v)
                finalSize++
            } else {
                // It might have been put and then removed by the random removes
                // Check if it's truly absent or if it was a victim of random remove
                // This check is hard without knowing which random removes succeeded.
                // For now, just count missing ones.
                missingCount++
            }
        }
        assertEquals(map.size, finalSize, "Calculated size should match map.size")
        println("NBHM Concurrent Resize: Final size: ${map.size}, Initial total puts: $totalPuts, Missing/Removed: $missingCount")
        assertTrue(map.size > 0, "Map should not be empty after many puts")
        assertTrue(map.size <= totalPuts, "Map size should not exceed total puts")
    }
}
