package borg.trikeshed.collections

import kotlin.test.*

class TreeMapTest {

    @Test
    fun testEmptyMap() {
        val map = TreeMap<Int, String>()
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
    fun testPutAndGet() {
        val map = TreeMap<Int, String>()
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

        assertEquals("uno", map.get(1)) // Check existing key not affected
    }

    @Test
    fun testRemove() {
        val map = TreeMap<Int, String>()
        map.put(1, "one")
        map.put(2, "two")
        map.put(3, "three")
        assertEquals(3, map.size)

        assertEquals("two", map.remove(2))
        assertEquals(2, map.size)
        assertNull(map.get(2))
        assertFalse(map.containsKey(2))

        assertEquals("one", map.get(1))
        assertEquals("three", map.get(3))

        assertNull(map.remove(5)) // Remove non-existent
        assertEquals(2, map.size)

        assertEquals("one", map.remove(1))
        assertEquals(1, map.size)
        assertNull(map.get(1))

        assertEquals("three", map.remove(3))
        assertEquals(0, map.size)
        assertTrue(map.isEmpty())
    }

    @Test
    fun testContainsKeyAndValue() {
        val map = TreeMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)

        assertTrue(map.containsKey("a"))
        assertTrue(map.containsKey("b"))
        assertFalse(map.containsKey("c"))

        assertTrue(map.containsValue(1))
        assertTrue(map.containsValue(2))
        assertFalse(map.containsValue(3))
    }

    @Test
    fun testClearAndIsEmpty() {
        val map = TreeMap<Int, String>()
        map.put(1, "a")
        map.put(2, "b")
        assertFalse(map.isEmpty())
        assertEquals(2, map.size)

        map.clear()
        assertTrue(map.isEmpty())
        assertEquals(0, map.size)
        assertFalse(map.containsKey(1))
        assertNull(map.get(1))
    }

    @Test
    fun testSize() {
        val map = TreeMap<Int, Int>()
        assertEquals(0, map.size)
        map.put(1,1)
        assertEquals(1, map.size)
        map.put(2,2)
        assertEquals(2, map.size)
        map.put(1,10) // overwrite
        assertEquals(2, map.size)
        map.remove(1)
        assertEquals(1, map.size)
        map.clear()
        assertEquals(0, map.size)
    }

    @Test
    fun testNaturalOrderIntegerKeys() {
        val map = TreeMap<Int, String>()
        map.put(3, "three")
        map.put(1, "one")
        map.put(2, "two")
        map.put(5, "five")
        map.put(4, "four")

        val expectedKeys = listOf(1, 2, 3, 4, 5)
        val actualKeys = map.keys.toList()
        assertEquals(expectedKeys, actualKeys)

        val expectedEntries = listOf(
            1 to "one", 2 to "two", 3 to "three", 4 to "four", 5 to "five"
        ).map { (k,v) -> SimpleEntry(k,v) } // Assuming SimpleEntry for comparison if needed

        val actualEntries = map.entries.map { e -> SimpleEntry(e.key, e.value) }
         assertEquals(expectedEntries.map{it.toString()}, actualEntries.map{it.toString()}) // Compare string reps for simplicity
    }

    // Helper for entry comparison if direct comparison fails due to MutableEntry vs Entry
    private data class SimpleEntry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V>


    @Test
    fun testNaturalOrderStringKeys() {
        val map = TreeMap<String, Int>()
        map.put("c", 3)
        map.put("a", 1)
        map.put("b", 2)
        map.put("e", 5)
        map.put("d", 4)

        val expectedKeys = listOf("a", "b", "c", "d", "e")
        val actualKeys = map.keys.toList()
        assertEquals(expectedKeys, actualKeys)
    }

    @Test
    fun testCustomComparatorReverseOrder() {
        val reversedComparator = compareBy<Int> { -it } // Or Comparator<Int> { o1, o2 -> o2.compareTo(o1) }
        val map = TreeMap<Int, String>(reversedComparator)
        map.put(3, "three")
        map.put(1, "one")
        map.put(2, "two")
        map.put(5, "five")
        map.put(4, "four")

        val expectedKeys = listOf(5, 4, 3, 2, 1)
        val actualKeys = map.keys.toList()
        assertEquals(expectedKeys, actualKeys)
        assertNotNull(map.comparator())
    }

