package rtsgame

import rtsgame.core.*
import rtsgame.codec.*
import rtsgame.systems.*
import rtsgame.terrain.*
import rtsgame.pathfinding.*
import borg.trikeshed.lib.*
import kotlin.random.*
import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.Position
import rtsgame.demo.startSpaceGraphHttpServer

/**
 * Main entry point for RTS simulation
 * Minimal working example to verify system integration
 */
suspend fun main() {
    println("RTS Simulation Starting...")
    
    // Start SpaceGraph HTTP server
    startSpaceGraphHttpServer(8080)

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