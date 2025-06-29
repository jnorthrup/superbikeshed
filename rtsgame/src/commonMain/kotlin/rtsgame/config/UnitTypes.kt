package rtsgame.config

import rtsgame.config.GameConstants // Assuming GameConstants will be in this package

// Direct translation of js/config/unitTypes.js
// NO IMPROVEMENTS - preserving exact JS behavior

object UNIT_TYPES {
    val tank = UnitType(
        name = "Tank",
        domain = "land",
        size = 10.0,
        speed = GameConstants.UNIT_SPEED * 0.7,
        maxHp = GameConstants.UNIT_HEALTH * 1.5,
        damage = 25.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 1.2,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 1.2, // This is likely frames, convert to seconds if needed by logic
        buildTime = 240, // frames or seconds? JS was frames. Assuming frames for now.
        color = "#4a90e2",
        effectColor = "#ff0",
        cost = Cost(mass = 150, energy = 75),
        tier = 1,
        commandRank = 1,
        radius = 16.0,
        abilities = listOf("attack")
    )

    val bot = UnitType(
        name = "Bot",
        domain = "land",
        size = 6.0,
        speed = GameConstants.UNIT_SPEED * 2.0,
        maxHp = GameConstants.UNIT_HEALTH * 0.7,
        damage = 10.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 0.8,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 0.7,
        buildTime = 30,
        color = "#aaa",
        effectColor = "#f80",
        cost = Cost(mass = 50, energy = 25),
        tier = 1,
        commandRank = 1,
        radius = 12.0,
        abilities = listOf("scout")
    )

    val artillery = UnitType(
        name = "Artillery",
        domain = "land",
        size = 12.0,
        speed = GameConstants.UNIT_SPEED * 0.5,
        maxHp = GameConstants.UNIT_HEALTH * 0.8,
        damage = 40.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 3.0,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 2.0,
        buildTime = 300,
        color = "#4a90e2",
        effectColor = "#f00",
        cost = Cost(mass = 200, energy = 150),
        tier = 2,
        commandRank = 2,
        radius = 14.0,
        abilities = listOf("attack")
    )

    val battleship = UnitType(
        name = "Battleship",
        domain = "sea",
        size = 20.0,
        speed = GameConstants.UNIT_SPEED * 0.8,
        maxHp = GameConstants.UNIT_HEALTH * 1.2,
        damage = 40.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 1.5,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 1.5,
        buildTime = 120,
        color = "#048",
        effectColor = "#0ff",
        cost = Cost(mass = 200, energy = 150),
        tier = 2
    )

    val submarine = UnitType(
        name = "Submarine",
        domain = "sea",
        size = 15.0,
        speed = GameConstants.UNIT_SPEED * 1.2,
        maxHp = GameConstants.UNIT_HEALTH * 0.9,
        damage = 30.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 1.8,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 1.8,
        buildTime = 80,
        color = "#026",
        effectColor = "#088",
        cost = Cost(mass = 120, energy = 80),
        tier = 1
    )

    val fighter = UnitType(
        name = "Fighter",
        domain = "air",
        size = 8.0,
        speed = GameConstants.UNIT_SPEED * 4.0,
        maxHp = GameConstants.UNIT_HEALTH * 0.6,
        damage = 15.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 1.5,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 1.5,
        buildTime = 50,
        color = "#ccc",
        effectColor = "#fff",
        cost = Cost(mass = 80, energy = 60),
        tier = 1
    )

    val bomber = UnitType(
        name = "Bomber",
        domain = "air",
        size = 12.0,
        speed = GameConstants.UNIT_SPEED * 2.0,
        maxHp = GameConstants.UNIT_HEALTH * 1.0,
        damage = 60.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 1.0,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 1.0,
        buildTime = 100,
        color = "#999",
        effectColor = "#ff8",
        cost = Cost(mass = 150, energy = 120),
        tier = 2
    )

    val gunship = UnitType(
        name = "Gunship",
        domain = "air",
        size = 10.0,
        speed = GameConstants.UNIT_SPEED * 3.0,
        maxHp = GameConstants.UNIT_HEALTH * 0.8,
        damage = 25.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 1.2,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 1.2,
        buildTime = 70,
        color = "#bbb",
        effectColor = "#f8f",
        cost = Cost(mass = 100, energy = 80),
        tier = 1
    )

    val engineer = UnitType(
        name = "Engineer",
        domain = "land",
        size = 8.0,
        speed = GameConstants.UNIT_SPEED * 0.9,
        speedLand = GameConstants.UNIT_SPEED * 0.9,
        speedWater = GameConstants.UNIT_SPEED * 0.9,
        maxHp = GameConstants.UNIT_HEALTH * 0.6,
        damage = 2.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 0.5,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 0.5,
        buildTime = 180,
        color = "#4a90e2",
        effectColor = "#0f0",
        cost = Cost(mass = 75, energy = 50),
        support = true,
        movementType = "amphibious",
        abilities = listOf("build", "repair", "capture"),
        radius = 12.0,
        tier = 1
    )

