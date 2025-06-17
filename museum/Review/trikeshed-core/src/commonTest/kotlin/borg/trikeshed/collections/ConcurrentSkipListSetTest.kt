package borg.trikeshed.collections

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.*

class ConcurrentSkipListSetTest {

    // --- Single-threaded tests ---

    @Test
    fun testEmptySet() {
        val set = ConcurrentSkipListSet<Int>()
        assertEquals(0, set.size)
        assertTrue(set.isEmpty())
        assertFalse(set.contains(1))
        assertFalse(set.remove(1))
        assertTrue(set.iterator().hasNext().not())
    }

    @Test
    fun testAddAndContains_SingleThreaded() {
        val set = ConcurrentSkipListSet<Int>()
        assertTrue(set.add(1))
        assertTrue(set.contains(1))
        assertEquals(1, set.size)
        assertFalse(set.isEmpty())

        assertFalse(set.add(1)) // Add duplicate
        assertEquals(1, set.size)

        assertTrue(set.add(2))
        assertTrue(set.contains(2))
        assertEquals(2, set.size)
    }

    @Test
    fun testRemove_SingleThreaded() {
        val set = ConcurrentSkipListSet<Int>()
        set.add(1)
        set.add(2)
        set.add(3)
        assertEquals(3, set.size)

        assertTrue(set.remove(2))
        assertEquals(2, set.size)
        assertFalse(set.contains(2))

        assertFalse(set.remove(5)) // Remove non-existent
        assertEquals(2, set.size)

        assertTrue(set.remove(1))
        assertEquals(1, set.size)

        assertTrue(set.remove(3))
        assertEquals(0, set.size)
        assertTrue(set.isEmpty())
    }

    @Test
    fun testClearAndIsEmpty_SingleThreaded() {
        val set = ConcurrentSkipListSet<Int>()
        set.add(1)
        set.add(2)
        assertFalse(set.isEmpty())
        assertEquals(2, set.size)

        set.clear()
        assertTrue(set.isEmpty())
        assertEquals(0, set.size)
        assertFalse(set.contains(1))
    }

    @Test
    fun testIterator_SingleThreaded() {
        val set = ConcurrentSkipListSet<Int>()
        set.add(1)
        set.add(3)
        set.add(2) // Order should be 1, 2, 3

        val iter = set.iterator()
        assertTrue(iter.hasNext())
        assertEquals(1, iter.next())
        assertTrue(iter.hasNext())
        assertEquals(2, iter.next())
        iter.remove() // Remove 2
        assertFalse(set.contains(2))
        assertEquals(2, set.size)
        assertTrue(iter.hasNext())
        assertEquals(3, iter.next())
        assertFalse(iter.hasNext())
        assertFailsWith<NoSuchElementException> { iter.next() }
    }

    @Test
    fun testBulkOperations_SingleThreaded() {
        val set = ConcurrentSkipListSet<Int>()
        set.addAll(listOf(1,2,3,2)) // 2 is duplicate
        assertEquals(3, set.size)
        assertTrue(set.containsAll(listOf(1,3)))
        assertFalse(set.containsAll(listOf(1,4)))

        assertTrue(set.removeAll(listOf(1,4))) // 1 removed, 4 not present
        assertEquals(listOf(2,3), set.toList().sorted())

        set.addAll(listOf(1,4)) // now 1,2,3,4
        assertTrue(set.retainAll(listOf(2,3,5))) // keep 2,3
        assertEquals(listOf(2,3), set.toList().sorted())
    }


    @Test
    fun testNaturalOrderInteger_SingleThreaded() {
        val set = ConcurrentSkipListSet<Int>()
        set.add(3)
        set.add(1)
        set.add(2)
        assertEquals(listOf(1, 2, 3), set.toList())
    }

    @Test
    fun testCustomComparator_SingleThreaded() {
        val set = ConcurrentSkipListSet<Int>(compareByDescending { it })
        set.add(3)
        set.add(1)
        set.add(2)
        assertEquals(listOf(3, 2, 1), set.toList())
        assertNotNull(set.comparator())
    }

    @Test
    fun testFirstLast_SingleThreaded() {
        val set = ConcurrentSkipListSet<Int>()
        assertFailsWith<NoSuchElementException> { set.first() }
        assertFailsWith<NoSuchElementException> { set.last() }

        set.add(10)
        assertEquals(10, set.first())
        assertEquals(10, set.last())

        set.add(5)
        assertEquals(5, set.first())
        assertEquals(10, set.last())

        set.add(20)
        assertEquals(5, set.first())
        assertEquals(20, set.last())
    }

    @Test
    fun testPollFirstLast_SingleThreaded() {
        val set = ConcurrentSkipListSet<Int>()
        set.addAll(listOf(10, 20, 5))

        assertEquals(5, set.pollFirst())
        assertEquals(2, set.size)
        assertEquals(10, set.first())

        assertEquals(20, set.pollLast())
        assertEquals(1, set.size)
        assertEquals(10, set.last())

        assertEquals(10, set.pollFirst())
        assertTrue(set.isEmpty())
        assertNull(set.pollFirst())
        assertNull(set.pollLast())
    }

    @Test
    fun testNavigationMethods_SingleThreaded() {
        val set = ConcurrentSkipListSet<Int>().apply {
            addAll(listOf(10, 20, 30, 40, 50))
        }

        assertEquals(10, set.lower(20))
        assertEquals(20, set.floor(20))
        assertEquals(10, set.floor(15))

        assertEquals(20, set.ceiling(20))
        assertEquals(20, set.ceiling(15))

        assertEquals(30, set.higher(20))

        assertNull(set.lower(10))
        assertNull(set.higher(50))
    }

