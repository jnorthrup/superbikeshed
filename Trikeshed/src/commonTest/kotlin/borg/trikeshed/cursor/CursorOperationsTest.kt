package borg.trikeshed.cursor

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.lib.Join
import borg.trikeshed.isam.meta.IOMemento
import kotlin.test.*

/**
 * Cursor operations tests ported from columnar project
 * Tests specific operations like ninety-degree rotation and other transformations
 */
class CursorOperationsTest {
    
    @Test
    fun testNinetyDegreeRotation() {
        // Create a 3x3 matrix
        val data = listOf(
            "col1,col2,col3",
            "a,b,c",
            "d,e,f",
            "g,h,i"
        )
        
        val cursor = data.size j { i ->
            val line = data[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Rotate 90 degrees clockwise (transpose and reverse rows)
        val rotated = cursor[0].size j { col ->
            cursor.size j { row ->
                cursor[cursor.size - 1 - row][col]
            }
        }
        
        assertEquals(3, rotated.size) // 3 columns become 3 rows
        assertEquals(4, rotated[0].size) // 4 rows become 4 columns
        
        // Verify rotation
        // Original: [a,b,c], [d,e,f], [g,h,i]
        // Rotated:  [g,d,a], [h,e,b], [i,f,c]
        assertEquals("g", rotated[0][0])
        assertEquals("d", rotated[0][1])
        assertEquals("a", rotated[0][2])
        assertEquals("col1", rotated[0][3])
        
        assertEquals("h", rotated[1][0])
        assertEquals("e", rotated[1][1])
        assertEquals("b", rotated[1][2])
        assertEquals("col2", rotated[1][3])
        
        assertEquals("i", rotated[2][0])
        assertEquals("f", rotated[2][1])
        assertEquals("c", rotated[2][2])
        assertEquals("col3", rotated[2][3])
    }
    
    @Test
    fun testCursorSlicing() {
        val data = listOf(
            "id,name,age,city,salary",
            "1,Alice,25,NYC,50000",
            "2,Bob,30,LA,60000",
            "3,Charlie,35,CHI,70000",
            "4,David,40,SF,80000",
            "5,Eve,28,BOS,55000"
        )
        
        val cursor = data.size j { i ->
            val line = data[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Test row slicing
        val rowSlice = 3 j { i -> cursor[i + 1] } // rows 1, 2, 3 (skip header)
        assertEquals(3, rowSlice.size)
        assertEquals("1", rowSlice[0][0])
        assertEquals("2", rowSlice[1][0])
        assertEquals("3", rowSlice[2][0])
        
        // Test column slicing
        val colSlice = cursor.size j { i ->
            2 j { j -> cursor[i][j + 1] } // columns 1, 2 (name, age)
        }
        assertEquals(6, colSlice.size)
        assertEquals("name", colSlice[0][0])
        assertEquals("age", colSlice[0][1])
        assertEquals("Alice", colSlice[1][0])
        assertEquals("25", colSlice[1][1])
        
        // Test mixed slicing
        val mixedSlice = 2 j { i ->
            2 j { j -> cursor[i + 2][j + 1] } // rows 2,3, columns 1,2
        }
        assertEquals(2, mixedSlice.size)
        assertEquals("Bob", mixedSlice[0][0])
        assertEquals("30", mixedSlice[0][1])
        assertEquals("Charlie", mixedSlice[1][0])
        assertEquals("35", mixedSlice[1][1])
    }
    
    @Test
    fun testCursorFiltering() {
        val data = listOf(
            "id,name,age,salary",
            "1,Alice,25,50000",
            "2,Bob,30,60000",
            "3,Charlie,35,70000",
            "4,David,40,80000",
            "5,Eve,28,55000"
        )
        
        val cursor = data.size j { i ->
            val line = data[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Filter by age > 30
        val ageFiltered = mutableListOf<Indexed<String>>()
        for (i in 1 until cursor.size) {
            val age = cursor[i][2].toIntOrNull() ?: 0
            if (age > 30) {
                ageFiltered.add(cursor[i])
            }
        }
        
        assertEquals(2, ageFiltered.size)
        assertEquals("3", ageFiltered[0][0]) // Charlie
        assertEquals("4", ageFiltered[1][0]) // David
        
        // Filter by salary range
        val salaryFiltered = mutableListOf<Indexed<String>>()
        for (i in 1 until cursor.size) {
            val salary = cursor[i][3].toIntOrNull() ?: 0
            if (salary >= 55000 && salary <= 65000) {
                salaryFiltered.add(cursor[i])
            }
        }
        
        assertEquals(3, salaryFiltered.size)
        assertEquals("2", salaryFiltered[0][0]) // Bob
        assertEquals("5", salaryFiltered[1][0]) // Eve
        assertEquals("3", salaryFiltered[2][0]) // Charlie
    }
    
    @Test
    fun testCursorSorting() {
        val data = listOf(
            "id,name,age,salary",
            "1,Alice,25,50000",
            "2,Bob,30,60000",
            "3,Charlie,35,70000",
            "4,David,40,80000",
            "5,Eve,28,55000"
        )
        
        val cursor = data.size j { i ->
            val line = data[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Sort by age
        val ageSorted = (1 until cursor.size).map { cursor[it] }
            .sortedBy { it[2].toIntOrNull() ?: 0 }
            .toMutableList()
        
        assertEquals(5, ageSorted.size)
        assertEquals("1", ageSorted[0][0]) // Alice, 25
        assertEquals("5", ageSorted[1][0]) // Eve, 28
        assertEquals("2", ageSorted[2][0]) // Bob, 30
        assertEquals("3", ageSorted[3][0]) // Charlie, 35
        assertEquals("4", ageSorted[4][0]) // David, 40
        
        // Sort by salary descending
        val salarySorted = (1 until cursor.size).map { cursor[it] }
            .sortedByDescending { it[3].toIntOrNull() ?: 0 }
            .toMutableList()
        
        assertEquals(5, salarySorted.size)
        assertEquals("4", salarySorted[0][0]) // David, 80000
        assertEquals("3", salarySorted[1][0]) // Charlie, 70000
        assertEquals("2", salarySorted[2][0]) // Bob, 60000
        assertEquals("5", salarySorted[3][0]) // Eve, 55000
        assertEquals("1", salarySorted[4][0]) // Alice, 50000
    }
    
    @Test
    fun testCursorAggregationByColumn() {
        val data = listOf(
            "dept,name,salary",
            "Engineering,Alice,50000",
            "Engineering,Bob,60000",
            "Marketing,Charlie,55000",
            "Engineering,David,70000",
            "Sales,Eve,45000",
            "Marketing,Frank,65000"
        )
        
        val cursor = data.size j { i ->
            val line = data[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Aggregate by department
        val deptAggregations = mutableMapOf<String, MutableList<Int>>()
        for (i in 1 until cursor.size) {
            val dept = cursor[i][0]
            val salary = cursor[i][2].toIntOrNull() ?: 0
            val group = deptAggregations.getOrPut(dept) { mutableListOf() }
            group.add(salary)
        }
        
        // Calculate statistics
        val deptStats = deptAggregations.mapValues { (_, salaries) ->
            mapOf(
                "count" to salaries.size,
                "sum" to salaries.sum(),
                "avg" to salaries.average(),
                "min" to salaries.minOrNull() ?: 0,
                "max" to salaries.maxOrNull() ?: 0
            )
        }
        
        assertEquals(3, deptStats.size)
        
        val engStats = deptStats["Engineering"]!!
        assertEquals(3, engStats["count"])
        assertEquals(180000, engStats["sum"])
        assertEquals(60000.0, engStats["avg"])
        assertEquals(50000, engStats["min"])
        assertEquals(70000, engStats["max"])
        
        val mktStats = deptStats["Marketing"]!!
        assertEquals(2, mktStats["count"])
        assertEquals(120000, mktStats["sum"])
        assertEquals(60000.0, mktStats["avg"])
        
        val salesStats = deptStats["Sales"]!!
        assertEquals(1, salesStats["count"])
        assertEquals(45000, salesStats["sum"])
        assertEquals(45000.0, salesStats["avg"])
    }
    
    @Test
    fun testCursorWithMetadataOperations() {
        // Create metadata for columns
        val metadata = 4 j { i ->
            when (i) {
                0 -> IOMemento().apply { name = "id"; type = "int"; width = 4 }
                1 -> IOMemento().apply { name = "name"; type = "string"; width = 20 }
                2 -> IOMemento().apply { name = "age"; type = "int"; width = 4 }
                3 -> IOMemento().apply { name = "salary"; type = "double"; width = 8 }
                else -> IOMemento()
            }
        }
        
        val data = listOf(
            "1,Alice,25,50000.0",
            "2,Bob,30,60000.0",
            "3,Charlie,35,70000.0"
        )
        
        val cursor = data.size j { i ->
            val fields = data[i].split(",")
            fields.size j { j -> 
                fields[j] j { metadata[j] }
            }
        }
        
        // Test metadata access
        assertEquals("id", cursor[0][0].b.name)
        assertEquals("int", cursor[0][0].b.type)
        assertEquals("name", cursor[0][1].b.name)
        assertEquals("string", cursor[0][1].b.type)
        assertEquals("age", cursor[0][2].b.name)
        assertEquals("int", cursor[0][2].b.type)
        assertEquals("salary", cursor[0][3].b.name)
        assertEquals("double", cursor[0][3].b.type)
        
        // Test data access with metadata
        assertEquals("1", cursor[0][0].a)
        assertEquals("Alice", cursor[0][1].a)
        assertEquals("25", cursor[0][2].a)
        assertEquals("50000.0", cursor[0][3].a)
        
        // Test column extraction with metadata
        val nameColumn = cursor.size j { i -> cursor[i][1] }
        assertEquals("Alice", nameColumn[0].a)
        assertEquals("Bob", nameColumn[1].a)
        assertEquals("Charlie", nameColumn[2].a)
        
        // Verify metadata is preserved
        assertEquals("name", nameColumn[0].b.name)
        assertEquals("string", nameColumn[0].b.type)
    }
    
    @Test
    fun testCursorReshaping() {
        val data = listOf(
            "a,b,c,d",
            "1,2,3,4",
            "5,6,7,8",
            "9,10,11,12"
        )
        
        val cursor = data.size j { i ->
            val line = data[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Reshape to 2x6 (flatten and reshape)
        val flattened = mutableListOf<String>()
        for (i in 1 until cursor.size) { // skip header
            for (j in 0 until cursor[i].size) {
                flattened.add(cursor[i][j])
            }
        }
        
        val reshaped = 2 j { i ->
            6 j { j -> flattened[i * 6 + j] }
        }
        
        assertEquals(2, reshaped.size)
        assertEquals(6, reshaped[0].size)
        
        // Verify reshaping
        assertEquals("1", reshaped[0][0])
        assertEquals("2", reshaped[0][1])
        assertEquals("3", reshaped[0][2])
        assertEquals("4", reshaped[0][3])
        assertEquals("5", reshaped[0][4])
        assertEquals("6", reshaped[0][5])
        
        assertEquals("7", reshaped[1][0])
        assertEquals("8", reshaped[1][1])
        assertEquals("9", reshaped[1][2])
        assertEquals("10", reshaped[1][3])
        assertEquals("11", reshaped[1][4])
        assertEquals("12", reshaped[1][5])
    }
} 