    @Test
    fun testCustomComparatorStringCaseInsensitive() {
        val caseInsensitiveComparator = Comparator<String> { s1, s2 -> s1.compareTo(s2, ignoreCase = true) }
        val map = TreeMap<String, Int>(caseInsensitiveComparator)
        map.put("apple", 1)
        map.put("Banana", 2) // Different case
        map.put("Cherry", 3)
        map.put("APPLE", 10) // Overwrite "apple" due to case-insensitivity

        assertEquals(3, map.size)
        assertEquals(10, map.get("apple"))
        assertEquals(10, map.get("APPLE"))
        assertEquals(2, map.get("banana"))
        assertTrue(map.containsKey("cherry"))

        val expectedKeys = listOf("APPLE", "Banana", "Cherry") // Order might depend on tie-breaking of original keys
                                                              // For "apple" and "APPLE", the one inserted last might be the key if comparator returns 0.
                                                              // Let's check if original "apple" is replaced by "APPLE"

        map.clear()
        map.put("apple", 1)
        map.put("Banana", 2)
        map.put("Cherry", 3)

        // If we put "APPLE" now, it should replace "apple"
        map.put("APPLE", 10)
        assertEquals(10, map["apple"]) // Accessing with lowercase "apple" should still work.
        assertEquals(10, map["APPLE"])

        val actualKeys = map.keys.toList()
        // The exact key stored ("apple" or "APPLE") when they are equal by comparator can vary.
        // The important part is that only one of them is present.
        // Standard Java TreeMap keeps the first key if new key is "equal" by comparator.
        // Our put logic replaces if key is "equal". Let's verify this.
        // If map.put("APPLE", 10) was after map.put("apple",1), then "APPLE" is the key.

        // Let's ensure the order is case-insensitive "Apple", "Banana", "Cherry"
        // The actual keys stored might be "APPLE", "Banana", "Cherry" or "apple", "Banana", "Cherry"
        // depending on insertion order and how tie-breaking for keys (that compare to 0) is handled.
        // Our put logic: if cmp == 0, it updates value. The key remains the one originally inserted.
        // So, if "apple" was first, then "APPLE" is put, key "apple" value becomes 10.

        map.clear()
        map.put("CHERRY", 3)
        map.put("banana", 2)
        map.put("Apple", 1)

        val expectedOrder = listOf("Apple", "banana", "CHERRY")
        assertEquals(expectedOrder, map.keys.toList())
    }


    @Test
    fun testFirstAndLastKey() {
        val map = TreeMap<Int, String>()
        assertFailsWith<NoSuchElementException> { map.firstKey() }
        assertFailsWith<NoSuchElementException> { map.lastKey() }

        map.put(3, "c")
        assertEquals(3, map.firstKey())
        assertEquals(3, map.lastKey())

        map.put(1, "a")
        assertEquals(1, map.firstKey())
        assertEquals(3, map.lastKey())

        map.put(5, "e")
        assertEquals(1, map.firstKey())
        assertEquals(5, map.lastKey())

        map.put(0, "z")
        assertEquals(0, map.firstKey())
    }

    @Test
    fun testComparatorMethod() {
        val mapNatural = TreeMap<Int, String>()
        assertNull(mapNatural.comparator())

        val customComparator = compareBy<Int> { it }
        val mapCustom = TreeMap<Int, String>(customComparator)
        assertSame(customComparator, mapCustom.comparator())
    }

    // --- Basic NavigableMap Operations ---
    @Test
    fun testNavigationMethodsSimple() {
        val map = TreeMap<Int, String>().apply {
            put(10, "A")
            put(20, "B")
            put(30, "C")
            put(40, "D")
            put(50, "E")
        }

        assertEquals(10, map.lowerKey(20)) // Key < 20
        assertEquals(10, map.floorKey(10)) // Key <= 10 (exact match)
        assertEquals(10, map.floorKey(15)) // Key <= 15

        assertEquals(20, map.ceilingKey(20)) // Key >= 20 (exact match)
        assertEquals(20, map.ceilingKey(15)) // Key >= 15

        assertEquals(20, map.higherKey(10))  // Key > 10

        assertNull(map.lowerKey(10))
        assertEquals(10, map.floorKey(10))
        assertEquals(10, map.ceilingKey(10))
        assertEquals(20, map.higherKey(10))

        assertNull(map.higherKey(50))
        assertEquals(50, map.floorKey(50))
        assertEquals(50, map.ceilingKey(50))
        assertEquals(40, map.lowerKey(50))

        // Entries
        assertEquals("A", map.lowerEntry(20)?.value)
        assertEquals("B", map.floorEntry(20)?.value)
        assertEquals("B", map.ceilingEntry(20)?.value)
        assertEquals("C", map.higherEntry(20)?.value)
    }

