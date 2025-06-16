package borg.trikeshed.collections

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.*

class ConcurrentSkipListMapTest {

    // --- Single-threaded tests ---

    @Test
    fun testEmptyMap() {
        val map = ConcurrentSkipListMap<Int, String>()
        assertEquals(0, map.size)
        assertTrue(map.isEmpty())
        assertNull(map.get(1))
        assertFalse(map.containsKey(1))
        assertFalse(map.containsValue("a"))
        assertNull(map.remove(1))
        assertTrue(map.entries.isEmpty())
        assertTrue(map.keys.isEmpty())
        assertTrue(map.values.isEmpty())
    }

    @Test
    fun testPutAndGet_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>()
        assertNull(map.put(1, "one"))
        assertEquals("one", map.get(1))
        assertEquals(1, map.size)
        assertFalse(map.isEmpty())

        assertEquals("one", map.put(1, "uno")) // Overwrite
        assertEquals("uno", map.get(1))
        assertEquals(1, map.size)

        assertNull(map.put(2, "two"))
        assertEquals("two", map.get(2))
        assertEquals(2, map.size)

        assertEquals("uno", map.get(1))
    }

    @Test
    fun testRemove_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>()
        map.put(1, "one")
        map.put(2, "two")
        map.put(3, "three")
        assertEquals(3, map.size)

        assertEquals("two", map.remove(2))
        assertEquals(2, map.size)
        assertNull(map.get(2)) // Logically deleted
        assertFalse(map.containsKey(2))

        assertEquals("one", map.get(1))
        assertEquals("three", map.get(3))

        assertNull(map.remove(5)) // Remove non-existent
        assertEquals(2, map.size)

        // Test internal value after logical delete
        // This requires access to internal Node structure or a way to check logical deletion.
        // For now, rely on get/containsKey.

        assertEquals("one", map.remove(1))
        assertEquals(1, map.size)

        assertEquals("three", map.remove(3))
        assertEquals(0, map.size)
        assertTrue(map.isEmpty())
    }

    @Test
    fun testContainsKeyAndValue_SingleThreaded() {
        val map = ConcurrentSkipListMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)

        assertTrue(map.containsKey("a"))
        assertTrue(map.containsKey("b"))
        assertFalse(map.containsKey("c"))

        assertTrue(map.containsValue(1))
        assertTrue(map.containsValue(2))
        assertFalse(map.containsValue(3))

        map.remove("a")
        assertFalse(map.containsKey("a"))
        assertFalse(map.containsValue(1))
    }

    @Test
    fun testClearAndIsEmpty_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>()
        map.put(1, "a")
        map.put(2, "b")
        assertFalse(map.isEmpty())
        assertEquals(2, map.size)

        map.clear()
        assertTrue(map.isEmpty())
        assertEquals(0, map.size)
        assertFalse(map.containsKey(1))
    }

    @Test
    fun testSize_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>()
        assertEquals(0, map.size)
        map.put(1, "a")
        assertEquals(1, map.size)
        map.put(2, "b")
        assertEquals(2, map.size)
        map.put(1, "aa") // overwrite
        assertEquals(2, map.size)
        map.remove(1)
        assertEquals(1, map.size)
        map.put(1, "aaa") // re-add
        assertEquals(2, map.size)
        map.remove(1)
        map.remove(2)
        assertEquals(0, map.size)
    }


    @Test
    fun testNaturalOrderIntegerKeys_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>()
        map.put(3, "three")
        map.put(1, "one")
        map.put(2, "two")

        assertEquals(listOf(1, 2, 3), map.keys.toList())
    }

    @Test
    fun testCustomComparator_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>(compareByDescending { it })
        map.put(3, "three")
        map.put(1, "one")
        map.put(2, "two")

        assertEquals(listOf(3, 2, 1), map.keys.toList())
        assertNotNull(map.comparator())
    }

    @Test
    fun testFirstLastKeyEntry_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>()
        assertTrue(map.isEmpty())
        assertNull(map.firstKey())
        assertNull(map.lastKey())
        assertNull(map.firstEntry())
        assertNull(map.lastEntry())

        map.put(10, "A")
        assertEquals(10, map.firstKey())
        assertEquals(10, map.lastKey())
        assertEquals(10 to "A", map.firstEntry()?.toPair())

        map.put(5, "B")
        assertEquals(5, map.firstKey())
        assertEquals(10, map.lastKey())

        map.put(20, "C")
        assertEquals(5, map.firstKey())
        assertEquals(20, map.lastKey())
        assertEquals(5 to "B", map.firstEntry()?.toPair())
        assertEquals(20 to "C", map.lastEntry()?.toPair())

        map.remove(5)
        assertEquals(10, map.firstKey())
        map.remove(20)
        assertEquals(10, map.lastKey())
    }

    private fun <K,V> Map.Entry<K,V>.toPair(): Pair<K,V> = key to value


    @Test
    fun testPollFirstLastEntry_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>()
        map.put(10, "ten")
        map.put(20, "twenty")
        map.put(5, "five")

        assertEquals(5 to "five", map.pollFirstEntry()?.toPair())
        assertEquals(2, map.size)
        assertEquals(10, map.firstKey())

        assertEquals(20 to "twenty", map.pollLastEntry()?.toPair())
        assertEquals(1, map.size)
        assertEquals(10, map.lastKey())

        assertEquals(10 to "ten", map.pollFirstEntry()?.toPair())
        assertTrue(map.isEmpty())
        assertNull(map.pollFirstEntry())
    }

    @Test
    fun testNavigationMethods_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>().apply {
            put(10, "A"); put(20, "B"); put(30, "C"); put(40, "D"); put(50, "E")
        }

        assertEquals(10, map.lowerKey(20))
        assertEquals("A", map.lowerEntry(20)?.value)
        assertEquals(20, map.floorKey(20))
        assertEquals("B", map.floorEntry(20)?.value)
        assertEquals(10, map.floorKey(15))

        assertEquals(20, map.ceilingKey(20))
        assertEquals("B", map.ceilingEntry(20)?.value)
        assertEquals(20, map.ceilingKey(15))

        assertEquals(30, map.higherKey(20))
        assertEquals("C", map.higherEntry(20)?.value)

        assertNull(map.lowerKey(10))
        assertNull(map.higherKey(50))
    }

    @Test
    fun testIterators_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>().apply {
            put(1, "a"); put(2, "b"); put(3, "c")
        }

        // Keys
        val keyIter = map.keys.iterator()
        assertEquals(1, keyIter.next())
        assertEquals(2, keyIter.next())
        keyIter.remove() // remove 2="b"
        assertFalse(map.containsKey(2))
        assertEquals(3, keyIter.next())
        assertFalse(keyIter.hasNext())

        // Values
        map.put(2, "b_new") // Re-add 2
        val valIter = map.values.iterator()
        assertEquals("a", valIter.next()) // 1="a"
        assertEquals("b_new", valIter.next()) // 2="b_new"
        valIter.remove()
        assertFalse(map.containsKey(2))
        assertEquals("c", valIter.next()) // 3="c"
        assertFalse(valIter.hasNext())

        // Entries
        map.put(2, "b_again")
        val entryIter = map.entries.iterator()
        assertEquals(1 to "a", entryIter.next().toPair())
        val entry2 = entryIter.next()
        assertEquals(2 to "b_again", entry2.toPair())

        // Test MutableMap.MutableEntry.setValue
        assertTrue(entry2 is MutableMap.MutableEntry)
        val oldVal = (entry2 as MutableMap.MutableEntry).setValue("B_MODIFIED")
        assertEquals("b_again", oldVal)
        assertEquals("B_MODIFIED", map.get(2))

        entryIter.remove()
        assertFalse(map.containsKey(2))
        assertEquals(3 to "c", entryIter.next().toPair())
        assertFalse(entryIter.hasNext())
    }

    @Test
    fun testCollectionViews_SingleThreaded() {
        val map = ConcurrentSkipListMap<Int, String>()
        map.put(1, "a"); map.put(2, "b"); map.put(3, "c")

        // Keys
        val keys = map.keys
        assertEquals(3, keys.size)
        assertTrue(keys.contains(1))
        assertTrue(keys.remove(1))
        assertEquals(2, keys.size)
        assertFalse(map.containsKey(1))

        // Values
        val values = map.values
        assertEquals(2, values.size) // 2="b", 3="c"
        assertTrue(values.contains("b"))
        assertTrue(values.remove("b"))
        assertEquals(1, values.size)
        assertFalse(map.containsValue("b"))

        // Entries
        val entries = map.entries
        assertEquals(1, entries.size) // 3="c"
        // entries.remove needs an exact match. Test by iterating.
        val entryToRemove = entries.first()
        assertTrue(entries.remove(entryToRemove))
        assertTrue(map.isEmpty())
    }

    @Test
    fun testNullKeyOrValueThrows() {
        val map = ConcurrentSkipListMap<Int, String>()
        assertFailsWith<NullPointerException> { map.put(null as Int, "null key") }
        assertFailsWith<NullPointerException> { map.put(1, null as String) }

        // Also check for get, remove, containsKey with null keys
        assertFailsWith<NullPointerException> { map.get(null as Int) }
        assertFailsWith<NullPointerException> { map.remove(null as Int) }
        assertFailsWith<NullPointerException> { map.containsKey(null as Int) }
        // containsValue(null) is also disallowed by our design.
        assertFailsWith<NullPointerException> { map.containsValue(null as String)}
    }

    // --- Concurrency Tests ---
    // Helper to run concurrent jobs
    private fun runConcurrent(times: Int, action: suspend (Int) -> Unit) = runBlocking {
        (1..times).map { i ->
            async(Dispatchers.Default) { // Use Default dispatcher for CPU-bound work
                action(i)
            }
        }.awaitAll()
    }

    @Test
    fun testConcurrentPut() = runBlocking {
        val map = ConcurrentSkipListMap<Int, Int>()
        val numPuts = 1000
        val itemsPerThread = 10

        (1..(numPuts/itemsPerThread)).map { threadId ->
            async(Dispatchers.Default) {
                for (i in 0 until itemsPerThread) {
                    val key = threadId * itemsPerThread + i
                    map.put(key, key)
                }
            }
        }.awaitAll()
        assertEquals(numPuts, map.size)
        for (i in 0 until numPuts) {
            assertEquals(i, map.get(i))
        }
    }

    @Test
    fun testConcurrentPutOverwrite() = runBlocking {
        val map = ConcurrentSkipListMap<Int, String>()
        val numKeys = 100
        val numUpdates = 10 // Each key updated 10 times

        // Initial puts
        (0 until numKeys).forEach { map.put(it, "initial-$it") }

        (1..numUpdates).map { updateNum ->
            async(Dispatchers.Default) {
                 (0 until numKeys).shuffled().forEach { key -> // shuffled to increase contention
                    map.put(key, "update-$updateNum-$key")
                }
            }
        }.awaitAll()

        assertEquals(numKeys, map.size)
        // Check that each key has one of the "update-NUMUPDATES-key" values
        // (or the last one if updates are somewhat ordered by updateNum)
        // The exact value is hard to predict without more sync, but it should be an updated one.
        // For simplicity, check that it's not "initial".
         (0 until numKeys).forEach { key ->
            val value = map.get(key)
            assertNotNull(value)
            assertTrue(value!!.startsWith("update-"), "Value for $key should be updated, but was $value")
         }
    }


    @Test
    fun testConcurrentGetAndPutRemove() = runBlocking {
        val map = ConcurrentSkipListMap<Int, String>()
        val numOperations = 500
        val keyRange = 100

        // Populate map initially
        for (i in 0 until keyRange) {
            map.put(i, "value-$i")
        }

        val jobs = mutableListOf<Job>()

        // Putter/Remover threads
        repeat(5) { // 5 threads doing puts and removes
            jobs += launch(Dispatchers.Default) {
                for (i in 0 until numOperations) {
                    val key = Random.nextInt(keyRange)
                    val value = "op-$i-$key"
                    if (Random.nextBoolean()) {
                        map.put(key, value)
                    } else {
                        map.remove(key)
                    }
                }
            }
        }

        // Getter threads
        repeat(5) { // 5 threads doing gets
            jobs += launch(Dispatchers.Default) {
                for (i in 0 until numOperations * 2) { // More gets
                    val key = Random.nextInt(keyRange)
                    map.get(key) // Just perform get, result can vary
                }
            }
        }
        jobs.joinAll()
        // Post-concurrency checks:
        // Size can be anything from 0 to keyRange.
        // Just ensure map is in a consistent state (e.g., no crashes).
        // We can check that all keys present have non-null values (our deletion marker).
        map.keys.forEach { key ->
            assertNotNull(map.get(key), "Key $key in keyset but get returns null")
        }
        println("ConcurrentGetAndPutRemove final map size: ${map.size}") // Informational
        assertTrue(map.size <= keyRange)
    }


    @Test
    fun testConcurrentRemove() = runBlocking {
        val map = ConcurrentSkipListMap<Int, String>()
        val numItems = 1000
        for (i in 0 until numItems) {
            map.put(i, "item-$i")
        }
        assertEquals(numItems, map.size)

        (1..10).map {
            async(Dispatchers.Default) {
                for (i in 0 until numItems / 10) {
                    val keyToRemove = Random.nextInt(numItems)
                    map.remove(keyToRemove)
                }
            }
        }.awaitAll()

        // All items could potentially be removed.
        // Check that all remaining items are valid.
        map.keys.forEach { key ->
            assertNotNull(map.get(key))
        }
        println("ConcurrentRemove final map size: ${map.size}")
        assertTrue(map.size < numItems) // Some should have been removed
    }

    @Test
    fun testConcurrentPoll() = runBlocking {
        val map = ConcurrentSkipListMap<Int, String>()
        val numItems = 1000
        for (i in 0 until numItems) {
            map.put(i, "item-$i")
        }

        val polledFirst = mutableListOf<Pair<Int, String>>()
        val polledLast = mutableListOf<Pair<Int, String>>()
        val mutex = Mutex() // To safely add to lists

        (1..5).map {
            async(Dispatchers.Default) {
                repeat(numItems / 5 / 2) { // Each thread polls some first and some last
                    map.pollFirstEntry()?.let { entry -> mutex.withLock { polledFirst.add(entry.toPair()) } }
                    map.pollLastEntry()?.let { entry -> mutex.withLock { polledLast.add(entry.toPair()) } }
                }
            }
        }.awaitAll()

        val totalPolled = polledFirst.size + polledLast.size
        assertEquals(numItems, totalPolled + map.size, "All items should be either polled or remain in map")

        // Check uniqueness of polled items
        val allPolledItems = (polledFirst.map { it.first } + polledLast.map { it.first }).toSet()
        assertEquals(totalPolled, allPolledItems.size, "Polled items should be unique")

        // Check order of polledFirst (should be ascending) and polledLast (should be descending)
        if (polledFirst.size > 1) {
            for(i in 0 until polledFirst.size -1) {
                 assertTrue(polledFirst[i].first < polledFirst[i+1].first, "Polled first items not in order")
            }
        }
         if (polledLast.size > 1) {
            for(i in 0 until polledLast.size -1) {
                 assertTrue(polledLast[i].first > polledLast[i+1].first, "Polled last items not in order")
            }
        }
        println("ConcurrentPoll: Polled first count: ${polledFirst.size}, Polled last count: ${polledLast.size}, Remaining in map: ${map.size}")
    }

    @Test
    fun testConcurrentIteratorRemove() = runBlocking {
        val map = ConcurrentSkipListMap<Int, String>()
        val numItems = 100
        for (i in 0 until numItems) { map.put(i, "val$i") }

        // Multiple coroutines iterating and removing parts of the map
        // This is tricky because iterators are weakly consistent.
        // A simple test: one coroutine iterates and removes even numbers.
        // Another iterates and removes odd numbers (if not already removed).
        // This test might be too prone to specific interleavings.

        // Let's test a simpler scenario: one iterator removing while other things happen.
        launch(Dispatchers.Default) {
            val iter = map.keys.iterator()
            while (iter.hasNext()) {
                val key = iter.next()
                if (key % 2 == 0) {
                    try { iter.remove() } catch (e: Exception) { /* Potentially concurrent removal, ignore for this test */ }
                }
                delay(1) // yield for other coroutines
            }
        }

        launch(Dispatchers.Default) {
            for (i in 0 until numItems) {
                if (i % 10 == 0) map.put(i + numItems, "newVal$i") // Add new items
                delay(1)
            }
        }
        delay(300) // Allow coroutines to run

        // Assertions:
        // Some even numbers should be removed. Some new items might be added.
        // Exact state is hard to predict, but map should be valid.
        var evenCount = 0
        var oddCount = 0
        map.keys.forEach {
            assertNotNull(map.get(it))
            if (it < numItems) { // Original range
                if (it % 2 == 0) evenCount++ else oddCount++
            }
        }
        println("ConcurrentIteratorRemove: remaining evens (orig range): $evenCount, odds: $oddCount, total size: ${map.size}")
        assertTrue(evenCount < numItems / 2, "Some even numbers should have been removed")
    }
}
