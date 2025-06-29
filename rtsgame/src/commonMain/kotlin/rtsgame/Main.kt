package rtsgame

<<<<<<< HEAD
import rtsgame.core.*
import rtsgame.codec.*
import rtsgame.systems.*
import rtsgame.terrain.*
import rtsgame.pathfinding.*
import borg.trikeshed.lib.*
import kotlin.random.*

/**
 * Main entry point for RTS simulation
 * Minimal working example to verify system integration
 */
suspend fun main() {
    println("RTS Simulation Starting...")
    
    // Create deterministic simulation
    val seed = 12345L
    val random = DeterministicRandom(seed)
    
    val context = SimulationContext(
        GAME_SEED = seed,
        seedRandom = Random(seed),
        HEADLESS_MODE = true,
        RECORD_AI_DECISIONS = true,
        RECORD_AI_DECISIONS_DURATION_SECONDS = 10,
        battleJournal = null
    )
    
    val simulation = Simulation(context)
    val requestFactory = RTSRequestFactory(simulation)
    
    // Initialize terrain system
    val terrain = TerrainSystem(random)
    terrain.generateTerrain(seed)
    
    // Initialize pathfinding
    val pathfinder = AStar(terrain)
    
    // Initialize simulation
    simulation.init()
    
    println("Running 10-second simulation...")
    
    // Main simulation loop - 600 frames at 60 FPS
    val targetFrames = 600
    val deltaTime = 1.0 / 60.0 // 60 FPS
    
    repeat(targetFrames) { frame ->
        // Create simulation tick request
        val tickRequest = RTSRequest.SimulationTick(
            deltaTime = deltaTime,
            frameNumber = frame.toLong(),
            timestamp = frame * deltaTime
        )
        
        // Process through RequestFactory
        val requestData = RTSCodec.encodeRequest(tickRequest)
        val responseData = requestFactory.process(requestData)
        val response = RTSCodec.decodeResponse(responseData)
        
        // Log progress every second
        if (frame % 60 == 0) {
            val seconds = frame / 60
            println("Frame $frame (${seconds}s) - Response: ${response.success}")
            
            // Log entity counts
            val unitCount = simulation.entityManager.units.size
            val buildingCount = simulation.entityManager.buildings.size
            println("  Units: $unitCount, Buildings: $buildingCount")
        }
    }
    
    println("Simulation completed successfully")
    
    // Export final state
    val finalState = requestFactory.invokeService("sync.state", byteArrayOf().let { bytes ->
        bytes.size j { i: Int -> if (i < bytes.size) bytes[i] else 0 }
    })
    
    println("Final state checksum calculated")
}

/**
 * Test pathfinding system independently  
 */
fun testPathfinding() {
    val seed = 42L
    val random = DeterministicRandom(seed)
    val terrain = TerrainSystem(random)
    terrain.generateTerrain(seed)
    
    val pathfinder = AStar(terrain)
    
    // Test path from corner to corner
    val path = pathfinder.findPath(50.0, 50.0, 750.0, 750.0) as borg.trikeshed.lib.Indexed<Pair<Double, Double>>?
    
    if (path != null && path.size > 0) {
        println("Pathfinding test passed - found path with ${path.size} waypoints")
        
        // Print first few waypoints
        val waypoints = minOf(5, path.size)
        repeat(waypoints) { i: Int ->
            val (x, y) = path[i]
            println("  Waypoint $i: ($x, $y)")
        }
    } else {
        println("Pathfinding test failed - no path found")
=======
import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.Position
import rtsgame.core.*
import rtsgame.demo.startSpaceGraphHttpServer

class RTSGameEngine {
    private var gameState = GameState(
        entities = Series.of(0) { throw IndexOutOfBoundsException() }
    )
    
    fun initialize(): GameState {
        // Create initial game state
        val initialEntities = listOf(
            Entity(
                id = EntityId("unit_001"),
                position = Position(100f, 100f),
                health = Health(100f),
                playerId = PlayerId(1)
            ),
            Entity(
                id = EntityId("unit_002"),
                position = Position(200f, 200f),
                health = Health(80f),
                playerId = PlayerId(2)
            )
        )
        
        gameState = GameState(
            entities = Series.of(initialEntities.size) { i -> initialEntities[i] }
        )
        
        return gameState
    }
    
    fun tick(): GameState {
        gameState = gameState.advance()
        return gameState
    }
    
    fun getState(): GameState = gameState
}

expect fun platformMain()

fun startGame(): RTSGameEngine {
    val engine = RTSGameEngine()
    engine.initialize()
    return engine
}

fun demonstrateGame() {
    println("RTS Game Demonstration")
    val engine = startGame()
    
    repeat(5) { i ->
        val state = engine.tick()
        println("Tick ${state.tick.value}: ${state.entities.play.size} entities")
        state.entities.play.forEachIndexed { index, entity ->
            println("  Entity $index: ${entity.id.value} at (${entity.position.x}, ${entity.position.y})")
        }
>>>>>>> origin/feat/core-serialization-impl
    }
}

/**
<<<<<<< HEAD
 * Test RequestFactory codec independently
 */
fun testCodec() {
    val originalRequest = RTSRequest.MoveUnit(
        unitId = 42,
        x = 100.5,
        y = 200.7,
        frameNumber = 1337L,
        timestamp = 22.28
    )
    
    // Encode and decode
    val encoded = RTSCodec.encodeRequest(originalRequest)
    val decoded = RTSCodec.decodeRequest(encoded)
    
    if (decoded is RTSRequest.MoveUnit && 
        decoded.unitId == originalRequest.unitId &&
        decoded.x == originalRequest.x &&
        decoded.y == originalRequest.y) {
        println("Codec test passed - round trip successful")
    } else {
        println("Codec test failed - data corruption")
    }
=======
 * Main entry point for RTS game
 */
suspend fun main() {
    // Start SpaceGraph HTTP server
    startSpaceGraphHttpServer(8080)
>>>>>>> origin/feat/core-serialization-impl
}