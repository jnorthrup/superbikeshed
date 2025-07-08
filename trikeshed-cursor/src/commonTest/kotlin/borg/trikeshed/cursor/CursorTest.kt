@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.cursor

import kotlin.test.*
import borg.trikeshed.lib.*

class CursorTest {
    
    @Test
    fun testBasicCursorOperations() {
        val data = listOf(
            listOf("Alice", 25, 75.5),
            listOf("Bob", 30, 80.0),
            listOf("Charlie", 35, 70.0)
        )
        val columnNames = listOf("name", "age", "weight")
        val cursor = cursorOf(data, columnNames)
        
        assertEquals(3, cursor.size)
        assertEquals("Alice", cursor.at(0).getString(0))
        assertEquals(25, cursor.at(0).getInt(1))
        assertEquals(75.5, cursor.at(0).getDouble(2))
    }
    
    @Test
    fun testNegativeIndexing() {
        val data = listOf(
            listOf("A", 1),
            listOf("B", 2),
            listOf("C", 3)
        )
        val cursor = cursorOf(data)
        
        assertEquals("C", cursor.at(-1).getString(0))
        assertEquals("B", cursor.at(-2).getString(0))
        assertEquals("A", cursor.at(-3).getString(0))
    }
    
    @Test
    fun testSlicing() {
        val data = listOf(
            listOf("A", 1),
            listOf("B", 2),
            listOf("C", 3),
            listOf("D", 4),
            listOf("E", 5)
        )
        val cursor = cursorOf(data)
        val slice = cursor.at(1..3)
        
        assertEquals(3, slice.size)
        assertEquals("B", slice.at(0).getString(0))
        assertEquals("C", slice.at(1).getString(0))
        assertEquals("D", slice.at(2).getString(0))
    }
    
    @Test
    fun testColumnAccess() {
        val data = listOf(
            listOf("Alice", 25),
            listOf("Bob", 30),
            listOf("Charlie", 35)
        )
        val columnNames = listOf("name", "age")
        val cursor = cursorOf(data, columnNames)
        
        val nameColumn = cursor.column("name")
        assertEquals(3, nameColumn.size)
        assertEquals("Alice", nameColumn.b(0))
        assertEquals("Bob", nameColumn.b(1))
        assertEquals("Charlie", nameColumn.b(2))
        assertEquals(3, nameColumn.size)
        
        val ageColumn = cursor.column(1)
        assertEquals(25, ageColumn.b(0))
        assertEquals(30, ageColumn.b(1))
        assertEquals(35, ageColumn.b(2))
    }
    
    @Test
    fun testIndexedAccess() {
        val data = listOf(
            listOf("A", 1),
            listOf("B", 2),
            listOf("C", 3),
            listOf("D", 4)
        )
        val cursor = cursorOf(data)
        val indexed = cursor[0, 2, 1]
        
        assertEquals(3, indexed.size)
        assertEquals("A", indexed.at(0).getString(0))
        assertEquals("C", indexed.at(1).getString(0))
        assertEquals("B", indexed.at(2).getString(0))
        assertEquals(3, indexed.size)
    }
    
    @Test
    fun testFiltering() {
        val data = listOf(
            listOf("Alice", 25),
            listOf("Bob", 30),
            listOf("Charlie", 35),
            listOf("Diana", 28)
        )
        val cursor = cursorOf(data)
        val filtered = cursor.filter { row ->
            val age = row.getInt(1)
            age != null && age >= 30
        }
        
        assertEquals(2, filtered.size)
        assertEquals("Bob", filtered.at(0).getString(0))
        assertEquals("Charlie", filtered.at(1).getString(0))
    }
    
    @Test
    fun testSorting() {
        val data = listOf(
            listOf("Charlie", 35),
            listOf("Alice", 25),
            listOf("Bob", 30)
        )
        val cursor = cursorOf(data)
        val sorted = cursor.sortBy(1) // Sort by age
        
        assertEquals("Alice", sorted.at(0).getString(0))
        assertEquals("Bob", sorted.at(1).getString(0))
        assertEquals("Charlie", sorted.at(2).getString(0))
    }
    
    @Test
    fun testGrouping() {
        val data = listOf(
            listOf("Engineering", "Alice"),
            listOf("Marketing", "Bob"),
            listOf("Engineering", "Charlie"),
            listOf("Marketing", "Diana")
        )
        val cursor = cursorOf(data, listOf("department", "name"))
        val groups = cursor.groupBy(0)
        
        assertEquals(2, groups.size)
        
        // Check that we have groups for both departments
        val group1 = groups.b(0)
        val group2 = groups.b(1)
        
        assertEquals(2, groups.size)
        assertTrue(group1.size == 2 || group2.size == 2)
    }
    
    @Test
    fun testAggregations() {
        val data = listOf(
            listOf("Item1", 10.5),
            listOf("Item2", 20.0),
            listOf("Item3", 15.5),
            listOf("Item4", 25.0)
        )
        val cursor = cursorOf(data)
        
        val sum = cursor.sumColumn(1)
        assertEquals(71.0, sum)
        
        val count = cursor.countColumn(0)
        assertEquals(4, count)
    }
    
    @Test
    fun testIteration() {
        val data = listOf(
            listOf("A", 1),
            listOf("B", 2),
            listOf("C", 3)
        )
        val cursor = cursorOf(data)
        
        val names = mutableListOf<String>()
        cursor.forEach { row ->
            names.add(row.getString(0) ?: "")
        }
        
        assertEquals(listOf("A", "B", "C"), names)
    }
    
    @Test
    fun testTypeSafety() {
        val data = listOf(
            listOf("Alice", 25, 75.5f, true)
        )
        val cursor = cursorOf(data)
        val row = cursor.at(0)
        
        assertEquals("Alice", row.getString(0))
        assertEquals(25, row.getInt(1))
        assertEquals(75.5f, row.getFloat(2))
        
        // Test type mismatches return null
        assertNull(row.getInt(0)) // String column
        assertNull(row.getString(1)) // Int column
        assertNull(row.getFloat(1)) // Int column
    }
    
    @Test
    fun testEmptyHandling() {
        assertFailsWith<IllegalArgumentException> {
            cursorOf(emptyList())
        }
    }
    
    @Test
    fun testMetadata() {
        val data = listOf(listOf("test", 123))
        val columnNames = listOf("str_col", "int_col")
        val cursor = cursorOf(data, columnNames)
        
        val names = cursor.columnNames
        assertEquals("str_col", names.b(0))
        assertEquals("int_col", names.b(1))
        
        val colIdx = cursor.colIdx
        assertEquals(0, colIdx["str_col"])
        assertEquals(1, colIdx["int_col"])
    }
}