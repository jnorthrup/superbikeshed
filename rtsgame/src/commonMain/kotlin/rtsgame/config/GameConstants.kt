import kotlin.math.*
package rtsgame.config
import kotlinx.datetime.*
import kotlin.time.*

// Direct translation of js/config/gameConstants.js
// NO IMPROVEMENTS - exact values preserved

// World configuration
const val WORLD_SIZE = 800
const val GRID_SIZE = 32
const val TILE_SIZE = WORLD_SIZE / GRID_SIZE

// Camera configuration
const val MIN_ZOOM = 0.5
const val MAX_ZOOM = 2.0
const val ZOOM_STEP = 0.1

// Resource configuration
const val INITIAL_MASS = 100
const val INITIAL_ENERGY = 150
const val MASS_EXTRACTOR_RATE = 1.0
const val ENERGY_EXTRACTOR_RATE = 2.0

// GameUnit configuration
const val UNIT_SPEED = 2.0
const val UNIT_ATTACK_RANGE = 100
const val UNIT_ATTACK_COOLDOWN = 60 // frames
const val UNIT_HEALTH = 100

// Building configuration
const val BUILDING_HEALTH = 500
const val BUILDING_CONSTRUCTION_TIME = 300 // frames

// Projectile configuration
const val PROJECTILE_SPEED = 5.0
const val PROJECTILE_DAMAGE = 10
const val PROJECTILE_BLAST_RADIUS = 5

// Effect configuration
const val EFFECT_DURATION = 30 // frames
const val EFFECT_SCALE = 1.0

// AI configuration
const val AI_DECISION_INTERVAL = 60 // frames
const val AI_ATTACK_COORDINATION_RADIUS = 200

object TERRAIN_TYPES {
    const val WATER = 0
    const val LAND = 1
    const val MOUNTAIN = 2
    const val RESOURCE = 3 // New terrain type for resources
}

// Resource Types - Core RTS resources plus Computronium
object RESOURCE_TYPES {
    const val MASS = "MASS"
    const val ENERGY = "ENERGY"
}

// Computronium Configuration
object COMPUTRONIUM_CONFIG {
    const val BASE_GENERATION_RATE = 0.1 // Base computronium per second from cores
    const val DINING_PHILOSOPHERS_PENALTY = 0.15 // Efficiency loss when cores compete
    const val MAX_CORE_EFFICIENCY = 1.0
    const val MIN_CORE_EFFICIENCY = 0.2
    const val COMPUTATIONAL_WARFARE_COST = 5.0 // Computronium cost per PoW operation
    const val C2_LATENCY_BASE = 0.1 // Base command latency in seconds
    const val C2_COMPUTRONIUM_BONUS = 0.02 // Latency reduction per computronium point
    
    // Command & Control ranges
    object COMMAND_RANGES {
        const val TACTICAL = 150   // Close-range tactical command
        const val OPERATIONAL = 300 // Medium-range operational command
        const val STRATEGIC = 500   // Long-range strategic command
    }
}

const val MIN_LAND_PERCENTAGE = 0.3
const val MAX_TERRAIN_RETRIES = 5

// Veterancy levels
object VETERANCY_LEVELS {
    const val GREEN = "GREEN"
    const val REGULAR = "REGULAR"
    const val VETERAN = "VETERAN"
    const val ELITE = "ELITE"
    const val HERO = "HERO"
}

// Command fitness levels
object COMMAND_FITNESS_LEVELS {
    const val FULL_COMMAND = "FULL_COMMAND"
    const val REDUCED_AUTHORITY = "REDUCED_AUTHORITY"
    const val COMPROMISED_COMMAND = "COMPROMISED_COMMAND"
    const val CRITICAL_STATUS = "CRITICAL_STATUS"
    const val COMBAT_INEFFECTIVE = "COMBAT_INEFFECTIVE"
}

// Economic phases
object ECONOMIC_PHASES {
    const val SURVIVAL = "SURVIVAL"      // 1-2 extractors
    const val EXPANSION = "EXPANSION"    // 3-4 extractors
    const val ADVANCED = "ADVANCED"      // 5-6 extractors
    const val EXPERIMENTAL = "EXPERIMENTAL" // 7+ extractors
}

// Building costs
object BUILDING_COSTS {
    object EXTRACTOR {
        const val mass = 50
        const val energy = 25
    }
    object ENERGY_PLANT {
        const val mass = 30
        const val energy = 20
    }
    object LAND_FACTORY {
        const val mass = 200
        const val energy = 100
    }
    object ADVANCED_FACTORY {
        const val mass = 400
        const val energy = 300
    }
    object AIR_FACTORY {
        const val mass = 150
        const val energy = 120
    }
    object NAVAL_FACTORY {
        const val mass = 250
        const val energy = 150
    }
}

// Building yields
object BUILDING_YIELDS {
    object EXTRACTOR {
        const val mass = 2
    }
    object ENERGY_PLANT {
        const val energy = 3
    }
}