    @Test
    fun testNavigationMethodsAtBoundariesAndOutOfRange() {
        val map = TreeMap<Int, String>().apply {
            put(10, "A")
            put(30, "C")
            put(50, "E")
        }
        // Lower
        assertNull(map.lowerKey(5))
        assertNull(map.lowerEntry(5))
        assertNull(map.lowerKey(10))
        assertNull(map.lowerEntry(10))
        assertEquals(10, map.lowerKey(11))
        assertEquals(10, map.lowerKey(30))

        // Floor
        assertNull(map.floorKey(5))
        assertNull(map.floorEntry(5))
        assertEquals(10, map.floorKey(10))
        assertEquals("A", map.floorEntry(10)?.value)
        assertEquals(10, map.floorKey(20))
        assertEquals("A", map.floorEntry(20)?.value)
        assertEquals(50, map.floorKey(50))
        assertEquals(50, map.floorKey(60))

        // Ceiling
        assertNull(map.ceilingKey(55))
        assertNull(map.ceilingEntry(55))
        assertEquals(50, map.ceilingKey(50))
        assertEquals("E", map.ceilingEntry(50)?.value)
        assertEquals(50, map.ceilingKey(40))
        assertEquals("E", map.ceilingEntry(40)?.value)
        assertEquals(10, map.ceilingKey(10))
        assertEquals(10, map.ceilingKey(5))

        // Higher
        assertNull(map.higherKey(55))
        assertNull(map.higherEntry(55))
        assertNull(map.higherKey(50))
        assertNull(map.higherEntry(50))
        assertEquals(50, map.higherKey(49))
        assertEquals(50, map.higherKey(30))
    }


    @Test
    fun testFirstLastPollEntries() {
        val map = TreeMap<Int, String>()
        assertNull(map.firstEntry())
        assertNull(map.lastEntry())
        assertNull(map.pollFirstEntry())
        assertNull(map.pollLastEntry())

        map.put(10, "A")
        assertEquals(10 to "A", map.firstEntry()?.toPair())
        assertEquals(10 to "A", map.lastEntry()?.toPair())

        map.put(20, "B")
        map.put(5, "Z")
        assertEquals(5 to "Z", map.firstEntry()?.toPair())
        assertEquals(20 to "B", map.lastEntry()?.toPair())

        // PollFirst
        assertEquals(3, map.size)
        val fe = map.pollFirstEntry()
        assertNotNull(fe)
        assertEquals(5, fe.key)
        assertEquals("Z", fe.value)
        assertEquals(2, map.size)
        assertEquals(10, map.firstKey())
        assertFalse(map.containsKey(5))

        // PollLast
        val le = map.pollLastEntry()
        assertNotNull(le)
        assertEquals(20, le.key)
        assertEquals("B", le.value)
        assertEquals(1, map.size)
        assertEquals(10, map.lastKey())
        assertFalse(map.containsKey(20))

        // Poll remaining
        assertEquals(10 to "A", map.pollFirstEntry()?.toPair())
        assertTrue(map.isEmpty())
        assertNull(map.pollFirstEntry())
        assertNull(map.pollLastEntry())
    }

    private fun <K,V> Map.Entry<K,V>.toPair(): Pair<K,V> = key to value

    // --- Iterators ---
    @Test
    fun testKeySetIterator() {
        val map = TreeMap<Int, String>()
        map.put(1, "a")
        map.put(2, "b")
        map.put(3, "c")

        val keyIterator = map.keys.iterator()
        assertTrue(keyIterator.hasNext())
        assertEquals(1, keyIterator.next())
        assertTrue(keyIterator.hasNext())
        assertEquals(2, keyIterator.next())

        keyIterator.remove() // Remove 2
        assertFalse(map.containsKey(2))
        assertEquals(2, map.size)

        assertTrue(keyIterator.hasNext())
        assertEquals(3, keyIterator.next())
        assertFalse(keyIterator.hasNext())
        assertFailsWith<NoSuchElementException> { keyIterator.next() }
    }

