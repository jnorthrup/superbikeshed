package rtsgame.core

import rtsgame.config.*
import kotlin.random.Random

// Direct translation of js/core/simulation.js
// NO IMPROVEMENTS - preserving exact JS behavior

data class SimulationContext(
    val GAME_SEED: Long,
    val seedRandom: Random,
    val HEADLESS_MODE: Boolean,
    val RECORD_AI_DECISIONS: Boolean,
    val RECORD_AI_DECISIONS_DURATION_SECONDS: Int,
    val battleJournal: Any?, // Will be typed when ported
    val resourceNodes: MutableList<ResourceNode>? = null
)

data class ResourceNode(
    var x: Double,
    var y: Double,
    var type: String,
    var amount: Int,
    var maxAmount: Int,
    var occupied: Boolean
)

data class TerrainCell(
    val type: Int,
    val elevation: Double
)

data class TeamResourcesExtended(
    var mass: Double, // Changed to Double
    var energy: Double, // Changed to Double
    var computronium: Double = 10.0, // Changed to Double, Start with some Computronium for initial building
    var massIncome: Double = 0.0,
    var energyIncome: Double = 0.0,
    var computroniumIncome: Double = 0.0
)

class Simulation(private val context: SimulationContext) {
    val GAME_SEED = context.GAME_SEED
    val seedRandom = context.seedRandom
    val HEADLESS_MODE = context.HEADLESS_MODE
    val RECORD_AI_DECISIONS = context.RECORD_AI_DECISIONS
    val RECORD_AI_DECISIONS_DURATION_SECONDS = context.RECORD_AI_DECISIONS_DURATION_SECONDS
    val battleJournal = context.battleJournal

    // Initialize managers - use TrikeShed-based entity management
    val entityManager = EntityManager() // Assuming EntityManager is defined elsewhere and compatible
    val gameState = GameState() // Assuming GameState is defined elsewhere and compatible

    // Initialize Computronium managers for each team
    // TODO: Replace Any with actual ComputroniumManager when ported
    val computroniumManagers = mutableMapOf<String, Any>(
        "blue" to Any(),
        "red" to Any()
    )

    // Initialize Enhanced Command Hierarchies for each team
    // TODO: Replace Any with actual EnhancedCommandHierarchy when ported
    val commandHierarchies = mutableMapOf<String, Any>(
        "blue" to Any(),
        "red" to Any()
    )

    // Initialize resources - now including Computronium
    val resources = mutableMapOf(
        "blue" to TeamResourcesExtended(
            mass = SIMULATION_CONFIG.INITIAL_BLUE_MASS.toDouble(), // Convert Int to Double
            energy = SIMULATION_CONFIG.INITIAL_BLUE_ENERGY.toDouble() // Convert Int to Double
        ),
        "red" to TeamResourcesExtended(
            mass = SIMULATION_CONFIG.INITIAL_RED_MASS.toDouble(), // Convert Int to Double
            energy = SIMULATION_CONFIG.INITIAL_RED_ENERGY.toDouble() // Convert Int to Double
        )
    )

    // Initialize terrain and resource nodes
    val terrain: MutableList<MutableList<TerrainCell>> = mutableListOf()
    val resourceNodes: MutableList<ResourceNode> = context.resourceNodes ?: mutableListOf()

    // Create gameContext for compatibility with existing code that might expect it
    // TODO: Refine this context as other modules are ported
    val simulationContextForUnitUpdates = mapOf(
        "entityManager" to entityManager,
        "gameState" to gameState,
        "seedRandom" to seedRandom,
        "resources" to resources,
        "terrain" to terrain, // Make sure this is populated by generateTerrain
        "resourceNodes" to resourceNodes,
        "UNIT_TYPES" to rtsgame.config.UNIT_TYPES, // Assuming UNIT_TYPES object/map when ported
        "BUILDING_TYPES" to rtsgame.config.BUILDING_TYPES, // Assuming BUILDING_TYPES object/map when ported
        "WORLD_SIZE" to WORLD_SIZE,
        "TILE_SIZE" to TILE_SIZE,
        "GRID_SIZE" to GRID_SIZE,
        "TERRAIN_TYPES" to TERRAIN_TYPES,
        "battleJournal" to battleJournal // Pass battleJournal for event recording
        // Add other necessary parts of the context like `addEvent` if Unit/Building use it directly
    )