    @Test
    fun testDeferredNavigableSetMethodsThrowUnsupported() {
        val set = ConcurrentSkipListSet<Int>()
        assertFailsWith<UnsupportedOperationException> { set.descendingSet() }
        assertFailsWith<UnsupportedOperationException> { set.descendingIterator() }
        assertFailsWith<UnsupportedOperationException> { set.subSet(1, true, 5, true) }
        assertFailsWith<UnsupportedOperationException> { set.headSet(5, true) }
        assertFailsWith<UnsupportedOperationException> { set.tailSet(1, true) }
    }


    @Test
    fun testNullElementThrows() {
        val set = ConcurrentSkipListSet<String>()
        assertFailsWith<NullPointerException> { set.add(null as String) }
        // For other methods, it depends on if they call internal map methods that throw for null.
        // contains(null) might throw or return false. Let's assume it throws due to map.
        assertFailsWith<NullPointerException> { set.contains(null as String) }
        assertFailsWith<NullPointerException> { set.remove(null as String) }
    }

    // --- Concurrency Tests ---
    private fun runConcurrent(times: Int, action: suspend (Int) -> Unit) = runBlocking {
        (1..times).map { i ->
            async(Dispatchers.Default) { action(i) }
        }.awaitAll()
    }

    @Test
    fun testConcurrentAdd() = runBlocking {
        val set = ConcurrentSkipListSet<Int>()
        val numAdds = 1000
        val itemsPerThread = 10

        (1..(numAdds / itemsPerThread)).map { threadId ->
            async(Dispatchers.Default) {
                for (i in 0 until itemsPerThread) {
                    val item = threadId * itemsPerThread + i
                    set.add(item)
                }
            }
        }.awaitAll()
        assertEquals(numAdds, set.size)
        for (i in 0 until numAdds) {
            assertTrue(set.contains(i))
        }
    }

    @Test
    fun testConcurrentAddDuplicates() = runBlocking {
        val set = ConcurrentSkipListSet<Int>()
        val numItems = 100
        val numThreads = 10
        // First, add all items to ensure they are there
        for (i in 0 until numItems) set.add(i)
        assertEquals(numItems, set.size)

        // Then, concurrently try to add them again
        (1..numThreads).map {
            async(Dispatchers.Default) {
                for (i in 0 until numItems) {
                    assertFalse(set.add(i)) // Should return false as they already exist
                }
            }
        }.awaitAll()
        assertEquals(numItems, set.size) // Size should not change
    }

    @Test
    fun testConcurrentRemove() = runBlocking {
        val set = ConcurrentSkipListSet<Int>()
        val numItems = 1000
        for (i in 0 until numItems) set.add(i)
        assertEquals(numItems, set.size)

        val itemsToRemovePerThread = numItems / 10
        (1..10).map { threadId ->
            async(Dispatchers.Default) {
                for (i in 0 until itemsToRemovePerThread) {
                    // Each thread removes a disjoint set of items to avoid double-counting successful removes
                    val item = (threadId -1) * itemsToRemovePerThread + i
                    set.remove(item)
                }
            }
        }.awaitAll()
        assertEquals(0, set.size, "All items should have been removed")
    }

    @Test
    fun testConcurrentAddRemoveContains() = runBlocking {
        val set = ConcurrentSkipListSet<Int>()
        val numOps = 1000
        val keyRange = 100

        val jobs = mutableListOf<Job>()
        repeat(10) { // Number of coroutines
            jobs += launch(Dispatchers.Default) {
                repeat(numOps / 10) {
                    val key = Random.nextInt(keyRange)
                    when (Random.nextInt(3)) {
                        0 -> set.add(key)
                        1 -> set.remove(key)
                        2 -> set.contains(key)
                    }
                }
            }
        }
        jobs.joinAll()
        // Final state is non-deterministic, but should not crash.
        // Check basic integrity:
        set.forEach { element -> // Iterate to check for issues
           assertTrue(set.contains(element)) // If it's in the set, contains should be true
        }
        println("ConcurrentAddRemoveContains final set size: ${set.size}")
        assertTrue(set.size <= keyRange)
    }

    @Test
    fun testConcurrentPollFirstLast() = runBlocking {
        val set = ConcurrentSkipListSet<Int>()
        val numItems = 1000
        (0 until numItems).shuffled().forEach { set.add(it) } // Add in random order

        val polledItems = mutableListOf<Int>()
        val mutex = Mutex()

        (1..5).map { // 5 coroutines polling
            async(Dispatchers.Default) {
                repeat(numItems / 5) { // Each coroutine tries to poll its share
                    if (Random.nextBoolean()) {
                        set.pollFirst()?.let { mutex.withLock { polledItems.add(it) } }
                    } else {
                        set.pollLast()?.let { mutex.withLock { polledItems.add(it) } }
                    }
                }
            }
        }.awaitAll()

        assertEquals(numItems, polledItems.size + set.size, "All items must be polled or remain")
        assertEquals(numItems, polledItems.distinct().size + set.size, "Polled items + remaining should be unique and cover all original items")
        assertTrue(set.isEmpty() || polledItems.size > 0)
        println("ConcurrentPollFirstLast: Polled count: ${polledItems.size}, Remaining in set: ${set.size}")
    }
}