    @Test
    fun testValuesIterator() {
        val map = TreeMap<Int, String>()
        map.put(1, "a")
        map.put(2, "b")
        map.put(3, "c")

        val valuesIterator = map.values.iterator()
        assertTrue(valuesIterator.hasNext())
        assertEquals("a", valuesIterator.next())
        assertTrue(valuesIterator.hasNext())
        assertEquals("b", valuesIterator.next())

        valuesIterator.remove() // Remove entry for "b" (key 2)
        assertFalse(map.containsKey(2))
        assertEquals(2, map.size)
        assertFalse(map.containsValue("b"))

        assertTrue(valuesIterator.hasNext())
        assertEquals("c", valuesIterator.next())
        assertFalse(valuesIterator.hasNext())
    }

    @Test
    fun testEntrySetIteratorAndRemove() {
        val map = TreeMap<Int, String>()
        map.put(1, "one")
        map.put(2, "two")
        map.put(3, "three")

        val entryIterator = map.entries.iterator()
        assertTrue(entryIterator.hasNext())
        var entry = entryIterator.next()
        assertEquals(1, entry.key)
        assertEquals("one", entry.value)

        assertTrue(entryIterator.hasNext())
        entry = entryIterator.next()
        assertEquals(2, entry.key)
        assertEquals("two", entry.value)

        entryIterator.remove() // Remove entry (2, "two")
        assertEquals(2, map.size)
        assertFalse(map.containsKey(2))
        assertNull(map.get(2))

        assertTrue(entryIterator.hasNext())
        entry = entryIterator.next()
        assertEquals(3, entry.key)

        assertFalse(entryIterator.hasNext())
        assertFailsWith<NoSuchElementException> { entryIterator.next() }

        // Test removing last element returned by iterator
        val map2 = TreeMap<Int, String>().apply{ put(1,"a"); put(2,"b")}
        val iter2 = map2.entries.iterator()
        iter2.next() // 1=a
        iter2.next() // 2=b
        iter2.remove() // remove 2=b
        assertEquals(1, map2.size)
        assertFalse(map2.containsKey(2))
    }

    @Test
    fun testIteratorRemoveIllegalState() {
        val map = TreeMap<Int, String>()
        map.put(1, "a")
        val iter = map.keys.iterator()
        assertFailsWith<IllegalStateException> { iter.remove() } // Remove before next
        iter.next()
        iter.remove() // Valid remove
        assertFailsWith<IllegalStateException> { iter.remove() } // Remove again
    }

    @Test
    fun testConcurrentModificationExceptionForIterators() {
        val map = TreeMap<Int, String>()
        map.put(1, "a")
        map.put(2, "b")

        val keyIter = map.keys.iterator()
        assertTrue(keyIter.hasNext())
        assertEquals(1, keyIter.next())

        map.put(3, "c") // Modify map directly

        assertFailsWith<ConcurrentModificationException> { keyIter.hasNext() }
        assertFailsWith<ConcurrentModificationException> { keyIter.next() }
        assertFailsWith<ConcurrentModificationException> { keyIter.remove() }

        // Test on values iterator
        val mapValues = TreeMap<Int, String>().apply { put(1,"a"); put(2,"b")}
        val valIter = mapValues.values.iterator()
        valIter.next()
        mapValues.remove(1)
        assertFailsWith<ConcurrentModificationException> { valIter.hasNext() }

        // Test on entries iterator
        val mapEntries = TreeMap<Int, String>().apply { put(1,"a"); put(2,"b")}
        val entryIter = mapEntries.entries.iterator()
        entryIter.next()
        mapEntries.put(3,"c")
        assertFailsWith<ConcurrentModificationException> { entryIter.next() }
    }