    private var lastSystemTime = 0L // For calculating deltaTime based on system time

    suspend fun init() {
        println("Initializing KMP simulation...")
        lastSystemTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        gameState.isRunning = true // Set game as running

        // Generate terrain
        generateTerrain()

        // Setup commander build lists
        // TODO: Port UNIT_TYPES and BUILDING_TYPES and then uncomment/implement this
        // if (rtsgame.config.UNIT_TYPES.commander != null) {
        //     rtsgame.config.UNIT_TYPES.commander.buildList = listOf(
        //         rtsgame.config.BUILDING_TYPES.massExtractor,
        //         rtsgame.config.BUILDING_TYPES.energyExtractor,
        //         rtsgame.config.BUILDING_TYPES.landFactory,
        //         rtsgame.config.BUILDING_TYPES.computroniumExtractor,
        //         rtsgame.config.BUILDING_TYPES.advancedComputroniumCore
        //     )
        // }

        // Spawn commanders
        spawnCommanders() // This needs to be awaitable if it involves async ops, or just a normal fun

        println("KMP Simulation initialized successfully")
    }

    private fun generateTerrain() { // Removed suspend as it's synchronous
        println("Generating terrain...")
        // Initialize terrain grid
        for (x in 0 until GRID_SIZE) {
            terrain.add(mutableListOf())
            for (y in 0 until GRID_SIZE) {
                val noise = seedRandom.nextDouble()
                val cell = when {
                    noise < 0.1 -> TerrainCell(type = TERRAIN_TYPES.WATER, elevation = -0.5)
                    noise > 0.85 -> TerrainCell(type = TERRAIN_TYPES.MOUNTAIN, elevation = 1.0)
                    noise > 0.80 -> TerrainCell(type = TERRAIN_TYPES.RESOURCE, elevation = 0.1)
                    else -> TerrainCell(type = TERRAIN_TYPES.LAND, elevation = 0.0)
                }
                terrain[x].add(cell)
            }
        }
        generateResourceNodes()
        println("Terrain generation complete")
    }

