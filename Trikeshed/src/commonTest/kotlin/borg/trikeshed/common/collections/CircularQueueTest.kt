package borg.trikeshed.common.collections

import kotlinx.coroutines.InternalCoroutinesApi // Required due to CirQlar using it
import borg.trikeshed.lib.* // For Indexed, MutableIndexed, Join, and extensions like .size, .get
import kotlin.test.*
import java.util.NoSuchElementException // Already in CircularQueue.kt but good for clarity here too

@OptIn(InternalCoroutinesApi::class) // Opt-in for the test class for CirQlar usage
class CircularQueueTest {

    @Test
    fun testOfferAndPoll() {
        val q = CirQlar<Int>(3)
        assertTrue(q.isEmpty)
        assertEquals(0, q.currentSize)

        q.offer(1) // [1]
        assertFalse(q.isEmpty)
        assertEquals(1, q.currentSize)
        assertEquals(1, q.peek())

        q.offer(2) // [1, 2]
        assertEquals(2, q.currentSize)
        assertEquals(1, q.peek())

        q.offer(3) // [1, 2, 3]
        assertEquals(3, q.currentSize)
        assertTrue(q.isFull)
        assertEquals(1, q.peek())

        q.offer(4) // Offer to full queue. Old head (1) is replaced by 4. Head advances. Q: [2,3,4]
        assertEquals(3, q.currentSize)
        assertEquals(2, q.peek())

        assertEquals(2, q.poll()) // Q: [3,4]. Head is 3.
        assertEquals(2, q.currentSize)
        assertEquals(3, q.peek())

        assertEquals(3, q.poll()) // Q: [4]. Head is 4.
        assertEquals(1, q.currentSize)
        assertEquals(4, q.peek())

        assertEquals(4, q.poll()) // Q: []. Empty.
        assertEquals(0, q.currentSize)
        assertTrue(q.isEmpty)

        assertNull(q.poll()) // Poll empty queue.
    }

    @Test
    fun testPeekEmpty() {
        val q = CirQlar<String>(2)
        assertFailsWith<NoSuchElementException> {
            q.peek()
        }
    }

    @Test
    fun testToListAndToVect0r() {
        val q = CirQlar<Int>(3)
        q.offer(10)
        q.offer(20)
        assertEquals(listOf(10, 20), q.toList())
        var v = q.toVect0r()
        assertEquals(2, v.a) // Changed from v.size to v.a for Join
        assertEquals(10, v.b(0)) // Using .b() as per Join<A,B> structure if B is (Int)->T
        assertEquals(20, v.b(1))

        q.offer(30) // Q: [10, 20, 30]
        assertEquals(listOf(10, 20, 30), q.toList())
        v = q.toVect0r()
        assertEquals(3, v.a) // Changed from v.size to v.a
        assertEquals(10, v.b(0)); assertEquals(20, v.b(1)); assertEquals(30, v.b(2))

        q.offer(40) // Evicts 10. Q: [20, 30, 40]
        assertEquals(listOf(20, 30, 40), q.toList())
        v = q.toVect0r()
        assertEquals(3, v.a) // Changed from v.size to v.a
        assertEquals(20, v.b(0)); assertEquals(30, v.b(1)); assertEquals(40, v.b(2))

        q.poll() // Removes 20. Q: [30, 40]
        assertEquals(listOf(30, 40), q.toList())
        v = q.toVect0r()
        assertEquals(2, v.a) // Changed from v.size to v.a
        assertEquals(30, v.b(0)); assertEquals(40, v.b(1))
    }

