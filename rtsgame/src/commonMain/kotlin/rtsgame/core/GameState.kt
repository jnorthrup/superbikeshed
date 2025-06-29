package rtsgame.core

// Direct translation of js/core/gameState.js
// NO IMPROVEMENTS - preserving exact JS behavior

data class TeamResources( // Updated to include computronium, matching TeamResourcesExtended
    var mass: Double = 100.0,
    var energy: Double = 150.0,
    var computronium: Double = 10.0,
    var massIncome: Double = 0.0,
    var energyIncome: Double = 0.0,
    var computroniumIncome: Double = 0.0
)

// Structure for game events, similar to JS
data class GameEvent(
    val time: Int, // Game time of the event
    val type: String,
    val message: String,
    val importance: Int = 1,
    val position: Pair<Double, Double>? = null // Optional world position
// Direct translation of js/core/gameState.js
// NO IMPROVEMENTS - preserving exact JS behavior

data class TeamResources( // Updated to include computronium, matching TeamResourcesExtended
    var mass: Double = 100.0,
    var energy: Double = 150.0,
    var computronium: Double = 10.0,
    var massIncome: Double = 0.0,
    var energyIncome: Double = 0.0,
    var computroniumIncome: Double = 0.0
)

// Structure for game events, similar to JS
data class GameEvent(
    val time: Double, // Game time of the event - Changed to Double
    val type: String,
    val message: String,
    val importance: Int = 1,
    val position: Pair<Double, Double>? = null // Optional world position
)

class GameState {
    var gameTime: Double = 0.0 // Changed to Double to match JS float time
    var winner: String? = null
    var paused: Boolean = false
    var isRunning: Boolean = false // Indicates if the simulation loop should continue

    val resources = mutableMapOf(
        "blue" to TeamResources(),
        "red" to TeamResources()
    )

    // Entity lists - will be typed properly when entities are ported
    val units = mutableListOf<Any>()
    val buildings = mutableListOf<Any>()
    val terrain = mutableListOf<Any>() // Consider a more structured terrain representation later
    val resourceNodes = mutableListOf<Any>()
    val effects = mutableListOf<Any>()
    val projectiles = mutableListOf<Any>()
    val captions = mutableListOf<Any>()

    val events = mutableListOf<GameEvent>() // Added events list

    fun updateResourceIncome() {
        for (team in listOf("blue", "red")) {
            val teamRes = resources[team] ?: continue
            var currentMassIncome = 0.0
            var currentEnergyIncome = 0.0
            var currentComputroniumIncome = 0.0 // Assuming computronium income is possible

            // Calculate income from buildings
            // TODO: This logic needs to be updated when Building.kt is fully ported
            // and has type-safe access to `type` and `income` properties.
            for (building in buildings) {
                val b = building as? Map<String, Any> // Temporary unsafe cast
                if (b != null && b["team"] == team) {
                    // Assuming 'income' is per-second, and 'resourceType' specifies what it generates
                    val buildingIncome = (b["income"] as? Double) ?: 0.0
                    when (b["type"] as? String) { // Assuming building type is a string
                        "massExtractor" -> currentMassIncome += buildingIncome
                        "energyExtractor", "energyPlant" -> currentEnergyIncome += buildingIncome
                        "computroniumExtractor", "advancedComputroniumCore" -> currentComputroniumIncome += buildingIncome
                    }
                }
            }

            teamRes.massIncome = currentMassIncome
            teamRes.energyIncome = currentEnergyIncome
            teamRes.computroniumIncome = currentComputroniumIncome

            // Add income to resources (scaled by a typical tick, e.g., 1/60th of a second if called per frame)
            // This should ideally be driven by deltaTime from the main loop.
            // For now, assuming this is called per second or deltaTime is handled by caller.
            // The JS version added income directly. Let's assume this method is called per second for now.
            // This logic should actually be: teamRes.mass += teamRes.massIncome * deltaTime
            // The income properties are rates (per second). The actual addition happens based on deltaTime in the game loop.
            // For now, keeping it as direct addition to mirror a possible interpretation of the JS,
            // but this will be refined when resource system is fully ported.
            teamRes.mass += teamRes.massIncome
            teamRes.energy += teamRes.energyIncome
            teamRes.computronium += teamRes.computroniumIncome
        }
    }

    // Method to add game events, similar to JS `addEvent`
    fun addEvent(type: String, message: String, importance: Int = 1, position: Pair<Double, Double>? = null): GameEvent {
        val event = GameEvent(
            time = this.gameTime, // gameTime is now Double
            type = type,
            message = message,
            importance = importance,
            position = position
        )
        this.events.add(0, event) // Add to the beginning to match JS unshift behavior
        if (this.events.size > 10) { // Keep only the last 10 events
            this.events.removeLast()
        }
        // TODO: JS version also updated UI here. That will be handled separately in KMP.
        return event
    }


    // createGameContext might not be needed if Simulation directly accesses GameState properties.
    // If kept, it should return a more typed representation or be used carefully.
    fun createGameContext(): Map<String, Any> {
        return mapOf(
            "units" to units,
            "buildings" to buildings,
            "terrain" to terrain,
            "resourceNodes" to resourceNodes,
            "effects" to effects,
            "projectiles" to projectiles,
            "resources" to resources,
            "gameTime" to gameTime,
            "gameState" to this // Provide the GameState instance itself
        )
    }

    fun reset() {
        gameTime = 0.0 // Reset to Double
        winner = null
        paused = false
        isRunning = false // Should be true when simulation starts

        resources.clear()
        resources["blue"] = TeamResources()
        resources["red"] = TeamResources()

        units.clear()
        buildings.clear()
        terrain.clear()
        resourceNodes.clear()
        effects.clear()
        projectiles.clear()
        captions.clear()
        events.clear() // Clear events on reset
    }
}