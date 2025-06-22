package borg.trikeshed.cursor

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.lib.Join
import borg.trikeshed.isam.meta.IOMemento
import kotlin.test.*

/**
 * Advanced cursor tests ported from columnar project
 * Tests complex cursor operations like grouping, pivoting, and transformations
 */
class AdvancedCursorTest {
    
    @Test
    fun testCursorGrouping() {
        // Create test data with categories
        val data = listOf(
            "category,value,name",
            "A,100,item1",
            "B,200,item2",
            "A,150,item3",
            "C,300,item4",
            "B,250,item5",
            "A,120,item6"
        )
        
        val cursor = data.size j { i ->
            val line = data[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Group by category
        val groups = mutableMapOf<String, MutableList<Indexed<String>>>()
        for (i in 1 until cursor.size) { // skip header
            val category = cursor[i][0]
            val group = groups.getOrPut(category) { mutableListOf() }
            group.add(cursor[i])
        }
        
        assertEquals(3, groups.size)
        assertEquals(3, groups["A"]?.size)
        assertEquals(2, groups["B"]?.size)
        assertEquals(1, groups["C"]?.size)
        
        // Verify group contents
        val groupA = groups["A"]!!
        assertEquals("item1", groupA[0][2])
        assertEquals("item3", groupA[1][2])
        assertEquals("item6", groupA[2][2])
    }
    
    @Test
    fun testCursorAggregation() {
        val data = listOf(
            "category,value",
            "A,100",
            "B,200",
            "A,150",
            "C,300",
            "B,250",
            "A,120"
        )
        
        val cursor = data.size j { i ->
            val line = data[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Aggregate by category
        val aggregations = mutableMapOf<String, MutableList<Int>>()
        for (i in 1 until cursor.size) {
            val category = cursor[i][0]
            val value = cursor[i][1].toIntOrNull() ?: 0
            val group = aggregations.getOrPut(category) { mutableListOf() }
            group.add(value)
        }
        
        // Calculate sums
        val sums = aggregations.mapValues { (_, values) -> values.sum() }
        assertEquals(370, sums["A"]) // 100 + 150 + 120
        assertEquals(450, sums["B"]) // 200 + 250
        assertEquals(300, sums["C"]) // 300
        
        // Calculate averages
        val averages = aggregations.mapValues { (_, values) -> values.average() }
        assertEquals(123.33, averages["A"]!!, 0.01)
        assertEquals(225.0, averages["B"]!!, 0.01)
        assertEquals(300.0, averages["C"]!!, 0.01)
    }
    
    @Test
    fun testCursorPivoting() {
        val data = listOf(
            "date,category,value",
            "2023-01-01,A,100",
            "2023-01-01,B,200",
            "2023-01-02,A,150",
            "2023-01-02,B,250",
            "2023-01-03,A,120",
            "2023-01-03,B,300"
        )
        
        val cursor = data.size j { i ->
            val line = data[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Create pivot table: date rows, category columns
        val dates = mutableSetOf<String>()
        val categories = mutableSetOf<String>()
        
        for (i in 1 until cursor.size) {
            dates.add(cursor[i][0])
            categories.add(cursor[i][1])
        }
        
        val dateList = dates.toList().sorted()
        val categoryList = categories.toList().sorted()
        
        // Create pivot matrix
        val pivot = dateList.size j { i ->
            categoryList.size j { j ->
                val date = dateList[i]
                val category = categoryList[j]
                
                // Find matching value
                var value = 0
                for (k in 1 until cursor.size) {
                    if (cursor[k][0] == date && cursor[k][1] == category) {
                        value = cursor[k][2].toIntOrNull() ?: 0
                        break
                    }
                }
                value
            }
        }
        
        assertEquals(3, pivot.size) // 3 dates
        assertEquals(2, pivot[0].size) // 2 categories
        
        // Verify pivot values
        assertEquals(100, pivot[0][0]) // 2023-01-01, A
        assertEquals(200, pivot[0][1]) // 2023-01-01, B
        assertEquals(150, pivot[1][0]) // 2023-01-02, A
        assertEquals(250, pivot[1][1]) // 2023-01-02, B
        assertEquals(120, pivot[2][0]) // 2023-01-03, A
        assertEquals(300, pivot[2][1]) // 2023-01-03, B
    }
    
    @Test
    fun testCursorTransformation() {
        val data = listOf(
            "name,age,salary",
            "Alice,25,50000",
            "Bob,30,60000",
            "Charlie,35,70000",
            "David,40,80000"
        )
        
        val cursor = data.size j { i ->
            val line = data[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Transform: add calculated columns
        val transformed = cursor.size j { i ->
            if (i == 0) {
                // Header row
                5 j { j ->
                    when (j) {
                        0 -> "name"
                        1 -> "age"
                        2 -> "salary"
                        3 -> "age_group"
                        4 -> "salary_band"
                        else -> "unknown"
                    }
                }
            } else {
                // Data rows
                val name = cursor[i][0]
                val age = cursor[i][1].toIntOrNull() ?: 0
                val salary = cursor[i][2].toIntOrNull() ?: 0
                
                5 j { j ->
                    when (j) {
                        0 -> name
                        1 -> age.toString()
                        2 -> salary.toString()
                        3 -> when {
                            age < 30 -> "young"
                            age < 40 -> "middle"
                            else -> "senior"
                        }
                        4 -> when {
                            salary < 55000 -> "low"
                            salary < 65000 -> "medium"
                            else -> "high"
                        }
                        else -> "unknown"
                    }
                }
            }
        }
        
        assertEquals(5, transformed.size)
        assertEquals(5, transformed[0].size)
        
        // Verify transformations
        assertEquals("young", transformed[1][3]) // Alice
        assertEquals("low", transformed[1][4])
        assertEquals("middle", transformed[2][3]) // Bob
        assertEquals("medium", transformed[2][4])
        assertEquals("senior", transformed[4][3]) // David
        assertEquals("high", transformed[4][4])
    }
    
    @Test
    fun testCursorJoin() {
        val data1 = listOf(
            "id,name",
            "1,Alice",
            "2,Bob",
            "3,Charlie"
        )
        
        val data2 = listOf(
            "id,salary,dept",
            "1,50000,Engineering",
            "2,60000,Marketing",
            "4,70000,Sales"
        )
        
        val cursor1 = data1.size j { i ->
            val line = data1[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        val cursor2 = data2.size j { i ->
            val line = data2[i]
            val fields = line.split(",")
            fields.size j { j -> fields[j] }
        }
        
        // Inner join on id
        val joined = mutableListOf<Indexed<String>>()
        for (i in 1 until cursor1.size) {
            val id1 = cursor1[i][0]
            for (j in 1 until cursor2.size) {
                val id2 = cursor2[j][0]
                if (id1 == id2) {
                    // Join the rows
                    val joinedRow = 4 j { k ->
                        when (k) {
                            0 -> id1
                            1 -> cursor1[i][1] // name
                            2 -> cursor2[j][1] // salary
                            3 -> cursor2[j][2] // dept
                            else -> ""
                        }
                    }
                    joined.add(joinedRow)
                }
            }
        }
        
        assertEquals(2, joined.size) // Only id 1 and 2 match
        
        // Verify joined data
        assertEquals("1", joined[0][0])
        assertEquals("Alice", joined[0][1])
        assertEquals("50000", joined[0][2])
        assertEquals("Engineering", joined[0][3])
        
        assertEquals("2", joined[1][0])
        assertEquals("Bob", joined[1][1])
        assertEquals("60000", joined[1][2])
        assertEquals("Marketing", joined[1][3])
    }
    
    @Test
    fun testCursorWithMetadataJoin() {
        // Create metadata for columns
        val meta1 = 2 j { i ->
            when (i) {
                0 -> IOMemento().apply { name = "id"; type = "int"; width = 4 }
                1 -> IOMemento().apply { name = "name"; type = "string"; width = 20 }
                else -> IOMemento()
            }
        }
        
        val meta2 = 3 j { i ->
            when (i) {
                0 -> IOMemento().apply { name = "id"; type = "int"; width = 4 }
                1 -> IOMemento().apply { name = "value"; type = "double"; width = 8 }
                2 -> IOMemento().apply { name = "category"; type = "string"; width = 15 }
                else -> IOMemento()
            }
        }
        
        val data1 = listOf("1,Alice", "2,Bob", "3,Charlie")
        val data2 = listOf("1,100.5,A", "2,200.3,B", "4,300.7,C")
        
        val cursor1 = data1.size j { i ->
            val fields = data1[i].split(",")
            2 j { j -> fields[j] j { meta1[j] } }
        }
        
        val cursor2 = data2.size j { i ->
            val fields = data2[i].split(",")
            3 j { j -> fields[j] j { meta2[j] } }
        }
        
        // Join with metadata preservation
        val joined = mutableListOf<Indexed<Join<Any?, () -> IOMemento>>>()
        for (i in 0 until cursor1.size) {
            val id1 = cursor1[i][0].a as String
            for (j in 0 until cursor2.size) {
                val id2 = cursor2[j][0].a as String
                if (id1 == id2) {
                    // Create joined row with metadata
                    val joinedRow = 4 j { k ->
                        when (k) {
                            0 -> id1 j { meta1[0] }
                            1 -> cursor1[i][1]
                            2 -> cursor2[j][1]
                            3 -> cursor2[j][2]
                            else -> "" j { IOMemento() }
                        }
                    }
                    joined.add(joinedRow)
                }
            }
        }
        
        assertEquals(2, joined.size)
        
        // Verify metadata is preserved
        assertEquals("id", joined[0][0].b.name)
        assertEquals("int", joined[0][0].b.type)
        assertEquals("name", joined[0][1].b.name)
        assertEquals("string", joined[0][1].b.type)
        assertEquals("value", joined[0][2].b.name)
        assertEquals("double", joined[0][2].b.type)
    }
} 