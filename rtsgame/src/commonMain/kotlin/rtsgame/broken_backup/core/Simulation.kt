import kotlin.math.*
package rtsgame.core
import kotlinx.datetime.*
import kotlin.time.*

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
    var mass: Int,
    var energy: Int,
    var computronium: Int = 10, // Start with some Computronium for initial building
    var massIncome: Double = 0.0,
    var energyIncome: Double = 0.0,
    var computroniumIncome: Double = 0.0
)

class Simulation(internal val context: SimulationContext) {
    val GAME_SEED = context.GAME_SEED
    val seedRandom = context.seedRandom
    val HEADLESS_MODE = context.HEADLESS_MODE
    val RECORD_AI_DECISIONS = context.RECORD_AI_DECISIONS
    val RECORD_AI_DECISIONS_DURATION_SECONDS = context.RECORD_AI_DECISIONS_DURATION_SECONDS
    val battleJournal = context.battleJournal
    
    // Initialize managers - use TrikeShed-based entity management
    val entityManager = EntityManager()
    val gameState = GameState()
    
    // Initialize Computronium managers for each team
    val computroniumManagers = mutableMapOf(
        "blue" to Any(), // ComputroniumManager("blue") - will be typed when ported
        "red" to Any()   // ComputroniumManager("red") - will be typed when ported
    )
    
    // Initialize Enhanced Command Hierarchies for each team
    val commandHierarchies = mutableMapOf(
        "blue" to Any(), // EnhancedCommandHierarchy("blue", computroniumManagers["blue"]) - will be typed when ported
        "red" to Any()   // EnhancedCommandHierarchy("red", computroniumManagers["red"]) - will be typed when ported
    )
    
    // Initialize resources - now including Computronium
    val resources = mutableMapOf(
        "blue" to TeamResourcesExtended(
            mass = SIMULATION_CONFIG.INITIAL_BLUE_MASS,
            energy = SIMULATION_CONFIG.INITIAL_BLUE_ENERGY
        ),
        "red" to TeamResourcesExtended(
            mass = SIMULATION_CONFIG.INITIAL_RED_MASS,
            energy = SIMULATION_CONFIG.INITIAL_RED_ENERGY
        )
    )

    // Initialize terrain and resource nodes
    val terrain: MutableList<MutableList<TerrainCell>> = mutableListOf()
    val resourceNodes: MutableList<ResourceNode> = context.resourceNodes ?: mutableListOf()
    
    // Create gameContext for compatibility with existing code
    val gameContext = mapOf(
        "terrain" to terrain,
        "resourceNodes" to resourceNodes,
        "UNIT_TYPES" to Any(), // Will be typed when ported
        "BUILDING_TYPES" to Any(), // Will be typed when ported
        "WORLD_SIZE" to WORLD_SIZE,
        "TILE_SIZE" to TILE_SIZE,
        "GRID_SIZE" to GRID_SIZE,
        "TERRAIN_TYPES" to TERRAIN_TYPES
    )

    var lastFrameTime = 0L

    suspend fun init() {
        println("Initializing simulation...")
        
        // Generate terrain
        generateTerrain()
        
        // Setup commander build lists
        // TODO: When UNIT_TYPES is ported, implement this
        
        // Spawn commanders
        spawnCommanders()
        
        println("Simulation initialized successfully")
    }

    suspend fun generateTerrain() {
        println("Generating terrain...")
        
        // Initialize terrain grid
        for (x in 0 until GRID_SIZE) {
            terrain.add(mutableListOf())
            for (y in 0 until GRID_SIZE) {
                // Simple terrain generation - mostly land with some water and mountains
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
        
        // Generate resource nodes
        generateResourceNodes()
        
        println("Terrain generation complete")
    }

    fun generateResourceNodes() {
        resourceNodes.clear()
        
        // Place resource nodes on resource terrain tiles
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                if (terrain[x][y].type == TERRAIN_TYPES.RESOURCE) {
                    val worldX = x * TILE_SIZE.toDouble()
                    val worldY = y * TILE_SIZE.toDouble()
                    // Generate different resource types including Computronium
                    val rand = seedRandom.nextDouble()
                    val type = when {
                        rand < 0.4 -> RESOURCE_TYPES.MASS
                        rand < 0.8 -> RESOURCE_TYPES.ENERGY
                        else -> "COMPUTRONIUM" // Rare but valuable - TODO: Add to RESOURCE_TYPES
                    }
                    
                    resourceNodes.add(ResourceNode(
                        x = worldX,
                        y = worldY,
                        type = type,
                        amount = 10000,
                        maxAmount = 10000,
                        occupied = false
                    ))
                }
            }
        }
        
        println("Generated ${resourceNodes.size} resource nodes")
    }

    suspend fun spawnCommanders() {
        // TODO: Implement when GameUnit class is ported
        println("Spawning commanders...")
    }

    fun update(deltaTime: Double) {
        // TODO: Implement main update loop when all components are ported
        gameState.gameTime += deltaTime.toInt()
    }
}