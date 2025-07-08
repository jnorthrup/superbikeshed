package rtsgame.config

import kotlinx.serialization.Serializable

/**
 * Unit type definitions for RTS game flow mode
 * Ported from JS configuration with Kotlin data structures
 */
@Serializable
data class UnitType(
    val name: String,
    val domain: String, // land, sea, air
    val size: Int,
    val speed: Float,
    val maxHp: Float,
    val damage: Int,
    val range: Float,
    val attackSpeed: Float,
    val buildTime: Int,
    val color: String,
    val effectColor: String,
    val cost: ResourceCost,
    val tier: Int,
    val commandRank: Int = 1,
    val radius: Int = 10,
    val abilities: List<String> = emptyList(),
    val modelPath: String = "",
    val scale: Float = 1.0f
)

@Serializable
data class ResourceCost(
    val mass: Int,
    val energy: Int
)

/**
 * Predefined unit types for flow mode
 */
object UnitTypes {
    val TANK = UnitType(
        name = "Tank",
        domain = "land",
        size = 10,
        speed = 1.4f,
        maxHp = 150f,
        damage = 25,
        range = 120f,
        attackSpeed = 72f,
        buildTime = 240,
        color = "#4a90e2",
        effectColor = "#ff0",
        cost = ResourceCost(150, 75),
        tier = 1,
        commandRank = 1,
        radius = 16,
        abilities = listOf("attack"),
        modelPath = "tank",
        scale = 1.0f
    )
    
    val SCOUT = UnitType(
        name = "Scout",
        domain = "land",
        size = 6,
        speed = 4.0f,
        maxHp = 70f,
        damage = 10,
        range = 80f,
        attackSpeed = 42f,
        buildTime = 30,
        color = "#aaa",
        effectColor = "#f80",
        cost = ResourceCost(50, 25),
        tier = 1,
        commandRank = 1,
        radius = 12,
        abilities = listOf("scout"),
        modelPath = "scout",
        scale = 0.8f
    )
    
    val ARTILLERY = UnitType(
        name = "Artillery",
        domain = "land",
        size = 12,
        speed = 1.0f,
        maxHp = 80f,
        damage = 40,
        range = 300f,
        attackSpeed = 120f,
        buildTime = 300,
        color = "#4a90e2",
        effectColor = "#f00",
        cost = ResourceCost(200, 150),
        tier = 2,
        commandRank = 2,
        radius = 14,
        abilities = listOf("attack"),
        modelPath = "artillery",
        scale = 1.2f
    )
    
    val COMMANDER = UnitType(
        name = "Commander",
        domain = "land",
        size = 18,
        speed = 1.6f,
        maxHp = 200f,
        damage = 20,
        range = 150f,
        attackSpeed = 90f,
        buildTime = 0,
        color = "#f0f",
        effectColor = "#f0f",
        cost = ResourceCost(0, 0),
        tier = 3,
        commandRank = 3,
        radius = 20,
        abilities = listOf("build", "command"),
        modelPath = "commander",
        scale = 1.5f
    )
    
    val FIGHTER = UnitType(
        name = "Fighter",
        domain = "air",
        size = 8,
        speed = 8.0f,
        maxHp = 60f,
        damage = 15,
        range = 150f,
        attackSpeed = 90f,
        buildTime = 50,
        color = "#ccc",
        effectColor = "#fff",
        cost = ResourceCost(80, 60),
        tier = 1,
        commandRank = 1,
        radius = 10,
        abilities = listOf("attack"),
        modelPath = "fighter",
        scale = 0.9f
    )
    
    val SUBMARINE = UnitType(
        name = "Submarine",
        domain = "sea",
        size = 15,
        speed = 2.4f,
        maxHp = 90f,
        damage = 30,
        range = 216f,
        attackSpeed = 108f,
        buildTime = 80,
        color = "#026",
        effectColor = "#088",
        cost = ResourceCost(120, 80),
        tier = 1,
        commandRank = 1,
        radius = 15,
        abilities = listOf("attack"),
        modelPath = "submarine",
        scale = 1.1f
    )
    
    val ALL_UNITS = mapOf(
        "tank" to TANK,
        "scout" to SCOUT,
        "artillery" to ARTILLERY,
        "commander" to COMMANDER,
        "fighter" to FIGHTER,
        "submarine" to SUBMARINE
    )
    
    fun getUnitType(name: String): UnitType? = ALL_UNITS[name.lowercase()]
} 