    private fun generateResourceNodes() { // Removed suspend
        resourceNodes.clear()
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                if (terrain[x][y].type == TERRAIN_TYPES.RESOURCE) {
                    val worldX = x * TILE_SIZE.toDouble()
                    val worldY = y * TILE_SIZE.toDouble()
                    val rand = seedRandom.nextDouble()
                    val type = when {
                        rand < 0.4 -> RESOURCE_TYPES.MASS
                        rand < 0.8 -> RESOURCE_TYPES.ENERGY
                        else -> "COMPUTRONIUM" // Placeholder
                    }
                    resourceNodes.add(
                        ResourceNode(
                            x = worldX, y = worldY, type = type,
                            amount = 10000, maxAmount = 10000, occupied = false
                        )
                    )
                }
            }
        }
        println("Generated ${resourceNodes.size} resource nodes")
    }

    private fun spawnCommanders() { // Removed suspend
        // TODO: Implement when GameUnit class and UNIT_TYPES are fully ported
        // For now, let's assume commanders are added to entityManager directly elsewhere or by AI
        println("Spawning commanders (placeholder)...")
        // Example:
        // val blueSpawn = findSpawnPosition(0.2, 0.5)
        // val redSpawn = findSpawnPosition(0.8, 0.5)
        // if (blueSpawn != null && redSpawn != null && rtsgame.config.UNIT_TYPES.commander != null) {
        //     entityManager.addUnit(GameUnit(entityManager.nextEntityId++, blueSpawn.first, blueSpawn.second, "blue", rtsgame.config.UNIT_TYPES.commander, this))
        //     entityManager.addUnit(GameUnit(entityManager.nextEntityId++, redSpawn.first, redSpawn.second, "red", rtsgame.config.UNIT_TYPES.commander, this))
        //     gameState.addEvent("spawn", "Commanders deployed", 2)
        // } else {
        //     println("Error: Could not spawn commanders.")
        // }
    }

    // This is the main entry point for each simulation step, called by an external loop.
    fun gameLoop() : Boolean {
        if (gameState.paused || gameState.winner != null) {
            return !gameState.isRunning // Return false if game should stop, true to continue
        }

        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val deltaTime = if (lastSystemTime == 0L) 1.0/60.0 else (currentTime - lastSystemTime) / 1000.0
        lastSystemTime = currentTime

        gameState.gameTime += deltaTime // Changed from deltaTime.toInt() to deltaTime (Double)

        // Update resources (simplified, full logic in ResourceSystem)
        updateResourceIncome(deltaTime) // Pass deltaTime for accurate resource accumulation

        // Update Computronium systems
        // TODO: Port and call computroniumManagers update
        // computroniumManagers.forEach { (_, manager) -> (manager as ComputroniumManager).update(this, deltaTime) }

        // Update Command Hierarchies
        // TODO: Port and call commandHierarchies update
        // commandHierarchies.forEach { (_, hierarchy) -> (hierarchy as EnhancedCommandHierarchy).update(this, deltaTime) }

        // Update entities
        entityManager.update(this, deltaTime) // entityManager will call unit.update, building.update etc.

        // AI Decisions
        // TODO: Port StrategicAI and integrate
        // if (gameState.gameTime % AI_DECISION_INTERVAL == 0) { // AI_DECISION_INTERVAL from GameConstants
        //     aiBlue.makeDecisions(this).forEach { request -> processRequest(request) }
        //     aiRed.makeDecisions(this).forEach { request -> processRequest(request) }
        // }

        // Check win conditions
        checkWinConditions()

        // Record frame if battleJournal is active
        // TODO: Port BattleJournal and integrate
        // (battleJournal as? BattleJournal)?.recordFrame(this)

        if (RECORD_AI_DECISIONS && gameState.gameTime >= RECORD_AI_DECISIONS_DURATION_SECONDS) {
            gameState.winner = "RECORDING_COMPLETE"
            // (battleJournal as? BattleJournal)?.stopRecording(GameOutcome(winner = gameState.winner, reason = "Recording duration reached"))
            gameState.isRunning = false // Stop the game
        }

        return gameState.isRunning
    }

    private fun updateResourceIncome(deltaTime: Double) { // Added deltaTime parameter
        // Simplified resource income update, actual logic would be in a ResourceSystem
        // or ported from JS building/unit updates.
        for ((_, teamResources) in resources) {
            teamResources.mass += teamResources.massIncome * deltaTime
            teamResources.energy += teamResources.energyIncome * deltaTime
            teamResources.computronium += teamResources.computroniumIncome * deltaTime
        }
    }

    private fun checkWinConditions() {
        if (gameState.winner != null) return

        // TODO: Port GameUnit and UNIT_TYPES to check commander status
        // val blueCommander = entityManager.units.find { it.team == "blue" && it.type == rtsgame.config.UNIT_TYPES.commander }
        // val redCommander = entityManager.units.find { it.team == "red" && it.type == rtsgame.config.UNIT_TYPES.commander }

        // if (blueCommander == null && redCommander == null) {
        //     gameState.winner = "DRAW"
        //     // gameState.addEvent("game_over", "Both commanders destroyed - Draw!", 3)
        // } else if (blueCommander == null) {
        //     gameState.winner = "RED"
        //     // gameState.addEvent("game_over", "Red team wins! Blue commander destroyed!", 3)
        // } else if (redCommander == null) {
        //     gameState.winner = "BLUE"
        //     // gameState.addEvent("game_over", "Blue team wins! Red commander destroyed!", 3)
        // }
    }

    // Placeholder for processing RTSRequests if that system is used
    // fun processRequest(request: RTSRequest) {
    //     // Logic to handle various RTSRequests
    // }
}