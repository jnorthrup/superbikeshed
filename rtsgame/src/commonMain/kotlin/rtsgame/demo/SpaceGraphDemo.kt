import kotlin.math.*
package rtsgame.demo
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.spacegraph.*
import rtsgame.webgpu.CommonWebGPUSpaceGraph
import rtsgame.webgpu.CameraState
import rtsgame.webgpu.RenderResult as WebGPURenderResult
import rtsgame.spacegraph.Vector3D

/**
 * Comprehensive demo showing RTS game integrated with SpaceGraph WebGPU visualization
 */
class SpaceGraphRTSDemo {
    internal val gameEngine = GameEngine()
    internal val spaceGraphRenderer = RTSSpaceGraphRenderer()
    internal val webgpuRenderer = CommonWebGPUSpaceGraph()
    
    suspend fun runDemo(): DemoResult {
        // Initialize WebGPU renderer
        if (!webgpuRenderer.initialize()) {
            return DemoResult(false, "Failed to initialize WebGPU renderer")
        }
        
        // Create initial game state with diverse entities
        var gameState = createInitialGameState()
        
        val renderResults = mutableListOf<WebGPURenderResult>()
        val frameTimings = mutableListOf<Float>()
        
        // Run simulation with visualization for 10 ticks
        repeat(10) { tick ->
            // Update game state
            gameState = gameEngine.simulateTick(gameState)
            
            // Render with SpaceGraph
            val camera = createDynamicCamera(tick, gameState)
            val renderResult = webgpuRenderer.renderGameState(gameState, camera)
            
            renderResults.add(renderResult)
            frameTimings.add(renderResult.frameTime)
            
            // Log progress
            println("Tick ${gameState.tick.value}: Rendered ${renderResult.trianglesRendered} triangles in ${renderResult.frameTime}ms")
        }
        
        webgpuRenderer.dispose()
        
        return DemoResult(
            success = true,
            message = "Demo completed successfully",
            totalFrames = renderResults.size,
            avgFrameTime = frameTimings.average().toFloat(),
            totalTriangles = renderResults.sumOf { it.trianglesRendered }
        )
    }
    
    internal fun createInitialGameState(): GameState {
        val entities = mutableListOf<Entity>()
        
        // Player 1 forces
        entities.add(Entity(
            id = EntityId("commander_p1"),
            position = XCoord(100f) j YCoord(100f),
            health = Health(100f),
            playerId = PlayerId(1)
        ))
        
        entities.add(Entity(
            id = EntityId("tank_p1_1"),
            position = XCoord(120f) j YCoord(80f),
            health = Health(75f),
            playerId = PlayerId(1)
        ))
        
        entities.add(Entity(
            id = EntityId("scout_p1_1"),
            position = XCoord(90f) j YCoord(130f),
            health = Health(30f),
            playerId = PlayerId(1)
        ))
        
        // Player 2 forces
        entities.add(Entity(
            id = EntityId("commander_p2"),
            position = XCoord(300f) j YCoord(300f),
            health = Health(100f),
            playerId = PlayerId(2)
        ))
        
        entities.add(Entity(
            id = EntityId("tank_p2_1"),
            position = XCoord(280f) j YCoord(320f),
            health = Health(85f),
            playerId = PlayerId(2)
        ))
        
        // Neutral building
        entities.add(Entity(
            id = EntityId("resource_1"),
            position = XCoord(200f) j YCoord(200f),
            health = Health(50f),
            playerId = PlayerId(0)
        ))
        
        return GameState(
            entities = Indexed.of(entities.size) { i -> entities[i] },
            tick = GameTick(0)
        )
    }
    
    internal fun createDynamicCamera(tick: Int, gameState: GameState): CameraState {
        // Dynamic camera that orbits around the battlefield
        val centerX = 200.0
        val centerY = 200.0
        val radius = 400.0
        val angle = tick * 0.1
        
        val cameraX = centerX + radius * kotlin.math.cos(angle)
        val cameraY = centerY + radius * kotlin.math.sin(angle)
        val cameraZ = 300.0
        
        return CameraState(
            position = Vector3D(cameraX, cameraY, cameraZ),
            target = Vector3D(centerX, centerY, 0.0),
            fov = 60f,
            aspect = 16f / 9f
        )
    }
}

data class DemoResult(
    val success: Boolean,
    val message: String,
    val totalFrames: Int = 0,
    val avgFrameTime: Float = 0f,
    val totalTriangles: Int = 0
)

/**
 * Platform-agnostic entry point for SpaceGraph RTS demo
 */
suspend fun runSpaceGraphRTSDemo(): DemoResult {
    val demo = SpaceGraphRTSDemo()
    return demo.runDemo()
}