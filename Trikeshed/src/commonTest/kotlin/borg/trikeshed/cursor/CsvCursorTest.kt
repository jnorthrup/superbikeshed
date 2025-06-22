package borg.trikeshed.cursor

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.isam.meta.IOMemento
import kotlin.test.*

/**
 * CSV cursor tests ported from columnar project
 * Tests CSV parsing and cursor functionality with TrikeShed's Series-based design
 */
class CsvCursorTest {
    
    @Test
    fun testCsvCursorCreation() {
        // Create CSV-like data structure
        val csvData = listOf(
            "date,value,name",
            "2023-01-01,100.5,test1",
            "2023-01-02,200.3,test2",
            "2023-01-03,150.7,test3"
        )
        
        val cursor = csvData.size j { i ->
            val line = csvData[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        assertEquals(4, cursor.size) // header + 3 data rows
        assertEquals(3, cursor[0].size) // 3 columns
        assertEquals("date", cursor[0][0])
        assertEquals("value", cursor[0][1])
        assertEquals("name", cursor[0][2])
        assertEquals("2023-01-01", cursor[1][0])
        assertEquals("100.5", cursor[1][1])
        assertEquals("test1", cursor[1][2])
    }
    
    @Test
    fun testCsvCursorWithMetadata() {
        // Create CSV data with metadata
        val csvData = listOf(
            "date,value,name",
            "2023-01-01,100.5,test1",
            "2023-01-02,200.3,test2"
        )
        
        val metadata = 3 j { i ->
            when (i) {
                0 -> IOMemento().apply { name = "date"; type = "string"; width = 10 }
                1 -> IOMemento().apply { name = "value"; type = "double"; width = 8 }
                2 -> IOMemento().apply { name = "name"; type = "string"; width = 20 }
                else -> IOMemento()
            }
        }
        
        val cursor = csvData.size j { i ->
            val line = csvData[i]
            val fields = line.split(",")
            fields.size j { j -> 
                fields[j] j { metadata[j] }
            }
        }
        
        assertEquals(2, cursor.size) // header + 1 data row
        assertEquals(3, cursor[0].size)
        
        // Test metadata access
        assertEquals("date", cursor[0][0].b.name)
        assertEquals("string", cursor[0][0].b.type)
        assertEquals("value", cursor[0][1].b.name)
        assertEquals("double", cursor[0][1].b.type)
        assertEquals("name", cursor[0][2].b.name)
        assertEquals("string", cursor[0][2].b.type)
    }
    
    @Test
    fun testCsvCursorRowAccess() {
        val csvData = listOf(
            "col1,col2,col3",
            "a,1,true",
            "b,2,false",
            "c,3,true"
        )
        
        val cursor = csvData.size j { i ->
            val line = csvData[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Test row access
        val row1 = cursor[1]
        assertEquals("a", row1[0])
        assertEquals("1", row1[1])
        assertEquals("true", row1[2])
        
        val row3 = cursor[3]
        assertEquals("c", row3[0])
        assertEquals("3", row3[1])
        assertEquals("true", row3[2])
    }
    
    @Test
    fun testCsvCursorColumnExtraction() {
        val csvData = listOf(
            "name,age,city",
            "Alice,25,New York",
            "Bob,30,Los Angeles",
            "Charlie,35,Chicago"
        )
        
        val cursor = csvData.size j { i ->
            val line = csvData[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Extract name column (column 0)
        val nameColumn = cursor.size j { i -> cursor[i][0] }
        assertEquals("name", nameColumn[0])
        assertEquals("Alice", nameColumn[1])
        assertEquals("Bob", nameColumn[2])
        assertEquals("Charlie", nameColumn[3])
        
        // Extract age column (column 1)
        val ageColumn = cursor.size j { i -> cursor[i][1] }
        assertEquals("age", ageColumn[0])
        assertEquals("25", ageColumn[1])
        assertEquals("30", ageColumn[2])
        assertEquals("35", ageColumn[3])
    }
    
    @Test
    fun testCsvCursorWithEmptyFields() {
        val csvData = listOf(
            "col1,col2,col3",
            "a,,c",
            ",2,",
            "x,y,z"
        )
        
        val cursor = csvData.size j { i ->
            val line = csvData[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        assertEquals(4, cursor.size)
        assertEquals("", cursor[1][1]) // empty field
        assertEquals("", cursor[2][0]) // empty field
        assertEquals("", cursor[2][2]) // empty field
    }
    
    @Test
    fun testCsvCursorWithQuotedFields() {
        // Note: This is a simplified test - real CSV parsing would handle quotes properly
        val csvData = listOf(
            "name,description",
            "John,\"Hello, world\"",
            "Jane,\"Test\"\"quote\"",
            "Bob,Simple text"
        )
        
        val cursor = csvData.size j { i ->
            val line = csvData[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        assertEquals(4, cursor.size)
        assertEquals("\"Hello, world\"", cursor[1][1])
        assertEquals("\"Test\"\"quote\"", cursor[2][1])
        assertEquals("Simple text", cursor[3][1])
    }
    
    @Test
    fun testCsvCursorFiltering() {
        val csvData = listOf(
            "id,name,value",
            "1,Alice,100",
            "2,Bob,200",
            "3,Charlie,150",
            "4,David,300"
        )
        
        val cursor = csvData.size j { i ->
            val line = csvData[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Filter rows where value > 150
        val filteredRows = mutableListOf<Indexed<String>>()
        for (i in 1 until cursor.size) { // skip header
            val value = cursor[i][2].toIntOrNull() ?: 0
            if (value > 150) {
                filteredRows.add(cursor[i])
            }
        }
        
        assertEquals(2, filteredRows.size)
        assertEquals("2", filteredRows[0][0]) // Bob
        assertEquals("4", filteredRows[1][0]) // David
    }
} 