    // --- Collection Views ---
    @Test
    fun testKeySetView() {
        val map = TreeMap<Int, String>()
        map.put(1, "a"); map.put(2, "b"); map.put(3, "c")
        val keys = map.keys

        assertEquals(3, keys.size)
        assertTrue(keys.contains(1))
        assertFalse(keys.contains(4))
        assertTrue(keys.containsAll(listOf(1, 2)))
        assertFalse(keys.containsAll(listOf(1, 5)))

        assertTrue(keys.remove(2))
        assertEquals(2, map.size)
        assertFalse(map.containsKey(2))
        assertFalse(keys.contains(2))

        keys.clear()
        assertTrue(map.isEmpty())
        assertTrue(keys.isEmpty())
    }

    @Test
    fun testValuesView() {
        val map = TreeMap<Int, String>()
        map.put(1, "a"); map.put(2, "b"); map.put(3, "a") // Duplicate value "a"
        val values = map.values

        assertEquals(3, values.size)
        assertTrue(values.contains("a"))
        assertTrue(values.contains("b"))
        assertFalse(values.contains("d"))

        assertTrue(values.remove("b")) // Removes entry (2, "b")
        assertEquals(2, map.size)
        assertFalse(map.containsKey(2))

        assertTrue(values.remove("a")) // Removes first "a" (entry 1)
        assertEquals(1, map.size)
        assertTrue(map.containsKey(3)) // Entry (3,"a") should still be there
        assertFalse(map.containsKey(1))

        values.clear()
        assertTrue(map.isEmpty())
    }

    @Test
    fun testEntrySetView() {
        val map = TreeMap<Int, String>()
        map.put(1, "a"); map.put(2, "b")
        val entries = map.entries

        assertEquals(2, entries.size)
        // Note: For contains/remove on entry set, the entry must match exactly (key and value)
        // We need a way to create Map.Entry instances for testing.
        // Our SimpleMutableEntry won't work if the set contains SimpleEntry directly.
        // The set contains instances of an internal MutableMap.MutableEntry.
        // Let's iterate and find:
        assertTrue(entries.any { it.key == 1 && it.value == "a" })
        assertFalse(entries.any { it.key == 1 && it.value == "b" }) // Wrong value

        // Test remove
        // To remove "1=a", we need an actual entry. Iteration and remove is one way.
        // Or construct an entry that would be equal.
        // For simplicity, test remove via iterator or known key.
        // The AbstractSet.remove(element) will iterate and call equals.
        // Our SimpleMutableEntry has equals/hashCode based on key/value.

        // This test relies on the internal SimpleMutableEntry's equals method
        assertTrue(entries.remove(map.SimpleMutableEntry(1, "a")))
        assertEquals(1, map.size)
        assertFalse(map.containsKey(1))

        entries.clear()
        assertTrue(map.isEmpty())
    }

    @Test
    fun testNullValues() {
        val map = TreeMap<Int, String?>()
        map.put(1, "a")
        map.put(2, null)
        map.put(3, "c")

        assertEquals("a", map.get(1))
        assertNull(map.get(2))
        assertEquals("c", map.get(3))
        assertTrue(map.containsKey(2)) // Key for null value is present
        assertTrue(map.containsValue(null))
        assertFalse(map.containsValue("d"))
        assertTrue(map.containsValue("a"))

        assertEquals(3, map.size)

        // Remove entry with null value
        assertNull(map.remove(2))
        assertEquals(2, map.size)
        assertFalse(map.containsKey(2))
        assertFalse(map.containsValue(null)) // Assuming it was the only null

        map.put(4, null)
        assertTrue(map.containsValue(null))

        // Iterate values
        val values = map.values.toList()
        assertEquals(listOf("a", "c", null), values.sortedWith(compareBy(nullsLast()) { it }))
    }

    // --- Descending Map Tests ---
    @Test
    fun testDescendingMapIteration() {
        val map = TreeMap<Int, String>().apply {
            put(1, "a"); put(2, "b"); put(3, "c")
        }
        val descendingMap = map.descendingMap()

        val expectedKeys = listOf(3, 2, 1)
        assertEquals(expectedKeys, descendingMap.keys.toList())

        val expectedValues = listOf("c", "b", "a")
        assertEquals(expectedValues, descendingMap.values.toList())

        val expectedEntries = listOf(3 to "c", 2 to "b", 1 to "a")
        assertEquals(expectedEntries, descendingMap.entries.map { it.toPair() })
    }