    @Test
    fun testIterator() {
        val q = CirQlar<String>(3)
        q.offer("a"); q.offer("b"); q.offer("c") // Q: [a, b, c]

        val items = mutableListOf<String>()
        val iter = q.iterator()
        while(iter.hasNext()) {
            items.add(iter.next())
        }
        assertEquals(listOf("a", "b", "c"), items)

        // Test remove - first element
        val iter2 = q.iterator() // q is [a,b,c]
        assertEquals("a", iter2.next())
        iter2.remove() // remove "a". q should be [b,c]
        assertEquals(2, q.currentSize)
        assertEquals("b", q.peek())
        items.clear()
        while(iter2.hasNext()){ items.add(iter2.next()) }
        assertEquals(listOf("b", "c"), items) // Iterator continues correctly

        // Test remove - middle element (relative to its own iteration)
        q.clear(); q.offer("x"); q.offer("y"); q.offer("z") // Q: [x,y,z]
        val iter3 = q.iterator()
        assertEquals("x", iter3.next()) // x
        assertEquals("y", iter3.next()) // y
        iter3.remove() // remove "y". q should be [x,z]
        assertEquals(2, q.currentSize)
        assertEquals("x", q.peek())
        assertEquals("z", q.toList()[1])

        items.clear()
        // Iterator iter3 was on "y" (logical idx 1), after removing "y", its logicalIdx becomes 1.
        // Next element for iter3 is "z" (which was logical idx 2, now logical idx 1).
        assertEquals("z", iter3.next())
        items.add("x"); items.add("z") // Manually reconstruct what should have been iterated
        assertFalse(iter3.hasNext())
        assertEquals(listOf("x", "z"), q.toList())


        // Test remove - last element
        q.clear(); q.offer("1"); q.offer("2"); q.offer("3") // Q: [1,2,3]
        val iter4 = q.iterator()
        iter4.next() // 1
        iter4.next() // 2
        assertEquals("3", iter4.next()) // 3
        iter4.remove() // remove "3". q should be [1,2]
        assertFalse(iter4.hasNext())
        assertEquals(listOf("1","2"), q.toList())
    }

    @Test
    fun testIteratorRemoveAll() {
        val q = CirQlar<Int>(3)
        q.offer(1); q.offer(2); q.offer(3)
        val iter = q.iterator()
        while(iter.hasNext()){
            iter.next()
            iter.remove()
        }
        assertTrue(q.isEmpty)
        assertEquals(0, q.currentSize)
    }

    @Test
    fun testIteratorRemoveIllegalState() {
        val q = CirQlar<Int>(1)
        q.offer(1)
        val iter = q.iterator()
        assertFailsWith<IllegalStateException> { iter.remove() } // Before next()
        iter.next()
        iter.remove() // Valid
        assertFailsWith<IllegalStateException> { iter.remove() } // Twice
    }

    @Test
    fun testEvictionCallback() {
        val evictedItems = mutableListOf<String>()
        val q = CirQlar<String>(2, evict = { evictedItems.add(it) })

        q.offer("A") // No eviction
        q.offer("B") // No eviction
        assertTrue(evictedItems.isEmpty())

        q.offer("C") // "A" should be evicted (offer replaces head)
        assertEquals(listOf("A"), evictedItems)
        assertEquals(listOf("B", "C"), q.toList())

        q.offer("D") // "B" should be evicted
        assertEquals(listOf("A", "B"), evictedItems)
        assertEquals(listOf("C", "D"), q.toList())

        // Test eviction with add(index, item) when full
        evictedItems.clear()
        q.clear(); q.offer("X"); q.offer("Y") // Q: [X, Y]
        // Add "Z" at index 0. "X" should be evicted. Q becomes [Z, Y]
        q.add(0, "Z")
        assertEquals(listOf("X"), evictedItems)
        assertEquals(listOf("Z", "Y"), q.toList())

        // Add "W" at index 1. "Z" should be evicted. Q becomes [Y, W]
        evictedItems.clear()
        q.add(1, "W")
        assertEquals(listOf("Z"), evictedItems)
        assertEquals(listOf("Y", "W"), q.toList())
    }

    // --- MutableIndexed Tests ---
    @Test
    fun testGetAndSet() {
        val q = CirQlar<Int>(3) // Is a MutableIndexed<Int>
        q.offer(1); q.offer(2); q.offer(3) // [1,2,3]

        assertEquals(1, q[0])
        assertEquals(2, q[1])
        assertEquals(3, q[2])
        assertFailsWith<IndexOutOfBoundsException> { q[3] }
        assertFailsWith<IndexOutOfBoundsException> { q[-1] }

        q[1] = 20 // [1,20,3]
        assertEquals(20, q[1])
        assertEquals(listOf(1,20,3), q.toList())

        q.offer(4) // evicts 1. Q: [20,3,4] (head is 20)
        assertEquals(20, q[0])
        assertEquals(3, q[1])
        assertEquals(4, q[2])

        q[0] = 200 // Q: [200,3,4]
        assertEquals(200, q[0])
        assertEquals(listOf(200,3,4), q.toList())
    }

