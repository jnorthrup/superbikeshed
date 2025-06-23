package borg.trikeshed.trie

import borg.trikeshed.lib.*
import kotlin.test.*

class ArrayMapTest {
    @Test
    fun testArrayMapBasic() {
        val keys = 3 j { it }
        val values = 3 j { it * 10 }
        val map = ArrayMap.fromSeries(keys, values)
        assertEquals(3, map.size)
        assertEquals(0, map.keys.b(0))
        assertEquals(0, map.values.b(0))
        assertEquals(10, map.getValue(1))
        assertTrue(map.containsKey(2))
        assertFalse(map.containsKey(99))
    }

    @Test
    fun testArrayMapFromMapInterop() {
        val stdMap = mapOf("a" to 1, "b" to 2)
        val arrayMap = ArrayMap.fromMap(stdMap)
        val asMap = arrayMap.asMap()
        assertEquals(2, asMap.size)
        assertEquals(1, asMap["a"])
        assertEquals(2, asMap["b"])
        assertNull(asMap["z"])
    }

    @Test
    fun testArrayMapEmpty() {
        val empty = ArrayMap.fromMap(emptyMap<String, Int>())
        assertEquals(0, empty.size)
        assertFalse(empty.containsKey("x"))
        assertTrue(empty.asMap().isEmpty())
    }
} 