    @Test
    fun testDescendingMapNavigation() {
        val map = TreeMap<Int, String>().apply {
            put(10, "A"); put(20, "B"); put(30, "C"); put(40, "D")
        }
        val descendingMap = map.descendingMap()

        assertEquals(40, descendingMap.firstKey())
        assertEquals(10, descendingMap.lastKey())
        assertEquals(40 to "D", descendingMap.firstEntry()?.toPair())
        assertEquals(10 to "A", descendingMap.lastEntry()?.toPair())

        assertEquals(20, descendingMap.lowerKey(10)) // Equivalent to map.higherKey(10)
        assertEquals(30, descendingMap.higherKey(40)) // Equivalent to map.lowerKey(40)
        assertEquals(20, descendingMap.floorKey(10))   // Equivalent to map.ceilingKey(10) but from other end.
                                                       // descending floor(k) == original ceiling(k)
        assertEquals(30, descendingMap.ceilingKey(40)) // descending ceiling(k) == original floor(k)


        // Test specific NavigableMap methods on descending view
        assertEquals(map.higherKey(20), descendingMap.lowerKey(20))
        assertEquals(map.lowerKey(20), descendingMap.higherKey(20))
        assertEquals(map.ceilingKey(20), descendingMap.floorKey(20))
        assertEquals(map.floorKey(20), descendingMap.ceilingKey(20))
    }

    @Test
    fun testDescendingMapModificationsReflectInOriginal() {
        val map = TreeMap<Int, String>().apply {
            put(1, "a"); put(2, "b")
        }
        val descendingMap = map.descendingMap()

        descendingMap.put(3, "c")
        assertTrue(map.containsKey(3))
        assertEquals("c", map.get(3))
        assertEquals(3, map.size)

        descendingMap.remove(1)
        assertFalse(map.containsKey(1))
        assertEquals(2, map.size)

        // Test modifying original reflects in descending
        map.put(0, "z")
        assertTrue(descendingMap.containsKey(0))
        assertEquals("z", descendingMap.get(0))
        assertEquals(0, descendingMap.lastKey()) // last in descending is smallest
    }

    @Test
    fun testDescendingMapOfDescendingMap() {
        val map = TreeMap<Int, String>().apply {
            put(1, "a"); put(2, "b"); put(3, "c")
        }
        val dMap = map.descendingMap()
        val ddMap = dMap.descendingMap()

        assertEquals(map.keys.toList(), ddMap.keys.toList())
        assertEquals(map.entries.map {it.toPair()}, ddMap.entries.map {it.toPair()})

        // Ensure operations on ddMap affect original map
        ddMap.put(4, "d")
        assertTrue(map.containsKey(4))
        assertEquals(map.get(4), "d")

        ddMap.remove(1)
        assertFalse(map.containsKey(1))
    }

    @Test
    fun testDescendingKeySet() {
        val map = TreeMap<Int, String>().apply {
            put(1, "a"); put(2, "b"); put(3, "c")
        }
        val descendingKeys = map.navigableKeySet().descendingSet()
        assertEquals(listOf(3, 2, 1), descendingKeys.toList())

        val descendingKeys2 = map.descendingKeySet()
        assertEquals(listOf(3, 2, 1), descendingKeys2.toList())

        // Test iterator from descendingKeySet
        val iter = descendingKeys.iterator()
        assertTrue(iter.hasNext())
        assertEquals(3, iter.next())
        assertEquals(2, iter.next())
        assertEquals(1, iter.next())
        assertFalse(iter.hasNext())
    }

    @Test
    fun testDescendingMapOnEmptyMap() {
        val map = TreeMap<Int, String>()
        val descendingMap = map.descendingMap()
        assertTrue(descendingMap.isEmpty())
        assertEquals(0, descendingMap.size)
        assertFailsWith<NoSuchElementException> { map.firstKey() } // Original map throws
        // Corrected expectation for descending map on empty: should also throw for firstKey/lastKey
        assertFailsWith<NoSuchElementException> { descendingMap.firstKey() }
        assertFailsWith<NoSuchElementException> { descendingMap.lastKey() }
    }

