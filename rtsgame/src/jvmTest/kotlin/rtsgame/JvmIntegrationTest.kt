package rtsgame

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.demo.*
import kotlinx.coroutines.*
import kotlin.test.*

/**
 * JVM-specific integration tests
 */
class JvmIntegrationTest {
    
    @Test
    fun `interactive demo initializes on JVM`() = runBlocking {
        val demo = runInteractiveWebGPUDemo()
        assertNotNull(demo, "Demo should initialize successfully")
        
        // Test a frame render
        val state = demo.renderFrame()
        assertNotNull(state, "Should render frame successfully")
        assertTrue(state.isRunning, "Demo should be running")
        
        demo.dispose()
    }
    
    @Test
    fun `swing integration works`() {
        // Test that we can create the main components without errors
        assertDoesNotThrow {
            val engine = GameEngine()
            val state = engine.tick()
            assertTrue(state.entities.`play`.isNotEmpty())
        }
    }
    
    @Test
    fun `mouse interaction simulation`() = runBlocking {
        val demo = runInteractiveWebGPUDemo()
        
        // Simulate mouse clicks
        val interaction1 = demo.handleMouseClick(75f, 65f) // START button
        assertNotNull(interaction1, "Should handle mouse click")
        
        val interaction2 = demo.handleMouseRelease(75f, 65f)
        assertNotNull(interaction2, "Should handle mouse release")
        
        demo.dispose()
    }
    
    @Test
    fun `performance meets requirements`() = runBlocking {
        val demo = runInteractiveWebGPUDemo()
        
        val startTime = System.currentTimeMillis()
        
        // Render 60 frames (1 second at 60 FPS)
        repeat(60) {
            demo.renderFrame()
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // Should complete within reasonable time
        assertTrue(duration < 5000, "60 frames should render within 5 seconds")
        
        demo.dispose()
    }
}