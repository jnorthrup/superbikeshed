package nexus

import nexus.implementations.*
import nexus.core.*
import kotlin.test.*

class NexusActualTest {
    
    @Test
    fun `working nexus handles basic requests`() {
        val nexus = WorkingNexus()
        
        val response = nexus.handle("analyze the current codebase")
        
        assertTrue(response.contains("Analysis in scope"))
        assertTrue(response.contains("current codebase"))
    }
    
    @Test
    fun `working nexus updates context from interactions`() {
        val nexus = WorkingNexus()
        
        nexus.handle("generate a service class")
        val context = nexus.getCurrentContext()
        
        val experience = context.extractCurrentExperience()
        assertTrue(experience.contains("generate a service class"))
    }
    
    @Test
    fun `CCEK context can be updated`() {
        val context = buildInitialCCEKContext()
        
        val updated = context.updateScope("test-scope")
        
        assertEquals("test-scope", updated.extractCurrentScope())
    }
    
    @Test
    fun `CCEK context can add capabilities`() {
        val context = buildInitialCCEKContext()
        
        val updated = context.addCapability("file-system")
        
        assertTrue(updated.extractCurrentCapabilities().contains("file-system"))
    }
    
    @Test
    fun `request parsing extracts targets correctly`() {
        assertEquals("the database layer", "analyze the database layer".extractAnalysisTarget())
        assertEquals("unit tests", "generate unit tests".extractGenerationTarget())
        assertEquals("legacy code", "refactor legacy code".extractRefactorTarget())
    }
    
    @Test
    fun `series operations work correctly`() {
        val series = listOf(1, 5, 3, 9, 2).toIndexed()
        
        assertEquals(9, series.best())
        assertEquals(3, series.take(3).toList().size)
    }
}

// Helper to convert Series to List for testing
fun <T> Indexed<T>.toList(): List<T> = this play { it }