    // --- SubMap Tests ---
    @Test
    fun testSubMapCreationAndBasicProperties() {
        val map = TreeMap<Int, String>().apply {
            put(1, "a"); put(2, "b"); put(3, "c"); put(4, "d"); put(5, "e")
        }

        // subMap(fromInclusive, toExclusive)
        val subMap1 = map.subMap(2, 4) // Keys 2, 3
        assertEquals(2, subMap1.size)
        assertTrue(subMap1.containsKey(2))
        assertTrue(subMap1.containsKey(3))
        assertFalse(subMap1.containsKey(1))
        assertFalse(subMap1.containsKey(4))
        assertEquals("b", subMap1.get(2))
        assertEquals(2, subMap1.firstKey())
        assertEquals(3, subMap1.lastKey())

        // headMap(toExclusive)
        val headMap1 = map.headMap(3) // Keys 1, 2
        assertEquals(2, headMap1.size)
        assertTrue(headMap1.containsKey(1))
        assertTrue(headMap1.containsKey(2))
        assertFalse(headMap1.containsKey(3))
        assertEquals(1, headMap1.firstKey())
        assertEquals(2, headMap1.lastKey())

        // tailMap(fromInclusive)
        val tailMap1 = map.tailMap(4) // Keys 4, 5
        assertEquals(2, tailMap1.size)
        assertTrue(tailMap1.containsKey(4))
        assertTrue(tailMap1.containsKey(5))
        assertFalse(tailMap1.containsKey(3))
        assertEquals(4, tailMap1.firstKey())
        assertEquals(5, tailMap1.lastKey())
    }

    @Test
    fun testSubMapInclusiveExclusive() {
        val map = TreeMap<Int, String>().apply {
            (1..5).forEach { put(it, it.toString()) }
        }
        // subMap(from, fromInclusive, to, toInclusive)
        val sub = map.subMap(2, true, 4, true) // 2,3,4
        assertEquals(listOf(2,3,4), sub.keys.toList())

        val subExcl = map.subMap(2, false, 4, false) // 3
        assertEquals(listOf(3), subExcl.keys.toList())

        val headIncl = map.headMap(3, true) // 1,2,3
        assertEquals(listOf(1,2,3), headIncl.keys.toList())

        val tailExcl = map.tailMap(3, false) // 4,5
        assertEquals(listOf(4,5), tailExcl.keys.toList())
    }

    @Test
    fun testSubMapRangeChecks() {
        val map = TreeMap<Int, String>().apply {
            (1..5).forEach { put(it, it.toString()) }
        }
        val subMap = map.subMap(2, 4) // Keys 2, 3. View of [2, 4)

        assertNull(subMap.get(1))
        assertFalse(subMap.containsKey(4))

        assertFailsWith<IllegalArgumentException> { subMap.put(1, "x") } // Key out of range
        assertFailsWith<IllegalArgumentException> { subMap.put(4, "y") } // Key out of range (exclusive toKey)

        // Test remove out of range (should not remove from original)
        assertNull(subMap.remove(1))
        assertEquals(5, map.size)
    }

    @Test
    fun testSubMapModificationsReflectInOriginal() {
        val map = TreeMap<Int, String>().apply {
            (1..5).forEach { put(it, it.toString()) }
        }
        val subMap = map.subMap(2, true, 4, true) // Keys 2, 3, 4

        subMap.put(3, "cc")
        assertEquals("cc", map.get(3))
        assertEquals(5, map.size) // Size of original map unchanged

        subMap.remove(2)
        assertFalse(map.containsKey(2))
        assertEquals(4, map.size) // Size of original map changes
        assertEquals(2, subMap.size) // Size of submap also changes

        // Modify original map, check submap
        map.put(4, "dd") // Key 4 is in subMap range
        assertEquals("dd", subMap.get(4))

        map.remove(3) // Key 3 is in subMap range
        assertFalse(subMap.containsKey(3))
        assertEquals(1, subMap.size) // 4="dd"

        map.put(0, "zero") // Key 0 is outside subMap range
        assertNull(subMap.get(0))
        assertFalse(subMap.containsKey(0))
        assertEquals(1, subMap.size)
    }

