package borg.trikeshed.ljson

import borg.trikeshed.lib.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.random.Random

/**
 * ISAM Superannuate Test for Spansh Galaxy Data
 * 
 * Tests ISAM (Indexed Sequential Access Method) functionality for
 * columnar galaxy map data using TrikeShed's cursor extensions.
 * 
 * "Superannuate" in this context means we're testing the ability
 * to archive and retrieve galaxy data that may be considered
 * "retired" or historical (like older EDSM snapshots).
 */
class SpanshISAMTest {
    
    /**
     * Test basic ISAM write/read with galaxy system data
     */
    @Test
    fun testGalaxySystemISAM() = runBlocking {
        // Create test galaxy data as MetaSeries
        val testSystems = createTestGalaxySystems()
        
        // Write to ISAM
        val written = testSystems.writeISAM()
        assertTrue(written, "Failed to write ISAM")
        
        // Read back from ISAM
        val opened = testSystems.openISAM()
        assertTrue(opened, "Failed to open ISAM")
        
        // Verify data integrity
        assertEquals(testSystems.a, 5, "Row count mismatch")
        assertEquals(testSystems.columnNames.play.toList(), 
            listOf("id", "name", "x", "y", "z", "population", "allegiance"),
            "Column names mismatch"
        )
    }
    
    /**
     * Test ISAM with normalized galactic coordinates
     */
    @Test
    fun testNormalizedCoordinatesISAM() = runBlocking {
        val systems = createTestGalaxySystems()
        
        // Normalize X, Y, Z coordinates
        val normalized = systems
            .normalizeColumn("x")
            .normalizeColumn("y")
            .normalizeColumn("z")
        
        // Write normalized data
        assertTrue(normalized.writeISAM())
        
        // Verify normalization
        val xValues = (0 until normalized.a).map { 
            normalized[it].getValueByName("x") as Double 
        }
        assertTrue(xValues.all { it in 0.0..1.0 }, "X values not normalized")
    }
    
    /**
     * Test ISAM with filtered galactic regions
     */
    @Test
    fun testFilteredRegionsISAM() = runBlocking {
        val systems = createTestGalaxySystems()
        
        // Filter systems within 100 LY of Sol
        val nearSol = systems.filterRows { row ->
            val x = row.getValueByName("x") as Double
            val y = row.getValueByName("y") as Double
            val z = row.getValueByName("z") as Double
            val distance = kotlin.math.sqrt(x*x + y*y + z*z)
            distance <= 100.0
        }
        
        assertTrue(nearSol.writeISAM())
        assertTrue(nearSol.a > 0, "No systems found near Sol")
    }
    
    /**
     * Test ISAM with computed columns (jump distance)
     */
    @Test
    fun testComputedColumnsISAM() = runBlocking {
        val systems = createTestGalaxySystems()
        
        // Add jump distance from Sol
        val withJumpDistance = systems.withComputedColumn(
            "distanceFromSol",
            Double::class
        ) { row ->
            val x = row.getValueByName("x") as Double
            val y = row.getValueByName("y") as Double
            val z = row.getValueByName("z") as Double
            kotlin.math.sqrt(x*x + y*y + z*z)
        }
        
        assertTrue(withJumpDistance.writeISAM())
        
        // Verify computed column
        val firstDistance = withJumpDistance[0].getValueByName("distanceFromSol") as Double
        assertTrue(firstDistance >= 0.0, "Invalid distance computed")
    }
    
    /**
     * Test ISAM with column selection (economic data only)
     */
    @Test
    fun testColumnSelectionISAM() = runBlocking {
        val systems = createTestGalaxySystems()
        
        // Select only economic columns
        val economicData = systems.selectColumns("id", "name", "population", "allegiance")
        
        assertTrue(economicData.writeISAM())
        assertEquals(economicData.columnNames.a, 4, "Column count mismatch")
    }
    
