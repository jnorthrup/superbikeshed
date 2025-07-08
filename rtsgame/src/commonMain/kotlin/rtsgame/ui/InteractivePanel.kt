import kotlin.math.*
package rtsgame.ui
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.spacegraph.*
import rtsgame.webgpu.*
import rtsgame.compat.*

/**
 * Interactive WebGPU panel with actual buttons and controls
 */

value class ButtonId(val value: String)

value class PanelId(val value: String)

data class ButtonState(
    val id: ButtonId,
    val label: String,
    val enabled: Boolean = true,
    val pressed: Boolean = false,
    val position: Vector3D,
    val size: Vector3D
)

data class PanelState(
    val id: PanelId,
    val position: Vector3D,
    val size: Vector3D,
    val buttons: Indexed<ButtonState>,
    val visible: Boolean = true
)

data class MouseState(
    val x: Float,
    val y: Float,
    val leftDown: Boolean = false,
    val rightDown: Boolean = false
)

data class UIInteraction(
    val buttonClicked: ButtonId?,
    val mouseState: MouseState
)

/**
 * WebGPU-based interactive control panel
 */
class InteractiveWebGPUPanel {
    internal var gameEngine = GameEngine()
    internal var gameState = createDemoGameState()
    internal var panelState = createControlPanel()
    internal var mouseState = MouseState(0f, 0f)
    internal var isRunning = false
    
    fun createControlPanel(): PanelState {
        val buttons = mutableListOf<ButtonState>()
        
        // Start/Stop simulation button
        buttons.add(ButtonState(
            id = ButtonId("start_stop"),
            label = if (isRunning) "STOP" else "START",
            position = Vector3D(50.0, 50.0, 0.0),
            size = Vector3D(100.0, 40.0, 5.0)
        ))
        
        // Add unit button
        buttons.add(ButtonState(
            id = ButtonId("add_unit"),
            label = "ADD UNIT",
            position = Vector3D(200.0, 50.0, 0.0),
            size = Vector3D(120.0, 40.0, 5.0)
        ))
        
        // Reset simulation button
        buttons.add(ButtonState(
            id = ButtonId("reset"),
            label = "RESET",
            position = Vector3D(350.0, 50.0, 0.0),
            size = Vector3D(80.0, 40.0, 5.0)
        ))
        
        // Camera control buttons
        buttons.add(ButtonState(
            id = ButtonId("zoom_in"),
            label = "+",
            position = Vector3D(500.0, 50.0, 0.0),
            size = Vector3D(30.0, 30.0, 5.0)
        ))
        
        buttons.add(ButtonState(
            id = ButtonId("zoom_out"),
            label = "-",
            position = Vector3D(540.0, 50.0, 0.0),
            size = Vector3D(30.0, 30.0, 5.0)
        ))
        
        return PanelState(
            id = PanelId("main_control"),
            position = Vector3D(0.0, 0.0, 10.0),
            size = Vector3D(800.0, 100.0, 10.0),
            buttons = Indexed.of(buttons.size) { i -> buttons[i] }
        )
    }
    
    fun handleMouseInput(x: Float, y: Float, leftDown: Boolean): UIInteraction {
        mouseState = MouseState(x, y, leftDown)
        
        var clickedButton: ButtonId? = null
        
        if (leftDown) {
            // Check button clicks
            panelState.buttons.play.forEach { button ->
                if (isPointInButton(x, y, button)) {
                    clickedButton = button.id
                    handleButtonClick(button.id)
                }
            }
        }
        
        return UIInteraction(
            buttonClicked = clickedButton,
            mouseState = mouseState
        )
    }
    
    internal fun isPointInButton(x: Float, y: Float, button: ButtonState): Boolean {
        val minX = button.position.x.toFloat()
        val maxX = (button.position.x + button.size.x).toFloat()
        val minY = button.position.y.toFloat()
        val maxY = (button.position.y + button.size.y).toFloat()
        
        return x >= minX && x <= maxX && y >= minY && y <= maxY
    }
    
    internal fun handleButtonClick(buttonId: ButtonId) {
        when (buttonId.value) {
            "start_stop" -> {
                isRunning = !isRunning
                panelState = updateButtonLabel(panelState, buttonId, if (isRunning) "STOP" else "START")
            }
            "add_unit" -> {
                gameState = addRandomUnit(gameState)
            }
            "reset" -> {
                gameState = createDemoGameState()
                isRunning = false
                panelState = updateButtonLabel(panelState, ButtonId("start_stop"), "START")
            }
            "zoom_in" -> {
                // Camera zoom handled by renderer
            }
            "zoom_out" -> {
                // Camera zoom handled by renderer  
            }
        }
    }
    
    internal fun updateButtonLabel(panel: PanelState, buttonId: ButtonId, newLabel: String): PanelState {
        val updatedButtons = panel.buttons.play.map { button ->
            if (button.id == buttonId) {
                button.copy(label = newLabel)
            } else {
                button
            }
        }
        
        return panel.copy(
            buttons = Indexed.of(updatedButtons.size) { i -> updatedButtons[i] }
        )
    }
    
    fun tick(): GameState {
        if (isRunning) {
            gameState = gameEngine.simulateTick(gameState)
        }
        return gameState
    }
    
    fun getCurrentState(): Pair<GameState, PanelState> = gameState to panelState
    
    internal fun createDemoGameState(): GameState {
        val entities = mutableListOf<Entity>()
        
        // Create recognizable demo units
        entities.add(Entity(
            id = EntityId("commander_blue"),
            position = Position(400f, 300f),
            health = Health(100f),
            playerId = PlayerId(1)
        ))
        
        entities.add(Entity(
            id = EntityId("tank_blue_1"),
            position = Position(350f, 250f),
            health = Health(80f),
            playerId = PlayerId(1)
        ))
        
        entities.add(Entity(
            id = EntityId("tank_blue_2"), 
            position = Position(450f, 250f),
            health = Health(80f),
            playerId = PlayerId(1)
        ))
        
        entities.add(Entity(
            id = EntityId("commander_red"),
            position = Position(400f, 500f),
            health = Health(100f),
            playerId = PlayerId(2)
        ))
        
        return GameState(
            entities = Indexed.of(entities.size) { i -> entities[i] },
            tick = GameTick(0)
        )
    }
    
    internal fun addRandomUnit(currentState: GameState): GameState {
        val newUnit = Entity(
            id = EntityId("unit_${Clock.System.now().toEpochMilliseconds()}"),
            position = Position(
                kotlin.random.Random.nextFloat() * 800f,
                kotlin.random.Random.nextFloat() * 600f
            ),
            health = Health(60f + kotlin.random.Random.nextFloat() * 40f),
            playerId = PlayerId(if (kotlin.random.Random.nextBoolean()) 1 else 2)
        )
        
        val allEntities = currentState.entities.`play` + newUnit
        
        return currentState.copy(
            entities = Indexed.of(allEntities.size) { i -> allEntities[i] }
        )
    }
}