    val shieldGenerator = UnitType(
        name = "Shield Generator",
        domain = "land",
        size = 14.0,
        speed = GameConstants.UNIT_SPEED * 0.7,
        maxHp = GameConstants.UNIT_HEALTH * 1.5,
        damage = 0.0,
        range = 0.0,
        attackSpeed = 0.0,
        buildTime = 80,
        color = "#0ff",
        effectColor = "#0ff",
        cost = Cost(mass = 120, energy = 200),
        support = true,
        shields = 100.0, // Old HP shield
        shieldRegen = 2.0, // Old HP shield regen
        shieldRadius = 150.0,
        shieldBoost = 5.0,
        tier = 2
    )

    val experimental = UnitType(
        name = "Experimental",
        domain = "land",
        size = 25.0,
        speed = GameConstants.UNIT_SPEED * 0.5,
        maxHp = GameConstants.UNIT_HEALTH * 5.0,
        damage = 100.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 2.0,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 40.0, // JS had 40, likely meant frames. If so, needs conversion or logic adjustment.
        buildTime = 300,
        color = "#f0f",
        effectColor = "#f0f",
        cost = Cost(mass = 500, energy = 400),
        shields = 200.0, // Old HP shield
        shieldRegen = 5.0, // Old HP shield regen
        tier = 3
    )

    val commander = UnitType(
        name = "Commander",
        domain = "land",
        size = 18.0,
        speed = GameConstants.UNIT_SPEED * 0.8,
        speedLand = GameConstants.UNIT_SPEED * 0.8,
        speedWater = GameConstants.UNIT_SPEED * 0.8,
        maxHp = GameConstants.UNIT_HEALTH * 2.0,
        damage = 20.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 1.5,
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 0.8,
        color = "#4a90e2",
        effectColor = "#f0f",
        cost = Cost(mass = 0, energy = 0),
        tier = 2, // Tier 2.5 in JS, rounded to 2
        support = true,
        buildList = emptyList(), // To be populated later
        buildRate = 1.0,
        shields = 200.0, // Old HP shield
        shieldRegen = 2.0, // Old HP shield regen
        movementType = "amphibious",
        abilities = listOf("build", "repair", "capture"),
        radius = 20.0,
        // Energy related properties from JS unit.js, not directly in unitTypes.js but part of unit definition
        batteryCapacity = 500.0, // Example value
        generatorOutput = 5.0,  // Example value (energy per second)
        weaponEnergyCost = 0.0, // Commander basic weapon might not cost energy
        maxEnergyShields = 100.0, // New energy shield
        shieldRegenRate = 2.0     // New energy shield regen rate (energy per second)
    )

    val ai_commander = UnitType(
        name = "AI Commander",
        domain = "land",
        size = 15.0,
        speed = GameConstants.UNIT_SPEED * 1.5,
        maxHp = GameConstants.UNIT_HEALTH * 8.0, // JS had 800, assuming UNIT_HEALTH = 100
        maxEnergyShields = 300.0,
        shieldRegenRate = 5.0,
        damage = 40.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 2.0, // JS had 200
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 2.0, // JS had 20, assuming it meant 2x cooldown
        buildTime = 300,
        color = "#0ff",
        effectColor = "#0af",
        cost = Cost(mass = 500, energy = 800, computronium = 25),
        tier = 3,
        hasComputroniumCore = true,
        coreEfficiency = 1.2,
        commandRank = 4,
        provides = listOf("enhanced_c2", "tactical_analysis"),
        description = "Advanced AI-controlled commander with enhanced tactical capabilities"
    )

    val quantum_scout = UnitType(
        name = "Quantum Scout",
        domain = "land",
        size = 8.0,
        speed = GameConstants.UNIT_SPEED * 4.0,
        maxHp = GameConstants.UNIT_HEALTH * 1.5, // JS had 150
        maxEnergyShields = 100.0,
        shieldRegenRate = 8.0,
        damage = 15.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 2.5, // JS had 250
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 1.5, // JS had 15
        buildTime = 120,
        color = "#f0f",
        effectColor = "#f8f",
        cost = Cost(mass = 200, energy = 300, computronium = 10),
        tier = 3,
        hasComputroniumCore = true,
        coreEfficiency = 0.6,
        abilities = listOf("quantum_entanglement", "phase_shift"),
        description = "Advanced scout with quantum-enhanced reconnaissance capabilities"
    )