    @Test
    fun testSubMapIterators() {
        val map = TreeMap<Int, String>().apply {
            (1..5).forEach { put(it, it.toString()) }
        }
        val subMap = map.subMap(2, true, 4, true) // 2, 3, 4

        assertEquals(listOf(2,3,4), subMap.keys.toList())
        assertEquals(listOf("2","3","4"), subMap.values.toList())
        assertEquals(listOf(2 to "2", 3 to "3", 4 to "4"), subMap.entries.map{it.toPair()})

        val keyIter = subMap.keys.iterator()
        assertEquals(2, keyIter.next())
        keyIter.remove() // Remove 2
        assertFalse(subMap.containsKey(2))
        assertFalse(map.containsKey(2))
        assertEquals(listOf(3,4), subMap.keys.toList())
    }

    @Test
    fun testSubMapNavigation() {
        val map = TreeMap<Int, String>().apply {
            (1..10 step 2).forEach { put(it, it.toString()) } // 1,3,5,7,9
        }
        val subMap = map.subMap(3, true, 7, true) // Keys 3,5,7

        assertEquals(3, subMap.firstKey())
        assertEquals(7, subMap.lastKey())

        assertEquals(3, subMap.ceilingKey(2))
        assertEquals(3, subMap.ceilingKey(3))
        assertEquals(5, subMap.ceilingKey(4))
        assertEquals(7, subMap.ceilingKey(7))
        assertNull(subMap.ceilingKey(8))

        assertEquals(7, subMap.floorKey(8))
        assertEquals(7, subMap.floorKey(7))
        assertEquals(5, subMap.floorKey(6))
        assertEquals(3, subMap.floorKey(3))
        assertNull(subMap.floorKey(2))

        assertEquals(5, subMap.higherKey(3))
        assertNull(subMap.higherKey(7))

        assertEquals(5, subMap.lowerKey(7))
        assertNull(subMap.lowerKey(3))
    }

    @Test
    fun testSubMapOfSubMap() {
        val map = TreeMap<Int, String>().apply { (1..10).forEach { put(it, it.toString()) } }
        val sub1 = map.subMap(3, 8) // 3,4,5,6,7
        val sub2 = sub1.subMap(5, 7) // 5,6 (from sub1's perspective, which is [5,7) of original)

        assertEquals(listOf(5,6), sub2.keys.toList())
        assertEquals(5, sub2.firstKey())
        assertEquals(6, sub2.lastKey())

        sub2.put(5, "SS")
        assertEquals("SS", map.get(5))
        assertEquals("SS", sub1.get(5))
    }

    @Test
    fun testSubMapOfDescendingMap() {
        val map = TreeMap<Int, String>().apply { (1..5).forEach { put(it, it.toString()) } }
        val descendingMap = map.descendingMap() // 5,4,3,2,1

        // Submap on descending map: keys are still compared naturally, but iteration is reversed.
        // A submap of a descending map is also descending.
        // subMap(from, to) on descending means (to, from] in original order, iterated descendingly.
        // fromKey=4 (exclusive), toKey=2 (inclusive) for descendingMap means (2, 4] of original keys, iterated descendingly.
        // So keys are 4, 3.
        val subDesc = descendingMap.subMap(2, true, 4, true) // Keys 4,3,2 from descending view [4..2]
                                                                 // Equivalent to original map's (2, true, 4, true) but iterated descendingly.
        assertEquals(listOf(4,3,2), subDesc.keys.toList())
        assertEquals(4, subDesc.firstKey()) // First in descending submap
        assertEquals(2, subDesc.lastKey())  // Last in descending submap

        assertTrue(subDesc.containsKey(3))
        subDesc.put(3, "CC")
        assertEquals("CC", map.get(3))
    }

    @Test
    fun testEmptySubMap() {
        val map = TreeMap<Int, String>().apply { (1..5).forEach { put(it, it.toString()) } }
        val emptySub = map.subMap(3,3) // Empty range [3,3)
        assertTrue(emptySub.isEmpty())
        assertEquals(0, emptySub.size)
        assertNull(emptySub.firstKey()) // Should be null or throw, depending on NavigableMap spec for empty.
                                        // Our current impl might throw from firstEntry() being null.
        assertFailsWith<NoSuchElementException> { emptySub.firstKey() }

        val emptySub2 = map.subMap(7,9)
        assertTrue(emptySub2.isEmpty())
    }
}
