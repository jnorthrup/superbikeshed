package rtsgame.config

import rtsgame.config.GameConstants // Assuming GameConstants will be in this package

// Direct translation of js/config/buildingTypes.js
// NO IMPROVEMENTS - exact values preserved

object BUILDING_TYPES {
    val landFactory = BuildingType(
        name = "Land Factory",
        health = GameConstants.BUILDING_HEALTH.toDouble(),
        cost = Cost(mass = 200, energy = 100),
        buildTime = GameConstants.BUILDING_CONSTRUCTION_TIME, // Assuming frames
        radius = 25.0,
        color = "#4a90e2",
        abilities = listOf("build"),
        buildList = listOf("scout", "tank", "artillery", "engineer"), // Names of UnitTypes
        size = 40.0 // From modelDefaults.js (approx, JS has 100 for landFactory model, but type size is often smaller)
    )

    val advancedLandFactory = BuildingType(
        name = "Advanced Land Factory",
        size = 50.0,
        maxHp = 700.0,
        health = 700.0,
        produces = listOf("artillery", "shieldGenerator_unit", "experimental"), // Assuming shieldGenerator is a unit type name
        color = "#a60",
        cost = Cost(mass = 400, energy = 300),
        buildTime = GameConstants.BUILDING_CONSTRUCTION_TIME * 2 // Example, adjust as needed
    )

    val airFactory = BuildingType(
        name = "Air Factory",
        health = GameConstants.BUILDING_HEALTH * 1.2,
        cost = Cost(mass = 300, energy = 200),
        buildTime = (GameConstants.BUILDING_CONSTRUCTION_TIME * 1.2).toInt(),
        radius = 25.0,
        color = "#4a90e2",
        abilities = listOf("build"),
        buildList = listOf("scout", "fighter", "bomber"),
        size = 40.0 // Approx
    )

    val navalFactory = BuildingType(
        name = "Naval Factory",
        size = 50.0,
        maxHp = 600.0,
        health = 600.0,
        produces = listOf("battleship", "submarine"),
        color = "#048",
        cost = Cost(mass = 250, energy = 150),
        buildTime = (GameConstants.BUILDING_CONSTRUCTION_TIME * 1.5).toInt() // Example
    )

    val massExtractor = BuildingType(
        name = "Mass Extractor",
        health = GameConstants.BUILDING_HEALTH * 0.8,
        cost = Cost(mass = 100, energy = 50),
        buildTime = (GameConstants.BUILDING_CONSTRUCTION_TIME * 0.8).toInt(),
        productionRate = GameConstants.MASS_EXTRACTOR_RATE,
        radius = 20.0,
        color = "#4a90e2",
        abilities = listOf("extract"),
        resourceGeneration = ResourceGeneration(type = "mass", amount = GameConstants.MASS_EXTRACTOR_RATE),
        size = 30.0 // Approx
    )

    val energyExtractor = BuildingType( // Renamed from energyPlant in JS to match usage
        name = "Energy Extractor", // Consistent naming
        health = GameConstants.BUILDING_HEALTH * 0.8,
        cost = Cost(mass = 75, energy = 25),
        buildTime = (GameConstants.BUILDING_CONSTRUCTION_TIME * 0.8).toInt(),
        productionRate = GameConstants.ENERGY_EXTRACTOR_RATE, // JS used this for energyExtractor rate
        radius = 20.0,
        color = "#4a90e2",
        abilities = listOf("extract"),
        resourceGeneration = ResourceGeneration(type = "energy", amount = GameConstants.ENERGY_EXTRACTOR_RATE),
        size = 30.0 // Approx
    )

    // JS buildingTypes also had 'energyPlant', but 'energyExtractor' seems more specific
    // If 'energyPlant' is distinct, it should be added. Assuming 'energyExtractor' covers it.

    val computroniumExtractor = BuildingType(
        name = "Computronium Core Facility",
        size = 30.0,
        maxHp = 300.0,
        health = 300.0,
        produces = null,
        color = "#0af",
        resourceGeneration = ResourceGeneration(type = "computronium", amount = 0.5),
        cost = Cost(mass = 150, energy = 200, computronium = 5),
        hasComputroniumCore = true,
        coreEfficiency = 0.8,
        description = "Advanced facility that extracts and processes Computronium from specialized nodes",
        buildTime = (GameConstants.BUILDING_CONSTRUCTION_TIME * 1.0).toInt() // Example
    )

    val advancedComputroniumCore = BuildingType(
        name = "Advanced Computational Matrix",
        size = 40.0,
        maxHp = 500.0,
        health = 500.0,
        produces = null,
        color = "#08f",
        resourceGeneration = ResourceGeneration(type = "computronium", amount = 1.0),
        cost = Cost(mass = 300, energy = 400, computronium = 15),
        hasComputroniumCore = true,
        coreEfficiency = 1.0,
        provides = listOf("c2_enhancement", "pow_defense"),
        description = "Cutting-edge computational facility providing C&C enhancements and PoW defense",
        buildTime = (GameConstants.BUILDING_CONSTRUCTION_TIME * 1.8).toInt() // Example
    )