    val cyber_warfare_unit = UnitType(
        name = "Cyber Warfare Specialist",
        domain = "land",
        size = 10.0,
        speed = GameConstants.UNIT_SPEED * 1.8,
        maxHp = GameConstants.UNIT_HEALTH * 2.0, // JS had 200
        maxEnergyShields = 150.0,
        shieldRegenRate = 6.0,
        damage = 25.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 1.8, // JS had 180
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 2.5, // JS had 25
        buildTime = 180,
        color = "#808",
        effectColor = "#a0a",
        cost = Cost(mass = 300, energy = 500, computronium = 20),
        tier = 3,
        hasComputroniumCore = true,
        coreEfficiency = 1.0,
        abilities = listOf("pow_attack", "electronic_warfare", "system_infiltration"),
        description = "Specialized unit capable of launching computational attacks on enemy systems"
    )

    val quantum_tank = UnitType(
        name = "Quantum-Enhanced Heavy Tank",
        domain = "land",
        size = 14.0,
        speed = GameConstants.UNIT_SPEED * 1.2,
        maxHp = GameConstants.UNIT_HEALTH * 4.0, // JS had 400
        maxEnergyShields = 200.0,
        shieldRegenRate = 4.0,
        damage = 60.0,
        range = GameConstants.UNIT_ATTACK_RANGE * 2.2, // JS had 220
        attackSpeed = GameConstants.UNIT_ATTACK_COOLDOWN * 4.0, // JS had 40
        buildTime = 240,
        color = "#048",
        effectColor = "#06a",
        cost = Cost(mass = 400, energy = 600, computronium = 15),
        tier = 3,
        hasComputroniumCore = true,
        coreEfficiency = 0.8,
        abilities = listOf("adaptive_armor", "predictive_targeting"),
        description = "Heavy tank with quantum-enhanced targeting and adaptive defense systems"
    )

    // Helper to get a unit type by name, useful for dynamic creation
    fun byName(name: String): UnitType? {
        return UnitTypesData.values.find { it.name == name }
    }
}

data class UnitType(
    val name: String,
    val domain: String, // "land", "air", "sea"
    val size: Double, // Visual size, also for collision
    val speed: Double, // Base speed in world units per second
    val maxHp: Double,
    val damage: Double, // Base damage per attack
    val range: Double, // Attack range
    val attackSpeed: Double, // Cooldown in frames/seconds. If frames, needs conversion. JS used frames.
    val buildTime: Int, // Time to build in frames or seconds
    val color: String, // Hex color for rendering
    val effectColor: String, // Color for attack effects
    val cost: Cost,
    val tier: Int, // Tech tier
    val commandRank: Int? = null, // For command hierarchy
    val radius: Double? = null, // For collision or selection
    val abilities: List<String>? = null,
    val support: Boolean? = false,
    val movementType: String? = "land", // e.g., "land", "air", "amphibious"
    val speedLand: Double? = speed, // Specific speed on land for amphibious
    val speedWater: Double? = speed, // Specific speed on water for amphibious
    val shields: Double? = 0.0, // Old HP shield system
    val shieldRegen: Double? = 0.0, // Old HP shield regen
    val buildList: List<String> = emptyList(), // For builder units, list of buildable unit/building type names
    val buildRate: Double? = 1.0, // For builder units, progress per second
    val hasComputroniumCore: Boolean? = false,
    val coreEfficiency: Double? = 1.0,
    val provides: List<String>? = null,
    val description: String? = null,
    val unitWeight: Double? = 0.0, // For speed penalty calculation
    // New energy system properties (from unit.js, not unitTypes.js)
    val batteryCapacity: Double? = null, // Max energy storage
    val generatorOutput: Double? = null,  // Energy generation per second
    val weaponEnergyCost: Double? = null, // Energy cost per weapon fire
    val maxEnergyShields: Double? = null, // New energy-based shield HP
    val shieldRegenRate: Double? = null   // New energy-based shield regeneration rate (energy/sec)
)

data class Cost(
    val mass: Int,
    val energy: Int,
    val computronium: Int? = 0
)

// Internal data storage for byName lookup
private object UnitTypesData {
    val values: List<UnitType> by lazy {
        listOf(
            UNIT_TYPES.tank, UNIT_TYPES.bot, UNIT_TYPES.artillery, UNIT_TYPES.battleship,
            UNIT_TYPES.submarine, UNIT_TYPES.fighter, UNIT_TYPES.bomber, UNIT_TYPES.gunship,
            UNIT_TYPES.engineer, UNIT_TYPES.shieldGenerator, UNIT_TYPES.experimental,
            UNIT_TYPES.commander, UNIT_TYPES.ai_commander, UNIT_TYPES.quantum_scout,
            UNIT_TYPES.cyber_warfare_unit, UNIT_TYPES.quantum_tank
        )
    }
}