    @Test
    fun testAddItem() { // add(item:T) from MutableIndexed (Unit return)
        val q = CirQlar<String>(2)
        q.add("one") // Uses offer
        assertEquals(listOf("one"), q.toList())
        q.add("two")
        assertEquals(listOf("one", "two"), q.toList())
        q.add("three") // evicts "one"
        assertEquals(listOf("two", "three"), q.toList())
    }

    @Test
    fun testAddAtIndex() {
        val q = CirQlar<Int>(3)
        q.add(0, 10) // [10]
        assertEquals(listOf(10), q.toList())

        q.add(1, 30) // [10,30] (add at end)
        assertEquals(listOf(10,30), q.toList())

        q.add(0, 5) // [5,10,30] (add at beginning)
        assertEquals(listOf(5,10,30), q.toList())
        assertTrue(q.isFull)

        // Add at end of full queue (behaves like offer, evicts head 5)
        // Q: [5,10,30]. Head=5.
        q.add(3, 40) // Evicts 5. Q: [10,30,40]. Head=10.
        assertEquals(listOf(10,30,40), q.toList())

        // Add into middle of full queue (evicts head 10, then inserts)
        // Q: [10,30,40]. Head=10.
        // add(1, 25) -> evicts 10. Head becomes 30. Space made.
        // Old queue elements relative to new head (30): 30 is logical 0, 40 is logical 1.
        // Insert 25 at logical 1. Element 40 (at logical 1) shifts to logical 2.
        // Q: [30, 25, 40]. Head=30.
        q.add(1, 25)
        assertEquals(listOf(30,25,40), q.toList())
        assertEquals(3, q.currentSize)
    }

    @Test
    fun testAddAtIndexOutOfBounds() {
        val q = CirQlar<Int>(2)
        assertFailsWith<IndexOutOfBoundsException> { q.add(-1, 0) }
        // q.size is 0. Valid indices for add are 0.
        assertFailsWith<IndexOutOfBoundsException> { q.add(1, 0) }
        q.add(0,1) // OK, size becomes 1. Q: [1]
        // q.size is 1. Valid indices for add are 0, 1.
        assertFailsWith<IndexOutOfBoundsException> { q.add(2, 0) }
    }

    @Test
    fun testRemoveAt() {
        val q = CirQlar<String>(3)
        q.offer("a"); q.offer("b"); q.offer("c") // [a,b,c]

        assertEquals("b", q.removeAt(1)) // [a,c]
        assertEquals(listOf("a","c"), q.toList())
        assertEquals(2, q.currentSize)

        assertEquals("a", q.removeAt(0)) // [c]
        assertEquals(listOf("c"), q.toList())
        assertEquals(1, q.currentSize)

        assertEquals("c", q.removeAt(0)) // []
        assertTrue(q.isEmpty)

        assertFailsWith<IndexOutOfBoundsException> { q.removeAt(0) }
    }

    @Test
    fun testRemoveItem() {
        val q = CirQlar<String>(4)
        q.offer("x"); q.offer("y"); q.offer("z"); q.offer("y") // [x,y,z,y]

        assertTrue(q.remove("y")) // Removes first "y". Q: [x,z,y]
        assertEquals(listOf("x","z","y"), q.toList())

        assertFalse(q.remove("a")) // Item not present
        assertEquals(3, q.currentSize)

        assertTrue(q.remove("x")) // Q: [z,y]
        assertEquals(listOf("z","y"), q.toList())

        assertTrue(q.remove("y")) // Q: [z]
        assertEquals(listOf("z"), q.toList())

        assertTrue(q.remove("z")) // Q: []
        assertTrue(q.isEmpty)
        assertFalse(q.remove("z")) // remove from empty
    }

    @Test
    fun testClear() {
        val q = CirQlar<Double>(3)
        q.offer(1.0); q.offer(2.0)
        assertFalse(q.isEmpty)
        q.clear()
        assertTrue(q.isEmpty)
        assertEquals(0, q.currentSize)
        assertEquals(0, q.a) // from MutableIndexed (Indexed<T> is Join<Int, (Int)->T>, size is 'a')
        // Check if it can be reused
        q.offer(3.0)
        assertEquals(listOf(3.0), q.toList())
    }

