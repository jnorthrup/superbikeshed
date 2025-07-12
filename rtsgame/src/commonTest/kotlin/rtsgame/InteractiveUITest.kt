package rtsgame

import borg.trikeshed.lib.*
import rtsgame.ui.*
import rtsgame.core.*
import rtsgame.spacegraph.*
import kotlin.test.*

/**
 * Integration tests for Interactive UI Panel
 */
class InteractiveUITest {
    
    internal lateinit var panel: InteractiveWebGPUPanel
    
    @BeforeTest
    fun setup() {
        panel = InteractiveWebGPUPanel()
    }
    
    @Test
    fun `panel creates control buttons`() {
        val (_, panelState) = panel.getCurrentState()
        
        assertEquals(5, panelState.buttons.play.size, "Should have 5 control buttons")
        
        val buttonLabels = panelState.buttons.play.map { it.label }
        assertTrue(buttonLabels.contains("START"), "Should have START button")
        assertTrue(buttonLabels.contains("ADD UNIT"), "Should have ADD UNIT button")
        assertTrue(buttonLabels.contains("RESET"), "Should have RESET button")
        assertTrue(buttonLabels.contains("+"), "Should have zoom in button")
        assertTrue(buttonLabels.contains("-"), "Should have zoom out button")
    }
    
    @Test
    fun `start stop button toggles correctly`() {
        val interaction1 = panel.handleMouseInput(75f, 65f, true) // Click START button
        
        assertEquals(ButtonId("start_stop"), interaction1.buttonClicked)
        
        val (_, panelState1) = panel.getCurrentState()
        val startButton1 = panelState1.buttons.play.find { it.id.value == "start_stop" }
        assertEquals("STOP", startButton1?.label, "Button should show STOP after clicking START")
        
        // Click again to stop
        val interaction2 = panel.handleMouseInput(75f, 65f, true)
        val (_, panelState2) = panel.getCurrentState()
        val startButton2 = panelState2.buttons.play.find { it.id.value == "start_stop" }
        assertEquals("START", startButton2?.label, "Button should show START after clicking STOP")
    }
    
    @Test
    fun `add unit button creates new entities`() {
        val (gameState1, _) = panel.getCurrentState()
        val initialCount = gameState1.entities.`play`.size
        
        // Click ADD UNIT button
        panel.handleMouseInput(250f, 65f, true)
        
        val (gameState2, _) = panel.getCurrentState()
        val newCount = gameState2.entities.`play`.size
        
        assertEquals(initialCount + 1, newCount, "Should add one entity")
        
        // Verify new entity has valid properties
        val newEntity = gameState2.entities.`play`.last()
        assertTrue(newEntity.id.value.startsWith("unit_"), "New entity should have unit_ prefix")
        assertTrue(newEntity.position.component1().value >= 20f, "X position should be valid")
        assertTrue(newEntity.position.component2().value >= 120f, "Y position should be valid")
        assertTrue(newEntity.health.value > 0f, "Health should be positive")
        assertTrue(newEntity.playerId.value in 1..2, "Player ID should be valid")
    }
    
    @Test
    fun `reset button restores initial state`() {
        // Add some entities first
        panel.handleMouseInput(250f, 65f, true) // ADD UNIT
        panel.handleMouseInput(250f, 65f, true) // ADD UNIT again
        
        val (gameState1, _) = panel.getCurrentState()
        assertTrue(gameState1.entities.`play`.size > 4, "Should have more than initial entities")
        
        // Click RESET button
        panel.handleMouseInput(390f, 65f, true)
        
        val (gameState2, panelState2) = panel.getCurrentState()
        assertEquals(4, gameState2.entities.`play`.size, "Should restore to initial 4 entities")
        assertEquals(GameTick(0), gameState2.tick, "Should reset tick to 0")
        
        // Check START button is reset
        val startButton = panelState2.buttons.play.find { it.id.value == "start_stop" }
        assertEquals("START", startButton?.label, "START button should be reset")
    }
    
