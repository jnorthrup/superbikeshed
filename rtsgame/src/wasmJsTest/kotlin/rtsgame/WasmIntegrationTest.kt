package rtsgame

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.compat.*
import kotlin.test.*

/**
 * WASM-specific integration tests
 */
class WasmIntegrationTest {
    
    @Test
    fun `wasm platform functions work`() {
        val timestamp = currentTimeMillis()
        assertTrue(timestamp > 0, "Current time should work in WASM")
        
        val formatted = formatFloat(123.456f, 2)
        assertNotNull(formatted, "Float formatting should work in WASM")
    }
    
    @Test
    fun `game engine works in wasm`() {
        val engine = GameEngine()
        val state = engine.tick()
        
        assertTrue(state.entities.`play`.isNotEmpty(), "Should create entities in WASM")
        assertTrue(state.tick.value > 0, "Should have valid tick in WASM")
    }
    
    @Test
    fun `simulation works in wasm`() {
        val engine = GameEngine()
        var state = engine.tick()
        
        val initialTick = state.tick.value
        state = engine.simulateTick(state)
        
        assertEquals(initialTick + 1, state.tick.value, "Simulation should advance in WASM")
    }
    
    @Test
    fun `wasm module structure is valid`() {
        // Test that WASM-specific code compiles and runs
        assertDoesNotThrow {
            platformMain()
        }
    }
    
    @Test
    fun `series operations work in wasm`() {
        val data = listOf(1, 2, 3, 4, 5)
        val series = Indexed.of(data.size) { i -> data[i] }
        
        assertEquals(5, series.size)
        assertEquals(data, series.play)
    }
    
    @Test
    fun `join operations work in wasm`() {
        val coord = XCoord(10f) j YCoord(20f)
        
        assertEquals(10f, coord.component1().value)
        assertEquals(20f, coord.component2().value)
    }
}