    @Test
    fun testOperatorPlus() { // Returns new CirQlar
        val q1 = CirQlar<Int>(3)
        q1.offer(1); q1.offer(2) // [1,2]

        val q2 = q1 + 3 // q2 should be [1,2,3] (new queue)
        assertEquals(listOf(1,2), q1.toList(), "q1 should be unchanged")
        assertEquals(listOf(1,2,3), q2.toList(), "q2 has new item")
        // assertEquals(3, (q2 as CirQlar).capacity) // Cannot access private capacity

        val q3 = q2 + 4 // q2 is [1,2,3]. q3 is [2,3,4] (evicts 1 from q2's content for new queue)
        assertEquals(listOf(1,2,3), q2.toList(), "q2 should be unchanged")
        assertEquals(listOf(2,3,4), q3.toList(), "q3 has new item, oldest evicted")
    }

    @Test
    fun testOperatorMinus() { // Returns new CirQlar
        val q1 = CirQlar<Int>(3)
        q1.offer(1); q1.offer(2); q1.offer(1) // [1,2,1]

        val q2 = q1 - 1 // q2 should be [2,1] (removes first 1)
        assertEquals(listOf(1,2,1), q1.toList(), "q1 should be unchanged")
        assertEquals(listOf(2,1), q2.toList(), "q2 has first '1' removed")
        assertEquals(2, (q2 as CirQlar<Int>).currentSize) // Access currentSize after cast
        // assertEquals(3, (q2 as CirQlar).capacity) // Capacity remains same - Cannot access private capacity

        val q3 = q2 - 3 // Item not present in q2 ([2,1])
        assertEquals(listOf(2,1), q3.toList(), "q3 unchanged as item not found")
    }

    @Test
    fun testOperatorPlusAssign() {
        val q = CirQlar<String>(2)
        q += "A" // [A]
        assertEquals(listOf("A"), q.toList())
        q += "B" // [A,B]
        assertEquals(listOf("A","B"), q.toList())
        q += "C" // [B,C] (evicts A)
        assertEquals(listOf("B","C"), q.toList())
    }

    @Test
    fun testOperatorMinusAssign() {
        val q = CirQlar<String>(3)
        q.offer("m"); q.offer("n"); q.offer("m") // [m,n,m]
        q -= "m" // [n,m]
        assertEquals(listOf("n","m"), q.toList())
        q -= "p" // No change
        assertEquals(listOf("n","m"), q.toList())
        q -= "m" // [n]
        assertEquals(listOf("n"), q.toList())
        q -= "n" // []
        assertTrue(q.isEmpty)
        q -= "n" // no change from empty
        assertTrue(q.isEmpty)
    }

    @Test
    fun testZeroCapacityQueue() {
        val q = CirQlar<Int>(0)
        assertTrue(q.isEmpty)
        assertTrue(q.isFull)
        assertFalse(q.offer(1))
        assertNull(q.poll())
        assertFailsWith<NoSuchElementException> { q.peek() }
        assertTrue(q.toList().isEmpty())

        assertEquals(0, q.size)
        assertFailsWith<IndexOutOfBoundsException> { q[0] }
        assertFailsWith<IndexOutOfBoundsException> { q.set(0,1) }

        q.add(1) // offer is called, which does nothing for capacity 0.
        assertTrue(q.isEmpty)

        // For add(index, item), if index is 0 and count is 0, it's valid.
        // Then it calls offer(1), which fails for capacity 0.
        q.add(0, 1)
        assertTrue(q.isEmpty)

        assertFailsWith<IndexOutOfBoundsException> { q.removeAt(0) }
        assertFalse(q.remove(1))
        q.clear()
        assertTrue(q.isEmpty)
    }

    @Test
    fun testCapacityOneQueue() {
        val q = CirQlar<String>(1)
        q.offer("x") // [x]
        assertTrue(q.isFull)
        assertEquals("x", q.peek())
        q.offer("y") // [y] (evicts x)
        assertEquals("y", q.peek())
        assertEquals("y", q.poll()) // []
        assertTrue(q.isEmpty)

        q.add("z") // [z] via offer (add(item:T))
        assertEquals("z", q[0])
        q[0] = "Z" // [Z] via set
        assertEquals("Z", q[0])

        // q is [Z]. Add "A" at index 0.
        // isFull is true. index (0) < count (1). Evicts "Z". New head is (0+1)%1 = 0.
        // Shift loop is (1-2 downTo 0) - doesn't run.
        // insertArrIdx = (0+0)%1 = 0. buffer[0] = "A".
        // Count remains 1.
        q.add(0, "A")
        assertEquals(listOf("A"), q.toList())
        assertEquals("A", q.removeAt(0))
        assertTrue(q.isEmpty)
    }
}
