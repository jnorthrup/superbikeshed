package rtsgame

import rtsgame.core.*
import rtsgame.rendering.*
import rtsgame.massive.*
import org.khronos.webgl.*
import org.w3c.dom.*
import kotlinx.browser.*

/**
 * WASM entry point for the RTS game
 */
class RTSGameWeb(
    internal val canvas: HTMLCanvasElement,
    internal val gpuDevice: dynamic
) {
    internal lateinit var launcher: RTSGameLauncher
    internal lateinit var renderer: WebGPUOptimizedRenderer
    internal var isPaused = false
    
    // Selection state
    internal val selectedUnits = mutableSetOf<EntityId>()
    
    @JsExport
    suspend fun initialize() {
        println("Initializing RTS Game for WASM...")
        
        // Create game launcher
        launcher = RTSGameLauncher()
        
        // Initialize renderer with canvas
        renderer = WebGPUOptimizedRenderer()
        renderer.initialize(canvas)
        
        // Launch game
        launcher.launch()
    }
    
    @JsExport
    fun update(deltaTime: Float) {
        if (!isPaused) {
            // Update simulation
            launcher.simulation.update(deltaTime)
        }
    }
    
    @JsExport
    fun render() {
        renderer.render(launcher.simulation.world, 1f)
    }
    
    @JsExport
    fun onMouseDown(x: Int, y: Int, button: Int) {
        when (button) {
            0 -> { // Left click - selection
                selectedUnits.clear()
            }
            2 -> { // Right click - command
                if (selectedUnits.isNotEmpty()) {
                    val worldPos = screenToWorld(x, y)
                    val command = MoveCommand(worldPos)
                    launcher.simulation.issueCommand(selectedUnits.toList(), command)
                }
            }
        }
    }
    
    @JsExport
    fun onMouseMove(x: Int, y: Int) {
        // Update hover state
    }
    
    @JsExport
    fun onMouseUp(x: Int, y: Int, button: Int) {
        // Finalize selection
    }
    
    @JsExport
    fun updateSelection(startX: Int, startY: Int, endX: Int, endY: Int) {
        // Box selection
        selectedUnits.clear()
        
        val minX = minOf(startX, endX)
        val maxX = maxOf(startX, endX)
        val minY = minOf(startY, endY)
        val maxY = maxOf(startY, endY)
        
        launcher.simulation.world.forEach<PositionComponent>(ComponentTypes.POSITION) { entity, pos ->
            val screenPos = worldToScreen(pos)
            if (screenPos.x in minX..maxX && screenPos.y in minY..maxY) {
                selectedUnits.add(entity)
            }
        }
    }
    
    @JsExport
    fun onKeyDown(key: String) {
        // Handle keyboard input
    }
    
    @JsExport
    fun onZoom(delta: Float) {
        // Handle camera zoom
    }
    
    @JsExport
    fun togglePause() {
        isPaused = !isPaused
    }
    
    @JsExport
    fun spawnArmy(teamId: Int) {
        // Spawn units for testing
        repeat(100) {
            val x = 5000f + (it % 10) * 50f
            val y = 5000f + (it / 10) * 50f
            launcher.simulation.spawnUnit(teamId, UnitType.MARINE, x, y)
        }
    }
    
    @JsExport
    fun benchmarkMode() {
        // Switch to million unit benchmark
        val benchmark = MillionUnitBenchmark()
        benchmark.runBenchmark()
    }
    
    @JsExport
    fun getUnitCount(): Int {
        return launcher.simulation.entityCount
    }
    
    @JsExport
    fun getSelectedUnits(): Int {
        return selectedUnits.size
    }
    
    @JsExport
    fun selectAll() {
        selectedUnits.clear()
        launcher.simulation.world.forEach<TeamComponent>(ComponentTypes.TEAM) { entity, team ->
            if (team.teamId == 0) { // Select only player's units
                selectedUnits.add(entity)
            }
        }
    }
    
    @JsExport
    fun selectControlGroup(group: Int) {
        // Control group selection
    }
    
    @JsExport
    fun getUnitPositions(): Array<UnitPosition> {
        val positions = mutableListOf<UnitPosition>()
        
        launcher.simulation.world.forEach<PositionComponent>(ComponentTypes.POSITION) { entity, pos ->
            val team = launcher.simulation.world.getComponent<TeamComponent>(entity, ComponentTypes.TEAM)
            positions.add(UnitPosition(pos.x, pos.y, team?.teamId ?: 0))
        }
        
        return positions.toTypedArray()
    }
    
    internal fun screenToWorld(x: Int, y: Int): PositionComponent {
        // Convert screen coordinates to world coordinates
        return PositionComponent(x.toFloat(), y.toFloat())
    }
    
    internal fun worldToScreen(pos: PositionComponent): ScreenPos {
        // Convert world coordinates to screen coordinates
        return ScreenPos(pos.x.toInt(), pos.y.toInt())
    }
}

data class UnitPosition(
    val x: Float,
    val y: Float,
    val team: Int
)

data class ScreenPos(val x: Int, val y: Int)

/**
 * Main entry point for WASM
 */
fun createRTSGame(): RTSGameWeb {
    // This will be called from JavaScript
    val canvas = document.getElementById("gameCanvas") as HTMLCanvasElement
    val gpuDevice = js("navigator.gpu.requestAdapter().then(a => a.requestDevice())")
    
    return RTSGameWeb(canvas, gpuDevice)
}