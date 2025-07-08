import kotlin.math.*
package rtsgame.webgpu
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.spacegraph.*
import rtsgame.ui.*

/**
 * WebGPU renderer that handles both game world and UI elements
 */
class InteractiveWebGPURenderer {
    internal val spaceGraphRenderer = RTSSpaceGraphRenderer()
    internal val webgpuRenderer = CommonWebGPUSpaceGraph()
    internal var cameraDistance = 500.0
    internal var cameraAngle = 0.0
    
    suspend fun initialize(): Boolean {
        return webgpuRenderer.initialize()
    }
    
    fun renderFrame(gameState: GameState, panelState: PanelState, deltaTime: Float): InteractiveRenderResult {
        // Update camera
        cameraAngle += deltaTime * 0.5
        val camera = createCamera(gameState, cameraDistance, cameraAngle)
        
        // Render game world
        val worldResult = webgpuRenderer.renderGameState(gameState, camera)
        
        // Render UI panel (convert to renderable format)
        val uiResult = renderUIPanel(panelState)
        
        return InteractiveRenderResult(
            worldTriangles = worldResult.trianglesRendered,
            uiTriangles = uiResult.trianglesRendered,
            frameTime = worldResult.frameTime + uiResult.frameTime,
            success = worldResult.success && uiResult.success,
            entityCount = gameState.entities.`play`.size,
            buttonCount = panelState.buttons.play.size,
            tick = gameState.tick.value
        )
    }
    
    internal fun createCamera(gameState: GameState, distance: Double, angle: Double): CameraState {
        // Calculate center of all entities
        val entities = gameState.entities.`play`
        val centerX = if (entities.isNotEmpty()) {
            entities.map { it.position.x }.average()
        } else 400.0
        
        val centerY = if (entities.isNotEmpty()) {
            entities.map { it.position.y }.average() 
        } else 300.0
        
        // Orbital camera
        val cameraX = centerX + distance * kotlin.math.cos(angle)
        val cameraY = centerY + distance * kotlin.math.sin(angle)
        val cameraZ = distance * 0.7
        
        return CameraState(
            position = Vector3D(cameraX, cameraY, cameraZ),
            target = Vector3D(centerX, centerY, 0.0),
            fov = 60f,
            aspect = 16f / 9f
        )
    }
    
    internal fun renderUIPanel(panelState: PanelState): RenderResult {
        // Convert UI elements to SpaceGraph nodes for rendering
        val uiNodes = mutableListOf<SpaceGraphNode>()
        
        // Panel background
        uiNodes.add(SpaceGraphNode(
            id = NodeId("panel_bg_${panelState.id.value}"),
            position = panelState.position,
            data = NodeData(
                label = "Control Panel",
                health = 100f,
                playerId = 0,
                entityType = EntityType.BUILDING
            )
        ))
        
        // Buttons as nodes
        panelState.buttons.play.forEach { button ->
            uiNodes.add(SpaceGraphNode(
                id = NodeId("btn_${button.id.value}"),
                position = button.position,
                data = NodeData(
                    label = button.label,
                    health = if (button.enabled) 100f else 50f,
                    playerId = if (button.pressed) 2 else 1,
                    entityType = EntityType.UNIT
                )
            ))
        }
        
        // Simulate rendering UI (in real implementation would render to overlay)
        return RenderResult(
            success = true,
            frameTime = 2.5f, // UI rendering time
            trianglesRendered = uiNodes.size * 2 // 2 triangles per quad
        )
    }
    
    fun adjustCamera(zoomIn: Boolean) {
        if (zoomIn) {
            cameraDistance = (cameraDistance * 0.9).coerceAtLeast(100.0)
        } else {
            cameraDistance = (cameraDistance * 1.1).coerceAtMost(2000.0)
        }
    }
    
    fun dispose() {
        webgpuRenderer.dispose()
    }
}

data class InteractiveRenderResult(
    val worldTriangles: Int,
    val uiTriangles: Int,
    val frameTime: Float,
    val success: Boolean,
    val entityCount: Int,
    val buttonCount: Int,
    val tick: Long
) {
    val totalTriangles: Int get() = worldTriangles + uiTriangles
}