    @Test
    fun `zoom buttons trigger camera adjustments`() {
        // Click zoom in
        val interaction1 = panel.handleMouseInput(515f, 65f, true)
        assertEquals(ButtonId("zoom_in"), interaction1.buttonClicked)
        
        // Click zoom out
        val interaction2 = panel.handleMouseInput(555f, 65f, true)
        assertEquals(ButtonId("zoom_out"), interaction2.buttonClicked)
    }
    
    @Test
    fun `mouse interaction detects button clicks correctly`() {
        // Test each button's click area
        val buttons = listOf(
            Pair(75f, 65f) to "start_stop",    // START button center
            Pair(250f, 65f) to "add_unit",     // ADD UNIT button center
            Pair(390f, 65f) to "reset",        // RESET button center
            Pair(515f, 65f) to "zoom_in",      // + button center
            Pair(555f, 65f) to "zoom_out"      // - button center
        )
        
        buttons.forEach { (coords, expectedId) ->
            val interaction = panel.handleMouseInput(coords.first, coords.second, true)
            assertEquals(ButtonId(expectedId), interaction.buttonClicked, 
                "Click at (${coords.first}, ${coords.second}) should trigger $expectedId button")
        }
    }
    
    @Test
    fun `mouse clicks outside buttons return null`() {
        // Click outside any button area
        val interaction = panel.handleMouseInput(10f, 10f, true)
        assertNull(interaction.buttonClicked, "Click outside buttons should return null")
    }
    
    @Test
    fun `mouse state is tracked correctly`() {
        val interaction = panel.handleMouseInput(100f, 200f, true)
        
        assertEquals(100f, interaction.mouseState.x)
        assertEquals(200f, interaction.mouseState.y)
        assertTrue(interaction.mouseState.leftDown)
        assertFalse(interaction.mouseState.rightDown)
    }
    
    @Test
    fun `simulation tick advances when running`() {
        // Start simulation
        panel.handleMouseInput(75f, 65f, true) // Click START
        
        val initialTick = panel.getCurrentState().first.tick.value
        
        // Tick simulation
        val gameState = panel.tick()
        
        assertTrue(gameState.tick.value > initialTick, "Tick should advance when running")
    }
    
    @Test
    fun `simulation does not advance when stopped`() {
        // Ensure simulation is stopped (default state)
        val initialTick = panel.getCurrentState().first.tick.value
        
        // Tick simulation
        val gameState = panel.tick()
        
        assertEquals(initialTick, gameState.tick.value, "Tick should not advance when stopped")
    }
}

/**
 * UI Button State Tests
 */
class ButtonStateTest {
    
    @Test
    fun `button state has valid properties`() {
        val button = ButtonState(
            id = ButtonId("test_button"),
            label = "Test Button",
            enabled = true,
            pressed = false,
            position = Vector3D(10.0, 20.0, 0.0),
            size = Vector3D(100.0, 40.0, 5.0)
        )
        
        assertEquals(ButtonId("test_button"), button.id)
        assertEquals("Test Button", button.label)
        assertTrue(button.enabled)
        assertFalse(button.pressed)
        assertEquals(10.0, button.position.x)
        assertEquals(20.0, button.position.y)
        assertEquals(100.0, button.size.x)
        assertEquals(40.0, button.size.y)
    }
    
    @Test
    fun `panel state manages multiple buttons`() {
        val buttons = listOf(
            ButtonState(
                id = ButtonId("btn1"),
                label = "Button 1",
                position = Vector3D(0.0, 0.0, 0.0),
                size = Vector3D(50.0, 30.0, 5.0)
            ),
            ButtonState(
                id = ButtonId("btn2"),
                label = "Button 2",
                position = Vector3D(60.0, 0.0, 0.0),
                size = Vector3D(50.0, 30.0, 5.0)
            )
        )
        
        val panelState = PanelState(
            id = PanelId("test_panel"),
            position = Vector3D(0.0, 0.0, 10.0),
            size = Vector3D(200.0, 50.0, 10.0),
            buttons = Indexed.of(buttons.size) { i -> buttons[i] },
            visible = true
        )
        
        assertEquals(2, panelState.buttons.play.size)
        assertTrue(panelState.visible)
        assertEquals(PanelId("test_panel"), panelState.id)
    }
}