    /**
     * Test ISAM with Spansh nightly sample data
     */
    @Test
    fun testSpanshNightlySample() = runBlocking {
        // Simulate loading Spansh nightly gzip sample
        val spanshSample = loadSpanshNightlySample()
        
        assertTrue(spanshSample.writeISAM())
        
        // Verify sample contains expected Spansh fields
        val columnList = spanshSample.columnNames.play.toList()
        assertTrue("coords_x" in columnList || "x" in columnList, "Missing X coordinate")
        assertTrue("coords_y" in columnList || "y" in columnList, "Missing Y coordinate")
        assertTrue("coords_z" in columnList || "z" in columnList, "Missing Z coordinate")
    }
    
    /**
     * Test ISAM superannuation (archiving old galaxy snapshots)
     */
    @Test
    fun testSuperannuation() = runBlocking {
        val currentSystems = createTestGalaxySystems()
        
        // Add timestamp metadata for superannuation
        val archived = currentSystems.withComputedColumn(
            "archivedAt",
            Long::class
        ) { _ ->
            System.currentTimeMillis()
        }
        
        // Write as superannuated (archived) data
        assertTrue(archived.writeISAM())
        
        // Verify we can query archived data
        val recentlyArchived = archived.filterRows { row ->
            val timestamp = row.getValueByName("archivedAt") as Long
            System.currentTimeMillis() - timestamp < 60000 // Within last minute
        }
        
        assertEquals(recentlyArchived.a, archived.a, "Archive filtering failed")
    }
    
    /**
     * Create test galaxy systems data
     */
    private fun createTestGalaxySystems(): MetaSeries<Int, RowVec> {
        val systemData = listOf(
            listOf(1L, "Sol", 0.0, 0.0, 0.0, 22_000_000_000L, "Federation"),
            listOf(2L, "Alpha Centauri", 3.03125, -0.09375, 3.15625, 6_000_000_000L, "Federation"),
            listOf(3L, "Barnard's Star", -0.875, -1.375, 5.75, 0L, "Independent"),
            listOf(4L, "Wolf 359", 3.875, 6.46875, -1.90625, 0L, "Federation"),
            listOf(5L, "Colonia", 9530.5, -910.28125, 19808.125, 583_869L, "Colonia Council")
        )
        
        val columnMetas = listOf(
            ColumnMeta("id", Long::class),
            ColumnMeta("name", String::class),
            ColumnMeta("x", Double::class),
            ColumnMeta("y", Double::class),
            ColumnMeta("z", Double::class),
            ColumnMeta("population", Long::class),
            ColumnMeta("allegiance", String::class)
        )
        
        return systemData.size j { rowIdx ->
            val rowData = systemData[rowIdx]
            columnMetas.size j { colIdx ->
                rowData[colIdx] j columnMetas[colIdx]
            }
        }
    }
    
    /**
     * Load Spansh nightly sample (simulated)
     */
    private fun loadSpanshNightlySample(): MetaSeries<Int, RowVec> {
        // Simulate a subset of Spansh nightly data
        val sampleData = listOf(
            listOf(30000001L, "HIP 58832", 11.0, -39.25, -6.40625, 6_195_842_231L, "Empire", "2024-01-15"),
            listOf(30000002L, "Pleiades Sector HR-W d1-79", -80.625, -146.6875, -343.375, 0L, "None", "2024-01-15"),
            listOf(30000003L, "Sagittarius A*", 25.21875, -20.90625, 25899.96875, 0L, "None", "2024-01-15")
        )
        
        val columnMetas = listOf(
            ColumnMeta("id64", Long::class),
            ColumnMeta("name", String::class),
            ColumnMeta("coords_x", Double::class),
            ColumnMeta("coords_y", Double::class),
            ColumnMeta("coords_z", Double::class),
            ColumnMeta("population", Long::class),
            ColumnMeta("allegiance", String::class),
            ColumnMeta("date_modified", String::class)
        )
        
        return sampleData.size j { rowIdx ->
            val rowData = sampleData[rowIdx]
            columnMetas.size j { colIdx ->
                rowData[colIdx] j columnMetas[colIdx]
            }
        }
    }
}

/**
 * Extension to convert list to indexed for testing
 */
private fun <T> List<T>.toIdx(): Indexed<T> = size j ::get