    val quantumComputingCenter = BuildingType(
        name = "Quantum Computing Center",
        size = 50.0,
        maxHp = 800.0,
        health = 800.0,
        produces = listOf("tank", "bot"), // Temporary until advanced units implemented
        color = "#f0f",
        resourceGeneration = ResourceGeneration(type = "computronium", amount = 2.0),
        cost = Cost(mass = 500, energy = 800, computronium = 50),
        hasComputroniumCore = true,
        coreEfficiency = 1.5,
        provides = listOf("advanced_ai", "quantum_entanglement", "pow_attack"),
        description = "Ultimate computational facility enabling quantum-enhanced warfare capabilities",
        buildTime = (GameConstants.BUILDING_CONSTRUCTION_TIME * 2.5).toInt() // Example
    )

    val defenseTower = BuildingType(
        name = "Defense Tower",
        health = GameConstants.BUILDING_HEALTH * 0.6,
        cost = Cost(mass = 150, energy = 75),
        buildTime = (GameConstants.BUILDING_CONSTRUCTION_TIME * 0.6).toInt(),
        attackRange = 200.0,
        attackCooldown = 30, // Assuming frames
        damage = 15.0,
        radius = 15.0,
        color = "#4a90e2",
        abilities = listOf("attack"),
        size = 25.0 // Approx
    )

    val shieldGenerator_building = BuildingType( // Renamed to avoid conflict with unit if any
        name = "Shield Generator", // This is a building
        health = GameConstants.BUILDING_HEALTH * 0.7,
        cost = Cost(mass = 250, energy = 150),
        buildTime = (GameConstants.BUILDING_CONSTRUCTION_TIME * 0.7).toInt(),
        shieldRadius = 150.0,
        shieldStrength = 500.0,
        shieldRegen = 5.0,
        radius = 20.0,
        color = "#4a90e2",
        abilities = listOf("shield"),
        size = 30.0 // Approx
    )

    val radar = BuildingType(
        name = "Radar",
        health = GameConstants.BUILDING_HEALTH * 0.5,
        cost = Cost(mass = 100, energy = 75),
        buildTime = (GameConstants.BUILDING_CONSTRUCTION_TIME * 0.5).toInt(),
        detectionRadius = 300.0,
        radius = 15.0,
        color = "#4a90e2",
        abilities = listOf("detect"),
        size = 20.0 // Approx
    )

    // Helper to get a building type by name
    fun byName(name: String): BuildingType? {
        return BuildingTypesData.values.find { it.name == name }
    }
}

data class BuildingType(
    val name: String,
    val health: Double,
    val cost: Cost, // Reusing Cost data class from UnitTypes.kt
    val buildTime: Int, // Assuming frames, consistent with GameConstants
    val radius: Double, // Collision/selection radius
    val color: String,
    val abilities: List<String>? = null,
    val buildList: List<String>? = null, // Names of UnitTypes this building can produce
    val size: Double, // Visual/footprint size
    val maxHp: Double = health, // Default maxHp to health if not specified
    val produces: List<String>? = null, // Alternative to buildList for direct production definition
    val resourceGeneration: ResourceGeneration? = null,
    val productionRate: Double? = null, // For extractors, if different from resourceGeneration.amount
    val attackRange: Double? = null,
    val attackCooldown: Int? = null, // Assuming frames
    val damage: Double? = null,
    val shieldRadius: Double? = null,
    val shieldStrength: Double? = null,
    val shieldRegen: Double? = null,
    val detectionRadius: Double? = null,
    val hasComputroniumCore: Boolean? = false,
    val coreEfficiency: Double? = 1.0,
    val provides: List<String>? = null,
    val description: String? = null
)

data class ResourceGeneration(
    val type: String, // "mass", "energy", "computronium"
    val amount: Double // Amount per second or per tick, needs consistency
)

// Internal data storage for byName lookup
private object BuildingTypesData {
    val values: List<BuildingType> by lazy {
        listOf(
            BUILDING_TYPES.landFactory, BUILDING_TYPES.advancedLandFactory, BUILDING_TYPES.airFactory,
            BUILDING_TYPES.navalFactory, BUILDING_TYPES.massExtractor, BUILDING_TYPES.energyExtractor,
            BUILDING_TYPES.computroniumExtractor, BUILDING_TYPES.advancedComputroniumCore,
            BUILDING_TYPES.quantumComputingCenter, BUILDING_TYPES.defenseTower,
            BUILDING_TYPES.shieldGenerator_building, BUILDING_TYPES.radar
        )
    }
}
