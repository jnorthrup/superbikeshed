package rtsgame

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.compat.*
import kotlin.test.*

/**
 * Comprehensive integration tests for RTS Game Engine
 */
class GameEngineTest {
    
    internal lateinit var engine: GameEngine
    
    @BeforeTest
    fun setup() {
        engine = GameEngine()
    }
    
    @Test
    fun `engine creates initial game state`() {
        val state = engine.tick()
        
        assertEquals(2, state.entities.`play`.size, "Should have 2 initial entities")
        assertTrue(state.tick.value > 0, "Tick should be initialized")
        
        // Check entity types
        val entities = state.entities.`play`
        assertTrue(entities.any { it.id.value.contains("unit_1") })
        assertTrue(entities.any { it.id.value.contains("unit_2") })
    }
    
    @Test
    fun `simulation advances tick correctly`() {
        val initialState = engine.tick()
        val nextState = engine.simulateTick(initialState)
        
        assertEquals(initialState.tick.value + 1, nextState.tick.value, "Tick should advance by 1")
        assertEquals(initialState.entities.`play`.size, nextState.entities.`play`.size, "Entity count should remain same")
    }
    
    @Test
    fun `entities move during simulation`() {
        val initialState = engine.tick()
        val nextState = engine.simulateTick(initialState)
        
        val initialPositions = initialState.entities.`play`.map { it.position }
        val nextPositions = nextState.entities.`play`.map { it.position }
        
        // At least one entity should have moved (due to randomization)
        val hasMoved = initialPositions.zip(nextPositions).any { (initial, next) ->
            initial.component1().value != next.component1().value || initial.component2().value != next.component2().value
        }
        
        assertTrue(hasMoved, "At least one entity should move during simulation")
    }
    
    @Test
    fun `entity positions stay within bounds`() {
        var state = engine.tick()
        
        // Run multiple simulation steps
        repeat(10) {
            state = engine.simulateTick(state)
        }
        
        // Check all entities are within reasonable bounds
        state.entities.`play`.forEach { entity ->
            assertTrue(entity.position.component1().value >= 20f, "X position should be >= 20")
            assertTrue(entity.position.component1().value <= 800f, "X position should be <= 800")
            assertTrue(entity.position.component2().value >= 120f, "Y position should be >= 120")
            assertTrue(entity.position.component2().value <= 600f, "Y position should be <= 600")
        }
    }
    
    @Test
    fun `entity health remains valid`() {
        val state = engine.tick()
        
        state.entities.`play`.forEach { entity ->
            assertTrue(entity.health.value > 0f, "Health should be positive")
            assertTrue(entity.health.value <= 100f, "Health should not exceed 100")
        }
    }
    
    @Test
    fun `player ids are valid`() {
        val state = engine.tick()
        
        state.entities.`play`.forEach { entity ->
            assertTrue(entity.playerId.value in 1..2, "Player ID should be 1 or 2")
        }
    }
    
    @Test
    fun `series operations work correctly`() {
        val state = engine.tick()
        
        // Test Indexed.play property
        assertNotNull(state.entities.`play`)
        assertTrue(state.entities.`play`.isNotEmpty())
        
        // Test Indexed size
        assertTrue(state.entities.size > 0)
        assertEquals(state.entities.size, state.entities.`play`.size)
    }
    
    @Test
    fun `join operations work correctly`() {
        val state = engine.tick()
        val entity = state.entities.`play`.first()
        
        // Test Position (Join<XCoord, YCoord>)
        assertNotNull(entity.position.component1()) // XCoord
        assertNotNull(entity.position.component2()) // YCoord
        
        assertTrue(entity.position.component1().value is Float)
        assertTrue(entity.position.component2().value is Float)
    }
    
    @Test
    fun `platform compatibility works`() {
        val timestamp = currentTimeMillis()
        assertTrue(timestamp > 0, "Current time should be positive")
        
        val formatted = formatFloat(123.456f, 2)
        assertNotNull(formatted, "Float formatting should work")
    }
}

/**
 * Performance and stress tests
 */
class GameEnginePerformanceTest {
    
    @Test
    fun `large simulation runs efficiently`() {
        val engine = GameEngine()
        var state = engine.tick()
        
        val startTime = currentTimeMillis()
        
        // Run 1000 simulation steps
        repeat(1000) {
            state = engine.simulateTick(state)
        }
        
        val duration = currentTimeMillis() - startTime
        
        // Should complete within reasonable time (less than 10 seconds)
        assertTrue(duration < 10000, "1000 simulation steps should complete within 10 seconds")
        assertEquals(1001, state.tick.value, "Should reach tick 1001")
    }
    
    @Test
    fun `memory usage stays stable`() {
        val engine = GameEngine()
        var state = engine.tick()
        
        val initialEntityCount = state.entities.`play`.size
        
        // Run many simulation steps
        repeat(100) {
            state = engine.simulateTick(state)
        }
        
        // Entity count should remain stable (no memory leaks)
        assertEquals(initialEntityCount, state.entities.`play`.size, "Entity count should remain stable")
    }
}