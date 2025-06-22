package borg.trikeshed.cursor

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.isam.meta.IOMemento
import kotlin.test.*

/**
 * Simple cursor tests ported from columnar project
 * Tests basic cursor functionality with TrikeShed's Series-based design
 */
class SimpleCursorTest {
    
    @Test
    fun testBasicCursorCreation() {
        // Create a simple cursor with string data
        val data = 3 j { i ->
            val rowData = 2 j { j -> "row${i}_col${j}" }
            rowData
        }
        
        assertEquals(3, data.size)
        assertEquals("row0_col0", data[0][0])
        assertEquals("row1_col1", data[1][1])
    }
    
    @Test
    fun testCursorWithMixedTypes() {
        // Create cursor with mixed data types
        val mixedData = 2 j { i ->
            val rowData = 3 j { j ->
                when (j) {
                    0 -> "string_$i"
                    1 -> i * 10
                    2 -> i * 1.5
                    else -> null
                }
            }
            rowData
        }
        
        assertEquals(2, mixedData.size)
        assertEquals("string_0", mixedData[0][0])
        assertEquals(0, mixedData[0][1])
        assertEquals(0.0, mixedData[0][2])
        assertEquals("string_1", mixedData[1][0])
        assertEquals(10, mixedData[1][1])
        assertEquals(1.5, mixedData[1][2])
    }
    
    @Test
    fun testCursorRowAccess() {
        val cursor = 4 j { i ->
            2 j { j -> i * 10 + j }
        }
        
        // Test row access
        val row0 = cursor[0]
        assertEquals(0, row0[0])
        assertEquals(1, row0[1])
        
        val row2 = cursor[2]
        assertEquals(20, row2[0])
        assertEquals(21, row2[1])
    }
    
    @Test
    fun testCursorColumnAccess() {
        val cursor = 3 j { i ->
            4 j { j -> i * 10 + j }
        }
        
        // Test column access by iterating through rows
        val column0 = cursor.size j { i -> cursor[i][0] }
        assertEquals(0, column0[0])
        assertEquals(10, column0[1])
        assertEquals(20, column0[2])
        
        val column1 = cursor.size j { i -> cursor[i][1] }
        assertEquals(1, column1[0])
        assertEquals(11, column1[1])
        assertEquals(21, column1[2])
    }
    
    @Test
    fun testCursorWithIOMemento() {
        // Test cursor with IOMemento metadata
        val stringMeta = IOMemento().apply {
            name = "name"
            type = "string"
            width = 50
            nullable = false
        }
        
        val intMeta = IOMemento().apply {
            name = "age"
            type = "int"
            width = 4
            nullable = false
        }
        
        val cursor = 2 j { i ->
            2 j { j ->
                when (j) {
                    0 -> "person_$i" j { stringMeta }
                    1 -> i * 25 j { intMeta }
                    else -> null j { IOMemento() }
                }
            }
        }
        
        assertEquals(2, cursor.size)
        assertEquals("person_0", cursor[0][0].a)
        assertEquals(0, cursor[0][1].a)
        assertEquals("person_1", cursor[1][0].a)
        assertEquals(25, cursor[1][1].a)
    }
    
    @Test
    fun testEmptyCursor() {
        val emptyCursor = 0 j { i -> 0 j { j -> "empty" } }
        assertEquals(0, emptyCursor.size)
    }
    
    @Test
    fun testSingleRowCursor() {
        val singleRow = 1 j { i ->
            3 j { j -> "value_$j" }
        }
        
        assertEquals(1, singleRow.size)
        assertEquals(3, singleRow[0].size)
        assertEquals("value_0", singleRow[0][0])
        assertEquals("value_1", singleRow[0][1])
        assertEquals("value_2", singleRow[0][2])
    }
} 