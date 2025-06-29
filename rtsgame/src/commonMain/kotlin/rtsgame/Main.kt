package rtsgame

import rtsgame.core.*
import rtsgame.codec.*
import rtsgame.systems.*
import rtsgame.terrain.*
import rtsgame.pathfinding.*
import borg.trikeshed.lib.*
import kotlin.random.*

/**
 * Main entry point for RTS simulation - Now used for testing ported features
 */
suspend fun main() {
    println("RTS KMP Porting Tests Starting...")
    println("==================================")

    testCoreLoopAndGameStateTime()
    // testEntityCreation() // TODO: Uncomment when EntityFactory and types are ready
    // testBuildingProduction() // TODO: Uncomment
    // testUnitMovement() // TODO: Uncomment
    // testUnitCombat() // TODO: Uncomment

    println("==================================")
    println("RTS KMP Porting Tests Finished.")
}

fun testCoreLoopAndGameStateTime() {
    println("\n--- Test: Core Loop and GameState Time Progression ---")
    val seed = 12345L
    val simContext = SimulationContext(
        GAME_SEED = seed,
        seedRandom = Random(seed.toInt()), // Kotlin Random needs Int seed
        HEADLESS_MODE = true,
        RECORD_AI_DECISIONS = false,
        RECORD_AI_DECISIONS_DURATION_SECONDS = 0,
        battleJournal = null // No battle journal for this simple test
        // resourceNodes will be initialized by Simulation's init -> generateTerrain -> generateResourceNodes
    )
    val simulation = Simulation(simContext)

    // Manually call init to set up internal state like lastSystemTime and generate terrain/resources
    // In a real scenario, this would be part of the simulation setup.
    // For this test, we need terrain to be generated so GameUnit can be created if needed by other tests.
    // However, this specific test only checks gameTime.
    // simulation.init() // Not strictly needed for *just* gameTime test if gameLoop doesn't depend on initialized entities for time.
    // Let's initialize lastSystemTime directly as gameLoop expects it.
    simulation.gameState.isRunning = true // Start the game for the loop to run
    simulation.lastSystemTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()


    val initialTime = simulation.gameState.gameTime
    val iterations = 100 // Simulate 100 frames
    var totalDeltaTimeSimulated = 0.0

    println("Initial gameTime: $initialTime, isRunning: ${simulation.gameState.isRunning}")

    for (i in 1..iterations) {
        // Simulate time passing for deltaTime calculation by gameLoop
        val mockCurrentSystemTime = simulation.lastSystemTime + 16 // Simulate ~16ms per frame
        
        // The gameLoop will internally update lastSystemTime.
        // To correctly simulate, we'd typically pass the mockCurrentSystemTime to a method
        // that then calculates deltaTime. Since gameLoop does it internally, we ensure
        // lastSystemTime is set before it's called, and it updates itself.
        // For the *first* call to gameLoop in a real scenario, lastSystemTime is 0 or based on init.
        // Here, we're driving it manually.

        val continueLoop = simulation.gameLoop()
        // gameLoop updates simulation.lastSystemTime to the "current" time it used for calculation.
        // For the next iteration, we'll advance our mockSystemTime from this new base.
        simulation.lastSystemTime = mockCurrentSystemTime // Ensure next iteration uses this as its "current"


        val expectedDeltaTime = (16) / 1000.0 // Approximate deltaTime used by the loop
        totalDeltaTimeSimulated += expectedDeltaTime
        
        if (!continueLoop && simulation.gameState.winner == null) {
            println("Test Error: Simulation loop stopped prematurely at frame $i. GameTime: ${simulation.gameState.gameTime}")
            return
        }
         if (simulation.gameState.winner != null && simulation.gameState.winner != "RECORDING_COMPLETE") {
            println("Test Note: Game ended due to winner: ${simulation.gameState.winner} at frame $i.")
            break
        }
        if (simulation.gameState.winner == "RECORDING_COMPLETE") {
             println("Test Note: Game ended due to recording completion at frame $i.")
            break
        }
    }

    val finalTime = simulation.gameState.gameTime
    // gameState.gameTime is Int. Each gameLoop call adds deltaTime.toInt() to it.
    // If deltaTime is ~0.016, deltaTime.toInt() is 0. So gameTime might not advance as expected with Int.
    // Let's check the JS logic: gameState.gameTime += deltaTime; (JS gameTime is float)
    // Kotlin Simulation.kt: gameState.gameTime += deltaTime.toInt()
    // This means gameTime will only increment if deltaTime is >= 1.0.
    // This needs to be changed in Simulation.kt if gameTime is meant to be seconds.
    // For now, we expect gameTime to be 0 if deltaTime is always < 1.0.

    // Re-evaluating expectation: If gameTime is integer ticks, and deltaTime is float seconds,
    // the current `gameState.gameTime += deltaTime.toInt()` will result in gameTime not changing
    // if deltaTime is always < 1.0 (which it is for 60fps).
    // The JS `game.js` `update` function: `gameContext.gameState.gameTime += deltaTime;` (float seconds)
    // The KMP `Simulation.kt` `gameLoop`: `gameState.gameTime += deltaTime.toInt()`
    // This is a discrepancy. For the test to pass based on current KMP code, gameTime should remain low.
    // Let's assume gameTime in KMP is meant to be Ticks, and 1 tick = 1 frame.
    // If `gameLoop()` in Simulation.kt is called 100 times, gameTime should be 100 * (deltaTime.toInt()).
    // If deltaTime is e.g. 0.016, deltaTime.toInt() is 0. So gameTime will be 0.
    // The JS_TO_KMP_RESCUE_PLAN says "preserve exact JS behavior".
    // So, GameState.gameTime should probably be Double.

    // GameState.gameTime is now Double and accumulates float deltaTime.
    // Expected finalTime is initialTime + totalDeltaTimeSimulated.
    val expectedFinalTime = initialTime + totalDeltaTimeSimulated

    println("Final gameTime after $iterations iterations: $finalTime (Expected: ${expectedFinalTime.toFloat()})")
    println("Total float deltaTime simulated: ${totalDeltaTimeSimulated.toFloat()} seconds")

    // Compare floating point numbers with a small tolerance
    val tolerance = 0.00001
    if (kotlin.math.abs(finalTime - expectedFinalTime) < tolerance) {
        println("✅ PASSED: GameState.gameTime (Double) progressed as expected.")
    } else {
        println("❌ FAILED: GameState.gameTime (Double) did not progress as expected. Got $finalTime, expected ${expectedFinalTime.toFloat()}.")
    }
    println("Simulation isRunning at end: ${simulation.gameState.isRunning}")
}

// TODO: Implement other test functions:
// fun testEntityCreation() { ... }
// fun testBuildingProduction() { ... }
// fun testUnitMovement() { ... }
// fun testUnitCombat() { ... }


// --- Mock/Helper for DeterministicRandom if the library one is not ready ---
// (Included MockDeterministicRandom above for now)

// --- Placeholder for rtsgame.codec.RTSRequest if needed by tests ---
// Might not be needed for these initial local tests if we directly call simulation methods.
// Example:
// sealed class RTSRequest { data class SimulationTick(val deltaTime: Double, val frameNumber: Long, val timestamp: Double) : RTSRequest() }
// object RTSCodec { fun encodeRequest(req: RTSRequest): Indexed<Byte> {return byteArrayOf().size j {0} } }
// object RTSResponse { val success = true }
// class RTSRequestFactory(sim: Simulation) { fun process(payload: Indexed<Byte>): Indexed<Byte> { sim.gameLoop(); return byteArrayOf().size j {0} } }

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
    }
}

/**
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
}