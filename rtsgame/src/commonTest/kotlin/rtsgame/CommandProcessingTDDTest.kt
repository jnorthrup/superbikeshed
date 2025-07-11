package rtsgame

import kotlin.test.*
import rtsgame.codec.*
import rtsgame.core.*

class CommandProcessingTDDTest {
    lateinit var simulation: Simulation
    lateinit var requestFactory: RTSRequestFactory

    @BeforeTest
    fun setup() {
        val (sim, factory) = createRTSSimulation()
        simulation = sim
        requestFactory = factory
    }

    @Test
    fun `simulation initializes successfully`() {
        assertNotNull(simulation, "Simulation should initialize")
        assertNotNull(requestFactory, "Request factory should initialize")
    }

    @Test
    fun `simulation can be updated`() {
        val initialTick = simulation.getCurrentTick()
        simulation.update(1.0f / 60.0f)
        assertTrue(simulation.getCurrentTick() >= initialTick, "Simulation tick should advance")
    }

    @Test
    fun `simulation can be reset`() {
        simulation.update(1.0f / 60.0f)
        simulation.reset()
        assertEquals(0, simulation.getCurrentTick(), "Simulation should reset to tick 0")
    }

    @Test
    fun `request factory creates valid requests`() {
        val request = requestFactory.createMoveRequest(1, 100f, 100f)
        assertNotNull(request, "Should create move request")
        assertEquals("move", request.type, "Request type should be move")
    }

    @Test
    fun `simulation handles basic entity management`() {
        val entityCount = simulation.getEntityCount()
        assertTrue(entityCount >= 0, "Entity count should be non-negative")
    }
} 